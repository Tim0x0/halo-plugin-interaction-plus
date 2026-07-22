package com.timxs.interactionplus.identity.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 公开身份批量查询请求与结果。
 */
public final class PublicIdentityBatch {

    private PublicIdentityBatch() {
    }

    /**
     * 批量请求体。单次最多 50 个用户名，自动去重。
     */
    @Data
    public static class Request {
        private List<String> userNames;
    }

    /**
     * 批量结果。单个用户失败不影响整批；不存在或不可用用户进入 skipped。
     */
    @Data
    public static class Result {
        private List<PublicIdentityVo> items = new ArrayList<>();
        private List<String> skipped = new ArrayList<>();
    }
}
