package com.timxs.interactionplus.decoration.reconciler;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import com.timxs.interactionplus.decoration.extension.UserDecorationTag;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;

/**
 * 装饰标签删除级联清理：从引用资产的 {@code spec.tagNames} 移除该标签。
 * List.contains / remove 为精确比较，不受索引数组字段 equal 语义影响。
 */
@Component
public class TagReconciler extends AbstractMetadataReconciler<UserDecorationTag> {

    public TagReconciler(ExtensionClient client) {
        super(client);
    }

    @Override
    protected Class<UserDecorationTag> type() {
        return UserDecorationTag.class;
    }

    @Override
    protected UserDecorationTag newExtension() {
        return new UserDecorationTag();
    }

    @Override
    protected String assetField() {
        return "spec.tagNames";
    }

    @Override
    protected boolean references(UserDecorationAsset asset, String metadataName) {
        var tagNames = asset.getSpec().getTagNames();
        return tagNames != null && tagNames.contains(metadataName);
    }

    @Override
    protected void removeReference(UserDecorationAsset asset, String metadataName) {
        var tagNames = asset.getSpec().getTagNames();
        if (tagNames != null) {
            tagNames.remove(metadataName);
        }
    }
}
