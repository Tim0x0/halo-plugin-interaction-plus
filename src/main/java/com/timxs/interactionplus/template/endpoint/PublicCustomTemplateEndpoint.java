package com.timxs.interactionplus.template.endpoint;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.timxs.interactionplus.core.constants.InteractionPlusConst;
import com.timxs.interactionplus.template.model.CustomTemplateVo;
import com.timxs.interactionplus.template.service.CustomTemplateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/**
 * 公开自定义模板 API（游客可访问）。
 *
 * <p>路径前缀：{@code /apis/api.interaction-plus.timxs.com/v1alpha1}
 *
 * <p>前台 runtime 在入口处立即拉这个接口，拿到后才注册 {@code hip-*} 自定义元素，
 * 因此它在首屏关键路径上 —— 响应要小、要能走 304。
 */
@Component
@RequiredArgsConstructor
public class PublicCustomTemplateEndpoint implements CustomEndpoint {

    private final CustomTemplateService customTemplateService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("/custom-templates", this::listTemplates)
            .build();
    }

    /**
     * 已启用的模板列表，带协商缓存。
     *
     * <p>{@code no-cache} 不是「不缓存」而是「用之前必须回源验证」：浏览器每次都发
     * 条件请求，未改动时 304 空体返回，改动后刷新即生效。用 {@code max-age} 强缓存
     * 会导致「后台改了几天不生效」；用 {@code no-store} 则每次都传全量模板文本。
     */
    private Mono<ServerResponse> listTemplates(ServerRequest request) {
        return customTemplateService.loadEnabled()
            .flatMap(snapshot -> {
                var etag = "\"" + snapshot.etag() + "\"";
                if (etag.equals(ifNoneMatch(request))) {
                    return ServerResponse.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .cacheControl(CacheControl.noCache())
                        .build();
                }
                return ServerResponse.ok()
                    .eTag(etag)
                    .cacheControl(CacheControl.noCache())
                    .bodyValue(new TemplateList(snapshot.items()));
            });
    }

    /**
     * 取 {@code If-None-Match}。浏览器会原样回传我们发出的强 ETag；
     * 但中间层（部分反代 / CDN）可能把它降级成弱校验 {@code W/"..."}，
     * 这里剥掉弱前缀，否则每次都会误判成「变了」而退回 200 全量。
     */
    private static String ifNoneMatch(ServerRequest request) {
        var value = request.headers().firstHeader(HttpHeaders.IF_NONE_MATCH);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.startsWith("W/") ? value.substring(2) : value;
    }

    /** 响应体使用 {@code { "items": [...] }} 结构。 */
    private record TemplateList(List<CustomTemplateVo> items) {
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(InteractionPlusConst.PUBLIC_API_GROUP,
            InteractionPlusConst.VERSION);
    }
}
