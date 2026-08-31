package com.timxs.interactionplus.identity.finder;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.timxs.interactionplus.identity.model.PublicIdentityVo;
import com.timxs.interactionplus.identity.service.PublicIdentityService;
import java.net.URLEncoder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.PluginContext;
import run.halo.app.theme.finders.Finder;

/**
 * Halo 模板 Finder：让主题及独立插件前台模板能取得 Runtime 地址，并以 SSR 方式读取
 * 用户公开身份与装扮。
 *
 * <p>使用 Halo 的 {@code @Finder} 模板集成机制。身份与装饰墙查询复用
 * {@link PublicIdentityService}，与公开 REST API 使用相同的数据口径。
 *
 * <p><b>模板用法</b>（Thymeleaf）：
 * <pre>{@code
 * <!-- 单用户身份（含佩戴装饰、身份标识） -->
 * <div th:with="id = ${interactionPlus.getIdentity('alice')}">
 *   <img th:if="${id != null}" th:src="${id.avatar}" th:alt="${id.displayName}" />
 *   <span th:text="${id?.displayName}"></span>
 *   <!-- 称号（若佩戴）：titleText 是称号名称、恒非空；t.url 是可选的称号图
 *        （多为横条插画，缩到一行文字的高度会看不清，建议只在有垂直空间的位置用） -->
 *   <th:block th:if="${id?.decorations?.title}" th:with="t = ${id.decorations.title}">
 *     <span th:text="${t.titleText}"></span>
 *   </th:block>
 * </div>
 *
 * <!-- 装饰墙（用户获得的全部有效装饰，尊重公开开关） -->
 * <div th:each="d : ${interactionPlus.getDecorations('alice')}">
 *   <img th:src="${d.url}" th:title="${d.displayName}" />
 * </div>
 * }</pre>
 *
 * <p>{@code getIdentity} / {@code getDecorations} 返回的 {@link Mono} 由 Halo 模板引擎
 * 自动订阅解包，模板里可直接当结果对象使用；用户不存在 / 不可用时
 * {@code getIdentity} 返回空（模板用 {@code th:if} 判空）。
 *
 * <p>无需额外 RBAC：Finder 在主题渲染上下文（服务端）执行，内部经
 * {@link PublicIdentityService} 已完成公开过滤（剥离私有字段、尊重公开开关）。
 *
 * @author Tim0x0
 */
@Component
@Finder("interactionPlus")
@RequiredArgsConstructor
public class InteractionPlusFinder {

    private static final String RUNTIME_ASSET_PATH =
        "/plugins/%s/assets/runtime/interaction-plus.runtime.js";

    private final PublicIdentityService publicIdentityService;
    private final PluginContext pluginContext;

    /**
     * 返回带当前已安装插件版本的 Runtime 静态资源地址。
     *
     * <p>供主题或独立插件前台的 Thymeleaf 模板直接写入 {@code th:src}。Runtime 文件名固定，
     * 查询参数使缓存键对应当前插件版本，不参与资源路由。版本取自 Halo 注入的
     * {@link PluginContext}，接入方无需手工填写。
     *
     * @return 带版本查询参数的 Runtime URL
     */
    public String getRuntimeUrl() {
        var version = URLEncoder.encode(pluginContext.getVersion(), UTF_8);
        return RUNTIME_ASSET_PATH.formatted(pluginContext.getName()) + "?v=" + version;
    }

    /**
     * 单用户公开身份：基础信息 + 佩戴中的各类装饰 + 身份标识 + 站点级展示策略。
     *
     * <p>用户不存在或被禁用时返回空 Mono。
     *
     * @param userName 用户名（metadata.name）
     * @return 公开身份；无数据返回空
     */
    public Mono<PublicIdentityVo> getIdentity(String userName) {
        return publicIdentityService.getIdentity(userName);
    }

    /**
     * 装饰墙：该用户获得的、当前有效（资产 active + 存在有效授予）的全部装饰，
     * 按获得时间倒序去重，自包含完整展示信息。
     *
     * <p>尊重用户「公开装扮墙」开关——关闭时返回空列表；
     * 用户不存在或被禁用时同样返回空列表（与 {@link #getIdentity} 的隐藏口径一致，
     * 模板无需区分判空）。
     *
     * @param userName 用户名
     * @return 装饰列表（可能为空）
     */
    public Mono<List<PublicIdentityVo.DecorationVo>> getDecorations(String userName) {
        return publicIdentityService.resolveOwnedDecorations(userName)
            .defaultIfEmpty(List.of());
    }
}
