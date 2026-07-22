package com.timxs.interactionplus.core.setting;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * 插件配置读取服务。统一从 settings/ConfigMap 读取，并提供默认值兜底。
 */
@Service
@RequiredArgsConstructor
public class InteractionPlusSettingService {

    static final String GROUP_DISPLAY = "decoration.display";
    static final String GROUP_MANAGEMENT = "decoration.management";

    private final ReactiveSettingFetcher settingFetcher;

    /**
     * 读取展示配置（含场景密度，同一 display 组内以 formkit 分节标题组织），
     * 无配置时返回默认值。
     */
    public Mono<DisplaySetting> getDisplaySetting() {
        return settingFetcher.fetch(GROUP_DISPLAY, DisplaySetting.class)
            .defaultIfEmpty(new DisplaySetting());
    }

    /**
     * 读取装饰管理配置（投稿 / 素材阈值 / 清理合并组），无配置时返回默认值。
     */
    private Mono<ManagementSetting> getManagementSetting() {
        return settingFetcher.fetch(GROUP_MANAGEMENT, ManagementSetting.class)
            .defaultIfEmpty(new ManagementSetting());
    }

    /**
     * 读取投稿配置（management 组 submission 节），无配置时返回默认值。
     */
    public Mono<SubmissionSetting> getSubmissionSetting() {
        return getManagementSetting()
            .map(management -> management.getSubmission() != null
                ? management.getSubmission() : new SubmissionSetting());
    }

    /**
     * Public identity 缓存 TTL（秒），限制在 0 - 300。
     * 静态换算：聚合链上已持有 DisplaySetting 时直接取值，避免为拿 TTL 再解析一遍配置。
     */
    public static int clampPublicIdentityCacheTtl(DisplaySetting display) {
        return Math.max(0, Math.min(300, display.getPublicIdentityCacheTtlSeconds()));
    }

    /**
     * 读取数据清理配置（management 组 cleanup 节），无配置时返回默认值。
     */
    public Mono<CleanupSetting> getCleanupSetting() {
        return getManagementSetting()
            .map(management -> management.getCleanup() != null
                ? management.getCleanup() : new CleanupSetting());
    }

    /**
     * 失效授予记录保留天数（非负，0 表示永不清理）。
     */
    public Mono<Integer> getGrantRetentionDays() {
        return getCleanupSetting().map(cleanup -> Math.max(0, cleanup.getGrantRetentionDays()));
    }
}
