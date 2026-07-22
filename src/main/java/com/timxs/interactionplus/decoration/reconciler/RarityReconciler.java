package com.timxs.interactionplus.decoration.reconciler;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationRarity;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;

/**
 * 装饰稀有度删除级联清理：从引用资产清除 {@code spec.rarityName}。
 */
@Component
public class RarityReconciler extends AbstractMetadataReconciler<UserDecorationRarity> {

    public RarityReconciler(ExtensionClient client) {
        super(client);
    }

    @Override
    protected Class<UserDecorationRarity> type() {
        return UserDecorationRarity.class;
    }

    @Override
    protected UserDecorationRarity newExtension() {
        return new UserDecorationRarity();
    }

    @Override
    protected String assetField() {
        return "spec.rarityName";
    }

    @Override
    protected boolean references(UserDecorationAsset asset, String metadataName) {
        return metadataName.equals(asset.getSpec().getRarityName());
    }

    @Override
    protected void removeReference(UserDecorationAsset asset, String metadataName) {
        asset.getSpec().setRarityName(null);
    }
}
