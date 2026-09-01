// 公开身份数据类型与获取（带去重的内存请求缓存）

export interface IdentityMark {
  displayName: string
  /** 图标 data URL（Iconify 字形）或图片地址；与 color 互斥（非空时 color 恒为空） */
  icon?: string
  /** 文字牌颜色；与 icon 互斥（非空时 icon 恒为空） */
  color?: string
  priority?: number
}

export interface DecorationVo {
  assetName: string
  type: string
  displayName?: string
  url?: string
  mediaType?: string
  /** 称号名称；行内场景展示它，同时是称号图的替代文本与加载失败兜底 */
  titleText?: string
  titleColor?: string
  titleBackground?: string
  /** 称号背景第二色（可选，渐变） */
  titleBackgroundSecondary?: string
  nameStyle?: { mode: 'solid' | 'gradient'; colors: string[] }
  rarityName?: string
  /** 稀有度显示名（内联，可直接展示） */
  rarityDisplayName?: string
  /** 稀有度颜色（内联，可直接展示） */
  rarityColor?: string
  /** 获得时间（仅装饰墙返回，ISO 字符串） */
  grantedAt?: string
  /** 有效期；缺省表示永久（仅装饰墙返回，ISO 字符串） */
  expiresAt?: string
}

export interface ContributedStat {
  /** 来源插件 id（系统按 ExtensionDefinition 归属标注） */
  source: string
  /** 机器标识（来源插件内唯一） */
  key: string
  /** 展示名（如「采纳」） */
  label: string
  /** 展示值文本（如 "23"、"Lv.16"），原样展示 */
  value: string
}

export interface IdentityStats {
  /** 公开文章数 */
  posts: number
  /** 公开评论数 */
  comments: number
  /** 装扮计数；null/缺省 = 用户关闭公开装扮墙（不可用） */
  decorations?: {
    total: number
    badge: number
    avatarFrame: number
    title: number
    nameStyle: number
    cardBackground: number
  } | null
  /** 外部插件贡献的统计项 */
  extras: ContributedStat[]
}

export interface PublicIdentity {
  userName: string
  displayName: string
  avatar?: string
  /** 用户公开简介 */
  bio?: string
  /** 注册时间（ISO 字符串），卡片展示「加入时间」 */
  registeredAt?: string
  identityMarks: IdentityMark[]
  decorations: {
    avatarFrame?: DecorationVo
    title?: DecorationVo
    primaryBadge?: DecorationVo
    badgeShowcase: DecorationVo[]
    cardBackground?: DecorationVo
    nameStyle?: DecorationVo
  }
  /** 互动统计；直接传入的 data 可省略，消费方需判空。 */
  stats?: IdentityStats
  /** 站点级展示策略（站长设置，全站一份），不是该用户的属性 */
  display: DisplayConfig
}

/** 身份行场景（hip-user-identity） */
export interface IdentityLineDisplay {
  showTitle: boolean
  showPrimaryBadge: boolean
  showNameStyle: boolean
  showIdentityMarks: boolean
  identityLimit: number
}

/** 头像场景（hip-user-avatar） */
export interface AvatarDisplay {
  showFrame: boolean
}

/** 用户卡场景（hip-user-card） */
export interface UserCardDisplay {
  showTitle: boolean
  showPrimaryBadge: boolean
  showShowcase: boolean
  showNameStyle: boolean
  showIdentityMarks: boolean
  showAvatarFrame: boolean
  showCardBackground: boolean
  showcaseBadgeLimit: number
  identityLimit: number
}

export interface DisplayConfig {
  identityLine: IdentityLineDisplay
  avatar: AvatarDisplay
  userCard: UserCardDisplay
  /** 昵称 / 用户卡头像跳转链接模板（{name} = 用户名）；空或缺失表示不跳转 */
  userCardLinkTemplate?: string
  /**
   * 无头像占位风格：halo 灰底首字母；hash 按显示名着色。
   * 只作用于内置 renderAvatar 占位，不改 avatar 字段；缺省按 halo。
   */
  avatarFallbackStyle?: 'halo' | 'hash'
}

/** 插件公开 API 基础路径（身份查询与自定义模板拉取共用）。 */
export const API_BASE = '/apis/api.interaction-plus.timxs.com/v1alpha1'

// 同一页面对同一用户的并发请求合并
const pending = new Map<string, Promise<PublicIdentity | null>>()

/**
 * 获取单个用户公开身份。失败时静默返回 null（组件自行降级）。
 */
export function fetchIdentity(userName: string): Promise<PublicIdentity | null> {
  if (!userName) {
    return Promise.resolve(null)
  }
  const cached = pending.get(userName)
  if (cached) {
    return cached
  }
  const promise = fetch(`${API_BASE}/identity/${encodeURIComponent(userName)}`, {
    headers: { Accept: 'application/json' },
  })
    .then((response) => (response.ok ? (response.json() as Promise<PublicIdentity>) : null))
    .catch(() => null)
    .finally(() => {
      // 请求结束后移除，浏览器侧不做长缓存（服务端已有短缓存）
      setTimeout(() => pending.delete(userName), 1000)
    })
  pending.set(userName, promise)
  return promise
}

/**
 * 解析 data 属性（JSON 字符串）。失败返回 null。
 */
export function parseData(raw: string | null): PublicIdentity | null {
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as PublicIdentity
  } catch {
    return null
  }
}

/**
 * 仅接受 3 / 6 / 8 位十六进制颜色，非法值返回空串（与后端 HEX_COLOR_PATTERN 对齐）。
 * 8 位是 #RRGGBBAA，用来带透明度。runtime 以字符串拼接输出 style 属性，
 * hex 白名单杜绝畸形值注入额外 CSS 声明；所有拼入 style 的颜色字段
 * （昵称样式 / 称号三色 / 标识色）统一走此过滤。
 */
export function safeHexColor(value?: string | null): string {
  return value && /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(value) ? value : ''
}

/**
 * 根据昵称样式生成受控 CSS 文本。
 *
 * ⚠ 双实现同步清单：本函数与 Console 缩略图的 `ui/src/utils/decoration.ts` 的
 * `nameStyleCss`（对象形式）是同一规则的两份实现（不同构建产物、刻意不共包）。
 * 改**hex 白名单、渐变角度（90deg）、声明组合、降级规则**时必须两处同改，
 * 否则后台缩略图与前台效果不一致 —— 不报错、只是站长看到的与访客不同。
 */
export function nameStyleCss(nameStyle?: { mode: string; colors: string[] }): string {
  if (!nameStyle || !nameStyle.colors || nameStyle.colors.length === 0) {
    return ''
  }
  // 仅接受合法 hex 颜色，过滤潜在的 HTML/CSS 注入（与后端 hex 校验对齐）
  const colors = nameStyle.colors.filter((c) => !!safeHexColor(c))
  if (colors.length === 0) {
    return ''
  }
  if (nameStyle.mode === 'gradient' && colors.length >= 2) {
    return [
      `background:linear-gradient(90deg, ${colors.join(', ')})`,
      '-webkit-background-clip:text',
      'background-clip:text',
      `color:${colors[0]}`,
      '-webkit-text-fill-color:transparent',
    ].join(';')
  }
  return `color:${colors[0]}`
}

/** HTML 转义。 */
export function escapeHtml(value: string | undefined | null): string {
  if (!value) {
    return ''
  }
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 转义 CSS url() 中的特殊字符。仅做 CSS 层转义——产物拼进 HTML 属性
 * （如 style="..."）时外层必须再包 {@link escapeHtml}，否则 URL 中的引号
 * 仍会在 HTML 解析层截断属性。
 */
export function escapeCssUrl(url: string): string {
  return url.replace(/["'()\\\s]/g, (char) => `\\${char}`)
}
