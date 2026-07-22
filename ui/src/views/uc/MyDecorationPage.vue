<script lang="ts" setup>
// UC - 我的装扮（对齐 Discord / Steam 装扮范式）：
// 左侧库存区（类型 Tab + 筛选 + 卡片网格，滚动），右侧预览面板 sticky
// （真实场景预览可切换 + 槽位摘要，点击槽位过滤左侧列表）。
// 先预览后保存语义不变；离开页面有未保存改动时确认。
import { computed, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  Dialog,
  Toast,
  VAlert,
  VButton,
  VCard,
  VEmpty,
  VPageHeader,
  VTabbar,
} from '@halo-dev/components'
import { consoleApiClient } from '@halo-dev/api-client'
import confetti from 'canvas-confetti'
import { ucApi } from '@/api'
import AssetThumb from '@/components/AssetThumb.vue'
import DecorationPreview from '@/components/DecorationPreview.vue'
import type { PreviewScene } from '@/components/DecorationPreview.vue'
import FilterDropdown from '@/components/filter/FilterDropdown.vue'
import FilterChips from '@/components/filter/FilterChips.vue'
import type { FilterChip } from '@/components/filter/FilterChips.vue'
import ListSkeleton from '@/components/ListSkeleton.vue'
import ListError from '@/components/ListError.vue'
import type {
  InvalidEquipItem,
  InventoryItem,
  MetadataOptions,
  PreviewData,
  PreviewIdentityMark,
  ProfileView,
} from '@/types'
import {
  FILTER_NONE,
  INVALID_BADGE_LABELS,
  INVENTORY_STATUS_LABELS,
  metadataLabel as metadataLabelOf,
  rarityColor as rarityColorOf,
  TYPE_LABELS,
} from '@/utils/decoration'
import type { MetadataOptionKind } from '@/utils/decoration'
import type { PreviewStatItem } from '@/types'

// 展示勋章佩戴上限（与后端 InteractionPlusConst.BADGE_SHOWCASE_MAX 同值联动）
const BADGE_SHOWCASE_MAX = 8

const loading = ref(false)
const loadError = ref(false)
const saving = ref(false)

const inventory = ref<InventoryItem[]>([])
const invalidItems = ref<InvalidEquipItem[]>([])
// 当前生效的身份标识（只读，按角色映射，由后端 /profile 一并返回）
const identityMarks = ref<PreviewIdentityMark[]>([])
const metadataOptions = ref<MetadataOptions | null>(null)

// ── 选择状态（保存前仅本地预览） ──────────────────────

interface Selection {
  avatarFrame: string
  title: string
  primaryBadge: string
  badgeShowcase: string[]
  cardBackground: string
  nameStyle: string
}

const selection = ref<Selection>(emptySelection())
const savedSnapshot = ref<string>(JSON.stringify(emptySelection()))

function emptySelection(): Selection {
  return {
    avatarFrame: '',
    title: '',
    primaryBadge: '',
    badgeShowcase: [],
    cardBackground: '',
    nameStyle: '',
  }
}

function selectionFromProfile(profile: ProfileView): Selection {
  return {
    avatarFrame: profile.avatarFrame ?? '',
    title: profile.title ?? '',
    primaryBadge: profile.primaryBadge ?? '',
    badgeShowcase: [...(profile.badgeShowcase ?? [])],
    cardBackground: profile.cardBackground ?? '',
    nameStyle: profile.nameStyle ?? '',
  }
}

const dirty = computed(() => JSON.stringify(selection.value) !== savedSnapshot.value)

// 离开页面脏检查：路由切换销毁组件前确认
onBeforeRouteLeave((to, from, next) => {
  if (!dirty.value) {
    next()
    return
  }
  Dialog.warning({
    title: '有未保存的佩戴改动',
    description: '离开页面将丢失未保存的佩戴选择，确定离开吗？',
    confirmType: 'danger',
    confirmText: '离开',
    cancelText: '留下',
    onConfirm: () => next(),
    onCancel: () => next(false),
  })
})

// ── 当前用户（预览头像 / 昵称 / 加入时间） ────────────────

const currentUser = ref<{
  userName?: string
  displayName?: string
  avatar?: string
  bio?: string
  registeredAt?: string
}>({})

async function loadCurrentUser() {
  try {
    // /users/- 是 Halo 唯一当前用户端点（所有已登录用户有权限）
    const { data } = await consoleApiClient.user.getCurrentUserDetail()
    currentUser.value = {
      userName: data.user.metadata.name,
      displayName: data.user.spec.displayName,
      avatar: data.user.spec.avatar,
      bio: data.user.spec.bio,
      registeredAt: data.user.spec.registeredAt ?? undefined,
    }
    if (currentUser.value.userName) {
      loadIdentityStats(currentUser.value.userName)
    }
  } catch {
    currentUser.value = {}
  }
}

// ── 互动统计（真实数据进预览卡片，与真卡同源） ────────────

/** 统计数值展示：≥1 万折算为「x.x万」，其余千分位（同步 runtime hip-user-card 的 formatCount）。 */
function formatCount(value: number): string {
  if (!Number.isFinite(value) || value < 0) {
    return '0'
  }
  if (value >= 10000) {
    const w = value / 10000
    const text = w >= 100 ? Math.round(w).toString() : w.toFixed(1).replace(/\.0$/, '')
    return `${text}万`
  }
  return value.toLocaleString('en-US')
}

const identityStats = ref<{ items: PreviewStatItem[]; badgeTotal?: number }>({ items: [] })

/** 拉取公开身份聚合的互动统计；失败静默（预览回落组件内示例值）。 */
async function loadIdentityStats(userName: string) {
  try {
    const response = await fetch(
      `/apis/api.interaction-plus.timxs.com/v1alpha1/identity/${encodeURIComponent(userName)}`,
      { headers: { Accept: 'application/json' } },
    )
    if (!response.ok) {
      return
    }
    const identity = await response.json()
    const stats = identity?.stats
    if (!stats) {
      return
    }
    const items: PreviewStatItem[] = [
      { label: '文章', value: formatCount(stats.posts ?? 0) },
      { label: '评论', value: formatCount(stats.comments ?? 0) },
    ]
    if (stats.decorations) {
      items.push({ label: '勋章', value: formatCount(stats.decorations.badge ?? 0) })
    }
    for (const extra of stats.extras ?? []) {
      if (extra?.label && extra?.value) {
        items.push({ label: extra.label, value: extra.value })
      }
    }
    // 关闭公开装扮墙时 decorations 为 null：badgeTotal 传 0（与真卡一致，不显示「+N」），
    // 不可留 undefined——那会让预览回落示例总数，显示假数据
    identityStats.value = {
      items,
      badgeTotal: stats.decorations ? (stats.decorations.badge ?? 0) : 0,
    }
  } catch {
    // 静默：预览回落示例值
  }
}

// ── 数据加载 ──────────────────────────────────────────

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const [inventoryData, profile, options] = await Promise.all([
      ucApi.inventory(),
      ucApi.getProfile(),
      ucApi.metadataOptions().catch(() => null),
    ])
    inventory.value = inventoryData
    metadataOptions.value = options
    applyProfile(profile)
    await loadCurrentUser()
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/**
 * 用服务端 profile 重建本地选择与快照。
 * 失效项（过期/撤销/停用等）**不自动卸载**，保留在佩戴位，仅记录失效原因供角标展示；
 * 是否取下交由用户决定（保存时后端照常校验过滤，前台展示也会过滤）。
 */
function applyProfile(profile: ProfileView) {
  const next = selectionFromProfile(profile)
  invalidItems.value = profile.invalidItems ?? []
  identityMarks.value = profile.identityMarks ?? []
  selection.value = next
  savedSnapshot.value = JSON.stringify(next)
}

/** 失效原因映射：assetName → reason（同一资产在多槽位失效原因一致，取首个）。 */
const invalidReasonByAsset = computed(() => {
  const map: Record<string, string> = {}
  for (const item of invalidItems.value) {
    if (!map[item.assetName]) {
      map[item.assetName] = item.reason
    }
  }
  return map
})

/** 失效的不同装饰数（按 assetName 去重）：失效项按卡槽统计，同一装饰占多个槽位只算一次。 */
const invalidAssetCount = computed(() => Object.keys(invalidReasonByAsset.value).length)

/** 某资产是否失效（用于卡片 / 槽位置灰）。 */
function invalidReasonOf(assetName?: string): string | undefined {
  if (!assetName) return undefined
  return invalidReasonByAsset.value[assetName]
}

// ── 左侧库存：类型 Tab + 筛选（历史默认隐藏） ──────────

const STATUS_ALL = '__all__'

const activeType = ref('')

const typeTabs = [
  { id: '', label: '全部' },
  ...Object.entries(TYPE_LABELS).map(([value, label]) => ({ id: value, label })),
]

const filters = ref({
  status: undefined as string | undefined,
  categoryName: undefined as string | undefined,
  tagName: undefined as string | undefined,
  rarityName: undefined as string | undefined,
})

const STATUS_ITEMS = [
  { label: '全部状态', value: STATUS_ALL },
  { label: '已过期', value: 'expired' },
  { label: '已撤销', value: 'revoked' },
  { label: '已停用', value: 'disabled' },
]

function metadataFilterItems(kind: 'categories' | 'tags' | 'rarities', noneLabel: string) {
  return [
    { label: noneLabel, value: FILTER_NONE },
    ...(metadataOptions.value?.[kind] ?? []).map((item) => ({
      label: item.spec.displayName,
      value: item.metadata.name,
    })),
  ]
}

const categoryItems = computed(() => metadataFilterItems('categories', '未分类'))
const tagItems = computed(() => metadataFilterItems('tags', '无标签'))
const rarityItems = computed(() => metadataFilterItems('rarities', '无稀有度'))

/** 状态排序权重：佩戴中 → 可用 → 过期 → 停用 → 撤销 */
function sortWeight(item: InventoryItem): number {
  if (isEquipped(item)) return 0
  switch (item.status) {
    case 'available':
      return 1
    case 'expired':
      return 2
    case 'disabled':
      return 3
    case 'revoked':
      return 4
    default:
      return 5
  }
}

const filteredInventory = computed(() => {
  const f = filters.value
  const list = inventory.value.filter((item) => {
    if (activeType.value && item.type !== activeType.value) return false
    // 历史装饰默认隐藏：未筛选状态时只显示可用项
    if (f.status === undefined) {
      if (item.status !== 'available') return false
    } else if (f.status !== STATUS_ALL && item.status !== f.status) {
      return false
    }
    if (f.categoryName === FILTER_NONE) {
      if (item.categoryName) return false
    } else if (f.categoryName && item.categoryName !== f.categoryName) {
      return false
    }
    if (f.tagName === FILTER_NONE) {
      if (item.tagNames?.length) return false
    } else if (f.tagName && !item.tagNames?.includes(f.tagName)) {
      return false
    }
    if (f.rarityName === FILTER_NONE) {
      if (item.rarityName) return false
    } else if (f.rarityName && item.rarityName !== f.rarityName) {
      return false
    }
    return true
  })
  return [...list].sort((a, b) => sortWeight(a) - sortWeight(b))
})

const metadataLabel = (kind: MetadataOptionKind, name?: string) =>
  metadataLabelOf(metadataOptions.value, kind, name)

const chips = computed<FilterChip[]>(() => {
  const f = filters.value
  const result: FilterChip[] = []
  if (f.status !== undefined) {
    result.push({
      key: 'status',
      label: `状态：${f.status === STATUS_ALL ? '全部' : (INVENTORY_STATUS_LABELS[f.status] ?? f.status)}`,
    })
  }
  if (f.categoryName) {
    result.push({
      key: 'categoryName',
      label: `分类：${metadataLabel('categories', f.categoryName)}`,
    })
  }
  if (f.tagName) {
    result.push({ key: 'tagName', label: `标签：${metadataLabel('tags', f.tagName)}` })
  }
  if (f.rarityName) {
    result.push({ key: 'rarityName', label: `稀有度：${metadataLabel('rarities', f.rarityName)}` })
  }
  return result
})

function removeChip(key: string) {
  ;(filters.value as Record<string, unknown>)[key] = undefined
}

function clearChips() {
  filters.value = {
    status: undefined,
    categoryName: undefined,
    tagName: undefined,
    rarityName: undefined,
  }
}

/** 稀有度描边色（取稀有度配置色）。 */
const rarityColor = (name?: string) => rarityColorOf(metadataOptions.value, name)

/**
 * 有效期展示：永久（无 expiresAt）不显示；临期（≤7 天）显示剩余倒计时并标黄；
 * 否则显示到期日期。仅对可用项有意义（失效项已有状态词）。
 */
function expiryInfo(item: InventoryItem): { text: string; urgent: boolean } | undefined {
  if (!item.expiresAt) return undefined
  const expireMs = new Date(item.expiresAt).getTime()
  if (Number.isNaN(expireMs)) return undefined
  const diffMs = expireMs - Date.now()
  if (diffMs <= 0) {
    return { text: '即将到期', urgent: true }
  }
  const hours = diffMs / 3_600_000
  // 不足 1 天显示剩余小时，避免一律显示「剩 1 天」
  if (hours < 24) {
    return { text: `剩 ${Math.max(1, Math.ceil(hours))} 小时`, urgent: true }
  }
  const days = Math.ceil(hours / 24)
  if (days <= 7) {
    return { text: `剩 ${days} 天`, urgent: true }
  }
  return { text: `${new Date(item.expiresAt).toLocaleDateString('zh-CN')} 到期`, urgent: false }
}

// ── 佩戴 / 取下 ──────────────────────────────────────

/**
 * 单值佩戴槽位定义（不含展示勋章列表）：key = Selection 字段，type = 资产类型。
 * 槽位摘要、类型→槽位映射均由此推导，新增槽位只改这里。
 */
const SLOT_DEFS = [
  { key: 'avatarFrame', label: '头像框', type: 'avatar_frame' },
  { key: 'title', label: '称号', type: 'title' },
  { key: 'primaryBadge', label: '主勋章', type: 'badge' },
  { key: 'cardBackground', label: '名片背景', type: 'card_background' },
  { key: 'nameStyle', label: '昵称样式', type: 'name_style' },
] as const

type SingleSlotKey = (typeof SLOT_DEFS)[number]['key']

// badge 类型佩戴走主勋章 / 展示位双槽（togglePrimaryBadge / toggleShowcaseBadge），不在此映射
const SINGLE_SLOT_BY_TYPE: Record<string, SingleSlotKey> = Object.fromEntries(
  SLOT_DEFS.filter((def) => def.type !== 'badge').map((def) => [def.type, def.key]),
)

function isEquipped(item: InventoryItem): boolean {
  if (item.type === 'badge') {
    return (
      selection.value.primaryBadge === item.assetName ||
      selection.value.badgeShowcase.includes(item.assetName)
    )
  }
  const key = item.type ? SINGLE_SLOT_BY_TYPE[item.type] : undefined
  return key ? selection.value[key] === item.assetName : false
}

function toggleSingle(item: InventoryItem) {
  const key = item.type ? SINGLE_SLOT_BY_TYPE[item.type] : undefined
  if (!key) return
  selection.value[key] = selection.value[key] === item.assetName ? '' : item.assetName
}

function togglePrimaryBadge(item: InventoryItem) {
  selection.value.primaryBadge =
    selection.value.primaryBadge === item.assetName ? '' : item.assetName
}

function toggleShowcaseBadge(item: InventoryItem) {
  const list = selection.value.badgeShowcase
  const index = list.indexOf(item.assetName)
  if (index >= 0) {
    list.splice(index, 1)
    return
  }
  if (list.length >= BADGE_SHOWCASE_MAX) {
    Toast.warning(`展示勋章最多 ${BADGE_SHOWCASE_MAX} 个`)
    return
  }
  list.push(item.assetName)
}

// ── 右侧预览面板：场景切换 + 槽位摘要 ──────────────────

const previewScene = ref<PreviewScene>('user_card')

const sceneTabs: Array<{ id: PreviewScene; label: string }> = [
  { id: 'identity_line', label: '身份行' },
  { id: 'user_card', label: '卡片' },
]

function findItem(assetName?: string): InventoryItem | undefined {
  if (!assetName) return undefined
  return inventory.value.find((item) => item.assetName === assetName)
}

const previewData = computed<PreviewData>(() => {
  const avatarFrame = findItem(selection.value.avatarFrame)
  const title = findItem(selection.value.title)
  const primaryBadge = findItem(selection.value.primaryBadge)
  const cardBackground = findItem(selection.value.cardBackground)
  const nameStyle = findItem(selection.value.nameStyle)
  return {
    displayName: currentUser.value.displayName,
    avatar: currentUser.value.avatar,
    bio: currentUser.value.bio,
    avatarFrameUrl: avatarFrame?.asset?.url,
    titleMode: title?.payload?.titleMode,
    titleImageUrl: title?.asset?.url,
    titleText: title?.payload?.titleText,
    titleColor: title?.payload?.titleColor,
    titleBackground: title?.payload?.titleBackground,
    titleBackgroundSecondary: title?.payload?.titleBackgroundSecondary,
    primaryBadgeUrl: primaryBadge?.asset?.url,
    badgeShowcaseUrls: selection.value.badgeShowcase
      .map((name) => findItem(name)?.asset?.url)
      .filter((url): url is string => !!url),
    cardBackgroundUrl: cardBackground?.asset?.url,
    nameStyle: nameStyle?.payload?.nameStyle,
    identityMarks: identityMarks.value,
    // 真实互动统计 / 勋章总数 / 加入时间：与真卡同源；未加载到时组件回落示例值
    stats: identityStats.value.items.length ? identityStats.value.items : undefined,
    badgeTotal: identityStats.value.badgeTotal,
    registeredAt: currentUser.value.registeredAt,
  }
})

/** 槽位摘要：缩略图 + 当前佩戴 + 改动小点 + 失效角标 + 取下 + 点击过滤左侧列表 */
interface SlotSummary {
  key: string
  label: string
  type: string
  item?: InventoryItem
  /** 与已保存快照相比是否有改动（行首小绿点标识） */
  modified: boolean
  /** 佩戴中但已失效的原因码（置灰 + 角标）；有效则 undefined */
  invalidReason?: string
  clear: () => void
}

const slotSummaries = computed<SlotSummary[]>(() => {
  const s = selection.value
  const saved: Selection = JSON.parse(savedSnapshot.value)
  return SLOT_DEFS.map(({ key, label, type }) => ({
    key,
    label,
    type,
    item: findItem(s[key]),
    modified: s[key] !== saved[key],
    invalidReason: invalidReasonOf(s[key]),
    clear: () => (selection.value[key] = ''),
  }))
})

/** 展示勋章佩戴格（BADGE_SHOWCASE_MAX 格：已佩戴的渲染缩略图，空格虚线占位，失效格置灰+角标） */
const showcaseSlots = computed(() => {
  const list = selection.value.badgeShowcase
  return Array.from({ length: BADGE_SHOWCASE_MAX }, (_, index) => ({
    name: list[index],
    item: findItem(list[index]),
    invalidReason: invalidReasonOf(list[index]),
  }))
})

const showcaseModified = computed(() => {
  const saved: Selection = JSON.parse(savedSnapshot.value)
  return JSON.stringify(selection.value.badgeShowcase) !== JSON.stringify(saved.badgeShowcase)
})

function removeShowcaseAt(index: number) {
  selection.value.badgeShowcase.splice(index, 1)
}

/** 点击槽位 → 左侧列表过滤到对应类型 */
function focusSlotType(type: string) {
  activeType.value = type
}

// ── 保存 ──────────────────────────────────────────────

async function handleSave() {
  saving.value = true
  try {
    const profile = await ucApi.saveProfile({
      avatarFrame: selection.value.avatarFrame || undefined,
      title: selection.value.title || undefined,
      primaryBadge: selection.value.primaryBadge || undefined,
      badgeShowcase: selection.value.badgeShowcase,
      cardBackground: selection.value.cardBackground || undefined,
      nameStyle: selection.value.nameStyle || undefined,
    })
    // 用服务端实际保存结果回写本地选择（失效项保留、标记，不卸载）
    applyProfile(profile)
    if (invalidItems.value.length) {
      Toast.warning('已保存；其中失效装饰在公开展示时会自动隐藏')
    } else {
      Toast.success('佩戴已保存')
      confetti({ particleCount: 80, spread: 70, origin: { y: 0.7 } })
    }
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    saving.value = false
  }
}

function handleReset() {
  selection.value = JSON.parse(savedSnapshot.value)
}

onMounted(load)
</script>

<template>
  <VPageHeader title="我的装扮">
    <template #icon>
      <span class="hip-page-icon">✦</span>
    </template>
    <template #actions>
      <VButton :disabled="!dirty" @click="handleReset">重置</VButton>
      <VButton :loading="saving" :disabled="!dirty" type="secondary" @click="handleSave">
        保存佩戴
      </VButton>
    </template>
  </VPageHeader>

  <div class="hip-page">
    <ListSkeleton v-if="loading" variant="grid" />
    <ListError v-else-if="loadError" title="装扮数据加载失败" @retry="load" />
    <template v-else>
      <!-- 失效提示：佩戴中存在失效装饰（未自动卸载），提示但保留，原因见槽位角标 -->
      <VAlert
        v-if="invalidItems.length"
        type="warning"
        :title="`有 ${invalidAssetCount} 个佩戴装饰已失效（多为被停用或下架）`"
        class="hip-deco__alert"
        :closable="true"
      />

      <div class="hip-deco">
        <!-- 左：库存区 -->
        <VCard :body-class="['!p-0']" class="hip-deco__inventory">
          <template #header>
            <div class="hip-toolbar">
              <VTabbar v-model:active-id="activeType" :items="typeTabs" type="outline" />
              <div class="hip-toolbar__filters">
                <FilterDropdown v-model="filters.status" label="状态" :items="STATUS_ITEMS" />
                <FilterDropdown
                  v-model="filters.categoryName"
                  label="分类"
                  :items="categoryItems"
                />
                <FilterDropdown v-model="filters.tagName" label="标签" :items="tagItems" />
                <FilterDropdown v-model="filters.rarityName" label="稀有度" :items="rarityItems" />
              </div>
            </div>
          </template>
          <FilterChips :chips="chips" @remove="removeChip" @clear="clearChips" />

          <VEmpty
            v-if="!filteredInventory.length"
            title="暂无装饰"
            message="获得装饰后会显示在这里；历史装饰可通过状态筛选查看"
          />
          <div v-else class="hip-inv">
            <div
              v-for="item in filteredInventory"
              :key="item.assetName"
              class="hip-inv__card"
              :class="{
                'hip-inv__card--unavailable': !item.available,
                'hip-inv__card--equipped': isEquipped(item),
              }"
              :style="
                rarityColor(item.rarityName)
                  ? { borderColor: rarityColor(item.rarityName) }
                  : undefined
              "
            >
              <div class="hip-inv__thumb">
                <AssetThumb
                  :type="item.type"
                  :asset="item.asset"
                  :payload="item.payload"
                  :display-name="item.displayName"
                />
                <!-- 类型 tag 左上角（与 Console 资产卡一致） -->
                <span class="hip-inv__type">{{ item.type ? TYPE_LABELS[item.type] : '装饰' }}</span>
                <span v-if="isEquipped(item)" class="hip-inv__equipped-mark">✓ 佩戴中</span>
              </div>
              <div class="hip-inv__body">
                <div class="hip-inv__name" :title="item.displayName ?? item.assetName">
                  {{ item.displayName ?? item.assetName }}
                </div>
                <div class="hip-inv__meta">
                  <span
                    v-if="item.rarityName"
                    class="hip-inv__rarity"
                    :style="{ color: rarityColor(item.rarityName) }"
                  >
                    {{ metadataLabel('rarities', item.rarityName) }}
                  </span>
                  <span v-if="item.status !== 'available'" class="hip-inv__status">
                    {{ INVENTORY_STATUS_LABELS[item.status] ?? item.status }}
                  </span>
                  <span
                    v-if="item.available && expiryInfo(item)"
                    class="hip-inv__expiry"
                    :class="{ 'hip-inv__expiry--urgent': expiryInfo(item)?.urgent }"
                  >
                    {{ expiryInfo(item)?.text }}
                  </span>
                </div>
                <div v-if="item.available" class="hip-inv__actions">
                  <template v-if="item.type === 'badge'">
                    <!-- 完整 toggle 文案（触屏无 hover，语义须直接可见）；选中态高亮为第二通道 -->
                    <VButton
                      size="sm"
                      :type="selection.primaryBadge === item.assetName ? 'secondary' : 'default'"
                      @click="togglePrimaryBadge(item)"
                    >
                      {{ selection.primaryBadge === item.assetName ? '取下主勋章' : '设为主勋章' }}
                    </VButton>
                    <VButton
                      size="sm"
                      :type="
                        selection.badgeShowcase.includes(item.assetName) ? 'secondary' : 'default'
                      "
                      @click="toggleShowcaseBadge(item)"
                    >
                      {{
                        selection.badgeShowcase.includes(item.assetName) ? '移出展示' : '加入展示'
                      }}
                    </VButton>
                  </template>
                  <template v-else>
                    <VButton size="sm" @click="toggleSingle(item)">
                      {{ isEquipped(item) ? '取下' : '佩戴' }}
                    </VButton>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </VCard>

        <!-- 右：预览面板（sticky，常驻可见） -->
        <div class="hip-deco__panel">
          <VCard :body-class="['!p-0']">
            <div class="hip-panel__scenes" role="group" aria-label="预览场景切换">
              <button
                v-for="scene in sceneTabs"
                :key="scene.id"
                type="button"
                class="hip-panel__scene-btn"
                :class="{ 'hip-panel__scene-btn--active': previewScene === scene.id }"
                @click="previewScene = scene.id"
              >
                {{ scene.label }}
              </button>
            </div>
            <div class="hip-panel__preview">
              <DecorationPreview :data="previewData" :scenes="[previewScene]" />
            </div>
            <!-- 身份标识：来自角色，只读展示（不可佩戴 / 卸下） -->
            <div v-if="identityMarks.length" class="hip-panel__marks">
              <span class="hip-panel__marks-label">身份标识</span>
              <span class="hip-panel__marks-items">
                <template v-for="(mark, index) in identityMarks" :key="index">
                  <img
                    v-if="mark.icon"
                    class="hip-panel__mark-icon"
                    :src="mark.icon"
                    :alt="mark.displayName"
                    :title="mark.displayName"
                  />
                  <span
                    v-else
                    class="hip-panel__mark"
                    :style="{ borderColor: mark.color, color: mark.color }"
                    >{{ mark.displayName }}</span
                  >
                </template>
              </span>
              <span class="hip-panel__marks-hint">来自角色，自动展示</span>
            </div>
            <!-- 槽位摘要：行首竖排 dirty 点 + 缩略图 + 失效角标 + 取下 + 点击过滤 -->
            <ul class="hip-panel__slots">
              <li
                v-for="slot in slotSummaries"
                :key="slot.key"
                class="hip-panel__slot"
                :class="{ 'hip-panel__slot--invalid': slot.invalidReason }"
                @click="focusSlotType(slot.type)"
              >
                <span
                  class="hip-panel__dirty"
                  :class="{ 'hip-panel__dirty--on': slot.modified }"
                  :title="slot.modified ? '有未保存改动' : undefined"
                ></span>
                <span class="hip-panel__slot-label">{{ slot.label }}</span>
                <span class="hip-panel__slot-thumb">
                  <AssetThumb
                    v-if="slot.item"
                    size="sm"
                    class="hip-panel__slot-thumb-inner"
                    :type="slot.item.type"
                    :asset="slot.item.asset"
                    :payload="slot.item.payload"
                    :display-name="slot.item.displayName"
                  />
                  <span v-else class="hip-panel__slot-empty"></span>
                </span>
                <span class="hip-panel__slot-value" :title="slot.item?.displayName">
                  {{ slot.item?.displayName ?? '未佩戴' }}
                </span>
                <span v-if="slot.invalidReason" class="hip-panel__slot-badge">
                  {{ INVALID_BADGE_LABELS[slot.invalidReason] ?? '已失效' }}
                </span>
                <button
                  v-if="slot.item"
                  type="button"
                  class="hip-panel__slot-clear"
                  :aria-label="`取下${slot.label}`"
                  @click.stop="slot.clear()"
                >
                  ×
                </button>
              </li>
              <!-- 展示勋章：缩略图格 -->
              <li class="hip-panel__slot hip-panel__slot--showcase" @click="focusSlotType('badge')">
                <span
                  class="hip-panel__dirty"
                  :class="{ 'hip-panel__dirty--on': showcaseModified }"
                  :title="showcaseModified ? '有未保存改动' : undefined"
                ></span>
                <span class="hip-panel__slot-label">展示勋章</span>
                <span class="hip-panel__showcase">
                  <span
                    v-for="(cell, index) in showcaseSlots"
                    :key="index"
                    class="hip-panel__showcase-cell"
                    :class="{
                      'hip-panel__showcase-cell--empty': !cell.item,
                      'hip-panel__showcase-cell--invalid': cell.invalidReason,
                    }"
                    :title="
                      cell.item
                        ? cell.invalidReason
                          ? `${cell.item.displayName}（${INVALID_BADGE_LABELS[cell.invalidReason] ?? '已失效'}）`
                          : cell.item.displayName
                        : undefined
                    "
                  >
                    <AssetThumb
                      v-if="cell.item"
                      size="sm"
                      class="hip-panel__slot-thumb-inner"
                      :type="cell.item.type"
                      :asset="cell.item.asset"
                      :payload="cell.item.payload"
                      :display-name="cell.item.displayName"
                    />
                    <button
                      v-if="cell.item"
                      type="button"
                      class="hip-panel__showcase-remove"
                      :aria-label="`移出展示：${cell.item.displayName}`"
                      @click.stop="removeShowcaseAt(index)"
                    >
                      ×
                    </button>
                  </span>
                </span>
              </li>
            </ul>
          </VCard>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hip-page {
  margin: 16px;
}
@media (max-width: 640px) {
  .hip-page {
    margin: 0;
  }
}
.hip-page-icon {
  font-size: 18px;
  color: var(--hip-text-secondary);
}
.hip-deco__alert {
  margin-bottom: var(--hip-gap-md);
}

/* 左右分栏：左库存滚动，右预览 sticky（380 = 卡片小样约 0.6 倍，兼顾库存列空间） */
.hip-deco {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: var(--hip-gap-lg);
  align-items: start;
}
.hip-deco__panel {
  position: sticky;
  top: 16px;
}
/* 窄屏：预览面板吸顶置于上方（紧凑） */
@media (max-width: 900px) {
  .hip-deco {
    grid-template-columns: 1fr;
  }
  .hip-deco__panel {
    order: -1;
    top: 0;
    z-index: 10;
  }
  .hip-panel__slots {
    display: none;
  }
}

.hip-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--hip-gap-md);
  width: 100%;
  background: var(--hip-bg-subtle);
  padding: 12px 16px;
  flex-wrap: wrap;
}
.hip-toolbar__filters {
  display: flex;
  align-items: center;
  gap: var(--hip-gap-lg);
  flex-wrap: wrap;
}

/* 库存卡片（类型 tag 左上角、稀有度独占边框、佩戴中角标 + 柔光）；
   列宽下限对齐 Console 资产卡（AssetsPage .hip-grid 的 200px），两端卡片同规格，
   且保证勋章双钮（五字文案）一行放下 */
.hip-inv {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--hip-gap-md);
  padding: var(--hip-gap-lg);
}
.hip-inv__card {
  border: 1px solid var(--hip-border);
  border-radius: var(--hip-radius-card);
  background: var(--hip-bg-card);
  overflow: hidden;
  transition: box-shadow var(--hip-transition);
}
.hip-inv__card:hover {
  box-shadow: var(--hip-shadow-hover);
}
.hip-inv__card--equipped {
  background: color-mix(in srgb, var(--hip-primary) 5%, var(--hip-bg-card));
}
.hip-inv__card--unavailable {
  opacity: 0.55;
}
.hip-inv__thumb {
  position: relative;
  aspect-ratio: 4 / 3;
}
.hip-inv__type {
  position: absolute;
  top: 8px;
  left: 8px;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--hip-border);
  color: var(--hip-text-secondary);
}
.hip-inv__equipped-mark {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  background: var(--hip-primary);
  color: #fff;
}
.hip-inv__body {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hip-inv__name {
  font-size: var(--hip-font-title);
  font-weight: 500;
  color: var(--hip-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hip-inv__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  min-height: 16px;
}
.hip-inv__rarity {
  font-size: var(--hip-font-caption);
}
.hip-inv__status {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-faint);
}
.hip-inv__expiry {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-faint);
}
.hip-inv__expiry--urgent {
  color: var(--hip-warning);
  font-weight: 600;
}
.hip-inv__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  /* 勋章双钮必须一行：禁换行 + 等分卡宽 */
  flex-wrap: nowrap;
  margin-top: 2px;
}
.hip-inv__actions > * {
  flex: 1;
  min-width: 0;
}

/* 预览面板 */
.hip-panel__scenes {
  display: flex;
  gap: 4px;
  padding: 12px 16px 0;
}
.hip-panel__scene-btn {
  border: 1px solid var(--hip-border);
  background: var(--hip-bg-card);
  cursor: pointer;
  padding: 4px 12px;
  border-radius: var(--hip-radius-chip);
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
  transition: all var(--hip-transition);
}
.hip-panel__scene-btn--active {
  background: var(--hip-bg-subtle);
  color: var(--hip-text-primary);
  font-weight: 600;
}
.hip-panel__preview {
  padding: 12px 16px;
}
/* 身份标识只读区（来自角色，不可操作） */
.hip-panel__marks {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 0 16px 12px;
}
.hip-panel__marks-label {
  flex: none;
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
}
.hip-panel__marks-items {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.hip-panel__mark {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--hip-border);
  border-radius: 4px;
  font-size: 11px;
  line-height: 1;
  padding: 2px 5px;
  color: var(--hip-text-secondary);
}
.hip-panel__mark-icon {
  width: 16px;
  height: 16px;
  object-fit: contain;
}
.hip-panel__marks-hint {
  font-size: 11px;
  color: var(--hip-text-faint);
}
.hip-panel__slots {
  margin: 0;
  padding: 4px 0 8px;
  list-style: none;
  border-top: 1px solid var(--hip-border-light);
}
.hip-panel__slot {
  display: flex;
  align-items: center;
  gap: var(--hip-gap-sm);
  padding: 7px 16px 7px 0;
  cursor: pointer;
  transition: background var(--hip-transition);
}
.hip-panel__slot:hover {
  background: var(--hip-bg-subtle);
}
/* 行首竖排 dirty 点（VS Code 风）：固定占位，有改动时显示绿点 */
.hip-panel__dirty {
  flex: none;
  width: 3px;
  align-self: stretch;
  border-radius: 0 2px 2px 0;
  background: transparent;
  margin-right: 8px;
}
.hip-panel__dirty--on {
  background: var(--hip-primary);
}
/* 失效槽位：缩略图 / 名称置灰，角标保留醒目 */
.hip-panel__slot--invalid .hip-panel__slot-thumb,
.hip-panel__slot--invalid .hip-panel__slot-value {
  opacity: 0.45;
}
.hip-panel__slot-badge {
  flex: none;
  font-size: 11px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--hip-warning) 16%, transparent);
  color: var(--hip-warning);
}
.hip-panel__slot-label {
  flex: none;
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
  width: 72px;
}
.hip-panel__slot-thumb {
  flex: none;
  display: inline-flex;
}
.hip-panel__slot-thumb-inner {
  width: 26px !important;
  height: 26px !important;
  border-radius: 6px;
}
.hip-panel__slot-empty {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  border: 1px dashed var(--hip-border);
}
.hip-panel__slot--showcase {
  align-items: flex-start;
}
.hip-panel__showcase {
  flex: 1;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.hip-panel__showcase-cell {
  position: relative;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  display: inline-flex;
}
.hip-panel__showcase-cell--empty {
  border: 1px dashed var(--hip-border);
}
.hip-panel__showcase-cell--invalid {
  opacity: 0.45;
  outline: 1px solid var(--hip-warning);
  outline-offset: -1px;
  border-radius: 6px;
}
.hip-panel__showcase-remove {
  position: absolute;
  top: -5px;
  right: -5px;
  width: 14px;
  height: 14px;
  border: none;
  border-radius: 50%;
  background: var(--hip-text-faint);
  color: #fff;
  font-size: 10px;
  line-height: 1;
  cursor: pointer;
  display: none;
  align-items: center;
  justify-content: center;
}
.hip-panel__showcase-cell:hover .hip-panel__showcase-remove {
  display: inline-flex;
}
.hip-panel__slot-value {
  flex: 1;
  min-width: 0;
  font-size: var(--hip-font-caption);
  color: var(--hip-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hip-panel__slot-clear {
  flex: none;
  border: none;
  background: none;
  cursor: pointer;
  padding: 0 2px;
  font-size: 14px;
  line-height: 1;
  color: var(--hip-text-faint);
  transition: color var(--hip-transition);
}
.hip-panel__slot-clear:hover {
  color: var(--hip-danger);
}
</style>
