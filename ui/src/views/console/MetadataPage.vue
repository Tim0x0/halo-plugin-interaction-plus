<script lang="ts" setup>
// Console - 装饰元数据页：分类 / 标签 / 稀有度（单页 + 页内小 Tab）
// 排序为拖拽（displayOrder 由拖拽写入）；标签 / 稀有度渲染真实样式 chip；
// 配置类数据全量加载，不分页
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  Dialog,
  Toast,
  VButton,
  VCard,
  VDropdownItem,
  VEmpty,
  VModal,
  VPageHeader,
  VSpace,
  VStatusDot,
  VTabbar,
} from '@halo-dev/components'
import { VueDraggable } from 'vue-draggable-plus'
import { categoryApi, rarityApi, tagApi } from '@/api'
import { useListQuery } from '@/composables/use-list-query'
import RowActions from '@/components/RowActions.vue'
import ListSkeleton from '@/components/ListSkeleton.vue'
import ListError from '@/components/ListError.vue'
import type { DecorationMetadata, MetadataParam } from '@/types'

type MetadataKind = 'categories' | 'tags' | 'rarities'

const KIND_CONFIG: Record<MetadataKind, { label: string; hasColor: boolean }> = {
  categories: { label: '分类', hasColor: false },
  tags: { label: '标签', hasColor: true },
  rarities: { label: '稀有度', hasColor: true },
}

const apis = {
  categories: categoryApi,
  tags: tagApi,
  rarities: rarityApi,
}

const activeKind = ref<MetadataKind>('categories')

const tabItems = (Object.keys(KIND_CONFIG) as MetadataKind[]).map((kind) => ({
  id: kind,
  label: KIND_CONFIG[kind].label,
}))

// 配置类数据全量加载（不分页），便于拖拽排序；
// 加载状态机（乱序守卫防 Tab 快速切换 F1/F2、loading / error）复用 useListQuery
const { items, loading, error, load } = useListQuery<DecorationMetadata>(() =>
  apis[activeKind.value].list({ page: 1, size: 200 }),
)

watch(activeKind, () => {
  orderDirty.value = false
  load()
})

const hasColor = computed(() => KIND_CONFIG[activeKind.value].hasColor)
const kindLabel = computed(() => KIND_CONFIG[activeKind.value].label)

// ── 拖拽排序：displayOrder 越小越靠前，顶部 = 0 ────────
// 拖拽仅改本地顺序并标脏，由用户点「保存排序」显式提交（不即时保存）

const sorting = ref(false)
const orderDirty = ref(false)

function onDragEnd() {
  orderDirty.value = true
}

async function persistOrder() {
  if (sorting.value) return
  sorting.value = true
  const kind = activeKind.value
  try {
    const updates = items.value
      .map((item, index) => ({ item, displayOrder: index }))
      .filter(({ item, displayOrder }) => (item.spec.displayOrder ?? 0) !== displayOrder)
    for (const { item, displayOrder } of updates) {
      await apis[kind].update(item.metadata.name, {
        displayName: item.spec.displayName,
        description: item.spec.description,
        color: item.spec.color,
        enabled: item.spec.enabled,
        displayOrder,
      })
    }
    orderDirty.value = false
    if (updates.length) {
      Toast.success('排序已保存')
    }
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一处理；重新加载恢复服务端顺序
    await load()
  } finally {
    sorting.value = false
  }
}

async function cancelOrder() {
  orderDirty.value = false
  await load()
}

// ── 编辑弹窗（不再暴露排序值输入） ──────────────────────

const editVisible = ref(false)
const editing = ref<DecorationMetadata | undefined>()
const saving = ref(false)
const modal = ref<InstanceType<typeof VModal> | null>(null)
const formRef = ref()

const formState = reactive({
  displayName: '',
  description: '',
  color: '',
  enabled: true,
  externalGrantable: true,
})

function openCreate() {
  editing.value = undefined
  formState.displayName = ''
  formState.description = ''
  formState.color = ''
  formState.enabled = true
  formState.externalGrantable = true
  editVisible.value = true
}

function openEdit(item: DecorationMetadata) {
  editing.value = item
  formState.displayName = item.spec.displayName
  formState.description = item.spec.description || ''
  formState.color = item.spec.color || ''
  formState.enabled = item.spec.enabled !== false
  formState.externalGrantable = item.spec.externalGrantable !== false
  editVisible.value = true
}

function submitForm() {
  formRef.value?.node?.submit?.()
}

async function handleSave() {
  // 弹窗打开时快照 kind，防止保存期间切 Tab 打到错误 API
  const kind = activeKind.value
  saving.value = true
  try {
    const param: MetadataParam = {
      displayName: formState.displayName.trim(),
      description: formState.description.trim() || undefined,
      color: KIND_CONFIG[kind].hasColor ? formState.color || undefined : undefined,
      enabled: formState.enabled,
      // 新建排在最后；编辑保持原值
      displayOrder: editing.value ? editing.value.spec.displayOrder : items.value.length,
      // 仅稀有度提交「允许外部发放」开关
      externalGrantable: kind === 'rarities' ? formState.externalGrantable : undefined,
    }
    if (editing.value) {
      await apis[kind].update(editing.value.metadata.name, param)
    } else {
      await apis[kind].create(param)
    }
    Toast.success('保存成功')
    modal.value?.close()
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    saving.value = false
  }
}

// ── 启停与删除（防连点） ──────────────────────────────

const togglePending = ref(new Set<string>())

async function toggleEnabled(item: DecorationMetadata) {
  const kind = activeKind.value
  const name = item.metadata.name
  if (togglePending.value.has(name)) {
    return
  }
  togglePending.value.add(name)
  try {
    await apis[kind].update(name, {
      displayName: item.spec.displayName,
      description: item.spec.description,
      color: item.spec.color,
      enabled: item.spec.enabled === false,
      displayOrder: item.spec.displayOrder,
    })
    Toast.success(item.spec.enabled === false ? '已启用' : '已停用')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    togglePending.value.delete(name)
  }
}

/** 级联删除：先查引用数，按影响面区分确认文案。 */
async function handleDelete(item: DecorationMetadata) {
  const kind = activeKind.value
  const label = KIND_CONFIG[kind].label
  let usage = 0
  try {
    usage = await apis[kind].usageCount(item.metadata.name)
  } catch {
    usage = -1
  }
  const description =
    usage > 0
      ? `「${item.spec.displayName}」正被 ${usage} 个装饰使用，删除后将从这些装饰移除该${label}。确定删除吗？`
      : usage === 0
        ? `确定删除「${item.spec.displayName}」吗？该${label}当前没有被任何装饰使用。`
        : `确定删除「${item.spec.displayName}」吗？若有装饰正在使用，将一并移除引用。`
  Dialog.warning({
    title: `删除${label}`,
    description,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      await apis[kind].remove(item.metadata.name)
      Toast.success('删除成功')
      await load()
    },
  })
}

onMounted(load)
</script>

<template>
  <VPageHeader title="装饰元数据">
    <template #icon>
      <span class="hip-page-icon">✦</span>
    </template>
    <template #actions>
      <VButton type="secondary" @click="openCreate">新建{{ kindLabel }}</VButton>
    </template>
  </VPageHeader>

  <div class="hip-page">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class="hip-toolbar">
          <VTabbar v-model:active-id="activeKind" :items="tabItems" type="outline" />
          <div class="hip-toolbar__right">
            <template v-if="orderDirty">
              <span class="hip-toolbar__hint">排序未保存</span>
              <VButton size="sm" @click="cancelOrder">取消</VButton>
              <VButton size="sm" type="secondary" :loading="sorting" @click="persistOrder">
                保存排序
              </VButton>
            </template>
            <span v-else class="hip-toolbar__hint">拖拽 ⠿ 调整展示顺序（越靠上越靠前）</span>
          </div>
        </div>
      </template>

      <ListSkeleton v-if="loading" variant="list" />
      <ListError v-else-if="error" :title="`${kindLabel}加载失败`" @retry="load" />
      <VEmpty
        v-else-if="!items.length"
        :title="`暂无${kindLabel}`"
        :message="`点击右上角新建${kindLabel}`"
      />
      <table v-else class="hip-table hip-table--auto">
        <thead>
          <tr>
            <th class="hip-td-fit"></th>
            <th v-if="hasColor" class="hip-td-fit">预览</th>
            <th>名称 / 描述</th>
            <th v-if="hasColor" class="hip-td-fit">颜色</th>
            <th class="hip-td-fit">状态</th>
            <th class="hip-td-fit"></th>
          </tr>
        </thead>
        <VueDraggable
          v-model="items"
          tag="tbody"
          handle=".hip-drag-handle"
          :animation="150"
          @end="onDragEnd"
        >
          <tr v-for="item in items" :key="item.metadata.name">
            <td class="hip-td-fit">
              <span class="hip-drag-handle" title="拖拽排序">⠿</span>
            </td>
            <td v-if="hasColor" class="hip-td-fit">
              <span
                class="hip-meta-chip"
                :style="item.spec.color
                  ? { borderColor: item.spec.color, color: item.spec.color }
                  : undefined"
              >
                {{ item.spec.displayName }}
              </span>
            </td>
            <td>
              <div class="hip-table__main">
                <span class="hip-table__title">
                  <span class="hip-table__title-text" :title="item.spec.displayName">
                    {{ item.spec.displayName }}
                  </span>
                </span>
                <span v-if="item.spec.description" class="hip-table__desc" :title="item.spec.description">
                  {{ item.spec.description }}
                </span>
              </div>
            </td>
            <td v-if="hasColor" class="hip-td-fit">
              <template v-if="item.spec.color">
                <span class="hip-color">
                  <span class="hip-color-dot" :style="{ background: item.spec.color }"></span>
                  <span class="hip-color-value">{{ item.spec.color }}</span>
                </span>
              </template>
            </td>
            <td class="hip-td-fit">
              <VStatusDot
                :state="item.spec.enabled === false ? 'warning' : 'success'"
                :text="item.spec.enabled === false ? '已停用' : '已启用'"
              />
            </td>
            <td class="hip-td-fit">
              <RowActions>
                <VDropdownItem @click="openEdit(item)">编辑</VDropdownItem>
                <VDropdownItem @click="toggleEnabled(item)">
                  {{ item.spec.enabled === false ? '启用' : '停用' }}
                </VDropdownItem>
                <VDropdownItem type="danger" @click="handleDelete(item)">删除</VDropdownItem>
              </RowActions>
            </td>
          </tr>
        </VueDraggable>
      </table>
    </VCard>

    <VModal
      v-if="editVisible"
      ref="modal"
      :title="editing ? `编辑${kindLabel}` : `新建${kindLabel}`"
      :width="520"
      @close="editVisible = false"
    >
      <FormKit ref="formRef" type="form" :actions="false" @submit="handleSave">
        <FormKit v-model="formState.displayName" type="text" label="显示名称" validation="required" />
        <FormKit v-model="formState.description" type="textarea" label="描述" :auto-height="true" />
        <FormKit v-if="hasColor" v-model="formState.color" type="color" label="颜色" />
        <FormKit
          v-if="activeKind === 'rarities'"
          v-model="formState.externalGrantable"
          type="switch"
          label="允许外部发放"
          help="关闭后，外部插件无法经发奖 API 发放该稀有度下的装饰（用于保护传说 / 限定等珍贵装饰）"
        />
        <FormKit v-model="formState.enabled" type="switch" label="启用" />
      </FormKit>
      <template #footer>
        <VSpace>
          <VButton :loading="saving" type="secondary" @click="submitForm">保存</VButton>
          <VButton @click="modal?.close()">取消</VButton>
        </VSpace>
      </template>
    </VModal>
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
.hip-toolbar__right {
  display: flex;
  align-items: center;
  gap: var(--hip-gap-sm);
}
.hip-drag-handle {
  cursor: grab;
  color: var(--hip-text-faint);
  font-size: 14px;
  user-select: none;
  padding: 4px 2px;
}
.hip-drag-handle:active {
  cursor: grabbing;
}
.hip-meta-chip {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--hip-border);
  border-radius: var(--hip-radius-chip);
  padding: 2px 8px;
  font-size: var(--hip-font-caption);
  color: var(--hip-text-secondary);
  background: var(--hip-bg-card);
  white-space: nowrap;
}
.hip-color {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.hip-color-dot {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.08);
}
.hip-color-value {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-faint);
  font-family: monospace;
}
</style>
