<script lang="ts" setup>
// Console - 装饰资产页：卡片网格（默认）/ 行列表双视图
// 布局对齐 Halo 官方；缩略统一 AssetThumb（称号 / 昵称样式真实渲染）；
// 删除为级联模式：任何状态可删，有有效授予时警告影响面
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Dialog,
  IconGrid,
  IconList,
  Toast,
  VButton,
  VCard,
  VDropdown,
  VDropdownItem,
  VEmpty,
  VPageHeader,
  VPagination,
  VSpace,
  VStatusDot,
  VTag,
} from '@halo-dev/components'
import { assetApi, categoryApi, rarityApi, tagApi } from '@/api'
import AssetEditModal from '@/components/AssetEditModal.vue'
import AssetPreviewModal from '@/components/AssetPreviewModal.vue'
import AssetThumb from '@/components/AssetThumb.vue'
import RowActions from '@/components/RowActions.vue'
import GrantModal from '@/components/GrantModal.vue'
import FilterDropdown from '@/components/filter/FilterDropdown.vue'
import FilterChips from '@/components/filter/FilterChips.vue'
import type { FilterChip } from '@/components/filter/FilterChips.vue'
import ListSkeleton from '@/components/ListSkeleton.vue'
import ListError from '@/components/ListError.vue'
import { useListQuery } from '@/composables/use-list-query'
import type { DecorationAsset, DecorationMetadata, MetadataOptions } from '@/types'
import {
  FILTER_NONE,
  formatDateTime,
  metadataLabel as metadataLabelOf,
  rarityColor as rarityColorOf,
  restTagCount,
  shownTags,
  STATUS_LABELS,
  STATUS_OPTIONS,
  STATUS_STATES,
  tagChipStyle,
  TYPE_LABELS,
  TYPE_OPTIONS,
} from '@/utils/decoration'
import type { MetadataOptionKind } from '@/utils/decoration'

// ── 双视图（默认卡片网格，偏好记忆在本地） ──────────────

const VIEW_STORAGE_KEY = 'interaction-plus:assets-view'

const viewMode = ref<'grid' | 'list'>(
  localStorage.getItem(VIEW_STORAGE_KEY) === 'list' ? 'list' : 'grid',
)

function setViewMode(mode: 'grid' | 'list') {
  viewMode.value = mode
  localStorage.setItem(VIEW_STORAGE_KEY, mode)
}

// ── 筛选 ──────────────────────────────────────────────

const filters = reactive({
  keyword: '',
  submittedBy: '',
  type: undefined as string | undefined,
  status: undefined as string | undefined,
  categoryName: undefined as string | undefined,
  tagName: undefined as string | undefined,
  rarityName: undefined as string | undefined,
})

const metadataOptions = ref<MetadataOptions | null>(null)

async function loadMetadataOptions() {
  // Console 侧聚合全部元数据（含停用项），管理员可按停用元数据筛选
  try {
    const [categories, tags, rarities] = await Promise.all([
      categoryApi.list({ size: 200 }),
      tagApi.list({ size: 200 }),
      rarityApi.list({ size: 200 }),
    ])
    metadataOptions.value = {
      categories: categories.items,
      tags: tags.items,
      rarities: rarities.items,
    }
  } catch {
    // 元数据加载失败不阻断列表，仅筛选项缺失
  }
}

function metadataFilterItems(items: DecorationMetadata[], noneLabel: string) {
  return [
    { label: noneLabel, value: FILTER_NONE },
    ...items.map((item) => ({
      label:
        item.spec.enabled === false ? `${item.spec.displayName}（已停用）` : item.spec.displayName,
      value: item.metadata.name,
    })),
  ]
}

const categoryFilterItems = computed(() =>
  metadataFilterItems(metadataOptions.value?.categories ?? [], '未分类'),
)
const tagFilterItems = computed(() =>
  metadataFilterItems(metadataOptions.value?.tags ?? [], '无标签'),
)
const rarityFilterItems = computed(() =>
  metadataFilterItems(metadataOptions.value?.rarities ?? [], '无稀有度'),
)

const metadataLabel = (kind: MetadataOptionKind, name?: string) =>
  metadataLabelOf(metadataOptions.value, kind, name)

/** 稀有度描边色（取稀有度配置色）。 */
const rarityColor = (name?: string) => rarityColorOf(metadataOptions.value, name)

/** 标签列 hover 全称（chip 超出列宽时截断，靠 title 兜底）。 */
const tagTitle = (names: string[]) => names.map((name) => metadataLabel('tags', name)).join('、')

/** 标签 chip 配色：配置色只作描边；文字保持中性色，避免浅色标签在浅底上失去对比度。 */
const tagStyle = (name: string) => tagChipStyle(metadataOptions.value, name)

// ── 列表加载 ──────────────────────────────────────────

const {
  items,
  total,
  page,
  size,
  loading,
  error,
  load,
  search,
  onPaginationChange,
  reloadAfterRemove,
} = useListQuery<DecorationAsset>(({ page, size }) => {
  const params: Record<string, string | number> = { page, size }
  if (filters.keyword) params.keyword = filters.keyword
  if (filters.submittedBy) params.submittedBy = filters.submittedBy
  if (filters.type) params.type = filters.type
  if (filters.status) params.status = filters.status
  if (filters.categoryName) params.categoryName = filters.categoryName
  if (filters.tagName) params.tagName = filters.tagName
  if (filters.rarityName) params.rarityName = filters.rarityName
  return assetApi.list(params)
})

const chips = computed<FilterChip[]>(() => {
  const result: FilterChip[] = []
  if (filters.keyword) result.push({ key: 'keyword', label: `关键词：${filters.keyword}` })
  if (filters.submittedBy) {
    result.push({ key: 'submittedBy', label: `投稿者：${filters.submittedBy}` })
  }
  if (filters.type) {
    result.push({
      key: 'type',
      label: `类型：${TYPE_LABELS[filters.type as keyof typeof TYPE_LABELS] ?? filters.type}`,
    })
  }
  if (filters.status) {
    result.push({
      key: 'status',
      label: `状态：${STATUS_LABELS[filters.status as keyof typeof STATUS_LABELS] ?? filters.status}`,
    })
  }
  if (filters.categoryName) {
    result.push({
      key: 'categoryName',
      label: `分类：${metadataLabel('categories', filters.categoryName)}`,
    })
  }
  if (filters.tagName) {
    result.push({ key: 'tagName', label: `标签：${metadataLabel('tags', filters.tagName)}` })
  }
  if (filters.rarityName) {
    result.push({
      key: 'rarityName',
      label: `稀有度：${metadataLabel('rarities', filters.rarityName)}`,
    })
  }
  return result
})

function removeChip(key: string) {
  if (key === 'keyword') filters.keyword = ''
  else if (key === 'submittedBy') filters.submittedBy = ''
  else (filters as Record<string, unknown>)[key] = undefined
  search()
}

function clearChips() {
  filters.keyword = ''
  filters.submittedBy = ''
  filters.type = undefined
  filters.status = undefined
  filters.categoryName = undefined
  filters.tagName = undefined
  filters.rarityName = undefined
  search()
}

// ── 弹窗 ──────────────────────────────────────────────

const editVisible = ref(false)
const previewVisible = ref(false)
const grantVisible = ref(false)
const selectedAsset = ref<DecorationAsset | undefined>()

function openCreate() {
  selectedAsset.value = undefined
  editVisible.value = true
}

function openEdit(asset: DecorationAsset) {
  selectedAsset.value = asset
  editVisible.value = true
}

function openPreview(asset: DecorationAsset) {
  selectedAsset.value = asset
  previewVisible.value = true
}

function openGrant(asset?: DecorationAsset) {
  selectedAsset.value = asset
  grantVisible.value = true
}

// ── 状态流转（防连点） ────────────────────────────────

const transitionPending = ref(new Set<string>())

type TransitionAction = 'activate' | 'disable' | 'archive' | 'unarchive'

const TRANSITION_LABELS: Record<TransitionAction, string> = {
  activate: '启用',
  disable: '停用',
  archive: '归档',
  unarchive: '取消归档',
}

/**
 * 当前状态下可用的流转菜单项（网格 / 列表两视图共用同一份状态机条件，
 * 与后端 DecorationStatus.canTransitionTo 对齐）。
 */
function transitionActions(
  asset: DecorationAsset,
): Array<{ action: TransitionAction; label: string }> {
  const status = asset.spec.status
  const available: TransitionAction[] = []
  if (status === 'draft' || status === 'disabled') available.push('activate')
  if (status === 'active') available.push('disable')
  if (status === 'active' || status === 'disabled') available.push('archive')
  if (status === 'archived') available.push('unarchive')
  return available.map((action) => ({ action, label: TRANSITION_LABELS[action] }))
}

async function transition(asset: DecorationAsset, action: TransitionAction) {
  const name = asset.metadata.name
  if (transitionPending.value.has(name)) {
    return
  }
  transitionPending.value.add(name)
  try {
    await assetApi[action](name)
    Toast.success(`已${TRANSITION_LABELS[action]}`)
    if (filters.status) {
      reloadAfterRemove(1)
    } else {
      await load()
    }
  } catch {
    // 错误提示由 axios 拦截器统一处理，此处仅刷新展示真实状态
    await load()
  } finally {
    transitionPending.value.delete(name)
  }
}

/** 级联删除：先查有效授予数，按影响面区分确认文案。 */
async function handleDelete(asset: DecorationAsset) {
  let activeCount = 0
  try {
    activeCount = await assetApi.activeGrantCount(asset.metadata.name)
  } catch {
    // 查询失败按未知处理，仍给出级联提示
    activeCount = -1
  }
  const description =
    activeCount > 0
      ? `「${asset.spec.displayName}」当前已授予 ${activeCount} 位用户，删除将撤销这些授予并清理佩戴槽位（授予历史保留）。确定删除吗？`
      : activeCount === 0
        ? `确定删除「${asset.spec.displayName}」吗？该装饰当前没有有效授予。`
        : `确定删除「${asset.spec.displayName}」吗？若存在有效授予，删除将一并撤销并清理佩戴槽位。`
  Dialog.warning({
    title: '删除装饰',
    description,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      await assetApi.remove(asset.metadata.name)
      Toast.success('删除成功')
      reloadAfterRemove(1)
    },
  })
}

onMounted(() => {
  load()
  loadMetadataOptions()
})
</script>

<template>
  <VPageHeader title="装饰资产">
    <template #icon>
      <span class="hip-page-icon">✦</span>
    </template>
    <template #actions>
      <VSpace>
        <VButton
          v-permission="['plugin:interaction-plus:decoration:grant']"
          type="secondary"
          @click="openGrant(undefined)"
        >
          授予
        </VButton>
        <VButton type="secondary" @click="openCreate">创建装饰</VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <div class="hip-page">
    <VCard :body-class="['!p-0']">
      <!-- 工具栏：官方灰底 header，左搜索右筛选 + 视图切换 -->
      <template #header>
        <div class="hip-toolbar">
          <SearchInput
            v-model="filters.keyword"
            placeholder="搜索名称"
            @update:model-value="search"
          />
          <div class="hip-toolbar__filters">
            <FilterDropdown
              v-model="filters.type"
              label="类型"
              :items="TYPE_OPTIONS"
              @update:model-value="search"
            />
            <FilterDropdown
              v-model="filters.status"
              label="状态"
              :items="STATUS_OPTIONS"
              @update:model-value="search"
            />
            <FilterDropdown
              v-model="filters.categoryName"
              label="分类"
              :items="categoryFilterItems"
              @update:model-value="search"
            />
            <FilterDropdown
              v-model="filters.tagName"
              label="标签"
              :items="tagFilterItems"
              @update:model-value="search"
            />
            <FilterDropdown
              v-model="filters.rarityName"
              label="稀有度"
              :items="rarityFilterItems"
              @update:model-value="search"
            />
            <SearchInput
              v-model="filters.submittedBy"
              placeholder="按投稿者筛选"
              @update:model-value="search"
            />
            <div class="hip-view-switch" role="group" aria-label="视图切换">
              <button
                type="button"
                class="hip-view-switch__btn"
                :class="{ 'hip-view-switch__btn--active': viewMode === 'grid' }"
                title="卡片视图"
                @click="setViewMode('grid')"
              >
                <IconGrid />
              </button>
              <button
                type="button"
                class="hip-view-switch__btn"
                :class="{ 'hip-view-switch__btn--active': viewMode === 'list' }"
                title="列表视图"
                @click="setViewMode('list')"
              >
                <IconList />
              </button>
            </div>
          </div>
        </div>
      </template>
      <FilterChips :chips="chips" @remove="removeChip" @clear="clearChips" />

      <ListSkeleton v-if="loading" :variant="viewMode" />
      <ListError v-else-if="error" title="装饰列表加载失败" @retry="load" />
      <VEmpty v-else-if="!items.length" title="暂无装饰" message="点击右上角创建第一个装饰" />

      <!-- 卡片网格视图 -->
      <div v-else-if="viewMode === 'grid'" class="hip-grid">
        <div
          v-for="asset in items"
          :key="asset.metadata.name"
          class="hip-grid__card"
          :style="
            rarityColor(asset.spec.rarityName)
              ? { borderColor: rarityColor(asset.spec.rarityName) }
              : undefined
          "
        >
          <div class="hip-grid__thumb" @click="openPreview(asset)">
            <AssetThumb
              :type="asset.spec.type"
              :asset="asset.spec.asset"
              :payload="asset.spec.payload"
              :display-name="asset.spec.displayName"
            />
            <span class="hip-grid__type">{{ TYPE_LABELS[asset.spec.type] }}</span>
            <span
              v-if="asset.spec.categoryName"
              class="hip-grid__category"
              :title="metadataLabel('categories', asset.spec.categoryName)"
            >
              {{ metadataLabel('categories', asset.spec.categoryName) }}
            </span>
          </div>
          <div class="hip-grid__body">
            <div class="hip-grid__name" :title="asset.spec.displayName">
              {{ asset.spec.displayName }}
            </div>
            <div class="hip-grid__meta">
              <VStatusDot
                :state="STATUS_STATES[asset.spec.status]"
                :text="STATUS_LABELS[asset.spec.status]"
              />
              <!-- 投稿人不在卡片展示（次要信息，行视图与编辑弹窗可见），避免长名溢出 -->
              <span
                v-if="asset.spec.rarityName"
                class="hip-grid__rarity"
                :style="{ color: rarityColor(asset.spec.rarityName) }"
                :title="metadataLabel('rarities', asset.spec.rarityName)"
              >
                {{ metadataLabel('rarities', asset.spec.rarityName) }}
              </span>
            </div>
            <div class="hip-grid__actions">
              <VButton size="sm" @click="openEdit(asset)">编辑</VButton>
              <VButton
                v-if="asset.spec.status === 'active'"
                v-permission="['plugin:interaction-plus:decoration:grant']"
                size="sm"
                @click="openGrant(asset)"
              >
                授予
              </VButton>
              <VDropdown>
                <VButton size="sm">更多</VButton>
                <template #popper>
                  <VDropdownItem @click="openPreview(asset)">预览</VDropdownItem>
                  <VDropdownItem
                    v-for="item in transitionActions(asset)"
                    :key="item.action"
                    @click="transition(asset, item.action)"
                  >
                    {{ item.label }}
                  </VDropdownItem>
                  <VDropdownItem type="danger" @click="handleDelete(asset)">删除</VDropdownItem>
                </template>
              </VDropdown>
            </div>
          </div>
        </div>
      </div>

      <!-- 行列表视图：列宽由 colgroup 明确分配；低于最小宽度时保留信息密度并横向滑动。 -->
      <div
        v-else
        class="hip-table-scroll"
        role="region"
        aria-label="装饰资产列表，可横向滚动查看完整信息"
        tabindex="0"
      >
        <table class="hip-table hip-assets-table">
          <colgroup>
            <col class="hip-assets-table__col--asset" />
            <col class="hip-assets-table__col--category" />
            <col class="hip-assets-table__col--tags" />
            <col class="hip-assets-table__col--rarity" />
            <col class="hip-assets-table__col--status" />
            <col class="hip-assets-table__col--submitter" />
            <col class="hip-assets-table__col--created" />
            <col class="hip-assets-table__col--actions" />
          </colgroup>
          <thead>
            <tr>
              <th>装饰</th>
              <th>分类</th>
              <th>标签</th>
              <th>稀有度</th>
              <th>状态</th>
              <th>投稿人</th>
              <th>创建时间</th>
              <th class="hip-table__sticky-end" aria-label="操作"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="asset in items" :key="asset.metadata.name">
              <td>
                <div class="hip-assets-table__asset">
                  <AssetThumb
                    size="sm"
                    :type="asset.spec.type"
                    :asset="asset.spec.asset"
                    :payload="asset.spec.payload"
                    :display-name="asset.spec.displayName"
                  />
                  <div class="hip-table__main">
                    <span class="hip-table__title">
                      <span class="hip-table__title-text" :title="asset.spec.displayName">
                        {{ asset.spec.displayName }}
                      </span>
                      <VTag>{{ TYPE_LABELS[asset.spec.type] }}</VTag>
                    </span>
                    <span
                      v-if="asset.spec.description"
                      class="hip-table__desc"
                      :title="asset.spec.description"
                    >
                      {{ asset.spec.description }}
                    </span>
                  </div>
                </div>
              </td>
              <td>
                <span
                  v-if="asset.spec.categoryName"
                  class="hip-table__cell-ellipsis"
                  :title="metadataLabel('categories', asset.spec.categoryName)"
                >
                  {{ metadataLabel('categories', asset.spec.categoryName) }}
                </span>
                <span v-else>-</span>
              </td>
              <td>
                <span v-if="!asset.spec.tagNames?.length">-</span>
                <span v-else class="hip-table__tags" :title="tagTitle(asset.spec.tagNames)">
                  <span
                    v-for="tag in shownTags(asset.spec.tagNames)"
                    :key="tag"
                    class="hip-tag"
                    :style="tagStyle(tag)"
                  >
                    {{ metadataLabel('tags', tag) }}
                  </span>
                  <span
                    v-if="restTagCount(asset.spec.tagNames)"
                    class="hip-tag hip-tag--more"
                    aria-label="更多标签"
                  >
                    +{{ restTagCount(asset.spec.tagNames) }}
                  </span>
                </span>
              </td>
              <td>
                <span
                  v-if="asset.spec.rarityName"
                  class="hip-table__cell-ellipsis"
                  :style="{ color: rarityColor(asset.spec.rarityName) }"
                  :title="metadataLabel('rarities', asset.spec.rarityName)"
                >
                  {{ metadataLabel('rarities', asset.spec.rarityName) }}
                </span>
                <span v-else>-</span>
              </td>
              <td>
                <VStatusDot
                  :state="STATUS_STATES[asset.spec.status]"
                  :text="STATUS_LABELS[asset.spec.status]"
                />
              </td>
              <!-- 投稿人 / 创建时间：元信息靠右成组 -->
              <td>
                <span
                  v-if="asset.spec.submittedBy"
                  class="hip-table__cell-ellipsis"
                  :title="asset.spec.submittedBy"
                >
                  {{ asset.spec.submittedBy }}
                </span>
                <span v-else>-</span>
              </td>
              <td>
                <span
                  class="hip-table__cell-ellipsis"
                  :title="formatDateTime(asset.metadata.creationTimestamp)"
                >
                  {{ formatDateTime(asset.metadata.creationTimestamp) }}
                </span>
              </td>
              <td class="hip-assets-table__actions hip-table__sticky-end">
                <RowActions>
                  <VDropdownItem @click="openPreview(asset)">预览</VDropdownItem>
                  <VDropdownItem @click="openEdit(asset)">编辑</VDropdownItem>
                  <VDropdownItem
                    v-if="asset.spec.status === 'active'"
                    v-permission="['plugin:interaction-plus:decoration:grant']"
                    @click="openGrant(asset)"
                  >
                    授予
                  </VDropdownItem>
                  <VDropdownItem
                    v-for="item in transitionActions(asset)"
                    :key="item.action"
                    @click="transition(asset, item.action)"
                  >
                    {{ item.label }}
                  </VDropdownItem>
                  <VDropdownItem type="danger" @click="handleDelete(asset)">删除</VDropdownItem>
                </RowActions>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <template #footer>
        <VPagination
          :page="page"
          :size="size"
          :total="total"
          :size-options="[20, 50, 100]"
          @change="onPaginationChange"
        />
      </template>
    </VCard>

    <AssetEditModal
      v-if="editVisible"
      :asset="selectedAsset"
      mode="console"
      :metadata-options="metadataOptions"
      @close="editVisible = false"
      @saved="load"
    />
    <AssetPreviewModal
      v-if="previewVisible && selectedAsset"
      :asset="selectedAsset"
      :metadata-options="metadataOptions"
      @close="previewVisible = false"
    />
    <GrantModal
      v-if="grantVisible"
      :asset="selectedAsset?.spec.status === 'active' ? selectedAsset : undefined"
      @close="grantVisible = false"
      @granted="load"
    />
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
.hip-view-switch {
  display: inline-flex;
  gap: 4px;
}
.hip-view-switch__btn {
  border: none;
  background: none;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  font-size: 16px;
  color: var(--hip-text-muted);
  display: inline-flex;
  align-items: center;
  transition: all var(--hip-transition);
}
.hip-view-switch__btn:hover {
  background: #e5e7eb;
}
.hip-view-switch__btn--active {
  background: #e5e7eb;
  color: var(--hip-text-primary);
}

/* 卡片网格 */
.hip-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--hip-gap-md);
  padding: var(--hip-gap-lg);
}
.hip-grid__card {
  border: 1px solid var(--hip-border);
  border-radius: var(--hip-radius-card);
  background: var(--hip-bg-card);
  overflow: hidden;
  transition:
    box-shadow var(--hip-transition),
    transform var(--hip-transition);
}
.hip-grid__card:hover {
  box-shadow: var(--hip-shadow-hover);
}
.hip-grid__thumb {
  position: relative;
  aspect-ratio: 4 / 3;
  cursor: pointer;
  overflow: hidden;
}
.hip-grid__type {
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
/* 分类徽标：缩略图左下角，与左上角类型徽标同列成组（右上角在 UC 侧是「佩戴中」，留空不占） */
.hip-grid__category {
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
.hip-grid__body {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hip-grid__name {
  font-size: var(--hip-font-title);
  font-weight: 500;
  color: var(--hip-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hip-grid__meta {
  display: flex;
  align-items: center;
  gap: var(--hip-gap-sm);
  min-height: 18px;
}
.hip-grid__rarity {
  font-size: var(--hip-font-caption);
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hip-grid__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

/* 资产列表使用显式列规格：内容主列弹性伸缩，其余列按信息类型分配稳定宽度。
   表格窄于 min-width 时由外层横向滚动，长投稿人与标签不参与挤压主列。 */
.hip-assets-table {
  min-width: 1160px;
  table-layout: fixed;
}
.hip-assets-table__col--category {
  width: 112px;
}
.hip-assets-table__col--tags {
  width: 232px;
}
.hip-assets-table__col--rarity {
  width: 104px;
}
.hip-assets-table__col--status {
  width: 92px;
}
.hip-assets-table__col--submitter {
  width: 136px;
}
.hip-assets-table__col--created {
  width: 144px;
}
.hip-assets-table__col--actions {
  width: 48px;
}
.hip-assets-table tbody tr,
.hip-assets-table td {
  height: 64px;
}
.hip-assets-table__asset {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.hip-assets-table__asset .hip-table__main,
.hip-assets-table__asset .hip-table__title,
.hip-assets-table__asset .hip-table__title-text,
.hip-assets-table__asset .hip-table__desc {
  min-width: 0;
  max-width: 100%;
}
.hip-assets-table__actions {
  text-align: center;
}
</style>
