package com.timxs.interactionplus.decoration.service;

import com.timxs.interactionplus.core.support.NameGenerator;
import com.timxs.interactionplus.decoration.extension.UserDecorationRarity;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * 插件引导：首次启动时创建默认稀有度。
 *
 * <p>使用独立标记 ConfigMap 记录初始化状态，保证幂等且尊重用户后续删除：
 * 用户删除默认稀有度后，重启 / 重载不会重复创建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapService {

    /** 记录初始化状态的标记 ConfigMap，与 settings 的 configMap 区分。 */
    private static final String BOOTSTRAP_CONFIGMAP = "interaction-plus-bootstrap-state";

    private static final String KEY_RARITY_INITIALIZED = "rarityInitialized";

    private final ReactiveExtensionClient client;

    /**
     * 默认稀有度定义：显示名、颜色、排序。
     */
    private static final List<DefaultRarity> DEFAULT_RARITIES = List.of(
        new DefaultRarity("普通", "#9ca3af", 0),
        new DefaultRarity("稀有", "#3b82f6", 1),
        new DefaultRarity("史诗", "#a855f7", 2),
        new DefaultRarity("传说", "#f59e0b", 3),
        new DefaultRarity("限定", "#ef4444", 4)
    );

    /**
     * 执行初始化。已初始化则跳过。
     */
    public Mono<Void> initializeDefaults() {
        return client.fetch(ConfigMap.class, BOOTSTRAP_CONFIGMAP)
            .map(configMap -> {
                var data = configMap.getData();
                return data != null && "true".equals(data.get(KEY_RARITY_INITIALIZED));
            })
            .defaultIfEmpty(false)
            .flatMap(initialized -> {
                if (initialized) {
                    return Mono.empty();
                }
                return createDefaultRarities().then(markInitialized());
            })
            .doOnError(e -> log.error("初始化默认稀有度失败", e))
            .then();
    }

    private Mono<Void> createDefaultRarities() {
        return Flux.fromIterable(DEFAULT_RARITIES)
            .concatMap(this::createRarity)
            .then();
    }

    private Mono<UserDecorationRarity> createRarity(DefaultRarity def) {
        var rarity = new UserDecorationRarity();
        var metadata = new Metadata();
        metadata.setName(NameGenerator.generate("rarity"));
        rarity.setMetadata(metadata);
        var spec = new UserDecorationRarity.Spec();
        spec.setDisplayName(def.displayName());
        spec.setColor(def.color());
        spec.setDisplayOrder(def.displayOrder());
        spec.setEnabled(true);
        rarity.setSpec(spec);
        log.info("创建默认稀有度：{}", def.displayName());
        return client.create(rarity);
    }

    private Mono<Void> markInitialized() {
        var configMap = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName(BOOTSTRAP_CONFIGMAP);
        configMap.setMetadata(metadata);
        configMap.setData(Map.of(KEY_RARITY_INITIALIZED, "true"));
        return client.create(configMap).then();
    }

    private record DefaultRarity(String displayName, String color, int displayOrder) {
    }
}
