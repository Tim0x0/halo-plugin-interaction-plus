<script lang="ts" setup>
// 装饰选择器字段：稳定挂载在授予表单内的子组件，内部持有「已选 chips + 选择按钮 + 二级选择弹窗」。
// 关键（对齐 Halo 官方 AttachmentInput / UserAvatar 范式）：二级 VModal(DecorationSelectorModal)
// 作为本「稳定子组件」内部的 v-if 节点，其挂载锚点落在本组件 div 内（稳定父节点），而非父 VModal
// 被 overlayscrollbars 接管的 modal-body slot 顶层 —— 从而规避 insertBefore / emitsOptions=null 崩溃。
// 触发器（按钮/chips）全程常驻不卸载，只有 DecorationSelectorModal 自身随 v-if 挂载/销毁。
import { ref } from 'vue'
import { VButton } from '@halo-dev/components'
import AssetThumb from '@/components/AssetThumb.vue'
import DecorationSelectorModal from '@/components/DecorationSelectorModal.vue'
import type { DecorationAsset } from '@/types'

const props = defineProps<{
  /** 已选装饰（双向绑定） */
  modelValue: DecorationAsset[]
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: DecorationAsset[]): void
}>()

const selectorVisible = ref(false)

function removeAsset(name: string) {
  emit(
    'update:modelValue',
    props.modelValue.filter((asset) => asset.metadata.name !== name),
  )
}

function onConfirm(assets: DecorationAsset[]) {
  emit('update:modelValue', assets)
}
</script>

<template>
  <div class="hip-deco-picker">
    <div class="hip-deco-picker__label">装饰</div>
    <div v-if="modelValue.length" class="hip-deco-picker__chips">
      <span
        v-for="asset in modelValue"
        :key="asset.metadata.name"
        class="hip-deco-picker__chip"
      >
        <AssetThumb
          size="sm"
          class="hip-deco-picker__chip-thumb"
          :type="asset.spec.type"
          :asset="asset.spec.asset"
          :payload="asset.spec.payload"
          :display-name="asset.spec.displayName"
        />
        <span class="hip-deco-picker__chip-name">{{ asset.spec.displayName }}</span>
        <button
          type="button"
          class="hip-deco-picker__chip-remove"
          :aria-label="`移除 ${asset.spec.displayName}`"
          @click="removeAsset(asset.metadata.name)"
        >
          ×
        </button>
      </span>
    </div>
    <VButton size="sm" @click="selectorVisible = true">
      {{ modelValue.length ? '修改选择' : '选择装饰' }}
    </VButton>

    <!-- 二级选择弹窗：稳定子组件内部 v-if，锚点落在本 div 内（稳定父节点）；
         自身 mount-to-body teleport 到 body，与父弹窗在 body 内成兄弟。
         等价于官方 AttachmentInput 的结构（编辑弹窗内选资源的标准做法）。 -->
    <DecorationSelectorModal
      v-if="selectorVisible"
      :selected="modelValue"
      @confirm="onConfirm"
      @close="selectorVisible = false"
    />
  </div>
</template>

<style scoped>
.hip-deco-picker {
  margin-bottom: var(--hip-gap-md);
}
.hip-deco-picker__label {
  font-size: var(--hip-font-body);
  font-weight: 600;
  color: var(--hip-text-primary);
  margin-bottom: 8px;
}
.hip-deco-picker__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--hip-gap-sm);
  margin-bottom: 8px;
}
.hip-deco-picker__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--hip-border);
  border-radius: var(--hip-radius-chip);
  background: var(--hip-bg-subtle);
  padding: 4px 6px 4px 4px;
  font-size: var(--hip-font-caption);
  color: var(--hip-text-secondary);
}
.hip-deco-picker__chip-thumb {
  width: 28px !important;
  height: 28px !important;
}
.hip-deco-picker__chip-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hip-deco-picker__chip-remove {
  border: none;
  background: none;
  cursor: pointer;
  padding: 0 2px;
  font-size: 14px;
  line-height: 1;
  color: var(--hip-text-faint);
  transition: color var(--hip-transition);
}
.hip-deco-picker__chip-remove:hover {
  color: var(--hip-danger);
}
</style>
