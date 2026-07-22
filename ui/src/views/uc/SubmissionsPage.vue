<script lang="ts" setup>
// UC - 我的投稿：创建、编辑、删除自己的未启用草稿
// 布局对齐 Halo 官方 UC 页面：VPageHeader + VCard 灰底 header + VEntityContainer
import { onMounted, ref } from 'vue'
import {
  Dialog,
  Toast,
  VButton,
  VCard,
  VDropdownItem,
  VEmpty,
  VPageHeader,
  VPagination,
  VStatusDot,
  VTag,
} from '@halo-dev/components'
import { ucApi } from '@/api'
import AssetEditModal from '@/components/AssetEditModal.vue'
import AssetThumb from '@/components/AssetThumb.vue'
import RowActions from '@/components/RowActions.vue'
import ListSkeleton from '@/components/ListSkeleton.vue'
import ListError from '@/components/ListError.vue'
import { useListQuery } from '@/composables/use-list-query'
import type { DecorationAsset, MetadataOptions } from '@/types'
import { formatDateTime, STATUS_LABELS, STATUS_STATES, TYPE_LABELS } from '@/utils/decoration'

const { items, total, page, size, loading, error, load, onPaginationChange, reloadAfterRemove } =
  useListQuery<DecorationAsset>(({ page, size }) => ucApi.listSubmissions({ page, size }))

const metadataOptions = ref<MetadataOptions | null>(null)

async function loadMetadataOptions() {
  try {
    metadataOptions.value = await ucApi.metadataOptions()
  } catch {
    // 元数据加载失败不阻断列表
  }
}

const editVisible = ref(false)
const editing = ref<DecorationAsset | undefined>()

function openCreate() {
  editing.value = undefined
  editVisible.value = true
}

function openEdit(asset: DecorationAsset) {
  if (asset.spec.status !== 'draft') {
    Toast.warning('该投稿已被处理，不能再编辑')
    return
  }
  editing.value = asset
  editVisible.value = true
}

function handleDelete(asset: DecorationAsset) {
  Dialog.warning({
    title: '删除投稿',
    description: `确定删除草稿「${asset.spec.displayName}」吗？`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      await ucApi.deleteSubmission(asset.metadata.name)
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
  <VPageHeader title="我的投稿">
    <template #icon>
      <span class="hip-page-icon">✦</span>
    </template>
    <template #actions>
      <VButton type="secondary" @click="openCreate">创建草稿</VButton>
    </template>
  </VPageHeader>

  <div class="hip-page">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class="hip-toolbar">
          <span class="hip-toolbar__hint">
            投稿的装饰草稿由管理员审核：启用即通过；驳回会删除草稿并通知你。
          </span>
        </div>
      </template>

      <ListSkeleton v-if="loading" variant="list" />
      <ListError v-else-if="error" title="投稿列表加载失败" @retry="load" />
      <VEmpty v-else-if="!items.length" title="暂无投稿" message="点击右上角创建装饰草稿" />
      <table v-else class="hip-table hip-table--auto">
        <thead>
          <tr>
            <th class="hip-td-fit"></th>
            <th>装饰</th>
            <th class="hip-td-fit">状态</th>
            <th class="hip-td-fit">创建时间</th>
            <th class="hip-td-fit"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="asset in items" :key="asset.metadata.name">
            <td class="hip-td-fit">
              <AssetThumb
                size="sm"
                :type="asset.spec.type"
                :asset="asset.spec.asset"
                :payload="asset.spec.payload"
                :display-name="asset.spec.displayName"
              />
            </td>
            <td>
              <div class="hip-table__main">
                <span class="hip-table__title">
                  <span class="hip-table__title-text" :title="asset.spec.displayName">
                    {{ asset.spec.displayName }}
                  </span>
                  <VTag>{{ TYPE_LABELS[asset.spec.type] }}</VTag>
                </span>
                <span v-if="asset.spec.description" class="hip-table__desc" :title="asset.spec.description">
                  {{ asset.spec.description }}
                </span>
              </div>
            </td>
            <td class="hip-td-fit">
              <VStatusDot
                :state="STATUS_STATES[asset.spec.status]"
                :text="asset.spec.status === 'draft' ? '待处理' : STATUS_LABELS[asset.spec.status]"
              />
            </td>
            <td class="hip-td-fit">{{ formatDateTime(asset.metadata.creationTimestamp) }}</td>
            <td class="hip-td-fit">
              <RowActions v-if="asset.spec.status === 'draft'">
                <VDropdownItem @click="openEdit(asset)">编辑</VDropdownItem>
                <VDropdownItem type="danger" @click="handleDelete(asset)">删除</VDropdownItem>
              </RowActions>
              <RowActions v-else />
            </td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <VPagination
          :page="page"
          :size="size"
          :total="total"
          :size-options="[20, 50]"
          @change="onPaginationChange"
        />
      </template>
    </VCard>

    <AssetEditModal
      v-if="editVisible"
      :asset="editing"
      mode="uc"
      :metadata-options="metadataOptions"
      @close="editVisible = false"
      @saved="load"
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
.hip-toolbar__hint {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
}
</style>
