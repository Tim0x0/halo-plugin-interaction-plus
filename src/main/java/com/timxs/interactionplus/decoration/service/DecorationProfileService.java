package com.timxs.interactionplus.decoration.service;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.timxs.interactionplus.decoration.constants.DecorationType;
import com.timxs.interactionplus.decoration.constants.EquipSlot;
import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationGrant;
import com.timxs.interactionplus.decoration.extension.UserDecorationProfile;
import com.timxs.interactionplus.decoration.model.InvalidEquipItem;
import com.timxs.interactionplus.decoration.model.InventoryItem;
import com.timxs.interactionplus.decoration.model.ProfileSaveParam;
import com.timxs.interactionplus.decoration.model.ProfileView;
import com.timxs.interactionplus.core.support.NameGenerator;
import com.timxs.interactionplus.identity.service.PublicIdentityService;
import com.timxs.interactionplus.identity.support.PublicIdentityCache;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * 用户装扮档案服务：库存查询、当前佩戴读取、佩戴保存与失效清理。
 *
 * <p>保存佩戴仅硬校验展示勋章数量；失效项（过期 / 撤销 / 停用等）不阻止保存，
 * 持久化后由 UC 读取路径标记 invalidItems、公开展示由 Public API 过滤。
 * 使用乐观锁保存，版本冲突返回 {@code PROFILE_CONFLICT}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecorationProfileService {

    private final ReactiveExtensionClient client;
    private final PublicIdentityCache publicIdentityCache;
    private final PublicIdentityService publicIdentityService;

    // ───────────────────────── 库存 ─────────────────────────

    /**
     * 我的装饰库存。基于授予记录列出，标记可用 / 已过期 / 已撤销 / 已下架。
     */
    public Flux<InventoryItem> getInventory(String userName) {
        var now = Instant.now();
        var options = ListOptions.builder()
            .fieldQuery(and(equal("spec.userName", userName), isNull("metadata.deletionTimestamp")))
            .build();
        return client.listAll(UserDecorationGrant.class, options,
                Sort.by(Sort.Order.desc("spec.grantedAt")))
            .collectList()
            .flatMapMany(grants -> {
                var assetNames = grants.stream()
                    .map(grant -> grant.getSpec().getAssetName())
                    .distinct()
                    .toList();
                return loadAssets(assetNames).flatMapMany(assetMap -> {
                    // 按装饰聚合：同一装饰多次授予 / 过期重授只呈现一张卡（衣柜视角），
                    // 历史明细仍可在 Console 授予页按 grant 查看。保持 grantedAt desc 顺序。
                    var byAsset = new LinkedHashMap<String, List<UserDecorationGrant>>();
                    for (var grant : grants) {
                        byAsset.computeIfAbsent(grant.getSpec().getAssetName(),
                            key -> new ArrayList<>()).add(grant);
                    }
                    return Flux.fromIterable(byAsset.entrySet())
                        .map(entry -> aggregateItem(entry.getKey(), entry.getValue(),
                            assetMap, now));
                });
            });
    }

    /**
     * 将同一装饰的多条授予聚合为一个库存项。
     *
     * <p>同一装饰只要存在有效授予，就以有效授予计算库存；资产可用时为可用，
     * 资产不可用时为已下架。没有有效授予时，按最近发生的失效事件判定已撤销 / 已过期，
     * 不能按授予创建时间判定，否则较早授予、较晚撤销的记录会被较新的过期记录遮盖。
     * 有效期取有效授予中最晚到期（任一永久则视为永久）。
     */
    static InventoryItem aggregateItem(String assetName, List<UserDecorationGrant> grants,
        Map<String, UserDecorationAsset> assetMap, Instant now) {
        var item = new InventoryItem();
        item.setAssetName(assetName);

        var asset = assetMap.get(assetName);
        if (asset != null) {
            var assetSpec = asset.getSpec();
            item.setType(assetSpec.getType());
            item.setDisplayName(assetSpec.getDisplayName());
            item.setDescription(assetSpec.getDescription());
            item.setAsset(assetSpec.getAsset());
            item.setPayload(assetSpec.getPayload());
            item.setCategoryName(assetSpec.getCategoryName());
            item.setTagNames(assetSpec.getTagNames());
            item.setRarityName(assetSpec.getRarityName());
        }

        boolean assetActive = asset != null && asset.isActive();
        var activeGrants = grants.stream().filter(grant -> grant.isActiveAt(now)).toList();

        if (!activeGrants.isEmpty()) {
            var representative = latestGranted(activeGrants);
            item.setGrantName(representative.getMetadata().getName());
            item.setGrantedAt(representative.getSpec().getGrantedAt());
            // 有效期：任一永久=永久，否则取最晚到期（与公开装饰墙同源聚合）
            item.setExpiresAt(UserDecorationGrant.effectiveExpiresAt(activeGrants));
            item.setAvailable(assetActive);
            item.setStatus(assetActive
                ? InventoryItem.STATUS_AVAILABLE
                : InventoryItem.STATUS_DISABLED);
        } else {
            var invalidation = latestInvalidation(grants, now);
            var representative = invalidation.grant();
            item.setGrantName(representative.getMetadata().getName());
            item.setAvailable(false);
            item.setGrantedAt(representative.getSpec().getGrantedAt());
            item.setExpiresAt(representative.getSpec().getExpiresAt());
            switch (invalidation.state()) {
                case REVOKED -> item.setStatus(InventoryItem.STATUS_REVOKED);
                case EXPIRED -> item.setStatus(InventoryItem.STATUS_EXPIRED);
                case ACTIVE -> throw new IllegalStateException(
                    "Active grant cannot be selected as an invalidation");
            }
        }
        return item;
    }

    private static UserDecorationGrant latestGranted(List<UserDecorationGrant> grants) {
        return grants.stream()
            .max(Comparator.comparing(
                grant -> grant.getSpec().getGrantedAt(),
                Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElseThrow();
    }

    private static GrantInvalidation latestInvalidation(List<UserDecorationGrant> grants,
        Instant now) {
        return grants.stream()
            .map(grant -> {
                var state = grant.stateAt(now);
                return new GrantInvalidation(grant, state, invalidatedAt(grant, state));
            })
            .filter(invalidation -> invalidation.state() != UserDecorationGrant.State.ACTIVE)
            .max(Comparator
                .comparing(GrantInvalidation::occurredAt,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                // 时间相同优先展示撤销，和 UserDecorationGrant.stateAt 的领域顺序一致。
                .thenComparingInt(invalidation ->
                    invalidation.state() == UserDecorationGrant.State.REVOKED ? 1 : 0))
            .orElseThrow();
    }

    private static Instant invalidatedAt(UserDecorationGrant grant,
        UserDecorationGrant.State state) {
        return switch (state) {
            case REVOKED -> grant.getSpec().getRevokedAt() != null
                ? grant.getSpec().getRevokedAt()
                : grant.getSpec().getGrantedAt();
            case EXPIRED -> grant.getSpec().getExpiresAt();
            case ACTIVE -> null;
        };
    }

    private record GrantInvalidation(UserDecorationGrant grant,
                                     UserDecorationGrant.State state,
                                     Instant occurredAt) {
    }

    // ───────────────────────── 当前佩戴 ─────────────────────────

    /**
     * 读取当前佩戴，并标记失效项；附带当前生效的身份标识（只读展示）。不自动清理（保存时清理）。
     */
    public Mono<ProfileView> getProfile(String userName) {
        return client.fetch(UserDecorationProfile.class, NameGenerator.profileName(userName))
            .flatMap(profile -> buildProfileView(userName, profile.getSpec()))
            .switchIfEmpty(Mono.just(new ProfileView()))
            .flatMap(view -> attachIdentityMarks(userName, view));
    }

    /**
     * ProfileView 统一附带当前生效的身份标识（只读展示）。读取与保存路径共用——
     * 保存响应缺失该字段时，前端以响应回写状态会把已展示的标识清空。
     */
    private Mono<ProfileView> attachIdentityMarks(String userName, ProfileView view) {
        return publicIdentityService.resolveIdentityMarks(userName)
            .doOnNext(view::setIdentityMarks)
            .thenReturn(view);
    }

    private Mono<ProfileView> buildProfileView(String userName, UserDecorationProfile.Spec spec) {
        var view = new ProfileView();
        view.setAvatarFrame(spec.getAvatarFrame());
        view.setTitle(spec.getTitle());
        view.setPrimaryBadge(spec.getPrimaryBadge());
        view.setBadgeShowcase(spec.getBadgeShowcase() == null
            ? new ArrayList<>() : new ArrayList<>(spec.getBadgeShowcase()));
        view.setCardBackground(spec.getCardBackground());
        view.setNameStyle(spec.getNameStyle());
        view.setPublicDecorationsVisible(spec.getPublicDecorationsVisible());

        var slots = collectSlots(spec);
        if (slots.isEmpty()) {
            return Mono.just(view);
        }
        // 佩戴校验所需资产与该用户全部授予各读取一次，并在槽位间共享。
        var now = Instant.now();
        var assetNames = slots.stream().map(SlotEntry::assetName).distinct().toList();
        return Mono.zip(loadAssets(assetNames), loadGrantsByAsset(userName))
            .map(tuple -> {
                var assetMap = tuple.getT1();
                var grantsByAsset = tuple.getT2();
                for (var entry : slots) {
                    var invalid = validateEquip(entry.slot(), entry.assetName(),
                        assetMap.get(entry.assetName()),
                        grantsByAsset.getOrDefault(entry.assetName(), List.of()), now);
                    if (invalid != null) {
                        view.getInvalidItems().add(invalid);
                    }
                }
                return view;
            });
    }

    /** 该用户全部授予记录按资产分组（grantedAt desc），佩戴校验各槽位共享。 */
    private Mono<Map<String, List<UserDecorationGrant>>> loadGrantsByAsset(String userName) {
        var options = ListOptions.builder()
            .fieldQuery(and(equal("spec.userName", userName),
                isNull("metadata.deletionTimestamp")))
            .build();
        return client.listAll(UserDecorationGrant.class, options,
                Sort.by(Sort.Order.desc("spec.grantedAt")))
            .collectList()
            .map(grants -> grants.stream().collect(
                Collectors.groupingBy(grant -> grant.getSpec().getAssetName())));
    }

    // ───────────────────────── 保存佩戴 ─────────────────────────

    /**
     * 保存当前佩戴。失效项（过期/撤销/停用等）**不阻止保存**——按用户选择持久化，
     * 失效项不自动卸载、保留在档案中；展示时由 Public API 过滤、UC 读取时标记（invalidItems）。
     * 仅展示勋章数量超限做硬校验。乐观锁写入，版本冲突返回 PROFILE_CONFLICT。
     */
    public Mono<ProfileView> saveProfile(String userName, ProfileSaveParam param) {
        // 展示勋章去重（保持顺序），防止 API 直调传入重复项
        if (param.getBadgeShowcase() != null) {
            param.setBadgeShowcase(param.getBadgeShowcase().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList());
        }
        var badgeShowcase = param.getBadgeShowcase() == null
            ? List.<String>of() : param.getBadgeShowcase();
        if (badgeShowcase.size() > InteractionPlusConst.BADGE_SHOWCASE_MAX) {
            return Mono.error(InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "展示勋章超限",
                "展示勋章最多 " + InteractionPlusConst.BADGE_SHOWCASE_MAX + " 个。"));
        }
        // 失效项不阻止保存；持久化后由 buildProfileView 标记 invalidItems 供前端展示角标。
        return upsert(userName, spec -> applyParam(spec, userName, param));
    }

    /**
     * 仅更新「公开装扮墙」开关，不影响佩戴槽位。无档案时创建仅含该开关的档案。
     */
    public Mono<ProfileView> updateVisibility(String userName, boolean visible) {
        return upsert(userName, spec -> {
            spec.setPublicDecorationsVisible(visible);
            spec.setUpdatedAt(Instant.now());
        });
    }

    /**
     * 档案 upsert 公共管道：fetch → 应用变更 / 不存在则新建后应用 → 乐观锁冲突转
     * PROFILE_CONFLICT → 清公开缓存 → 返回校验视图。变更内容由 mutator 决定。
     */
    private Mono<ProfileView> upsert(String userName,
        Consumer<UserDecorationProfile.Spec> mutator) {
        var profileName = NameGenerator.profileName(userName);
        return client.fetch(UserDecorationProfile.class, profileName)
            .flatMap(existing -> {
                mutator.accept(existing.getSpec());
                return client.update(existing);
            })
            .switchIfEmpty(Mono.defer(() -> {
                var profile = new UserDecorationProfile();
                var metadata = new Metadata();
                metadata.setName(profileName);
                profile.setMetadata(metadata);
                var spec = new UserDecorationProfile.Spec();
                spec.setUserName(userName);
                mutator.accept(spec);
                profile.setSpec(spec);
                return client.create(profile);
            }))
            .onErrorMap(OptimisticLockingFailureException.class, error ->
                InteractionPlusException.conflict(ErrorCodes.PROFILE_CONFLICT, "佩戴档案冲突",
                    "佩戴档案已被其它操作修改，请刷新后重试。"))
            // 并发首次保存时创建分支撞确定性主键名（profile-<user>），同样归为 409 冲突（重试即自愈）
            .onErrorMap(DuplicateKeyException.class, error ->
                InteractionPlusException.conflict(ErrorCodes.PROFILE_CONFLICT, "佩戴档案冲突",
                    "佩戴档案已被其它操作修改，请刷新后重试。"))
            // 佩戴 / 可见性变更影响公开展示，清理该用户的 Public identity 缓存
            .doOnNext(saved -> publicIdentityCache.evict(userName))
            .flatMap(saved -> buildProfileView(userName, saved.getSpec()))
            .flatMap(view -> attachIdentityMarks(userName, view));
    }

    private void applyParam(UserDecorationProfile.Spec spec, String userName,
        ProfileSaveParam param) {
        spec.setUserName(userName);
        spec.setAvatarFrame(emptyToNull(param.getAvatarFrame()));
        spec.setTitle(emptyToNull(param.getTitle()));
        spec.setPrimaryBadge(emptyToNull(param.getPrimaryBadge()));
        spec.setBadgeShowcase(param.getBadgeShowcase() == null
            ? new ArrayList<>() : new ArrayList<>(param.getBadgeShowcase()));
        spec.setCardBackground(emptyToNull(param.getCardBackground()));
        spec.setNameStyle(emptyToNull(param.getNameStyle()));
        spec.setUpdatedAt(Instant.now());
    }

    // ───────────────────────── 失效清理（撤销 / 失效时调用）─────────────

    /**
     * 从用户当前佩戴档案中移除指定装饰。乐观锁冲突时重读最新版重试（最多 3 次），
     * 仍失败由 Public API 过滤兜底。
     */
    public Mono<Void> removeAssetFromProfile(String userName, String assetName) {
        if (!StringUtils.hasText(userName) || !StringUtils.hasText(assetName)) {
            return Mono.empty();
        }
        // 撤销 / 失效清理时同步清理该用户的 Public identity 缓存
        publicIdentityCache.evict(userName);
        return client.fetch(UserDecorationProfile.class, NameGenerator.profileName(userName))
            .flatMap(profile -> profile.getSpec().removeAsset(assetName)
                ? client.update(profile).then()
                : Mono.<Void>empty())
            // 重订阅会从 fetch 重新读最新版本，仅对乐观锁冲突定向重试
            .retryWhen(Retry.max(3)
                .filter(OptimisticLockingFailureException.class::isInstance))
            .onErrorResume(error -> {
                log.error("清理用户 {} 佩戴槽位中的装饰 {} 失败，将由 Public API 过滤兜底",
                    userName, assetName, error);
                return Mono.empty();
            })
            .then();
    }

    // ───────────────────────── 校验与辅助 ─────────────────────────

    /**
     * 校验某槽位佩戴是否有效（纯内存判定，资产与授予由调用方批量预载），
     * 无效返回明细（含精确原因），有效返回 null。
     * 原因区分：资产不存在 / 已停用 / 类型不符 / 已撤销 / 已过期 / 未拥有。
     */
    private InvalidEquipItem validateEquip(EquipSlot slot, String assetName,
        UserDecorationAsset asset, List<UserDecorationGrant> grants, Instant now) {
        if (asset == null) {
            return new InvalidEquipItem(slot.getValue(), assetName, ErrorCodes.ASSET_NOT_FOUND);
        }
        if (!asset.isActive()) {
            return new InvalidEquipItem(slot.getValue(), assetName, ErrorCodes.ASSET_NOT_ACTIVE);
        }
        if (DecorationType.from(asset.getSpec().getType()) != slot.getAssetType()) {
            return new InvalidEquipItem(slot.getValue(), assetName, ErrorCodes.INVALID_ASSET_TYPE);
        }
        String reason = resolveGrantReason(grants, now);
        return reason == null ? null : new InvalidEquipItem(slot.getValue(), assetName, reason);
    }

    /**
     * 判定用户对某资产的授予状态：有有效授予返回 null（有效）；否则按历史授予返回
     * 精确失效原因——存在未撤销但已过期的记录→已过期；仅有已撤销记录→已撤销；无任何记录→未拥有。
     */
    private String resolveGrantReason(List<UserDecorationGrant> grants, Instant now) {
        if (grants.isEmpty()) {
            return ErrorCodes.DECORATION_NOT_OWNED;
        }
        var states = grants.stream().map(grant -> grant.stateAt(now)).toList();
        if (states.contains(UserDecorationGrant.State.ACTIVE)) {
            return null;
        }
        return states.contains(UserDecorationGrant.State.EXPIRED)
            ? ErrorCodes.GRANT_EXPIRED : ErrorCodes.GRANT_REVOKED;
    }

    Mono<Map<String, UserDecorationAsset>> loadAssets(List<String> assetNames) {
        if (CollectionUtils.isEmpty(assetNames)) {
            return Mono.just(Map.of());
        }
        return Flux.fromIterable(assetNames)
            .flatMap(name -> client.fetch(UserDecorationAsset.class, name)
                .map(asset -> Map.entry(name, asset)))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private List<SlotEntry> collectSlots(UserDecorationProfile.Spec spec) {
        var entries = new ArrayList<SlotEntry>();
        addSlot(entries, EquipSlot.AVATAR_FRAME, spec.getAvatarFrame());
        addSlot(entries, EquipSlot.TITLE, spec.getTitle());
        addSlot(entries, EquipSlot.PRIMARY_BADGE, spec.getPrimaryBadge());
        addSlot(entries, EquipSlot.CARD_BACKGROUND, spec.getCardBackground());
        addSlot(entries, EquipSlot.NAME_STYLE, spec.getNameStyle());
        if (spec.getBadgeShowcase() != null) {
            for (String badge : spec.getBadgeShowcase()) {
                addSlot(entries, EquipSlot.BADGE_SHOWCASE, badge);
            }
        }
        return entries;
    }

    private void addSlot(List<SlotEntry> entries, EquipSlot slot, String assetName) {
        if (StringUtils.hasText(assetName)) {
            entries.add(new SlotEntry(slot, assetName));
        }
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private record SlotEntry(EquipSlot slot, String assetName) {
    }
}
