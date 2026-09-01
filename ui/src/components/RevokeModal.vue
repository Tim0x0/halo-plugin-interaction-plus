<script lang="ts" setup>
// 撤销装饰弹窗：确认 + 填写撤销原因。
// 多来源并存下，该用户此装饰若还有其他来源的有效授予，仅撤本条用户仍持有——
// 弹窗内明示并提供「一并撤销全部来源」选项（彻底收回）。
import { onMounted, ref } from 'vue'
import { Toast, VButton, VModal, VSpace } from '@halo-dev/components'
import { grantApi } from '@/api'
import { grantSourceLabel } from '@/utils/decoration'
import type { DecorationGrant, GrantView } from '@/types'

const props = defineProps<{
  grant: DecorationGrant
  /** 装饰显示名（用于提示文案） */
  assetLabel: string
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'revoked'): void
}>()

const modal = ref<InstanceType<typeof VModal> | null>(null)
const reason = ref('')
const submitting = ref(false)

/** 同用户同装饰的其他来源有效授予（多来源并存提示用） */
const otherActive = ref<GrantView[]>([])
const revokeAll = ref(false)

onMounted(async () => {
  try {
    const result = await grantApi.list({
      userName: props.grant.spec.userName,
      assetName: props.grant.spec.assetName,
      revoked: 'false',
      page: 1,
      size: 100,
    })
    otherActive.value = result.items.filter(
      (view) => view.status === 'active' && view.grant.metadata.name !== props.grant.metadata.name,
    )
  } catch {
    // 查询失败不阻断撤销流程，仅缺一条并存提示
  }
})

async function handleRevoke() {
  submitting.value = true
  try {
    const trimmedReason = reason.value.trim() || undefined
    if (revokeAll.value && otherActive.value.length) {
      const { revokedCount } = await grantApi.revokeHeld(
        props.grant.spec.userName,
        props.grant.spec.assetName,
        trimmedReason,
      )
      Toast.success(`已撤销全部来源（${revokedCount} 条）`)
    } else {
      await grantApi.revoke(props.grant.metadata.name, trimmedReason)
      Toast.success('撤销成功')
    }
    emit('revoked')
    modal.value?.close()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <VModal ref="modal" title="撤销装饰" :width="480" mount-to-body @close="emit('close')">
    <p class="hip-revoke__desc">
      确定撤销用户「{{ grant.spec.userName }}」的装饰「{{ assetLabel }}」吗？
      <template v-if="otherActive.length">
        该用户此装饰还有 {{ otherActive.length }} 条其他来源的有效授予（{{
          otherActive.map((view) => grantSourceLabel(view)).join('、')
        }}）——仅撤销本条，用户仍会持有该装饰。
      </template>
      <template v-else>
        撤销后该用户将失去此装饰并清理对应佩戴槽位，撤销原因会通过站内通知告知用户。
      </template>
    </p>
    <FormKit
      v-if="otherActive.length"
      v-model="revokeAll"
      type="checkbox"
      :label="`一并撤销全部来源的授予（共 ${otherActive.length + 1} 条），彻底收回该装饰`"
    />
    <FormKit v-model="reason" type="textarea" label="撤销原因（可选）" :auto-height="true" />
    <template #footer>
      <VSpace>
        <VButton :loading="submitting" type="danger" @click="handleRevoke">撤销</VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped>
.hip-revoke__desc {
  margin: 0 0 12px;
  font-size: 13px;
  color: #4b5563;
  line-height: 1.6;
}
</style>
