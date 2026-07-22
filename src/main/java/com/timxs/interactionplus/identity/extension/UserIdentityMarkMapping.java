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

        @Schema(description = "图标地址或图标标识")
        private String icon;

        @Schema(pattern = HEX_COLOR_PATTERN, description = "标识颜色")
        private String color;

        @Schema(description = "优先级，越大越靠前")
        private Integer priority;

        @Schema(description = "是否启用")
        private Boolean enabled;
    }
}
