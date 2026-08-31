package com.timxs.interactionplus.core.setting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 基础配置，对应 settings.yaml 的 basic 分组：站点级、与具体前台组件无关的项。
 *
 * <p>与 {@link DisplaySetting} 的分层：本类是<b>功能总闸</b>（关掉某类型，后端读出口
 * 直接不吐该类型的数据，任何组件、Finder、其它插件都拿不到），{@code DisplaySetting}
 * 是<b>展示策略</b>（数据照常给，由前台组件按场景裁剪自己那一份）。
 *
 * <p>因为总闸在后端就把数据摘掉、不进 {@code PublicIdentity.display} 快照，
 * 所以它<b>不在「五处同步清单」里</b>——只有 settings.yaml 与本类两份副本。
 *
 * <p>类型开关在 settings.yaml 内以 {@code $formkit: group} {@code types} 分组
 * （官方分组写法），故 ConfigMap 数据中嵌套在 {@code types} 对象下。本类保留扁平委托
 * getter，使装配侧仍按 {@code basic.isEnabledXxx()} 读取。
 */
@Data
public class BasicSetting {

    private Types types = new Types();

    private int publicIdentityCacheTtlSeconds = 30;

    // ── 扁平委托：保持调用方按 basic.isEnabledXxx() 直接读取类型总闸 ──

    @JsonIgnore
    public boolean isEnabledBadge() {
        return types.isEnabledBadge();
    }

    @JsonIgnore
    public boolean isEnabledAvatarFrame() {
        return types.isEnabledAvatarFrame();
    }

    @JsonIgnore
    public boolean isEnabledTitle() {
        return types.isEnabledTitle();
    }

    @JsonIgnore
    public boolean isEnabledNameStyle() {
        return types.isEnabledNameStyle();
    }

    @JsonIgnore
    public boolean isEnabledCardBackground() {
        return types.isEnabledCardBackground();
    }

    @JsonIgnore
    public boolean isEnabledIdentityMark() {
        return types.isEnabledIdentityMark();
    }

    /**
     * 全局类型开关子配置（对应 settings.yaml basic 组下的 types 分组）。
     */
    @Data
    public static class Types {
        private boolean enabledBadge = true;
        private boolean enabledAvatarFrame = true;
        private boolean enabledTitle = true;
        private boolean enabledNameStyle = true;
        private boolean enabledCardBackground = true;
        private boolean enabledIdentityMark = true;
    }
}
