package com.timxs.interactionplus.core.setting;

import lombok.Data;

/**
 * 展示配置，对应 settings.yaml 的 decoration.display 分组。
 * 字段默认值与 settings.yaml 保持一致，作为后端兜底。
 *
 * <p>只管<b>展示策略</b>（哪个组件显示什么、显示几个）：数据后端照常给全量，
 * 由前台组件按场景裁剪自己那一份。功能总闸（关掉某类型则后端整个不吐该类型数据）
 * 在 {@link BasicSetting}，两者不是一层东西。
 *
 * <p>三个组件场景在 settings.yaml 内分别以 {@code $formkit: group}
 * {@code identityLine} / {@code avatar} / {@code userCard} 分组（官方分组写法），
 * 故 ConfigMap 数据中嵌套在对应对象下。
 *
 * <p>⚠ <b>五处同步清单</b>：这套默认值在下列位置各有一份（后端读不到前端常量，
 * 两个前端产物又刻意不共包，每层都要自己的兜底）。<b>增删设置项时五处全改</b>：
 * <ol>
 *   <li>{@code src/main/resources/extensions/settings.yaml}（真值源，站长实际配的）</li>
 *   <li>本类（后端读设置的兜底）</li>
 *   <li>{@code identity/model/PublicIdentityVo.java}（后端出口 DTO 的兜底）</li>
 *   <li>{@code runtime/src/hip-data.ts}（前台组件的兜底）</li>
 *   <li>{@code ui/src/utils/preview-identity.ts}（后台预览的兜底）</li>
 * </ol>
 * 漏改任一处都不报错，表现只是该层的默认行为与其余层不一致。
 *
 * <p>{@link BasicSetting} 的类型总闸不在此清单内——它不进 {@code display} 快照，
 * 只有 settings.yaml 与那个类两份副本。
 */
@Data
public class DisplaySetting {

    private IdentityLine identityLine = new IdentityLine();

    private Avatar avatar = new Avatar();

    private UserCard userCard = new UserCard();

    private String userCardLinkTemplate = "/authors/{name}";

    /**
     * 无头像占位风格：{@code halo} 灰底首字母；{@code hash} 按显示名着色。
     * 非法值由读出口收成 {@code halo}，不在这里钳。
     */
    private String avatarFallbackStyle = "halo";

    /**
     * 身份行场景（{@code hip-user-identity}）。
     */
    @Data
    public static class IdentityLine {
        private boolean showTitle = true;
        private boolean showPrimaryBadge = true;
        private boolean showNameStyle = true;
        private boolean showIdentityMarks = true;
        private int identityLimit = 1;
    }

    /**
     * 头像场景（{@code hip-user-avatar}）。
     */
    @Data
    public static class Avatar {
        private boolean showFrame = true;
    }

    /**
     * 用户卡场景（{@code hip-user-card}）。
     */
    @Data
    public static class UserCard {
        private boolean showTitle = true;
        private boolean showPrimaryBadge = true;
        private boolean showShowcase = true;
        private boolean showNameStyle = true;
        private boolean showIdentityMarks = true;
        private boolean showAvatarFrame = true;
        private boolean showCardBackground = true;
        private int showcaseBadgeLimit = 5;
        private int identityLimit = 3;
    }
}
