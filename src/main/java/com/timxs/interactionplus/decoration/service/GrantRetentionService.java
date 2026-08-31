package com.timxs.interactionplus.decoration.service;

import static run.halo.app.extension.index.query.Queries.isNull;

import com.timxs.interactionplus.decoration.extension.UserDecorationGrant;
import com.timxs.interactionplus.core.setting.InteractionPlusSettingService;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * 授予记录自动保留清理（按 settings 的 grantRetentionDays）。
 *
 * <p>每天凌晨 3 点（低峰期）扫描，删除「已撤销 / 已过期且失效时间超过保留天数」的授予记录；
 * 有效授予永不删除；grantRetentionDays=0 时不清理。删除幂等。
 *
 * <p>定时由 Spring {@link Scheduled} 驱动。
 * 插件上下文经 {@code InteractionPlusConfiguration} 的 {@code @EnableScheduling} 开启调度；
 * 插件停用时上下文关闭，定时任务自动取消，无需手动管理生命周期。
 *
 * @author Tim0x0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrantRetentionService {

    /** block 超时保护，防止单次清理异常卡住调度线程。 */
    private static final Duration CLEANUP_TIMEOUT = Duration.ofMinutes(10);

    private final ReactiveExtensionClient client;
    private final InteractionPlusSettingService settingService;

    /**
     * 每天凌晨 3 点执行清理。
     *
     * <p>在调度线程上 {@code block} 等待本次清理完成，确保 {@code @Scheduled} 的
     * 串行语义（上一次未结束不会重叠触发下一次）；超时仅作兜底防卡死。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanUp() {
        sweepOnce().block(CLEANUP_TIMEOUT);
    }

    /** 执行一次清理。失败仅记录日志，不抛出。 */
    public Mono<Void> sweepOnce() {
        return settingService.getGrantRetentionDays()
            .filter(days -> days > 0)
            .flatMap(days -> {
                var now = Instant.now();
                var cutoff = now.minus(Duration.ofDays(days));
                var options = ListOptions.builder()
                    .fieldQuery(isNull("metadata.deletionTimestamp"))
                    .build();
                return client.listAll(UserDecorationGrant.class, options,
                        Sort.by(Sort.Order.asc("metadata.name")))
                    .filter(grant -> isExpiredOrRevokedBefore(grant, now, cutoff))
                    .concatMap(grant -> client.delete(grant)
                        .onErrorResume(e -> {
                            // 单条失败不阻断整轮，下个周期会重试，留 debug 供比对删除数
                            log.debug("删除失效授予记录 {} 失败，本次跳过",
                                grant.getMetadata().getName(), e);
                            return Mono.empty();
                        }))
                    .count()
                    .doOnNext(deleted -> {
                        if (deleted > 0) {
                            log.info("授予记录保留清理：删除 {} 条超过 {} 天的失效记录", deleted, days);
                        }
                    })
                    .then();
            })
            .onErrorResume(e -> {
                log.warn("授予记录保留清理执行失败，将在下个周期重试", e);
                return Mono.empty();
            });
    }

    /**
     * 判定为「已失效且失效时间早于 cutoff」的死记录：
     * 已撤销 → 以 revokedAt（缺失回退 grantedAt）判断；
     * 已过期（未撤销但 expiresAt 已过）→ 以 expiresAt 判断。
     * 有效授予一律返回 false。
     */
    private boolean isExpiredOrRevokedBefore(UserDecorationGrant grant, Instant now,
        Instant cutoff) {
        var spec = grant.getSpec();
        return switch (grant.stateAt(now)) {
            case REVOKED -> {
                var deadAt = spec.getRevokedAt() != null ? spec.getRevokedAt()
                    : spec.getGrantedAt();
                yield deadAt != null && deadAt.isBefore(cutoff);
            }
            case EXPIRED -> spec.getExpiresAt().isBefore(cutoff);
            case ACTIVE -> false;
        };
    }
}
