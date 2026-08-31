<script lang="ts" setup>
// Console - 身份标识页：角色到身份标识映射管理
// 排序为拖拽（priority 由拖拽写入，不暴露数字输入）；
// 角色一律展示显示名；配置类数据全量加载，不分页
import { onMounted, reactive, ref } from 'vue'
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
import type {
  IdentityMarkMappingSpec,
  IdentityMarkMappingView,
  IdentityMarkMode as MarkMode,
} from '@/types'

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
      // 后端 applyParam 是全量覆盖，未带的字段会被写成 null——
      // 这类「只改一个字段」的更新必须把其余字段原样回传
      await identityMarkApi.update(view.mapping.metadata.name, {
        displayName: spec.displayName,
        displayMode: spec.displayMode,
        icon: spec.icon,
        image: spec.image,
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

// ── 编辑弹窗（优先级由列表拖拽管理） ──────────────────────

const editVisible = ref(false)
const editing = ref<IdentityMarkMappingView | undefined>()
const saving = ref(false)
const modal = ref<InstanceType<typeof VModal> | null>(null)
const formRef = ref()

const formState = reactive({
  roleName: '' as string | string[],
  displayName: '',
  // 三形态各占独立字段，切形态只换当前生效项
  iconGlyph: '',
  imageUrl: '',
  color: '',
  enabled: true,
})

/** 展示形态三选一：text / icon / image。 */
const markMode = ref<MarkMode>('text')

/** 归位展示形态：显式值优先，否则按字段非空推断。 */
function resolveMarkMode(spec: IdentityMarkMappingSpec): MarkMode {
  if (spec.displayMode) {
    return spec.displayMode
  }
  if (spec.image) {
    return 'image'
  }
  return spec.icon ? 'icon' : 'text'
}

/** 当前形态生效的图（icon → 图标库字形，image → 上传图）；文字形态为空。 */
function markSource(spec: IdentityMarkMappingSpec): string | undefined {
  const mode = resolveMarkMode(spec)
  if (mode === 'icon') {
    return spec.icon || undefined
  }
  return mode === 'image' ? spec.image || undefined : undefined
}

/** 该映射是否以图渲染（图标 / 图片形态且确有图）；否则渲染文字牌。 */
function showsIcon(spec: IdentityMarkMappingSpec): boolean {
  return !!markSource(spec)
}

function openCreate() {
  editing.value = undefined
  formState.roleName = ''
  formState.displayName = ''
  formState.iconGlyph = ''
  formState.imageUrl = ''
  formState.color = ''
  formState.enabled = true
  markMode.value = 'text'
  editVisible.value = true
}

function openEdit(view: IdentityMarkMappingView) {
  editing.value = view
  const spec = view.mapping.spec
  formState.roleName = spec.roleName
  formState.displayName = spec.displayName
  // 三个字段各填各的，与当前选中形态无关——文字形态下图标 / 图片同样留着，切回去就在
  formState.iconGlyph = spec.icon || ''
  formState.imageUrl = spec.image || ''
  formState.color = spec.color || ''
  formState.enabled = spec.enabled !== false
  markMode.value = resolveMarkMode(spec)
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
  const mode = markMode.value
  if (mode === 'icon' && !formState.iconGlyph) {
    Toast.warning('请选择图标')
    return
  }
  if (mode === 'image' && !formState.imageUrl) {
    Toast.warning('请上传图片')
    return
  }
  saving.value = true
  try {
    const param = {
      roleName: roleName || undefined,
      displayName: formState.displayName.trim(),
      displayMode: mode,
      icon: formState.iconGlyph || undefined,
      image: formState.imageUrl || undefined,
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
      displayMode: spec.displayMode,
      icon: spec.icon,
      image: spec.image,
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
      <div
        v-else
        class="hip-table-scroll"
        role="region"
        aria-label="身份标识列表，可横向滚动查看完整信息"
        tabindex="0"
      >
        <table class="hip-table hip-identity-table">
          <colgroup>
            <col class="hip-identity-table__col--drag" />
            <col class="hip-identity-table__col--preview" />
            <col class="hip-identity-table__col--main" />
            <col class="hip-identity-table__col--status" />
            <col class="hip-identity-table__col--actions" />
          </colgroup>
          <thead>
            <tr>
              <th class="hip-table__sticky-start" aria-label="拖拽排序"></th>
              <th>预览</th>
              <th>名称 / 角色</th>
              <th>状态</th>
              <th class="hip-table__sticky-end" aria-label="操作"></th>
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
              <td class="hip-table__sticky-start hip-identity-table__drag">
                <span class="hip-drag-handle" title="拖拽排序">⠿</span>
              </td>
              <td>
                <!-- 按形态取图而非「哪个字段非空」：另外两个形态的值可能留着，
                   与 runtime 一致的口径来自读出口（它按形态只下发其一）。
                   裂图回落牌无色，对齐 runtime 的回落牌 -->
                <img
                  v-if="
                    showsIcon(view.mapping.spec) && !brokenIcons.has(markSource(view.mapping.spec)!)
                  "
                  class="hip-mark-icon"
                  :src="markSource(view.mapping.spec)"
                  :alt="view.mapping.spec.displayName"
                  :title="view.mapping.spec.displayName"
                  @error="brokenIcons.add(markSource(view.mapping.spec)!)"
                />
                <span
                  v-else
                  class="hip-mark-preview"
                  :title="view.mapping.spec.displayName"
                  :style="
                    showsIcon(view.mapping.spec) || !view.mapping.spec.color
                      ? {}
                      : { borderColor: view.mapping.spec.color, color: view.mapping.spec.color }
                  "
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
              <td>
                <VStatusDot
                  :state="view.mapping.spec.enabled === false ? 'warning' : 'success'"
                  :text="view.mapping.spec.enabled === false ? '已停用' : '已启用'"
                />
              </td>
              <td class="hip-table__sticky-end hip-identity-table__actions">
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
      </div>
    </VCard>

    <VModal
      v-if="editVisible"
      ref="modal"
      :title="editing ? '编辑身份标识映射' : '新建身份标识映射'"
      :width="520"
      mount-to-body
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
          :help="
            markMode === 'text'
              ? '文字牌上显示的名称'
              : '后台列表名；前台作图标/图片的悬停提示，加载失败时回落为文字'
          "
        />
        <FormKit
          v-model="markMode"
          type="radio"
          label="展示形态"
          :options="[
            { label: '文字', value: 'text' },
            { label: '图标', value: 'icon' },
            { label: '图片', value: 'image' },
          ]"
          help="三选一：纯文字牌 / Iconify 小图标 / 上传小图；不与文字并排"
        />
        <FormKit
          v-if="markMode === 'icon'"
          v-model="formState.iconGlyph"
          type="iconify"
          label="图标"
          format="dataurl"
          :value-only="true"
          help="从 Iconify 图标库选择；颜色在选择器里写入 SVG，前台按原样渲染"
        />
        <FormKit
          v-else-if="markMode === 'image'"
          v-model="formState.imageUrl"
          type="attachment"
          label="图片"
          :accepts="['image/*']"
          help="身份标识展示的图片，建议使用小尺寸图片"
        />
        <FormKit
          v-if="markMode === 'text'"
          v-model="formState.color"
          type="color"
          format="hex8"
          label="颜色"
          help="文字牌的边框与文字颜色；不选则用模板默认铬件，可拉透明度"
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

/* 预览列有固定展示面积，名称 / 角色列负责吸收剩余空间。 */
.hip-identity-table {
  min-width: 760px;
  table-layout: fixed;
}
.hip-identity-table__col--drag {
  width: 44px;
}
.hip-identity-table__col--preview {
  width: 176px;
}
.hip-identity-table__col--status {
  width: 96px;
}
.hip-identity-table__col--actions {
  width: 48px;
}
.hip-identity-table tbody tr,
.hip-identity-table td {
  height: 60px;
}
.hip-identity-table .hip-table__main,
.hip-identity-table .hip-table__title,
.hip-identity-table .hip-table__title-text,
.hip-identity-table .hip-table__desc {
  min-width: 0;
  max-width: 100%;
}
.hip-identity-table__drag,
.hip-identity-table__actions {
  text-align: center;
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
/* inline-block + 列内截断：ellipsis 对 flex 化的匿名文本不生效，纯文本牌无需 flex。 */
.hip-mark-preview {
  box-sizing: border-box;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
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
