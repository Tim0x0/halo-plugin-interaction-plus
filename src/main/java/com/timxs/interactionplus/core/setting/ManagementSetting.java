package com.timxs.interactionplus.core.setting;

import lombok.Data;

/**
 * 装饰管理配置，对应 settings.yaml 的 decoration.management 组。
 * 组内以 formkit group 分节（submission / cleanup），数据按节名嵌套。
 */
@Data
public class ManagementSetting {

    /** 用户投稿节。 */
    private SubmissionSetting submission = new SubmissionSetting();

    /** 数据清理节。 */
    private CleanupSetting cleanup = new CleanupSetting();
}
