package com.timxs.interactionplus.decoration.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 批量授予结果（Console 侧）。单个用户失败不影响整批。
 * 与对外发奖 API 的 {@code com.timxs.interactionplus.api.GrantResult}（单笔三态）语义不同，
 * 刻意不同名以避免实现类里到处全限定名。
 */
@Data
public class BatchGrantResult {

    /** 成功授予的明细。 */
    private List<GrantItem> granted = new ArrayList<>();

    /** 续期的明细（已持有后台来源的有效授予，本次刷新了有效期）。 */
    private List<GrantItem> renewed = new ArrayList<>();

    /** 跳过的明细（已持有后台来源的有效授予，且本次有效期不比现有更晚）。 */
    private List<GrantItem> skipped = new ArrayList<>();

    /** 失败的明细。 */
    private List<GrantFailure> failed = new ArrayList<>();

    public int getSuccessCount() {
        return granted.size();
    }

    public int getRenewedCount() {
        return renewed.size();
    }

    public int getSkippedCount() {
        return skipped.size();
    }

    public int getFailedCount() {
        return failed.size();
    }

    /**
     * 授予明细项。
     */
    @Data
    public static class GrantItem {
        private String userName;
        /** 被授予用户显示名（优先展示，回退用户名）。 */
        private String userDisplayName;
        private String assetName;
        private String grantName;

        public GrantItem() {
        }

        public GrantItem(String userName, String assetName, String grantName) {
            this.userName = userName;
            this.assetName = assetName;
            this.grantName = grantName;
        }
    }

    /**
     * 授予失败项。
     */
    @Data
    public static class GrantFailure {
        private String userName;
        /** 被授予用户显示名（优先展示，回退用户名）。 */
        private String userDisplayName;
        private String assetName;
        /** 失败错误码。 */
        private String reason;

        public GrantFailure() {
        }

        public GrantFailure(String userName, String assetName, String reason) {
            this.userName = userName;
            this.assetName = assetName;
            this.reason = reason;
        }
    }
}
