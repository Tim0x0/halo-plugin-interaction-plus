package com.timxs.interactionplus.api;

/**
 * 发放结果。
 *
 * @param status    结果状态
 * @param grantName 命中、续期或新建的授予记录内部名（仅 GRANTED / RENEWED / ALREADY_HELD 有值，否则 null）
 */
public record GrantResult(Status status, String grantName) {

    /** 发放结果状态。 */
    public enum Status {
        /** 已新发放一条有效授予。 */
        GRANTED,
        /** 已持有你（同 sourcePlugin）发放的有效授予，本次已把有效期延长为请求的 expiresAt（仅新值更晚才刷新）。 */
        RENEWED,
        /** 已持有你（同 sourcePlugin）发放的有效授予、且本次有效期不比现有更晚，幂等跳过。 */
        ALREADY_HELD,
        /** 装饰不存在（decorationName 无效）。 */
        DECORATION_NOT_FOUND,
        /** 装饰未启用（非 active）。 */
        DECORATION_INACTIVE,
        /** 装饰所属稀有度不允许外部发放。 */
        DECORATION_NOT_EXTERNALLY_GRANTABLE,
        /** 用户不存在。 */
        USER_NOT_FOUND
    }

    /** 是否表示用户最终持有该装饰（新发、续期或已持有）。 */
    public boolean isHeld() {
        return status == Status.GRANTED || status == Status.RENEWED
            || status == Status.ALREADY_HELD;
    }

    public static GrantResult granted(String grantName) {
        return new GrantResult(Status.GRANTED, grantName);
    }

    public static GrantResult renewed(String grantName) {
        return new GrantResult(Status.RENEWED, grantName);
    }

    public static GrantResult alreadyHeld(String grantName) {
        return new GrantResult(Status.ALREADY_HELD, grantName);
    }

    public static GrantResult of(Status status) {
        return new GrantResult(status, null);
    }
}
