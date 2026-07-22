package com.timxs.interactionplus.decoration.service;

import com.timxs.interactionplus.api.DecorationGrantApi;
import com.timxs.interactionplus.api.GrantRequest;
import com.timxs.interactionplus.api.GrantResult;
import com.timxs.interactionplus.api.GrantableDecoration;
import com.timxs.interactionplus.api.RevokeRequest;
import com.timxs.interactionplus.api.RevokeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 对外发奖 API 实现。
 *
 * <p>以 {@code @Component} 注册到本插件的 Spring 容器，经 Halo 核心
 * {@code ExtensionGetter.getEnabledExtension(DecorationGrantApi.class)} 暴露给依赖本插件的外部插件
 * （Halo 插件容器相互隔离，外部无法直接 {@code @Autowired}，只能经 ExtensionGetter 取用）。
 * 实际逻辑委托 {@link DecorationGrantService}。
 *
 * @author Tim0x0
 */
@Component
@RequiredArgsConstructor
public class DecorationGrantApiImpl implements DecorationGrantApi {

    private final DecorationGrantService grantService;

    @Override
    public Flux<GrantableDecoration> listGrantable(String categoryName) {
        return grantService.listGrantable(categoryName);
    }

    @Override
    public Mono<GrantResult> grant(GrantRequest request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("request 不能为空"));
        }
        return grantService.externalGrant(request.userName(), request.decorationName(),
            request.sourcePlugin(), request.expiresAt(), request.reason());
    }

    @Override
    public Mono<RevokeResult> revoke(RevokeRequest request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("request 不能为空"));
        }
        return grantService.externalRevoke(request.userName(), request.decorationName(),
            request.sourcePlugin(), request.reason());
    }
}
