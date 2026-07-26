package com.timxs.interactionplus.core.constants;

/**
 * API 错误码。错误响应采用 ProblemDetail 风格，错误码置于 {@code code} 属性。
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    /** 装饰资产不存在。 */
    public static final String ASSET_NOT_FOUND = "ASSET_NOT_FOUND";

    /** 装饰资产不是启用状态。 */
    public static final String ASSET_NOT_ACTIVE = "ASSET_NOT_ACTIVE";

    /** 用户未拥有该装饰。 */
    public static final String DECORATION_NOT_OWNED = "DECORATION_NOT_OWNED";

    /** 授予已过期。 */
    public static final String GRANT_EXPIRED = "GRANT_EXPIRED";

    /** 授予已撤销。 */
    public static final String GRANT_REVOKED = "GRANT_REVOKED";

    /** 已存在有效授予。 */
    public static final String GRANT_ALREADY_EXISTS = "GRANT_ALREADY_EXISTS";

    /** 授予记录不存在。 */
    public static final String GRANT_NOT_FOUND = "GRANT_NOT_FOUND";

    /** 元数据（分类 / 标签 / 稀有度）不存在。 */
    public static final String METADATA_NOT_FOUND = "METADATA_NOT_FOUND";

    /** 身份标识映射不存在。 */
    public static final String MAPPING_NOT_FOUND = "MAPPING_NOT_FOUND";

    /** 佩戴档案版本冲突。 */
    public static final String PROFILE_CONFLICT = "PROFILE_CONFLICT";

    /** 装饰类型与槽位不匹配。 */
    public static final String INVALID_ASSET_TYPE = "INVALID_ASSET_TYPE";

    /** Halo 角色不存在。 */
    public static final String ROLE_NOT_FOUND = "ROLE_NOT_FOUND";

    /** Halo 用户不存在。 */
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";

    /** 批量请求超过上限。 */
    public static final String BATCH_LIMIT_EXCEEDED = "BATCH_LIMIT_EXCEEDED";

    /** SVG 安全校验失败。 */
    public static final String SVG_UNSAFE = "SVG_UNSAFE";

    /** 请求参数校验失败。 */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /** 状态流转非法。 */
    public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";
}
