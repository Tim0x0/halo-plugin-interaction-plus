// 后台预览加载前台 runtime（renderPreview）。

import type { PublicIdentity, TemplateComponent } from '@/types'

/**
 * ReverseProxy 暴露的产物地址（见 extensions/reverseProxy.yaml）。
 *
 * `?v=` 使用构建时的插件版本，只供 Console / UC 内部加载。Runtime 文件名不带 hash，
 * 版本查询参数用于区分 Halo 长期缓存，使 `BUILTIN_TEMPLATES` 与当前构建产物一致。
 * 前台 Thymeleaf 模板通过 Finder 获取 Runtime URL。
 */
const RUNTIME_URL = `/plugins/interaction-plus/assets/runtime/interaction-plus.runtime.js?v=${process.env.PLUGIN_VERSION}`

/**
 * ⚠ 与 runtime/src/preview.ts 的 PreviewOptions 是同一份契约的两处声明
 * （两个独立构建产物，刻意不共包），改一处必须两处同改。
 */
export interface PreviewOptions {
  /**
   * 要渲染的组件槽位。给数组则同一个预览区里横排渲染多个，各自取自己那份模板与数据
   * （行内组合场景用：`['avatar', 'identity']` 即评论头部的真实排布）。
   */
  component: TemplateComponent | TemplateComponent[]
  /**
   * 草稿 HTML；**不传则渲染当前生效模板**（后台自定义 > 主题 template > 内置默认）。
   * 只有自定义组件页传草稿，消费端预览一律不传。
   */
  html?: string
  /** 草稿 CSS，与 html 同进同出 */
  css?: string
  /** 样本身份数据；缺省渲染骨架态 */
  data?: PublicIdentity | null
  /** 打到宿主上的属性，用于场景分档预览 */
  attrs?: Record<string, string>
  dark?: boolean
  /**
   * 预览视口宽度（px），也就是**模板眼里的 window.innerWidth**。
   * 用户卡窄屏判据是 max-width:640 而卡身只有 560 —— 要桌面形态必须给 > 640。
   * 缺省铺满容器（fit 模式下由 runtime 取默认基准宽）。
   */
  width?: number
  /** 容器装不下时整体等比缩小（缩视觉尺寸，不改视口宽度，因此形态判定不受影响） */
  fit?: boolean
  /** 锁定：用户卡自动展开且禁用一切交互（消费端预览是看外观，不是验交互） */
  locked?: boolean
}

export interface HipRuntime {
  /**
   * 模板注册表就绪信号（含拉取失败降级），恒不 reject。
   * 缺省渲染当前生效模板的调用必须等它，否则会拿空注册表渲染成内置默认。
   */
  templatesReady: Promise<void>
  /** 渲染完成后 resolve；容器始终取不到 iframe 浏览上下文则 reject。内部已等就绪，调用方 await 一次即可。 */
  renderPreview(container: HTMLElement, options: PreviewOptions): Promise<void>
  /** 摘掉该容器登记的全局监听与观察器（模板会往 document 上挂外点 / Esc） */
  disposePreview(container: HTMLElement): void
  BUILTIN_TEMPLATES: Record<string, { html: string; css: string }>
  fetchIdentity(userName: string): Promise<PublicIdentity | null>
}

declare global {
  interface Window {
    InteractionPlusRuntime?: HipRuntime
  }
}

let loading: Promise<HipRuntime> | null = null

/**
 * 加载并返回 runtime 全局对象（IIFE 产物挂 window，全局名见 runtime/vite.config.ts）。
 * 多次调用共享同一次加载。
 */
export function loadRuntime(): Promise<HipRuntime> {
  if (window.InteractionPlusRuntime) {
    return Promise.resolve(window.InteractionPlusRuntime)
  }
  if (loading) {
    return loading
  }
  loading = new Promise<HipRuntime>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = RUNTIME_URL
    script.async = true
    script.onload = () => {
      const runtime = window.InteractionPlusRuntime
      if (runtime) {
        resolve(runtime)
      } else {
        reject(new Error('runtime 已加载但未挂到 window.InteractionPlusRuntime'))
      }
    }
    script.onerror = () => reject(new Error('runtime 脚本加载失败'))
    document.head.appendChild(script)
  }).catch((error) => {
    // 失败后允许重试（如首次加载时插件正在重启）
    loading = null
    throw error
  })
  return loading
}

/**
 * 加载 runtime 并等模板注册表就绪。
 *
 * <p>消费端预览（UC / 资产弹窗）用这个而不是 {@link loadRuntime}：它们渲染的是
 * 「站长现在配的那套模板」，而模板请求在 runtime 脚本执行时才发出 —— 脚本 onload
 * 只代表引擎可用，抢在模板到达前渲染会静默退成内置默认，且没有任何东西会触发重渲染。
 *
 * <p>自定义组件页不用等：它传的是编辑框里的草稿，本来就不看注册表。
 *
 * `templatesReady` 同时用于确认 UI 与 Runtime 产物契约一致。缺少该字段说明两个独立
 * 产物没有由同一次完整插件构建生成，不能继续预览。
 */
export async function loadRuntimeForPreview(): Promise<HipRuntime> {
  const runtime = await loadRuntime()
  if (!runtime.templatesReady) {
    throw new Error('UI 与 Runtime 产物不匹配，请执行完整插件构建')
  }
  await runtime.templatesReady
  return runtime
}
