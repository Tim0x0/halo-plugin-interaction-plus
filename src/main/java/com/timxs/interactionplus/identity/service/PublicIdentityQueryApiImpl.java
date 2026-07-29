package com.timxs.interactionplus.identity.service;

import com.timxs.interactionplus.api.PublicIdentity;
import com.timxs.interactionplus.api.PublicIdentityQueryApi;
import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.identity.model.PublicIdentityBatch;
import com.timxs.interactionplus.identity.model.PublicIdentityVo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 对外身份查询 API 实现。
 *
 * <p>以 {@code @Component} 注册到本插件的 Spring 容器，经 Halo 核心
 * {@code ExtensionGetter.getEnabledExtension(PublicIdentityQueryApi.class)} 暴露给依赖本插件的外部插件
 * （Halo 插件容器相互隔离，外部无法直接 {@code @Autowired}，只能经 ExtensionGetter 取用）。
 * 实际聚合委托 {@link PublicIdentityService}（与公开 HTTP API、Finder 同源，缓存共享），
 * 本类只做参数校验与 VO → 对外 DTO 的单点映射（两个出口的字段增减都改这里，防漂移）。
 *
 * <p>注意：ExtensionDefinition 按运行时类名精确匹配实现类，本类不可添加会触发 CGLIB
 * 代理的注解（如事务），有此类需求应下沉到被委托的 service。
 *
 * @author Tim0x0
 */
@Component
@RequiredArgsConstructor
public class PublicIdentityQueryApiImpl implements PublicIdentityQueryApi {

    private final PublicIdentityService publicIdentityService;

    @Override
    public Mono<PublicIdentity> getIdentity(String userName) {
        if (!StringUtils.hasText(userName)) {
            return Mono.error(new IllegalArgumentException("userName 不能为空"));
        }
        return publicIdentityService.getIdentity(userName)
            .map(PublicIdentityQueryApiImpl::toApi);
    }

    @Override
    public Flux<PublicIdentity> listIdentities(Collection<String> userNames) {
        var distinct = new LinkedHashSet<String>();
        if (userNames != null) {
            userNames.stream().filter(StringUtils::hasText).forEach(distinct::add);
        }
        if (distinct.isEmpty()) {
            return Flux.empty();
        }
        if (distinct.size() > InteractionPlusConst.PUBLIC_IDENTITY_BATCH_LIMIT) {
            return Flux.error(new IllegalArgumentException(
                "单次最多查询 " + InteractionPlusConst.PUBLIC_IDENTITY_BATCH_LIMIT + " 个用户"));
        }
        // 复用批量聚合（批级共享全局字典、有界并发、单用户失败不影响整批）；
        // 进程内出口不区分 skipped，查不到的用户直接缺席
        return publicIdentityService.getIdentities(new ArrayList<>(distinct))
            .flatMapIterable(PublicIdentityBatch.Result::getItems)
            .map(PublicIdentityQueryApiImpl::toApi);
    }

    // ───────────────────── VO → 对外 DTO 映射（单点） ─────────────────────

    private static PublicIdentity toApi(PublicIdentityVo vo) {
        return new PublicIdentity(
            vo.getUserName(),
            vo.getDisplayName(),
            vo.getAvatar(),
            vo.getBio(),
            vo.getRegisteredAt(),
            toIdentityMarks(vo.getIdentityMarks()),
            toDecorations(vo.getDecorations()),
            toStats(vo.getStats()),
            toDisplayConfig(vo.getDisplay()));
    }

    private static PublicIdentity.Stats toStats(PublicIdentityVo.StatsVo vo) {
        if (vo == null) {
            return new PublicIdentity.Stats(0, 0, null, List.of());
        }
        var counts = vo.getDecorations();
        var decorations = counts == null ? null
            : new PublicIdentity.DecorationCounts(counts.getTotal(), counts.getBadge(),
                counts.getAvatarFrame(), counts.getTitle(), counts.getNameStyle(),
                counts.getCardBackground());
        var extras = vo.getExtras() == null
            ? List.<PublicIdentity.ContributedStat>of()
            : vo.getExtras().stream()
                .map(extra -> new PublicIdentity.ContributedStat(extra.getSource(),
                    extra.getKey(), extra.getLabel(), extra.getValue()))
                .toList();
        return new PublicIdentity.Stats(vo.getPosts(), vo.getComments(), decorations, extras);
    }

    private static List<PublicIdentity.IdentityMark> toIdentityMarks(
        List<PublicIdentityVo.IdentityMarkVo> marks) {
        if (marks == null || marks.isEmpty()) {
            return List.of();
        }
        return marks.stream()
            .map(mark -> new PublicIdentity.IdentityMark(mark.getDisplayName(), mark.getIcon(),
                mark.getColor(), mark.getPriority()))
            .toList();
    }

    private static PublicIdentity.WornDecorations toDecorations(
        PublicIdentityVo.DecorationsVo vo) {
        if (vo == null) {
            return new PublicIdentity.WornDecorations(null, null, null, List.of(), null, null);
        }
        var showcase = vo.getBadgeShowcase() == null
            ? List.<PublicIdentity.Decoration>of()
            : vo.getBadgeShowcase().stream()
                .map(PublicIdentityQueryApiImpl::toDecoration)
                .toList();
        return new PublicIdentity.WornDecorations(
            toDecoration(vo.getAvatarFrame()),
            toDecoration(vo.getTitle()),
            toDecoration(vo.getPrimaryBadge()),
            showcase,
            toDecoration(vo.getCardBackground()),
            toDecoration(vo.getNameStyle()));
    }

    private static PublicIdentity.Decoration toDecoration(PublicIdentityVo.DecorationVo vo) {
        if (vo == null) {
            return null;
        }
        return new PublicIdentity.Decoration(
            vo.getAssetName(),
            vo.getType(),
            vo.getDisplayName(),
            vo.getUrl(),
            vo.getMediaType(),
            vo.getTitleMode(),
            vo.getTitleText(),
            vo.getTitleColor(),
            vo.getTitleBackground(),
            vo.getTitleBackgroundSecondary(),
            toNameStyle(vo.getNameStyle()),
            vo.getRarityName(),
            vo.getRarityDisplayName(),
            vo.getRarityColor(),
            vo.getGrantedAt(),
            vo.getExpiresAt());
    }

    private static PublicIdentity.NameStyle toNameStyle(UserDecorationAsset.NameStyle nameStyle) {
        if (nameStyle == null) {
            return null;
        }
        return new PublicIdentity.NameStyle(nameStyle.getMode(),
            nameStyle.getColors() == null ? List.of() : List.copyOf(nameStyle.getColors()));
    }

    private static PublicIdentity.DisplayConfig toDisplayConfig(
        PublicIdentityVo.DisplayConfigVo vo) {
        return new PublicIdentity.DisplayConfig(
            vo.isIdentityLineShowPrimaryBadge(),
            vo.getIdentityLineIdentityLimit(),
            vo.getUserCardShowcaseBadgeLimit(),
            vo.getUserCardIdentityLimit(),
            vo.getUserCardLinkTemplate());
    }
}
