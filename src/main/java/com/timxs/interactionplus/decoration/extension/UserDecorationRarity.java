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
 * 用户装饰稀有度。单个装饰最多 1 个稀有度，可为空。
 *
 * <p>插件首次启动时默认创建：普通、稀有、史诗、传说、限定。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = "UserDecorationRarity",
    plural = "userdecorationrarities", singular = "userdecorationrarity")
public class UserDecorationRarity extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "UserDecorationRaritySpec")
    public static class Spec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50,
            description = "显示名称")
        private String displayName;

        @Schema(maxLength = 200, description = "描述")
        private String description;

        @Schema(pattern = HEX_COLOR_PATTERN,
            description = "稀有度颜色（#RGB / #RRGGBB / #RRGGBBAA，可空）")
        private String color;

        @Schema(description = "是否启用")
        private Boolean enabled;

        @Schema(description = "排序值，越小越靠前")
        private Integer displayOrder;

        @Schema(description = "是否允许外部插件经对外 API 发放该稀有度下的装饰；为空视为允许（默认开）")
        private Boolean externalGrantable;
    }
}
