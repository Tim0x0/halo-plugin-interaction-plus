package com.timxs.interactionplus.decoration.model;

import lombok.Data;

/**
 * 「公开装扮墙」可见性设置请求体（UC /profile/visibility）。
 */
@Data
public class VisibilityParam {

    /** 是否公开展示装扮墙；为空视为公开。 */
    private Boolean publicDecorationsVisible;
}
