package com.timxs.interactionplus.identity.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.timxs.interactionplus.identity.model.PublicIdentityVo;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.PluginConfigUpdatedEvent;

/**
 * Public identity 短缓存（Caffeine，由 Halo 运行时提供依赖）。
 *
 * <p>规则：
 * <ul>
 *   <li>TTL 由插件配置决定（0 - 300 秒），0 表示关闭缓存；</li>
 *   <li>条目按写入时的 TTL 计算过期时间，读取时校验；</li>
 *   <li>支持按 userName 清理（换装、撤销时调用）；</li>
 *   <li>插件配置更新时全量失效（配置版本语义）。</li>
 * </ul>
 */
@Slf4j
@Component
public class PublicIdentityCache {

    private static final int MAX_ENTRIES = 10_000;

    private final Cache<String, Entry> cache = Caffeine.newBuilder()
        .maximumSize(MAX_ENTRIES)
        // 兜底过期：不超过配置允许的最大 TTL
        .expireAfterWrite(Duration.ofSeconds(300))
        .build();

    /**
     * 读取缓存；未命中或已过期返回 null。
     */
    @Nullable
    public PublicIdentityVo get(String userName) {
        var entry = cache.getIfPresent(userName);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            cache.invalidate(userName);
            return null;
        }
        return entry.value();
    }

    /**
     * 写入缓存。ttlSeconds 不大于 0 时不缓存。
     */
    public void put(String userName, PublicIdentityVo value, int ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        cache.put(userName, new Entry(value, Instant.now().plusSeconds(ttlSeconds)));
    }

    /**
     * 清理指定用户缓存（换装 / 撤销时调用）。
     */
    public void evict(String userName) {
        if (userName != null) {
            cache.invalidate(userName);
        }
    }

    /**
     * 全量清理。
     */
    public void clearAll() {
        cache.invalidateAll();
    }

    /**
     * 插件配置更新（含展示配置保存）时全量失效。
     */
    @EventListener(PluginConfigUpdatedEvent.class)
    public void onConfigUpdated(PluginConfigUpdatedEvent event) {
        log.debug("插件配置更新，清空 Public identity 缓存");
        clearAll();
    }

    private record Entry(PublicIdentityVo value, Instant expiresAt) {
    }
}
