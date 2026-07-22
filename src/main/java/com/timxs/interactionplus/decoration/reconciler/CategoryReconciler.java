package com.timxs.interactionplus.decoration.reconciler;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationCategory;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;

/**
 * 装饰分类删除级联清理：从引用资产清除 {@code spec.categoryName}。
 */
@Component
public class CategoryReconciler extends AbstractMetadataReconciler<UserDecorationCategory> {

    public CategoryReconciler(ExtensionClient client) {
        super(client);
    }

    @Override
    protected Class<UserDecorationCategory> type() {
        return UserDecorationCategory.class;
    }

    @Override
    protected UserDecorationCategory newExtension() {
        return new UserDecorationCategory();
    }

    @Override
    protected String assetField() {
        return "spec.categoryName";
    }

    @Override
    protected boolean references(UserDecorationAsset asset, String metadataName) {
        return metadataName.equals(asset.getSpec().getCategoryName());
    }

    @Override
    protected void removeReference(UserDecorationAsset asset, String metadataName) {
        asset.getSpec().setCategoryName(null);
    }
}
