<script lang="ts" setup>
// UC「装扮」父路由壳：按权限跳转到第一个有权访问的子页面
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { utils } from '@halo-dev/ui-shared'

const route = useRoute()
const router = useRouter()

const CANDIDATES = [
  { name: 'InteractionPlusUcMyDecoration', permission: 'plugin:interaction-plus:decoration:equip' },
  { name: 'InteractionPlusUcSubmissions', permission: 'plugin:interaction-plus:decoration:submit' },
]

function redirectToFirstAccessible() {
  if (route.name !== 'InteractionPlusUcDecorations') {
    return
  }
  const target = CANDIDATES.find((candidate) => utils.permission.has([candidate.permission]))
  if (target) {
    router.replace({ name: target.name })
  }
}

watch(() => route.name, redirectToFirstAccessible, { immediate: true })
</script>

<template>
  <RouterView />
</template>
