package com.timxs.interactionplus.decoration.constants;

import org.jspecify.annotations.Nullable;

/**
 * 装饰资产状态。
 *
 * <p>状态流转：
 * <pre>
 * draft -&gt; active
 * active -&gt; disabled
 * active -&gt; archived
 * disabled -&gt; active
 * disabled -&gt; archived
 * archived -&gt; disabled（取消归档）
 * </pre>
 *
 * <p>删除不是状态：任何状态均可删除（级联撤销全部有效授予，见
 * {@code DecorationAssetService#deleteAsset}）；仅 UC 投稿删除限草稿状态。
 */
public enum DecorationStatus {

    DRAFT("draft"),
    ACTIVE("active"),
    DISABLED("disabled"),
    ARCHIVED("archived");

    private final String value;

    DecorationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public static DecorationStatus from(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (DecorationStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 校验状态流转是否合法。
     */
    public boolean canTransitionTo(DecorationStatus target) {
        return switch (this) {
            case DRAFT -> target == ACTIVE;
            case ACTIVE -> target == DISABLED || target == ARCHIVED;
            case DISABLED -> target == ACTIVE || target == ARCHIVED;
            case ARCHIVED -> target == DISABLED;
        };
    }
}
