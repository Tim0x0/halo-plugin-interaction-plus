import { BUILTIN_TEMPLATES } from './builtin'
import { HipUserAvatar } from './hip-user-avatar'
import { HipUserCard } from './hip-user-card'
import { HipUserIdentity } from './hip-user-identity'
import { disposePreview, renderPreview } from './preview'
import { fetchTemplates, setTemplates } from './template-source'

// 注册 hip-* Web Components；主题需显式使用标签，插件不向页面注入
function define(name: string, ctor: CustomElementConstructor): void {
  if (!customElements.get(name)) {
    customElements.define(name, ctor)
  }
}

function defineAll(): void {
  define('hip-user-avatar', HipUserAvatar)
  define('hip-user-identity', HipUserIdentity)
  define('hip-user-card', HipUserCard)
}

// 模板请求在模块顶层立即发起（与 script 解析同时，不等 DOM、不等任何组件 connect），
// 拿到后才注册自定义元素——延迟注册是为了避免「先按内置默认渲染一遍、模板到了再变一次」
// 的闪烁。代价是这期间标签露出的是插槽内容，所以请求要尽可能早、超时要短
// （见 template-source.ts）。拉取失败 / 超时 = 全部走下一级来源，与「没做自定义」一致。
//
// 包一层 Promise.resolve().then 而不是直接调：fetch 若在此环境不可用会同步抛，
// 顶层同步异常会让下面的 .finally 根本没挂上，三个组件一个都注册不了——
// 进了微任务，任何异常都变成可被 catch 的拒绝。代价只是一个微任务，不影响请求时机。
/**
 * 模板就绪信号：注册表写入（或拉取失败降级）之后 resolve，恒不 reject。
 *
 * <p>后台消费端预览（UC / 资产弹窗）必须等它：`renderPreview` 缺省取的是**当前生效
 * 模板**，而 runtime 脚本 `onload` 时这个请求多半还在飞 —— 抢跑就会拿着空注册表渲染成
 * 内置默认，而且模板到了之后没有任何东西会触发重渲染，站长配的模板等于不生效。
 */
export const templatesReady: Promise<void> = Promise.resolve()
  .then(fetchTemplates)
  .then(setTemplates)
  .catch(() => {
    // 网络错误已在 fetchTemplates 内部吞掉，这里只兜底意料外异常，不能让注册不发生
  })
  .finally(defineAll)
  .catch((error) => {
    // 注册自定义元素本身抛错（名字非法 / 冲突）是代码问题，不能静默咽掉
    console.error('[interaction-plus] 组件注册失败', error)
  })

export { fetchIdentity } from './identity'
export type { PublicIdentity } from './identity'
export { renderPreview, disposePreview }
export { BUILTIN_TEMPLATES }
export type { TemplateSource } from './template-engine'
export type { HipComponent, HipData } from './hip-data'
