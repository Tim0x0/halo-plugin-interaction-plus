// 明暗感知：给每个 hip-* 宿主打 data-hip-dark，主题切换时实时更新
//
// Shadow DOM 选不到外部的 .dark，这是自定义模板感知明暗的唯一通道：
//   .name { color: #24292f; }
//   :host([data-hip-dark]) .name { color: #e9edf2; }
// 主题若自带 CSS 变量则优先用变量（可继承穿透 Shadow DOM，零额外代码）；
// 只跟随系统时直接在模板里写 @media (prefers-color-scheme: dark)，Shadow DOM 内天然可用。

/** 强制暗色的挂点：生态里三种主流写法。 */
const DARK_SELECTOR = '.dark, .color-scheme-dark, [data-color-scheme="dark"]'

/** 跟随系统的挂点：仅当系统偏好暗色时才算暗。 */
const AUTO_SELECTOR = '.color-scheme-auto, [data-color-scheme="auto"]'

/** 出现在 class / data-color-scheme 里就可能影响明暗判定的片段。 */
const THEME_TOKENS = ['dark', 'color-scheme']

const DARK_ATTRIBUTE = 'data-hip-dark'

const hosts = new Set<HTMLElement>()

let mediaQuery: MediaQueryList | null = null
let observer: MutationObserver | null = null

function prefersDark(): boolean {
  if (!mediaQuery) {
    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  }
  return mediaQuery.matches
}

/**
 * 判断单个宿主是否处于暗色。
 *
 * 沿组合树向上逐层判定：先 closest 查宿主所在树直到该树的根，仍未命中且身处
 * Shadow 树时跳到宿主元素继续向上——嵌在其它插件影子根里的组件（评论区整体渲染在
 * comment-widget 这类自定义元素的 Shadow DOM 内）只有这样才能看到 html / body 上的
 * 暗色挂点，closest 本身不跨影子边界。主题可能只在某个容器上挂暗色 class，
 * 按祖先判定才与「这一块是暗的」的视觉事实一致。
 */
function isDark(host: HTMLElement): boolean {
  let node: Element | null = host
  while (node) {
    if (node.closest(DARK_SELECTOR)) {
      return true
    }
    if (node.closest(AUTO_SELECTOR) && prefersDark()) {
      return true
    }
    const root = node.getRootNode()
    node = root instanceof ShadowRoot ? root.host : null
  }
  return false
}

/** 应用到单个宿主。渲染前调用，保证首帧 CSS 就能命中。 */
export function applyDarkAttribute(host: HTMLElement): void {
  host.toggleAttribute(DARK_ATTRIBUTE, isDark(host))
}

function refreshAll(): void {
  hosts.forEach(applyDarkAttribute)
}

function hasThemeToken(value: string | null): boolean {
  return !!value && THEME_TOKENS.some((token) => value.includes(token))
}

/**
 * 判断一批变更里是否真有可能影响明暗。
 *
 * 观察范围是整个文档子树，而主题里下拉、hover、动画都在频繁切 class——
 * 不加这道闸，评论页几十个组件会被无关变更反复触发 closest 扫描。
 * 变更「加上」暗色挂点时目标元素当场能选中；「移除」时得看 oldValue。
 */
function affectsColorScheme(records: MutationRecord[]): boolean {
  return records.some((record) => {
    const target = record.target as Element
    if (target.nodeType !== Node.ELEMENT_NODE) {
      return false
    }
    return (
      target.matches(DARK_SELECTOR) ||
      target.matches(AUTO_SELECTOR) ||
      hasThemeToken(record.oldValue)
    )
  })
}

function ensureWatching(): void {
  if (observer) {
    return
  }
  // 主题切换表现为祖先元素的 class / data-color-scheme 变化
  observer = new MutationObserver((records) => {
    if (affectsColorScheme(records)) {
      refreshAll()
    }
  })
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'data-color-scheme'],
    attributeOldValue: true,
    subtree: true,
  })
  const query = window.matchMedia('(prefers-color-scheme: dark)')
  mediaQuery = query
  query.addEventListener('change', refreshAll)
}

/** 无宿主消费时停掉全文档观察与媒体监听，下次有组件登记时由 ensureWatching 重建。 */
function stopWatching(): void {
  observer?.disconnect()
  observer = null
  mediaQuery?.removeEventListener('change', refreshAll)
  mediaQuery = null
}

/** 组件挂载时登记，并立即应用一次。 */
export function registerDarkHost(host: HTMLElement): void {
  ensureWatching()
  hosts.add(host)
  applyDarkAttribute(host)
}

/** 组件卸载时注销，避免集合无限增长；全部卸载后停止观察。 */
export function unregisterDarkHost(host: HTMLElement): void {
  hosts.delete(host)
  if (hosts.size === 0) {
    stopWatching()
  }
}
