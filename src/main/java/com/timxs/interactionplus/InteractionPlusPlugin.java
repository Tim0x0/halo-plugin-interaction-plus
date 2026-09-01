package com.timxs.interactionplus;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationCategory;
import com.timxs.interactionplus.decoration.extension.UserDecorationGrant;
import com.timxs.interactionplus.decoration.extension.UserDecorationProfile;
import com.timxs.interactionplus.decoration.extension.UserDecorationRarity;
import com.timxs.interactionplus.decoration.extension.UserDecorationTag;
import com.timxs.interactionplus.identity.extension.UserIdentityMarkMapping;
import com.timxs.interactionplus.template.extension.CustomTemplate;
import com.timxs.interactionplus.template.service.CustomTemplateService;
import com.timxs.interactionplus.decoration.service.BootstrapService;
import java.time.Instant;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.AbstractExtension;
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
 * @since 1.0.0
 */
@Slf4j
@Component
public class InteractionPlusPlugin extends BasePlugin {

    private final SchemeManager schemeManager;
    private final BootstrapService bootstrapService;
    private final CustomTemplateService customTemplateService;

    public InteractionPlusPlugin(PluginContext pluginContext, SchemeManager schemeManager,
        BootstrapService bootstrapService, CustomTemplateService customTemplateService) {
        super(pluginContext);
        this.schemeManager = schemeManager;
        this.bootstrapService = bootstrapService;
        this.customTemplateService = customTemplateService;
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
        registerCustomTemplate();
        // 空错误消费者兜底，避免无参 subscribe 触发 onErrorDropped
        bootstrapService.initializeDefaults().subscribe(null, error -> { });
        // 自定义模板三条固定记录（幂等，缺失即补建）
        customTemplateService.ensureDefaults()
            .subscribe(null, error -> log.error("初始化自定义模板记录失败", error));
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
        schemeManager.unregister(Scheme.buildFromType(CustomTemplate.class));
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
        registerOrderedMetadata(UserDecorationCategory.class,
            category -> category.getSpec().getEnabled(),
            category -> category.getSpec().getDisplayOrder(),
            category -> category.getSpec().getDisplayName());
    }

    private void registerTag() {
        registerOrderedMetadata(UserDecorationTag.class,
            tag -> tag.getSpec().getEnabled(),
            tag -> tag.getSpec().getDisplayOrder(),
            tag -> tag.getSpec().getDisplayName());
    }

    private void registerRarity() {
        registerOrderedMetadata(UserDecorationRarity.class,
            rarity -> rarity.getSpec().getEnabled(),
            rarity -> rarity.getSpec().getDisplayOrder(),
            rarity -> rarity.getSpec().getDisplayName());
    }

    /**
     * 有序元数据（分类 / 标签 / 稀有度）统一注册同一组索引：
     * enabled（!FALSE 即启用）、displayOrder、displayName，三类仅实体类型不同。
     */
    private <E extends AbstractExtension> void registerOrderedMetadata(Class<E> type,
        Function<E, Boolean> enabled, Function<E, Integer> displayOrder,
        Function<E, String> displayName) {
        schemeManager.register(type, specs -> {
            specs.add(IndexSpecs.<E, Boolean>single("spec.enabled", Boolean.class)
                .indexFunc(ext -> !Boolean.FALSE.equals(enabled.apply(ext))));
            specs.add(IndexSpecs.<E, Integer>single("spec.displayOrder", Integer.class)
                .indexFunc(displayOrder::apply));
            specs.add(IndexSpecs.<E, String>single("spec.displayName", String.class)
                .indexFunc(displayName::apply));
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

    /**
     * 自定义模板：固定三条记录，一律按 {@code metadata.name}（组件名）主键直取，
     * 无列表查询与排序需求，故不建业务索引。
     */
    private void registerCustomTemplate() {
        schemeManager.register(CustomTemplate.class);
    }
}
