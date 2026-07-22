<script lang="ts" setup>
// 列表筛选下拉（复刻 Halo 官方内部组件 FilterDropdown）
// 官方源码位于 halo 仓库 ui/src/components/filter/FilterDropdown.vue（未进组件库），
// 此处以 scoped CSS 替代官方 Tailwind 类。再次点击已选项 = 清除该筛选。
import { computed } from 'vue'
import { IconArrowDown, VDropdown, VDropdownItem } from '@halo-dev/components'

export type FilterValue = string | boolean | number | undefined

export interface FilterItem {
  label: string
  value?: string | boolean | number
}

const props = withDefaults(
  defineProps<{
    items: FilterItem[]
    label: string
    modelValue?: string | boolean | number
  }>(),
  {
    modelValue: undefined,
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', modelValue: FilterValue): void
}>()

const selectedItem = computed(() => {
  return props.items.find((item) => item.value === props.modelValue)
})

function handleSelect(item: FilterItem) {
  if (item.value === props.modelValue) {
    emit('update:modelValue', undefined)
    return
  }
  emit('update:modelValue', item.value)
}
</script>

<template>
  <VDropdown>
    <div
      class="hip-filter-dropdown"
      :class="{ 'hip-filter-dropdown--active': modelValue !== undefined }"
    >
      <span v-if="!selectedItem" class="hip-filter-dropdown__label">
        {{ label }}
      </span>
      <span v-else class="hip-filter-dropdown__label">
        {{ label }}：{{ selectedItem.label }}
      </span>
      <span class="hip-filter-dropdown__arrow">
        <IconArrowDown />
      </span>
    </div>
    <template #popper>
      <VDropdownItem
        v-for="(item, index) in items"
        :key="index"
        :selected="item.value === modelValue"
        @click="handleSelect(item)"
      >
        {{ item.label }}
      </VDropdownItem>
    </template>
  </VDropdown>
</template>

<style scoped>
.hip-filter-dropdown {
  display: flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
  font-size: var(--hip-font-body);
  color: var(--hip-text-secondary);
  transition: color var(--hip-transition);
}
.hip-filter-dropdown:hover {
  color: var(--hip-text-primary);
}
.hip-filter-dropdown--active .hip-filter-dropdown__label {
  font-weight: 600;
}
.hip-filter-dropdown__label {
  margin-right: 2px;
  white-space: nowrap;
}
.hip-filter-dropdown__arrow {
  display: inline-flex;
  align-items: center;
}
</style>
