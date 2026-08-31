// 预览身份快照：把「一件装饰」或「一套佩戴选择」拼成可直接喂给 runtime 的 PublicIdentity
//
// runtime 的 renderPreview 不会自己去取数（见 preview.ts 的 buildPreviewData），
// 数据一律由调用方备好。三处预览的底座取法刻意不同：
//   - 自定义组件页：真实用户，越丰满越好 —— 调版式要靠真实数据暴露长昵称、多标识这类极端情况
//   - 资产弹窗：干净底座 + 单槽位 —— 看的是「这一件」，站长自己那身装扮会干扰判断，
//     而且每个站长账号装扮不同，同一个素材会看出不同结论
//   - UC 我的装扮：真实用户 + 草稿覆盖 —— 用户要看的就是全套搭配起来的样子

import { consoleApiClient } from '@halo-dev/api-client'
import type {
  AssetPayload,
  DecorationTypeValue,
  DecorationVo,
  InventoryItem,
  PublicIdentity,
  TemplateComponent,
} from '@/types'

/**
 * 预览场景两档，资产预览 / 编辑弹窗与 UC 面板共用（标签文案要一致，故收在一处）。
 *
 * <p>行内档把**头像与身份行一起渲染**：这两个组件在评论头部本来就是并排的，拆成两档
 * 看不出彼此的相对关系；更要紧的是身份行模板里没有头像，头像框装饰在那一档整个缺席，
 * 看着像素材没生效。合成一档后，四类行内落点（头像框 / 勋章 / 称号 / 昵称样式）一屏看全。
 */
export interface PreviewScene {
  id: string
  label: string
  components: TemplateComponent[]
}

export const PREVIEW_SCENES: PreviewScene[] = [
  { id: 'inline', label: '头像 + 身份行', components: ['avatar', 'identity'] },
  { id: 'card', label: '用户卡', components: ['card'] },
]

/** 按 id 取该档要渲染的组件；取不到回落用户卡。 */
export function sceneComponents(id: string): TemplateComponent[] {
  return PREVIEW_SCENES.find((scene) => scene.id === id)?.components || ['card']
}

/**
 * display 各项缺省值。拿不到真实设置时用它兜底 —— 这几项决定密度裁剪，
 * 缺了模板会读到 undefined。
 *
 * ⚠ 五处同步清单见 `runtime/src/hip-data.ts` 的 DISPLAY_DEFAULTS 注释；
 * 本文件是其中第 5 处（后台预览的兜底）。增删设置项时五处全改。
 */
export const DISPLAY_DEFAULTS: PublicIdentity['display'] = {
  identityLine: {
    showTitle: true,
    showPrimaryBadge: true,
    showNameStyle: true,
    showIdentityMarks: true,
    identityLimit: 1,
  },
  avatar: { showFrame: true },
  userCard: {
    showTitle: true,
    showPrimaryBadge: true,
    showShowcase: true,
    showNameStyle: true,
    showIdentityMarks: true,
    showAvatarFrame: true,
    showCardBackground: true,
    showcaseBadgeLimit: 5,
    identityLimit: 3,
  },
  userCardLinkTemplate: '/authors/{name}',
  avatarFallbackStyle: 'halo',
}

/**
 * 资产弹窗的干净底座。
 *
 * <p>存在的理由不是「没有真实数据可用」，而是**需要一个受控、可复现、无干扰的展示环境**：
 * 新素材还没发给任何人，它要判断的是自身观感，不是和站长现有装扮的搭配效果。
 *
 * <p>统计与加入时间必须给示例值：模板对空数据的正确反应是整块删除
 * （`stats` 为空则数据行消失、`registeredAt` 无效则加入时间消失），那是前台该有的行为，
 * 但会让素材预览看起来像张残缺的卡，站长无从判断这件装饰在真实卡里的位置。
 */
const SAMPLE_BASE: PublicIdentity = {
  userName: 'sample',
  displayName: '示例用户',
  bio: '这里是个人说明，用来预览装扮在真实卡片里的位置与配色。',
  registeredAt: '2021-03-01T00:00:00Z',
  identityMarks: [],
  decorations: { badgeShowcase: [] },
  stats: {
    posts: 128,
    comments: 356,
    decorations: {
      total: 18,
      badge: 12,
      avatarFrame: 3,
      title: 2,
      nameStyle: 1,
      cardBackground: 0,
    },
    extras: [],
  },
  display: DISPLAY_DEFAULTS,
}

/** 一件待预览的装饰：资产弹窗从表单或已存资产取，UC 从库存项取。 */
export interface PreviewDecoration {
  type?: DecorationTypeValue
  /** 素材地址（勋章 / 头像框 / 名片背景 / 称号图片） */
  url?: string
  payload?: AssetPayload
  /** 装饰名，仅用于展柜格的 title 提示；预览可缺省 */
  displayName?: string
  assetName?: string
}

/**
 * 装饰 → 公开接口里的快照形态。字段名与后端 DecorationVo 一一对应，
 * 模板读到的东西因此与前台完全一致。
 */
function toDecorationVo(item: PreviewDecoration, type: string): DecorationVo {
  return {
    assetName: item.assetName || '',
    type,
    displayName: item.displayName,
    url: item.url,
    titleText: item.payload?.titleText,
    titleColor: item.payload?.titleColor,
    titleBackground: item.payload?.titleBackground,
    titleBackgroundSecondary: item.payload?.titleBackgroundSecondary,
    nameStyle: item.payload?.nameStyle,
  }
}

/** 库存项 → 待预览装饰（UC 的槽位选择走这条）。 */
export function fromInventoryItem(item?: InventoryItem): PreviewDecoration | undefined {
  if (!item) {
    return undefined
  }
  return {
    type: item.type,
    url: item.asset?.url,
    payload: item.payload,
    displayName: item.displayName,
    assetName: item.assetName,
  }
}

/** 六个佩戴槽位。 */
export interface DecorationSlots {
  avatarFrame?: PreviewDecoration
  title?: PreviewDecoration
  primaryBadge?: PreviewDecoration
  badgeShowcase?: PreviewDecoration[]
  cardBackground?: PreviewDecoration
  nameStyle?: PreviewDecoration
}

/**
 * 在既有身份上覆盖一套佩戴选择，产出新的快照。
 *
 * <p>UC 用：底座是当前用户从公开接口拿到的**已保存**身份（真实统计、标识、加入时间），
 * 草稿只改 decorations 六个槽位 —— 接口返回的装扮是已保存的那套，不能直接拿来预览。
 *
 * <p>必须返回副本：底座对象会被多次预览复用，就地改会让「取消选择」再也回不到原状。
 */
export function withDecorations(base: PublicIdentity, slots: DecorationSlots): PublicIdentity {
  return {
    ...base,
    decorations: {
      avatarFrame: slots.avatarFrame && toDecorationVo(slots.avatarFrame, 'avatar_frame'),
      title: slots.title && toDecorationVo(slots.title, 'title'),
      primaryBadge: slots.primaryBadge && toDecorationVo(slots.primaryBadge, 'badge'),
      badgeShowcase: (slots.badgeShowcase || []).map((item) => toDecorationVo(item, 'badge')),
      cardBackground:
        slots.cardBackground && toDecorationVo(slots.cardBackground, 'card_background'),
      nameStyle: slots.nameStyle && toDecorationVo(slots.nameStyle, 'name_style'),
    },
  }
}

/**
 * 取当前登录用户的头像，给资产预览的底座用。
 *
 * <p>拿不到就返回 undefined，模板回落首字母占位 —— 预览少张头像不是错误，
 * 不值得为它中断整个预览。
 */
export async function currentUserAvatar(): Promise<string | undefined> {
  try {
    // /users/- 是 Halo 唯一当前用户端点（所有已登录用户有权限）
    const { data } = await consoleApiClient.user.getCurrentUserDetail()
    return data.user.spec.avatar
  } catch {
    return undefined
  }
}

/**
 * 资产弹窗预览：干净底座 + 把这一件放进它该在的槽位。
 *
 * <p>头像取当前登录用户的真实值：头像框必须套在一张真实头像上才看得准，而头像是身份
 * 不是装扮，不构成干扰。缺省时模板回落首字母占位。
 *
 * <p>勋章同时放主勋章位与展柜首格 —— 一件勋章在卡上有两个落点，站长两处都要看到。
 */
export function sampleIdentityWith(item: PreviewDecoration, avatar?: string): PublicIdentity {
  const base: PublicIdentity = { ...SAMPLE_BASE, avatar }
  switch (item.type) {
    case 'badge':
      return withDecorations(base, { primaryBadge: item, badgeShowcase: [item] })
    case 'avatar_frame':
      return withDecorations(base, { avatarFrame: item })
    case 'title':
      return withDecorations(base, { title: item })
    case 'card_background':
      return withDecorations(base, { cardBackground: item })
    case 'name_style':
      return withDecorations(base, { nameStyle: item })
    default:
      return withDecorations(base, {})
  }
}
