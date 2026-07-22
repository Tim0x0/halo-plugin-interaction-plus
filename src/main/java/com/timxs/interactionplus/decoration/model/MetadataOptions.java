package com.timxs.interactionplus.decoration.model;

import com.timxs.interactionplus.decoration.extension.UserDecorationCategory;
import com.timxs.interactionplus.decoration.extension.UserDecorationRarity;
import com.timxs.interactionplus.decoration.extension.UserDecorationTag;
import java.util.List;
import lombok.Data;

/**
 * 元数据选项（启用项），供投稿表单与筛选使用。
 */
@Data
public class MetadataOptions {

    private List<UserDecorationCategory> categories;
    private List<UserDecorationTag> tags;
    private List<UserDecorationRarity> rarities;
}
