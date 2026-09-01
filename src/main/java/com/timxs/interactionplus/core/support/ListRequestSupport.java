package com.timxs.interactionplus.core.support;

import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.SortResolver;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;

/**
 * Console / UC 请求解析支持（列表查询参数与请求体）。
 *
 * <p>统一规范：page 从 1 开始；size 默认 20，最大 100；sort 使用 {@code field,asc|desc}。
 */
public final class ListRequestSupport {

    private ListRequestSupport() {
    }

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /**
     * 解析页码，最小为 1。
     */
    public static int getPage(ServerWebExchange exchange) {
        return parseInt(exchange.getRequest().getQueryParams().getFirst("page"), 1, 1,
            Integer.MAX_VALUE);
    }

    /**
     * 解析每页数量，默认 20，范围 1 - 100。
     */
    public static int getSize(ServerWebExchange exchange) {
        return parseInt(exchange.getRequest().getQueryParams().getFirst("size"), DEFAULT_SIZE, 1,
            MAX_SIZE);
    }

    /**
     * 解析关键词，去除首尾空白，空白返回 null。
     */
    public static String getKeyword(ServerWebExchange exchange) {
        String keyword = exchange.getRequest().getQueryParams().getFirst("keyword");
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    /**
     * 构造分页请求。未显式指定 sort 时使用 defaultSort。
     */
    public static PageRequest toPageRequest(ServerWebExchange exchange, Sort defaultSort) {
        Sort sort = SortResolver.defaultInstance.resolve(exchange);
        if (sort.isUnsorted()) {
            sort = defaultSort;
        }
        return PageRequestImpl.of(getPage(exchange), getSize(exchange), sort);
    }

    /**
     * 读取请求体；空请求体统一 400 拒绝
     * （不拦截时 bodyToMono 为空链，会以 200 空响应静默返回）。
     */
    public static <T> Mono<T> readBody(ServerRequest request, Class<T> bodyType) {
        return request.bodyToMono(bodyType)
            .switchIfEmpty(Mono.error(InteractionPlusException.badRequest(
                ErrorCodes.VALIDATION_FAILED, "请求体为空", "请求体不能为空。")));
    }

    private static int parseInt(String value, int defaultValue, int min, int max) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
