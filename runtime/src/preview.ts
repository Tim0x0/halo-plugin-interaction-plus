// 后台预览入口：把模板喂给同一个渲染引擎，渲染在一个独立 iframe 里
import { BUILTIN_TEMPLATES } from './builtin'
import {
  resolveProfileUrl,
  skeletonData,
  toHipData,
  type HipComponent,
  type HipData,
} from './hip-data'
import type { PublicIdentity } from './identity'
import { renderTemplate, runCleanups, type TemplateSource } from './template-engine'
import { getTemplate } from './template-source'

export interface PreviewOptions {
  /** 组件槽位；数组则同框横排（如 `['avatar', 'identity']`）。 */
  component: HipComponent | HipComponent[]
  /** 草稿 HTML；缺省取当前生效模板。只有自定义组件页传草稿。 */
  html?: string
  /** 草稿 CSS；与 `html` 同进同出。 */
  css?: string
  /** 样本身份数据；缺省时渲染骨架态 */
  data?: PublicIdentity | null
  /** 打到宿主上的属性，用于场景分档预览（:host([scene="comment"]) 照常命中） */
  attrs?: Record<string, string>
  /** 预览暗色形态 */
  dark?: boolean
  /**
   * 预览视口宽度（px）= 模板眼里的 `window.innerWidth`。
   * 桌面形态须 > 640；缺省铺满容器（`fit` 取 {@link FIT_VIEWPORT_WIDTH}）。
   */
  width?: number
  /** 容器装不下时只缩视觉（`transform: scale`），不改 iframe 内 CSS 像素。 */
  fit?: boolean
  /** 用户卡自动展开并禁用指针（`pointer-events: none`，不影响合成 click）。 */
  locked?: boolean
}

/** 铺底文档四周的留白，`measureContent` 量占地时要按它补右 / 下边。 */
const FRAME_PADDING = 24

/** iframe 铺底：16px sans-serif。背景铺在 html，body 不加 min-height（否则高度只能涨）。 */
const FRAME_HTML = `<!DOCTYPE html><html><head><meta charset="utf-8"><style>
html { height: 100%; }
body {
  margin: 0;
  padding: ${FRAME_PADDING}px;
  box-sizing: border-box;
  font: 16px/1.6 ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
}
</style></head><body data-hip-ready="1"></body></html>`

/** 预览视口最小高度。用户卡是浮层、不计入 scrollHeight，只能靠档位撑开。 */
const FRAME_HEIGHT: Record<HipComponent, number> = {
  identity: 120,
  avatar: 160,
  card: 400,
}

/** 须与卡片模板 `@media (max-width: 640px)` 一致；桌面态也是 fixed，只能按视口宽判定。 */
const NARROW_BREAKPOINT = 640

/** `fit` 默认视口宽，须大于 {@link NARROW_BREAKPOINT}。 */
const FIT_VIEWPORT_WIDTH = 720

/** 组合预览间距（评论头部头像与昵称的常见留白）。 */
const INLINE_GAP = 12

/**
 * iframe 取得浏览上下文的等待上限。容器随 Teleport / 弹窗过渡接入文档要若干帧；
 * 超过这个时长仍取不到，说明容器不会再接入（调用方已卸载，或压根没挂进文档），
 * 抛错交由调用方展示，不空转。
 */
const FRAME_READY_TIMEOUT = 2000

/** 一次预览的资源。按容器索引：同屏可有多个预览，全局单例会顶掉先渲染那个的 cleanup。 */
interface PreviewSession {
  /** 缩放层，同时负责裁掉 `transform` 溢出的布局占位 */
  wrapper: HTMLElement | null
  frame: HTMLIFrameElement | null
  /** 上一次渲染的 ShadowRoot（组合预览有多个），用于摘掉模板登记的全局监听 */
  roots: ShadowRoot[]
  /** `fit` 模式下跟随容器宽度重算缩放 */
  observer: ResizeObserver | null
  /** `fit` 缩放基准：内容实际占地，不是 iframe 视口。 */
  contentLeft: number
  contentWidth: number
  frameHeight: number
  /**
   * 渲染代次。等待浏览上下文期间可能又发起新一轮渲染（防抖、watch 连发），
   * 旧的那轮恢复后必须自行作废：否则它登记的 roots 会顶掉新一轮登记的，
   * 那批 cleanup 就再也没人跑得到。
   */
  generation: number
}

const sessions = new WeakMap<HTMLElement, PreviewSession>()

function sessionOf(container: HTMLElement): PreviewSession {
  let session = sessions.get(container)
  if (!session) {
    session = {
      wrapper: null,
      frame: null,
      roots: [],
      observer: null,
      contentLeft: 0,
      contentWidth: 0,
      frameHeight: 0,
      generation: 0,
    }
    sessions.set(container, session)
  }
  return session
}

/**
 * 量内容占地（四周补铺底留白）。浮层不计 scrollWidth，只能量 ShadowRoot 子节点；
 * 零尺寸节点跳过。窄屏铺满视口，须由调用方按 {@link NARROW_BREAKPOINT} 提前拦掉。
 */
function measureContent(
  roots: ShadowRoot[],
): { left: number; width: number; height: number } | null {
  let left = Infinity
  let right = 0
  let bottom = 0
  for (const root of roots) {
    for (const child of Array.from(root.children)) {
      const rect = (child as HTMLElement).getBoundingClientRect()
      if (!rect.width || !rect.height) {
        continue
      }
      left = Math.min(left, rect.left - FRAME_PADDING)
      right = Math.max(right, rect.right + FRAME_PADDING)
      bottom = Math.max(bottom, rect.bottom + FRAME_PADDING)
    }
  }
  return right > left && left !== Infinity ? { left, width: right - left, height: bottom } : null
}

/**
 * 在容器内渲染一份模板。建普通 div + attachShadow，不复用已注册的 hip-*。
 * 套 iframe：模板脚本在 iframe realm 编译，外点关闭只在区内生效，@media 按 iframe 宽判定。
 *
 * <p>返回的 Promise 在渲染真正完成后 resolve；容器一直取不到浏览上下文则 reject。
 * 调用方 await 一次即可，不必自己按帧补渲染。
 */
export async function renderPreview(
  container: HTMLElement,
  options: PreviewOptions,
): Promise<void> {
  const session = sessionOf(container)
  const generation = ++session.generation
  disposePreview(container)
  const { frame, doc, view } = await ensureFrameReady(container, session)
  // 等待期间被更新的一轮渲染顶掉，此处收手，DOM 与 roots 都交给那一轮
  if (session.generation !== generation) {
    return
  }
  const components = Array.isArray(options.component) ? options.component : [options.component]
  const viewportWidth = options.width || (options.fit ? FIT_VIEWPORT_WIDTH : 0)
  frame.style.width = viewportWidth ? `${viewportWidth}px` : '100%'
  frame.style.pointerEvents = options.locked ? 'none' : ''
  // 背景铺在 html（见 FRAME_HTML），文字色给 body —— 暗色下继承色的组件才有正确对比
  doc.documentElement.style.background = options.dark ? '#0d1117' : '#ffffff'
  doc.body.style.color = options.dark ? '#e6edf3' : '#1f2328'
  layoutBody(doc.body, components)
  doc.body.innerHTML = ''

  const attrs = options.attrs || {}
  // 草稿只在单组件预览时成立：自定义组件页一次编辑一份模板，组合预览取的一律是生效模板
  const draft =
    options.html !== undefined && components.length === 1
      ? { html: options.html, css: options.css || '' }
      : null
  const roots: ShadowRoot[] = []
  const centered = !components.includes('card')
  for (const component of components) {
    const host = doc.createElement('div')
    // 居中 flex 默认会收缩宿主；禁缩后超宽才能如实量到，`fit` 才缩得对
    if (centered) {
      host.style.flexShrink = '0'
    }
    for (const [name, value] of Object.entries(attrs)) {
      host.setAttribute(name, value)
    }
    if (options.dark) {
      host.setAttribute('data-hip-dark', '')
    }
    doc.body.appendChild(host)

    const root = host.attachShadow({ mode: 'open' })
    roots.push(root)
    renderTemplate({
      root,
      host,
      data: buildPreviewData(options, attrs, component),
      template: resolveTemplate(component, draft),
      component,
      realm: view,
    })
    if (component === 'card') {
      // locked 只看卡面。必须藏在 openCard 之前：坐标按触发器算死，后藏会留一整行空白
      if (options.locked) {
        hideCardTrigger(root)
      }
      openCard(root)
    }
  }
  session.roots = roots

  // 窄屏不量（铺满视口）。判据取 iframe 自己的 innerWidth，只看 options.width 会漏判
  const narrow = (view.innerWidth || viewportWidth) <= NARROW_BREAKPOINT
  const measured = narrow ? null : measureContent(roots)
  const floor = Math.max(...components.map((component) => FRAME_HEIGHT[component] || 160))
  // 高度：量得到就按实际占地收紧（浮层不计入 scrollHeight，只能靠量），
  // 量不到（窄屏形态铺满视口）退回档位；两条都不低于 scrollHeight
  session.frameHeight = Math.max(measured?.height ?? floor, doc.body.scrollHeight)
  session.contentLeft = measured?.left ?? 0
  session.contentWidth = measured?.width ?? (viewportWidth || container.clientWidth)
  frame.style.height = `${session.frameHeight}px`
  applyFit(container, session, options.fit === true)
}

/** 渲染要用的 iframe 三件套。 */
interface FrameContext {
  frame: HTMLIFrameElement
  doc: Document
  view: Window & typeof globalThis
}

/** 等一帧。容器未接入文档时 iframe 没有浏览上下文，只能按帧回探。 */
function nextFrame(): Promise<void> {
  return new Promise((resolve) => {
    requestAnimationFrame(() => resolve())
  })
}

/**
 * 建好 wrapper + iframe 并等到浏览上下文可用。
 * 容器刚随 Teleport / 弹窗内容区接入文档时 contentDocument 仍可能为 null，
 * 等待统一收在这里：iframe 就绪与否是本模块的实现细节，不该由每个调用方各写一套重试。
 */
async function ensureFrameReady(
  container: HTMLElement,
  session: PreviewSession,
): Promise<FrameContext> {
  const deadline = Date.now() + FRAME_READY_TIMEOUT
  for (;;) {
    ensureFrame(container, session)
    const frame = session.frame
    const doc = frame?.contentDocument
    // 取 defaultView 而不是 contentWindow：只有它的类型带 typeof globalThis，
    // 模板脚本要用其中的 Function 构造器跨 realm 编译
    const view = doc?.defaultView
    if (frame && doc && view) {
      return { frame, doc, view }
    }
    if (Date.now() >= deadline) {
      throw new Error('预览 iframe 未能取得浏览上下文，容器可能尚未接入文档')
    }
    await nextFrame()
  }
}

/** 行内组件居中横排；用户卡贴左上。min-height 用固定 px，百分比会与自适应高度互锁。 */
function layoutBody(body: HTMLElement, components: HipComponent[]): void {
  const centered = !components.includes('card')
  const floor = Math.max(...components.map((component) => FRAME_HEIGHT[component] || 160))
  body.style.display = centered ? 'flex' : ''
  body.style.alignItems = centered ? 'center' : ''
  body.style.justifyContent = centered ? 'center' : ''
  body.style.gap = centered ? `${INLINE_GAP}px` : ''
  body.style.minHeight = centered ? `${floor}px` : ''
}

/** 给了草稿用草稿，否则取当前生效模板（回落链与 base.ts 一致）。 */
function resolveTemplate(component: HipComponent, draft: TemplateSource | null): TemplateSource {
  if (draft) {
    return draft
  }
  const template = getTemplate(component) || BUILTIN_TEMPLATES[component]
  if (!template?.html) {
    // 走到这里只有一种可能：component 不是 identity / avatar / card。
    // 明写出来，否则调用方看到的是模板引擎深处的 "undefined.replace"，无从定位
    throw new Error(`取不到 ${component} 的模板，组件名只能是 identity / avatar / card 之一`)
  }
  return template
}

/** 复用容器内的 wrapper + iframe；body 上无标记则重新铺底。 */
function ensureFrame(container: HTMLElement, session: PreviewSession): void {
  // 元素引用随 session 走，但容器可能在 Vue 重建 DOM 后换了内容，所以要校验还在不在
  if (session.wrapper && !session.wrapper.isConnected) {
    session.wrapper = null
    session.frame = null
  }
  if (!session.wrapper) {
    const wrapper = document.createElement('div')
    // overflow 裁掉缩放后 iframe 在布局上多出来的那部分（transform 不改变布局占位）
    wrapper.style.cssText = 'overflow:hidden;width:100%'
    container.appendChild(wrapper)
    session.wrapper = wrapper
  }
  if (!session.frame) {
    const frame = document.createElement('iframe')
    frame.title = '模板预览'
    frame.style.cssText = 'display:block;border:0;width:100%;margin:0 auto;transform-origin:top left'
    session.wrapper.appendChild(frame)
    session.frame = frame
  }
  const doc = session.frame.contentDocument
  if (doc && doc.body?.dataset.hipReady !== '1') {
    doc.open()
    doc.write(FRAME_HTML)
    doc.close()
  }
}

/** 按容器宽度等比缩放；wrapper 高按视觉高度写死（transform 不改布局占位）。 */
function applyFit(container: HTMLElement, session: PreviewSession, fit: boolean): void {
  const { wrapper, frame } = session
  if (!wrapper || !frame) {
    return
  }
  if (!fit) {
    wrapper.style.height = ''
    frame.style.transform = ''
    frame.style.margin = '0 auto'
    return
  }
  // 观察器必须在宽度检查之前挂：弹窗过渡期间容器宽度是 0，若那时先 return，
  // 就再也没有东西会在宽度就绪后触发重算，预览会一直停在未缩放的状态
  if (!session.observer) {
    // 容器宽度会变：弹窗开合动画、窗口缩放、面板折叠。
    // 只认宽度变化 —— 本函数会写 wrapper 的高度，而那会改变容器自身的高度、
    // 再次触发观察器；虽然重算是幂等的（收敛而非死循环），但没必要每次渲染都多跑一轮
    let lastWidth = -1
    session.observer = new ResizeObserver(() => {
      const width = container.clientWidth
      if (width === lastWidth) {
        return
      }
      lastWidth = width
      applyFit(container, session, true)
    })
    session.observer.observe(container)
  }
  // 容器尚无宽度（弹窗过渡 / display:none）时不写入，留着上一次的有效值等下一次回调
  const available = container.clientWidth
  if (!available || !session.contentWidth) {
    return
  }
  const scale = Math.min(1, available / session.contentWidth)
  // 内容在 iframe 里不一定贴左（行内组件居中摆放），偏移要减掉它自己的左边界 ——
  // iframe 以左上角为原点缩放，内容左边界缩放后落在 contentLeft * scale。
  // 结果可能为负：那说明左边有一截空白该被推到容器外，由 wrapper 的 overflow 裁掉
  const offset = (available - session.contentWidth * scale) / 2 - session.contentLeft * scale
  frame.style.transform = scale < 1 ? `scale(${scale})` : ''
  frame.style.margin = `0 0 0 ${offset}px`
  wrapper.style.height = `${session.frameHeight * scale}px`
}

/** 向 `<slot>` 派发一次 click 展开用户卡。找不到 slot 就警告，不猜。 */
function openCard(root: ShadowRoot): void {
  const slot = root.querySelector<HTMLElement>('slot')
  if (!slot) {
    console.warn(
      '[interaction-plus] 用户卡模板里找不到 <slot>，预览无法自动展开；' +
        '前台的后果更严重 —— 主题放在标签中间的头像 / 昵称会整个不显示',
    )
    return
  }
  const view = slot.ownerDocument.defaultView || window
  slot.dispatchEvent(new view.MouseEvent('click', { bubbles: true }))
}

/** 藏触发器只留卡面。先认 `.trigger`，没有就藏 `<slot>`，不向上藏父节点。 */
function hideCardTrigger(root: ShadowRoot): void {
  const trigger = root.querySelector<HTMLElement>('.trigger')
  if (trigger) {
    trigger.style.display = 'none'
    return
  }
  const slot = root.querySelector<HTMLElement>('slot')
  if (slot) {
    slot.style.display = 'none'
  }
}

/**
 * 摘掉某个预览容器登记的全局监听与观察器。
 * 每次渲染前自动调用；消费端组件卸载时也必须调用。
 */
export function disposePreview(container: HTMLElement): void {
  const session = sessions.get(container)
  if (!session) {
    return
  }
  for (const root of session.roots) {
    runCleanups(root)
  }
  session.roots = []
  if (session.observer) {
    session.observer.disconnect()
    session.observer = null
  }
}

function buildPreviewData(
  options: PreviewOptions,
  attrs: Record<string, string>,
  component: HipComponent,
): HipData {
  const identity = options.data
  if (!identity) {
    return skeletonData(attrs['user-name'] || '', attrs)
  }
  const fallbackName = identity.userName || attrs['user-name'] || ''
  // 与前台同一个裁剪函数：预览里看到几个标识，前台就是几个
  return toHipData(identity, attrs, resolveProfileUrl(identity, fallbackName), component)
}
