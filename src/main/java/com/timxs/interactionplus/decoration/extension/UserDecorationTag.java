package com.timxs.interactionplus.decoration.extension;

import static com.timxs.interactionplus.core.constants.InteractionPlusConst.GROUP;
import static com.timxs.interactionplus.core.constants.InteractionPlusConst.HEX_COLOR_PATTERN;
import static com.timxs.interactionplus.core.constants.InteractionPlusConst.VERSION;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * 用户装饰标签。单个装饰最多 5 个标签，可为空。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = "UserDecorationTag",
    plural = "userdecorationtags", singular = "userdecorationtag")
public class UserDecorationTag extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "UserDecorationTagSpec")
    public static class Spec implements OrderedMetadataSpec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50,
            description = "显示名称")
        private String displayName;

        @Schema(maxLength = 200, description = "描述")
        private String description;

        @Schema(pattern = HEX_COLOR_PATTERN,
            description = "标签颜色（#RGB / #RRGGBB / #RRGGBBAA，可空）")
        private String color;

        @Schema(description = "是否启用")
        private Boolean enabled;

        @Schema(description = "排序值，越小越靠前")
        private Integer displayOrder;
    }
}
