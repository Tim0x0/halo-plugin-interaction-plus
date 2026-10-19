<script lang="ts" setup>
// UC「装扮」父路由壳：登录用户一律进入「我的装扮」。
// 「我的投稿」由子路由的 decoration:submit 单独控制，不参与默认跳转。
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

function redirectToMyDecoration() {
  if (route.name !== 'InteractionPlusUcDecorations') {
    return
  }
  router.replace({ name: 'InteractionPlusUcMyDecoration' })
}

watch(() => route.name, redirectToMyDecoration, { immediate: true })
</script>

<template>
  <RouterView />
</template>
