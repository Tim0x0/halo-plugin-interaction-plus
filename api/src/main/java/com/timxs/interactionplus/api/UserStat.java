package com.timxs.interactionplus.api;

/**
 * 单个用户统计贡献项（{@link UserStatContributor} 的返回项）。
 *
 * <p>三个字段均必填，超限或非法的项会被 interaction-plus 丢弃（不报错、不影响其他项）：
 *
 * <ul>
 *   <li>{@code key}：机器标识，插件内唯一；小写字母 / 数字 / 短横线，1-32 字符，
 *       须匹配 {@code ^[a-z0-9][a-z0-9-]{0,31}$}。消费方以「来源插件 id + key」为完整标识，
 *       不同插件的同名 key 互不冲突；</li>
 *   <li>{@code label}：展示名（如「采纳」「获赏」），1-16 字符；</li>
 *   <li>{@code value}：展示值文本（如 {@code "23"}、{@code "1.2k"}、{@code "Lv.16"}），
 *       1-32 字符。格式化由贡献方决定，interaction-plus 原样展示。</li>
 * </ul>
 *
 * @param key   机器标识（插件内唯一）
 * @param label 展示名
 * @param value 展示值文本
 * @author Tim0x0
 */
public record UserStat(
    String key,
    String label,
    String value
) {
}
