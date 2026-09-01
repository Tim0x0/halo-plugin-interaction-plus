package com.timxs.interactionplus.decoration.extension;

import static com.timxs.interactionplus.core.constants.InteractionPlusConst.GROUP;
import static com.timxs.interactionplus.core.constants.InteractionPlusConst.VERSION;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/**
 * 用户装饰分类。单个装饰最多 1 个分类，可为空。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = "UserDecorationCategory",
    plural = "userdecorationcategories", singular = "userdecorationcategory")
public class UserDecorationCategory extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "UserDecorationCategorySpec")
    public static class Spec implements OrderedMetadataSpec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50,
            description = "显示名称")
        private String displayName;

        @Schema(maxLength = 200, description = "描述")
        private String description;

        @Schema(description = "是否启用")
        private Boolean enabled;

        @Schema(description = "排序值，越小越靠前")
        private Integer displayOrder;
    }
}
