package com.timxs.interactionplus.template.service;

import com.timxs.interactionplus.core.constants.ErrorCodes;
import com.timxs.interactionplus.core.exception.InteractionPlusException;
import com.timxs.interactionplus.template.constants.TemplateComponent;
import com.timxs.interactionplus.template.extension.CustomTemplate;
import com.timxs.interactionplus.template.model.CustomTemplateParam;
import com.timxs.interactionplus.template.model.CustomTemplateVo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * 自定义模板服务。
 *
 * <p>固定三条记录（{@code metadata.name} = 组件名），启动时确保存在；
 * 读写都按枚举顺序逐条 fetch —— 只有三条，不建索引也不分页。
 *
 * <p>不加缓存：公开读路径是三次主键 fetch，且模板要求「保存后刷新即生效」；
 * TTL 缓存会破坏这个行为。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomTemplateService {

    /**
     * 指纹拼接分隔符（U+001F 单元分隔符）。
     *
     * <p>用控制字符而非可打印串：分隔符一旦可能出现在被拼接的内容里，
     * 「html 末尾多一段」与「css 开头多一段」就会拼出同一个字符串、算出同一个指纹。
     * HTML / CSS 里不会出现 U+001F。
     */
    private static final char FINGERPRINT_SEPARATOR = 0x1F;

    private final ReactiveExtensionClient client;

    /**
     * 确保三条记录存在（缺失则建空记录）。插件每次启动调用，幂等。
     *
     * <p>与默认稀有度不同，这里不记「已初始化」标记：三条记录是系统固定槽位而非
     * 用户内容，被误删后重建回来才是预期行为。
     */
    public Mono<Void> ensureDefaults() {
        return Flux.fromArray(TemplateComponent.values())
            .concatMap(component -> client.fetch(CustomTemplate.class, component.getValue())
                .switchIfEmpty(Mono.defer(() -> create(component))))
            .then();
    }

    private Mono<CustomTemplate> create(TemplateComponent component) {
        var template = new CustomTemplate();
        var metadata = new Metadata();
        metadata.setName(component.getValue());
        template.setMetadata(metadata);
        var spec = new CustomTemplate.Spec();
        spec.setComponent(component.getValue());
        spec.setEnabled(false);
        spec.setHtml("");
        spec.setCss("");
        template.setSpec(spec);
        log.info("创建自定义模板占位记录：{}", component.getValue());
        return client.create(template);
    }

    /** Console：按枚举顺序列出全部三条（含未启用）。 */
    public Mono<List<CustomTemplate>> listAll() {
        return Flux.fromArray(TemplateComponent.values())
            .concatMap(component -> client.fetch(CustomTemplate.class, component.getValue()))
            .collectList();
    }

    /** Console：保存单个组件的模板。记录缺失（被误删）时补建，保存不因此失败。 */
    public Mono<CustomTemplate> save(String component, CustomTemplateParam param) {
        var target = TemplateComponent.from(component);
        if (target == null) {
            return Mono.error(InteractionPlusException.badRequest(ErrorCodes.VALIDATION_FAILED,
                "参数校验失败", "组件只能是 identity / avatar / card。"));
        }
        return client.fetch(CustomTemplate.class, target.getValue())
            .switchIfEmpty(Mono.defer(() -> create(target)))
            .flatMap(template -> {
                var spec = template.getSpec();
                spec.setComponent(target.getValue());
                spec.setEnabled(Boolean.TRUE.equals(param.getEnabled()));
                spec.setHtml(param.getHtml() == null ? "" : param.getHtml());
                spec.setCss(param.getCss() == null ? "" : param.getCss());
                return client.update(template);
            });
    }

    /**
     * 前台：已启用且 HTML 非空白的模板，附内容指纹供 ETag 使用。
     *
     * <p>「开了开关但内容还没写」等同未启用 —— 下发空模板会让组件渲染成空白，
     * 比不自定义更糟。这一步在接口层做掉，runtime 只需「响应里有 = 用自定义」。
     */
    public Mono<Snapshot> loadEnabled() {
        return Flux.fromArray(TemplateComponent.values())
            .concatMap(component -> client.fetch(CustomTemplate.class, component.getValue()))
            .filter(template -> {
                var spec = template.getSpec();
                return spec != null && Boolean.TRUE.equals(spec.getEnabled())
                    && StringUtils.hasText(spec.getHtml());
            })
            .map(template -> {
                var spec = template.getSpec();
                var vo = new CustomTemplateVo();
                vo.setComponent(spec.getComponent());
                vo.setHtml(spec.getHtml());
                vo.setCss(spec.getCss() == null ? "" : spec.getCss());
                return vo;
            })
            .collectList()
            .map(items -> new Snapshot(items, fingerprint(items)));
    }

    /**
     * 内容指纹：只取实际下发的字段，改一个字符就变。
     *
     * <p>不用 {@code metadata.version} —— 那是三条记录各自的版本，还会因为
     * 「保存了但内容没变」而递增，导致无谓的 200；内容哈希才与响应体一一对应。
     */
    private static String fingerprint(List<CustomTemplateVo> items) {
        var canonical = new StringBuilder();
        for (var item : items) {
            canonical.append(item.getComponent()).append(FINGERPRINT_SEPARATOR)
                .append(item.getHtml()).append(FINGERPRINT_SEPARATOR)
                .append(item.getCss()).append(FINGERPRINT_SEPARATOR);
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            // 128 位足够：这是缓存校验不是安全边界，短一半可省报文
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制实现，不可能走到
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 一次读取的结果：下发内容与其指纹，保证 ETag 与响应体一致。 */
    public record Snapshot(List<CustomTemplateVo> items, String etag) {
    }
}
