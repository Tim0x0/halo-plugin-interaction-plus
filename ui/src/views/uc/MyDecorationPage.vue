<script lang="ts" setup>
// UC - 我的装扮（对齐 Discord / Steam 装扮范式）：
// 左侧库存区（类型 Tab + 筛选 + 卡片网格，滚动），右侧预览面板 sticky
// （真实场景预览可切换 + 槽位摘要，点击槽位过滤左侧列表）。
// 先预览后保存语义不变；离开页面有未保存改动时确认。
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  Dialog,
  Toast,
  VAlert,
  VButton,
  VCard,
  VEmpty,
  VPageHeader,
  VPagination,
  VTabbar,
} from '@halo-dev/components'
import confetti from 'canvas-confetti'
import { ucApi } from '@/api'
import AssetThumb from '@/components/AssetThumb.vue'
import FilterDropdown from '@/components/filter/FilterDropdown.vue'
import FilterChips from '@/components/filter/FilterChips.vue'
import type { FilterChip } from '@/components/filter/FilterChips.vue'
import ListSkeleton from '@/components/ListSkeleton.vue'
import ListError from '@/components/ListError.vue'
import type {
  IdentityMark,
  InvalidEquipItem,
  InventoryItem,
  MetadataOptions,
  ProfileView,
  PublicIdentity,
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
import {
  currentUserDetail,
  DISPLAY_DEFAULTS,
  fromInventoryItem,
  PREVIEW_SCENES,
  sceneComponents,
  withDecorations,
} from '@/utils/preview-identity'
import { loadRuntimeForPreview, type HipRuntime } from '@/utils/runtime-loader'

// 展示勋章佩戴上限（与后端 InteractionPlusConst.BADGE_SHOWCASE_MAX 同值联动）
const BADGE_SHOWCASE_MAX = 8

const loading = ref(false)
const loadError = ref(false)
const saving = ref(false)

const inventory = ref<InventoryItem[]>([])
const invalidItems = ref<InvalidEquipItem[]>([])
// 当前生效的身份标识（只读，按角色映射，由后端 /profile 一并返回）
const identityMarks = ref<IdentityMark[]>([])
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
const savedSelection = ref<Selection>(emptySelection())

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

/** 深拷贝（平铺字段 + 一个数组）：快照与当前选择必须互不影响。 */
function cloneSelection(value: Selection): Selection {
  return { ...value, badgeShowcase: [...value.badgeShowcase] }
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

const dirty = computed(() => {
  const current = selection.value
  const saved = savedSelection.value
  return (
    current.avatarFrame !== saved.avatarFrame ||
    current.title !== saved.title ||
    current.primaryBadge !== saved.primaryBadge ||
    current.cardBackground !== saved.cardBackground ||
    current.nameStyle !== saved.nameStyle ||
    current.badgeShowcase.length !== saved.badgeShowcase.length ||
    current.badgeShowcase.some((name, index) => name !== saved.badgeShowcase[index])
  )
})

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
  const user = await currentUserDetail()
  if (!user) {
    currentUser.value = {}
    return
  }
  currentUser.value = {
    userName: user.metadata.name,
    displayName: user.spec.displayName,
    avatar: user.spec.avatar,
    bio: user.spec.bio,
    registeredAt: user.spec.registeredAt ?? undefined,
  }
  if (currentUser.value.userName) {
    loadBaseIdentity(currentUser.value.userName)
  }
}

// ── 预览底座（真实身份数据，与真卡同源） ──────────────────

/**
 * 当前用户的公开身份，作为预览底座 —— 真实统计、身份标识、加入时间、显示设置全在里面。
 * 草稿只覆盖 decorations 六个槽位（见 previewIdentity），其余原样交给模板。
 */
const baseIdentity = shallowRef<PublicIdentity | null>(null)

// 标识图标 404 兜底：裂图回落文字牌（按 URL 记失效，对齐 runtime「失败切文本牌」）
const brokenMarkIcons = ref(new Set<string>())

/**
 * 拉当前用户的公开身份当底座。
 *
 * <p>走 runtime 的 fetchIdentity 而不是自己发请求：接口地址、失败降级、并发合并都在
 * 那一份实现里，前台与这里读到的是同一个东西。
 *
 * <p>失败静默 —— 预览回落到只有基本信息的最小底座（见 fallbackBase），
 * 表现与前台拿不到数据时一致：统计行与加入时间整块不显示，而不是编一份假的出来。
 */
async function loadBaseIdentity(userName: string) {
  try {
    const loaded = await loadRuntimeForPreview()
    baseIdentity.value = await loaded.fetchIdentity(userName)
  } catch {
    // runtime 不可用时预览区已有独立提示，这里不必再报
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
  savedSelection.value = cloneSelection(next)
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
  { label: '已下架', value: 'disabled' },
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

/** 状态 / 分类 / 标签 / 稀有度（不含类型 Tab）。历史装饰默认隐藏。 */
function matchesFilters(item: InventoryItem): boolean {
  const f = filters.value
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
}

/** 当前筛选下的库存（未按类型切）。 */
const scopedInventory = computed(() => inventory.value.filter(matchesFilters))

const typeTabs = [
  { id: '', label: '全部' },
  ...Object.entries(TYPE_LABELS).map(([value, label]) => ({ id: value, label })),
]

/** 状态排序权重：佩戴中 → 可用 → 过期 → 下架 → 撤销 */
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
  const list = scopedInventory.value.filter(
    (item) => !activeType.value || item.type === activeType.value,
  )
  return [...list].sort((a, b) => sortWeight(a) - sortWeight(b))
})

// 前端分页：库存接口一次 listAll，全量留给预览 / 槽位反查；分页只切当前筛选结果。
const page = ref(1)
const size = ref(20)

const pagedInventory = computed(() => {
  const list = filteredInventory.value
  if (!list.length) return []
  const maxPage = Math.max(1, Math.ceil(list.length / size.value))
  const current = Math.min(Math.max(1, page.value), maxPage)
  return list.slice((current - 1) * size.value, current * size.value)
})

watch([() => filteredInventory.value.length, size], () => {
  const maxPage = Math.max(1, Math.ceil(filteredInventory.value.length / size.value))
  if (page.value > maxPage) {
    page.value = maxPage
  }
})

function onPaginationChange(value: { page: number; size: number }) {
  page.value = value.page
  size.value = value.size
}

/** 改 Tab / 筛选时回到第 1 页。 */
function resetPage() {
  page.value = 1
}

function onTypeChange(id: string | number) {
  const next = String(id)
  if (activeType.value === next) return
  activeType.value = next
  resetPage()
}

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
  resetPage()
}

function clearChips() {
  filters.value = {
    status: undefined,
    categoryName: undefined,
    tagName: undefined,
    rarityName: undefined,
  }
  resetPage()
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

// ── 右侧预览面板：组件切换 + 槽位摘要 ──────────────────

const previewScene = ref('card')

const sceneTabs = PREVIEW_SCENES

function findItem(assetName?: string): InventoryItem | undefined {
  if (!assetName) return undefined
  return inventory.value.find((item) => item.assetName === assetName)
}

/**
 * 公开身份拿不到时的最小底座：只有当前用户的基本信息 + /profile 返回的身份标识。
 *
 * <p>不补示例统计 —— 那会让用户看到「128 篇文章」这种假数据。空着才是对的：
 * 模板对空统计的反应是整块删除，与前台此刻的表现一致。
 */
const fallbackBase = computed<PublicIdentity>(() => ({
  userName: currentUser.value.userName || '',
  displayName: currentUser.value.displayName || currentUser.value.userName || '',
  avatar: currentUser.value.avatar,
  bio: currentUser.value.bio,
  registeredAt: currentUser.value.registeredAt,
  identityMarks: identityMarks.value,
  decorations: { badgeShowcase: [] },
  display: DISPLAY_DEFAULTS,
}))

/**
 * 预览身份：真实底座 + 未保存的佩戴草稿。
 *
 * <p>底座里的统计、身份标识、显示设置一概不动 —— 接口返回的 decorations 是**已保存**
 * 的那套，只有这六个槽位需要换成草稿。
 */
const previewIdentity = computed<PublicIdentity>(() =>
  withDecorations(baseIdentity.value || fallbackBase.value, {
    avatarFrame: fromInventoryItem(findItem(selection.value.avatarFrame)),
    title: fromInventoryItem(findItem(selection.value.title)),
    primaryBadge: fromInventoryItem(findItem(selection.value.primaryBadge)),
    badgeShowcase: selection.value.badgeShowcase
      .map((name) => fromInventoryItem(findItem(name)))
      .filter((item): item is NonNullable<typeof item> => !!item),
    cardBackground: fromInventoryItem(findItem(selection.value.cardBackground)),
    nameStyle: fromInventoryItem(findItem(selection.value.nameStyle)),
  }),
)

// ── 预览渲染（与前台同一个模板引擎） ────────────────────

const previewRef = ref<HTMLElement | null>(null)
const runtime = shallowRef<HipRuntime | null>(null)
const previewError = ref('')

onMounted(async () => {
  try {
    runtime.value = await loadRuntimeForPreview()
  } catch (e) {
    previewError.value = e instanceof Error ? e.message : String(e)
  }
})

// flush post：容器由 v-else 控制，渲染前要确保那个 div 已经在文档里
watch([previewIdentity, previewScene, runtime], () => renderPreview(), { flush: 'post' })

function renderPreview(): void {
  const container = previewRef.value
  if (!container || !runtime.value) {
    return
  }
  runtime.value
    .renderPreview(container, {
      component: sceneComponents(previewScene.value),
      data: previewIdentity.value,
      // fit 的基准是内容实际占地，装得下就 1:1 —— 行内档比面板小，实际不会被缩；
      // 只有 560 的用户卡真的超宽才等比缩。预览看的是搭配效果，不验交互，展开后锁死
      fit: true,
      locked: true,
    })
    .then(() => {
      previewError.value = ''
    })
    .catch((e) => {
      previewError.value = e instanceof Error ? e.message : String(e)
    })
}

onBeforeUnmount(() => {
  if (previewRef.value) {
    runtime.value?.disposePreview(previewRef.value)
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
  const saved = savedSelection.value
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
  const current = selection.value.badgeShowcase
  const saved = savedSelection.value.badgeShowcase
  return current.length !== saved.length || current.some((name, index) => name !== saved[index])
})

function removeShowcaseAt(index: number) {
  selection.value.badgeShowcase.splice(index, 1)
}

/** 点击槽位 → 左侧列表过滤到对应类型 */
function focusSlotType(type: string) {
  if (activeType.value === type) return
  activeType.value = type
  resetPage()
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
      Toast.warning('已保存，失效装饰前台不展示')
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
  selection.value = cloneSelection(savedSelection.value)
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
              <VTabbar
                :active-id="activeType"
                :items="typeTabs"
                type="outline"
                @update:active-id="onTypeChange"
              />
              <div class="hip-toolbar__filters">
                <FilterDropdown
                  v-model="filters.status"
                  label="状态"
                  :items="STATUS_ITEMS"
                  @update:model-value="resetPage"
                />
                <FilterDropdown
                  v-model="filters.categoryName"
                  label="分类"
                  :items="categoryItems"
                  @update:model-value="resetPage"
                />
                <FilterDropdown
                  v-model="filters.tagName"
                  label="标签"
                  :items="tagItems"
                  @update:model-value="resetPage"
                />
                <FilterDropdown
                  v-model="filters.rarityName"
                  label="稀有度"
                  :items="rarityItems"
                  @update:model-value="resetPage"
                />
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
              v-for="item in pagedInventory"
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
                <!-- 分类左下角（与 Console 资产卡一致）；右上角留给「佩戴中」 -->
                <span
                  v-if="item.categoryName"
                  class="hip-inv__category"
                  :title="metadataLabel('categories', item.categoryName)"
                >
                  {{ metadataLabel('categories', item.categoryName) }}
                </span>
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
          <template #footer>
            <VPagination
              :page="page"
              :size="size"
              :total="filteredInventory.length"
              :size-options="[20, 50, 100]"
              @change="onPaginationChange"
            />
          </template>
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
              <p v-if="previewError" class="hip-panel__preview-error">
                预览不可用：{{ previewError }}
              </p>
              <!-- 内部完全交给 runtime（它在里面建 iframe），Vue 不碰这层的子节点 -->
              <div v-else ref="previewRef"></div>
            </div>
            <!-- 身份标识：来自角色，只读展示（不可佩戴 / 卸下） -->
            <div v-if="identityMarks.length" class="hip-panel__marks">
              <span class="hip-panel__marks-label">身份标识</span>
              <span class="hip-panel__marks-items">
                <template v-for="(mark, index) in identityMarks" :key="index">
                  <img
                    v-if="mark.icon && !brokenMarkIcons.has(mark.icon)"
                    class="hip-panel__mark-icon"
                    :src="mark.icon"
                    :alt="mark.displayName"
                    :title="mark.displayName"
                    @error="brokenMarkIcons.add(mark.icon)"
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

/* 左右分栏：左库存滚动，右预览 sticky。预览列流式取宽：占容器 40%、
   区间 480-520（卡片缩放约 0.75-0.82）——预览是小样不是真卡，1:1 在管理面板里
   视觉过强（实测反馈），压回克制区间；库存列 auto-fill 自适应，不足走堆叠断点。
   横向滚动条防线在另外两处：库存列 min-width:0 + 断点 1200 保证并排时左列够宽 */
.hip-deco {
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(480px, 40%, 520px);
  gap: var(--hip-gap-lg);
  align-items: start;
}
/* 左列必须允许收缩（grid 项默认 min-width:auto 会被 VTabbar 等
   不可收缩内容撑破，进而顶出横向滚动条） */
.hip-deco__inventory {
  min-width: 0;
}
.hip-deco__panel {
  position: sticky;
  top: 16px;
}
/* 窄屏：预览面板吸顶置于上方（紧凑）。断点定 1200：并排形态只在
   左列保底约 450px（类型 Tab 行 + 双列库存放得下）时才存在，
   再窄一律堆叠——任何宽度下都不允许出现横向滚动条 */
@media (max-width: 1200px) {
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

/* 库存卡片（类型 tag 左上角、稀有度独占边框、佩戴中角标 + 柔光）。
   列宽下限 216：勋章双钮（「设为主勋章」五字 + sm 钮内衬 ≈ 89px/钮）在
   内容区 192px 内稳定一行——200 下限实测在窄列临界处按钮文字会折行，
   故有意偏离 Console 资产卡的 200（Console 卡无双钮，不受此约束） */
.hip-inv {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(216px, 1fr));
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
/* 分类徽标：缩略图左下角，与 Console 资产卡一致（右上角是「佩戴中」） */
.hip-inv__category {
  position: absolute;
  bottom: 8px;
  left: 8px;
  max-width: calc(100% - 16px);
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--hip-border);
  color: var(--hip-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
/* 勋章双钮必须一行：禁换行 + 等分卡宽 */
.hip-inv__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
  margin-top: 2px;
}
/* 兜底：按钮文字绝不折行成两行高（列宽由网格 216 下限保证，不应触发） */
.hip-inv__actions > * {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
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
.hip-panel__preview-error {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
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
