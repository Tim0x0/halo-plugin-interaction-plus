<script lang="ts" setup>
// Console「装饰」父路由壳：不写死 redirect，
// 按权限跳转到第一个有权访问的子页面
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { utils } from '@halo-dev/ui-shared'

const route = useRoute()
const router = useRouter()

/** 子页面候选（按菜单顺序；custom-templates 权限仅超管可得、且超管在首项即放行，故不列入） */
const CANDIDATES = [
  { name: 'InteractionPlusAssets', permission: 'plugin:interaction-plus:decoration:manage' },
  { name: 'InteractionPlusGrants', permission: 'plugin:interaction-plus:decoration:grant' },
  { name: 'InteractionPlusMetadata', permission: 'plugin:interaction-plus:decoration:manage' },
  { name: 'InteractionPlusIdentityMarks', permission: 'plugin:interaction-plus:system:manage' },
]

function redirectToFirstAccessible() {
  if (route.name !== 'InteractionPlusDecorations') {
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
