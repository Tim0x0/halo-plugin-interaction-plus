package com.timxs.interactionplus.decoration.reconciler;

import static run.halo.app.extension.ExtensionUtil.addFinalizers;
import static run.halo.app.extension.ExtensionUtil.isDeleted;
import static run.halo.app.extension.ExtensionUtil.removeFinalizers;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationGrant;
import com.timxs.interactionplus.decoration.extension.UserDecorationProfile;
import com.timxs.interactionplus.decoration.service.DecorationAssetService;
import com.timxs.interactionplus.core.support.NameGenerator;
import com.timxs.interactionplus.identity.support.PublicIdentityCache;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

/**
 * 装饰资产删除级联清理（finalizer + Reconciler 模式）。
 *
 * <p>无论删除来自 Console API、Extension 自动 CRUD API 还是其它代码的
 * {@code client.delete()}，删除只会先打 deletionTimestamp，由本 Reconciler
 * 级联撤销全部有效授予并清理佩戴槽位与缓存后，移除 finalizer 放行真正删除。
 * Console service 层的级联（含操作者上下文）为第一道即时执行，本类兜底其它入口，二者幂等。
 * 授予历史记录保留。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDecorationAssetReconciler implements Reconciler<Reconciler.Request> {

    public static final String FINALIZER = "interaction-plus.timxs.com/cleanup";

    private final ExtensionClient client;
    private final PublicIdentityCache publicIdentityCache;

    @Override
    public Result reconcile(Request request) {
        client.fetch(UserDecorationAsset.class, request.name()).ifPresent(asset -> {
            if (isDeleted(asset)) {
                var finalizers = asset.getMetadata().getFinalizers();
                if (finalizers != null && finalizers.contains(FINALIZER)) {
                    cleanUp(asset);
                    removeFinalizers(asset.getMetadata(), Set.of(FINALIZER));
                    client.update(asset);
                }
                return;
            }
            if (addFinalizers(asset.getMetadata(), Set.of(FINALIZER))) {
                client.update(asset);
            }
        });
        return Result.doNotRetry();
    }

    /** 级联：撤销全部有效授予 + 清理佩戴槽位与缓存。失败抛出由 Controller 自动重试。 */
    private void cleanUp(UserDecorationAsset asset) {
        var assetName = asset.getMetadata().getName();
        var now = Instant.now();
        var options = ListOptions.builder()
            .fieldQuery(and(equal("spec.assetName", assetName),
                equal("spec.revoked", false),
                isNull("metadata.deletionTimestamp")))
            .build();
        var grants = client.listAll(UserDecorationGrant.class, options,
            Sort.by(Sort.Order.asc("metadata.name")));
        for (UserDecorationGrant grant : grants) {
            if (!grant.isActiveAt(now)) {
                continue;
            }
            grant.markRevoked("system", DecorationAssetService.REASON_ASSET_DELETED);
            client.update(grant);
            removeFromProfile(grant.getSpec().getUserName(), assetName);
        }
        log.info("装饰资产 {} 删除级联清理完成", assetName);
    }

    /** 阻塞版佩戴槽位清理（槽位遍历共用 Spec.removeAsset，与 DecorationProfileService 零漂移）。 */
    private void removeFromProfile(String userName, String assetName) {
        if (!StringUtils.hasText(userName)) {
            return;
        }
        publicIdentityCache.evict(userName);
        client.fetch(UserDecorationProfile.class, NameGenerator.profileName(userName))
            .ifPresent(profile -> {
                if (profile.getSpec().removeAsset(assetName)) {
                    client.update(profile);
                }
            });
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder
            .extension(new UserDecorationAsset())
            .build();
    }
}
