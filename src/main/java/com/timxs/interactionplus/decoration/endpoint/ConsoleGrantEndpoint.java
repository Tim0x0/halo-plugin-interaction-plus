package com.timxs.interactionplus.decoration.endpoint;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.decoration.model.GrantParam;
import com.timxs.interactionplus.decoration.model.RevokeHeldParam;
import com.timxs.interactionplus.decoration.model.RevokeParam;
import com.timxs.interactionplus.decoration.model.RoleGrantParam;
import com.timxs.interactionplus.decoration.service.DecorationGrantService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/**
 * Console 授予 API：授予、批量授予、按角色快照授予、撤销。
 *
 * <p>路径前缀：{@code /apis/console.api.interaction-plus.timxs.com/v1alpha1}
 */
@Component
@RequiredArgsConstructor
public class ConsoleGrantEndpoint implements CustomEndpoint {

    private final DecorationGrantService grantService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("/grants", this::listGrants)
            .POST("/grants", this::grant)
            // 动作路由使用子资源风格：集合级动作用 /-/action，单资源动作用 /{name}/action。
            // 冒号风格（:action）会被 Halo RequestInfoFactory 解析为非资源请求，细粒度角色将被 403
            .POST("/grants/-/grant-by-role-snapshot", this::grantByRoleSnapshot)
            .POST("/grants/-/revoke-held", this::revokeAllHeld)
            .POST("/grants/{name}/revoke", this::revoke)
            .build();
    }

    private Mono<ServerResponse> listGrants(ServerRequest request) {
        return grantService.listGrants(request.exchange())
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> grant(ServerRequest request) {
        return request.bodyToMono(GrantParam.class)
            .defaultIfEmpty(new GrantParam())
            .flatMap(grantService::grant)
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> grantByRoleSnapshot(ServerRequest request) {
        return request.bodyToMono(RoleGrantParam.class)
            .defaultIfEmpty(new RoleGrantParam())
            .flatMap(grantService::grantByRoleSnapshot)
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> revoke(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(RevokeParam.class)
            .defaultIfEmpty(new RevokeParam())
            .flatMap(param -> grantService.revoke(name, param))
            .flatMap(grant -> ServerResponse.ok().bodyValue(grant));
    }

    /** 「全部撤销」：撤销某用户某装饰全部来源的有效授予（彻底收回）。 */
    private Mono<ServerResponse> revokeAllHeld(ServerRequest request) {
        return request.bodyToMono(RevokeHeldParam.class)
            .defaultIfEmpty(new RevokeHeldParam())
            .flatMap(grantService::revokeAllHeld)
            .flatMap(count -> ServerResponse.ok().bodyValue(Map.of("revokedCount", count)));
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(InteractionPlusConst.CONSOLE_API_GROUP,
            InteractionPlusConst.VERSION);
    }
}
