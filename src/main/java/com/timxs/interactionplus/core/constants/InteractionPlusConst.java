package com.timxs.interactionplus.core.constants;

/**
 * 插件级常量：API group、版本、各类上限等。
 */
public final class InteractionPlusConst {

    private InteractionPlusConst() {
    }

    /** 自定义业务资源 API group。 */
    public static final String GROUP = "interaction-plus.timxs.com";

    /** API 版本。 */
    public static final String VERSION = "v1alpha1";

    /** Console CustomEndpoint API group。 */
    public static final String CONSOLE_API_GROUP = "console.api." + GROUP;

    /** UC CustomEndpoint API group。 */
    public static final String UC_API_GROUP = "uc.api." + GROUP;

    /** Public CustomEndpoint API group。 */
    public static final String PUBLIC_API_GROUP = "api." + GROUP;

    /** Public identity 批量查询单次最大用户数。 */
    public static final int PUBLIC_IDENTITY_BATCH_LIMIT = 50;

    /** 展示勋章最大佩戴数量（与 settings.yaml 卡片展示勋章上限联动）。 */
    public static final int BADGE_SHOWCASE_MAX = 8;

    /** 单个装饰最多标签数。 */
    public static final int ASSET_TAG_MAX = 5;

    /** 颜色校验：3 或 6 位十六进制（对齐 Halo Tag.color 的 schema 约束）。 */
    public static final String HEX_COLOR_PATTERN = "^#([a-fA-F0-9]{6}|[a-fA-F0-9]{3})$";
}
