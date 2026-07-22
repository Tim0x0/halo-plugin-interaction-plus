package com.timxs.interactionplus.decoration.model;

import java.util.List;
import lombok.Data;

/**
 * 保存当前佩戴请求体。各字段为装饰资产内部名。
 */
@Data
public class ProfileSaveParam {

    private String avatarFrame;
    private String title;
    private String primaryBadge;
    private List<String> badgeShowcase;
    private String cardBackground;
    private String nameStyle;
}
