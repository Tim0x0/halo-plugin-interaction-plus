package com.timxs.interactionplus.decoration.model;

import com.timxs.interactionplus.decoration.extension.UserDecorationAsset;
import java.util.List;
import lombok.Data;

/**
 * 装饰资产创建与更新请求体。
 * status、createdBy、submittedBy 等由系统控制，不在请求体中。
 */
@Data
public class DecorationAssetParam {

    /** 装饰类型，创建后不可修改。 */
    private String type;

    /** 显示名称，必填。 */
    private String displayName;

    /** 描述。 */
    private String description;

    /** 分类内部名，可为空。 */
    private String categoryName;

    /** 标签内部名列表，最多 5 个。 */
    private List<String> tagNames;

    /** 稀有度内部名，可为空。 */
    private String rarityName;

    /** 素材引用。 */
    private UserDecorationAsset.AssetRef asset;

    /** 类型扩展字段。 */
    private UserDecorationAsset.Payload payload;
}
