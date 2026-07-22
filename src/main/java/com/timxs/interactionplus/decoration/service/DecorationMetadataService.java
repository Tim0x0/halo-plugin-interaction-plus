package com.timxs.interactionplus.decoration.service;

import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationCategory;
import com.timxs.interactionplus.decoration.extension.UserDecorationRarity;
import com.timxs.interactionplus.decoration.extension.UserDecorationTag;
import com.timxs.interactionplus.decoration.model.MetadataParam;
import com.timxs.interactionplus.core.support.ListRequestSupport;
import com.timxs.interactionplus.core.support.NameGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Condition;

/**
 * 装饰元数据（分类 / 标签 / 稀有度）服务。
 *
 * <p>删除为级联模式（对齐 Halo 官方分类 / 标签删除语义）：
 * 任何时候可删，引用清理由 Reconciler 在删除后级联完成（从所有引用资产移除该引用）。
 * 引用统计用索引候选 + 内存精确匹配（规避数组字段索引 equal 的子串语义疑虑）。
 */
@Service
@RequiredArgsConstructor
public class DecorationMetadataService {

    /** 元数据默认排序：displayOrder 升序，再按创建时间降序。 */
    private static final Sort METADATA_SORT = Sort.by(
        Sort.Order.asc("spec.displayOrder"),
        Sort.Order.desc("metadata.creationTimestamp"));

    private final ReactiveExtensionClient client;

    /**
     * 稀有度字典（内部名 → 实体）一次性加载，供授予可发判定 / 公开展示内联稀有度属性
     * 等聚合复用（避免 N+1；此前授予与公开身份两个 service 各自持有一份逐字相同实现）。
     */
    public Mono<Map<String, UserDecorationRarity>> loadRarityMap() {
        return client.listAll(UserDecorationRarity.class,
                ListOptions.builder().fieldQuery(isNull("metadata.deletionTimestamp")).build(),
                Sort.by(Sort.Order.asc("metadata.name")))
            .collectMap(rarity -> rarity.getMetadata().getName(), rarity -> rarity);
    }

    // ───────────────────────── 分类 ─────────────────────────

    public Mono<ListResult<UserDecorationCategory>> listCategories(ServerWebExchange exchange) {
        return client.listBy(UserDecorationCategory.class, buildListOptions(exchange),
            ListRequestSupport.toPageRequest(exchange, METADATA_SORT));
    }

    public Mono<UserDecorationCategory> createCategory(MetadataParam param) {
        validate(param);
        var category = new UserDecorationCategory();
        category.setMetadata(newMetadata("category"));
        var spec = new UserDecorationCategory.Spec();
        spec.setDisplayName(param.getDisplayName());
        spec.setDescription(param.getDescription());
        spec.setEnabled(param.getEnabled() == null || param.getEnabled());
        spec.setDisplayOrder(param.getDisplayOrder());
        category.setSpec(spec);
        return client.create(category);
    }

    public Mono<UserDecorationCategory> updateCategory(String name, MetadataParam param) {
        validate(param);
        return client.fetch(UserDecorationCategory.class, name)
            .switchIfEmpty(notFound("分类"))
            .flatMap(category -> {
                var spec = category.getSpec();
                spec.setDisplayName(param.getDisplayName());
                spec.setDescription(param.getDescription());
                if (param.getEnabled() != null) {
                    spec.setEnabled(param.getEnabled());
                }
                spec.setDisplayOrder(param.getDisplayOrder());
                return client.update(category);
            });
    }

    public Mono<Void> deleteCategory(String name) {
        return deleteMetadata(UserDecorationCategory.class, name);
    }

    /** 分类引用数（删除确认提示影响面用）。 */
    public Mono<Long> countCategoryUsage(String name) {
        return countUsage("spec.categoryName", name,
            asset -> name.equals(asset.getSpec().getCategoryName()));
    }

    // ───────────────────────── 标签 ─────────────────────────

    public Mono<ListResult<UserDecorationTag>> listTags(ServerWebExchange exchange) {
        return client.listBy(UserDecorationTag.class, buildListOptions(exchange),
            ListRequestSupport.toPageRequest(exchange, METADATA_SORT));
    }

    public Mono<UserDecorationTag> createTag(MetadataParam param) {
        validate(param);
        var tag = new UserDecorationTag();
        tag.setMetadata(newMetadata("tag"));
        var spec = new UserDecorationTag.Spec();
        spec.setDisplayName(param.getDisplayName());
        spec.setDescription(param.getDescription());
        spec.setColor(param.getColor());
        spec.setEnabled(param.getEnabled() == null || param.getEnabled());
        spec.setDisplayOrder(param.getDisplayOrder());
        tag.setSpec(spec);
        return client.create(tag);
    }

    public Mono<UserDecorationTag> updateTag(String name, MetadataParam param) {
        validate(param);
        return client.fetch(UserDecorationTag.class, name)
            .switchIfEmpty(notFound("标签"))
            .flatMap(tag -> {
                var spec = tag.getSpec();
                spec.setDisplayName(param.getDisplayName());
                spec.setDescription(param.getDescription());
                spec.setColor(param.getColor());
                if (param.getEnabled() != null) {
                    spec.setEnabled(param.getEnabled());
                }
                spec.setDisplayOrder(param.getDisplayOrder());
                return client.update(tag);
            });
    }

    public Mono<Void> deleteTag(String name) {
        return deleteMetadata(UserDecorationTag.class, name);
    }

    /** 标签引用数。List.contains 精确比较，不依赖索引对数组字段的 equal 语义（B5）。 */
    public Mono<Long> countTagUsage(String name) {
        return countUsage("spec.tagNames", name,
            asset -> asset.getSpec().getTagNames() != null
                && asset.getSpec().getTagNames().contains(name));
    }

    // ───────────────────────── 稀有度 ─────────────────────────

    public Mono<ListResult<UserDecorationRarity>> listRarities(ServerWebExchange exchange) {
        return client.listBy(UserDecorationRarity.class, buildListOptions(exchange),
            ListRequestSupport.toPageRequest(exchange, METADATA_SORT));
    }

    public Mono<UserDecorationRarity> createRarity(MetadataParam param) {
        validate(param);
        var rarity = new UserDecorationRarity();
        rarity.setMetadata(newMetadata("rarity"));
        var spec = new UserDecorationRarity.Spec();
        spec.setDisplayName(param.getDisplayName());
        spec.setDescription(param.getDescription());
        spec.setColor(param.getColor());
        spec.setEnabled(param.getEnabled() == null || param.getEnabled());
        spec.setDisplayOrder(param.getDisplayOrder());
        spec.setExternalGrantable(param.getExternalGrantable());
        rarity.setSpec(spec);
        return client.create(rarity);
    }

    public Mono<UserDecorationRarity> updateRarity(String name, MetadataParam param) {
        validate(param);
        return client.fetch(UserDecorationRarity.class, name)
            .switchIfEmpty(notFound("稀有度"))
            .flatMap(rarity -> {
                var spec = rarity.getSpec();
                spec.setDisplayName(param.getDisplayName());
                spec.setDescription(param.getDescription());
                spec.setColor(param.getColor());
                if (param.getEnabled() != null) {
                    spec.setEnabled(param.getEnabled());
                }
                spec.setDisplayOrder(param.getDisplayOrder());
                if (param.getExternalGrantable() != null) {
                    spec.setExternalGrantable(param.getExternalGrantable());
                }
                return client.update(rarity);
            });
    }

    public Mono<Void> deleteRarity(String name) {
        return deleteMetadata(UserDecorationRarity.class, name);
    }

    /** 稀有度引用数。 */
    public Mono<Long> countRarityUsage(String name) {
        return countUsage("spec.rarityName", name,
            asset -> name.equals(asset.getSpec().getRarityName()));
    }

    // ───────────────────────── 通用逻辑 ─────────────────────────

    /**
     * 删除元数据（级联模式）：任何时候可删，
     * 引用清理由对应 Reconciler 在删除后级联完成。
     */
    private <E extends AbstractExtension> Mono<Void> deleteMetadata(Class<E> type, String name) {
        return client.fetch(type, name)
            .switchIfEmpty(notFound("元数据"))
            .flatMap(client::delete)
            .then();
    }

    /** 引用数统计：索引 equal 仅作候选集，内存精确匹配兜底。 */
    private Mono<Long> countUsage(String field, String value,
        Predicate<UserDecorationAsset> exactMatch) {
        return findReferencingAssets(field, value)
            .filter(exactMatch::test)
            .count();
    }

    private Flux<UserDecorationAsset> findReferencingAssets(String field, String value) {
        var options = ListOptions.builder()
            .fieldQuery(and(equal(field, value), isNull("metadata.deletionTimestamp")))
            .build();
        return client.listAll(UserDecorationAsset.class, options,
            Sort.by(Sort.Order.asc("metadata.name")));
    }

    private ListOptions buildListOptions(ServerWebExchange exchange) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(isNull("metadata.deletionTimestamp"));
        var queryParams = exchange.getRequest().getQueryParams();
        String enabled = queryParams.getFirst("enabled");
        if (StringUtils.hasText(enabled)) {
            conditions.add(equal("spec.enabled", Boolean.parseBoolean(enabled)));
        }
        String keyword = ListRequestSupport.getKeyword(exchange);
        if (keyword != null) {
            conditions.add(contains("spec.displayName", keyword));
        }
        Condition[] rest = conditions.subList(1, conditions.size()).toArray(Condition[]::new);
        return ListOptions.builder()
            .fieldQuery(and(conditions.get(0), rest))
            .build();
    }

    private Metadata newMetadata(String prefix) {
        var metadata = new Metadata();
        metadata.setName(NameGenerator.generate(prefix));
        return metadata;
    }

    private void validate(MetadataParam param) {
        if (param == null || !StringUtils.hasText(param.getDisplayName())) {
            throw InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "显示名称不能为空。");
        }
    }

    private <T> Mono<T> notFound(String label) {
        return Mono.error(InteractionPlusException.notFound(ErrorCodes.VALIDATION_FAILED,
            label + "不存在", label + "不存在或已被删除。"));
    }
}
