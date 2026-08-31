package com.timxs.interactionplus.template.extension;

import static com.timxs.interactionplus.core.constants.InteractionPlusConst.GROUP;
import static com.timxs.interactionplus.core.constants.InteractionPlusConst.VERSION;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * 前台组件自定义模板：站长贴 HTML + CSS 完全接管 {@code hip-*} 组件的渲染。
 *
 * <p>固定三条记录，{@code metadata.name} 即组件名（identity / avatar / card），
 * 插件启动时确保存在。{@code enabled=false} 或内容为空白时走内置默认模板。
 *
 * <p>⚠ {@code html} 可含 {@code <script>}，等价于给出页面 JS 执行权限，
 * 因此不发放角色模板 —— 仅超级管理员可写（见 roleTemplate.yaml 说明）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = "CustomTemplate",
    plural = "customtemplates", singular = "customtemplate")
public class CustomTemplate extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "CustomTemplateSpec")
    public static class Spec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"identity", "avatar", "card"},
            description = "目标组件，与 metadata.name 一致")
        private String component;

        @Schema(description = "是否启用；false 时走内置默认模板")
        private Boolean enabled;

        @Schema(description = "HTML 片段，可含 <script>（在组件 ShadowRoot 内执行）")
        private String html;

        @Schema(description = "纯 CSS，渲染时自动包 <style>")
        private String css;
    }
}
