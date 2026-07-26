<script lang="ts" setup>
// Console - 身份标识页：角色到身份标识映射管理
// 排序为拖拽（priority 由拖拽写入，不暴露数字输入）；
// 角色一律展示显示名；配置类数据全量加载，不分页
import { onMounted, reactive, ref, watch } from 'vue'
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
  VTag,
} from '@halo-dev/components'
import { VueDraggable } from 'vue-draggable-plus'
import { identityMarkApi } from '@/api'
import { useListQuery } from '@/composables/use-list-query'
import RowActions from '@/components/RowActions.vue'
import ListSkeleton from '@/components/ListSkeleton.vue'
import ListError from '@/components/ListError.vue'
import type { IdentityMarkMappingView } from '@/types'

// 配置类数据全量加载（角色数量级，不分页），便于拖拽排序；
// 加载状态机（乱序守卫 / loading / error）复用 useListQuery
const { items, loading, error, load } = useListQuery<IdentityMarkMappingView>(() =>
  identityMarkApi.list({ page: 1, size: 200 }),
)

// 图标 404 兜底：裂图回落文字牌（对齐 runtime「mark 图标失败切文本牌」）；
// 按图标 URL 记失效，编辑换图后新地址自然重试，无需手动清理
const brokenIcons = ref(new Set<string>())

// ── 拖拽排序：priority 越大越靠前，顶部 = 最大 ──────────
// 拖拽仅改本地顺序并标脏，由用户点「保存排序」显式提交（不即时保存）

const sorting = ref(false)
const orderDirty = ref(false)

function onDragEnd() {
  orderDirty.value = true
}

async function persistOrder() {
  if (sorting.value) return
  sorting.value = true
  try {
    const list = items.value
    const updates = list
      .map((view, index) => ({ view, priority: list.length - 1 - index }))
      .filter(({ view, priority }) => (view.mapping.spec.priority ?? 0) !== priority)
    for (const { view, priority } of updates) {
      const spec = view.mapping.spec
      await identityMarkApi.update(view.mapping.metadata.name, {
        displayName: spec.displayName,
        icon: spec.icon,
        color: spec.color,
        priority,
        enabled: spec.enabled,
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

// ── 编辑弹窗（不再暴露优先级输入） ──────────────────────

const editVisible = ref(false)
const editing = ref<IdentityMarkMappingView | undefined>()
const saving = ref(false)
const modal = ref<InstanceType<typeof VModal> | null>(null)
const formRef = ref()

const formState = reactive({
  roleName: '' as string | string[],
  displayName: '',
  icon: '',
  color: '#3b82f6',
  enabled: true,
})

// 图标来源：图标库（Halo iconify 控件，format=dataurl 物化为自包含图片地址）/ 上传图片。
// 两者写同一个 icon 字段，落库形态一致（均为 img src 可用的地址），消费端无感知
const iconSource = ref<'iconify' | 'upload'>('iconify')

// 切换来源时清掉与新来源不匹配的旧值，避免图标库值出现在附件输入框（反之亦然）
watch(iconSource, (source) => {
  const isDataUrl = formState.icon.startsWith('data:')
  if (source === 'iconify' && formState.icon && !isDataUrl) {
    formState.icon = ''
  } else if (source === 'upload' && isDataUrl) {
    formState.icon = ''
  }
})

function openCreate() {
  editing.value = undefined
  formState.roleName = ''
  formState.displayName = ''
  formState.icon = ''
  formState.color = '#3b82f6'
  formState.enabled = true
  iconSource.value = 'iconify'
  editVisible.value = true
}

function openEdit(view: IdentityMarkMappingView) {
  editing.value = view
  formState.roleName = view.mapping.spec.roleName
  formState.displayName = view.mapping.spec.displayName
  formState.icon = view.mapping.spec.icon || ''
  formState.color = view.mapping.spec.color || '#3b82f6'
  formState.enabled = view.mapping.spec.enabled !== false
  // 既有值按形态归位来源：data URL 归图标库，其余（附件地址）归上传；空值默认图标库
  iconSource.value = !formState.icon || formState.icon.startsWith('data:') ? 'iconify' : 'upload'
  editVisible.value = true
}

function submitForm() {
  formRef.value?.node?.submit?.()
}

async function handleSave() {
  const roleName = Array.isArray(formState.roleName) ? formState.roleName[0] : formState.roleName
  if (!editing.value && !roleName) {
    Toast.warning('请选择角色')
    return
  }
  saving.value = true
  try {
    const param = {
      roleName: roleName || undefined,
      displayName: formState.displayName.trim(),
      icon: formState.icon || undefined,
      color: formState.color || undefined,
      // 新建排在最前（当前最大 + 1）；编辑保持原值
      priority: editing.value
        ? editing.value.mapping.spec.priority
        : Math.max(0, ...items.value.map((view) => view.mapping.spec.priority ?? 0)) + 1,
      enabled: formState.enabled,
    }
    if (editing.value) {
      await identityMarkApi.update(editing.value.mapping.metadata.name, param)
    } else {
      await identityMarkApi.create(param)
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

async function toggleEnabled(view: IdentityMarkMappingView) {
  const name = view.mapping.metadata.name
  if (togglePending.value.has(name)) {
    return
  }
  togglePending.value.add(name)
  const spec = view.mapping.spec
  try {
    await identityMarkApi.update(name, {
      displayName: spec.displayName,
      icon: spec.icon,
      color: spec.color,
      priority: spec.priority,
      enabled: spec.enabled === false,
    })
    Toast.success(spec.enabled === false ? '已启用' : '已停用')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    togglePending.value.delete(name)
  }
}

function handleDelete(view: IdentityMarkMappingView) {
  Dialog.warning({
    title: '删除身份标识映射',
    description: `确定删除「${view.mapping.spec.displayName}」吗？删除后对应角色的用户将不再展示该身份标识。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      await identityMarkApi.remove(view.mapping.metadata.name)
      Toast.success('删除成功')
      await load()
    },
  })
}

onMounted(load)
</script>

<template>
  <VPageHeader title="身份标识">
    <template #icon>
      <span class="hip-page-icon">✦</span>
    </template>
    <template #actions>
      <VButton type="secondary" @click="openCreate">新建映射</VButton>
    </template>
  </VPageHeader>

  <div class="hip-page">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class="hip-toolbar">
          <span class="hip-toolbar__hint">
            身份标识来自 Halo 原生角色，拖拽 ⠿
            调整展示顺序（越靠上越优先），不进入用户库存、不占佩戴槽位。
          </span>
          <div v-if="orderDirty" class="hip-toolbar__right">
            <span class="hip-toolbar__hint">排序未保存</span>
            <VButton size="sm" @click="cancelOrder">取消</VButton>
            <VButton size="sm" type="secondary" :loading="sorting" @click="persistOrder">
              保存排序
            </VButton>
          </div>
        </div>
      </template>

      <ListSkeleton v-if="loading" variant="list" />
      <ListError v-else-if="error" title="身份标识加载失败" @retry="load" />
      <VEmpty v-else-if="!items.length" title="暂无身份标识映射" message="点击右上角新建映射" />
      <table v-else class="hip-table hip-table--auto">
        <thead>
          <tr>
            <th class="hip-td-fit"></th>
            <th class="hip-td-fit">预览</th>
            <th>名称 / 角色</th>
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
          <tr v-for="view in items" :key="view.mapping.metadata.name">
            <td class="hip-td-fit">
              <span class="hip-drag-handle" title="拖拽排序">⠿</span>
            </td>
            <td class="hip-td-fit">
              <!-- 有图标只显图标，无图标 / 图标加载失败显文字牌；统一尺寸不撑高行 -->
              <img
                v-if="view.mapping.spec.icon && !brokenIcons.has(view.mapping.spec.icon)"
                class="hip-mark-icon"
                :src="view.mapping.spec.icon"
                :alt="view.mapping.spec.displayName"
                :title="view.mapping.spec.displayName"
                @error="brokenIcons.add(view.mapping.spec.icon)"
              />
              <span
                v-else
                class="hip-mark-preview"
                :style="{ borderColor: view.mapping.spec.color, color: view.mapping.spec.color }"
              >
                {{ view.mapping.spec.displayName }}
              </span>
            </td>
            <td>
              <div class="hip-table__main">
                <span class="hip-table__title">
                  <span class="hip-table__title-text" :title="view.mapping.spec.displayName">
                    {{ view.mapping.spec.displayName }}
                  </span>
                  <VTag v-if="!view.roleExists" theme="danger">角色不存在</VTag>
                </span>
                <span
                  class="hip-table__desc"
                  :title="`角色：${view.roleDisplayName ?? view.mapping.spec.roleName}`"
                >
                  角色：{{ view.roleDisplayName ?? view.mapping.spec.roleName }}
                </span>
              </div>
            </td>
            <td class="hip-td-fit">
              <VStatusDot
                :state="view.mapping.spec.enabled === false ? 'warning' : 'success'"
                :text="view.mapping.spec.enabled === false ? '已停用' : '已启用'"
              />
            </td>
            <td class="hip-td-fit">
              <RowActions>
                <VDropdownItem @click="openEdit(view)">编辑</VDropdownItem>
                <VDropdownItem @click="toggleEnabled(view)">
                  {{ view.mapping.spec.enabled === false ? '启用' : '停用' }}
                </VDropdownItem>
                <VDropdownItem type="danger" @click="handleDelete(view)">删除</VDropdownItem>
              </RowActions>
            </td>
          </tr>
        </VueDraggable>
      </table>
    </VCard>

    <VModal
      v-if="editVisible"
      ref="modal"
      :title="editing ? '编辑身份标识映射' : '新建身份标识映射'"
      :width="520"
      @close="editVisible = false"
    >
      <FormKit ref="formRef" type="form" :actions="false" @submit="handleSave">
        <FormKit
          v-if="!editing"
          v-model="formState.roleName"
          type="roleSelect"
          label="Halo 角色"
          help="创建后不可修改；一个角色只能映射一次"
        />
        <FormKit
          v-else
          type="text"
          label="Halo 角色"
          :model-value="editing.roleDisplayName ?? editing.mapping.spec.roleName"
          disabled
        />
        <FormKit
          v-model="formState.displayName"
          type="text"
          label="身份名称"
          validation="required"
        />
        <FormKit
          v-model="iconSource"
          type="radio"
          label="图标来源"
          :options="[
            { label: '图标库', value: 'iconify' },
            { label: '上传图片', value: 'upload' },
          ]"
        />
        <FormKit
          v-if="iconSource === 'iconify'"
          v-model="formState.icon"
          type="iconify"
          label="图标"
          format="dataurl"
          :value-only="true"
          help="从 Iconify 图标库选择（可设颜色）；选择时需联网，保存后自包含不再依赖外部服务"
        />
        <FormKit
          v-else
          v-model="formState.icon"
          type="attachment"
          label="图标"
          :accepts="['image/*']"
        />
        <FormKit
          v-model="formState.color"
          type="color"
          label="颜色"
          :help="
            formState.icon
              ? '已设置图标：图标本身不受此颜色影响（图标要上色请在图标库选择器内设色，或上传已上色的图片）；此颜色仅在图标加载失败、回落为文字牌时生效'
              : '文字牌（无图标时的展示形态）的边框与文字颜色'
          "
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
.hip-mark-preview {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--hip-border);
  border-radius: 3px;
  padding: 2px 6px;
  font-size: var(--hip-font-caption);
  color: var(--hip-text-secondary);
  white-space: nowrap;
}
.hip-mark-preview img {
  width: 14px;
  height: 14px;
  object-fit: contain;
}
.hip-mark-icon {
  width: 22px;
  height: 22px;
  object-fit: contain;
  display: block;
}
</style>
