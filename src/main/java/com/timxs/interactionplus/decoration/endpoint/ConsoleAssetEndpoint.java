package com.timxs.interactionplus.decoration.endpoint;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.decoration.model.DecorationAssetParam;
import com.timxs.interactionplus.decoration.service.DecorationAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/**
 * Console 装饰资产 API。
 *
 * <p>路径前缀：{@code /apis/console.api.interaction-plus.timxs.com/v1alpha1}
 */
@Component
@RequiredArgsConstructor
public class ConsoleAssetEndpoint implements CustomEndpoint {

    private final DecorationAssetService assetService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("/assets", this::listAssets)
            .POST("/assets", this::createAsset)
            .GET("/assets/{name}", this::getAsset)
            .PUT("/assets/{name}", this::updateAsset)
            .DELETE("/assets/{name}", this::deleteAsset)
            // 动作路由使用子资源风格（/{name}/action）而非冒号风格（/{name}:action）：
            // 冒号风格会被 Halo RequestInfoFactory 解析为非资源请求，细粒度角色将被 403
            .POST("/assets/{name}/activate", this::activate)
            .POST("/assets/{name}/disable", this::disable)
            .POST("/assets/{name}/archive", this::archive)
            .POST("/assets/{name}/unarchive", this::unarchive)
            // 有效授予数（删除确认提示影响面用）
            .GET("/assets/{name}/active-grant-count", this::activeGrantCount)
            .build();
    }

    private Mono<ServerResponse> listAssets(ServerRequest request) {
        return assetService.listAssets(request.exchange()).flatMap(this::ok);
    }

    private Mono<ServerResponse> getAsset(ServerRequest request) {
        return assetService.getAsset(request.pathVariable("name")).flatMap(this::ok);
    }

    private Mono<ServerResponse> createAsset(ServerRequest request) {
        return readBody(request).flatMap(assetService::createManagedAsset).flatMap(this::ok);
    }

    private Mono<ServerResponse> updateAsset(ServerRequest request) {
        var name = request.pathVariable("name");
        return readBody(request)
            .flatMap(param -> assetService.updateAsset(name, param))
            .flatMap(this::ok);
    }

    private Mono<ServerResponse> deleteAsset(ServerRequest request) {
        return assetService.deleteAsset(request.pathVariable("name"))
            .then(ServerResponse.ok().build());
    }

    private Mono<ServerResponse> activate(ServerRequest request) {
        return assetService.activate(request.pathVariable("name")).flatMap(this::ok);
    }

    private Mono<ServerResponse> disable(ServerRequest request) {
        return assetService.disable(request.pathVariable("name")).flatMap(this::ok);
    }

    private Mono<ServerResponse> archive(ServerRequest request) {
        return assetService.archive(request.pathVariable("name")).flatMap(this::ok);
    }

    private Mono<ServerResponse> unarchive(ServerRequest request) {
        return assetService.unarchive(request.pathVariable("name")).flatMap(this::ok);
    }

    private Mono<ServerResponse> activeGrantCount(ServerRequest request) {
        return assetService.countActiveGrants(request.pathVariable("name"))
            .flatMap(count -> ok(java.util.Map.of("count", count)));
    }

    private Mono<DecorationAssetParam> readBody(ServerRequest request) {
        return request.bodyToMono(DecorationAssetParam.class)
            .switchIfEmpty(Mono.error(InteractionPlusException.badRequest(
                ErrorCodes.VALIDATION_FAILED, "请求体为空", "请求体不能为空。")));
    }

    private Mono<ServerResponse> ok(Object body) {
        return ServerResponse.ok().bodyValue(body);
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(InteractionPlusConst.CONSOLE_API_GROUP,
            InteractionPlusConst.VERSION);
    }
}
