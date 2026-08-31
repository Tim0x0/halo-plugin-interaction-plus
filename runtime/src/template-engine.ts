// 模板渲染引擎：占位符替换 + <script> 执行 + 清理登记 + 错误呈现
// 内置默认模板与站长自定义模板走同一条路径，后台预览也调这里（renderPreview）。
import type { HipData } from './hip-data'
import { hipHelper, type HipHelper } from './hip-helper'
import { escapeHtml } from './identity'

/** 一份模板 = HTML 框 + CSS 框。 */
export interface TemplateSource {
  html: string
  css: string
}

/**
 * 占位符可读的顶层字段白名单（= PublicIdentity 契约 + 三个派生字段）。
 *
 * <p>使用静态清单而不是运行时 {@code hasOwnProperty}：第三方直接传入的 data
 * 可以省略 `stats` 等字段，按运行时对象判会把
 * `{{stats.posts}}` 误报成「拼错了」——而它其实只是这个用户没有统计数据。
 */
const ROOT_KEYS = new Set([
  'userName',
  'displayName',
  'avatar',
  'bio',
  'registeredAt',
  'identityMarks',
  'decorations',
  'stats',
  'display',
  'profileUrl',
  'attrs',
  'loaded',
])

/**
 * 占位符：`{{路径}}`，取一个数据并**自动 HTML 转义**。
 *
 * <p>第二段起放开 `-` 与数字，是为了 `{{attrs.user-name}}`（标签属性名带连字符）
 * 与 `{{identityMarks.0.displayName}}`（数组下标）。
 */
const PLACEHOLDER_PATTERN = /\{\{\s*([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_-]+)*)\s*\}\}/g

const SCRIPT_PATTERN = /<script\b[^>]*>([\s\S]*?)<\/script\s*>/gi

/**
 * 拆出 `<script>` 文本并从 HTML 中移除。
 *
 * <p>必须在占位符替换**之前**做：脚本里的 `{{` 是合法 JS（嵌套块 / 对象字面量），
 * 当占位符替换掉会把代码改坏；而且 JS 本来就能直接读 `hipData`，不需要占位符。
 */
function extractScripts(html: string): { html: string; scripts: string[] } {
  const scripts: string[] = []
  const stripped = html.replace(SCRIPT_PATTERN, (_match, code: string) => {
    scripts.push(code)
    return ''
  })
  return { html: stripped, scripts }
}

/**
 * 按路径取值。顶层名不在白名单直接抛错，深层缺失返回空串。
 *
 * <p>两种口径不同是有意的：顶层名拼错（`{{dispalyName}}`）是**模板的错**，静默渲染成
 * 空白是最难自查的一类问题；而深层缺失（`{{decorations.title.titleText}}` 碰上没佩戴
 * 称号的用户）是**数据的正常形态**，报错会让模板对半数用户直接红屏。
 */
function resolvePath(data: HipData, path: string): unknown {
  const segments = path.split('.')
  const root = segments[0] as string
  if (!ROOT_KEYS.has(root)) {
    throw new Error(`未知占位符 {{${path}}}，可用顶层字段：${[...ROOT_KEYS].join(' / ')}`)
  }
  let current: unknown = data
  for (const segment of segments) {
    if (current === null || current === undefined) {
      return ''
    }
    current = (current as Record<string, unknown>)[segment]
  }
  return current
}

/** 取到的值转成可渲染文本。对象 / 数组没有合理的文本形态，一律空串。 */
function stringify(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  return ''
}

function substitute(html: string, data: HipData): string {
  return html.replace(PLACEHOLDER_PATTERN, (_match, path: string) =>
    escapeHtml(stringify(resolvePath(data, path))),
  )
}

export interface RenderOptions {
  root: ShadowRoot
  host: HTMLElement
  data: HipData
  template: TemplateSource
  /** 组件标签名，仅用于报错定位 */
  component: string
  /**
   * 编译模板脚本用的 realm，缺省为当前窗口。
   *
   * <p>预览传 iframe 的 `contentDocument.defaultView`：跨 realm 编译出的函数，函数体里的
   * `document` 指向那个 iframe 的文档 —— 于是模板挂的外点关闭、Esc 这类全局监听只在预览区
   * 内生效，点 Console 别处不会把刚展开的卡关掉。
   *
   * <p>类型带 `typeof globalThis` 是必须的：`Window` 接口本身不声明 `Function` 等全局
   * 构造器（`iframe.contentWindow` 正是这个类型），取 `defaultView` 才拿得到完整的那个。
   */
  realm?: Window & typeof globalThis
}

/** 模板 `<script>` 里可登记清理函数，引擎在下次渲染前与组件卸载时统一调用。 */
export type CleanupRegistrar = (fn: () => void) => void

type TemplateScript = (
  hipData: HipData,
  helper: HipHelper,
  root: ShadowRoot,
  host: HTMLElement,
  onCleanup: CleanupRegistrar,
) => void

/**
 * 已编译脚本缓存，按源码文本索引。
 *
 * <p>渲染是热路径：一次组件生命周期渲染两次（骨架 + 数据到达），一个评论页几十个
 * 身份行就是上百次渲染，而它们跑的是**同一段**内置默认模板脚本。不缓存就等于
 * 每次渲染都重新编译一遍。
 *
 * <p>条目数与组件数同级（内置默认 + 启动时拉到的自定义模板，注册表启动后只读），
 * 上限只是防御 —— 预览那条「每敲一个键就是一份新源码」的路径走 realm 编译、
 * 根本不进这张表。超限直接整表清空而非 LRU：只有几条，清了立刻重建。
 */
const COMPILE_CACHE_MAX = 32

const compiled = new Map<string, TemplateScript>()

// innerHTML 插进来的 <script> 浏览器不会执行，只能提取文本自己编译
function build(ctor: FunctionConstructor, code: string): TemplateScript {
  return new ctor('hipData', 'hipHelper', 'root', 'host', 'onCleanup', code) as TemplateScript
}

function compile(code: string, realm?: Window & typeof globalThis): TemplateScript {
  // 指定 realm（预览的 iframe）就地编译、不进缓存：跨 realm 的函数与本窗口的不是同一个
  // 东西，而 iframe 随时可能重建，缓存下来就是一堆指向已销毁 realm 的死函数
  if (realm) {
    return build(realm.Function, code)
  }
  let script = compiled.get(code)
  if (!script) {
    if (compiled.size >= COMPILE_CACHE_MAX) {
      compiled.clear()
    }
    script = build(Function, code)
    compiled.set(code, script)
  }
  return script
}

/**
 * 清理函数登记表。
 *
 * <p>ShadowRoot 内的 DOM 与其上的监听器随重渲染一并丢弃，不会累积；但模板挂到
 * `document` / `window` 上的监听（用户卡的外点关闭、Esc、窄屏滚动关闭）不会——
 * 而一次组件生命周期至少渲染两次（骨架 + 数据到达），不清理就是稳定泄漏。
 *
 * <p>WeakMap 按 ShadowRoot 索引：宿主被回收时表项自动消失，不需要额外注销。
 */
const cleanups = new WeakMap<ShadowRoot, Array<() => void>>()

/**
 * 跑掉并清空某个 ShadowRoot 上登记的清理函数。
 * 渲染前自动调用；组件 `disconnectedCallback` 也必须调用（见 base.ts）。
 */
export function runCleanups(root: ShadowRoot): void {
  const list = cleanups.get(root)
  if (!list) {
    return
  }
  cleanups.delete(root)
  for (const fn of list) {
    try {
      fn()
    } catch (error) {
      // 一个清理函数抛错不能连累其余的，否则后面的监听全都摘不掉
      console.error('[interaction-plus] 模板清理函数执行失败', error)
    }
  }
}

/**
 * 渲染一份模板到 ShadowRoot。
 * 内容出错出红字，不回落内置默认；拉取接口失败才静默用内置（见 template-source）。
 */
export function renderTemplate(options: RenderOptions): void {
  const { root, host, data, template, component, realm } = options
  // 上一次渲染登记的全局监听必须先摘掉，再重建 DOM
  runCleanups(root)
  try {
    const { html, scripts } = extractScripts(template.html)
    const style = template.css ? `<style>${template.css}</style>` : ''
    root.innerHTML = style + substitute(html, data)
    if (scripts.length) {
      const registered: Array<() => void> = []
      const onCleanup: CleanupRegistrar = (fn) => {
        if (typeof fn === 'function') {
          registered.push(fn)
        }
      }
      cleanups.set(root, registered)
      for (const code of scripts) {
        compile(code, realm)(data, hipHelper, root, host, onCleanup)
      }
    }
  } catch (error) {
    renderError(root, component, data.userName, error)
  }
}

/** 渲染失败提示：ShadowRoot 内一行红字 + console 完整堆栈。 */
function renderError(
  root: ShadowRoot,
  component: string,
  userName: string,
  error: unknown,
): void {
  const message = error instanceof Error ? error.message : String(error)
  console.error(
    `[interaction-plus] <${component}> 自定义模板渲染失败（用户：${userName || '-'}）`,
    error,
  )
  root.innerHTML =
    '<style>:host{display:inline-block}' +
    '.hip-template-error{color:#dc2626;font-size:12px;line-height:1.4}</style>' +
    `<span class="hip-template-error">[interaction-plus] 自定义模板渲染失败：${escapeHtml(message)}</span>`
}
