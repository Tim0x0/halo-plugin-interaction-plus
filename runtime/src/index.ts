import './styles.css'
import { HipUserAvatar } from './hip-user-avatar'
import { HipUserIdentity } from './hip-user-identity'
import { HipUserCard } from './hip-user-card'

// 注册 hip-* Web Components（不注册 halo-user-* 别名，不自动注入主题页面）
function define(name: string, ctor: CustomElementConstructor): void {
  if (!customElements.get(name)) {
    customElements.define(name, ctor)
  }
}

define('hip-user-avatar', HipUserAvatar)
define('hip-user-identity', HipUserIdentity)
define('hip-user-card', HipUserCard)

export const RUNTIME_VERSION = '0.1.0'

export { fetchIdentity } from './identity'
export type { PublicIdentity } from './identity'
