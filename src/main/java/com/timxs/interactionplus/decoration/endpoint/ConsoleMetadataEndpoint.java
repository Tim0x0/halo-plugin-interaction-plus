package com.timxs.interactionplus.decoration.endpoint;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.core.support.ListRequestSupport;
import com.timxs.interactionplus.decoration.model.MetadataParam;
import com.timxs.interactionplus.decoration.service.DecorationMetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/**
 * Console 元数据 API：分类 / 标签 / 稀有度。
 *
 * <p>路径前缀：{@code /apis/console.api.interaction-plus.timxs.com/v1alpha1}
 */
@Component
@RequiredArgsConstructor
public class ConsoleMetadataEndpoint implements CustomEndpoint {

    private final DecorationMetadataService metadataService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            // 分类
            .GET("/categories", this::listCategories)
            .POST("/categories", this::createCategory)
            .PUT("/categories/{name}", this::updateCategory)
            .DELETE("/categories/{name}", this::deleteCategory)
            .GET("/categories/{name}/usage-count", this::categoryUsageCount)
            // 标签
            .GET("/tags", this::listTags)
            .POST("/tags", this::createTag)
            .PUT("/tags/{name}", this::updateTag)
            .DELETE("/tags/{name}", this::deleteTag)
            .GET("/tags/{name}/usage-count", this::tagUsageCount)
            // 稀有度
            .GET("/rarities", this::listRarities)
            .POST("/rarities", this::createRarity)
            .PUT("/rarities/{name}", this::updateRarity)
            .DELETE("/rarities/{name}", this::deleteRarity)
            .GET("/rarities/{name}/usage-count", this::rarityUsageCount)
            .build();
    }

    // ── 分类 ──
    private Mono<ServerResponse> listCategories(ServerRequest request) {
        return metadataService.listCategories(request.exchange()).flatMap(this::ok);
    }

    private Mono<ServerResponse> createCategory(ServerRequest request) {
        return readBody(request).flatMap(metadataService::createCategory).flatMap(this::ok);
    }

    private Mono<ServerResponse> updateCategory(ServerRequest request) {
        var name = request.pathVariable("name");
        return readBody(request)
            .flatMap(param -> metadataService.updateCategory(name, param))
            .flatMap(this::ok);
    }

    private Mono<ServerResponse> deleteCategory(ServerRequest request) {
        return metadataService.deleteCategory(request.pathVariable("name"))
            .then(ServerResponse.ok().build());
    }

    // ── 标签 ──
    private Mono<ServerResponse> listTags(ServerRequest request) {
        return metadataService.listTags(request.exchange()).flatMap(this::ok);
    }

    private Mono<ServerResponse> createTag(ServerRequest request) {
        return readBody(request).flatMap(metadataService::createTag).flatMap(this::ok);
    }

    private Mono<ServerResponse> updateTag(ServerRequest request) {
        var name = request.pathVariable("name");
        return readBody(request)
            .flatMap(param -> metadataService.updateTag(name, param))
            .flatMap(this::ok);
    }

    private Mono<ServerResponse> deleteTag(ServerRequest request) {
        return metadataService.deleteTag(request.pathVariable("name"))
            .then(ServerResponse.ok().build());
    }

    // ── 稀有度 ──
    private Mono<ServerResponse> listRarities(ServerRequest request) {
        return metadataService.listRarities(request.exchange()).flatMap(this::ok);
    }

    private Mono<ServerResponse> createRarity(ServerRequest request) {
        return readBody(request).flatMap(metadataService::createRarity).flatMap(this::ok);
    }

    private Mono<ServerResponse> updateRarity(ServerRequest request) {
        var name = request.pathVariable("name");
        return readBody(request)
            .flatMap(param -> metadataService.updateRarity(name, param))
            .flatMap(this::ok);
    }

    private Mono<ServerResponse> deleteRarity(ServerRequest request) {
        return metadataService.deleteRarity(request.pathVariable("name"))
            .then(ServerResponse.ok().build());
    }

    private Mono<MetadataParam> readBody(ServerRequest request) {
        return ListRequestSupport.readBody(request, MetadataParam.class);
    }

    // ── 引用数（删除确认提示影响面用） ──
    private Mono<ServerResponse> categoryUsageCount(ServerRequest request) {
        return metadataService.countCategoryUsage(request.pathVariable("name"))
            .flatMap(count -> ok(java.util.Map.of("count", count)));
    }

    private Mono<ServerResponse> tagUsageCount(ServerRequest request) {
        return metadataService.countTagUsage(request.pathVariable("name"))
            .flatMap(count -> ok(java.util.Map.of("count", count)));
    }

    private Mono<ServerResponse> rarityUsageCount(ServerRequest request) {
        return metadataService.countRarityUsage(request.pathVariable("name"))
            .flatMap(count -> ok(java.util.Map.of("count", count)));
    }

    private Mono<ServerResponse> ok(Object body) {
        return ServerResponse.ok().bodyValue(body);
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(InteractionPlusConst.CONSOLE_API_GROUP, InteractionPlusConst.VERSION);
    }
}
