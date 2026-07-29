package com.timxs.interactionplus.identity.model;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 公开身份聚合数据。只返回允许公开的字段，
 * 不包含邮箱、角色列表、授予历史、撤销原因、审计字段等。
 */
@Data
public class PublicIdentityVo {

    private String userName;
    private String displayName;
    private String avatar;

    /** 用户公开简介（User.spec.bio），用于卡片 / 主页展示。 */
    private String bio;

    /** 注册时间（User.spec.registeredAt），用于卡片展示「加入时间」。 */
    private Instant registeredAt;

    /** 公开身份标识，按优先级降序，最多 10 个。 */
    private List<IdentityMarkVo> identityMarks = new ArrayList<>();

    /** 当前有效且允许展示的装饰。 */
    private DecorationsVo decorations = new DecorationsVo();

    /** 互动统计（文章 / 评论 / 装扮计数 / 外部插件贡献项）。 */
    private StatsVo stats = new StatsVo();

    /** 展示密度配置快照，供前台组件按场景裁剪。 */
    private DisplayConfigVo display = new DisplayConfigVo();

    /**
     * 身份标识展示项。
     */
    @Data
    public static class IdentityMarkVo {
        private String displayName;
        private String icon;
        private String color;
        private Integer priority;
    }

    /**
     * 各槽位装饰展示数据。
     */
    @Data
    public static class DecorationsVo {
        private DecorationVo avatarFrame;
        private DecorationVo title;
        private DecorationVo primaryBadge;
        private List<DecorationVo> badgeShowcase = new ArrayList<>();
        private DecorationVo cardBackground;
        private DecorationVo nameStyle;
    }

    /**
     * 单个装饰展示数据。
     */
    @Data
    public static class DecorationVo {
        private String assetName;
        private String type;
        private String displayName;
        private String url;
        private String mediaType;
        /** 称号形态：text=文字牌 / image=整图（type=title）。 */
        private String titleMode;
        /** 称号文本（type=title）；image 形态下为替代文本与加载失败回落。 */
        private String titleText;
        /** 称号文字颜色。 */
        private String titleColor;
        /** 称号背景颜色。 */
        private String titleBackground;
        /** 称号背景第二色（可选，渐变）。 */
        private String titleBackgroundSecondary;
        /** 昵称样式（type=name_style）。 */
        private UserDecorationAsset.NameStyle nameStyle;
        /** 稀有度内部名（用于前台样式区分，可为空）。 */
        private String rarityName;
        /** 稀有度显示名（内联，前台可直接展示，可为空）。 */
        private String rarityDisplayName;
        /** 稀有度颜色（内联，前台可直接展示，可为空）。 */
        private String rarityColor;
        /** 获得时间（仅装饰墙填充，可为空）。 */
        private Instant grantedAt;
        /** 有效期；null 表示永久（仅装饰墙填充）。 */
        private Instant expiresAt;
    }

    /**
     * 互动统计。
     */
    @Data
    public static class StatsVo {
        /** 公开文章数（已发布、未删除、可见性为公开）。 */
        private long posts;
        /** 公开评论数（已审核、未隐藏）。 */
        private long comments;
        /** 装扮计数；null 表示用户关闭了公开装扮墙。 */
        private DecorationCountsVo decorations;
        /** 外部插件经 UserStatContributor 贡献的统计项。 */
        private List<ContributedStatVo> extras = new ArrayList<>();
    }

    /**
     * 按装扮类型的持有计数（有效授予且资产可用，按资产去重）。
     */
    @Data
    public static class DecorationCountsVo {
        private long total;
        private long badge;
        private long avatarFrame;
        private long title;
        private long nameStyle;
        private long cardBackground;
    }

    /**
     * 外部插件贡献的统计项（source 由系统按 ExtensionDefinition 归属标注）。
     */
    @Data
    public static class ContributedStatVo {
        private String source;
        private String key;
        private String label;
        private String value;
    }

    /**
     * 展示密度配置快照。
     */
    @Data
    public static class DisplayConfigVo {
        private boolean identityLineShowPrimaryBadge = true;
        private int identityLineIdentityLimit = 1;
        private int userCardShowcaseBadgeLimit = 5;
        private int userCardIdentityLimit = 3;
        /** 用户卡头像 / 名字跳转链接模板（{name} = 用户名），空串表示不跳转。 */
        private String userCardLinkTemplate = "/authors/{name}";
    }
}
