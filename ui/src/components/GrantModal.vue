<script lang="ts" setup>
// 授予装饰弹窗：指定用户授予 / 按角色快照授予
// 装饰选择改为「同一弹窗内 v-if 切换」到 AssetSelectPanel（不再套第二个 VModal，
// 嵌套弹窗会触发 insertBefore / emitsOptions=null 崩溃）；全部成功自动关闭，有跳过/失败保留明细
import { computed, reactive, ref } from 'vue'
import { Toast, VAlert, VButton, VModal, VSpace } from '@halo-dev/components'
import { grantApi } from '@/api'
import DecorationPicker from '@/components/DecorationPicker.vue'
import { GRANT_FAILURE_LABELS } from '@/utils/decoration'
import type { DecorationAsset, GrantResult } from '@/types'

const props = defineProps<{
  /** 预选装饰（从资产列表 / 再次授予进入时） */
  asset?: DecorationAsset
  /** 预填用户（再次授予场景） */
  initialUserNames?: string[]
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'granted'): void
}>()

const modal = ref<InstanceType<typeof VModal> | null>(null)
// 经 form 节点提交以触发 FormKit 校验管线（不依赖全局 id registry）
const formRef = ref()

const formState = reactive({
  grantMode: 'users' as 'users' | 'role',
  userNames: [...(props.initialUserNames ?? [])] as string[],
  roleNames: [] as string[],
  expiresAt: '',
  reason: '',
})

// 已选装饰（完整对象，v-model 绑定到 DecorationPicker；handleSubmit 取 assetNames）
const selectedAssets = ref<DecorationAsset[]>(props.asset ? [props.asset] : [])

/** 有效期下限：当前时间（datetime-local 本地格式） */
const expiresMin = computed(() => {
  const now = new Date()
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset())
  return now.toISOString().slice(0, 16)
})

const submitting = ref(false)
const result = ref<GrantResult | null>(null)

/** 结果横幅类型：全部失败 error、部分失败 warning、其余 success。 */
const resultAlertType = computed(() => {
  if (!result.value) return 'success'
  if (result.value.failedCount > 0) {
    return result.value.successCount === 0 && result.value.renewedCount === 0
      ? 'error'
      : 'warning'
  }
  return 'success'
})

/** 结果横幅标题：续期为 0 时不显示该段，避免噪音。 */
const resultTitle = computed(() => {
  if (!result.value) return ''
  const parts = [`成功 ${result.value.successCount}`]
  if (result.value.renewedCount > 0) {
    parts.push(`续期 ${result.value.renewedCount}`)
  }
  parts.push(`跳过 ${result.value.skippedCount}`, `失败 ${result.value.failedCount}`)
  return `执行完成：${parts.join('，')}`
})

function submitForm() {
  formRef.value?.node?.submit?.()
}

/** 授予失败码转中文（未知码回退原文）。 */
function failureLabel(reason: string): string {
  return GRANT_FAILURE_LABELS[reason] ?? reason
}

// FormKit @submit 回调
async function handleSubmit() {
  if (!selectedAssets.value.length) {
    Toast.warning('请选择要授予的装饰')
    return
  }
  if (formState.grantMode === 'users' && !formState.userNames.length) {
    Toast.warning('请选择用户')
    return
  }
  if (formState.grantMode === 'role' && !formState.roleNames.length) {
    Toast.warning('请选择角色')
    return
  }
  if (formState.expiresAt && new Date(formState.expiresAt).getTime() <= Date.now()) {
    Toast.warning('有效期必须晚于当前时间')
    return
  }
  submitting.value = true
  try {
    const assetNames = selectedAssets.value.map((asset) => asset.metadata.name)
    const expiresAt = formState.expiresAt
      ? new Date(formState.expiresAt).toISOString()
      : undefined
    const reason = formState.reason.trim() || undefined
    if (formState.grantMode === 'users') {
      result.value = await grantApi.grant({
        userNames: formState.userNames,
        assetNames,
        expiresAt,
        reason,
      })
    } else {
      result.value = await grantApi.grantByRoleSnapshot({
        roleNames: formState.roleNames,
        assetNames,
        expiresAt,
        reason,
      })
    }
    emit('granted')
    // 全部顺利（无跳过 / 失败）：仅 Toast（全局消息）并关闭，不在弹窗内渲染结果横幅；
    // 有跳过 / 失败：保留弹窗展示明细
    if (result.value.failedCount === 0 && result.value.skippedCount === 0) {
      const parts = [`成功 ${result.value.successCount}`]
      if (result.value.renewedCount > 0) {
        parts.push(`续期 ${result.value.renewedCount}`)
      }
      Toast.success(`授予完成（${parts.join('，')} 项）`)
      result.value = null
      modal.value?.close()
    } else if (
      result.value.failedCount > 0 &&
      result.value.successCount === 0 &&
      result.value.renewedCount === 0
    ) {
      Toast.error('授予全部失败，请查看失败明细')
    } else {
      Toast.warning('授予部分完成，请查看明细')
    }
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <VModal ref="modal" title="授予装饰" :width="720" mount-to-body @close="emit('close')">
    <div class="hip-grant__form">
      <!-- 装饰选择：稳定挂载的子组件（内部持有二级选择弹窗），不在本 VModal slot 顶层放 modal -->
      <DecorationPicker v-model="selectedAssets" />

      <FormKit ref="formRef" type="form" :actions="false" @submit="handleSubmit">
        <FormKit
          v-model="formState.grantMode"
          type="radio"
          label="授予方式"
          :options="[
            { label: '指定用户', value: 'users' },
            { label: '按角色快照', value: 'role' },
          ]"
        />
        <FormKit
          v-if="formState.grantMode === 'users'"
          v-model="formState.userNames"
          type="userSelect"
          label="用户"
          multiple
        />
        <FormKit
          v-if="formState.grantMode === 'role'"
          v-model="formState.roleNames"
          type="roleSelect"
          label="角色"
          multiple
          help="一次性快照授予当前拥有该角色的用户，后续角色变动不会自动调整"
        />
        <FormKit
          v-model="formState.expiresAt"
          type="datetime-local"
          :min="expiresMin"
          max="9999-12-31T23:59"
          label="有效期至"
          help="留空表示永久有效"
        />
        <FormKit v-model="formState.reason" type="text" label="授予原因" />
      </FormKit>

      <!-- 执行结果（有跳过 / 失败时保留展示） -->
      <div v-if="result" class="hip-grant-result">
        <VAlert :type="resultAlertType" :title="resultTitle" :closable="false">
          <template #description>
            <div v-if="result.renewed.length">
              已续期（有效期已延长至本次所选）：{{
                result.renewed.map((item) => item.userDisplayName || item.userName).join('、')
              }}
            </div>
            <div v-if="result.skipped.length">
              跳过（已持有相同或更长有效期）：{{
                result.skipped.map((item) => item.userDisplayName || item.userName).join('、')
              }}
            </div>
            <div v-if="result.failed.length">
              失败：{{
                result.failed
                  .map((item) => `${item.userDisplayName || item.userName}（${failureLabel(item.reason)}）`)
                  .join('、')
              }}
            </div>
          </template>
        </VAlert>
      </div>
    </div>

    <template #footer>
      <VSpace>
        <VButton :loading="submitting" type="secondary" @click="submitForm">
          执行授予
        </VButton>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped>
.hip-grant-result {
  margin-top: 12px;
}
</style>
