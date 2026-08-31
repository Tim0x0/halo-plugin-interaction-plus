package com.timxs.interactionplus.decoration.reconciler;

import static run.halo.app.extension.ExtensionUtil.addFinalizers;
import static run.halo.app.extension.ExtensionUtil.isDeleted;
import static run.halo.app.extension.ExtensionUtil.removeFinalizers;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

/**
 * 装饰元数据（分类 / 标签 / 稀有度）删除级联清理基类。
 *
 * <p>删除时由 Reconciler 级联从所有引用资产（含草稿 / 停用 / 归档）移除该引用。
 * 引用匹配采用索引候选 + 内存精确比较，不依赖索引对数组字段的 equal 语义。
 */
@Slf4j
public abstract class AbstractMetadataReconciler<E extends AbstractExtension>
    implements Reconciler<Reconciler.Request> {

    public static final String FINALIZER = "interaction-plus.timxs.com/cleanup";

    protected final ExtensionClient client;

    protected AbstractMetadataReconciler(ExtensionClient client) {
        this.client = client;
    }

    /** 元数据资源类型。 */
    protected abstract Class<E> type();

    /** 供 ControllerBuilder 注册用的空实例。 */
    protected abstract E newExtension();

    /** 资产中引用该元数据的索引字段（仅作候选集查询）。 */
    protected abstract String assetField();

    /** 精确判断资产是否引用该元数据。 */
    protected abstract boolean references(UserDecorationAsset asset, String metadataName);

    /** 从资产移除该元数据引用。 */
    protected abstract void removeReference(UserDecorationAsset asset, String metadataName);

    @Override
    public Result reconcile(Request request) {
        client.fetch(type(), request.name()).ifPresent(metadata -> {
            if (isDeleted(metadata)) {
                var finalizers = metadata.getMetadata().getFinalizers();
                if (finalizers != null && finalizers.contains(FINALIZER)) {
                    cleanUpReferences(request.name());
                    removeFinalizers(metadata.getMetadata(), Set.of(FINALIZER));
                    client.update(metadata);
                }
                return;
            }
            if (addFinalizers(metadata.getMetadata(), Set.of(FINALIZER))) {
                client.update(metadata);
            }
        });
        return Result.doNotRetry();
    }

    /** 级联：从所有引用资产移除该引用。失败抛出由 Controller 自动重试。 */
    private void cleanUpReferences(String metadataName) {
        var options = ListOptions.builder()
            .fieldQuery(and(equal(assetField(), metadataName),
                isNull("metadata.deletionTimestamp")))
            .build();
        var candidates = client.listAll(UserDecorationAsset.class, options,
            Sort.by(Sort.Order.asc("metadata.name")));
        int cleaned = 0;
        for (UserDecorationAsset asset : candidates) {
            // 索引仅作候选，内存精确比较后再清除，避免误清相似名称的引用
            if (!references(asset, metadataName)) {
                continue;
            }
            removeReference(asset, metadataName);
            client.update(asset);
            cleaned++;
        }
        if (cleaned > 0) {
            log.info("元数据 {} 删除级联：已从 {} 个装饰移除引用", metadataName, cleaned);
        }
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(newExtension())
            .build();
    }
}
