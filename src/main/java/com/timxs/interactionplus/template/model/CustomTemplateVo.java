package com.timxs.interactionplus.template.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 公开模板视图：前台 runtime 消费。
 *
 * <p>只出现在响应里的都是「已启用且有内容」的模板 —— 未启用、或开了开关但内容还没写的
 * 一律不下发（空模板会让组件渲染成空白，比不自定义更糟）。runtime 因此可以直接
 * 「响应里有 = 用自定义，没有 = 用内置默认」，不必自己判空。
 *
 * <p>不含 {@code enabled}：对前台没有意义，出现在响应里本身就等于启用。
 */
@Data
public class CustomTemplateVo {

    @Schema(description = "目标组件：identity / avatar / card")
    private String component;

    @Schema(description = "HTML 片段，可含 <script>")
    private String html;

    @Schema(description = "纯 CSS，渲染时由 runtime 包 <style>")
    private String css;
}
