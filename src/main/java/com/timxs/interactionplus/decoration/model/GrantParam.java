package com.timxs.interactionplus.decoration.model;

import java.time.Instant;
import java.util.List;
import lombok.Data;

/**
 * 授予请求体（单用户 / 多用户、单装饰 / 多装饰）。
 */
@Data
public class GrantParam {

    /** 被授予用户名列表，必填。 */
    private List<String> userNames;

    /** 装饰资产内部名列表，必填。 */
    private List<String> assetNames;

    /** 授予原因，可选。 */
    private String reason;

    /** 过期时间，可选；为空表示永久有效。 */
    private Instant expiresAt;
}
