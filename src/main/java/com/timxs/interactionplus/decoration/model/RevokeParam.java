package com.timxs.interactionplus.decoration.model;

import lombok.Data;

/**
 * 撤销授予请求体。
 */
@Data
public class RevokeParam {

    /** 撤销原因，可选。 */
    private String reason;
}
