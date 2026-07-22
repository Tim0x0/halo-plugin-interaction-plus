package com.timxs.interactionplus.identity.model;

import lombok.Data;

/**
 * 身份标识映射创建与更新请求体。
 */
@Data
public class IdentityMarkMappingParam {

    /** Halo 角色名（创建后不可修改）。 */
    private String roleName;

    /** 身份标识显示名称，必填。 */
    private String displayName;

    /** 图标地址或图标标识。 */
    private String icon;

    /** 标识颜色。 */
    private String color;

    /** 优先级，越大越靠前。 */
    private Integer priority;

    /** 是否启用，默认 true。 */
    private Boolean enabled;
}
