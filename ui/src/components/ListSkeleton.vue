<script lang="ts" setup>
// 骨架屏：卡片网格 / 行列表场景替代 VLoading 转圈
withDefaults(
  defineProps<{
    /** list：行骨架；grid：卡片网格骨架 */
    variant?: 'list' | 'grid'
    /** 骨架条目数量 */
    count?: number
  }>(),
  {
    variant: 'list',
    count: 6,
  },
)
</script>

<template>
  <!-- 单根容器：组件必须单根。多根（Fragment）组件在父级 v-if/v-else 条件链中
       切换时，Vue 以注释节点作 fragment 锚点，卸载/挂载易出现锚点失配，
       触发 NotFoundError: insertBefore（列表 loading→数据 切换时高发）。 -->
  <div class="hip-skeleton" :class="`hip-skeleton--${variant}`">
    <div
      v-for="i in count"
      :key="i"
      :class="variant === 'grid' ? 'hip-skeleton__card' : 'hip-skeleton__row'"
    >
      <template v-if="variant === 'grid'">
        <div class="hip-skeleton__thumb hip-skeleton__pulse"></div>
        <div class="hip-skeleton__line hip-skeleton__pulse" style="width: 70%"></div>
        <div class="hip-skeleton__line hip-skeleton__pulse" style="width: 45%"></div>
      </template>
      <template v-else>
        <div class="hip-skeleton__avatar hip-skeleton__pulse"></div>
        <div class="hip-skeleton__row-lines">
          <div class="hip-skeleton__line hip-skeleton__pulse" style="width: 35%"></div>
          <div class="hip-skeleton__line hip-skeleton__pulse" style="width: 55%"></div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.hip-skeleton--grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--hip-gap-md);
  padding: var(--hip-gap-lg);
}
.hip-skeleton__card {
  border: 1px solid var(--hip-border-light);
  border-radius: var(--hip-radius-card);
  padding: var(--hip-gap-md);
  display: flex;
  flex-direction: column;
  gap: var(--hip-gap-sm);
}
.hip-skeleton__thumb {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: var(--hip-radius-thumb);
}
.hip-skeleton--list {
  display: flex;
  flex-direction: column;
}
.hip-skeleton__row {
  display: flex;
  align-items: center;
  gap: var(--hip-gap-md);
  padding: 14px 16px;
}
.hip-skeleton__row + .hip-skeleton__row {
  border-top: 1px solid var(--hip-border-light);
}
.hip-skeleton__avatar {
  width: 40px;
  height: 40px;
  border-radius: var(--hip-radius-thumb);
  flex: none;
}
.hip-skeleton__row-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hip-skeleton__line {
  height: 12px;
  border-radius: 4px;
}
.hip-skeleton__pulse {
  background: linear-gradient(
    90deg,
    var(--hip-bg-thumb) 25%,
    var(--hip-bg-subtle) 50%,
    var(--hip-bg-thumb) 75%
  );
  background-size: 200% 100%;
  animation: hip-skeleton-pulse 1.4s ease infinite;
}
@keyframes hip-skeleton-pulse {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>
