<script lang="ts" setup>
// 装饰资产创建 / 编辑弹窗（Console 管理与 UC 投稿共用）
// 左右结构：左侧配置表单，右侧实时预览
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { Toast, VButton, VModal, VSpace } from '@halo-dev/components'
import { assetApi, ucApi } from '@/api'
import type {
  AssetPayload,
  DecorationAsset,
  DecorationAssetParam,
  DecorationTypeValue,
  MetadataOptions,
  PublicIdentity,
} from '@/types'
import { TYPE_OPTIONS } from '@/utils/decoration'
import {
  currentUserAvatar,
  PREVIEW_SCENES,
  sampleIdentityWith,
  sceneComponents,
} from '@/utils/preview-identity'
import { loadRuntimeForPreview, type HipRuntime } from '@/utils/runtime-loader'

const props = defineProps<{
  /** 编辑对象；为空表示新建 */
  asset?: DecorationAsset
  /** console：管理端创建；uc：用户投稿 */
  mode: 'console' | 'uc'
  /** 元数据选项 */
  metadataOptions?: MetadataOptions | null
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'saved'): void
}>()

const modal = ref<InstanceType<typeof VModal> | null>(null)
// 经 form 节点提交以触发 FormKit 校验管线（不依赖全局 id registry）
const formRef = ref()

function submitForm() {
  formRef.value?.node?.submit?.()
}

async function copyId() {
  if (!props.asset) {
    return
  }
  try {
    if (!navigator.clipboard) {
      // 非安全上下文（http 站点）下 clipboard API 不存在
      throw new Error('clipboard unavailable')
    }
    await navigator.clipboard.writeText(props.asset.metadata.name)
    Toast.success('已复制装饰标识')
  } catch {
    // 标识值已设 user-select: all，点击即可全选
    Toast.warning('复制失败，请手动选中复制')
  }
}

const isEdit = computed(() => !!props.asset)

const formState = reactive({
  type: 'badge' as DecorationTypeValue,
  displayName: '',
  description: '',
  categoryName: '',
  tagNames: [] as string[],
  rarityName: '',
  assetUrl: '',
  assetMediaType: '',
  titleText: '',
  titleColor: '',
  titleBackground: '',
  titleBackground2: '',
  nameStyleMode: 'solid' as 'solid' | 'gradient',
  color1: '#3b82f6',
  color2: '#a855f7',
  color3: '',
})

watch(
  () => props.asset,
  (asset) => {
    if (!asset) {
      return
    }
    formState.type = asset.spec.type
    formState.displayName = asset.spec.displayName
    formState.description = asset.spec.description || ''
    formState.categoryName = asset.spec.categoryName || ''
    formState.tagNames = asset.spec.tagNames ? [...asset.spec.tagNames] : []
    formState.rarityName = asset.spec.rarityName || ''
    formState.assetUrl = asset.spec.asset?.url || ''
    formState.assetMediaType = asset.spec.asset?.mediaType || ''
    formState.titleText = asset.spec.payload?.titleText || ''
    formState.titleColor = asset.spec.payload?.titleColor || ''
    formState.titleBackground = asset.spec.payload?.titleBackground || ''
    formState.titleBackground2 = asset.spec.payload?.titleBackgroundSecondary || ''
    const nameStyle = asset.spec.payload?.nameStyle
    if (nameStyle) {
      formState.nameStyleMode = nameStyle.mode
      formState.color1 = nameStyle.colors[0] || '#3b82f6'
      formState.color2 = nameStyle.colors[1] || '#a855f7'
      formState.color3 = nameStyle.colors[2] || ''
    }
  },
  { immediate: true },
)

// attachment 输入只返回素材 URL：更换 URL 时清空 mediaType，避免提交不匹配的
// URL 与类型（后端不探测资源；SVG 判定可按 .svg 后缀处理）。
watch(
  () => formState.assetUrl,
  (url) => {
    const original = props.asset?.spec.asset
    formState.assetMediaType = original && url === original.url ? original.mediaType || '' : ''
  },
)

// 给素材位的类型：图片类三种恒需素材，称号可选加图；昵称样式纯配置，不给素材位
const needMaterial = computed(() => formState.type !== 'name_style')

const materialHelp = computed(() =>
  formState.type === 'title'
    ? '可选。配了图，用户卡的称号行显示这张图；评论等行内场景不显示图，仍用称号名称。建议细长横条图（宽高比 2:1 ~ 10:1）、内容画满画布勿留透明边距；卡片按最高 48px 缩放，图内文字占画布高 1/3 即约 16px、够读'
    : '勋章建议不低于 128x128 且图案画满画布、勿留透明边距（展示位按图适配，边距会让勋章显小一圈）；头像框建议透明图不低于 256x256，图案画满画布、勿留透明边距（展示按头像约 1.24 倍叠放）；名片背景建议 1120x596（与卡片 560x298 同比例零裁切，偏方的图会居中裁上下）；单图大小建议控制在 1-4MB 内',
)

const categoryOptions = computed(() => [
  { label: '未分类', value: '' },
  ...(props.metadataOptions?.categories ?? []).map((item) => ({
    label: item.spec.displayName,
    value: item.metadata.name,
  })),
])

const tagOptions = computed(() =>
  (props.metadataOptions?.tags ?? []).map((item) => ({
    label: item.spec.displayName,
    value: item.metadata.name,
  })),
)

const rarityOptions = computed(() => [
  { label: '无稀有度', value: '' },
  ...(props.metadataOptions?.rarities ?? []).map((item) => ({
    label: item.spec.displayName,
    value: item.metadata.name,
  })),
])

const accepts = computed(() => {
  if (formState.type === 'card_background') {
    return ['image/png', 'image/webp', 'image/jpeg', 'image/gif']
  }
  return ['image/png', 'image/webp', 'image/svg+xml', 'image/gif']
})

// 实时预览数据：干净底座 + 正在编辑的这一件（跟随表单值）。
// 底座刻意不用站长自己的真实装扮 —— 素材要判断的是自身观感，旁边摆着一身别的装扮
// 会干扰判断，而且每个站长账号装扮不同，同一个素材会看出不同结论
const previewIdentity = computed<PublicIdentity>(() => {
  const url = needMaterial.value && formState.assetUrl ? formState.assetUrl : undefined
  const colors =
    formState.nameStyleMode === 'solid'
      ? [formState.color1]
      : [formState.color1, formState.color2, formState.color3].filter(Boolean)
  // 各类型的字段一并备好，最终只有当前类型对应的槽位会被读到
  const payload: AssetPayload = {
    // 未填时给占位文案，否则称号预览是个空牌子
    titleText: formState.titleText || '称号预览',
    titleColor: formState.titleColor || undefined,
    titleBackground: formState.titleBackground || undefined,
    titleBackgroundSecondary:
      formState.titleBackground && formState.titleBackground2
        ? formState.titleBackground2
        : undefined,
    nameStyle: { mode: formState.nameStyleMode, colors },
  }
  // 昵称恒用底座的「示例用户」，不跟随装饰名称字段（装饰名不是昵称样本）
  return sampleIdentityWith({ type: formState.type, url, payload }, currentAvatar.value)
})

// ── 预览渲染（与前台同一个模板引擎） ────────────────────

const previewRef = ref<HTMLElement | null>(null)
const runtime = shallowRef<HipRuntime | null>(null)
const previewError = ref('')
const currentAvatar = ref<string>()

/**
 * 预览场景，默认停在用户卡（它是唯一能看全所有装饰类型的落点）。
 *
 * <p>不按类型自动切档：行内档本身带头像、用户卡上也看得见头像框，两档都不会漏东西。
 */
const previewScene = ref('card')
const sceneTabs = PREVIEW_SCENES

onMounted(async () => {
  try {
    // 头像取真实值：头像框要套在真实头像上，而头像是身份不是装扮，不构成干扰
    const [loaded, avatar] = await Promise.all([loadRuntimeForPreview(), currentUserAvatar()])
    runtime.value = loaded
    currentAvatar.value = avatar
  } catch (e) {
    previewError.value = e instanceof Error ? e.message : String(e)
  }
})

// 颜色选择器是拖动输入，而一次预览是「重建 shadow + 重跑整段模板脚本」的全量重渲染，
// 跟不上每一帧。50ms 防抖看不出延迟，却能把渲染次数压下一个量级
let renderTimer: ReturnType<typeof setTimeout> | undefined

watch([previewIdentity, previewScene, runtime], () => {
  clearTimeout(renderTimer)
  renderTimer = setTimeout(renderPreview, 50)
})

function renderPreview(): void {
  const container = previewRef.value
  if (!container || !runtime.value) {
    return
  }
  runtime.value
    .renderPreview(container, {
      component: sceneComponents(previewScene.value),
      data: previewIdentity.value,
      // fit 的基准是内容实际占地，装得下就 1:1 —— 行内档比右栏小，实际不会被缩；
      // 只有 560 的用户卡真的超宽才等比缩。预览看外观不验交互，卡片展开后锁死
      fit: true,
      locked: true,
    })
    .then(() => {
      previewError.value = ''
    })
    .catch((e) => {
      previewError.value = e instanceof Error ? e.message : String(e)
    })
}

onBeforeUnmount(() => {
  clearTimeout(renderTimer)
  if (previewRef.value) {
    runtime.value?.disposePreview(previewRef.value)
  }
})

const saving = ref(false)

function buildParam(): DecorationAssetParam {
  const param: DecorationAssetParam = {
    type: formState.type,
    displayName: formState.displayName.trim(),
    description: formState.description.trim() || undefined,
    categoryName: formState.categoryName || undefined,
    // 数量上限由表单 max-count 控制，不在此静默截断
    tagNames: formState.tagNames.length ? formState.tagNames : undefined,
    rarityName: formState.rarityName || undefined,
  }
  // 仅需要素材的类型才提交素材，避免切换类型后残留素材被一并入库
  if (needMaterial.value && formState.assetUrl) {
    param.asset = {
      url: formState.assetUrl,
      mediaType: formState.assetMediaType || undefined,
    }
  }
  if (formState.type === 'title') {
    param.payload = {
      titleText: formState.titleText.trim(),
      // 三色恒入库：行内文字牌用它，卡片的图挂了回落文字牌也用它
      titleColor: formState.titleColor || undefined,
      titleBackground: formState.titleBackground || undefined,
      // 第二色仅在主背景色存在时有意义
      titleBackgroundSecondary:
        formState.titleBackground && formState.titleBackground2
          ? formState.titleBackground2
          : undefined,
    }
  } else if (formState.type === 'name_style') {
    const colors =
      formState.nameStyleMode === 'solid'
        ? [formState.color1]
        : [formState.color1, formState.color2, formState.color3].filter(Boolean)
    param.payload = {
      nameStyle: { mode: formState.nameStyleMode, colors },
    }
  }
  return param
}

// FormKit @submit 回调（footer 按钮经 form 节点 submit 触发校验后进入）
async function handleSubmit() {
  if (formState.tagNames.length > 5) {
    Toast.warning('标签最多 5 个')
    return
  }
  saving.value = true
  try {
    const param = buildParam()
    if (props.mode === 'console') {
      if (isEdit.value && props.asset) {
        await assetApi.update(props.asset.metadata.name, param)
      } else {
        await assetApi.create(param)
      }
    } else {
      if (isEdit.value && props.asset) {
        await ucApi.updateSubmission(props.asset.metadata.name, param)
      } else {
        await ucApi.createSubmission(param)
      }
    }
    Toast.success(isEdit.value ? '保存成功' : '创建成功')
    emit('saved')
    modal.value?.close()
  } catch {
    // 错误提示由 axios 拦截器统一处理，避免 unhandled rejection
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <VModal
    ref="modal"
    :title="isEdit ? '编辑装饰' : mode === 'uc' ? '创建装饰草稿' : '创建装饰'"
    :width="920"
    mount-to-body
    @close="emit('close')"
  >
    <!-- 左右两栏顺排配置（不分通用/特定），预览置于右栏末尾 -->
    <FormKit ref="formRef" type="form" :actions="false" @submit="handleSubmit">
      <div class="hip-edit__cols">
        <!-- 左栏：依次排列的配置 -->
        <div class="hip-edit__col">
          <FormKit
            v-model="formState.type"
            type="select"
            label="装饰类型"
            :options="TYPE_OPTIONS"
            :disabled="isEdit"
            help="创建后不可修改"
          />
          <FormKit v-model="formState.displayName" type="text" label="名称" validation="required" />
          <div v-if="isEdit && asset" class="hip-edit__id">
            <span class="hip-edit__id-label">装饰标识</span>
            <code
              class="hip-edit__id-value"
              title="外部发奖插件用此值（decorationName）指定该装饰"
              >{{ asset.metadata.name }}</code
            >
            <button type="button" class="hip-edit__id-copy" @click="copyId">复制</button>
          </div>
          <FormKit
            v-model="formState.description"
            type="textarea"
            label="描述"
            :auto-height="true"
          />
          <FormKit
            v-model="formState.categoryName"
            type="select"
            label="分类"
            :options="categoryOptions"
          />
          <FormKit
            v-model="formState.tagNames"
            type="select"
            label="标签"
            multiple
            :max-count="5"
            :options="tagOptions"
            help="最多 5 个"
          />
          <FormKit
            v-model="formState.rarityName"
            type="select"
            label="稀有度"
            :options="rarityOptions"
          />
        </div>

        <!-- 右栏：剩余配置（随类型变化）+ 末尾预览 -->
        <div class="hip-edit__col">
          <!-- 称号扩展：名称必填、三色恒有效，图片是可选增强，故排在素材位之前 -->
          <template v-if="formState.type === 'title'">
            <FormKit
              v-model="formState.titleText"
              type="text"
              label="称号名称"
              validation="required"
              help="评论等行内场景展示这个文字；配了称号图片时，它同时作为图片的替代文本与加载失败兜底"
            />
            <FormKit
              v-model="formState.titleColor"
              type="color"
              format="hex8"
              label="文字颜色"
              help="不选则继承正文颜色"
            />
            <FormKit
              v-model="formState.titleBackground"
              type="color"
              format="hex8"
              label="背景颜色"
              help="不选则无背景；可拉透明度"
            />
            <FormKit
              v-if="formState.titleBackground"
              v-model="formState.titleBackground2"
              type="color"
              format="hex8"
              label="背景颜色 2（可选）"
              help="填写后与背景颜色形成渐变"
            />
          </template>

          <!-- 素材：勋章 / 头像框 / 名片背景恒需，称号可选加图 -->
          <FormKit
            v-if="needMaterial"
            v-model="formState.assetUrl"
            type="attachment"
            :label="formState.type === 'title' ? '称号图片' : '素材'"
            :accepts="accepts"
            :help="materialHelp"
          />

          <!-- 昵称样式扩展 -->
          <template v-if="formState.type === 'name_style'">
            <FormKit
              v-model="formState.nameStyleMode"
              type="radio"
              label="颜色模式"
              :options="[
                { label: '纯色', value: 'solid' },
                { label: '渐变', value: 'gradient' },
              ]"
            />
            <FormKit v-model="formState.color1" type="color" format="hex8" label="颜色 1" />
            <template v-if="formState.nameStyleMode === 'gradient'">
              <FormKit v-model="formState.color2" type="color" format="hex8" label="颜色 2" />
              <FormKit
                v-model="formState.color3"
                type="color"
                format="hex8"
                label="颜色 3（可选）"
              />
            </template>
          </template>

          <!-- 预览：右栏末尾，两档落点 Tab 切换（行内组合 / 用户卡） -->
          <div class="hip-edit__preview">
            <div class="hip-edit__preview-head">
              <span class="hip-edit__preview-label">实时预览</span>
              <div class="hip-edit__scenes" role="group" aria-label="预览组件切换">
                <button
                  v-for="scene in sceneTabs"
                  :key="scene.id"
                  type="button"
                  class="hip-edit__scene-btn"
                  :class="{ 'hip-edit__scene-btn--active': previewScene === scene.id }"
                  @click="previewScene = scene.id"
                >
                  {{ scene.label }}
                </button>
              </div>
            </div>
            <p v-if="previewError" class="hip-edit__preview-error">
              预览不可用：{{ previewError }}
            </p>
            <!-- 内部完全交给 runtime（它在里面建 iframe），Vue 不碰这层的子节点 -->
            <div v-else ref="previewRef"></div>
          </div>
        </div>
      </div>
    </FormKit>

    <template #footer>
      <VSpace>
        <!-- 经表单管线提交，触发 FormKit 校验与红字反馈 -->
        <VButton :loading="saving" type="secondary" @click="submitForm">
          {{ isEdit ? '保存' : '创建' }}
        </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped>
/* 左右两栏顺排，配置 + 预览顺次填充 */
.hip-edit__cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--hip-gap-lg);
  align-items: start;
}
@media (max-width: 760px) {
  .hip-edit__cols {
    grid-template-columns: 1fr;
  }
}
.hip-edit__preview {
  margin-top: var(--hip-gap-md);
  padding-top: var(--hip-gap-md);
  border-top: 1px solid var(--hip-border-light);
}
.hip-edit__preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--hip-gap-sm);
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.hip-edit__preview-label {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
}
.hip-edit__preview-error {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
}
.hip-edit__scenes {
  display: flex;
  gap: 4px;
}
.hip-edit__scene-btn {
  border: 1px solid var(--hip-border);
  background: var(--hip-bg-card);
  cursor: pointer;
  padding: 3px 10px;
  border-radius: var(--hip-radius-chip);
  font-size: var(--hip-font-caption);
  color: var(--hip-text-muted);
  transition: all var(--hip-transition);
}
.hip-edit__scene-btn--active {
  background: var(--hip-bg-subtle);
  color: var(--hip-text-primary);
  font-weight: 600;
}
.hip-edit__id {
  display: flex;
  align-items: center;
  gap: var(--hip-gap-sm);
  margin-bottom: var(--hip-gap-md);
  font-size: var(--hip-font-caption);
}
.hip-edit__id-label {
  color: var(--hip-text-muted);
}
.hip-edit__id-value {
  font-family: monospace;
  color: var(--hip-text-secondary);
  background: var(--hip-bg-subtle);
  padding: 2px 6px;
  border-radius: var(--hip-radius-chip);
  user-select: all;
}
.hip-edit__id-copy {
  border: 1px solid var(--hip-border);
  background: var(--hip-bg-card);
  cursor: pointer;
  padding: 2px 8px;
  border-radius: var(--hip-radius-chip);
  font-size: var(--hip-font-caption);
  color: var(--hip-text-secondary);
  transition: background var(--hip-transition);
}
.hip-edit__id-copy:hover {
  background: var(--hip-bg-subtle);
}
</style>
