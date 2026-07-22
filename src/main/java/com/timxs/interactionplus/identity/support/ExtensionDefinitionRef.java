package com.timxs.interactionplus.identity.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * Halo 核心 {@code ExtensionDefinition} 的只读引用类（复制 GVK 模式）。
 *
 * <p>官方类位于 Halo application 模块，插件编译期不可见；本类按相同 GVK 声明、
 * 只保留统计贡献溯源所需字段，经 {@code ReactiveExtensionClient} 直接查询
 * （scheme 已由 Halo 核心注册，本插件<b>不得</b>重复注册本类）。
 * 仅用于读取，禁止用本类做任何写操作。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@GVK(group = "plugin.halo.run", version = "v1alpha1", kind = "ExtensionDefinition",
    plural = "extensiondefinitions", singular = "extensiondefinition")
public class ExtensionDefinitionRef extends AbstractExtension {

    private Spec spec;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Spec {

        /** 扩展实现类全名。 */
        private String className;

        /** 所属扩展点（EPD 的 metadata.name）。 */
        private String extensionPointName;
    }
}
