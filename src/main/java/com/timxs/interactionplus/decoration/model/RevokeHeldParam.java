package com.timxs.interactionplus.decoration.model;

import lombok.Data;

/**
 * 「全部撤销」参数：撤销某用户某装饰全部来源的有效授予（彻底收回，含外部插件发放的）。
 */
@Data
public class RevokeHeldParam {

    private String userName;

    private String assetName;

    /** 撤销原因（可选，随收回通知展示给用户）。 */
    private String reason;
}
