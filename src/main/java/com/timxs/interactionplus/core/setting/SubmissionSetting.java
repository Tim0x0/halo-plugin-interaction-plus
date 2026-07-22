package com.timxs.interactionplus.core.setting;

import lombok.Data;

/**
 * 投稿配置，对应 settings.yaml decoration.management 组的 submission 节。
 */
@Data
public class SubmissionSetting {

    /** 是否启用用户投稿入口；实际可见性仍由 decoration:submit 权限控制。 */
    private boolean enabled = true;
}
