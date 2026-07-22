package com.timxs.interactionplus.api;

import java.time.Instant;
import java.util.List;

/**
 * 用户公开身份聚合数据（{@link PublicIdentityQueryApi} 的返回项）。
 *
 * <p>与公开 HTTP API {@code GET /identity/{userName}} 的 JSON 响应<b>同构</b>（字段一一对应），
 * 自包含全部展示信息（稀有度显示名与颜色等已内联），消费方零二次查询；可直接（剥掉
 * {@code userName} 后）喂给 hip-* runtime 组件的 data 模式渲染。只包含允许公开的字段，
 * 不含邮箱、角色列表、授予历史等私有数据。
 *
 * <p><b>前向兼容</b>：未来可能<b>新增字段</b>（如等级 / 积分上线后的展示属性），
 * 消费方请忽略未知字段，勿做穷举式强校验。列表字段永不为 {@code null}（无则空列表）。
 *
 * @param userName      用户名（{@code metadata.name}）；进程内做关联 / 缓存键用，
 *                      <b>下发浏览器前必须剥掉</b>（见 {@link PublicIdentityQueryApi} 脱敏责任）
 * @param displayName   显示名称（用户未设置时回落为用户名）
 * @param avatar        头像地址（绝对链接，可空）
 * @param bio           公开简介（可空）
 * @param registeredAt  注册时间（可空）
 * @param identityMarks 身份标识（如「管理员」，按角色映射、系统赋予、无需佩戴），按优先级降序
 * @param decorations   当前佩戴且有效的装扮（非装饰墙全量）
 * @param stats         互动统计（文章 / 评论 / 装扮计数 / 外部插件贡献项）
 * @param display       展示密度配置快照，供按场景裁剪展示数量
 * @author Tim0x0
 */
public record PublicIdentity(
    String userName,
    String displayName,
    String avatar,
    String bio,
    Instant registeredAt,
    List<IdentityMark> identityMarks,
    WornDecorations decorations,
    Stats stats,
    DisplayConfig display
) {

    /**
     * 身份标识展示项。
     *
     * @param displayName 显示名称（如「管理员」）
     * @param icon        图标地址（绝对链接，可空）
     * @param color       颜色（可空）
     * @param priority    优先级（越大越靠前；列表已按其降序）
     */
    public record IdentityMark(
        String displayName,
        String icon,
        String color,
        Integer priority
    ) {
    }

    /**
     * 各槽位当前佩戴的装扮。槽位为 {@code null} 表示未佩戴、佩戴已失效或该类型被站长全局停用。
     *
     * @param avatarFrame    头像框
     * @param title          称号
     * @param primaryBadge   主勋章
     * @param badgeShowcase  勋章展示柜（最多 8 个）
     * @param cardBackground 名片背景
     * @param nameStyle      昵称样式
     */
    public record WornDecorations(
        Decoration avatarFrame,
        Decoration title,
        Decoration primaryBadge,
        List<Decoration> badgeShowcase,
        Decoration cardBackground,
        Decoration nameStyle
    ) {
    }

    /**
     * 单个装扮的自包含展示数据。
     *
     * @param assetName                装扮标识（{@code metadata.name}）
     * @param type                     类型：badge / avatar_frame / title / card_background / name_style
     * @param displayName              显示名称
     * @param url                      素材地址（绝对链接；文字牌称号 / 昵称样式无素材时为空，
     *                                 整图称号（titleMode=image）在此给出图片地址）
     * @param mediaType                素材 MIME 类型（可空）
     * @param titleMode                称号形态：text=文字牌 / image=整图（仅 type=title）
     * @param titleText                称号文本（仅 type=title）；image 形态下为图片替代文本与加载失败回落
     * @param titleColor               称号文字颜色（仅 type=title，text 形态生效）
     * @param titleBackground          称号背景颜色（仅 type=title，text 形态生效）
     * @param titleBackgroundSecondary 称号背景第二色（可选渐变，仅 type=title，text 形态生效）
     * @param nameStyle                昵称样式（仅 type=name_style）
     * @param rarityName               稀有度标识（可空）
     * @param rarityDisplayName        稀有度显示名（内联，可空）
     * @param rarityColor              稀有度颜色（内联，可空）
     * @param grantedAt                获得时间（仅装饰墙类查询填充，本接口当前恒为 null）
     * @param expiresAt                有效期，null 表示永久（仅装饰墙类查询填充，本接口当前恒为 null）
     */
    public record Decoration(
        String assetName,
        String type,
        String displayName,
        String url,
        String mediaType,
        String titleMode,
        String titleText,
        String titleColor,
        String titleBackground,
        String titleBackgroundSecondary,
        NameStyle nameStyle,
        String rarityName,
        String rarityDisplayName,
        String rarityColor,
        Instant grantedAt,
        Instant expiresAt
    ) {
    }

    /**
     * 昵称样式。
     *
     * @param mode   颜色模式：solid / gradient
     * @param colors 颜色列表：纯色 1 个，渐变 2-3 个
     */
    public record NameStyle(
        String mode,
        List<String> colors
    ) {
    }

    /**
     * 互动统计。文章 / 评论为公开口径的索引计数；装扮计数尊重用户的「公开装扮墙」开关；
     * {@code extras} 为其他插件经 {@code UserStatContributor} 扩展点贡献的统计项。
     *
     * @param posts       公开文章数（已发布、未删除、可见性为公开）
     * @param comments    公开评论数（已审核、未隐藏）
     * @param decorations 装扮计数；{@code null} 表示用户关闭了公开装扮墙（消费方视为不可用）
     * @param extras      外部插件贡献的统计项（永不为 {@code null}，无则空列表）
     */
    public record Stats(
        long posts,
        long comments,
        DecorationCounts decorations,
        List<ContributedStat> extras
    ) {
    }

    /**
     * 按装扮类型的持有计数（有效授予且资产可用，按资产去重）。
     *
     * @param total          全部装扮总数（各类型之和）
     * @param badge          勋章数
     * @param avatarFrame    头像框数
     * @param title          称号数
     * @param nameStyle      昵称样式数
     * @param cardBackground 名片背景数
     */
    public record DecorationCounts(
        long total,
        long badge,
        long avatarFrame,
        long title,
        long nameStyle,
        long cardBackground
    ) {
    }

    /**
     * 外部插件贡献的统计项。完整标识为 {@code source + key}（不同插件同名 key 互不冲突）。
     *
     * @param source 来源插件 id（由系统按 ExtensionDefinition 归属标注，贡献方不可自报）
     * @param key    机器标识（贡献插件内唯一）
     * @param label  展示名（如「采纳」）
     * @param value  展示值文本（如 {@code "23"}、{@code "Lv.16"}）
     */
    public record ContributedStat(
        String source,
        String key,
        String label,
        String value
    ) {
    }

    /**
     * 展示密度配置快照（站长在后台配置的各场景展示数量）。
     *
     * @param identityLineShowPrimaryBadge 身份行是否展示主勋章
     * @param identityLineIdentityLimit    身份行身份标识数量上限
     * @param userCardShowcaseBadgeLimit   用户卡展示柜勋章数量上限
     * @param userCardIdentityLimit        用户卡身份标识数量上限
     */
    public record DisplayConfig(
        boolean identityLineShowPrimaryBadge,
        int identityLineIdentityLimit,
        int userCardShowcaseBadgeLimit,
        int userCardIdentityLimit
    ) {
    }
}
