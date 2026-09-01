// 模板可见的数据：骨架对象、密度裁剪、派生字段
import type {
  DisplayConfig,
  IdentityLineDisplay,
  IdentityMark,
  PublicIdentity,
  UserCardDisplay,
} from './identity'

/** 三个组件标识，与后端 CustomTemplate 的三条记录一一对应。 */
export type HipComponent = 'identity' | 'avatar' | 'card'

/**
 * 模板可见的身份数据：{@link PublicIdentity} 原样 + 三个派生字段。
 * 字段不做任何裁剪或重命名，与 REST 响应一字不差（数量按组件裁剪除外，见 applyDisplayPolicy）。
 */
export interface HipData extends PublicIdentity {
  /** 算好的跳转地址；空串表示不跳转 */
  profileUrl: string
  /** 标签上的全部属性，如 { 'user-name': 'tim', scene: 'comment' } */
  attrs: Record<string, string>
  /** false = 数据尚未到达 / 请求失败 */
  loaded: boolean
}

/**
 * display（站点级展示策略）各项缺省值。
 *
 * ⚠ 五处同步清单：这套默认值在下列位置各有一份（后端读不到前端常量，两个前端产物
 * 又刻意不共包，每层都要自己的兜底）。**增删设置项时五处全改**：
 *   1. `src/main/resources/extensions/settings.yaml`（真值源，站长实际配的）
 *   2. `core/setting/DisplaySetting.java`（后端读设置的兜底）
 *   3. `identity/model/PublicIdentityVo.java`（后端出口 DTO 的兜底）
 *   4. 本文件（前台组件的兜底 + mergeDisplay 补全）
 *   5. `ui/src/utils/preview-identity.ts`（后台预览的兜底）
 * 漏改本文件的表现：模板读到 undefined，不报错、只是行为不对。
 */
const IDENTITY_LINE_DEFAULTS: IdentityLineDisplay = {
  showTitle: true,
  showPrimaryBadge: true,
  showNameStyle: true,
  showIdentityMarks: true,
  identityLimit: 1,
}

const USER_CARD_DEFAULTS: UserCardDisplay = {
  showTitle: true,
  showPrimaryBadge: true,
  showShowcase: true,
  showNameStyle: true,
  showIdentityMarks: true,
  showAvatarFrame: true,
  showCardBackground: true,
  showcaseBadgeLimit: 5,
  identityLimit: 3,
}

export const DISPLAY_DEFAULTS: DisplayConfig = {
  identityLine: { ...IDENTITY_LINE_DEFAULTS },
  avatar: { showFrame: true },
  userCard: { ...USER_CARD_DEFAULTS },
  userCardLinkTemplate: '/authors/{name}' as string,
  avatarFallbackStyle: 'halo' as const,
}

function clamp(value: number | undefined, min: number, max: number, fallback: number): number {
  return typeof value === 'number' && value >= min && value <= max ? value : fallback
}

function mergeDisplay(raw?: DisplayConfig): DisplayConfig {
  const line = { ...IDENTITY_LINE_DEFAULTS, ...raw?.identityLine }
  const card = { ...USER_CARD_DEFAULTS, ...raw?.userCard }
  return {
    ...DISPLAY_DEFAULTS,
    ...raw,
    identityLine: {
      ...line,
      identityLimit: clamp(line.identityLimit, 1, 3, 1),
    },
    avatar: { showFrame: true, ...raw?.avatar },
    userCard: {
      ...card,
      showcaseBadgeLimit: clamp(card.showcaseBadgeLimit, 0, 8, 5),
      identityLimit: clamp(card.identityLimit, 1, 5, 3),
    },
  }
}

/**
 * 骨架对象：数据未到或请求失败时交给模板。恒非 null，避免模板处处判空。
 * 数组恒为数组，decorations / display 恒为对象；stats 骨架态不存在，用前先判。
 */
export function skeletonData(
  userName: string,
  attrs: Record<string, string>,
  displayName?: string,
): HipData {
  return {
    userName,
    displayName: displayName || userName,
    avatar: '',
    bio: '',
    registeredAt: '',
    identityMarks: [],
    decorations: { badgeShowcase: [] },
    stats: undefined,
    display: mergeDisplay(),
    profileUrl: '',
    attrs,
    loaded: false,
  }
}

/** 可被场景开关整体裁掉的装饰槽位（展柜按数量裁剪，不在此列）。 */
type TrimKey = Exclude<keyof PublicIdentity['decorations'], 'badgeShowcase'>

/** 开关关闭时把对应装饰置 undefined（身份行 / 用户卡共用的裁剪规则，新增开关进这里）。 */
function trimByFlags(
  decorations: PublicIdentity['decorations'],
  flags: Array<[show: boolean, key: TrimKey]>,
): void {
  for (const [show, key] of flags) {
    if (!show) {
      decorations[key] = undefined
    }
  }
}

/** 身份标识：开关开按场景数量上限截取，关则裁空。 */
function limitMarks(show: boolean, marks: IdentityMark[], limit: number): IdentityMark[] {
  return show ? marks.slice(0, Math.max(0, limit)) : []
}

/**
 * 按当前组件的场景配置裁剪数据。后端读出口给全量；这里返回副本，
 * 避免同页身份行与用户卡互相污染。
 */
export function applyDisplayPolicy(
  identity: PublicIdentity,
  component: HipComponent,
): PublicIdentity {
  const display = mergeDisplay(identity.display)
  const marks = identity.identityMarks || []
  const decorations = {
    ...identity.decorations,
    badgeShowcase: identity.decorations?.badgeShowcase || [],
  }
  // data 直传时可省略 stats；传入 stats 时把 extras 规范为空数组或原数组，
  // 与后端出口和模板的列表契约保持一致。
  const stats = identity.stats
    ? { ...identity.stats, extras: identity.stats.extras || [] }
    : identity.stats
  if (component === 'identity') {
    const line = display.identityLine
    trimByFlags(decorations, [
      [line.showTitle, 'title'],
      [line.showPrimaryBadge, 'primaryBadge'],
      [line.showNameStyle, 'nameStyle'],
    ])
    return {
      ...identity,
      identityMarks: limitMarks(line.showIdentityMarks, marks, line.identityLimit),
      decorations,
      stats,
      display,
    }
  }
  if (component === 'card') {
    const card = display.userCard
    trimByFlags(decorations, [
      [card.showTitle, 'title'],
      [card.showPrimaryBadge, 'primaryBadge'],
      [card.showNameStyle, 'nameStyle'],
      [card.showAvatarFrame, 'avatarFrame'],
      [card.showCardBackground, 'cardBackground'],
    ])
    return {
      ...identity,
      identityMarks: limitMarks(card.showIdentityMarks, marks, card.identityLimit),
      decorations: {
        ...decorations,
        badgeShowcase: card.showShowcase
          ? decorations.badgeShowcase.slice(0, Math.max(0, card.showcaseBadgeLimit))
          : [],
      },
      stats,
      display,
    }
  }
  if (!display.avatar.showFrame) {
    decorations.avatarFrame = undefined
  }
  return { ...identity, identityMarks: marks, decorations, stats, display }
}

/**
 * 把已到达的 {@link PublicIdentity} 补全成 hipData（含按组件裁剪）。
 * 只兜 `identityMarks` / `badgeShowcase` / `stats.extras`；`stats` 本身可缺席。
 */
export function toHipData(
  identity: PublicIdentity,
  attrs: Record<string, string>,
  profileUrl: string,
  component: HipComponent,
): HipData {
  return {
    ...applyDisplayPolicy(identity, component),
    profileUrl,
    attrs,
    loaded: true,
  }
}

/**
 * 由链接模板算跳转地址（{name} = 用户名）。模板为空或无用户名时返回空串。
 * 与用户卡、身份行同一套规则。
 */
export function resolveProfileUrl(identity: PublicIdentity | null, fallbackName: string): string {
  const template = (
    identity?.display?.userCardLinkTemplate ?? DISPLAY_DEFAULTS.userCardLinkTemplate ?? ''
  ).trim()
  const name = identity?.userName || fallbackName
  if (!template || !name) {
    return ''
  }
  return template.replace(/\{name\}/g, encodeURIComponent(name))
}
