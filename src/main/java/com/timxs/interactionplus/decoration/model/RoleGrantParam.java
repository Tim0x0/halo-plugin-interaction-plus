package com.timxs.interactionplus.decoration.model;

import java.time.Instant;
import java.util.List;
import lombok.Data;

/**
 * 按 Halo 角色快照授予请求体。一次性快照，不做动态绑定。
 */
@Data
public class RoleGrantParam {

    /** 来源角色名列表，必填。 */
    private List<String> roleNames;

    /** 装饰资产内部名列表，必填。 */
    private List<String> assetNames;

    /** 授予原因，可选。 */
    private String reason;

    /** 过期时间，可选；为空表示永久有效。 */
    private Instant expiresAt;
}
