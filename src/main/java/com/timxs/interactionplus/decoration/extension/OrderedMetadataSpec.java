package com.timxs.interactionplus.decoration.extension;

/**
 * 有序元数据（分类 / 标签 / 稀有度）spec 的公共字段写入口。
 * 三个 spec 由 Lombok 生成同名访问器，服务层据此把公共创建 / 更新逻辑收口一处。
 */
public interface OrderedMetadataSpec {

    void setDisplayName(String displayName);

    void setDescription(String description);

    void setEnabled(Boolean enabled);

    void setDisplayOrder(Integer displayOrder);
}
