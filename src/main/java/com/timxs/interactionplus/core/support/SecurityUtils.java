package com.timxs.interactionplus.core.support;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;
import run.halo.app.infra.AnonymousUserConst;

/**
 * 安全上下文工具。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户名；匿名时返回 {@link AnonymousUserConst#PRINCIPAL}。
     */
    public static Mono<String> getCurrentUserName() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(authentication -> authentication.getName())
            .defaultIfEmpty(AnonymousUserConst.PRINCIPAL);
    }

    /**
     * 是否为匿名用户名。
     */
    public static boolean isAnonymous(String userName) {
        return userName == null || AnonymousUserConst.isAnonymousUser(userName);
    }
}
