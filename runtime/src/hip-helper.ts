// 模板 JS 可用的工具函数（hipHelper）
import {
  avatarNode,
  identityMarksNode,
  nameNode,
  primaryBadgeNode,
  titleNode,
} from './card-parts'
import { escapeCssUrl, escapeHtml, safeHexColor, type PublicIdentity } from './identity'

/**
 * 注入模板 `<script>` 的 `hipHelper`。
 *
 * <p>render* 系列返回**插件内置渲染逻辑产出的 DOM 片段**（DocumentFragment，可能为空），
 * 直接 `append()` 即可，不必判空。返回节点而不是 HTML 字符串有三个用处：
 * 模板作者不必碰 `innerHTML`（零 XSS 风险）、片段可以在插入前后处理
 * （`frag.querySelectorAll('.mark').forEach(...)` 改类名 / 清内联色）、
 * 以及天然免疫转义遗漏。
 *
 * <p>⚠ 片段自带结构不自带样式：用的是内置类名（.avatar / .avatar--fallback / .frame /
 * .name / .mark / .mark-icon / .title / .title-img / .badge），样式得由 CSS 框里的
 * 对应规则提供。称号有底色时另带修饰类 .title--chip（裸文字无），供 CSS 区分
 * 「牌 / 裸文字」两种形态。
 *
 * <p>⚠ 签名与片段类名 / 结构属于对外模板契约。站长保存的自定义模板依赖这些名称与结构，
 * 因此不能随意修改上列类名，也不能拆掉「图 + 回落牌并排」或「首字母占位常驻叠放」。
 * 默认模板自己的容器类（.line / .wrapper / .plink / 用户卡布局类）不属于该契约。
 */
export interface HipHelper {
  /** HTML 转义。只在确实要拼 HTML 字符串时才需要；用 `textContent` / `append` 则不必。 */
  escape(text: string | undefined | null): string
  /** 转义 CSS `url()` 里的特殊字符，用于 `style.backgroundImage = 'url("' + ... + '")'`。 */
  escapeCssUrl(url: string): string
  /** hex 颜色白名单（3 / 6 / 8 位，8 位带透明度），非法返回空串。 */
  safeColor(value: string | undefined | null): string
  /** 昵称片段（已应用 nameStyle 渐变 / 纯色）。 */
  renderName(data: PublicIdentity): DocumentFragment
  /** 身份标识片段（数量已按当前组件的配置裁好）。 */
  renderMarks(data: PublicIdentity): DocumentFragment
  /** 称号片段（文字牌 + 可选图，加载失败回落文字牌）。 */
  renderTitle(data: PublicIdentity): DocumentFragment
  /** 主勋章片段（关闭或未佩戴时为空片段）。 */
  renderBadge(data: PublicIdentity): DocumentFragment
  /** 头像 + 头像框叠放片段（含无头像占位；风格由 display.avatarFallbackStyle 决定）。 */
  renderAvatar(data: PublicIdentity): DocumentFragment
}

export const hipHelper: HipHelper = {
  escape: escapeHtml,
  escapeCssUrl,
  safeColor: safeHexColor,
  renderName: nameNode,
  renderMarks: identityMarksNode,
  renderTitle: titleNode,
  renderBadge: primaryBadgeNode,
  renderAvatar: (data) => avatarNode(data, data.displayName || data.userName || ''),
}
