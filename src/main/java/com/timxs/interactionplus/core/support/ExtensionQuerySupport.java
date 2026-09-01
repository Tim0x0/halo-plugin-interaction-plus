package com.timxs.interactionplus.core.support;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Condition;

/**
 * 扩展查询与读取支持：动态条件拼装与按名批量读取。
 */
public final class ExtensionQuerySupport {

    private ExtensionQuerySupport() {
    }

    /**
     * 动态条件列表 AND 合并，调用方保证列表非空
     * （惯例首条件为 {@code isNull("metadata.deletionTimestamp")}）。
     */
    public static Condition andAll(List<Condition> conditions) {
        Condition[] rest = conditions.subList(1, conditions.size()).toArray(Condition[]::new);
        return and(conditions.get(0), rest);
    }

    /** 值非空时追加 equal 条件（查询参数筛选构造用）。 */
    public static void addEqualIfPresent(List<Condition> conditions, String field, String value) {
        if (StringUtils.hasText(value)) {
            conditions.add(equal(field, value));
        }
    }

    /**
     * 按名批量读取，返回 名 → 实体 映射；不存在的条目直接忽略。
     * 空集合短路返回空映射，不产生订阅。
     */
    public static <E extends Extension> Mono<Map<String, E>> fetchAll(
        ReactiveExtensionClient client, Class<E> type, Collection<String> names) {
        if (names.isEmpty()) {
            return Mono.just(Map.of());
        }
        return Flux.fromIterable(names)
            .flatMap(name -> client.fetch(type, name).map(ext -> Map.entry(name, ext)))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
