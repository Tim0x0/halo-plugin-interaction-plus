package com.timxs.interactionplus.identity.service;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.timxs.interactionplus.api.UserStat;
import com.timxs.interactionplus.api.UserStatContributor;
import com.timxs.interactionplus.decoration.constants.DecorationType;
import com.timxs.interactionplus.decoration.constants.EquipSlot;
import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationGrant;
import com.timxs.interactionplus.decoration.extension.UserDecorationProfile;
import com.timxs.interactionplus.decoration.extension.UserDecorationRarity;
import com.timxs.interactionplus.decoration.service.DecorationMetadataService;
import com.timxs.interactionplus.identity.extension.UserIdentityMarkMapping;
import com.timxs.interactionplus.identity.model.PublicIdentityBatch;
import com.timxs.interactionplus.identity.model.PublicIdentityVo;
import com.timxs.interactionplus.core.setting.DisplaySetting;
import com.timxs.interactionplus.core.setting.InteractionPlusSettingService;
import com.timxs.interactionplus.core.support.NameGenerator;
import com.timxs.interactionplus.identity.support.ExtensionDefinitionRef;
import com.timxs.interactionplus.identity.support.PublicIdentityCache;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Role;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.ExternalLinkProcessor;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

/**
 * 公开身份聚合服务。
 *
 * <p>聚合用户公开信息、身份标识与当前有效装饰；查询时永远过滤失效佩戴项；
 * 不返回邮箱、角色列表、授予历史等私有字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicIdentityService {

    /** 身份标识返回数量硬上限（配置项各场景允许范围为 1-10）。 */
    private static final int IDENTITY_MARK_MAX = 10;

    /** 装饰墙并发 fetch 资产的并发度上限（有界并发，避免压垮 DB 连接池）。 */
    private static final int ASSET_FETCH_CONCURRENCY = 8;

    /** 批量身份查询的并发度上限（每个聚合内部还有查询放大，取更保守值）。 */
    private static final int IDENTITY_FETCH_CONCURRENCY = 4;

    /** 统计贡献扩展点的 EPD 名（外部实现的 ExtensionDefinition.spec.extensionPointName 引用它）。 */
    private static final String USER_STAT_EPD_NAME = "user-stat-contributor";

    /** Comment.spec.owner 索引里内置用户身份的 kind（CommentOwner 未提供 User 侧常量）。 */
    private static final String COMMENT_OWNER_KIND_USER = "User";

    /**
     * Halo 给插件自带资源自动打的插件名 label。对齐核心 {@code PluginConst.PLUGIN_NAME_LABEL_NAME}
     * （该类在 application 模块，插件编译期不可见，故本地声明同值常量）。
     */
    private static final String PLUGIN_NAME_LABEL = "plugin.halo.run/plugin-name";

    /** 单个统计贡献方每用户至多贡献的项数（超出截断）。 */
    private static final int CONTRIBUTED_STATS_PER_PLUGIN_MAX = 5;

    /** 单个统计贡献方的总超时预算：超时丢弃其全部项，不拖慢身份聚合。 */
    private static final Duration CONTRIBUTOR_TIMEOUT = Duration.ofMillis(800);

    /** 统计贡献项 key 约束（与 api 包 UserStat javadoc 的契约一致）。 */
    private static final Pattern CONTRIBUTED_KEY_PATTERN =
        Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");

    /** 统计贡献项 label 长度上限。 */
    private static final int CONTRIBUTED_LABEL_MAX = 16;

    /** 统计贡献项 value 长度上限。 */
    private static final int CONTRIBUTED_VALUE_MAX = 32;

    private final ReactiveExtensionClient client;
    private final RoleService roleService;
    private final InteractionPlusSettingService settingService;
    private final DecorationMetadataService metadataService;
    private final PublicIdentityCache cache;
    private final ExternalLinkProcessor externalLinkProcessor;
    private final ExtensionGetter extensionGetter;

    /**
     * 一次聚合的共享上下文：display 配置 + 惰性加载且至多加载一次的全局字典
     * （稀有度表、启用的身份标识映射表、统计贡献实现的来源插件表）。批量查询 50 用户时
     * 全局表各读一次而非每用户一次；单用户路径同样按需加载（身份标识关闭时映射表不加载、
     * 无佩戴时稀有度表不加载、无统计贡献实现时来源表不加载）。
     */
    private record AggregationContext(DisplaySetting display,
        Mono<Map<String, UserDecorationRarity>> rarityMap,
        Mono<List<UserIdentityMarkMapping>> enabledMappings,
        Mono<Map<String, String>> contributorSources) {
    }

    private AggregationContext newContext(DisplaySetting display) {
        return new AggregationContext(display,
            metadataService.loadRarityMap().cache(),
            loadEnabledMappings().cache(),
            loadContributorSources().cache());
    }

    /** 全部启用的身份标识映射（priority desc）。 */
    private Mono<List<UserIdentityMarkMapping>> loadEnabledMappings() {
        var options = ListOptions.builder()
            .fieldQuery(and(equal("spec.enabled", true),
                isNull("metadata.deletionTimestamp")))
            .build();
        return client.listAll(UserIdentityMarkMapping.class, options,
                Sort.by(Sort.Order.desc("spec.priority")))
            .collectList();
    }

    /**
     * 获取单个用户的公开身份数据。用户不存在或不可用时返回空。
     */
    public Mono<PublicIdentityVo> getIdentity(String userName) {
        if (!StringUtils.hasText(userName)) {
            return Mono.empty();
        }
        var cached = cache.get(userName);
        if (cached != null) {
            return Mono.just(cached);
        }
        return settingService.getDisplaySetting()
            .flatMap(display -> aggregateAndCache(newContext(display), userName));
    }

    /** 缓存命中直接返回，否则聚合并按配置 TTL 回填缓存（批量查询多个用户共享同一 ctx）。 */
    private Mono<PublicIdentityVo> aggregateAndCache(AggregationContext ctx, String userName) {
        var cached = cache.get(userName);
        if (cached != null) {
            return Mono.just(cached);
        }
        int ttl = InteractionPlusSettingService.clampPublicIdentityCacheTtl(ctx.display());
        return client.fetch(User.class, userName)
            .filter(user -> !Boolean.TRUE.equals(user.getSpec().getDisabled()))
            .flatMap(user -> aggregate(user, ctx)
                .doOnNext(vo -> cache.put(userName, vo, ttl)));
    }

    /**
     * 单用户查询（API 用）：不存在时抛 404。
     */
    public Mono<PublicIdentityVo> requireIdentity(String userName) {
        return getIdentity(userName)
            .switchIfEmpty(Mono.error(InteractionPlusException.notFound(
                ErrorCodes.VALIDATION_FAILED, "用户不存在", "用户不存在或不可用。")));
    }

    /**
     * 批量查询。最多 50 个，自动去重；不存在或不可用用户进入 skipped。
     */
    public Mono<PublicIdentityBatch.Result> getIdentities(List<String> userNames) {
        var distinct = new LinkedHashSet<String>();
        if (userNames != null) {
            userNames.stream().filter(StringUtils::hasText).forEach(distinct::add);
        }
        if (distinct.isEmpty()) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "用户名列表不能为空。");
        }
        if (distinct.size() > InteractionPlusConst.PUBLIC_IDENTITY_BATCH_LIMIT) {
            throw InteractionPlusException.badRequest(ErrorCodes.BATCH_LIMIT_EXCEEDED,
                "批量请求超过上限",
                "单次最多查询 " + InteractionPlusConst.PUBLIC_IDENTITY_BATCH_LIMIT + " 个用户。");
        }
        // 全局字典（display / 稀有度表 / 标识映射表）批级共享、各至多加载一次（此前逐用户重复加载）；
        // 有界并发聚合：并发查询多个用户但限制并发度（避免压垮连接池），保序；
        // 收集完成后单线程分类到 items/skipped，无共享可变状态。
        return settingService.getDisplaySetting().flatMap(display -> {
            var ctx = newContext(display);
            return Flux.fromIterable(distinct)
                .flatMapSequential(userName -> aggregateAndCache(ctx, userName)
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        // 单个用户失败不影响整批
                        .onErrorReturn(Optional.empty())
                        .map(opt -> Map.entry(userName, opt)),
                    IDENTITY_FETCH_CONCURRENCY)
                .collectList()
                .map(entries -> {
                    var result = new PublicIdentityBatch.Result();
                    for (var entry : entries) {
                        entry.getValue().ifPresentOrElse(
                            result.getItems()::add,
                            () -> result.getSkipped().add(entry.getKey()));
                    }
                    return result;
                });
        });
    }

    // ───────────────────────── 聚合 ─────────────────────────

    private Mono<PublicIdentityVo> aggregate(User user, AggregationContext ctx) {
        var userName = user.getMetadata().getName();
        var vo = new PublicIdentityVo();
        vo.setUserName(userName);
        vo.setDisplayName(StringUtils.hasText(user.getSpec().getDisplayName())
            ? user.getSpec().getDisplayName() : userName);
        var avatar = user.getSpec().getAvatar();
        vo.setAvatar(StringUtils.hasText(avatar)
            ? externalLinkProcessor.processLink(avatar) : null);
        vo.setBio(user.getSpec().getBio());
        vo.setRegisteredAt(user.getSpec().getRegisteredAt());
        applyDisplayConfig(vo, ctx.display());

        var identityMono = ctx.display().isEnabledIdentityMark()
            ? loadIdentityMarks(userName, ctx).doOnNext(vo::setIdentityMarks)
            : Mono.just(List.<PublicIdentityVo.IdentityMarkVo>of());

        var decorationsMono = loadDecorations(userName, ctx)
            .doOnNext(vo::setDecorations);

        var statsMono = loadStats(userName, ctx).doOnNext(vo::setStats);

        // 三条链无数据依赖（各自 doOnNext 写 vo 的独立字段），并行执行取最大延迟而非之和
        return Mono.when(identityMono, decorationsMono, statsMono).thenReturn(vo);
    }

    private void applyDisplayConfig(PublicIdentityVo vo, DisplaySetting display) {
        var config = vo.getDisplay();
        config.setIdentityLineShowPrimaryBadge(display.isIdentityLineShowPrimaryBadge());
        config.setIdentityLineIdentityLimit(display.getIdentityLineIdentityLimit());
        config.setUserCardShowcaseBadgeLimit(display.getUserCardShowcaseBadgeLimit());
        config.setUserCardIdentityLimit(display.getUserCardIdentityLimit());
    }

    // ── 身份标识 ──

    /**
     * 解析用户当前生效的身份标识（按角色映射），供 UC 等模块复用。
     * 尊重全局开关：{@code enabledIdentityMark} 关闭时返回空列表。
     */
    public Mono<List<PublicIdentityVo.IdentityMarkVo>> resolveIdentityMarks(String userName) {
        if (!StringUtils.hasText(userName)) {
            return Mono.just(List.of());
        }
        return settingService.getDisplaySetting()
            .flatMap(display -> display.isEnabledIdentityMark()
                ? loadIdentityMarks(userName, newContext(display))
                : Mono.just(List.of()));
    }

    private Mono<List<PublicIdentityVo.IdentityMarkVo>> loadIdentityMarks(String userName,
        AggregationContext ctx) {
        // 取各场景配置的最大值（再按硬上限钳制），避免配置只要 2 个时仍查 10 个
        int limit = Math.max(ctx.display().getIdentityLineIdentityLimit(),
            ctx.display().getUserCardIdentityLimit());
        int take = Math.min(Math.max(limit, 1), IDENTITY_MARK_MAX);
        return roleService.getRolesByUsername(userName)
            .collect(HashSet<String>::new, HashSet::add)
            .flatMap(userRoles -> {
                if (userRoles.isEmpty()) {
                    return Mono.just(List.of());
                }
                return ctx.enabledMappings()
                    .flatMapMany(Flux::fromIterable)
                    .filter(mapping -> userRoles.contains(mapping.getSpec().getRoleName()))
                    // 角色不存在的映射运行时失效
                    .concatMap(mapping -> client.fetch(Role.class, mapping.getSpec().getRoleName())
                        .map(role -> mapping))
                    .take(take)
                    .map(this::toIdentityMarkVo)
                    .collectList();
            });
    }

    private PublicIdentityVo.IdentityMarkVo toIdentityMarkVo(UserIdentityMarkMapping mapping) {
        var vo = new PublicIdentityVo.IdentityMarkVo();
        vo.setDisplayName(mapping.getSpec().getDisplayName());
        var icon = mapping.getSpec().getIcon();
        // data: 图标（Console iconify 控件物化的自包含产物）绕过外链处理——
        // processLink 面向相对/站内路径的绝对化，对 data URI 无意义且行为未定义
        if (!StringUtils.hasText(icon)) {
            vo.setIcon(null);
        } else if (icon.startsWith("data:")) {
            vo.setIcon(icon);
        } else {
            vo.setIcon(externalLinkProcessor.processLink(icon));
        }
        vo.setColor(mapping.getSpec().getColor());
        vo.setPriority(mapping.getSpec().getPriority());
        return vo;
    }

    // ── 装饰 ──

    private Mono<PublicIdentityVo.DecorationsVo> loadDecorations(String userName,
        AggregationContext ctx) {
        return client.fetch(UserDecorationProfile.class, NameGenerator.profileName(userName))
            .flatMap(profile -> buildDecorations(userName, profile.getSpec(), ctx))
            .defaultIfEmpty(new PublicIdentityVo.DecorationsVo());
    }

    private Mono<PublicIdentityVo.DecorationsVo> buildDecorations(String userName,
        UserDecorationProfile.Spec spec, AggregationContext ctx) {
        var display = ctx.display();
        // 收集所有佩戴的资产名（按全局类型开关过滤）
        var equipped = new LinkedHashSet<String>();
        if (display.isEnabledAvatarFrame() && StringUtils.hasText(spec.getAvatarFrame())) {
            equipped.add(spec.getAvatarFrame());
        }
        if (display.isEnabledTitle() && StringUtils.hasText(spec.getTitle())) {
            equipped.add(spec.getTitle());
        }
        if (display.isEnabledBadge() && StringUtils.hasText(spec.getPrimaryBadge())) {
            equipped.add(spec.getPrimaryBadge());
        }
        if (display.isEnabledBadge() && !CollectionUtils.isEmpty(spec.getBadgeShowcase())) {
            spec.getBadgeShowcase().stream().filter(StringUtils::hasText).forEach(equipped::add);
        }
        if (display.isEnabledCardBackground() && StringUtils.hasText(spec.getCardBackground())) {
            equipped.add(spec.getCardBackground());
        }
        if (display.isEnabledNameStyle() && StringUtils.hasText(spec.getNameStyle())) {
            equipped.add(spec.getNameStyle());
        }
        if (equipped.isEmpty()) {
            return Mono.just(new PublicIdentityVo.DecorationsVo());
        }
        return Mono.zip(loadOwnedActiveAssets(userName, equipped), ctx.rarityMap())
            .map(tuple -> {
                var validAssets = tuple.getT1();
                var rarityMap = tuple.getT2();
                var decorations = new PublicIdentityVo.DecorationsVo();
                decorations.setAvatarFrame(
                    toDecorationVo(validAssets, spec.getAvatarFrame(), EquipSlot.AVATAR_FRAME,
                        rarityMap));
                decorations.setTitle(
                    toDecorationVo(validAssets, spec.getTitle(), EquipSlot.TITLE, rarityMap));
                decorations.setPrimaryBadge(
                    toDecorationVo(validAssets, spec.getPrimaryBadge(), EquipSlot.PRIMARY_BADGE,
                        rarityMap));
                if (!CollectionUtils.isEmpty(spec.getBadgeShowcase())) {
                    spec.getBadgeShowcase().stream()
                        .map(name -> toDecorationVo(validAssets, name, EquipSlot.BADGE_SHOWCASE,
                            rarityMap))
                        .filter(java.util.Objects::nonNull)
                        .limit(InteractionPlusConst.BADGE_SHOWCASE_MAX)
                        .forEach(decorations.getBadgeShowcase()::add);
                }
                decorations.setCardBackground(
                    toDecorationVo(validAssets, spec.getCardBackground(), EquipSlot.CARD_BACKGROUND,
                        rarityMap));
                decorations.setNameStyle(
                    toDecorationVo(validAssets, spec.getNameStyle(), EquipSlot.NAME_STYLE,
                        rarityMap));
                return decorations;
            });
    }

    /**
     * 加载用户佩戴中仍有效的资产：资产 active 且存在有效授予。
     */
    private Mono<Map<String, UserDecorationAsset>> loadOwnedActiveAssets(String userName,
        Set<String> assetNames) {
        var now = Instant.now();
        return client.listAll(UserDecorationGrant.class, activeGrantOptions(userName),
                Sort.by(Sort.Order.asc("metadata.name")))
            .filter(grant -> grant.isActiveAt(now))
            .map(grant -> grant.getSpec().getAssetName())
            .collect(HashSet<String>::new, HashSet::add)
            .flatMap(ownedNames -> Flux.fromIterable(assetNames)
                .filter(ownedNames::contains)
                .flatMap(name -> client.fetch(UserDecorationAsset.class, name))
                .filter(UserDecorationAsset::isActive)
                .collectMap(asset -> asset.getMetadata().getName()));
    }

    /** 该用户全部未撤销、未删除授予的查询条件（有效性还需内存过滤 isActiveAt）。 */
    private ListOptions activeGrantOptions(String userName) {
        return ListOptions.builder()
            .fieldQuery(and(equal("spec.userName", userName),
                equal("spec.revoked", false),
                isNull("metadata.deletionTimestamp")))
            .build();
    }

    private PublicIdentityVo.DecorationVo toDecorationVo(Map<String, UserDecorationAsset> assets,
        String assetName, EquipSlot slot, Map<String, UserDecorationRarity> rarityMap) {
        if (!StringUtils.hasText(assetName)) {
            return null;
        }
        var asset = assets.get(assetName);
        if (asset == null) {
            return null;
        }
        // 类型与槽位不匹配的过滤掉
        if (DecorationType.from(asset.getSpec().getType()) != slot.getAssetType()) {
            return null;
        }
        return assetToDecorationVo(asset, rarityMap);
    }

    /** 资产 → 公开装饰展示数据（剥离私有字段；不做槽位校验，供装饰墙等复用）。 */
    private PublicIdentityVo.DecorationVo assetToDecorationVo(UserDecorationAsset asset,
        Map<String, UserDecorationRarity> rarityMap) {
        var spec = asset.getSpec();
        var vo = new PublicIdentityVo.DecorationVo();
        vo.setAssetName(asset.getMetadata().getName());
        vo.setType(spec.getType());
        vo.setDisplayName(spec.getDisplayName());
        vo.setRarityName(spec.getRarityName());
        // 内联稀有度展示属性（显示名 + 颜色），前台零二次查
        if (StringUtils.hasText(spec.getRarityName()) && rarityMap != null) {
            var rarity = rarityMap.get(spec.getRarityName());
            if (rarity != null) {
                vo.setRarityDisplayName(rarity.getSpec().getDisplayName());
                vo.setRarityColor(rarity.getSpec().getColor());
            }
        }
        var ref = spec.getAsset();
        if (ref != null && StringUtils.hasText(ref.getUrl())) {
            vo.setUrl(externalLinkProcessor.processLink(ref.getUrl()));
            vo.setMediaType(ref.getMediaType());
        }
        var payload = spec.getPayload();
        if (payload != null) {
            vo.setTitleMode(payload.getTitleMode());
            vo.setTitleText(payload.getTitleText());
            vo.setTitleColor(payload.getTitleColor());
            vo.setTitleBackground(payload.getTitleBackground());
            vo.setTitleBackgroundSecondary(payload.getTitleBackgroundSecondary());
            vo.setNameStyle(payload.getNameStyle());
        }
        return vo;
    }

    // ── 互动统计 ──

    /**
     * 聚合互动统计：文章 / 评论为索引 count 级查询，装扮计数与外部贡献项并行加载。
     * 四条链彼此独立，失败语义各自兜底，不拖垮身份聚合主链。
     */
    private Mono<PublicIdentityVo.StatsVo> loadStats(String userName, AggregationContext ctx) {
        var stats = new PublicIdentityVo.StatsVo();
        var postsMono = client.countBy(Post.class, publishedPostOptions(userName))
            .doOnNext(stats::setPosts);
        var commentsMono = client.countBy(Comment.class, visibleCommentOptions(userName))
            .doOnNext(stats::setComments);
        // 关闭公开装扮墙时为空 Mono，decorations 保持 null（对外语义：不可用）
        var countsMono = loadDecorationCounts(userName).doOnNext(stats::setDecorations);
        var extrasMono = loadContributedStats(userName, ctx).doOnNext(stats::setExtras);
        return Mono.when(postsMono, commentsMono, countsMono, extrasMono).thenReturn(stats);
    }

    /** 公开文章口径：已发布、未删除、可见性 PUBLIC（均为 Halo 内置索引字段）。 */
    private ListOptions publishedPostOptions(String userName) {
        return ListOptions.builder()
            .fieldQuery(and(equal("spec.owner", userName),
                equal("spec.deleted", false),
                equal("spec.visible", Post.VisibleEnum.PUBLIC.name()),
                equal("status.phase", Post.PostPhase.PUBLISHED.name()),
                isNull("metadata.deletionTimestamp")))
            .build();
    }

    /** 公开评论口径：已审核、未隐藏；owner 索引值为 {@code kind#name}（v2.25 源码核实）。 */
    private ListOptions visibleCommentOptions(String userName) {
        var ownerIdentity =
            Comment.CommentOwner.ownerIdentity(COMMENT_OWNER_KIND_USER, userName);
        return ListOptions.builder()
            .fieldQuery(and(equal("spec.owner", ownerIdentity),
                equal("spec.approved", true),
                equal("spec.hidden", false),
                isNull("metadata.deletionTimestamp")))
            .build();
    }

    /**
     * 按类型统计用户持有的有效装扮（有效授予 + 资产可用，按资产去重，与装饰墙同口径）。
     * 尊重「公开装扮墙」开关：关闭时返回空 Mono（stats.decorations 保持 null）。
     */
    private Mono<PublicIdentityVo.DecorationCountsVo> loadDecorationCounts(String userName) {
        return client.fetch(UserDecorationProfile.class, NameGenerator.profileName(userName))
            .map(profile -> !Boolean.FALSE.equals(profile.getSpec().getPublicDecorationsVisible()))
            .defaultIfEmpty(true)
            .filter(Boolean::booleanValue)
            .flatMap(ignored -> {
                var now = Instant.now();
                return client.listAll(UserDecorationGrant.class, activeGrantOptions(userName),
                        Sort.by(Sort.Order.asc("metadata.name")))
                    .filter(grant -> grant.isActiveAt(now))
                    .map(grant -> grant.getSpec().getAssetName())
                    .distinct()
                    .flatMap(name -> client.fetch(UserDecorationAsset.class, name),
                        ASSET_FETCH_CONCURRENCY)
                    .filter(UserDecorationAsset::isActive)
                    .mapNotNull(asset -> DecorationType.from(asset.getSpec().getType()))
                    .collectList()
                    .map(this::toDecorationCounts);
            });
    }

    private PublicIdentityVo.DecorationCountsVo toDecorationCounts(List<DecorationType> types) {
        var counts = new PublicIdentityVo.DecorationCountsVo();
        for (var type : types) {
            switch (type) {
                case BADGE -> counts.setBadge(counts.getBadge() + 1);
                case AVATAR_FRAME -> counts.setAvatarFrame(counts.getAvatarFrame() + 1);
                case TITLE -> counts.setTitle(counts.getTitle() + 1);
                case NAME_STYLE -> counts.setNameStyle(counts.getNameStyle() + 1);
                case CARD_BACKGROUND -> counts.setCardBackground(counts.getCardBackground() + 1);
            }
        }
        counts.setTotal(types.size());
        return counts;
    }

    /**
     * 收集外部插件经 {@code UserStatContributor} 扩展点贡献的统计项。
     * 来源插件 id 按实现类对应 ExtensionDefinition 上 Halo 自动打的 plugin-name label
     * 标注（系统盖章、贡献方不可自报）；查不到 ED 归属的实现整体忽略。
     * 单个贡献方限时 + 限量 + 逐项校验，任何失败只影响该贡献方自身。
     */
    private Mono<List<PublicIdentityVo.ContributedStatVo>> loadContributedStats(String userName,
        AggregationContext ctx) {
        return extensionGetter.getEnabledExtensions(UserStatContributor.class)
            .flatMapSequential(contributor -> ctx.contributorSources()
                .flatMapMany(sources -> collectFromContributor(contributor, sources, userName)))
            .collectList();
    }

    private Flux<PublicIdentityVo.ContributedStatVo> collectFromContributor(
        UserStatContributor contributor, Map<String, String> sources, String userName) {
        var className = contributor.getClass().getName();
        var source = sources.get(className);
        if (!StringUtils.hasText(source)) {
            log.warn("忽略统计贡献实现 {}：未声明 ExtensionDefinition，无法标注来源插件", className);
            return Flux.empty();
        }
        return contributor.getUserStats(userName)
            .take(CONTRIBUTED_STATS_PER_PLUGIN_MAX)
            .collectList()
            .timeout(CONTRIBUTOR_TIMEOUT)
            .onErrorResume(e -> {
                log.warn("统计贡献方 {} 调用失败，本次聚合忽略其贡献项", source, e);
                return Mono.just(List.of());
            })
            .flatMapMany(Flux::fromIterable)
            .mapNotNull(stat -> toContributedStat(source, stat));
    }

    /** 贡献项字段校验与落 VO：非法项丢弃（返回 null 由 mapNotNull 过滤）。 */
    private PublicIdentityVo.ContributedStatVo toContributedStat(String source, UserStat stat) {
        if (stat == null || stat.key() == null || stat.label() == null || stat.value() == null) {
            return null;
        }
        if (!CONTRIBUTED_KEY_PATTERN.matcher(stat.key()).matches()
            || stat.label().isBlank() || stat.label().length() > CONTRIBUTED_LABEL_MAX
            || stat.value().isBlank() || stat.value().length() > CONTRIBUTED_VALUE_MAX) {
            log.warn("丢弃来源 {} 的非法统计贡献项：key={}", source, stat.key());
            return null;
        }
        var vo = new PublicIdentityVo.ContributedStatVo();
        vo.setSource(source);
        vo.setKey(stat.key());
        vo.setLabel(stat.label().strip());
        vo.setValue(stat.value().strip());
        return vo;
    }

    /** 统计贡献实现类名 → 来源插件 id（取 ED 上 Halo 自动打的 plugin-name label）。 */
    private Mono<Map<String, String>> loadContributorSources() {
        var options = ListOptions.builder()
            .fieldQuery(equal("spec.extensionPointName", USER_STAT_EPD_NAME))
            .build();
        return client.listAll(ExtensionDefinitionRef.class, options,
                Sort.by(Sort.Order.asc("metadata.name")))
            .collectMap(
                definition -> definition.getSpec().getClassName(),
                definition -> {
                    var labels = definition.getMetadata().getLabels();
                    return labels == null ? ""
                        : labels.getOrDefault(PLUGIN_NAME_LABEL, "");
                });
    }

    // ── 装饰墙（用户获得的装饰，供主题个人主页等展示） ──

    /**
     * 装饰墙：用户获得的、当前有效（资产 active + 存在有效授予）的全部装饰，按获得时间倒序去重。
     * 自包含完整展示信息，剥离授予原因 / 撤销人等私有字段。
     * 尊重用户的「公开装扮墙」开关——关闭时返回空列表。
     */
    public Mono<List<PublicIdentityVo.DecorationVo>> resolveOwnedDecorations(String userName) {
        if (!StringUtils.hasText(userName)) {
            return Mono.just(List.of());
        }
        return client.fetch(UserDecorationProfile.class, NameGenerator.profileName(userName))
            .map(profile -> !Boolean.FALSE.equals(profile.getSpec().getPublicDecorationsVisible()))
            .defaultIfEmpty(true)
            .flatMap(visible -> visible ? loadOwnedDecorations(userName) : Mono.just(List.of()));
    }

    private Mono<List<PublicIdentityVo.DecorationVo>> loadOwnedDecorations(String userName) {
        var now = Instant.now();
        return metadataService.loadRarityMap().flatMap(rarityMap ->
            client.listAll(UserDecorationGrant.class, activeGrantOptions(userName),
                Sort.by(Sort.Order.desc("spec.grantedAt")))
            .filter(grant -> grant.isActiveAt(now))
            .collectList()
            .flatMap(grants -> {
                // 同一装饰多来源授予聚合为一项：获得时间取最新（已按 grantedAt desc，首条即最新），
                // 展示有效期与 UC 库存同源（任一永久=永久，否则取最晚）
                var byAsset = new LinkedHashMap<String, List<UserDecorationGrant>>();
                for (var grant : grants) {
                    byAsset.computeIfAbsent(grant.getSpec().getAssetName(),
                        key -> new ArrayList<>()).add(grant);
                }
                return Flux.fromIterable(byAsset.entrySet())
                    .flatMapSequential(entry -> client.fetch(UserDecorationAsset.class,
                            entry.getKey())
                        .filter(UserDecorationAsset::isActive)
                        .map(asset -> {
                            var vo = assetToDecorationVo(asset, rarityMap);
                            vo.setGrantedAt(entry.getValue().get(0).getSpec().getGrantedAt());
                            vo.setExpiresAt(
                                UserDecorationGrant.effectiveExpiresAt(entry.getValue()));
                            return vo;
                        }), ASSET_FETCH_CONCURRENCY)
                    .collectList();
            }));
    }
}
