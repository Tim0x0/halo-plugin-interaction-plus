package com.timxs.interactionplus.core.setting;

import lombok.Data;

/**
 * 数据清理配置，对应 settings.yaml decoration.management 组的 cleanup 节。
 */
@Data
public class CleanupSetting {

    /** 失效授予记录（已撤销 / 已过期）保留天数；0 表示永不清理。 */
    private int grantRetentionDays = 0;
}
