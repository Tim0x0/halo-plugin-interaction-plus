// 共享 HTML 片段构建：头像 / 昵称 / 身份标识 / 称号
import {
  escapeHtml,
  nameStyleCss,
  safeHexColor,
  type DecorationVo,
  type PublicIdentity,
} from './identity'

export { escapeHtml, escapeCssUrl } from './identity'

/**
 * 头像 + 头像框 HTML 片段（hip-user-avatar 与 hip-user-card 共用）。
 * 首字母占位常驻兜底（头像缺失或加载失败时露出）；类名固定
 * avatar / avatar--fallback / frame，配套样式 {@link AVATAR_FRAME_CSS}
 * 与 {@link AVATAR_IMG_CSS}。
 */
export function avatarHtml(identity: PublicIdentity | null, fallbackName: string): string {
  const displayName = identity?.displayName || fallbackName
  const avatar = identity?.avatar
  // 首字母占位常驻，img 叠放其上：加载失败 onerror 隐藏 img 后自然露出占位，
  // 与 avatar 缺失路径共用同一兜底（对齐官方 VAvatar「失败 = 缺失」的降级）
  const fallback = `<span class="avatar avatar--fallback">${escapeHtml(displayName.charAt(0).toUpperCase())}</span>`
  const img = avatar
    ? `<img class="avatar" src="${escapeHtml(avatar)}" alt="${escapeHtml(displayName)}" loading="lazy" onerror="this.style.display='none'" />`
    : ''
  const frameUrl = identity?.decorations?.avatarFrame?.url
  const frame = frameUrl
    ? `<img class="frame" src="${escapeHtml(frameUrl)}" alt="" loading="lazy" onerror="this.style.display='none'" />`
    : ''
  return fallback + img + frame
}

/**
 * Shadow DOM 盒模型基线（所有 hip-* 组件样式首行引入）。
 * 宿主页的 reset/preflight（如 Tailwind 的 * { box-sizing: border-box }）穿不进
 * shadow root，box-sizing 又是非继承属性——组件内若不自声明，一律回落浏览器
 * 默认 content-box，而本插件全部「定高 + padding」布局（卡片 surface 236 等）
 * 与 Console 预览（跑在带 preflight 的 Console 页里）都以 border-box 为前提。
 * 规约「Shadow DOM 布局关键属性必须显式声明」的第二例（第一例是 display）。
 */
export const BOX_SIZING_CSS = `
        *,
        *::before,
        *::after {
          box-sizing: border-box;
        }
`

/**
 * 头像框覆盖层样式。124% 为实测最佳的框-头像视觉比例，
 * 前提是素材图案画满画布（上传提示已约定）。runtime 侧仅此一处定义；
 * ⚠ Console 预览（ui/src/components/DecorationPreview.vue 的
 * .hip-preview__frame）持有同值的另一份实现，改比例时两处必须同改。
 */
export const AVATAR_FRAME_CSS = `
        .frame {
          position: absolute;
          left: 50%;
          top: 50%;
          width: 124%;
          height: 124%;
          max-width: none;
          max-height: none;
          transform: translate(-50%, -50%);
          object-fit: contain;
          pointer-events: none;
        }
`

/**
 * 头像 img 叠放层样式（与 AVATAR_FRAME_CSS 配套引入）：img 绝对定位
 * 覆盖常驻的首字母占位；背景色与占位同色，避免加载中或透明头像
 * 透出底下的字母。
 */
export const AVATAR_IMG_CSS = `
        img.avatar {
          position: absolute;
          inset: 0;
          background: #e5e7eb;
        }
`

/**
 * 身份标识 HTML 片段。有图标只渲染图标（hover 出名称 tooltip），无图标渲染文字牌；
 * 图标加载失败时 onerror 切换到并排的隐藏文字牌，标识不消失。
 */
export function identityMarksHtml(identity: PublicIdentity, limit: number): string {
  const marks = (identity.identityMarks || []).slice(0, Math.max(0, limit))
  return marks
    .map((mark) => {
      const name = escapeHtml(mark.displayName)
      const color = safeHexColor(mark.color)
      const colorStyle = color ? `border-color:${color};color:${color}` : ''
      if (mark.icon) {
        const fallback = `<span class="mark" style="${colorStyle ? `${colorStyle};` : ''}display:none">${name}</span>`
        return (
          `<img class="mark-icon" src="${escapeHtml(mark.icon)}" alt="${name}" title="${name}" loading="lazy"` +
          ` onerror="this.style.display='none';this.nextElementSibling.style.display=''" />${fallback}`
        )
      }
      return `<span class="mark"${colorStyle ? ` style="${colorStyle}"` : ''}>${name}</span>`
    })
    .join('')
}

/**
 * 称号 HTML 片段。text 形态渲染文字牌；image 形态渲染整图，
 * 图片加载失败时 onerror 切换到并排的隐藏文字牌（对齐 identityMarksHtml 的回落范式）。
 *
 * ⚠ 双实现同步清单：称号样式规则（text/image 形态分支、双背景 135deg 渐变 /
 * 单背景 / 文字色、hex 颜色白名单过滤）与 Console 预览的
 * `ui/src/utils/decoration.ts` 的 `titleCss` 是同一规则的两份实现，改规则时必须两处同改。
 */
export function titleHtml(identity: PublicIdentity): string {
  const title = identity.decorations?.title
  if (!title || !title.titleText) {
    return ''
  }
  if (title.titleMode === 'image' && title.url) {
    const name = escapeHtml(title.titleText)
    return (
      `<img class="title-img" src="${escapeHtml(title.url)}" alt="${name}" title="${name}" loading="lazy"` +
      ` onerror="this.style.display='none';this.nextElementSibling.style.display=''" />` +
      textTitleHtml(title, true)
    )
  }
  return textTitleHtml(title, false)
}

/** 文字牌称号；hidden=true 渲染为整图形态的并排回落牌（默认隐藏，图挂时由 onerror 显出）。 */
function textTitleHtml(title: DecorationVo, hidden: boolean): string {
  const titleColor = safeHexColor(title.titleColor)
  const background = safeHexColor(title.titleBackground)
  const backgroundSecondary = safeHexColor(title.titleBackgroundSecondary)
  const styles: string[] = []
  if (titleColor) {
    styles.push(`color:${titleColor}`)
  }
  if (background && backgroundSecondary) {
    // 双背景色渲染线性渐变
    styles.push(`background:linear-gradient(135deg,${background},${backgroundSecondary})`)
  } else if (background) {
    styles.push(`background:${background}`)
  }
  if (hidden) {
    styles.push('display:none')
  }
  const style = styles.length ? ` style="${styles.join(';')}"` : ''
  return `<span class="title"${style}>${escapeHtml(title.titleText)}</span>`
}

/**
 * 昵称 HTML 片段（应用昵称样式）。
 */
export function nameHtml(identity: PublicIdentity): string {
  const css = nameStyleCss(identity.decorations?.nameStyle?.nameStyle)
  const style = css ? ` style="${css}"` : ''
  return `<span class="name"${style}>${escapeHtml(identity.displayName)}</span>`
}
