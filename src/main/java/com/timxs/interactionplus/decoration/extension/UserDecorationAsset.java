package com.timxs.interactionplus.decoration.extension;

import static com.timxs.interactionplus.core.constants.InteractionPlusConst.GROUP;
import static com.timxs.interactionplus.core.constants.InteractionPlusConst.HEX_COLOR_PATTERN;
import static com.timxs.interactionplus.core.constants.InteractionPlusConst.VERSION;

import com.timxs.interactionplus.decoration.constants.DecorationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * 用户装饰资产。
 *
 * <p>装饰类型固定 5 类：badge / avatar_frame / title / card_background / name_style。
 * 状态：draft / active / disabled / archived。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = "UserDecorationAsset",
    plural = "userdecorationassets", singular = "userdecorationasset")
public class UserDecorationAsset extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    /** 资产是否处于启用状态（可授予 / 可佩戴 / 可对外展示）。 */
    public boolean isActive() {
        return spec != null && DecorationStatus.ACTIVE.getValue().equals(spec.getStatus());
    }

    /** 资产是否为草稿（从未启用，允许物理删除与全量编辑）。 */
    public boolean isDraft() {
        return spec != null && DecorationStatus.DRAFT.getValue().equals(spec.getStatus());
    }

    @Data
    @Schema(name = "UserDecorationAssetSpec")
    public static class Spec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "装饰类型：badge/avatar_frame/title/card_background/name_style")
        private String type;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100,
            description = "显示名称")
        private String displayName;

        @Schema(maxLength = 500, description = "描述")
        private String description;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "状态：draft/active/disabled/archived")
        private String status;

        @Schema(description = "分类内部名，最多 1 个，可为空")
        private String categoryName;

        @Schema(description = "标签内部名列表，最多 5 个")
        private List<String> tagNames;

        @Schema(description = "稀有度内部名，最多 1 个，可为空")
        private String rarityName;

        @Schema(description = "素材引用")
        private AssetRef asset;

        @Schema(description = "类型扩展字段")
        private Payload payload;

        @Schema(description = "投稿者用户名；为空表示管理员创建")
        private String submittedBy;

        @Schema(description = "创建者用户名")
        private String createdBy;

        @Schema(description = "最后更新者用户名")
        private String updatedBy;

        @Schema(description = "启用时间")
        private Instant enabledAt;

        @Schema(description = "停用时间")
        private Instant disabledAt;

        @Schema(description = "归档时间")
        private Instant archivedAt;
    }

    /**
     * 素材引用，存附件 permalink 与媒体类型等通用信息。
     */
    @Data
    @Schema(name = "UserDecorationAssetRef")
    public static class AssetRef {

        @Schema(description = "素材访问地址（来自 Halo 附件）")
        private String url;

        @Schema(description = "媒体类型，例如 image/png")
        private String mediaType;

        @Schema(description = "文件大小（字节），仅用于提示")
        private Long sizeBytes;

        @Schema(description = "宽度（像素），仅用于提示")
        private Integer width;

        @Schema(description = "高度（像素），仅用于提示")
        private Integer height;
    }

    /**
     * 类型扩展字段：称号名称、昵称颜色配置等。
     */
    @Data
    @Schema(name = "UserDecorationAssetPayload")
    public static class Payload {

        @Schema(maxLength = 30,
            description = "称号名称（type=title，必填）：行内场景（评论等）展示它，"
                + "同时作为称号图片的替代文本与加载失败兜底")
        private String titleText;

        @Schema(pattern = HEX_COLOR_PATTERN,
            description = "称号文字颜色（type=title，可选；#RGB / #RRGGBB / #RRGGBBAA，空=继承）")
        private String titleColor;

        @Schema(pattern = HEX_COLOR_PATTERN,
            description = "称号背景颜色（type=title，可选；#RGB / #RRGGBB / #RRGGBBAA，空=无底）")
        private String titleBackground;

        @Schema(pattern = HEX_COLOR_PATTERN,
            description = "称号背景第二色（type=title，可选）；存在时与背景色形成线性渐变")
        private String titleBackgroundSecondary;

        @Schema(description = "昵称样式配置（type=name_style）")
        private NameStyle nameStyle;
    }

    /**
     * 昵称样式。V1 只支持文字颜色：纯色 1 个颜色，渐变 2-3 个颜色。
     * 前台由插件生成受控 CSS，不存储任意 CSS 字符串。
     */
    @Data
    @Schema(name = "UserDecorationNameStyle")
    public static class NameStyle {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "颜色模式：solid/gradient")
        private String mode;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1,
            description = "颜色列表：纯色 1 个，渐变 2-3 个")
        private List<String> colors;
    }
}
