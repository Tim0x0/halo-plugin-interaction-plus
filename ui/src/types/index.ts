// interaction-plus 前端类型定义，与后端模型保持一致

export interface Metadata {
  name: string
  creationTimestamp?: string
  version?: number
  labels?: Record<string, string>
  annotations?: Record<string, string>
}

export interface ListResult<T> {
  page: number
  size: number
  total: number
  items: T[]
  first?: boolean
  last?: boolean
  hasNext?: boolean
  hasPrevious?: boolean
  totalPages?: number
}

// ── 装饰资产 ──────────────────────────────────────────────

export type DecorationTypeValue =
  | 'badge'
  | 'avatar_frame'
  | 'title'
  | 'card_background'
  | 'name_style'

export type DecorationStatusValue = 'draft' | 'active' | 'disabled' | 'archived'

export interface AssetRef {
  url?: string
  mediaType?: string
  sizeBytes?: number
  width?: number
  height?: number
}

export interface NameStyle {
  mode: 'solid' | 'gradient'
  colors: string[]
}

export interface AssetPayload {
  /** 称号名称；行内场景展示它，同时是称号图的替代文本与加载失败兜底 */
  titleText?: string
  titleColor?: string
  titleBackground?: string
  /** 称号背景第二色（可选）；存在时与 titleBackground 形成线性渐变 */
  titleBackgroundSecondary?: string
  nameStyle?: NameStyle
}

export interface DecorationAssetSpec {
  type: DecorationTypeValue
  displayName: string
  description?: string
  status: DecorationStatusValue
  categoryName?: string
  tagNames?: string[]
  rarityName?: string
  asset?: AssetRef
  payload?: AssetPayload
  submittedBy?: string
  createdBy?: string
  updatedBy?: string
  enabledAt?: string
  disabledAt?: string
  archivedAt?: string
}

export interface DecorationAsset {
  apiVersion?: string
  kind?: string
  metadata: Metadata
  spec: DecorationAssetSpec
}

export interface DecorationAssetParam {
  type?: DecorationTypeValue
  displayName: string
  description?: string
  categoryName?: string
  tagNames?: string[]
  rarityName?: string
  asset?: AssetRef
  payload?: AssetPayload
}

// ── 元数据 ──────────────────────────────────────────────

export interface DecorationMetadataSpec {
  displayName: string
  description?: string
  color?: string
  enabled?: boolean
  displayOrder?: number
  /** 是否允许外部发放（仅稀有度有意义）；为空视为允许 */
  externalGrantable?: boolean
}

export interface DecorationMetadata {
  metadata: Metadata
  spec: DecorationMetadataSpec
}

export interface MetadataParam {
  displayName: string
  description?: string
  color?: string
  enabled?: boolean
  displayOrder?: number
  /** 仅稀有度使用：是否允许外部发放 */
  externalGrantable?: boolean
}

export interface MetadataOptions {
  categories: DecorationMetadata[]
  tags: DecorationMetadata[]
  rarities: DecorationMetadata[]
}

// ── 授予 ──────────────────────────────────────────────

export interface GrantSpec {
  userName: string
  assetName: string
  grantType?: string
  /** 外部插件发放时的来源插件标识（grantType=external 时有值） */
  sourcePlugin?: string
  sourceRoleName?: string
  reason?: string
  expiresAt?: string
  revoked?: boolean
  revokedAt?: string
  revokedBy?: string
  revokeReason?: string
  grantedBy?: string
  grantedAt?: string
}

export interface DecorationGrant {
  metadata: Metadata
  spec: GrantSpec
}

/** 服务端判定的授予状态（避免客户端时钟偏差）。 */
export type GrantStatus = 'active' | 'expired' | 'revoked'

/** 授予记录列表视图：服务端聚合装饰显示名、类型、状态、来源角色显示名与素材数据。 */
export interface GrantView {
  grant: DecorationGrant
  assetDisplayName: string
  assetType?: DecorationTypeValue
  status: GrantStatus
  /** 角色快照来源的角色显示名；非角色快照授予为空 */
  sourceRoleDisplayName?: string
  /** 被授予用户的显示名（优先展示，回退用户名） */
  userDisplayName?: string
  /** 被授予用户的头像 URL */
  userAvatar?: string
  /** 装饰素材引用（缩略展示用）；资产已删除时为空 */
  asset?: AssetRef
  /** 装饰扩展数据（称号 / 昵称样式真实渲染用） */
  payload?: AssetPayload
}

export interface GrantParam {
  userNames: string[]
  assetNames: string[]
  reason?: string
  expiresAt?: string
}

export interface RoleGrantParam {
  roleNames: string[]
  assetNames: string[]
  reason?: string
  expiresAt?: string
}

export interface GrantResultItem {
  userName: string
  /** 被授予用户显示名（优先展示，回退用户名） */
  userDisplayName?: string
  assetName: string
  grantName?: string
}

export interface GrantFailure {
  userName: string
  /** 被授予用户显示名（优先展示，回退用户名） */
  userDisplayName?: string
  assetName: string
  reason: string
}

export interface GrantResult {
  granted: GrantResultItem[]
  /** 已续期项（已持有后台来源的有效授予，本次延长了有效期） */
  renewed: GrantResultItem[]
  skipped: GrantResultItem[]
  failed: GrantFailure[]
  successCount: number
  renewedCount: number
  skippedCount: number
  failedCount: number
}

// ── 身份标识 ──────────────────────────────────────────

/** 身份标识展示形态：文字牌用 color，图标用 icon，图片用 image。缺省按哪个字段非空推断 */
export type IdentityMarkMode = 'text' | 'icon' | 'image'

export interface IdentityMarkMappingSpec {
  roleName: string
  displayName: string
  displayMode?: IdentityMarkMode
  /** 图标库字形（data URL） */
  icon?: string
  /** 上传图地址（附件） */
  image?: string
  color?: string
  priority?: number
  enabled?: boolean
}

export interface IdentityMarkMapping {
  metadata: Metadata
  spec: IdentityMarkMappingSpec
}

export interface IdentityMarkMappingView {
  mapping: IdentityMarkMapping
  roleExists: boolean
  /** Halo 角色显示名；角色不存在时回退内部名 */
  roleDisplayName?: string
}

export interface IdentityMarkMappingParam {
  roleName?: string
  displayName: string
  displayMode?: IdentityMarkMode
  /**
   * 三种形态各占独立字段（icon / image / color），可并存、互不覆盖 ——
   * 读出口按形态挑一个输出，切换形态无需再抹另一侧的值；仅在确实要清空时传 null。
   */
  icon?: string | null
  image?: string | null
  color?: string | null
  priority?: number
  enabled?: boolean
}

// ── Console：自定义模板 ──────────────────────────────

/** 可自定义模板的前台组件，与 hip-* 一一对应；也是 metadata.name */
export type TemplateComponent = 'identity' | 'avatar' | 'card'

export interface CustomTemplateSpec {
  component: TemplateComponent
  enabled?: boolean
  /** HTML 片段，可含 <script> */
  html?: string
  /** 纯 CSS，渲染时由 runtime 包 <style> */
  css?: string
}

export interface CustomTemplate {
  metadata: Metadata
  spec: CustomTemplateSpec
}

export interface CustomTemplateParam {
  enabled?: boolean
  html?: string
  css?: string
}

// ── UC ──────────────────────────────────────────────

export type InventoryStatus = 'available' | 'expired' | 'revoked' | 'disabled'

export interface InventoryItem {
  grantName: string
  assetName: string
  type?: DecorationTypeValue
  displayName?: string
  description?: string
  asset?: AssetRef
  payload?: AssetPayload
  categoryName?: string
  tagNames?: string[]
  rarityName?: string
  status: InventoryStatus
  available: boolean
  grantedAt?: string
  expiresAt?: string
}

export interface InvalidEquipItem {
  slot: string
  assetName: string
  reason: string
}

export interface ProfileView {
  avatarFrame?: string
  title?: string
  primaryBadge?: string
  badgeShowcase: string[]
  cardBackground?: string
  nameStyle?: string
  invalidItems: InvalidEquipItem[]
  /** 当前生效的身份标识（只读展示，按角色映射） */
  identityMarks?: IdentityMark[]
  /** 是否公开展示「我的装扮墙」 */
  publicDecorationsVisible?: boolean
}

export interface ProfileSaveParam {
  avatarFrame?: string
  title?: string
  primaryBadge?: string
  badgeShowcase?: string[]
  cardBackground?: string
  nameStyle?: string
}

// ── 公开身份（与 runtime/src/identity.ts 同构，预览直喂 renderPreview） ──

export interface IdentityMark {
  displayName: string
  /** 图标 data URL（Iconify 字形）或图片地址；与 color 互斥（非空时 color 恒为空） */
  icon?: string
  /** 文字牌颜色；与 icon 互斥（非空时 icon 恒为空） */
  color?: string
  priority?: number
}

/** 装饰快照：一件已佩戴装饰在公开接口里的形态。 */
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
  titleBackgroundSecondary?: string
  nameStyle?: NameStyle
  rarityName?: string
  rarityDisplayName?: string
  rarityColor?: string
  grantedAt?: string
  expiresAt?: string
}

/** 外部插件贡献的统计项。 */
export interface ContributedStat {
  source: string
  key: string
  label: string
  value: string
}

export interface IdentityStats {
  posts: number
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
  extras: ContributedStat[]
}

export interface PublicIdentity {
  userName: string
  displayName: string
  avatar?: string
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
  /** 互动统计；预览骨架数据可省略，消费方需判空。 */
  stats?: IdentityStats
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
  /** 昵称 / 用户卡头像跳转链接模板（{name} = 用户名）；空表示不跳转 */
  userCardLinkTemplate?: string
  /**
   * 无头像占位风格：halo 灰底首字母；hash 按显示名着色。
   * 只作用于内置 renderAvatar 占位；缺省按 halo。
   */
  avatarFallbackStyle?: 'halo' | 'hash'
}
