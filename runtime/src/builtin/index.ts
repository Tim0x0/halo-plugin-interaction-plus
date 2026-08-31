import type { HipComponent } from '../hip-data'
import type { TemplateSource } from '../template-engine'
import { AVATAR_TEMPLATE } from './avatar-template'
import { CARD_TEMPLATE } from './card-template'
import { IDENTITY_TEMPLATE } from './identity-template'

/**
 * 三个组件的内置默认模板 —— 也是后台「预填」掏出来的文本。
 *
 * <p>从这里导出而不是在 Console 里抄一份：抄就成了「同一内容的双实现」，
 * 预填出来的文本一旦与真实默认渲染不一致，站长按预填改完会发现效果对不上。
 */
export const BUILTIN_TEMPLATES: Record<HipComponent, TemplateSource> = {
  identity: IDENTITY_TEMPLATE,
  avatar: AVATAR_TEMPLATE,
  card: CARD_TEMPLATE,
}
