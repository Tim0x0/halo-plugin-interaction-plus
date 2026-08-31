package com.timxs.interactionplus.api;

import java.util.Collection;
import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 对外身份查询 API：供其他 Halo 插件在<b>后端（进程内）</b>查询用户的公开身份与装扮数据，
 * 用于服务端聚合 / 内嵌到调用方自己的 API 响应中。
 *
 * <p><b>与其他读出口同源</b>：内部与公开 HTTP API（{@code GET /identity/{userName}}）、Halo 模板 Finder
 * （{@code interactionPlus.getIdentity(...)}）共用同一聚合服务，返回内容、判空行为、
 * 对用户禁用 / 删除的处理三个出口一致，仅出口形态不同。只读、无副作用，可任意重试。
 *
 * <p><b>获取方式</b>：Halo 各插件的 Spring 容器相互隔离，外部<b>无法直接 {@code @Autowired}</b> 本接口实现。
 * 外部插件应注入 Halo 核心的 {@code run.halo.app.plugin.extensionpoint.ExtensionGetter}，通过
 * {@code getEnabledExtension(PublicIdentityQueryApi.class)} 取得本插件提供的实现（本接口已声明 SINGLETON 的
 * {@code ExtensionPointDefinition}）：
 *
 * <pre>{@code
 * extensionGetter.getEnabledExtension(PublicIdentityQueryApi.class)
 *     .flatMapMany(api -> api.listIdentities(userNames))
 *     // interaction-plus 缺席时为空流，下游自然跳过
 * }</pre>
 *
 * <p>外部插件还需在自身 {@code plugin.yaml} 声明 {@code interaction-plus} 可选依赖，
 * 版本范围为 {@code >=1.0.0}，并使用 {@code api:1.0.0} 构件。完整配置见对外插件 API 对接指南。
 *
 * <p><b>API 契约版本</b>：{@code 1.0.0}。
 *
 * @author Tim0x0
 * @since 1.0.0
 */
public interface PublicIdentityQueryApi extends ExtensionPoint {

    /**
     * 查询单个用户的公开身份。
     *
     * <p>用户不存在或被禁用时返回空 {@code Mono}（不报错）。
     *
     * @param userName 用户名（{@code metadata.name}）；空白时以 {@link IllegalArgumentException}
     *                 走 error 通道
     * @return 公开身份聚合数据
     */
    Mono<PublicIdentity> getIdentity(String userName);

    /**
     * 批量查询公开身份。
     *
     * <p>自动忽略空白项并去重；去重后单次上限 50，超出以 {@link IllegalArgumentException}
     * 走 error 通道（上限数值与公开 HTTP 批量接口一致）。查不到 / 被禁用的用户不出现在结果中
     * （不占位、不报错），调用方按 {@link PublicIdentity#userName()} 关联即可；
     * 单个用户聚合失败不影响整批（该用户同样缺席）。{@code userNames} 为 {@code null}
     * 或全空白时返回空 {@code Flux}。
     *
     * @param userNames 用户名集合
     * @return 公开身份聚合数据流，按入参去重后的顺序返回
     */
    Flux<PublicIdentity> listIdentities(Collection<String> userNames);
}
