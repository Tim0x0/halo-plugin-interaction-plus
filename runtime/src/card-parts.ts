// 共享 DOM 片段：一律返回 DocumentFragment，createElement + textContent，不拼 HTML。
// 对外类名 / 结构契约见 hip-helper.ts。数量裁剪在 HipElement.buildData()。
import { nameStyleCss, safeHexColor, type DecorationVo, type PublicIdentity } from './identity'

/** 图片加载失败即隐藏自己：与「素材被删 / 外链挂了」的降级口径一致。 */
function hideOnError(image: HTMLImageElement): void {
  image.addEventListener('error', () => {
    image.style.display = 'none'
  })
}

/** 图片加载失败时隐藏自己、露出并排的回落节点（标识图标 / 称号图共用）。 */
function fallbackOnError(image: HTMLImageElement, fallback: HTMLElement): void {
  image.addEventListener('error', () => {
    image.style.display = 'none'
    fallback.style.display = ''
  })
}

function createImage(className: string, alt = ''): HTMLImageElement {
  const image = document.createElement('img')
  image.className = className
  image.alt = alt
  // loading / error 必须在赋 src 之前就位：缓存命中的失败图可能同步触发 error
  image.loading = 'lazy'
  return image
}

/**
 * 无头像色（Flarum 式算法）：显示名各字符 code 求和 % 360 作 HSV 色相，S=0.3 V=0.9。
 * 输出补零的 #rrggbb；补零防止小值拼出非法 5 位色。
 */
function nameHashColor(name: string): string {
  let sum = 0
  for (let i = 0; i < name.length; i++) {
    sum += name.charCodeAt(i)
  }
  const h = (sum % 360) / 360
  const s = 0.3
  const v = 0.9
  const sector = Math.floor(h * 6)
  const f = h * 6 - sector
  const p = v * (1 - s)
  const q = v * (1 - f * s)
  const t = v * (1 - (1 - f) * s)
  let r = 0
  let g = 0
  let b = 0
  switch (sector % 6) {
    case 0:
      r = v
      g = t
      b = p
      break
    case 1:
      r = q
      g = v
      b = p
      break
    case 2:
      r = p
      g = v
      b = t
      break
    case 3:
      r = p
      g = q
      b = v
      break
    case 4:
      r = t
      g = p
      b = v
      break
    default:
      r = v
      g = p
      b = q
      break
  }
  const hex = (n: number) => Math.floor(n * 255).toString(16).padStart(2, '0')
  return `#${hex(r)}${hex(g)}${hex(b)}`
}

/**
 * 头像 + 头像框片段（首字母占位常驻兜底）。
 *
 * <p>首字母占位常驻、img 叠放其上：加载失败隐藏 img 后自然露出占位，
 * 与 avatar 缺失路径共用同一兜底（对齐官方 VAvatar「失败 = 缺失」的降级）。
 * 占位样子由 {@code display.avatarFallbackStyle} 决定：{@code halo} 走模板 CSS 灰底；
 * {@code hash} 按显示名内联着色（每个用户不同，静态样式表表达不了）。不改 avatar 字段。
 */
export function avatarNode(
  identity: PublicIdentity | null,
  fallbackName: string,
): DocumentFragment {
  const frag = document.createDocumentFragment()
  const displayName = identity?.displayName || fallbackName

  const fallback = document.createElement('span')
  fallback.className = 'avatar avatar--fallback'
  fallback.textContent = displayName.charAt(0).toUpperCase()
  if (identity?.display?.avatarFallbackStyle === 'hash') {
    fallback.style.backgroundColor = nameHashColor(displayName)
    fallback.style.color = '#fff'
  }
  frag.appendChild(fallback)

  const avatar = identity?.avatar
  if (avatar) {
    const image = createImage('avatar', displayName)
    hideOnError(image)
    image.src = avatar
    frag.appendChild(image)
  }

  const frameUrl = identity?.decorations?.avatarFrame?.url
  if (frameUrl) {
    const frame = createImage('frame')
    hideOnError(frame)
    frame.src = frameUrl
    frag.appendChild(frame)
  }
  return frag
}

/**
 * 身份标识片段。有图标只渲染图标（hover 出名称 tooltip），无图标渲染文字牌；
 * 图标加载失败时切换到并排的隐藏文字牌，标识不消失。
 */
export function identityMarksNode(identity: PublicIdentity): DocumentFragment {
  const frag = document.createDocumentFragment()
  for (const mark of identity.identityMarks || []) {
    const name = mark.displayName || ''
    if (mark.icon) {
      const image = createImage('mark-icon', name)
      image.title = name
      // 回落牌无色：形态互斥契约下 icon 与 color 不并存（后端读出口已归一）
      const fallback = document.createElement('span')
      fallback.className = 'mark'
      fallback.textContent = name
      fallback.style.display = 'none'
      fallbackOnError(image, fallback)
      image.src = mark.icon
      frag.append(image, fallback)
      continue
    }
    const chip = document.createElement('span')
    chip.className = 'mark'
    chip.textContent = name
    const color = safeHexColor(mark.color)
    if (color) {
      chip.style.color = color
      chip.style.borderColor = color
    }
    frag.appendChild(chip)
  }
  return frag
}

/**
 * 称号片段。恒有文字牌；配了图则整图 + 并排隐藏文字牌（裂图回落）。
 * 行内 / 卡片怎么用由模板决定。文字牌的样式规则见 {@link textTitleNode}（含双实现同步清单）。
 */
export function titleNode(identity: PublicIdentity): DocumentFragment {
  const frag = document.createDocumentFragment()
  const title = identity.decorations?.title
  if (!title || !title.titleText) {
    return frag
  }
  if (title.url) {
    const image = createImage('title-img', title.titleText)
    image.title = title.titleText
    const fallback = textTitleNode(title)
    fallback.style.display = 'none'
    fallbackOnError(image, fallback)
    image.src = title.url
    frag.append(image, fallback)
    return frag
  }
  frag.appendChild(textTitleNode(title))
  return frag
}

/**
 * 称号文字牌。
 *
 * ⚠ 双实现同步清单：本函数与 Console 缩略图的 `ui/src/utils/decoration.ts` 的
 * `titleCss`（对象形式）是同一规则的两份实现（不同构建产物、刻意不共包）。
 * 改**hex 白名单、渐变角度（135deg）、双色/单色分支**时必须两处同改，
 * 否则后台缩略图与前台效果不一致 —— 不报错、只是站长看到的与访客不同。
 */
function textTitleNode(title: DecorationVo): HTMLElement {
  const chip = document.createElement('span')
  chip.className = 'title'
  chip.textContent = title.titleText || ''
  const color = safeHexColor(title.titleColor)
  const background = safeHexColor(title.titleBackground)
  const backgroundSecondary = safeHexColor(title.titleBackgroundSecondary)
  if (color) {
    chip.style.color = color
  }
  if (background && backgroundSecondary) {
    // 双背景色渲染线性渐变
    chip.style.background = `linear-gradient(135deg,${background},${backgroundSecondary})`
  } else if (background) {
    chip.style.background = background
  }
  return chip
}

/**
 * 昵称片段（已应用昵称样式）。
 *
 * <p>渐变昵称需要一组互相配合的声明（背景裁剪到文字 + 文字填充透明），
 * 单独设某一条都是坏的，所以整组一次写入 cssText；内容全部来自 hex 白名单过滤后的颜色。
 */
export function nameNode(identity: PublicIdentity): DocumentFragment {
  const frag = document.createDocumentFragment()
  const name = document.createElement('span')
  name.className = 'name'
  name.textContent = identity.displayName || identity.userName || ''
  const css = nameStyleCss(identity.decorations?.nameStyle?.nameStyle)
  if (css) {
    name.style.cssText = css
  }
  frag.appendChild(name)
  return frag
}

/**
 * 主勋章片段。未佩戴时返回空片段。
 *
 * <p>「身份行是否显示主勋章」的开关不在这里判——数据交到模板时已经按组件裁过
 * （关闭时 `decorations.primaryBadge` 直接不存在，见 base.ts）。
 */
export function primaryBadgeNode(identity: PublicIdentity): DocumentFragment {
  const frag = document.createDocumentFragment()
  const badge = identity.decorations?.primaryBadge
  if (!badge?.url) {
    return frag
  }
  const name = badge.displayName || ''
  const image = createImage('badge', name)
  image.title = name
  hideOnError(image)
  image.src = badge.url
  frag.appendChild(image)
  return frag
}
