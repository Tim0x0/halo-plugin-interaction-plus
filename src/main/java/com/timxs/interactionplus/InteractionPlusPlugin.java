package com.timxs.interactionplus;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationCategory;
import com.timxs.interactionplus.decoration.extension.UserDecorationGrant;
import com.timxs.interactionplus.decoration.extension.UserDecorationProfile;
import com.timxs.interactionplus.decoration.extension.UserDecorationRarity;
import com.timxs.interactionplus.decoration.extension.UserDecorationTag;
import com.timxs.interactionplus.identity.extension.UserIdentityMarkMapping;
import com.timxs.interactionplus.decoration.service.BootstrapService;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * 插件主类，管理 interaction-plus 插件生命周期：
 * 启动时注册自定义资源 Scheme 与查询索引，停止时注销。
 *
 * <p>级联清理 Reconciler 均为 {@code @Component}，由 Halo 的 {@code PluginControllerManager}
 * 在插件启动 / 停止时自动 setupWith+start / dispose，无需在此手动管理。
 *
 * @author Tim0x0
 * @since 0.1.0
 */
@Component
public class InteractionPlusPlugin extends BasePlugin {

    private final SchemeManager schemeManager;
    private final BootstrapService bootstrapService;

    public InteractionPlusPlugin(PluginContext pluginContext, SchemeManager schemeManager,
        BootstrapService bootstrapService) {
        super(pluginContext);
        this.schemeManager = schemeManager;
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void start() {
        registerAsset();
        registerGrant();
        registerProfile();
        registerCategory();
        registerTag();
        registerRarity();
        registerIdentityMarkMapping();
        // 级联清理 Reconciler 均为 @Component，由 Halo PluginControllerManager 在插件启动时
        // 自动 setupWith+start、停止时 dispose，无需在此手动启停。
        // 授予记录保留清理由 GrantRetentionService 的 @Scheduled 自动驱动
        // （插件上下文经 InteractionPlusConfiguration 的 @EnableScheduling 开启）。
        // 首次启动创建默认稀有度（幂等）
        bootstrapService.initializeDefaults().subscribe();
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(UserDecorationAsset.class));
        schemeManager.unregister(Scheme.buildFromType(UserDecorationGrant.class));
        schemeManager.unregister(Scheme.buildFromType(UserDecorationProfile.class));
        schemeManager.unregister(Scheme.buildFromType(UserDecorationCategory.class));
        schemeManager.unregister(Scheme.buildFromType(UserDecorationTag.class));
        schemeManager.unregister(Scheme.buildFromType(UserDecorationRarity.class));
        schemeManager.unregister(Scheme.buildFromType(UserIdentityMarkMapping.class));
    }

    private void registerAsset() {
        schemeManager.register(UserDecorationAsset.class, specs -> {
            specs.add(IndexSpecs.<UserDecorationAsset, String>single("spec.type", String.class)
                .indexFunc(asset -> asset.getSpec().getType()));
            specs.add(IndexSpecs.<UserDecorationAsset, String>single("spec.status", String.class)
                .indexFunc(asset -> asset.getSpec().getStatus()));
            specs.add(
                IndexSpecs.<UserDecorationAsset, String>single("spec.categoryName", String.class)
                    .indexFunc(asset -> asset.getSpec().getCategoryName()));
            specs.add(IndexSpecs.<UserDecorationAsset, String>multi("spec.tagNames", String.class)
                .indexFunc(asset -> {
                    var tagNames = asset.getSpec().getTagNames();
                    return tagNames == null ? Set.of() : Set.copyOf(tagNames);
                }));
            specs.add(
                IndexSpecs.<UserDecorationAsset, String>single("spec.rarityName", String.class)
                    .indexFunc(asset -> asset.getSpec().getRarityName()));
            specs.add(
                IndexSpecs.<UserDecorationAsset, String>single("spec.submittedBy", String.class)
                    .indexFunc(asset -> asset.getSpec().getSubmittedBy()));
            specs.add(
                IndexSpecs.<UserDecorationAsset, String>single("spec.createdBy", String.class)
                    .indexFunc(asset -> asset.getSpec().getCreatedBy()));
            specs.add(
                IndexSpecs.<UserDecorationAsset, String>single("spec.displayName", String.class)
                    .indexFunc(asset -> asset.getSpec().getDisplayName()));
        });
    }

    private void registerGrant() {
        schemeManager.register(UserDecorationGrant.class, specs -> {
            specs.add(IndexSpecs.<UserDecorationGrant, String>single("spec.userName", String.class)
                .indexFunc(grant -> grant.getSpec().getUserName()));
            specs.add(
                IndexSpecs.<UserDecorationGrant, String>single("spec.assetName", String.class)
                    .indexFunc(grant -> grant.getSpec().getAssetName()));
            specs.add(
                IndexSpecs.<UserDecorationGrant, Boolean>single("spec.revoked", Boolean.class)
                    .indexFunc(grant -> Boolean.TRUE.equals(grant.getSpec().getRevoked())));
            specs.add(
                IndexSpecs.<UserDecorationGrant, Instant>single("spec.expiresAt", Instant.class)
                    .indexFunc(grant -> grant.getSpec().getExpiresAt()));
            specs.add(
                IndexSpecs.<UserDecorationGrant, Instant>single("spec.grantedAt", Instant.class)
                    .indexFunc(grant -> grant.getSpec().getGrantedAt()));
            specs.add(IndexSpecs.<UserDecorationGrant, String>single("spec.sourceRoleName",
                    String.class)
                .indexFunc(grant -> grant.getSpec().getSourceRoleName()));
            specs.add(IndexSpecs.<UserDecorationGrant, String>single("spec.sourcePlugin",
                    String.class)
                .indexFunc(grant -> grant.getSpec().getSourcePlugin()));
        });
    }

    private void registerProfile() {
        schemeManager.register(UserDecorationProfile.class, specs -> specs.add(
            IndexSpecs.<UserDecorationProfile, String>single("spec.userName", String.class)
                .unique(true)
                .nullable(false)
                .indexFunc(profile -> profile.getSpec().getUserName())));
    }

    private void registerCategory() {
        schemeManager.register(UserDecorationCategory.class, specs -> {
            specs.add(
                IndexSpecs.<UserDecorationCategory, Boolean>single("spec.enabled", Boolean.class)
                    .indexFunc(category -> !Boolean.FALSE.equals(category.getSpec().getEnabled())));
            specs.add(IndexSpecs.<UserDecorationCategory, Integer>single("spec.displayOrder",
                    Integer.class)
                .indexFunc(category -> category.getSpec().getDisplayOrder()));
            specs.add(IndexSpecs.<UserDecorationCategory, String>single("spec.displayName",
                    String.class)
                .indexFunc(category -> category.getSpec().getDisplayName()));
        });
    }

    private void registerTag() {
        schemeManager.register(UserDecorationTag.class, specs -> {
            specs.add(IndexSpecs.<UserDecorationTag, Boolean>single("spec.enabled", Boolean.class)
                .indexFunc(tag -> !Boolean.FALSE.equals(tag.getSpec().getEnabled())));
            specs.add(
                IndexSpecs.<UserDecorationTag, Integer>single("spec.displayOrder", Integer.class)
                    .indexFunc(tag -> tag.getSpec().getDisplayOrder()));
            specs.add(
                IndexSpecs.<UserDecorationTag, String>single("spec.displayName", String.class)
                    .indexFunc(tag -> tag.getSpec().getDisplayName()));
        });
    }

    private void registerRarity() {
        schemeManager.register(UserDecorationRarity.class, specs -> {
            specs.add(
                IndexSpecs.<UserDecorationRarity, Boolean>single("spec.enabled", Boolean.class)
                    .indexFunc(rarity -> !Boolean.FALSE.equals(rarity.getSpec().getEnabled())));
            specs.add(IndexSpecs.<UserDecorationRarity, Integer>single("spec.displayOrder",
                    Integer.class)
                .indexFunc(rarity -> rarity.getSpec().getDisplayOrder()));
            specs.add(
                IndexSpecs.<UserDecorationRarity, String>single("spec.displayName", String.class)
                    .indexFunc(rarity -> rarity.getSpec().getDisplayName()));
        });
    }

    private void registerIdentityMarkMapping() {
        schemeManager.register(UserIdentityMarkMapping.class, specs -> {
            specs.add(
                IndexSpecs.<UserIdentityMarkMapping, String>single("spec.roleName", String.class)
                    .unique(true)
                    .nullable(false)
                    .indexFunc(mapping -> mapping.getSpec().getRoleName()));
            specs.add(
                IndexSpecs.<UserIdentityMarkMapping, Boolean>single("spec.enabled", Boolean.class)
                    .indexFunc(mapping -> !Boolean.FALSE.equals(mapping.getSpec().getEnabled())));
            specs.add(
                IndexSpecs.<UserIdentityMarkMapping, Integer>single("spec.priority", Integer.class)
                    .indexFunc(mapping -> mapping.getSpec().getPriority()));
            specs.add(IndexSpecs.<UserIdentityMarkMapping, String>single("spec.displayName",
                    String.class)
                .indexFunc(mapping -> mapping.getSpec().getDisplayName()));
        });
    }
}
