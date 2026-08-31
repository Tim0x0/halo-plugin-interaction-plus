<script lang="ts" setup>
// Console - 装饰授予页：列表、筛选、撤销（含原因）、发起授予
// 布局对齐 Halo 官方：工具栏在 VCard header 灰底；列表用固定列宽表格（非 VEntityContainer，
// 授予记录要跨行对齐用户 / 装饰 / 时间三组信息），窄屏由外层容器横向滚动；
// 角色快照显示角色显示名，已撤销记录不渲染操作菜单
import { computed, onMounted, reactive, ref } from 'vue'
import {
  VAvatar,
  VButton,
  VCard,
  VDropdownItem,
  VEmpty,
  VPageHeader,
  VPagination,
  VStatusDot,
  VTag,
} from '@halo-dev/components'
import { grantApi } from '@/api'
import GrantModal from '@/components/GrantModal.vue'
import RevokeModal from '@/components/RevokeModal.vue'
import AssetThumb from '@/components/AssetThumb.vue'
import RowActions from '@/components/RowActions.vue'
import FilterDropdown from '@/components/filter/FilterDropdown.vue'
import FilterChips from '@/components/filter/FilterChips.vue'
import type { FilterChip } from '@/components/filter/FilterChips.vue'
import ListSkeleton from '@/components/ListSkeleton.vue'
import ListError from '@/components/ListError.vue'
import { useListQuery } from '@/composables/use-list-query'
import type { DecorationAsset, GrantView } from '@/types'
import {
  formatDateTime,
  GRANT_STATUS_STATES,
  grantSourceLabel,
  TYPE_LABELS,
} from '@/utils/decoration'

const filters = reactive({
  userName: '',
  revoked: undefined as string | undefined,
})

const REVOKED_ITEMS = [
  { label: '未撤销', value: 'false' },
  { label: '已撤销', value: 'true' },
]

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
} = useListQuery<GrantView>(({ page, size }) => {
  const params: Record<string, string | number> = { page, size }
  if (filters.userName) params.userName = filters.userName
  if (filters.revoked !== undefined) params.revoked = filters.revoked
  return grantApi.list(params)
})

const chips = computed<FilterChip[]>(() => {
  const result: FilterChip[] = []
  if (filters.userName) {
    result.push({ key: 'userName', label: `用户：${filters.userName}` })
  }
  if (filters.revoked !== undefined) {
    const item = REVOKED_ITEMS.find((option) => option.value === filters.revoked)
    result.push({ key: 'revoked', label: `撤销状态：${item?.label ?? filters.revoked}` })
  }
  return result
})

function removeChip(key: string) {
  if (key === 'userName') filters.userName = ''
  if (key === 'revoked') filters.revoked = undefined
  search()
}

function clearChips() {
  filters.userName = ''
  filters.revoked = undefined
  search()
}

/** 来源列 title：完整来源 + 可选原因（列内截断后靠悬停看全）。 */
function sourceTitle(view: GrantView): string {
  const lines = [grantSourceLabel(view)]
  if (view.grant.spec.reason) {
    lines.push(`原因：${view.grant.spec.reason}`)
  }
  return lines.join('\n')
}

const grantVisible = ref(false)
const revokeTarget = ref<GrantView | null>(null)
/** 再次授予目标（已撤销记录）：预填原用户与原装饰 */
const regrantTarget = ref<GrantView | null>(null)

const regrantAsset = computed<DecorationAsset | undefined>(() => {
  const view = regrantTarget.value
  if (!view || !view.assetType) return undefined
  return {
    metadata: { name: view.grant.spec.assetName },
    spec: {
      type: view.assetType,
      displayName: view.assetDisplayName,
      status: 'active',
      asset: view.asset,
      payload: view.payload,
    },
  }
})

/** 撤销后刷新：筛选「未撤销」时该条会离开当前页，需考虑回退页码。 */
function handleRevoked() {
  if (filters.revoked === 'false') {
    reloadAfterRemove(1)
  } else {
    load()
  }
}

onMounted(load)
</script>

<template>
  <VPageHeader title="装饰授予">
    <template #icon>
      <span class="hip-page-icon">✦</span>
    </template>
    <template #actions>
      <VButton type="secondary" @click="grantVisible = true">授予装饰</VButton>
    </template>
  </VPageHeader>

  <div class="hip-page">
    <VCard :body-class="['!p-0']">
      <!-- 工具栏：官方灰底 header，左搜索右筛选 -->
      <template #header>
        <div class="hip-toolbar">
          <SearchInput
            v-model="filters.userName"
            placeholder="按用户名筛选"
            @update:model-value="search"
          />
          <div class="hip-toolbar__filters">
            <FilterDropdown
              v-model="filters.revoked"
              label="撤销状态"
              :items="REVOKED_ITEMS"
              @update:model-value="search"
            />
          </div>
        </div>
      </template>
      <FilterChips :chips="chips" @remove="removeChip" @clear="clearChips" />

      <ListSkeleton v-if="loading" variant="list" />
      <ListError v-else-if="error" title="授予记录加载失败" @retry="load" />
      <VEmpty v-else-if="!items.length" title="暂无授予记录" message="点击右上角授予装饰" />
      <div
        v-else
        class="hip-table-scroll"
        role="region"
        aria-label="装饰授予记录，可横向滚动查看完整信息"
        tabindex="0"
      >
        <table class="hip-table hip-grants-table">
          <colgroup>
            <col class="hip-grants-table__col--user" />
            <col class="hip-grants-table__col--asset" />
            <col class="hip-grants-table__col--source" />
            <col class="hip-grants-table__col--expires" />
            <col class="hip-grants-table__col--status" />
            <col class="hip-grants-table__col--granted" />
            <col class="hip-grants-table__col--actions" />
          </colgroup>
          <thead>
            <tr>
              <th>用户</th>
              <th>装饰</th>
              <th>来源</th>
              <th>有效期</th>
              <th>状态</th>
              <th>授予时间</th>
              <th class="hip-table__sticky-end" aria-label="操作"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="view in items" :key="view.grant.metadata.name">
              <!-- 用户：头像 + 显示名（@用户名辅助），长名称只在固定用户列内省略。 -->
              <td>
                <div class="hip-user">
                  <VAvatar
                    class="hip-user__avatar"
                    :src="view.userAvatar"
                    :alt="view.userDisplayName || view.grant.spec.userName"
                    width="28px"
                    height="28px"
                    circle
                  />
                  <span class="hip-user__text">
                    <span
                      class="hip-user__name"
                      :title="view.userDisplayName || view.grant.spec.userName"
                      >{{ view.userDisplayName || view.grant.spec.userName }}</span
                    >
                    <span class="hip-user__handle" :title="`@${view.grant.spec.userName}`"
                      >@{{ view.grant.spec.userName }}</span
                    >
                  </span>
                </div>
              </td>
              <td>
                <div class="hip-grants-table__asset">
                  <AssetThumb
                    size="sm"
                    :type="view.assetType"
                    :asset="view.asset"
                    :payload="view.payload"
                    :display-name="view.assetDisplayName"
                  />
                  <div class="hip-table__main">
                    <span class="hip-table__title">
                      <span class="hip-table__title-text" :title="view.assetDisplayName">
                        {{ view.assetDisplayName }}
                      </span>
                      <VTag v-if="view.assetType">{{ TYPE_LABELS[view.assetType] }}</VTag>
                    </span>
                  </div>
                </div>
              </td>
              <td>
                <span class="hip-table__cell-ellipsis" :title="sourceTitle(view)">
                  {{ grantSourceLabel(view) }}
                </span>
              </td>
              <td>
                <span
                  class="hip-table__cell-ellipsis"
                  :title="
                    view.grant.spec.expiresAt
                      ? `${formatDateTime(view.grant.spec.expiresAt)} 到期`
                      : '永久有效'
                  "
                >
                  {{
                    view.grant.spec.expiresAt
                      ? `${formatDateTime(view.grant.spec.expiresAt)} 到期`
                      : '永久有效'
                  }}
                </span>
              </td>
              <td>
                <VStatusDot
                  :state="GRANT_STATUS_STATES[view.status]?.state ?? 'success'"
                  :text="GRANT_STATUS_STATES[view.status]?.text ?? view.status"
                />
              </td>
              <td>
                <span
                  class="hip-table__cell-ellipsis"
                  :title="formatDateTime(view.grant.spec.grantedAt)"
                >
                  {{ formatDateTime(view.grant.spec.grantedAt) }}
                </span>
              </td>
              <td class="hip-table__sticky-end hip-grants-table__actions">
                <RowActions>
                  <!-- 有效→撤销；已过期 / 已撤销（均已失效）→再次授予 -->
                  <VDropdownItem
                    v-if="view.status === 'active'"
                    type="danger"
                    @click="revokeTarget = view"
                  >
                    撤销
                  </VDropdownItem>
                  <VDropdownItem v-else @click="regrantTarget = view">再次授予</VDropdownItem>
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

    <GrantModal v-if="grantVisible" @close="grantVisible = false" @granted="load" />
    <!-- 再次授予：预填原用户与原装饰 -->
    <GrantModal
      v-if="regrantTarget"
      :asset="regrantAsset"
      :initial-user-names="[regrantTarget.grant.spec.userName]"
      @close="regrantTarget = null"
      @granted="load"
    />
    <RevokeModal
      v-if="revokeTarget"
      :grant="revokeTarget.grant"
      :asset-label="revokeTarget.assetDisplayName"
      @close="revokeTarget = null"
      @revoked="handleRevoked"
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
/* 官方工具栏形态：VCard header 灰底 */
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

/* 授予记录的各类元信息长度差异很大，使用固定列规格避免用户名 / 来源决定整表宽度。 */
.hip-grants-table {
  min-width: 1140px;
  table-layout: fixed;
}
.hip-grants-table__col--user {
  width: 220px;
}
.hip-grants-table__col--source {
  width: 160px;
}
.hip-grants-table__col--expires {
  width: 164px;
}
.hip-grants-table__col--status {
  width: 92px;
}
.hip-grants-table__col--granted {
  width: 144px;
}
.hip-grants-table__col--actions {
  width: 48px;
}
.hip-grants-table tbody tr,
.hip-grants-table td {
  height: 64px;
}
.hip-grants-table__asset {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.hip-grants-table__asset .hip-table__main,
.hip-grants-table__asset .hip-table__title,
.hip-grants-table__asset .hip-table__title-text {
  min-width: 0;
  max-width: 100%;
}
.hip-grants-table__actions {
  text-align: center;
}

/* 用户列：头像 + 显示名 + @用户名。头像用官方 VAvatar（自带加载失败首字母兜底）。 */
.hip-user {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.hip-user__avatar {
  flex: none;
}
.hip-user__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}
.hip-user__name {
  font-size: var(--hip-font-title);
  font-weight: 500;
  color: var(--hip-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hip-user__handle {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
