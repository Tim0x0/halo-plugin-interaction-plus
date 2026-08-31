package com.timxs.interactionplus.template.constants;

import org.jspecify.annotations.Nullable;

/**
 * 可自定义模板的前台组件。与 {@code hip-*} Web Component 一一对应，
 * 同时用作 {@code CustomTemplate} 的 {@code metadata.name}（固定三条记录）。
 *
 * <p>三个组件均已接入渲染引擎（内置默认样子本身就是一份模板），各自独立开关。
 */
public enum TemplateComponent {

    IDENTITY("identity"),
    AVATAR("avatar"),
    CARD("card");

    private final String value;

    TemplateComponent(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public static TemplateComponent from(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (TemplateComponent component : values()) {
            if (component.value.equals(value)) {
                return component;
            }
        }
        return null;
    }
}
