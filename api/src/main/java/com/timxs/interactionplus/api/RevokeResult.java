package com.timxs.interactionplus.api;

/**
 * 撤销结果。
 *
 * @param status 结果状态
 */
public record RevokeResult(Status status) {

    /** 撤销结果状态。 */
    public enum Status {
        /** 已撤销一条有效授予。 */
        REVOKED,
        /** 该用户当前无此装饰的有效授予（从未发放 / 已失效），幂等无操作。 */
        NOT_HELD,
        /** 存在有效授予但非本 sourcePlugin 发放，禁止跨来源撤销。 */
        FORBIDDEN_NOT_OWNER
    }

    public static RevokeResult of(Status status) {
        return new RevokeResult(status);
    }
}
