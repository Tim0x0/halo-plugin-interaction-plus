package com.timxs.interactionplus.template.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 自定义模板保存请求体（Console）。
 *
 * <p>{@code component} 由路径决定，不在请求体里传，避免路径与体不一致。
 */
@Data
public class CustomTemplateParam {

    @Schema(description = "是否启用；false 时前台走内置默认模板")
    private Boolean enabled;

    @Schema(description = "HTML 片段，可含 <script>")
    private String html;

    @Schema(description = "纯 CSS")
    private String css;
}
