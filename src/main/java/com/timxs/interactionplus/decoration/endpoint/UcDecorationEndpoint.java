package com.timxs.interactionplus.decoration.endpoint;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.decoration.extension.UserDecorationCategory;
import com.timxs.interactionplus.decoration.extension.UserDecorationRarity;
import com.timxs.interactionplus.decoration.extension.UserDecorationTag;
import com.timxs.interactionplus.decoration.model.DecorationAssetParam;
import com.timxs.interactionplus.decoration.model.MetadataOptions;
import com.timxs.interactionplus.decoration.model.ProfileSaveParam;
import com.timxs.interactionplus.decoration.model.VisibilityParam;
import com.timxs.interactionplus.decoration.service.DecorationAssetService;
import com.timxs.interactionplus.decoration.service.DecorationProfileService;
import com.timxs.interactionplus.core.support.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * UC（用户中心）装扮 API：库存、当前佩戴、投稿、元数据选项。
 *
 * <p>路径前缀：{@code /apis/uc.api.interaction-plus.timxs.com/v1alpha1}。
 * 所有接口只操作当前登录用户自己的数据。
 */
@Component
@RequiredArgsConstructor
public class UcDecorationEndpoint implements CustomEndpoint {

    private static final Sort METADATA_SORT = Sort.by(
        Sort.Order.asc("spec.displayOrder"),
        Sort.Order.desc("metadata.creationTimestamp"));

    private final DecorationProfileService profileService;
    private final DecorationAssetService assetService;
    private final ReactiveExtensionClient client;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            // 我的装饰库存
            .GET("/inventory", this::getInventory)
            // 当前佩戴
            .GET("/profile", this::getProfile)
            .PUT("/profile", this::saveProfile)
            .PUT("/profile/visibility", this::saveVisibility)
            // 我的投稿
            .GET("/submissions", this::listSubmissions)
            .POST("/submissions", this::createSubmission)
            .GET("/submissions/{name}", this::getSubmission)
            .PUT("/submissions/{name}", this::updateSubmission)
            .DELETE("/submissions/{name}", this::deleteSubmission)
            // 元数据选项（启用项，供投稿表单与筛选）
            .GET("/metadata", this::getMetadataOptions)
            .build();
    }

    private Mono<ServerResponse> getInventory(ServerRequest request) {
        return requireUser()
            .flatMapMany(profileService::getInventory)
            .collectList()
            .flatMap(items -> ServerResponse.ok().bodyValue(items));
    }

    private Mono<ServerResponse> getProfile(ServerRequest request) {
        return requireUser()
            .flatMap(profileService::getProfile)
            .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> saveProfile(ServerRequest request) {
        return requireUser().flatMap(userName ->
            request.bodyToMono(ProfileSaveParam.class)
                .defaultIfEmpty(new ProfileSaveParam())
                .flatMap(param -> profileService.saveProfile(userName, param))
                .flatMap(view -> ServerResponse.ok().bodyValue(view)));
    }

    /** 仅更新「公开装扮墙」开关（不影响佩戴）。 */
    private Mono<ServerResponse> saveVisibility(ServerRequest request) {
        return requireUser().flatMap(userName ->
            request.bodyToMono(VisibilityParam.class)
                .defaultIfEmpty(new VisibilityParam())
                .flatMap(param -> profileService.updateVisibility(userName,
                    !Boolean.FALSE.equals(param.getPublicDecorationsVisible())))
                .flatMap(view -> ServerResponse.ok().bodyValue(view)));
    }

    private Mono<ServerResponse> listSubmissions(ServerRequest request) {
        return requireUser()
            .flatMap(userName -> assetService.listSubmissions(userName, request.exchange()))
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> createSubmission(ServerRequest request) {
        // requireUser 仅做登录拦截（SecurityUtils 匿名返回哨兵值而非空链），投稿者由 service 内部取当前用户
        return requireUser()
            .then(request.bodyToMono(DecorationAssetParam.class))
            // 空请求体时 bodyToMono 为空链，不拦截会以 200 空响应静默返回
            .switchIfEmpty(Mono.error(InteractionPlusException.badRequest(
                ErrorCodes.VALIDATION_FAILED, "请求体为空", "请求体不能为空。")))
            .flatMap(assetService::createSubmission)
            .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> getSubmission(ServerRequest request) {
        var name = request.pathVariable("name");
        return requireUser()
            .flatMap(userName -> assetService.getSubmission(userName, name))
            .flatMap(asset -> ServerResponse.ok().bodyValue(asset));
    }

    private Mono<ServerResponse> updateSubmission(ServerRequest request) {
        var name = request.pathVariable("name");
        return requireUser().flatMap(userName ->
            request.bodyToMono(DecorationAssetParam.class)
                .switchIfEmpty(Mono.error(InteractionPlusException.badRequest(
                    ErrorCodes.VALIDATION_FAILED, "请求体为空", "请求体不能为空。")))
                .flatMap(param -> assetService.updateSubmission(userName, name, param))
                .flatMap(asset -> ServerResponse.ok().bodyValue(asset)));
    }

    private Mono<ServerResponse> deleteSubmission(ServerRequest request) {
        var name = request.pathVariable("name");
        return requireUser()
            .flatMap(userName -> assetService.deleteSubmission(userName, name))
            .then(ServerResponse.ok().build());
    }

    private Mono<ServerResponse> getMetadataOptions(ServerRequest request) {
        var enabledOptions = ListOptions.builder()
            .fieldQuery(and(equal("spec.enabled", true), isNull("metadata.deletionTimestamp")))
            .build();
        // 三张小表互不依赖，并行取
        return Mono.zip(
                client.listAll(UserDecorationCategory.class, enabledOptions, METADATA_SORT)
                    .collectList(),
                client.listAll(UserDecorationTag.class, enabledOptions, METADATA_SORT)
                    .collectList(),
                client.listAll(UserDecorationRarity.class, enabledOptions, METADATA_SORT)
                    .collectList())
            .flatMap(tuple -> {
                var options = new MetadataOptions();
                options.setCategories(tuple.getT1());
                options.setTags(tuple.getT2());
                options.setRarities(tuple.getT3());
                return ServerResponse.ok().bodyValue(options);
            });
    }

    private Mono<String> requireUser() {
        return SecurityUtils.getCurrentUserName()
            .flatMap(userName -> SecurityUtils.isAnonymous(userName)
                ? Mono.error(InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                    "未登录", "请先登录后再操作。"))
                : Mono.just(userName));
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(InteractionPlusConst.UC_API_GROUP, InteractionPlusConst.VERSION);
    }
}
