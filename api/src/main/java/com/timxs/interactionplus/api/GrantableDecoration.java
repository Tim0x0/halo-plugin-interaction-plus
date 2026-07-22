package com.timxs.interactionplus.api;

/**
 * 可外部发放的装饰（{@link DecorationGrantApi#listGrantable} 的返回项）。
 *
 * <p>自包含展示信息，供外部插件做可视化选择 / 分组，无需二次查询。
 *
 * @param name              装饰标识（metadata.name）；发放时作为 {@link GrantRequest#decorationName} 回传
 * @param displayName       显示名称
 * @param type              类型：badge / avatar_frame / title / card_background / name_style
 * @param rarityName        稀有度标识（可空）
 * @param rarityDisplayName 稀有度显示名（可空；已聚合，免外部二次查询）
 * @param categoryName      分类标识（可空）
 */
public record GrantableDecoration(
    String name,
    String displayName,
    String type,
    String rarityName,
    String rarityDisplayName,
    String categoryName
) {
}
