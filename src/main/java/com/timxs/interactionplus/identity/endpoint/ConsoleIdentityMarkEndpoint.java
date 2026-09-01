package com.timxs.interactionplus.identity.endpoint;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.core.support.ListRequestSupport;
import com.timxs.interactionplus.identity.model.IdentityMarkMappingParam;
import com.timxs.interactionplus.identity.service.IdentityMarkMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/**
 * Console 身份标识映射 API。
 *
 * <p>路径前缀：{@code /apis/console.api.interaction-plus.timxs.com/v1alpha1}
 */
@Component
@RequiredArgsConstructor
public class ConsoleIdentityMarkEndpoint implements CustomEndpoint {

    private final IdentityMarkMappingService mappingService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("/identity-mark-mappings", this::listMappings)
            .POST("/identity-mark-mappings", this::createMapping)
            .PUT("/identity-mark-mappings/{name}", this::updateMapping)
            .DELETE("/identity-mark-mappings/{name}", this::deleteMapping)
            .build();
    }

    private Mono<ServerResponse> listMappings(ServerRequest request) {
        return mappingService.listMappings(request.exchange())
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> createMapping(ServerRequest request) {
        return readBody(request)
            .flatMap(mappingService::createMapping)
            .flatMap(mapping -> ServerResponse.ok().bodyValue(mapping));
    }

    private Mono<ServerResponse> updateMapping(ServerRequest request) {
        var name = request.pathVariable("name");
        return readBody(request)
            .flatMap(param -> mappingService.updateMapping(name, param))
            .flatMap(mapping -> ServerResponse.ok().bodyValue(mapping));
    }

    private Mono<ServerResponse> deleteMapping(ServerRequest request) {
        return mappingService.deleteMapping(request.pathVariable("name"))
            .then(ServerResponse.ok().build());
    }

    private Mono<IdentityMarkMappingParam> readBody(ServerRequest request) {
        return ListRequestSupport.readBody(request, IdentityMarkMappingParam.class);
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(InteractionPlusConst.CONSOLE_API_GROUP,
            InteractionPlusConst.VERSION);
    }
}
