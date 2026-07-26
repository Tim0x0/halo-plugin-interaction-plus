package com.timxs.interactionplus.decoration.constants;

import org.jspecify.annotations.Nullable;

/**
 * 装饰类型。V1 固定 5 类普通装饰，不开放后台自定义类型。
 * spec 中以小写下划线字符串存储，例如 {@code avatar_frame}。
 */
public enum DecorationType {

    BADGE("badge"),
    AVATAR_FRAME("avatar_frame"),
    TITLE("title"),
    CARD_BACKGROUND("card_background"),
    NAME_STYLE("name_style");

    private final String value;

    DecorationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public static DecorationType from(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (DecorationType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
