package com.timxs.interactionplus.core.support;

import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Role;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * Halo 角色工具：显示名解析（display-name annotation）、批量读取与存在性校验，不暴露内部名。
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

    /**
     * 角色名批量取显示名（名 → 显示名）。已删除的角色不在映射中，
     * 回退规则（回退内部名）由调用方按场景决定。
     */
    public static Mono<Map<String, String>> displayNames(ReactiveExtensionClient client,
        Collection<String> roleNames) {
        return ExtensionQuerySupport.fetchAll(client, Role.class, roleNames)
            .map(roles -> roles.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                    entry -> displayName(entry.getValue()))));
    }

    /** 角色存在性校验：任一缺失即 400（{@code ROLE_NOT_FOUND}），报错文案各处统一。 */
    public static Mono<Void> requireExists(ReactiveExtensionClient client,
        Collection<String> roleNames) {
        return Flux.fromIterable(roleNames)
            .concatMap(roleName -> client.fetch(Role.class, roleName)
                .switchIfEmpty(Mono.error(InteractionPlusException.badRequest(
                    ErrorCodes.ROLE_NOT_FOUND, "角色不存在", "Halo 角色不存在：" + roleName))))
            .then();
    }
}
