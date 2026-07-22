package com.timxs.interactionplus.api;

import java.util.Collection;
import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 对外身份查询 API：供其他 Halo 插件在<b>后端（进程内）</b>查询用户的公开身份与装扮数据，
 * 用于服务端聚合 / 内嵌到调用方自己的 API 响应中。
 *
 * <p><b>与其他读出口同源</b>：内部与公开 HTTP API（{@code GET /identity/{userName}}）、主题 Finder
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
 * <p>外部插件还需在自身 {@code plugin.yaml} 以<b>可选依赖</b>声明本插件（缺席时不影响外部启动）：
 *
 * <pre>{@code
 * spec:
 *   pluginDependencies:
 *     "interaction-plus?": ">=0.1.0"   # 末尾问号 = 可选依赖
 * }</pre>
 *
 * <p><b>脱敏责任</b>：返回的 {@link PublicIdentity} 含 {@code userName}（进程内做关联 / 缓存键用）。
 * 若把查询结果内嵌进你自己的<b>公开</b> API 响应，下发浏览器前<b>必须剥掉 {@code userName}</b>——
 * Halo 对评论者等互动用户的用户名明确脱敏（防枚举），不要替它解除；displayName、avatar
 * 及各装扮展示字段可以下发。
 *
 * <p><b>稳定性</b>：随 {@code 0.1.x} 以<b>实验性</b>状态发布——契约已定型但尚未经大规模生产验证，
 * 可能随首批接入者的反馈微调；调整将保持前向兼容（只增不改）并同步到对接文档。
 *
 * @author Tim0x0
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
     * 走 error 通道（与公开 HTTP 批量接口一致）。查不到 / 被禁用的用户不出现在结果中
     * （不占位、不报错），调用方按 {@link PublicIdentity#userName()} 关联即可；
     * 单个用户聚合失败不影响整批（该用户同样缺席）。{@code userNames} 为 {@code null}
     * 或全空白时返回空 {@code Flux}。
     *
     * @param userNames 用户名集合
     * @return 公开身份聚合数据流，按入参去重后的顺序返回
     */
    Flux<PublicIdentity> listIdentities(Collection<String> userNames);
}
