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
  /** 称号形态：text=文字牌（默认），image=整图（素材引用） */
  titleMode?: 'text' | 'image'
  /** 称号文本；image 形态下作为替代文本与加载失败回落 */
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

export interface IdentityMarkMappingSpec {
  roleName: string
  displayName: string
  icon?: string
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
  icon?: string
  color?: string
  priority?: number
  enabled?: boolean
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
  identityMarks?: PreviewIdentityMark[]
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

// ── 装饰预览 ──────────────────────────────────────────

/** 预览场景标识。 */
export type PreviewScene = 'identity_line' | 'user_card'

export interface PreviewIdentityMark {
  displayName: string
  icon?: string
  color?: string
}

/** 预览卡片数据行的单个统计项（已格式化的展示文本）。 */
export interface PreviewStatItem {
  label: string
  value: string
}

/** 预览数据（DecorationPreview 组件入参）。 */
export interface PreviewData {
  displayName?: string
  avatar?: string
  /** 用户公开简介（卡片区域展示） */
  bio?: string
  avatarFrameUrl?: string
  /** 称号形态：text=文字牌（默认），image=整图 */
  titleMode?: 'text' | 'image'
  /** 整图称号的图片地址（titleMode=image 时渲染） */
  titleImageUrl?: string
  titleText?: string
  titleColor?: string
  titleBackground?: string
  titleBackgroundSecondary?: string
  primaryBadgeUrl?: string
  badgeShowcaseUrls?: string[]
  cardBackgroundUrl?: string
  nameStyle?: NameStyle
  identityMarks?: PreviewIdentityMark[]
  /** 卡片数据行统计项（UC 传真实值；不传时组件用示例值，如 Console 素材预览） */
  stats?: PreviewStatItem[]
  /** 勋章总数（展柜「+N」计数；不传用示例值） */
  badgeTotal?: number
  /** 注册时间 ISO（「加入时间」行；不传用示例文案） */
  registeredAt?: string
}
