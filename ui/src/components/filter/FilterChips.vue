<script lang="ts" setup>
// 已选筛选条件 chips 行：
// 单击 × 清除单项，尾部「清空全部」一键清除。无筛选时整行不渲染。
export interface FilterChip {
  /** 唯一标识（清除单项时回传） */
  key: string
  /** 展示文案，如「状态：已启用」 */
  label: string
}

defineProps<{
  chips: FilterChip[]
}>()

const emit = defineEmits<{
  (event: 'remove', key: string): void
  (event: 'clear'): void
}>()
</script>

<template>
  <div v-if="chips.length" class="hip-filter-chips">
    <span v-for="chip in chips" :key="chip.key" class="hip-filter-chips__chip">
      {{ chip.label }}
      <button
        type="button"
        class="hip-filter-chips__remove"
        :aria-label="`清除筛选：${chip.label}`"
        @click="emit('remove', chip.key)"
      >
        ×
      </button>
    </span>
    <button type="button" class="hip-filter-chips__clear" @click="emit('clear')">清空全部</button>
  </div>
</template>

<style scoped>
.hip-filter-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--hip-gap-sm);
  padding: 8px 16px;
  border-bottom: 1px solid var(--hip-border-light);
  background: var(--hip-bg-subtle);
}
.hip-filter-chips__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--hip-border);
  border-radius: var(--hip-radius-chip);
  background: var(--hip-bg-card);
  padding: 2px 6px 2px 8px;
  font-size: var(--hip-font-caption);
  color: var(--hip-text-secondary);
}
.hip-filter-chips__remove {
  border: none;
  background: none;
  cursor: pointer;
  padding: 0 2px;
  font-size: 13px;
  line-height: 1;
  color: var(--hip-text-faint);
  transition: color var(--hip-transition);
}
.hip-filter-chips__remove:hover {
  color: var(--hip-danger);
}
.hip-filter-chips__clear {
  border: none;
  background: none;
  cursor: pointer;
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
  text-decoration: underline;
  text-underline-offset: 2px;
  transition: color var(--hip-transition);
}
.hip-filter-chips__clear:hover {
  color: var(--hip-text-primary);
}
</style>
