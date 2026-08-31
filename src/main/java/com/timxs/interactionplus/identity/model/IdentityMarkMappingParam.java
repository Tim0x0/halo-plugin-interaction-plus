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

    /** 展示形态：{@code text} / {@code icon} / {@code image}。缺省按哪个字段非空推断。 */
    private String displayMode;

    /** 图标库字形（data URL）。 */
    private String icon;

    /** 上传图地址（附件）。 */
    private String image;

    /** 文字牌颜色（仅 text 形态生效；可空，空=模板默认铬件）。 */
    private String color;

    /** 优先级，越大越靠前。 */
    private Integer priority;

    /** 是否启用，默认 true。 */
    private Boolean enabled;
}
