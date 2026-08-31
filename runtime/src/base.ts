import { BUILTIN_TEMPLATES } from './builtin'
import { registerDarkHost, unregisterDarkHost } from './dark-mode'
import {
  resolveProfileUrl,
  skeletonData,
  toHipData,
  type HipComponent,
  type HipData,
} from './hip-data'
import { fetchIdentity, parseData, type PublicIdentity } from './identity'
import { renderTemplate, runCleanups } from './template-engine'
import { getTemplate } from './template-source'

/**
 * hip-* 组件基类：
 * - 支持 user-name 自动请求与 data 直接渲染（data 优先）；
 * - Shadow DOM 样式隔离；
 * - API 失败时静默降级；
 * - 渲染只有一条路径：模板引擎。内置默认样子本身就是一份模板，
 *   与主题 `<template>`、后台自定义模板共用引擎。
 *
 * <p>属性只有三个：`user-name` / `data` / `scene`。`scene` 是纯 CSS 挂点
 * （`:host([scene="comment"])`）与 `hipData.attrs.scene`，插件不预定义值也不校验，
 * 因此不必进 observedAttributes —— 改它 CSS 自动重算，不需要重渲染。
 */
export abstract class HipElement extends HTMLElement {
  static get observedAttributes(): string[] {
    return ['user-name', 'data']
  }

  protected identity: PublicIdentity | null = null

  protected shadow: ShadowRoot

  private loadToken = 0

  constructor() {
    super()
    this.shadow = this.attachShadow({ mode: 'open' })
  }

  /** 组件标识，用于取模板与按场景裁剪数据。 */
  protected abstract get componentKey(): HipComponent

  connectedCallback(): void {
    // 先打 data-hip-dark 再渲染，保证首帧 :host([data-hip-dark]) 就能命中
    registerDarkHost(this)
    this.load()
  }

  disconnectedCallback(): void {
    unregisterDarkHost(this)
    // 模板挂在 document / window 上的监听（用户卡的外点、Esc、滚动关闭）在此摘除；
    // ShadowRoot 内部的监听随 DOM 一起走，不需要管
    runCleanups(this.shadow)
  }

  attributeChangedCallback(_name: string, oldValue: string | null, newValue: string | null): void {
    if (oldValue !== newValue && this.isConnected) {
      this.load()
    }
  }

  private load(): void {
    const token = ++this.loadToken
    const inline = parseData(this.getAttribute('data'))
    if (inline) {
      this.identity = inline
      this.render()
      return
    }
    const userName = this.getAttribute('user-name')
    if (!userName) {
      this.identity = null
      this.render()
      return
    }
    // 先渲染降级骨架，请求成功后再补全
    this.identity = null
    this.render()
    fetchIdentity(userName).then((identity) => {
      // 评论翻页会卸掉宿主：disconnectedCallback 已 runCleanups，
      // 若请求回来仍渲染，用户卡会把外点 / Esc / 滚动监听再挂回 document，再也没人摘
      if (token !== this.loadToken || !this.isConnected) {
        return
      }
      this.identity = identity
      this.render()
    })
  }

  /** 模板来源优先级：后台自定义 > 主题 `<template>` > 内置默认（按组件独立判断）。 */
  protected render(): void {
    const component = this.componentKey
    renderTemplate({
      root: this.shadow,
      host: this,
      data: this.buildData(),
      template: getTemplate(component) || BUILTIN_TEMPLATES[component],
      component: this.tagName.toLowerCase(),
    })
  }

  /**
   * 模板可见的数据。数据未到 / 失败时给骨架对象而非 null，免得每份模板都要写判空；
   * 已到达时按本组件的密度配置裁剪（见 hip-data.ts 的 applyDisplayPolicy）。
   */
  protected buildData(): HipData {
    const attrs: Record<string, string> = {}
    for (const attribute of Array.from(this.attributes)) {
      attrs[attribute.name] = attribute.value
    }
    const fallbackName = this.getAttribute('user-name') || ''
    if (!this.identity) {
      return skeletonData(fallbackName, attrs)
    }
    return toHipData(
      this.identity,
      attrs,
      resolveProfileUrl(this.identity, fallbackName),
      this.componentKey,
    )
  }
}
