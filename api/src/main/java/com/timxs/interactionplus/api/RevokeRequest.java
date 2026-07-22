package com.timxs.interactionplus.api;

/**
 * 撤销请求。
 *
 * @param userName       用户名（必填）
 * @param decorationName 装饰标识，即装饰的 metadata.name（必填）
 * @param sourcePlugin   调用方插件标识（必填）；只能撤销由相同来源发放的授予
 * @param reason         撤销原因（可选）
 */
public record RevokeRequest(
    String userName,
    String decorationName,
    String sourcePlugin,
    String reason
) {

    /** 无原因的简便构造。 */
    public RevokeRequest(String userName, String decorationName, String sourcePlugin) {
        this(userName, decorationName, sourcePlugin, null);
    }
}
