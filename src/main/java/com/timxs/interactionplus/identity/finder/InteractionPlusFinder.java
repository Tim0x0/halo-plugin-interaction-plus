package com.timxs.interactionplus.identity.finder;

import com.timxs.interactionplus.identity.model.PublicIdentityVo;
import com.timxs.interactionplus.identity.service.PublicIdentityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.theme.finders.Finder;

/**
 * 主题 Finder：让主题模板能以 SSR 方式读取用户公开身份与装扮。
 *
 * <p>对齐 Halo 主题集成正道（{@code @Finder}），补齐 hip-* Web Component 在 SEO /
 * 首屏渲染上的短板。复用 {@link PublicIdentityService} 的领域逻辑，与公开 REST API
 * （{@code /identity}、{@code /identity/{u}/decorations}）同源——三出口（REST / Finder /
 * Web Component）共用一套领域逻辑，返回完全一致、零漂移。
 *
 * <p><b>主题用法</b>（Thymeleaf）：
 * <pre>{@code
 * <!-- 单用户身份（含佩戴装饰、身份标识） -->
 * <div th:with="id = ${interactionPlus.getIdentity('alice')}">
 *   <img th:if="${id != null}" th:src="${id.avatar}" th:alt="${id.displayName}" />
 *   <span th:text="${id?.displayName}"></span>
 *   <!-- 称号（若佩戴）：image 形态渲染图片（titleText 为替代文本），text 形态渲染文字 -->
 *   <th:block th:if="${id?.decorations?.title}" th:with="t = ${id.decorations.title}">
 *     <img th:if="${t.titleMode == 'image' and t.url != null}"
 *          th:src="${t.url}" th:alt="${t.titleText}" style="height: 20px" />
 *     <span th:unless="${t.titleMode == 'image' and t.url != null}"
 *           th:text="${t.titleText}"></span>
 *   </th:block>
 * </div>
 *
 * <!-- 装饰墙（用户获得的全部有效装饰，尊重公开开关） -->
 * <div th:each="d : ${interactionPlus.getDecorations('alice')}">
 *   <img th:src="${d.url}" th:title="${d.displayName}" />
 * </div>
 * }</pre>
 *
 * <p>Finder 返回的 {@link Mono} 由 Halo 主题引擎自动订阅解包，模板里可直接当结果对象使用；
 * 用户不存在 / 不可用时 {@code getIdentity} 返回空（主题用 {@code th:if} 判空）。
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

    private final PublicIdentityService publicIdentityService;

    /**
     * 单用户公开身份：基础信息 + 佩戴中的各类装饰 + 身份标识 + 展示配置。
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
     * <p>尊重用户「公开装扮墙」开关——关闭时返回空列表。
     *
     * @param userName 用户名
     * @return 装饰列表（可能为空）
     */
    public Mono<List<PublicIdentityVo.DecorationVo>> getDecorations(String userName) {
        return publicIdentityService.resolveOwnedDecorations(userName);
    }
}
