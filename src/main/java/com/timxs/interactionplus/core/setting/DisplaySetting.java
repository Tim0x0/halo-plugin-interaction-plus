package com.timxs.interactionplus.core.setting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 展示配置，对应 settings.yaml 的 display 分组。
 * 字段默认值与 settings.yaml 保持一致，作为后端兜底。
 *
 * <p>类型开关与场景密度字段在 settings.yaml 内分别以 {@code $formkit: group name=types} 与
 * {@code name=density} 分组（官方分组写法），故 ConfigMap 数据中嵌套在 {@code types} /
 * {@code density} 对象下；这里用嵌套子对象承接，并保留扁平委托 getter 使调用方 API 不变。
 */
@Data
public class DisplaySetting {

    // 全局类型开关（$formkit:group 嵌套）
    private Types types = new Types();

    // 场景密度（$formkit:group 嵌套）
    private Density density = new Density();

    // Public identity 缓存 TTL（秒），0 表示关闭缓存
    private int publicIdentityCacheTtlSeconds = 30;

    // 用户卡头像 / 名字点击跳转链接模板，{name} 占位符替换为用户名；留空表示不跳转
    private String userCardLinkTemplate = "/authors/{name}";

    // ── 扁平委托：保持调用方按 display.isEnabledXxx() / getXxx() 直接读取 ──

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

    @JsonIgnore
    public boolean isIdentityLineShowPrimaryBadge() {
        return density.isIdentityLineShowPrimaryBadge();
    }

    @JsonIgnore
    public int getIdentityLineIdentityLimit() {
        return density.getIdentityLineIdentityLimit();
    }

    @JsonIgnore
    public int getUserCardShowcaseBadgeLimit() {
        return density.getUserCardShowcaseBadgeLimit();
    }

    @JsonIgnore
    public int getUserCardIdentityLimit() {
        return density.getUserCardIdentityLimit();
    }

    /**
     * 全局类型开关子配置（对应 settings.yaml decoration.display 下的 types 分组）。
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

    /**
     * 场景密度子配置（对应 settings.yaml decoration.display 下的 density 分组）。
     */
    @Data
    public static class Density {
        private boolean identityLineShowPrimaryBadge = true;
        private int identityLineIdentityLimit = 1;
        private int userCardShowcaseBadgeLimit = 5;
        private int userCardIdentityLimit = 3;
    }
}
