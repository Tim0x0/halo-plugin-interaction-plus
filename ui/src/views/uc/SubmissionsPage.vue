<script lang="ts" setup>
// UC - 我的投稿：创建、编辑、删除自己的未启用草稿
// 布局对齐 Halo 官方 UC 页面：VPageHeader + VCard 灰底 header；
// 列表用固定列宽表格（非 VEntityContainer），窄屏由外层容器横向滚动
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
import {
  formatDateTime,
  metadataLabel as metadataLabelOf,
  rarityColor as rarityColorOf,
  restTagCount,
  shownTags,
  STATUS_LABELS,
  STATUS_STATES,
  tagChipStyle,
  TYPE_LABELS,
} from '@/utils/decoration'
import type { MetadataOptionKind } from '@/utils/decoration'

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

const metadataLabel = (kind: MetadataOptionKind, name?: string) =>
  metadataLabelOf(metadataOptions.value, kind, name)

const rarityColor = (name?: string) => rarityColorOf(metadataOptions.value, name)

/** 标签列 hover 全称（chip 超出列宽时截断，靠 title 兜底）。 */
const tagTitle = (names: string[]) => names.map((name) => metadataLabel('tags', name)).join('、')

/** 标签 chip 配色：配置色只作描边，文字保持中性灰。 */
const tagStyle = (name: string) => tagChipStyle(metadataOptions.value, name)

const editVisible = ref(false)
const editing = ref<DecorationAsset | undefined>()

function openCreate() {
  editing.value = undefined
  editVisible.value = true
}

function openEdit(asset: DecorationAsset) {
  if (asset.spec.status !== 'draft') {
    Toast.warning('投稿已处理，不能再编辑')
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
      <div
        v-else
        class="hip-table-scroll"
        role="region"
        aria-label="我的投稿列表，可横向滚动查看完整信息"
        tabindex="0"
      >
        <table class="hip-table hip-submissions-table">
          <colgroup>
            <col class="hip-submissions-table__col--asset" />
            <col class="hip-submissions-table__col--category" />
            <col class="hip-submissions-table__col--tags" />
            <col class="hip-submissions-table__col--rarity" />
            <col class="hip-submissions-table__col--status" />
            <col class="hip-submissions-table__col--created" />
            <col class="hip-submissions-table__col--actions" />
          </colgroup>
          <thead>
            <tr>
              <th>装饰</th>
              <th>分类</th>
              <th>标签</th>
              <th>稀有度</th>
              <th>状态</th>
              <th>创建时间</th>
              <th class="hip-table__sticky-end" aria-label="操作"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="asset in items" :key="asset.metadata.name">
              <td>
                <div class="hip-submissions-table__asset">
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
                  :text="
                    asset.spec.status === 'draft' ? '待处理' : STATUS_LABELS[asset.spec.status]
                  "
                />
              </td>
              <td>
                <span
                  class="hip-table__cell-ellipsis"
                  :title="formatDateTime(asset.metadata.creationTimestamp)"
                >
                  {{ formatDateTime(asset.metadata.creationTimestamp) }}
                </span>
              </td>
              <td class="hip-table__sticky-end hip-submissions-table__actions">
                <RowActions v-if="asset.spec.status === 'draft'">
                  <VDropdownItem @click="openEdit(asset)">编辑</VDropdownItem>
                  <VDropdownItem type="danger" @click="handleDelete(asset)">删除</VDropdownItem>
                </RowActions>
                <RowActions v-else />
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

/* UC 保留完整状态与时间信息；窄屏通过横向滑动查看，主列不被压成一小条。 */
.hip-submissions-table {
  min-width: 1024px;
  table-layout: fixed;
}
.hip-submissions-table__col--category {
  width: 112px;
}
.hip-submissions-table__col--tags {
  width: 232px;
}
.hip-submissions-table__col--rarity {
  width: 104px;
}
.hip-submissions-table__col--status {
  width: 92px;
}
.hip-submissions-table__col--created {
  width: 144px;
}
.hip-submissions-table__col--actions {
  width: 48px;
}
.hip-submissions-table tbody tr,
.hip-submissions-table td {
  height: 64px;
}
.hip-submissions-table__asset {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.hip-submissions-table__asset .hip-table__main,
.hip-submissions-table__asset .hip-table__title,
.hip-submissions-table__asset .hip-table__title-text,
.hip-submissions-table__asset .hip-table__desc {
  min-width: 0;
  max-width: 100%;
}
.hip-submissions-table__actions {
  text-align: center;
}
</style>
