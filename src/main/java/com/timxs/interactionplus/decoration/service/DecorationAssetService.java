package com.timxs.interactionplus.decoration.service;

import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.timxs.interactionplus.decoration.constants.DecorationStatus;
import com.timxs.interactionplus.decoration.constants.DecorationType;
import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationGrant;
import com.timxs.interactionplus.decoration.model.DecorationAssetParam;
import com.timxs.interactionplus.core.notification.InteractionPlusNotificationService;
import com.timxs.interactionplus.core.setting.InteractionPlusSettingService;
import com.timxs.interactionplus.core.support.ExtensionQuerySupport;
import com.timxs.interactionplus.core.support.ListRequestSupport;
import com.timxs.interactionplus.core.support.NameGenerator;
import com.timxs.interactionplus.core.support.SecurityUtils;
import com.timxs.interactionplus.core.support.SvgSanitizer;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Condition;
import run.halo.app.infra.ExternalLinkProcessor;

/**
 * 装饰资产服务：CRUD、状态流转、启用强校验（含 SVG 安全）、删除保护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecorationAssetService {

    private static final Sort ASSET_SORT =
        Sort.by(Sort.Order.desc("metadata.creationTimestamp"));

    /** 筛选哨兵值：未分类 / 无标签 / 无稀有度。生成的内部名不含下划线，不会冲突。 */
    public static final String FILTER_NONE = "__none__";

    /** 资产删除级联撤销的原因文案（service 即时路径与 Reconciler 兜底路径共用）。 */
    public static final String REASON_ASSET_DELETED = "装饰已删除";

    private final ReactiveExtensionClient client;
    private final ExternalLinkProcessor externalLinkProcessor;
    private final InteractionPlusNotificationService notificationService;
    private final InteractionPlusSettingService settingService;
    private final DecorationProfileService profileService;
    private final Environment environment;

    /** 素材内容读取大小上限（与素材建议上限同量级，防止超大响应占用内存）。 */
    private static final int MATERIAL_MAX_BYTES = 4 * 1024 * 1024;

    /** 昵称颜色格式校验（hex）；仅此字段需要，其余颜色字段由 spec 的 @Schema(pattern) 兜底。 */
    private static final Pattern HEX_COLOR = Pattern.compile(InteractionPlusConst.HEX_COLOR_PATTERN);

    private final WebClient webClient = WebClient.builder()
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MATERIAL_MAX_BYTES))
        .build();

    // ───────────────────────── 查询 ─────────────────────────

    public Mono<ListResult<UserDecorationAsset>> listAssets(ServerWebExchange exchange) {
        return client.listBy(UserDecorationAsset.class, buildAssetListOptions(exchange),
            ListRequestSupport.toPageRequest(exchange, ASSET_SORT));
    }

    public Mono<UserDecorationAsset> getAsset(String name) {
        return client.fetch(UserDecorationAsset.class, name)
            .switchIfEmpty(Mono.error(InteractionPlusException.notFound(
                ErrorCodes.ASSET_NOT_FOUND, "装饰资产不存在", "装饰资产不存在或已被删除。")));
    }

    // ───────────────────────── 创建 ─────────────────────────

    /** 管理员创建（submittedBy 为空）。 */
    public Mono<UserDecorationAsset> createManagedAsset(DecorationAssetParam param) {
        return doCreate(param, false);
    }

    /** 用户投稿创建（submittedBy 为当前用户）；受投稿全局开关控制。 */
    public Mono<UserDecorationAsset> createSubmission(DecorationAssetParam param) {
        return settingService.getSubmissionSetting().flatMap(submission -> {
            if (!submission.isEnabled()) {
                return Mono.error(InteractionPlusException.badRequest(
                    ErrorCodes.VALIDATION_FAILED, "投稿未开放", "管理员已关闭装饰投稿入口。"));
            }
            return doCreate(param, true);
        });
    }

    private Mono<UserDecorationAsset> doCreate(DecorationAssetParam param, boolean asSubmission) {
        validateCreate(param);
        return SecurityUtils.getCurrentUserName().flatMap(currentUser -> {
            var asset = new UserDecorationAsset();
            var metadata = new Metadata();
            metadata.setName(NameGenerator.generate("asset"));
            asset.setMetadata(metadata);

            var spec = new UserDecorationAsset.Spec();
            spec.setType(param.getType());
            spec.setDisplayName(param.getDisplayName());
            spec.setDescription(param.getDescription());
            spec.setStatus(DecorationStatus.DRAFT.getValue());
            spec.setCategoryName(param.getCategoryName());
            spec.setTagNames(param.getTagNames());
            spec.setRarityName(param.getRarityName());
            spec.setAsset(param.getAsset());
            spec.setPayload(param.getPayload());
            spec.setCreatedBy(currentUser);
            spec.setUpdatedBy(currentUser);
            if (asSubmission) {
                spec.setSubmittedBy(currentUser);
            }
            asset.setSpec(spec);
            return client.create(asset);
        });
    }

    // ───────────────────────── 更新 ─────────────────────────

    public Mono<UserDecorationAsset> updateAsset(String name, DecorationAssetParam param) {
        if (param == null || !StringUtils.hasText(param.getDisplayName())) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "显示名称不能为空。");
        }
        validateTagCount(param.getTagNames());
        return getAsset(name)
            .flatMap(asset -> SecurityUtils.getCurrentUserName().flatMap(currentUser -> {
                var spec = asset.getSpec();
                // 类型创建后不可修改，忽略 param.type
                spec.setDisplayName(param.getDisplayName());
                spec.setDescription(param.getDescription());
                spec.setCategoryName(param.getCategoryName());
                spec.setTagNames(param.getTagNames());
                spec.setRarityName(param.getRarityName());
                spec.setAsset(param.getAsset());
                spec.setPayload(param.getPayload());
                spec.setUpdatedBy(currentUser);
                // 已启用资产编辑后仍需满足启用强校验（素材安全、payload 完整），防止绕过
                if (asset.isActive()) {
                    return validateForActivation(asset).then(client.update(asset));
                }
                return client.update(asset);
            }));
    }

    // ───────────────────────── 状态流转 ─────────────────────────

    /** 启用：draft/disabled -&gt; active，启用前强校验素材安全与 payload 完整性。 */
    public Mono<UserDecorationAsset> activate(String name) {
        return getAsset(name).flatMap(asset -> {
            var current = DecorationStatus.from(asset.getSpec().getStatus());
            if (current == null || !current.canTransitionTo(DecorationStatus.ACTIVE)) {
                return invalidTransition(current, DecorationStatus.ACTIVE);
            }
            return validateForActivation(asset)
                .then(Mono.defer(() -> {
                    asset.getSpec().setStatus(DecorationStatus.ACTIVE.getValue());
                    asset.getSpec().setEnabledAt(Instant.now());
                    return client.update(asset);
                }))
                .flatMap(this::notifyEnabledIfSubmission);
        });
    }

    public Mono<UserDecorationAsset> disable(String name) {
        return transition(name, DecorationStatus.DISABLED,
            spec -> spec.setDisabledAt(Instant.now()));
    }

    public Mono<UserDecorationAsset> archive(String name) {
        return transition(name, DecorationStatus.ARCHIVED,
            spec -> spec.setArchivedAt(Instant.now()));
    }

    public Mono<UserDecorationAsset> unarchive(String name) {
        // archived -> disabled
        return transition(name, DecorationStatus.DISABLED,
            spec -> spec.setDisabledAt(Instant.now()));
    }

    private Mono<UserDecorationAsset> transition(String name, DecorationStatus target,
        Consumer<UserDecorationAsset.Spec> timestampSetter) {
        return getAsset(name).flatMap(asset -> {
            var current = DecorationStatus.from(asset.getSpec().getStatus());
            if (current == null || !current.canTransitionTo(target)) {
                return invalidTransition(current, target);
            }
            asset.getSpec().setStatus(target.getValue());
            timestampSetter.accept(asset.getSpec());
            return client.update(asset);
        });
    }

    // ───────────────────────── 删除 ─────────────────────────

    /**
     * 删除资产（级联模式）：任何状态均可删除。
     * 存在有效授予时先撤销全部有效授予并清理对应用户佩戴槽位；
     * 授予历史记录保留（列表回退展示「（已删除的装饰）」占位）。
     * 管理员删除带投稿者的草稿视为驳回，通知投稿者；级联撤销不逐个发撤销通知。
     */
    public Mono<Void> deleteAsset(String name) {
        return SecurityUtils.getCurrentUserName().flatMap(operator ->
            getAsset(name).flatMap(asset ->
                revokeActiveGrants(name, operator)
                    .then(client.delete(asset))
                    .then(notifyDraftRejectedIfNeeded(asset))));
    }

    /** 资产当前有效授予数（前端删除确认提示影响面用）。 */
    public Mono<Long> countActiveGrants(String assetName) {
        var now = Instant.now();
        return client.listAll(UserDecorationGrant.class,
                UserDecorationGrant.activeGrantOptionsForAsset(assetName),
                Sort.by(Sort.Order.asc("metadata.name")))
            .filter(grant -> grant.isActiveAt(now))
            .count();
    }

    /** 级联撤销该资产全部有效授予，并清理对应用户佩戴槽位与缓存。 */
    private Mono<Void> revokeActiveGrants(String assetName, String operator) {
        var now = Instant.now();
        return client.listAll(UserDecorationGrant.class,
                UserDecorationGrant.activeGrantOptionsForAsset(assetName),
                Sort.by(Sort.Order.asc("metadata.name")))
            .filter(grant -> grant.isActiveAt(now))
            .concatMap(grant -> {
                grant.markRevoked(operator, REASON_ASSET_DELETED);
                String userName = grant.getSpec().getUserName();
                return client.update(grant)
                    .then(profileService.removeAssetFromProfile(userName, assetName));
            })
            .then();
    }

    private Mono<Void> notifyDraftRejectedIfNeeded(UserDecorationAsset asset) {
        if (!asset.isDraft()) {
            return Mono.empty();
        }
        String submittedBy = asset.getSpec().getSubmittedBy();
        return StringUtils.hasText(submittedBy)
            ? notificationService.notifyDraftRejected(submittedBy,
                asset.getSpec().getDisplayName())
            : Mono.empty();
    }

    // ───────────────────────── 校验 ─────────────────────────

    private void validateCreate(DecorationAssetParam param) {
        if (param == null) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "请求体不能为空。");
        }
        if (DecorationType.from(param.getType()) == null) {
            throw InteractionPlusException.badRequest(ErrorCodes.INVALID_ASSET_TYPE,
                "装饰类型非法", "装饰类型必须为 " + DecorationType.allValues() + "。");
        }
        if (!StringUtils.hasText(param.getDisplayName())) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "显示名称不能为空。");
        }
        validateTagCount(param.getTagNames());
    }

    private void validateTagCount(List<String> tagNames) {
        if (tagNames != null && tagNames.size() > InteractionPlusConst.ASSET_TAG_MAX) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "单个装饰最多 " + InteractionPlusConst.ASSET_TAG_MAX + " 个标签。");
        }
    }

    /**
     * 启用前强校验：payload 完整性 + 素材可访问 + SVG 安全。
     */
    private Mono<Void> validateForActivation(UserDecorationAsset asset) {
        validatePayload(asset);
        return validateMaterial(asset);
    }

    private void validatePayload(UserDecorationAsset asset) {
        var type = DecorationType.from(asset.getSpec().getType());
        var payload = asset.getSpec().getPayload();
        if (type == DecorationType.TITLE) {
            // titleText 必填（行内展示 + 称号图的 alt / 裂图回落）；图可选。
            if (payload == null || !StringUtils.hasText(payload.getTitleText())) {
                throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                    "称号内容缺失", "称号类装饰必须填写称号名称。");
            }
            // 称号三色不在此校验：三个字段的 spec 上都有 @Schema(pattern)，
            // Halo 写入时按 schema 校验并抛 400，服务层重复一遍没有意义
        } else if (type == DecorationType.NAME_STYLE) {
            var nameStyle = payload == null ? null : payload.getNameStyle();
            if (nameStyle == null || !StringUtils.hasText(nameStyle.getMode())
                || nameStyle.getColors() == null || nameStyle.getColors().isEmpty()) {
                throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                    "昵称样式缺失", "昵称样式必须配置颜色模式与颜色。");
            }
            int colorCount = nameStyle.getColors().size();
            boolean solidOk = "solid".equals(nameStyle.getMode()) && colorCount == 1;
            boolean gradientOk = "gradient".equals(nameStyle.getMode())
                && colorCount >= 2 && colorCount <= 3;
            if (!solidOk && !gradientOk) {
                throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                    "昵称样式非法", "纯色需 1 个颜色，渐变需 2 到 3 个颜色。");
            }
            nameStyle.getColors().forEach(this::validateNameStyleColor);
        }
    }

    /**
     * 昵称颜色是**唯一没有 schema 兜底**的颜色字段，故这一处校验不能省。
     *
     * <p>原因：{@code colors} 是 {@code List<String>}，`@Schema` 的 pattern 加不到数组元素上
     * ——实测生成的 schema 里 items 只有 type / minLength，没有 pattern。而其余颜色字段
     * （称号三色、标签、稀有度、身份标识）都在各自 spec 上带 `@Schema(pattern)`，
     * 由 Halo 在写入时校验，服务层不做重复校验。
     *
     * <p>颜色最终会被拼进前台的 CSS 文本（见 runtime 的 nameStyleCss），
     * 前台还有 safeHexColor 白名单过滤，这里拦的是「脏值落库」。
     */
    private void validateNameStyleColor(String color) {
        if (StringUtils.hasText(color) && !HEX_COLOR.matcher(color).matches()) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "颜色格式非法", "昵称颜色必须为 #RGB、#RRGGBB 或 #RRGGBBAA 格式的十六进制颜色。");
        }
    }

    private Mono<Void> validateMaterial(UserDecorationAsset asset) {
        // name_style / 称号不依赖素材：称号必有文字，图只是可选增强（有图卡片显示图，无图显示文字牌）
        var type = DecorationType.from(asset.getSpec().getType());
        var ref = asset.getSpec().getAsset();
        String url = ref == null ? null : ref.getUrl();
        boolean materialRequired = type != DecorationType.TITLE && type != DecorationType.NAME_STYLE;
        if (!StringUtils.hasText(url)) {
            if (materialRequired) {
                return Mono.error(InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                    "素材地址为空", "请先选择素材后再启用。"));
            }
            return Mono.empty();
        }
        return validateExternalUrl(url).then(Mono.defer(() -> {
            if (isSvg(asset)) {
                // SVG 强校验：读取内容并检测危险元素（依次尝试候选地址）
                return fetchFirstReachableContent(url)
                    .switchIfEmpty(Mono.error(inaccessibleMaterial()))
                    .flatMap(content -> SvgSanitizer.isSafe(content)
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(InteractionPlusException.unprocessable(
                            ErrorCodes.SVG_UNSAFE, "SVG 安全校验失败",
                            "SVG 含脚本、事件属性或外链等危险内容，已拒绝启用。")));
            }
            // 非 SVG 素材不做服务端可达性探测：附件 URL 选定即信任
            return Mono.empty();
        }));
    }

    /**
     * SSRF 防护：素材为用户配置的绝对地址时，限定 http/https 协议，
     * 且禁止解析到回环 / 私有网段 / 链路本地地址，防止服务端被用于探测内网。
     * 站内相对地址不在此限制（candidateUrls 构造的本地回环访问是预期行为）。
     * 校验与请求间存在 DNS 重绑定窗口，属纵深防御层面的已接受残留
     * （素材仅 decoration:manage 管理员可配置）。
     */
    private Mono<Void> validateExternalUrl(String url) {
        // 协议闸门：仅 http/https 绝对地址走 SSRF 校验（站内相对地址不受限）
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
                URI uri = URI.create(url);
                String host = uri.getHost();
                if (!StringUtils.hasText(host)) {
                    throw unsafeMaterialUrl();
                }
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (isPrivateAddress(address)) {
                        throw unsafeMaterialUrl();
                    }
                }
                return true;
            })
            // InetAddress 解析为阻塞调用，移出事件循环线程
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(e -> {
                if (e instanceof InteractionPlusException) {
                    return e;
                }
                // 主机名无法解析与安全拦截是两类问题，提示需区分，否则误导排查
                if (e instanceof UnknownHostException) {
                    return unresolvableMaterialUrl();
                }
                return unsafeMaterialUrl();
            })
            .then();
    }

    private boolean isPrivateAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        // IPv6 unique-local（fc00::/7）不在 isSiteLocalAddress 覆盖范围内
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    private InteractionPlusException unsafeMaterialUrl() {
        return InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
            "素材地址不允许", "外部素材地址仅支持 http/https，且不能指向内网地址。");
    }

    private InteractionPlusException unresolvableMaterialUrl() {
        return InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
            "素材地址无法解析", "外部素材地址的主机名无法解析，请检查地址是否正确。");
    }

    /**
     * 候选访问地址：站内相对地址优先本地回环（容器内 externalUrl 可能不可达），外部地址兜底。
     */
    private List<String> candidateUrls(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return List.of(url);
        }
        String path = url.startsWith("/") ? url : "/" + url;
        String port = environment.getProperty("server.port", "8090");
        String localUrl = "http://127.0.0.1:" + port + path;
        String externalUrl = externalLinkProcessor.processLink(url);
        return localUrl.equals(externalUrl) ? List.of(localUrl) : List.of(localUrl, externalUrl);
    }

    /** 依次尝试候选地址读取文本内容，全部失败返回空。 */
    private Mono<String> fetchFirstReachableContent(String url) {
        return Flux.fromIterable(candidateUrls(url))
            // 传 URI 对象避免 uri(String) 模板展开对已编码地址二次编码（中文文件名场景）
            .concatMap(candidate -> Mono.fromCallable(() -> URI.create(candidate))
                .flatMap(uri -> webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5)))
                .onErrorResume(error -> {
                    // 单个候选失败是预期内流程（还有后续候选），留 debug 痕迹供排查全失败
                    log.debug("读取素材候选地址失败：{}", candidate, error);
                    return Mono.empty();
                }))
            .next();
    }

    private InteractionPlusException inaccessibleMaterial() {
        return InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
            "素材不可访问", "无法读取 SVG 素材内容，请确认素材可访问后再启用。");
    }

    private boolean isSvg(UserDecorationAsset asset) {
        var ref = asset.getSpec().getAsset();
        if (ref == null) {
            return false;
        }
        String mediaType = ref.getMediaType();
        if (mediaType != null && mediaType.toLowerCase().contains("svg")) {
            return true;
        }
        String url = ref.getUrl();
        if (url == null) {
            return false;
        }
        String path = url.split("\\?", 2)[0].toLowerCase();
        return path.endsWith(".svg");
    }

    // ───────────────────────── 投稿（UC）─────────────────────────

    /**
     * 当前用户的投稿列表（仅自己提交的）。
     */
    public Mono<ListResult<UserDecorationAsset>> listSubmissions(String userName,
        ServerWebExchange exchange) {
        var params = exchange.getRequest().getQueryParams();
        List<Condition> conditions = new ArrayList<>();
        conditions.add(equal("spec.submittedBy", userName));
        conditions.add(isNull("metadata.deletionTimestamp"));
        ExtensionQuerySupport.addEqualIfPresent(conditions, "spec.status",
            params.getFirst("status"));
        ExtensionQuerySupport.addEqualIfPresent(conditions, "spec.type",
            params.getFirst("type"));
        var options = ListOptions.builder()
            .fieldQuery(ExtensionQuerySupport.andAll(conditions)).build();
        return client.listBy(UserDecorationAsset.class, options,
            ListRequestSupport.toPageRequest(exchange, ASSET_SORT));
    }

    /**
     * 获取自己的投稿，非本人投稿返回 404。
     */
    public Mono<UserDecorationAsset> getSubmission(String userName, String name) {
        return getAsset(name).flatMap(asset -> {
            if (!userName.equals(asset.getSpec().getSubmittedBy())) {
                return Mono.error(InteractionPlusException.notFound(ErrorCodes.ASSET_NOT_FOUND,
                    "投稿不存在", "投稿不存在或无权访问。"));
            }
            return Mono.just(asset);
        });
    }

    /**
     * 编辑自己的未启用草稿。
     */
    public Mono<UserDecorationAsset> updateSubmission(String userName, String name,
        DecorationAssetParam param) {
        return getSubmission(userName, name).flatMap(asset -> {
            if (!asset.isDraft()) {
                return Mono.error(InteractionPlusException.conflict(
                    ErrorCodes.INVALID_STATUS_TRANSITION, "投稿不可编辑",
                    "该投稿已被处理，不能再编辑。"));
            }
            return updateAsset(name, param);
        });
    }

    /**
     * 删除自己的未启用草稿（不发驳回通知）。
     */
    public Mono<Void> deleteSubmission(String userName, String name) {
        return getSubmission(userName, name).flatMap(asset -> {
            if (!asset.isDraft()) {
                return Mono.error(InteractionPlusException.conflict(
                    ErrorCodes.INVALID_STATUS_TRANSITION, "投稿不可删除",
                    "该投稿已被处理，不能删除。"));
            }
            return client.delete(asset).then();
        });
    }

    // ───────────────────────── 辅助 ─────────────────────────

    private Mono<UserDecorationAsset> notifyEnabledIfSubmission(UserDecorationAsset asset) {
        String submittedBy = asset.getSpec().getSubmittedBy();
        if (!StringUtils.hasText(submittedBy)) {
            return Mono.just(asset);
        }
        return notificationService
            .notifyDraftEnabled(submittedBy, asset.getSpec().getDisplayName())
            .thenReturn(asset);
    }

    private ListOptions buildAssetListOptions(ServerWebExchange exchange) {
        var params = exchange.getRequest().getQueryParams();
        List<Condition> conditions = new ArrayList<>();
        conditions.add(isNull("metadata.deletionTimestamp"));
        ExtensionQuerySupport.addEqualIfPresent(conditions, "spec.type",
            params.getFirst("type"));
        ExtensionQuerySupport.addEqualIfPresent(conditions, "spec.status",
            params.getFirst("status"));
        // 分类 / 稀有度 / 标签支持「未分类 / 无稀有度 / 无标签」哨兵值
        addEqualOrNone(conditions, "spec.categoryName", params.getFirst("categoryName"));
        addEqualOrNone(conditions, "spec.rarityName", params.getFirst("rarityName"));
        ExtensionQuerySupport.addEqualIfPresent(conditions, "spec.submittedBy",
            params.getFirst("submittedBy"));
        ExtensionQuerySupport.addEqualIfPresent(conditions, "spec.createdBy",
            params.getFirst("createdBy"));
        addEqualOrNone(conditions, "spec.tagNames", params.getFirst("tagName"));
        String keyword = ListRequestSupport.getKeyword(exchange);
        if (keyword != null) {
            conditions.add(contains("spec.displayName", keyword));
        }
        return ListOptions.builder()
            .fieldQuery(ExtensionQuerySupport.andAll(conditions)).build();
    }

    private void addEqualOrNone(List<Condition> conditions, String field, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (FILTER_NONE.equals(value)) {
            conditions.add(isNull(field));
        } else {
            conditions.add(equal(field, value));
        }
    }

    private Mono<UserDecorationAsset> invalidTransition(DecorationStatus from,
        DecorationStatus to) {
        return Mono.error(InteractionPlusException.badRequest(ErrorCodes.INVALID_STATUS_TRANSITION,
            "状态流转非法",
            "不能从 " + (from == null ? "未知" : from.getValue()) + " 切换到 " + to.getValue() + "。"));
    }
}
