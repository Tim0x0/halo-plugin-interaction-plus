import { fetchIdentity, parseData, type PublicIdentity } from './identity'

/**
 * hip-* 组件基类：
 * - 支持 user-name 自动请求与 data 直接渲染（data 优先）；
 * - Shadow DOM 样式隔离；
 * - API 失败时静默降级。
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

  connectedCallback(): void {
    this.load()
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
      if (token !== this.loadToken) {
        return
      }
      this.identity = identity
      this.render()
    })
  }

  protected abstract render(): void
}
