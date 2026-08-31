import { utils } from '@halo-dev/ui-shared'
import type {
  AssetPayload,
  DecorationStatusValue,
  DecorationTypeValue,
  MetadataOptions,
  NameStyle,
} from '@/types'
import type { GrantView } from '@/types'

/** 装饰类型中文名。 */
export const TYPE_LABELS: Record<DecorationTypeValue, string> = {
  badge: '勋章',
  avatar_frame: '头像框',
  title: '称号',
  card_background: '名片背景',
  name_style: '昵称样式',
}

/** 资产状态中文名。 */
export const STATUS_LABELS: Record<DecorationStatusValue, string> = {
  draft: '草稿',
  active: '已启用',
  disabled: '已停用',
  archived: '已归档',
}

/** 资产状态对应 VStatusDot 状态。 */
export const STATUS_STATES: Record<
  DecorationStatusValue,
  'default' | 'success' | 'warning' | 'error'
> = {
  draft: 'default',
  active: 'success',
  disabled: 'warning',
  archived: 'error',
}

/** 库存状态中文名。 */
export const INVENTORY_STATUS_LABELS: Record<string, string> = {
  available: '可用',
  expired: '已过期',
  revoked: '已撤销',
  disabled: '已下架',
}

/** 授予状态（服务端 GrantView.status）展示配置。 */
export const GRANT_STATUS_STATES: Record<
  string,
  { text: string; state: 'success' | 'warning' | 'error' }
> = {
  active: { text: '有效', state: 'success' },
  expired: { text: '已过期', state: 'warning' },
  revoked: { text: '已撤销', state: 'error' },
}

/** 授予来源展示：手动授予 / 角色快照（角色显示名）/ 外部插件（sourcePlugin）。 */
export function grantSourceLabel(view: GrantView): string {
  const spec = view.grant.spec
  if (spec.grantType === 'external') {
    return `插件 · ${spec.sourcePlugin ?? '未知来源'}`
  }
  if (spec.grantType === 'role_snapshot') {
    return `角色快照 · ${view.sourceRoleDisplayName ?? spec.sourceRoleName ?? ''}`
  }
  return '手动授予'
}

/** 失效角标短词（槽位摘要角标用，比完整原因更短）。 */
export const INVALID_BADGE_LABELS: Record<string, string> = {
  ASSET_NOT_FOUND: '已删除',
  ASSET_NOT_ACTIVE: '已停用',
  DECORATION_NOT_OWNED: '已失效',
  INVALID_ASSET_TYPE: '不匹配',
  GRANT_EXPIRED: '已过期',
  GRANT_REVOKED: '已撤销',
}

/** 授予失败码中文提示（GrantResult.failed[].reason，授予弹窗失败明细用；未知码回退原文）。 */
export const GRANT_FAILURE_LABELS: Record<string, string> = {
  ASSET_NOT_FOUND: '装饰不存在或已删除',
  ASSET_NOT_ACTIVE: '装饰未启用',
  USER_NOT_FOUND: '用户不存在',
  GRANT_ERROR: '授予失败',
}

/** 筛选哨兵值：未分类 / 无标签 / 无稀有度（与后端 DecorationAssetService.FILTER_NONE 对齐）。 */
export const FILTER_NONE = '__none__'

export type MetadataOptionKind = 'categories' | 'tags' | 'rarities'

const METADATA_NONE_LABELS: Record<MetadataOptionKind, string> = {
  categories: '未分类',
  tags: '无标签',
  rarities: '无稀有度',
}

/** 元数据内部名 → 显示名（哨兵值给「未分类 / 无标签 / 无稀有度」，未知名回退原文）。 */
export function metadataLabel(
  options: MetadataOptions | null | undefined,
  kind: MetadataOptionKind,
  name?: string,
): string {
  if (!name) return ''
  if (name === FILTER_NONE) return METADATA_NONE_LABELS[kind]
  return options?.[kind]?.find((item) => item.metadata.name === name)?.spec.displayName ?? name
}

/** 元数据内部名 → 配置色（标签 / 稀有度有 spec.color，分类没有）；未配置返回 undefined。 */
export function metadataColor(
  options: MetadataOptions | null | undefined,
  kind: MetadataOptionKind,
  name?: string,
): string | undefined {
  if (!name) return undefined
  return options?.[kind]?.find((item) => item.metadata.name === name)?.spec.color
}

/** 稀有度描边色（取稀有度配置色）。 */
export function rarityColor(
  options: MetadataOptions | null | undefined,
  name?: string,
): string | undefined {
  return metadataColor(options, 'rarities', name)
}

/** 标签 chip 内联样式：配置色只作描边，文字保持中性灰。资产列表与预览弹窗共用。 */
export function tagChipStyle(
  options: MetadataOptions | null | undefined,
  name: string,
): { borderColor: string } | undefined {
  const color = metadataColor(options, 'tags', name)
  return color ? { borderColor: color } : undefined
}

/** 列表标签单行呈现的个数上限，超量折叠成「+N」。 */
const TAG_CHIP_LIMIT = 2

/**
 * 列表标签单行只呈现前几个，其余折叠进计数牌。定值而非按宽度测量：
 * 列宽由 colgroup 定死，行为可预期比多占一行更划算。资产列表与投稿列表共用。
 */
export function shownTags(names: string[]): string[] {
  return names.slice(0, TAG_CHIP_LIMIT)
}

/** 折叠掉的标签个数（不足上限时为 0）。 */
export function restTagCount(names: string[]): number {
  return Math.max(0, names.length - TAG_CHIP_LIMIT)
}

export const TYPE_OPTIONS = (Object.keys(TYPE_LABELS) as DecorationTypeValue[]).map((value) => ({
  label: TYPE_LABELS[value],
  value,
}))

export const STATUS_OPTIONS = (Object.keys(STATUS_LABELS) as DecorationStatusValue[]).map(
  (value) => ({ label: STATUS_LABELS[value], value }),
)

/**
 * 仅接受 3 / 6 / 8 位十六进制颜色，非法值返回空串。与前台 `runtime/src/identity.ts`
 * 的 `safeHexColor` 同一规则（保证预览与前台对畸形数据的降级一致）。
 * 8 位是 #RRGGBBAA，用来带透明度。
 */
function safeHexColor(value?: string | null): string {
  return value && /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(value) ? value : ''
}

/**
 * 根据昵称样式配置生成受控的内联样式。
 * 纯色一个颜色；渐变 2-3 个颜色；不支持时降级第一个颜色。
 *
 * ⚠ 双实现同步清单：本函数与前台 `runtime/src/identity.ts` 的 `nameStyleCss`
 * （字符串形式）是同一规则的两份实现（不同构建产物、刻意不共包）。
 * 改**hex 白名单、渐变角度（90deg）、声明组合、降级规则**时必须两处同改，
 * 否则后台缩略图与前台效果不一致 —— 不报错、只是站长看到的与访客不同。
 */
export function nameStyleCss(nameStyle?: NameStyle | null): Record<string, string> {
  if (!nameStyle || !nameStyle.colors || nameStyle.colors.length === 0) {
    return {}
  }
  const colors = nameStyle.colors.filter((c) => !!safeHexColor(c))
  if (colors.length === 0) {
    return {}
  }
  if (nameStyle.mode === 'gradient' && colors.length >= 2) {
    return {
      background: `linear-gradient(90deg, ${colors.join(', ')})`,
      '-webkit-background-clip': 'text',
      'background-clip': 'text',
      color: colors[0],
      '-webkit-text-fill-color': 'transparent',
    }
  }
  return { color: colors[0] }
}

/**
 * 称号文字牌的内联样式（双背景 135° 渐变）。整图不走本函数。
 *
 * ⚠ 双实现同步清单：本函数与前台 `runtime/src/card-parts.ts` 的 `textTitleNode`
 * 内联的样式规则是同一规则的两份实现（不同构建产物、刻意不共包）。
 * 改**hex 白名单、渐变角度（135deg）、双色/单色分支**时必须两处同改。
 */
export function titleCss(payload?: AssetPayload | null): Record<string, string> {
  const style: Record<string, string> = {}
  const titleColor = safeHexColor(payload?.titleColor)
  const background = safeHexColor(payload?.titleBackground)
  const backgroundSecondary = safeHexColor(payload?.titleBackgroundSecondary)
  if (titleColor) {
    style.color = titleColor
  }
  if (background && backgroundSecondary) {
    style.background = `linear-gradient(135deg, ${background}, ${backgroundSecondary})`
  } else if (background) {
    style.background = background
  }
  return style
}

/**
 * 列表缩略图地址：优先走宿主附件缩略图（避免列表直接加载原图），
 * 宿主能力不可用或外链素材时回退原图。
 */
export function thumbnailUrl(url?: string, size: 'S' | 'M' | 'L' | 'XL' = 'S'): string | undefined {
  if (!url) {
    return undefined
  }
  try {
    const attachment = (
      utils as { attachment?: { getThumbnailUrl?: (url: string, size: string) => string } }
    ).attachment
    return attachment?.getThumbnailUrl?.(url, size) ?? url
  } catch {
    return url
  }
}

/** 格式化时间为本地字符串；空值或非法日期返回占位符。 */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}
