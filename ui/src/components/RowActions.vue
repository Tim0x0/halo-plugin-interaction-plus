<script lang="ts" setup>
// 表格行操作（···）：有菜单项时渲染下拉，无操作时渲染等宽占位保证列对齐
import { useSlots } from 'vue'
import { VDropdown } from '@halo-dev/components'
import RiMore2Fill from '~icons/ri/more-2-fill'

const slots = useSlots()
const hasActions = () => !!slots.default
</script>

<template>
  <!-- 单根容器（display:contents 不产生盒子、不影响表格布局）：避免多根（Fragment）
       组件在表格行 v-for 中 move/patch 时触发 insertBefore 锚点错位。 -->
  <span class="hip-row-actions-slot">
    <VDropdown v-if="hasActions()" compute-transform-origin>
      <button type="button" class="hip-row-actions" aria-label="更多操作">
        <RiMore2Fill />
      </button>
      <template #popper>
        <slot />
      </template>
    </VDropdown>
    <span v-else class="hip-row-actions hip-row-actions--placeholder" aria-hidden="true"></span>
  </span>
</template>

<style scoped>
.hip-row-actions-slot {
  /* 不产生盒子，内部下拉/占位直接参与单元格布局，等效原多根渲染 */
  display: contents;
}
.hip-row-actions {
  width: 28px;
  height: 28px;
  border: none;
  background: none;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--hip-text-muted);
  transition: all var(--hip-transition);
}
.hip-row-actions:hover {
  background: #e5e7eb;
  color: var(--hip-text-primary);
}
.hip-row-actions--placeholder {
  cursor: default;
  pointer-events: none;
}
</style>
