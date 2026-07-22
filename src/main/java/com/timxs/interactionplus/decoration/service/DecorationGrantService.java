package com.timxs.interactionplus.decoration.service;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.timxs.interactionplus.api.GrantResult;
import com.timxs.interactionplus.api.GrantableDecoration;
import com.timxs.interactionplus.api.RevokeResult;
import com.timxs.interactionplus.decoration.constants.DecorationStatus;
import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationGrant;
import com.timxs.interactionplus.decoration.extension.UserDecorationRarity;
import com.timxs.interactionplus.decoration.model.BatchGrantResult;
import com.timxs.interactionplus.decoration.model.GrantParam;
import com.timxs.interactionplus.decoration.model.GrantView;
import com.timxs.interactionplus.decoration.model.RevokeHeldParam;
import com.timxs.interactionplus.decoration.model.RevokeParam;
import com.timxs.interactionplus.decoration.model.RoleGrantParam;
import com.timxs.interactionplus.core.notification.InteractionPlusNotificationService;
import com.timxs.interactionplus.core.support.ListRequestSupport;
import com.timxs.interactionplus.core.support.NameGenerator;
import com.timxs.interactionplus.core.support.RoleUtils;
import com.timxs.interactionplus.core.support.SecurityUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import run.halo.app.core.extension.Role;
import run.halo.app.core.extension.RoleBinding;
import run.halo.app.core.extension.User;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Condition;

/**
 * 装饰授予服务：单 / 多用户授予、按角色快照授予、撤销。
 *
 * <p>授予按<b>来源并存</b>（后台手动 / 角色快照为一个来源，各外部插件各为一个来源）：
 * 同一来源对同一用户同一装饰同时至多一条有效授予，不同来源互不吞并、互不覆盖；
 * 用户<b>持有 = 任一来源存在有效授予</b>，展示有效期取各来源中最晚。
 * 同源重复授予只延长有效期（新值更晚才刷新）；撤销仅撤自己来源的一条，
 * 撤后仍有其他来源有效授予时不清佩戴、不发收回通知（引用计数语义）。
 */
@Service
@RequiredArgsConstructor
public class DecorationGrantService {

    private static final Sort GRANT_SORT =
        Sort.by(Sort.Order.desc("metadata.creationTimestamp"));

    /** 资产被物理删除后的显示名占位，避免界面 / 通知出现 asset-xxx 内部名。 */
    private static final String DELETED_ASSET_NAME = "（已删除的装饰）";

    private final ReactiveExtensionClient client;
    private final DecorationProfileService profileService;
    private final DecorationMetadataService metadataService;
    private final InteractionPlusNotificationService notificationService;

    /**
     * 单实例内按 userName 串行化授予临界区（检查 → 写入），消除同实例并发授予的检查竞态。
     * Halo 为单实例内存索引架构，无跨实例并发；万一出现冗余授予，展示层（Public API 仅取有效授予）
     * 与库存按装饰聚合均会去重，不影响用户可见结果。
     */
    private final ConcurrentHashMap<String, Mono<Void>> userGrantTails = new ConcurrentHashMap<>();

    // ───────────────────────── 查询 ─────────────────────────

    /**
     * 分页查询授予记录，服务端聚合装饰显示名（仅针对当页记录批量取资产）。
     */
    public Mono<ListResult<GrantView>> listGrants(ServerWebExchange exchange) {
        var params = exchange.getRequest().getQueryParams();
        List<Condition> conditions = new ArrayList<>();
        conditions.add(isNull("metadata.deletionTimestamp"));
        addEqualIfPresent(conditions, "spec.userName", params.getFirst("userName"));
        addEqualIfPresent(conditions, "spec.assetName", params.getFirst("assetName"));
        addEqualIfPresent(conditions, "spec.sourceRoleName", params.getFirst("sourceRoleName"));
        String revoked = params.getFirst("revoked");
        if (StringUtils.hasText(revoked)) {
            conditions.add(equal("spec.revoked", Boolean.parseBoolean(revoked)));
        }
        Condition[] rest = conditions.subList(1, conditions.size()).toArray(Condition[]::new);
        var options = ListOptions.builder().fieldQuery(and(conditions.get(0), rest)).build();
        return client.listBy(UserDecorationGrant.class, options,
                ListRequestSupport.toPageRequest(exchange, GRANT_SORT))
            .flatMap(this::toGrantViews);
    }

    private Mono<ListResult<GrantView>> toGrantViews(ListResult<UserDecorationGrant> result) {
        var now = Instant.now();
        var assetNames = result.getItems().stream()
            .map(grant -> grant.getSpec().getAssetName())
            .distinct()
            .toList();
        var roleNames = result.getItems().stream()
            .map(grant -> grant.getSpec().getSourceRoleName())
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        var userNames = result.getItems().stream()
            .map(grant -> grant.getSpec().getUserName())
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        return Mono.zip(profileService.loadAssets(assetNames), loadRoleDisplayNames(roleNames),
                loadUsers(userNames))
            .map(tuple -> {
                var assetMap = tuple.getT1();
                var roleDisplayNames = tuple.getT2();
                var userMap = tuple.getT3();
                var views = result.getItems().stream()
                    .map(grant -> {
                        var assetName = grant.getSpec().getAssetName();
                        var asset = assetMap.get(assetName);
                        var view = new GrantView(grant,
                            asset != null ? asset.getSpec().getDisplayName() : DELETED_ASSET_NAME,
                            asset != null ? asset.getSpec().getType() : null);
                        view.setStatus(computeStatus(grant, now));
                        if (asset != null) {
                            // 素材与扩展数据供列表缩略真实渲染（称号 / 昵称样式）
                            view.setAsset(asset.getSpec().getAsset());
                            view.setPayload(asset.getSpec().getPayload());
                        }
                        var roleName = grant.getSpec().getSourceRoleName();
                        if (StringUtils.hasText(roleName)) {
                            view.setSourceRoleDisplayName(
                                roleDisplayNames.getOrDefault(roleName, roleName));
                        }
                        // 用户显示名 / 头像（界面优先展示显示名，回退用户名）
                        var user = userMap.get(grant.getSpec().getUserName());
                        view.setUserDisplayName(
                            userDisplayName(userMap, grant.getSpec().getUserName()));
                        if (user != null) {
                            view.setUserAvatar(user.getSpec().getAvatar());
                        }
                        return view;
                    })
                    .toList();
                return new ListResult<>(result.getPage(), result.getSize(),
                    result.getTotal(), views);
            });
    }

    /** 当页用户名去重后批量取 User（用于显示名 / 头像聚合）。 */
    private Mono<Map<String, User>> loadUsers(List<String> userNames) {
        return Flux.fromIterable(userNames)
            .flatMap(userName -> client.fetch(User.class, userName)
                .map(user -> Map.entry(userName, user)))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /** 当页角色名去重后批量取显示名（角色已删除时回退内部名）。 */
    private Mono<Map<String, String>> loadRoleDisplayNames(List<String> roleNames) {
        return Flux.fromIterable(roleNames)
            .flatMap(roleName -> client.fetch(Role.class, roleName)
                .map(role -> Map.entry(roleName, RoleUtils.displayName(role))))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /** 服务端统一判定授予状态，避免客户端时钟偏差。 */
    private String computeStatus(UserDecorationGrant grant, Instant now) {
        return switch (grant.stateAt(now)) {
            case REVOKED -> GrantView.STATUS_REVOKED;
            case EXPIRED -> GrantView.STATUS_EXPIRED;
            case ACTIVE -> GrantView.STATUS_ACTIVE;
        };
    }

    // ───────────────────────── 授予 ─────────────────────────

    /**
     * 单 / 多用户、单 / 多装饰授予。
     */
    public Mono<BatchGrantResult> grant(GrantParam param) {
        var userNames = distinct(param == null ? null : param.getUserNames());
        var assetNames = distinct(param == null ? null : param.getAssetNames());
        if (userNames.isEmpty() || assetNames.isEmpty()) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "用户与装饰均不能为空。");
        }
        String reason = param.getReason();
        Instant expiresAt = param.getExpiresAt();
        validateExpiresAt(expiresAt);
        return SecurityUtils.getCurrentUserName().flatMap(operator ->
            profileService.loadAssets(assetNames).flatMap(assetMap -> {
                List<Pair> pairs = new ArrayList<>();
                for (String user : userNames) {
                    for (String asset : assetNames) {
                        pairs.add(new Pair(user, asset, null));
                    }
                }
                return processPairs(pairs, assetMap, UserDecorationGrant.GRANT_TYPE_MANUAL,
                    reason, expiresAt, operator);
            }));
    }

    /**
     * 按 Halo 角色快照授予。一次性快照，不做动态绑定。
     */
    public Mono<BatchGrantResult> grantByRoleSnapshot(RoleGrantParam param) {
        var roleNames = distinct(param == null ? null : param.getRoleNames());
        var assetNames = distinct(param == null ? null : param.getAssetNames());
        if (roleNames.isEmpty() || assetNames.isEmpty()) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "角色与装饰均不能为空。");
        }
        String reason = param.getReason();
        Instant expiresAt = param.getExpiresAt();
        validateExpiresAt(expiresAt);
        return SecurityUtils.getCurrentUserName().flatMap(operator ->
            validateRolesExist(roleNames)
                .then(profileService.loadAssets(assetNames))
                .flatMap(assetMap -> buildRolePairs(roleNames, assetNames)
                    .flatMap(pairs -> processPairs(pairs, assetMap,
                        UserDecorationGrant.GRANT_TYPE_ROLE_SNAPSHOT, reason, expiresAt,
                        operator))));
    }

    /** 有效期非空时必须晚于当前时间（与前端同规则；API 直调同样拦截，避免产出生来即过期的授予）。 */
    private static void validateExpiresAt(Instant expiresAt) {
        if (!isFutureOrPermanent(expiresAt)) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "有效期必须晚于当前时间。");
        }
    }

    /** 有效期谓词：null = 永久，非空必须晚于当前时间。后台与外部发放共用同一规则。 */
    private static boolean isFutureOrPermanent(Instant expiresAt) {
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }

    /**
     * 展开「角色 × 装饰」为授予对列表。RoleBinding 全表只读一次、
     * 全部角色共用（逐角色各扫一遍全表在大站点下成本随用户数放大）。
     */
    private Mono<List<Pair>> buildRolePairs(List<String> roleNames, List<String> assetNames) {
        var options = ListOptions.builder()
            .fieldQuery(isNull("metadata.deletionTimestamp"))
            .build();
        return client.listAll(RoleBinding.class, options,
                Sort.by(Sort.Order.asc("metadata.name")))
            .collectList()
            .map(bindings -> {
                List<Pair> pairs = new ArrayList<>();
                for (String roleName : roleNames) {
                    var members = bindings.stream()
                        .filter(binding -> binding.getRoleRef() != null
                            && roleName.equals(binding.getRoleRef().getName()))
                        .flatMap(binding -> binding.getSubjects() == null
                            ? java.util.stream.Stream.<RoleBinding.Subject>empty()
                            : binding.getSubjects().stream())
                        .filter(subject -> User.KIND.equals(subject.getKind()))
                        .map(RoleBinding.Subject::getName)
                        .distinct()
                        .toList();
                    for (String userName : members) {
                        for (String asset : assetNames) {
                            pairs.add(new Pair(userName, asset, roleName));
                        }
                    }
                }
                return pairs;
            });
    }

    private Mono<BatchGrantResult> processPairs(List<Pair> pairs, Map<String, UserDecorationAsset> assets,
        String grantType, String reason, Instant expiresAt, String operator) {
        return Flux.fromIterable(pairs)
            .concatMap(pair -> processOne(pair, assets, grantType, reason, expiresAt, operator))
            .collectList()
            .flatMap(outcomes -> buildResultAndNotify(outcomes, assets, operator));
    }

    private Mono<Outcome> processOne(Pair pair, Map<String, UserDecorationAsset> assets,
        String grantType, String reason, Instant expiresAt, String operator) {
        var asset = assets.get(pair.asset());
        if (asset == null) {
            return Mono.just(Outcome.failed(pair, ErrorCodes.ASSET_NOT_FOUND));
        }
        if (!asset.isActive()) {
            return Mono.just(Outcome.failed(pair, ErrorCodes.ASSET_NOT_ACTIVE));
        }
        // 检查 → 写入 → 复核整段按用户串行化，避免同实例并发授予互相穿插
        return serializeByUser(pair.user(),
            findActiveConsoleGrant(pair.user(), pair.asset())
                .flatMap(existing -> renewOrSkipManual(existing, pair, expiresAt))
                .switchIfEmpty(Mono.defer(() ->
                    createAfterRecheck(pair, grantType, reason, expiresAt, operator))));
    }

    /**
     * 临界区内的新建路径。入库前复核资产仍存在且启用——批量入口预载的资产快照可能在
     * 排队期间失效（如并发删除资产触发级联撤销），复核避免产出指向已删除 / 停用资产的授予。
     * 再查任一来源是否已持有：已持有则照常新建（多来源并存），但标记抑制「获得装饰」通知，
     * 跨来源补发不重复打扰用户。
     */
    private Mono<Outcome> createAfterRecheck(Pair pair, String grantType, String reason,
        Instant expiresAt, String operator) {
        return client.fetch(UserDecorationAsset.class, pair.asset())
            .flatMap(asset -> {
                if (!asset.isActive()) {
                    return Mono.just(Outcome.failed(pair, ErrorCodes.ASSET_NOT_ACTIVE));
                }
                return listActiveGrants(pair.user(), pair.asset()).hasElements()
                    .flatMap(heldBefore ->
                        createGrant(pair.user(), pair.asset(), grantType, pair.roleName(),
                            null, reason, expiresAt, operator)
                            .map(grant -> Outcome.granted(pair,
                                grant.getMetadata().getName(), heldBefore)));
            })
            .defaultIfEmpty(Outcome.failed(pair, ErrorCodes.ASSET_NOT_FOUND))
            .onErrorResume(error -> Mono.just(Outcome.failed(pair, "GRANT_ERROR")));
    }

    /**
     * 已持有后台来源的有效授予时的幂等分支：新有效期更晚（含升格永久）才续期，
     * **只延长不缩短**（缩短 / 收回走撤销再授予），否则跳过。
     * 外部插件的授予按来源独立并存，不出现在本路径（后台授予与其互不影响）。
     */
    private Mono<Outcome> renewOrSkipManual(UserDecorationGrant existing, Pair pair,
        Instant expiresAt) {
        var spec = existing.getSpec();
        if (!extendsValidity(spec.getExpiresAt(), expiresAt)) {
            return Mono.just(Outcome.skipped(pair, ErrorCodes.GRANT_ALREADY_EXISTS));
        }
        spec.setExpiresAt(expiresAt);
        return client.update(existing)
            .map(updated -> Outcome.renewed(pair, updated.getMetadata().getName()))
            .onErrorResume(error -> Mono.just(Outcome.failed(pair, "GRANT_ERROR")));
    }

    /**
     * 单实例内按 userName 把任务串成链表（前一个完成后才执行下一个）。
     * 任务结束后若自己仍是链尾则清理映射，避免内存泄漏。
     *
     * <p>已知限制：排队中的任务被取消（如 HTTP 连接中断）时，doFinally 会立即放行链上
     * 后继任务，极端时序下后继可能与仍在执行的前驱并发。Console 低并发场景实际影响可忽略，
     * 有意不为此引入取消隔离的复杂度；若未来出现高并发授予入口需一并重审。
     */
    private <T> Mono<T> serializeByUser(String userName, Mono<T> task) {
        return Mono.defer(() -> {
            var release = Sinks.<Void>empty();
            Mono<Void> myTail = release.asMono();
            Mono<Void> prevTail = userGrantTails.put(userName, myTail);
            Mono<T> guarded = prevTail == null ? task : prevTail.then(task);
            return guarded.doFinally(signal -> {
                release.tryEmitEmpty();
                userGrantTails.remove(userName, myTail);
            });
        });
    }

    /**
     * 新建一条授予记录（后台与外部发放共用）：后台路径传 sourceRoleName / grantedBy、
     * sourcePlugin 为 null；外部路径相反。setter 对 null 与不设置等价。
     */
    private Mono<UserDecorationGrant> createGrant(String userName, String assetName,
        String grantType, String sourceRoleName, String sourcePlugin, String reason,
        Instant expiresAt, String grantedBy) {
        var grant = new UserDecorationGrant();
        var metadata = new Metadata();
        metadata.setName(NameGenerator.generate("grant"));
        grant.setMetadata(metadata);
        var spec = new UserDecorationGrant.Spec();
        spec.setUserName(userName);
        spec.setAssetName(assetName);
        spec.setGrantType(grantType);
        spec.setSourceRoleName(sourceRoleName);
        spec.setSourcePlugin(sourcePlugin);
        spec.setReason(reason);
        spec.setExpiresAt(expiresAt);
        spec.setRevoked(false);
        spec.setGrantedBy(grantedBy);
        spec.setGrantedAt(Instant.now());
        grant.setSpec(spec);
        return client.create(grant);
    }

    private Mono<BatchGrantResult> buildResultAndNotify(List<Outcome> outcomes,
        Map<String, UserDecorationAsset> assets, String operator) {
        var userNames = outcomes.stream()
            .map(outcome -> outcome.pair().user())
            .distinct()
            .toList();
        return loadUsers(userNames).flatMap(userMap -> {
            var result = new BatchGrantResult();
            for (Outcome outcome : outcomes) {
                var user = outcome.pair().user();
                var asset = outcome.pair().asset();
                var userDisplay = userDisplayName(userMap, user);
                if (outcome.status() == OutcomeStatus.FAILED) {
                    var failure = new BatchGrantResult.GrantFailure(user, asset, outcome.reason());
                    failure.setUserDisplayName(userDisplay);
                    result.getFailed().add(failure);
                    continue;
                }
                // SKIPPED 的 grantName 本身即为 null
                var item = new BatchGrantResult.GrantItem(user, asset, outcome.grantName());
                item.setUserDisplayName(userDisplay);
                switch (outcome.status()) {
                    case GRANTED -> result.getGranted().add(item);
                    case RENEWED -> result.getRenewed().add(item);
                    case SKIPPED -> result.getSkipped().add(item);
                    default -> throw new IllegalStateException("未知授予结果：" + outcome.status());
                }
            }
            // 按用户合并“获得装饰”通知；跨来源补发（新建前任一来源已持有）不重复通知
            Map<String, List<String>> grantedByUser = outcomes.stream()
                .filter(o -> o.status() == OutcomeStatus.GRANTED && !o.heldBefore())
                .collect(Collectors.groupingBy(o -> o.pair().user(),
                    Collectors.mapping(o -> displayName(assets, o.pair().asset()),
                        Collectors.toList())));
            // 「批量授予完成」仅在真正批量（多于 1 条）时通知操作者，
            // 单条授予的结果界面已即时反馈，避免通知轰炸
            Mono<Void> notifyOperator = outcomes.size() > 1
                ? notificationService.notifyBatchGrantCompleted(operator,
                    result.getSuccessCount() + result.getRenewedCount(),
                    result.getSkippedCount(), result.getFailedCount())
                : Mono.empty();
            return Flux.fromIterable(grantedByUser.entrySet())
                .concatMap(entry ->
                    notificationService.notifyDecorationsGranted(entry.getKey(), entry.getValue()))
                .then(notifyOperator)
                .thenReturn(result);
        });
    }

    /** 用户显示名解析：有显示名用显示名，否则回退用户名。 */
    private String userDisplayName(Map<String, User> userMap, String userName) {
        var user = userMap.get(userName);
        if (user != null && StringUtils.hasText(user.getSpec().getDisplayName())) {
            return user.getSpec().getDisplayName();
        }
        return userName;
    }

    // ───────────────────────── 撤销 ─────────────────────────

    public Mono<UserDecorationGrant> revoke(String grantName, RevokeParam param) {
        String reason = param == null ? null : param.getReason();
        return SecurityUtils.getCurrentUserName().flatMap(operator ->
            client.fetch(UserDecorationGrant.class, grantName)
                .switchIfEmpty(Mono.error(InteractionPlusException.notFound(
                    ErrorCodes.GRANT_NOT_FOUND, "授予记录不存在", "授予记录不存在或已被删除。")))
                // 撤销 + 引用计数检查须与授予 / 其他撤销在同用户维度串行（并发撤销两条时，
                // 双方都可能看到"对方仍有效"而漏清佩戴）；串行区内重取最新版，避免与并发续期的乐观锁冲突
                .flatMap(grant -> serializeByUser(grant.getSpec().getUserName(),
                    client.fetch(UserDecorationGrant.class, grantName)
                        .flatMap(latest -> {
                            if (Boolean.TRUE.equals(latest.getSpec().getRevoked())) {
                                // 幂等：已撤销直接返回
                                return Mono.just(latest);
                            }
                            // 撤销一条本已过期的授予：用户早已失去装饰，不再补发「收回」通知
                            boolean wasActive = latest.isActiveAt(Instant.now());
                            latest.markRevoked(operator, reason);
                            String userName = latest.getSpec().getUserName();
                            String assetName = latest.getSpec().getAssetName();
                            return client.update(latest)
                                .flatMap(updated ->
                                    cleanupAfterRevoke(userName, assetName, reason, wasActive)
                                        .thenReturn(updated));
                        }))));
    }

    /**
     * 后台「全部撤销」：撤销该用户该装饰<b>全部来源</b>的有效授予（管理员收回语义，
     * 含外部插件发放的），全部撤完后统一清理佩戴并发一次收回通知。
     * 返回实际撤销条数（0 = 本就无有效授予，幂等）。
     */
    public Mono<Long> revokeAllHeld(RevokeHeldParam param) {
        String userName = param == null ? null : param.getUserName();
        String assetName = param == null ? null : param.getAssetName();
        if (!StringUtils.hasText(userName) || !StringUtils.hasText(assetName)) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "用户与装饰均不能为空。");
        }
        String reason = param.getReason();
        return SecurityUtils.getCurrentUserName().flatMap(operator ->
            serializeByUser(userName, listActiveGrants(userName, assetName)
                .concatMap(grant -> {
                    grant.markRevoked(operator, reason);
                    return client.update(grant);
                })
                .count()
                // 临界区内已撤销全部来源，无需再复查引用计数，直接清佩戴 + 发一次收回通知
                .flatMap(count -> count == 0 ? Mono.just(0L)
                    : profileService.removeAssetFromProfile(userName, assetName)
                        .then(notifyRevoked(userName, assetName, reason))
                        .thenReturn(count))));
    }

    private Mono<Void> notifyRevoked(String userName, String assetName, String reason) {
        return client.fetch(UserDecorationAsset.class, assetName)
            .map(asset -> asset.getSpec().getDisplayName())
            .defaultIfEmpty(DELETED_ASSET_NAME)
            .flatMap(displayName ->
                notificationService.notifyDecorationRevoked(userName, displayName, reason));
    }

    // ───────────────────────── 辅助 ─────────────────────────

    // ── 对外发奖 API 逻辑（供 DecorationGrantApiImpl 调用，按 decorationName + sourcePlugin） ──

    /**
     * 列出当前可被外部发放的装饰：仅 active + 所属稀有度允许外部发放；可按分类筛选。
     * 预载稀有度 map 聚合显示名避免 N+1，与 grant 的可发判定一致。
     */
    public Flux<GrantableDecoration> listGrantable(
        String categoryName) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(equal("spec.status", DecorationStatus.ACTIVE.getValue()));
        conditions.add(isNull("metadata.deletionTimestamp"));
        if (StringUtils.hasText(categoryName)) {
            conditions.add(equal("spec.categoryName", categoryName));
        }
        Condition[] rest = conditions.subList(1, conditions.size()).toArray(Condition[]::new);
        var options = ListOptions.builder().fieldQuery(and(conditions.get(0), rest)).build();
        return metadataService.loadRarityMap().flatMapMany(rarityMap ->
            client.listAll(UserDecorationAsset.class, options,
                    Sort.by(Sort.Order.desc("metadata.creationTimestamp")))
                .filter(asset -> rarityAllows(asset.getSpec().getRarityName(), rarityMap))
                .map(asset -> toGrantable(asset, rarityMap)));
    }

    /**
     * 外部插件发放：按装饰 name 指定，依次校验装饰存在 / 启用、稀有度允许外部发放、用户存在后幂等发放。
     * grantType 记为 {@code external} 并记录 {@code sourcePlugin}。授予按来源并存：仅查<b>自己来源</b>
     * 的有效授予——有则按「只延长」规则续期（见 {@link #renewOrSkip}），无则新建；
     * 他源（其他插件 / 后台）是否发放过与本次无关。
     */
    public Mono<GrantResult> externalGrant(String userName,
        String decorationName, String sourcePlugin, Instant expiresAt, String reason) {
        if (!StringUtils.hasText(userName) || !StringUtils.hasText(decorationName)
            || !StringUtils.hasText(sourcePlugin)) {
            return Mono.error(new IllegalArgumentException(
                "userName / decorationName / sourcePlugin 均不能为空"));
        }
        if (!isFutureOrPermanent(expiresAt)) {
            return Mono.error(new IllegalArgumentException(
                "expiresAt 必须晚于当前时间（为空表示永久）"));
        }
        return client.fetch(UserDecorationAsset.class, decorationName)
            .flatMap(asset -> {
                if (!asset.isActive()) {
                    return Mono.just(GrantResult.of(
                        GrantResult.Status.DECORATION_INACTIVE));
                }
                return rarityAllowsExternal(asset.getSpec().getRarityName()).flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.just(GrantResult.of(
                            GrantResult.Status.DECORATION_NOT_EXTERNALLY_GRANTABLE));
                    }
                    return client.fetch(User.class, userName).hasElement().flatMap(userExists -> {
                        if (!userExists) {
                            return Mono.just(GrantResult.of(
                                GrantResult.Status.USER_NOT_FOUND));
                        }
                        return serializeByUser(userName,
                            findActiveGrantOfSource(userName, decorationName, sourcePlugin)
                                .flatMap(own -> renewOrSkip(own, expiresAt))
                                .switchIfEmpty(Mono.defer(() ->
                                    createExternalAfterRecheck(userName, decorationName,
                                        sourcePlugin, expiresAt, reason))));
                    });
                });
            })
            .switchIfEmpty(Mono.just(GrantResult.of(
                GrantResult.Status.DECORATION_NOT_FOUND)));
    }

    /**
     * 外部发放临界区内的新建路径。入库前复核装饰仍存在且启用——临界区外的校验可能已被
     * 并发的资产删除 / 停用穿插失效；再查任一来源是否已持有：已持有则照常新建（多来源并存），
     * 但不再发「获得装饰」通知（跨来源补发不重复打扰用户）。
     */
    private Mono<GrantResult> createExternalAfterRecheck(
        String userName, String decorationName, String sourcePlugin, Instant expiresAt,
        String reason) {
        return client.fetch(UserDecorationAsset.class, decorationName)
            .flatMap(asset -> {
                if (!asset.isActive()) {
                    return Mono.just(GrantResult.of(
                        GrantResult.Status.DECORATION_INACTIVE));
                }
                String displayName = asset.getSpec().getDisplayName();
                return listActiveGrants(userName, decorationName).hasElements()
                    .flatMap(heldBefore ->
                        createGrant(userName, decorationName,
                            UserDecorationGrant.GRANT_TYPE_EXTERNAL, null, sourcePlugin,
                            reason, expiresAt, null)
                            .flatMap(grant -> (heldBefore ? Mono.<Void>empty()
                                : notificationService.notifyDecorationsGranted(userName,
                                    List.of(displayName)))
                                .thenReturn(GrantResult
                                    .granted(grant.getMetadata().getName()))));
            })
            .defaultIfEmpty(GrantResult.of(
                GrantResult.Status.DECORATION_NOT_FOUND));
    }

    /**
     * 已持有自己来源的有效授予时的幂等分支：新有效期更晚（含升格永久）才续期，
     * **只延长不缩短**（缩短 / 收回走 revoke），否则 ALREADY_HELD。
     */
    private Mono<GrantResult> renewOrSkip(
        UserDecorationGrant own, Instant expiresAt) {
        String grantName = own.getMetadata().getName();
        if (!extendsValidity(own.getSpec().getExpiresAt(), expiresAt)) {
            return Mono.just(GrantResult.alreadyHeld(grantName));
        }
        own.getSpec().setExpiresAt(expiresAt);
        return client.update(own)
            .thenReturn(GrantResult.renewed(grantName));
    }

    /**
     * 新有效期是否比现有更晚（null = 永久 = 最大）。续期只延长不缩短：
     * 取更晚值对重复 / 乱序重试天然免疫（结果单调收敛到最长有效期）。
     */
    private static boolean extendsValidity(Instant current, Instant proposed) {
        if (current == null) {
            return false;
        }
        return proposed == null || proposed.isAfter(current);
    }

    /**
     * 外部插件撤销：仅撤销由相同 {@code sourcePlugin} 发放、当前有效的那一条。
     * 自己无可撤时：他源仍有有效授予返回 FORBIDDEN_NOT_OWNER（不可越权），全无则 NOT_HELD。
     * 撤销后用户仍持有其他来源的有效授予（多来源并存）时，不清佩戴、不发收回通知。
     */
    public Mono<RevokeResult> externalRevoke(String userName,
        String decorationName, String sourcePlugin, String reason) {
        if (!StringUtils.hasText(userName) || !StringUtils.hasText(decorationName)
            || !StringUtils.hasText(sourcePlugin)) {
            return Mono.error(new IllegalArgumentException(
                "userName / decorationName / sourcePlugin 均不能为空"));
        }
        return serializeByUser(userName,
            findActiveGrantOfSource(userName, decorationName, sourcePlugin)
                .flatMap(own -> {
                    // revokedBy 语义为操作者用户名，外部来源暂无操作者概念（sourcePlugin 已在同记录）
                    own.markRevoked(null, reason);
                    return client.update(own)
                        .then(cleanupAfterRevoke(userName, decorationName, reason, true))
                        .thenReturn(RevokeResult.of(
                            RevokeResult.Status.REVOKED));
                })
                .switchIfEmpty(Mono.defer(() ->
                    listActiveGrants(userName, decorationName).hasElements()
                        .map(othersHold -> RevokeResult.of(
                            othersHold
                                ? RevokeResult.Status.FORBIDDEN_NOT_OWNER
                                : RevokeResult.Status.NOT_HELD)))));
    }

    /** 稀有度是否允许外发（map 版，listGrantable 用）：无稀有度 / 已删除默认允许，仅显式 false 拒绝。 */
    private boolean rarityAllows(String rarityName, Map<String, UserDecorationRarity> rarityMap) {
        if (!StringUtils.hasText(rarityName)) {
            return true;
        }
        var rarity = rarityMap.get(rarityName);
        return rarity == null || !Boolean.FALSE.equals(rarity.getSpec().getExternalGrantable());
    }

    private GrantableDecoration toGrantable(
        UserDecorationAsset asset, Map<String, UserDecorationRarity> rarityMap) {
        var spec = asset.getSpec();
        String rarityName = spec.getRarityName();
        String rarityDisplay = null;
        if (StringUtils.hasText(rarityName)) {
            var rarity = rarityMap.get(rarityName);
            rarityDisplay = rarity != null ? rarity.getSpec().getDisplayName() : null;
        }
        return new GrantableDecoration(
            asset.getMetadata().getName(), spec.getDisplayName(), spec.getType(),
            rarityName, rarityDisplay, spec.getCategoryName());
    }

    /** 稀有度是否允许外部发放：无稀有度 / 稀有度已删除均默认允许；仅显式 false 才拒绝。 */
    private Mono<Boolean> rarityAllowsExternal(String rarityName) {
        if (!StringUtils.hasText(rarityName)) {
            return Mono.just(true);
        }
        return client.fetch(UserDecorationRarity.class, rarityName)
            .map(rarity -> !Boolean.FALSE.equals(rarity.getSpec().getExternalGrantable()))
            .defaultIfEmpty(true);
    }

    /** 该用户该装饰当前全部有效授予（任意来源；未撤销、未过期）。多来源并存的基础查询。 */
    private Flux<UserDecorationGrant> listActiveGrants(String userName, String assetName) {
        var now = Instant.now();
        var options = ListOptions.builder()
            .fieldQuery(and(equal("spec.userName", userName),
                equal("spec.assetName", assetName),
                equal("spec.revoked", false),
                isNull("metadata.deletionTimestamp")))
            .build();
        return client.listAll(UserDecorationGrant.class, options,
                Sort.by(Sort.Order.asc("metadata.name")))
            .filter(grant -> grant.isActiveAt(now));
    }

    /** 指定外部来源（sourcePlugin）的有效授予，无则空。候选集极小，内存过滤即可。 */
    private Mono<UserDecorationGrant> findActiveGrantOfSource(String userName, String assetName,
        String sourcePlugin) {
        return listActiveGrants(userName, assetName)
            .filter(grant -> sourcePlugin.equals(grant.getSpec().getSourcePlugin()))
            .next();
    }

    /** 后台来源（手动 / 角色快照，非 external）的有效授予，无则空。 */
    private Mono<UserDecorationGrant> findActiveConsoleGrant(String userName, String assetName) {
        return listActiveGrants(userName, assetName)
            .filter(grant -> !UserDecorationGrant.GRANT_TYPE_EXTERNAL
                .equals(grant.getSpec().getGrantType()))
            .next();
    }

    /**
     * 撤销一条授予后的佩戴清理与收回通知：用户仍持有其他来源的有效授予（多来源并存）时
     * 两者均跳过——失去装扮以「全部有效授予耗尽」为准（引用计数语义）。
     * {@code notify=false} 用于撤销一条本已过期的授予：用户早已因过期失去装饰，
     * 只做幂等清理、不再补发「收回」通知。
     */
    private Mono<Void> cleanupAfterRevoke(String userName, String assetName, String reason,
        boolean notify) {
        return listActiveGrants(userName, assetName).hasElements()
            .flatMap(stillHeld -> stillHeld ? Mono.empty()
                : profileService.removeAssetFromProfile(userName, assetName)
                    .then(notify ? notifyRevoked(userName, assetName, reason) : Mono.empty()));
    }

    private Mono<Void> validateRolesExist(List<String> roleNames) {
        return Flux.fromIterable(roleNames)
            .concatMap(roleName -> client.fetch(Role.class, roleName)
                .switchIfEmpty(Mono.error(InteractionPlusException.badRequest(
                    ErrorCodes.ROLE_NOT_FOUND, "角色不存在", "Halo 角色不存在：" + roleName))))
            .then();
    }

    private String displayName(Map<String, UserDecorationAsset> assets, String assetName) {
        var asset = assets.get(assetName);
        return asset != null ? asset.getSpec().getDisplayName() : DELETED_ASSET_NAME;
    }

    private void addEqualIfPresent(List<Condition> conditions, String field, String value) {
        if (StringUtils.hasText(value)) {
            conditions.add(equal(field, value));
        }
    }

    private List<String> distinct(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(values.stream()
            .filter(StringUtils::hasText)
            .toList()));
    }

    private record Pair(String user, String asset, String roleName) {
    }

    private enum OutcomeStatus {
        GRANTED, RENEWED, SKIPPED, FAILED
    }

    /** heldBefore：新建前任一来源已持有（跨来源补发），用于抑制重复的「获得装饰」通知。 */
    private record Outcome(Pair pair, OutcomeStatus status, String grantName, String reason,
        boolean heldBefore) {
        static Outcome granted(Pair pair, String grantName, boolean heldBefore) {
            return new Outcome(pair, OutcomeStatus.GRANTED, grantName, null, heldBefore);
        }

        static Outcome renewed(Pair pair, String grantName) {
            return new Outcome(pair, OutcomeStatus.RENEWED, grantName, null, false);
        }

        static Outcome skipped(Pair pair, String reason) {
            return new Outcome(pair, OutcomeStatus.SKIPPED, null, reason, false);
        }

        static Outcome failed(Pair pair, String reason) {
            return new Outcome(pair, OutcomeStatus.FAILED, null, reason, false);
        }
    }
}
