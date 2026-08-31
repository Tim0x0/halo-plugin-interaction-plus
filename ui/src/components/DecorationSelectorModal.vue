<script lang="ts" setup>
// 选择装饰弹窗：独立的叠层弹窗（mount-to-body），由授予弹窗内 v-if 唤起。
// 对齐 Halo 官方范式（core AttachmentSelectorModal / plugin-photos / plugin-links）：
// 二级弹窗为独立单根 VModal + mount-to-body，经 Teleport 与父弹窗在 body 内成兄弟节点，
// 不与父弹窗 DOM 嵌套，从根上规避 insertBefore / emitsOptions=null 崩溃。
import { computed, onMounted, reactive, ref } from 'vue'
import { VButton, VEmpty, VModal, VPagination, VSpace } from '@halo-dev/components'
import { assetApi } from '@/api'
import AssetThumb from '@/components/AssetThumb.vue'
import FilterDropdown from '@/components/filter/FilterDropdown.vue'
import ListSkeleton from '@/components/ListSkeleton.vue'
import ListError from '@/components/ListError.vue'
import { useListQuery } from '@/composables/use-list-query'
import type { DecorationAsset } from '@/types'
import { TYPE_LABELS, TYPE_OPTIONS } from '@/utils/decoration'

const props = defineProps<{
  /** 已选装饰（回显选中态） */
  selected?: DecorationAsset[]
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'confirm', assets: DecorationAsset[]): void
}>()

const modal = ref<InstanceType<typeof VModal> | null>(null)

const filters = reactive({
  keyword: '',
  type: undefined as string | undefined,
})

const { items, total, page, size, loading, error, load, search, onPaginationChange } =
  useListQuery<DecorationAsset>(({ page, size }) => {
    const params: Record<string, string | number> = { page, size, status: 'active' }
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.type) params.type = filters.type
    return assetApi.list(params)
  })

// 选中集合（跨页保留，保存完整对象供父组件渲染 chips）
const selectedMap = ref(new Map<string, DecorationAsset>())

onMounted(() => {
  selectedMap.value = new Map((props.selected ?? []).map((asset) => [asset.metadata.name, asset]))
  load()
})

const selectedCount = computed(() => selectedMap.value.size)

function isSelected(asset: DecorationAsset) {
  return selectedMap.value.has(asset.metadata.name)
}

function toggle(asset: DecorationAsset) {
  if (selectedMap.value.has(asset.metadata.name)) {
    selectedMap.value.delete(asset.metadata.name)
  } else {
    selectedMap.value.set(asset.metadata.name, asset)
  }
  // Map 内部变更需手动触发响应
  selectedMap.value = new Map(selectedMap.value)
}

function handleConfirm() {
  emit('confirm', Array.from(selectedMap.value.values()))
  modal.value?.close()
}
</script>

<template>
  <VModal ref="modal" title="选择装饰" :width="720" mount-to-body @close="emit('close')">
    <div class="hip-select__toolbar">
      <SearchInput v-model="filters.keyword" placeholder="搜索名称" @update:model-value="search" />
      <FilterDropdown
        v-model="filters.type"
        label="类型"
        :items="TYPE_OPTIONS"
        @update:model-value="search"
      />
    </div>

    <div class="hip-select__list">
      <ListSkeleton v-if="loading" variant="grid" :count="8" />
      <ListError v-else-if="error" title="装饰加载失败" @retry="load" />
      <VEmpty
        v-else-if="!items.length"
        title="暂无可授予的装饰"
        message="只有启用状态的装饰可授予"
      />
      <div v-else class="hip-select__grid">
        <button
          v-for="asset in items"
          :key="asset.metadata.name"
          type="button"
          class="hip-select__card"
          :class="{ 'hip-select__card--selected': isSelected(asset) }"
          @click="toggle(asset)"
        >
          <div class="hip-select__thumb">
            <AssetThumb
              :type="asset.spec.type"
              :asset="asset.spec.asset"
              :payload="asset.spec.payload"
              :display-name="asset.spec.displayName"
            />
            <span v-if="isSelected(asset)" class="hip-select__check">✓</span>
          </div>
          <div class="hip-select__name" :title="asset.spec.displayName">
            {{ asset.spec.displayName }}
          </div>
          <div class="hip-select__type">{{ TYPE_LABELS[asset.spec.type] }}</div>
        </button>
      </div>
    </div>

    <div class="hip-select__pagination">
      <VPagination
        :page="page"
        :size="size"
        :total="total"
        :size-options="[20, 50]"
        @change="onPaginationChange"
      />
    </div>

    <template #footer>
      <VSpace>
        <VButton type="secondary" :disabled="!selectedCount" @click="handleConfirm">
          确定{{ selectedCount ? `（已选 ${selectedCount} 项）` : '' }}
        </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped>
.hip-select__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--hip-gap-md);
  margin-bottom: var(--hip-gap-md);
  flex-wrap: wrap;
}
/* 固定高度滚动区：骨架屏 / 空态 / 列表都在同一高度内，加载完成不改变弹窗高度，消除闪动 */
.hip-select__list {
  height: 44vh;
  overflow-y: auto;
}
.hip-select__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: var(--hip-gap-md);
}
.hip-select__card {
  border: 1px solid var(--hip-border);
  border-radius: var(--hip-radius-card);
  background: var(--hip-bg-card);
  padding: 0 0 10px;
  cursor: pointer;
  text-align: center;
  overflow: hidden;
  transition:
    border-color var(--hip-transition),
    box-shadow var(--hip-transition);
}
.hip-select__card:hover {
  box-shadow: var(--hip-shadow-hover);
}
.hip-select__card--selected {
  border-color: var(--hip-primary);
  box-shadow: 0 0 0 1px var(--hip-primary);
}
.hip-select__thumb {
  position: relative;
  aspect-ratio: 4 / 3;
  margin-bottom: 8px;
}
.hip-select__check {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--hip-primary);
  color: #fff;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.hip-select__name {
  font-size: var(--hip-font-body);
  font-weight: 500;
  color: var(--hip-text-primary);
  padding: 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hip-select__type {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-faint);
  margin-top: 2px;
}
.hip-select__pagination {
  margin-top: var(--hip-gap-md);
  border-top: 1px solid var(--hip-border-light);
  padding-top: 8px;
}
</style>
