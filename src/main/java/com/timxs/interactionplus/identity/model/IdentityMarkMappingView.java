package com.timxs.interactionplus.identity.model;

import com.timxs.interactionplus.identity.extension.UserIdentityMarkMapping;
import lombok.Data;

/**
 * 身份标识映射视图：附带角色存在性标记与角色显示名（界面不展示角色内部名）。
 */
@Data
public class IdentityMarkMappingView {

    private UserIdentityMarkMapping mapping;

    /** Halo 角色是否存在；不存在时前台不展示该身份标识。 */
    private boolean roleExists;

    /** Halo 角色显示名；角色不存在时回退内部名。 */
    private String roleDisplayName;

    public IdentityMarkMappingView() {
    }

    public IdentityMarkMappingView(UserIdentityMarkMapping mapping, boolean roleExists,
        String roleDisplayName) {
        this.mapping = mapping;
        this.roleExists = roleExists;
        this.roleDisplayName = roleDisplayName;
    }
}
