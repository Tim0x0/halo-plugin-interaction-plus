package com.timxs.interactionplus.api;

import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Flux;

/**
 * 用户统计贡献扩展点：其他插件把自己领域内的用户统计项（如问答插件的「采纳数」、
 * 打赏插件的「获赏数」）贡献到 interaction-plus 的用户悬浮卡数据行与公开身份数据中。
 *
 * <p><b>数据所有权</b>：统计数据始终保存在贡献方自己的模型里，本扩展点只在
 * interaction-plus 聚合公开身份（缓存未命中）时被调用一次；返回结果随公开身份整体缓存
 * （站长可配 TTL，默认 30 秒），并经由 hip-* 组件、主题 Finder、公开 HTTP API 三个读出口
 * 同源下发。实现应保证<b>快速返回</b>（自行缓存慢查询）；单个贡献方有总超时预算，
 * 超时或出错时本次聚合丢弃该贡献方的全部项，不影响其他数据。
 *
 * <p><b>来源标识由系统盖章</b>：每个贡献项对外携带的来源插件 id 取自实现类对应
 * {@code ExtensionDefinition} 上由 Halo 自动打的 {@code plugin.halo.run/plugin-name}
 * label，贡献方无法自报或冒充。因此实现方<b>必须</b>在自身插件的资源 yaml 中声明
 * {@code ExtensionDefinition}（缺失时该实现的贡献项会被整体忽略）：
 *
 * <pre>{@code
 * apiVersion: plugin.halo.run/v1alpha1
 * kind: ExtensionDefinition
 * metadata:
 *   name: your-plugin-user-stat-contributor
 * spec:
 *   className: com.example.yourplugin.YourUserStatContributor
 *   extensionPointName: user-stat-contributor
 *   displayName: "你的插件的统计贡献"
 * }</pre>
 *
 * <p>同时以<b>可选依赖</b>声明本插件（缺席时不影响你的插件启动）：
 *
 * <pre>{@code
 * spec:
 *   pluginDependencies:
 *     "interaction-plus?": ">=0.1.0"
 * }</pre>
 *
 * <p><b>数量约束</b>：每个贡献方每用户至多贡献 5 项，超出部分截断；
 * 各字段格式约束见 {@link UserStat}。
 *
 * <p><b>稳定性</b>：随 {@code 0.1.x} 以<b>实验性</b>状态发布——契约已定型但尚未经大规模生产验证，
 * 可能随首批接入者的反馈微调；调整将保持前向兼容（只增不改）并同步到对接文档。
 *
 * @author Tim0x0
 */
public interface UserStatContributor extends ExtensionPoint {

    /**
     * 返回指定用户的统计贡献项。
     *
     * <p>无数据时返回空 {@code Flux} 即可（不要返回 error）；实现内部的查询失败
     * 建议自行降级为空流，避免消耗聚合侧的容错预算。
     *
     * @param userName 用户名（{@code metadata.name}）
     * @return 统计贡献项流（每用户至多取前 5 项）
     */
    Flux<UserStat> getUserStats(String userName);
}
