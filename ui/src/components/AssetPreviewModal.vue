<script lang="ts" setup>
// 单个装饰的三场景预览弹窗
import { computed, ref } from 'vue'
import { VButton, VModal } from '@halo-dev/components'
import DecorationPreview from './DecorationPreview.vue'
import type { DecorationAsset } from '@/types'
import { assetToPreviewData } from '@/utils/decoration'

const props = defineProps<{ asset: DecorationAsset }>()

const emit = defineEmits<{ (event: 'close'): void }>()

const modal = ref<InstanceType<typeof VModal> | null>(null)

const previewData = computed(() => assetToPreviewData(props.asset))
</script>

<template>
  <!-- 650 = 卡片 1:1（560）+ 弹窗与预览容器内边距；窄屏时 DecorationPreview 自动等比下缩 -->
  <VModal ref="modal" :title="`预览：${asset.spec.displayName}`" :width="650" @close="emit('close')">
    <DecorationPreview :data="previewData" />
    <template #footer>
      <VButton @click="modal?.close()">关闭</VButton>
    </template>
  </VModal>
</template>
