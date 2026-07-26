<script lang="ts" setup>
// 注入到 Halo /uc/profile 个人资料页的「装扮」tab：公开装扮墙开关
// 开关用官方 VSwitch、布局对齐其它 UC profile tab（隐私设置归资料中枢，不影响佩戴）
import { onMounted, ref } from 'vue'
import { Toast, VLoading, VSwitch } from '@halo-dev/components'
import { ucApi } from '@/api'

const loading = ref(true)
const saving = ref(false)
const visible = ref(true)

async function load() {
  loading.value = true
  try {
    const profile = await ucApi.getProfile()
    // 为空视为公开
    visible.value = profile.publicDecorationsVisible !== false
  } catch {
    visible.value = true
  } finally {
    loading.value = false
  }
}

// 仿官方通知偏好 tab：@change 时取反并提交，成功后回写
async function toggle() {
  if (saving.value) return
  const next = !visible.value
  saving.value = true
  try {
    const profile = await ucApi.updateVisibility(next)
    visible.value = profile.publicDecorationsVisible !== false
    Toast.success(visible.value ? '已公开装扮墙' : '已关闭公开')
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <!-- 单根容器：本组件由 Halo 注入 /uc/profile，多根（Fragment）组件在 loading
       切换时会触发 Halo 主框架 Vue 的 insertBefore 锚点错位（NotFoundError）。 -->
  <div class="hip-profile-deco-tab">
    <VLoading v-if="loading" />
    <div v-else class="flex items-start justify-between gap-4 py-2">
      <div>
        <div class="text-sm font-medium text-gray-900">公开我的装扮墙</div>
        <div class="hip-profile-deco-tab__desc mt-1 text-xs text-gray-500">
          开启后，支持的主题可在你的个人主页等位置展示你获得的装饰；关闭后不对外公开。
          （不影响评论区等位置已佩戴装扮的正常展示。）
        </div>
      </div>
      <VSwitch :model-value="visible" :loading="saving" @change="toggle" />
    </div>
  </div>
</template>

<style scoped>
/* leading-relaxed 未被宿主 Tailwind JIT 产出（宿主源码不含该类），行高由本组件自持 */
.hip-profile-deco-tab__desc {
  line-height: 1.625;
}
</style>
