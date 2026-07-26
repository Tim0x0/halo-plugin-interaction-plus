<script lang="ts" setup>
// 装饰缩略展示：文字牌称号 / 昵称样式没有素材图，必须渲染真实效果——
// 称号 = 文本 + 前景 / 背景色；昵称样式 = 示例字 + 纯色 / 渐变；
// 整图称号（titleMode=image）与其他图片类型统一走素材图分支。
// 资产网格、行列表、我的装扮、投稿列表、装饰选择弹窗统一复用。
import { computed, ref, watch } from 'vue'
import type { AssetPayload, AssetRef, DecorationTypeValue } from '@/types'
import { nameStyleCss, thumbnailUrl, titleCss, TYPE_LABELS } from '@/utils/decoration'

const props = withDefaults(
  defineProps<{
    type?: DecorationTypeValue
    asset?: AssetRef | null
    payload?: AssetPayload | null
    /** 兜底展示文案（通常传装饰显示名） */
    displayName?: string
    /** sm：行内 40px 方块；lg：填充父容器（网格卡片缩略区） */
    size?: 'sm' | 'lg'
  }>(),
  {
    size: 'lg',
  },
)

const imageUrl = computed(() => thumbnailUrl(props.asset?.url, props.size === 'sm' ? 'S' : 'M'))

// 素材 404 兜底：裂图切换到文字兜底分支（对齐 runtime「加载失败 = 缺失」口径）；
// 素材地址变化（如编辑中更换）时复位重试
const broken = ref(false)
watch(imageUrl, () => {
  broken.value = false
})

const titleStyle = computed(() => titleCss(props.payload))
const nameCss = computed(() => nameStyleCss(props.payload?.nameStyle))

const fallbackLabel = computed(
  () => props.displayName || (props.type ? TYPE_LABELS[props.type] : '装饰'),
)
</script>

<template>
  <div class="hip-thumb" :class="`hip-thumb--${size}`">
    <!-- 文字牌称号：渲染真实前景 / 背景色（整图称号走下方素材图分支） -->
    <span
      v-if="type === 'title' && payload?.titleMode !== 'image' && payload?.titleText"
      class="hip-thumb__title"
      :style="titleStyle"
    >
      {{ payload.titleText }}
    </span>
    <!-- 昵称样式：示例字渲染真实纯色 / 渐变 -->
    <span
      v-else-if="type === 'name_style' && payload?.nameStyle"
      class="hip-thumb__name-style"
      :style="nameCss"
    >
      {{ displayName || '昵称样式' }}
    </span>
    <!-- 有素材图的类型 -->
    <img
      v-else-if="imageUrl && !broken"
      :src="imageUrl"
      :alt="displayName"
      loading="lazy"
      @error="broken = true"
    />
    <!-- 兜底 -->
    <span v-else class="hip-thumb__fallback">{{ fallbackLabel }}</span>
  </div>
</template>

<style scoped>
.hip-thumb {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hip-bg-thumb);
  overflow: hidden;
}
.hip-thumb--sm {
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: var(--hip-radius-thumb);
}
.hip-thumb--lg {
  width: 100%;
  height: 100%;
}
.hip-thumb img {
  max-width: 80%;
  max-height: 80%;
  object-fit: contain;
}
.hip-thumb--sm img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
.hip-thumb__title {
  display: inline-flex;
  align-items: center;
  border-radius: 6px;
  padding: 3px 10px;
  font-size: var(--hip-font-caption);
  font-weight: 500;
  line-height: 1.2;
  background: var(--hip-bg-subtle);
  color: var(--hip-text-secondary);
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.25);
  max-width: 90%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hip-thumb--lg .hip-thumb__title {
  font-size: var(--hip-font-title);
  padding: 5px 12px;
}
.hip-thumb__name-style {
  font-weight: 600;
  font-size: var(--hip-font-body);
  max-width: 90%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hip-thumb--lg .hip-thumb__name-style {
  font-size: 16px;
}
.hip-thumb__fallback {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-faint);
  padding: 0 6px;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
