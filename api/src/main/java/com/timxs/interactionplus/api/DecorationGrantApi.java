package com.timxs.interactionplus.api;

import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 对外发奖 API：供其他 Halo 插件调用，查询 / 发放 / 撤销用户装饰。
 *
 * <p>外部插件自行判断业务条件（如签到、积分达标等），在条件达成 / 中断的那一刻主动调用本接口；
 * 本插件只负责落地发放与撤销，不感知、不监听任何条件。
 *
 * <p><b>获取方式</b>：Halo 各插件的 Spring 容器相互隔离，外部<b>无法直接 {@code @Autowired}</b> 本接口实现。
 * 外部插件应注入 Halo 核心的 {@code run.halo.app.plugin.extensionpoint.ExtensionGetter}，通过
 * {@code getEnabledExtension(DecorationGrantApi.class)} 取得本插件提供的实现（本接口已声明 SINGLETON 的
 * {@code ExtensionPointDefinition}）：
 *
 * <pre>{@code
 * extensionGetter.getEnabledExtension(DecorationGrantApi.class)
 *     .flatMap(api -> api.grant(
 *         new GrantRequest("alice", decorationName, "my-checkin-plugin")));
 * }</pre>
 *
 * <p>{@code decorationName} 是装饰的 {@code metadata.name}：站长可在后台装饰编辑页复制，
 * 或外部经 {@link #listGrantable(String)} 拉清单让站长可视化选择。
 *
 * <p>外部插件还需在自身 {@code plugin.yaml} 以<b>可选依赖</b>声明本插件（缺席时不影响外部启动）：
 *
 * <pre>{@code
 * spec:
 *   pluginDependencies:
 *     "interaction-plus?": ">=0.1.0"   # 末尾问号 = 可选依赖
 * }</pre>
 *
 * <p>用法对齐 halo-dev 官方 {@code plugin-ai-foundation}（{@code AiModelService}）被
 * {@code plugin-live2d} 经 {@code getEnabledExtension} 调用的范式。
 *
 * <p><b>稳定性</b>：随 {@code 0.1.x} 以<b>实验性</b>状态发布——契约已定型但尚未经大规模生产验证，
 * 可能随首批接入者的反馈微调；调整将保持前向兼容（只增不改）并同步到对接文档。
 *
 * @author Tim0x0
 */
public interface DecorationGrantApi extends ExtensionPoint {

    /**
     * 列出当前可被外部发放的装饰，供外部做可视化选择或让站长挑选。
     *
     * <p>只返回 <b>已启用（active）</b> 且 <b>所属稀有度允许外部发放</b> 的装饰，与 {@link #grant} 的可发判定一致。
     *
     * @param categoryName 按分类标识筛选；为空 / 空串表示不限分类
     * @return 可发装饰列表（自包含显示信息）
     */
    Flux<GrantableDecoration> listGrantable(String categoryName);

    /**
     * 发放装饰。
     *
     * <p>幂等与多来源并存：授予按来源（{@code sourcePlugin}）相互独立——你、其他插件、站长后台
     * 可以各自给同一用户发同一装饰，互不吞并；用户<b>持有 = 任一来源存在有效授予</b>。你只需
     * 管好自己的发与撤，无需关心他人是否发放过。同一来源对同一用户同一装饰同时至多一条有效授予，
     * 可安全重复调用：已持有你发的授予时，本次 {@code expiresAt} 比现有<b>更晚</b>（或升格永久）
     * 则延长有效期并返回 {@link GrantResult.Status#RENEWED}——<b>只延长不缩短</b>（取更晚值，
     * 乱序重试安全；要缩短 / 收回请走 {@link #revoke}），不满足则返回
     * {@link GrantResult.Status#ALREADY_HELD}。持续型装饰以此实现「短时效 + 周期重发续期」。
     *
     * <p>装饰由 {@code decorationName}（装饰 metadata.name）指定；其所属稀有度须允许外部发放，
     * 否则返回 {@link GrantResult.Status#DECORATION_NOT_EXTERNALLY_GRANTABLE}。
     *
     * @param request 发放请求
     * @return 发放结果
     */
    Mono<GrantResult> grant(GrantRequest request);

    /**
     * 撤销装饰。
     *
     * <p>仅能撤销由相同 {@code sourcePlugin} 发放、且当前有效的那一条：你没有可撤的有效授予时，
     * 若用户仍持有他源（其他插件 / 后台）的有效授予返回
     * {@link RevokeResult.Status#FORBIDDEN_NOT_OWNER}（不可越权撤他人的），全无则返回
     * {@link RevokeResult.Status#NOT_HELD}。多来源并存下，撤销自己的一条后用户是否失去装扮，
     * 取决于是否还有其他来源的有效授予（持有 = 并集）。
     *
     * @param request 撤销请求
     * @return 撤销结果
     */
    Mono<RevokeResult> revoke(RevokeRequest request);
}
