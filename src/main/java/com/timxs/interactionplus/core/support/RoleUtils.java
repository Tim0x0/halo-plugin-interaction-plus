package com.timxs.interactionplus.core.support;

import org.springframework.util.StringUtils;
import run.halo.app.core.extension.Role;

/**
 * Halo 角色工具：界面展示一律使用显示名（display-name annotation），不暴露内部名。
 */
public final class RoleUtils {

    /** Halo 角色显示名所在 annotation。 */
    private static final String DISPLAY_NAME_ANNOTATION =
        "rbac.authorization.halo.run/display-name";

    private RoleUtils() {
    }

    /** 角色显示名；未配置时回退内部名。 */
    public static String displayName(Role role) {
        var annotations = role.getMetadata().getAnnotations();
        String displayName = annotations == null
            ? null : annotations.get(DISPLAY_NAME_ANNOTATION);
        return StringUtils.hasText(displayName) ? displayName : role.getMetadata().getName();
    }
}
