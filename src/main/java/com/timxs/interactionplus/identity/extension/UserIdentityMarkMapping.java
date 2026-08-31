package com.timxs.interactionplus.identity.extension;

import static com.timxs.interactionplus.core.constants.InteractionPlusConst.GROUP;
import static com.timxs.interactionplus.core.constants.InteractionPlusConst.HEX_COLOR_PATTERN;
import static com.timxs.interactionplus.core.constants.InteractionPlusConst.VERSION;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * 用户身份标识映射：Halo 原生角色 -&gt; 身份标识展示。
 *
 * <p>身份标识不进入用户库存、不占佩戴槽位；用户失去角色后自动不展示；
 * 角色不存在时映射保留，运行时标记失效，前台不展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = "UserIdentityMarkMapping",
    plural = "useridentitymarkmappings", singular = "useridentitymarkmapping")
public class UserIdentityMarkMapping extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "UserIdentityMarkMappingSpec")
    public static class Spec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Halo 角色名，唯一")
        private String roleName;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50,
            description = "身份标识显示名称")
        private String displayName;

        @Schema(allowableValues = {"text", "icon", "image"},
            description = "展示形态：text=文字牌（用 color），icon=图标库字形（用 icon），"
                + "image=上传图（用 image）。缺省时按哪个字段非空推断")
        private String displayMode;

        @Schema(description = "图标库字形（data URL；颜色只在 Iconify 选择器里写入 SVG）")
        private String icon;

        @Schema(description = "上传图地址（附件）")
        private String image;

        @Schema(pattern = HEX_COLOR_PATTERN,
            description = "文字牌颜色（仅 text 形态生效；#RGB / #RRGGBB / #RRGGBBAA，空=模板默认铬件）")
        private String color;

        @Schema(description = "优先级，越大越靠前")
        private Integer priority;

        @Schema(description = "是否启用")
        private Boolean enabled;
    }
}
