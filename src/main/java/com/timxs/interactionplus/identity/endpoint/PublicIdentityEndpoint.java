package com.timxs.interactionplus.identity.endpoint;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.core.support.ListRequestSupport;
import com.timxs.interactionplus.identity.model.PublicIdentityBatch;
import com.timxs.interactionplus.identity.service.PublicIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/**
 * 公开身份 API（游客可访问）。
 *
 * <p>路径前缀：{@code /apis/api.interaction-plus.timxs.com/v1alpha1}
 */
@Component
@RequiredArgsConstructor
public class PublicIdentityEndpoint implements CustomEndpoint {

    private final PublicIdentityService publicIdentityService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("/identity/{userName}", this::getIdentity)
            .GET("/identity/{userName}/decorations", this::getOwnedDecorations)
            .POST("/identities", this::getIdentities)
            .build();
    }

    private Mono<ServerResponse> getIdentity(ServerRequest request) {
        return publicIdentityService.requireIdentity(request.pathVariable("userName"))
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo));
    }

    /**
     * 装饰墙：返回该用户获得的、当前有效的全部装饰（自包含，尊重用户公开开关）。
     * 用户不存在或被禁用时 404，与 {@code GET /identity/{userName}} 口径一致。
     */
    private Mono<ServerResponse> getOwnedDecorations(ServerRequest request) {
        return publicIdentityService.resolveOwnedDecorations(request.pathVariable("userName"))
            .switchIfEmpty(Mono.error(InteractionPlusException.notFound(
                ErrorCodes.USER_NOT_FOUND, "用户不存在", "用户不存在或不可用。")))
            .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    private Mono<ServerResponse> getIdentities(ServerRequest request) {
        return ListRequestSupport.readBody(request, PublicIdentityBatch.Request.class)
            .flatMap(body -> publicIdentityService.getIdentities(body.getUserNames()))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(InteractionPlusConst.PUBLIC_API_GROUP,
            InteractionPlusConst.VERSION);
    }
}
