<script lang="ts" setup>
// 列表加载失败错误态（审查报告 F5：失败与空数据可区分，并提供重试入口）
import { VButton } from '@halo-dev/components'

withDefaults(
  defineProps<{
    title?: string
  }>(),
  {
    title: '加载失败',
  },
)

const emit = defineEmits<{
  (event: 'retry'): void
}>()
</script>

<template>
  <div class="hip-list-error">
    <div class="hip-list-error__icon">⚠</div>
    <div class="hip-list-error__title">{{ title }}</div>
    <div class="hip-list-error__message">网络异常或服务暂不可用，请稍后重试。</div>
    <VButton size="sm" @click="emit('retry')">重试</VButton>
  </div>
</template>

<style scoped>
.hip-list-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--hip-gap-sm);
  padding: 48px 16px;
  text-align: center;
}
.hip-list-error__icon {
  font-size: 28px;
  color: var(--hip-warning);
}
.hip-list-error__title {
  font-size: var(--hip-font-title);
  font-weight: 600;
  color: var(--hip-text-primary);
}
.hip-list-error__message {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
  margin-bottom: 4px;
}
</style>
