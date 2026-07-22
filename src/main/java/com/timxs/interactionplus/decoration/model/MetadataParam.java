package com.timxs.interactionplus.decoration.model;

import lombok.Data;

/**
 * 元数据（分类 / 标签 / 稀有度）创建与更新请求体。
 * 内部标识名由系统生成，不在请求体中。
 */
@Data
public class MetadataParam {

    /** 显示名称，必填。 */
    private String displayName;

    /** 描述，可选。 */
    private String description;

    /** 颜色，可选（分类忽略此字段）。 */
    private String color;

    /** 是否启用，默认 true。 */
    private Boolean enabled;

    /** 排序值，越小越靠前。 */
    private Integer displayOrder;

    /** 是否允许外部插件发放（仅稀有度使用；分类 / 标签忽略）。为空视为允许。 */
    private Boolean externalGrantable;
}
