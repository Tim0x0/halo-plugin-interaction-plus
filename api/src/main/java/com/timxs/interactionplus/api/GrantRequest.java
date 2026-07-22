package com.timxs.interactionplus.api;

import java.time.Instant;

/**
 * 发放请求。
 *
 * @param userName       被授予用户名（必填）
 * @param decorationName 装饰标识，即装饰的 metadata.name（必填；可在后台装饰编辑页复制，或经
 *                       {@link DecorationGrantApi#listGrantable} 取得）
 * @param sourcePlugin   调用方插件标识（必填，用于来源记录与撤销隔离）
 * @param expiresAt      过期时间（可选）；为空表示永久，非空时必须晚于当前时间（否则以
 *                       {@code IllegalArgumentException} 拒绝）。持续型可发短时效 + 周期续期，不续则自动失效
 * @param reason         发放原因（可选）
 */
public record GrantRequest(
    String userName,
    String decorationName,
    String sourcePlugin,
    Instant expiresAt,
    String reason
) {

    /** 无有效期、无原因的简便构造（永久发放）。 */
    public GrantRequest(String userName, String decorationName, String sourcePlugin) {
        this(userName, decorationName, sourcePlugin, null, null);
    }
}
