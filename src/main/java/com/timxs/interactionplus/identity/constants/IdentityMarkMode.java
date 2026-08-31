package com.timxs.interactionplus.identity.constants;

import org.jspecify.annotations.Nullable;

/**
 * 身份标识展示形态。三选一互斥：文字牌消费 color，图标消费 icon，图片消费 image。
 * spec 中以小写字符串存储，例如 {@code image}。
 *
 * <p>三种形态各占独立字段；后台切换形态只改当前生效项，另外两个原样留存。
 * 形态不进对外 DTO：读出口按形态挑一个填进 {@code IdentityMarkVo.icon} / {@code color}，
 * 消费端仍是「有 icon 渲染图，无 icon 渲染文字牌」。
 */
public enum IdentityMarkMode {

    TEXT("text"),
    ICON("icon"),
    IMAGE("image");

    private final String value;

    IdentityMarkMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public static IdentityMarkMode from(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (IdentityMarkMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * 解析展示形态：显式值优先；缺省或非法值按哪个字段非空推断（都为空则文字牌），
     * 兜住未带该字段的调用方。
     */
    public static IdentityMarkMode resolve(@Nullable String mode, @Nullable String icon,
        @Nullable String image) {
        var explicit = from(mode);
        if (explicit != null) {
            return explicit;
        }
        if (image != null && !image.isBlank()) {
            return IMAGE;
        }
        return icon != null && !icon.isBlank() ? ICON : TEXT;
    }

    /** 取本形态生效的图；文字牌形态没有图，返回 {@code null}。 */
    @Nullable
    public String pickSource(@Nullable String icon, @Nullable String image) {
        return switch (this) {
            case ICON -> icon;
            case IMAGE -> image;
            case TEXT -> null;
        };
    }
}
