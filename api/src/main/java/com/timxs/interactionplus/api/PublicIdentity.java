package com.timxs.interactionplus.api;

import java.time.Instant;
import java.util.List;

/**
 * 用户公开身份聚合数据（{@link PublicIdentityQueryApi} 的返回项）。
 *
 * <p>与公开 HTTP API {@code GET /identity/{userName}} 共用同一聚合源，当前身份与佩戴装饰的
 * 展示字段口径一致。全部展示信息自包含（稀有度显示名与颜色等已内联），消费方零二次查询；
 * 可直接喂给 hip-* runtime 组件的 data 模式渲染。只包含允许公开的字段，不含邮箱、角色列表、
 * 授予历史等私有数据。
 *
 * <p><b>API 契约版本</b>：{@code 1.0.0}。消费方只读取业务需要的字段，不对整个对象做穷举式字段校验。
 * 列表字段永不为 {@code null}（无则空列表）。
 *
 * @param userName      用户名（{@code metadata.name}）。进程内做关联 / 缓存键；
 *                      喂给 hip-* 的 data 模式时必须保留——默认跳转模板
 *                      {@code /authors/{name}} 用它拼 href
 * @param displayName   显示名称（用户未设置时回落为用户名）
 * @param avatar        头像地址（绝对链接，可空）
 * @param bio           公开简介（可空）
 * @param registeredAt  注册时间（恒有值：{@code spec.registeredAt} 缺失时回退资源创建时间）
 * @param identityMarks 身份标识（如「管理员」，按角色映射、系统赋予、无需佩戴），按优先级降序
 * @param decorations   当前佩戴且有效的装扮（非装饰墙全量）
 * @param stats         互动统计（文章 / 评论 / 装扮计数 / 外部插件贡献项）
 * @param display       <b>站点级</b>展示策略快照（按组件场景的开关 / 密度 / 跳转 / 无头像占位风格）——不是该用户的属性，
 *                      自行渲染界面的消费方可整个忽略
 * @author Tim0x0
 * @since 1.0.0
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
     * 身份标识展示项。三形态择一渲染，{@code icon} 与 {@code color} <b>互斥</b>（至多其一非空）：
     *
     * <ul>
     *   <li><b>文字牌</b>：{@code color} 非空、{@code icon} 为 null——按该色渲染边框 + 文字；</li>
     *   <li><b>图标</b>：{@code icon} 为 data URL（Iconify 字形）、{@code color} 为 null；</li>
     *   <li><b>图片</b>：{@code icon} 为图片地址（绝对链接）、{@code color} 为 null。</li>
     * </ul>
     *
     * <p>按 {@code icon} 是否为空分流即可。
     *
     * @param displayName 显示名称（如「管理员」）；恒有值，兼作图标 / 图片的悬停提示与加载失败时的文字牌回落
     * @param icon        图标 data URL 或图片地址（可空；非空时 {@code color} 恒为 null）
     * @param color       文字牌颜色（可空；非空时 {@code icon} 恒为 null）
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
     * @param url                      素材地址（绝对链接；昵称样式无素材时为空。
     *                                 称号的图是可选增强：配了图在此给出地址，没配则为空）
     * @param mediaType                素材 MIME 类型（可空）
     * @param titleText                称号名称（仅 type=title，恒非空）：行内场景展示它，
     *                                 同时作为称号图的替代文本与加载失败兜底
     * @param titleColor               称号文字颜色（仅 type=title，可空；
     *                                 {@code #RGB} / {@code #RRGGBB} / {@code #RRGGBBAA}，空=继承）
     * @param titleBackground          称号背景颜色（仅 type=title，可空；同上，空=无底）
     * @param titleBackgroundSecondary 称号背景第二色（可选渐变，仅 type=title，可空）
     * @param nameStyle                昵称样式（仅 type=name_style）
     * @param rarityName               稀有度标识（可空）
     * @param rarityDisplayName        稀有度显示名（内联，可空）
     * @param rarityColor              稀有度颜色（内联，可空）
     */
    public record Decoration(
        String assetName,
        String type,
        String displayName,
        String url,
        String mediaType,
        String titleText,
        String titleColor,
        String titleBackground,
        String titleBackgroundSecondary,
        NameStyle nameStyle,
        String rarityName,
        String rarityDisplayName,
        String rarityColor
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
     * 站点级展示策略快照（站长在后台按组件场景配置的开关、密度、跳转模板与无头像占位风格）。
     *
     * <p><b>不是该用户的属性</b>：这几项全站一份，与查的是谁无关，随站长改设置而变。
     * 内联在此只为让 {@code data} 模式零二次查询——把整份喂给 hip-* 就能画。
     * <b>自行渲染界面的消费方可整个忽略</b>；尤其别拿 {@code userCard.identityLimit}
     * 之类去裁剪并非用户卡的界面，那不是给你的场景定的。
     *
     * @param identityLine         身份行（{@code hip-user-identity}）开关与密度
     * @param avatar               头像（{@code hip-user-avatar}）开关
     * @param userCard             用户卡（{@code hip-user-card}）开关与密度
     * @param userCardLinkTemplate 昵称 / 用户卡头像的跳转链接模板（{name} = 用户名），空表示不跳转
     * @param avatarFallbackStyle  无头像占位风格：{@code halo} 灰底首字母；{@code hash} 按显示名着色。
     *                             只作用于内置 {@code renderAvatar} 占位，不改 {@code avatar} 字段
     */
    public record DisplayConfig(
        IdentityLineDisplay identityLine,
        AvatarDisplay avatar,
        UserCardDisplay userCard,
        String userCardLinkTemplate,
        String avatarFallbackStyle
    ) {
    }

    /**
     * 身份行场景策略。
     *
     * @param showTitle         是否展示称号
     * @param showPrimaryBadge  是否展示主勋章
     * @param showNameStyle     是否应用昵称样式
     * @param showIdentityMarks 是否展示身份标识
     * @param identityLimit     身份标识数量上限（1–3）
     */
    public record IdentityLineDisplay(
        boolean showTitle,
        boolean showPrimaryBadge,
        boolean showNameStyle,
        boolean showIdentityMarks,
        int identityLimit
    ) {
    }

    /**
     * 头像场景策略。
     *
     * @param showFrame 是否叠放头像框
     */
    public record AvatarDisplay(
        boolean showFrame
    ) {
    }

    /**
     * 用户卡场景策略。
     *
     * @param showTitle           是否展示称号
     * @param showPrimaryBadge    是否展示主勋章
     * @param showShowcase        是否展示勋章展柜
     * @param showNameStyle       是否应用昵称样式
     * @param showIdentityMarks   是否展示身份标识
     * @param showAvatarFrame     是否叠放头像框
     * @param showCardBackground  是否展示名片背景
     * @param showcaseBadgeLimit  展柜勋章数量上限（0–8）
     * @param identityLimit       身份标识数量上限（1–5）
     */
    public record UserCardDisplay(
        boolean showTitle,
        boolean showPrimaryBadge,
        boolean showShowcase,
        boolean showNameStyle,
        boolean showIdentityMarks,
        boolean showAvatarFrame,
        boolean showCardBackground,
        int showcaseBadgeLimit,
        int identityLimit
    ) {
    }
}
