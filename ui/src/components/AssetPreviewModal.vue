<script lang="ts" setup>
// 单个装饰的预览弹窗：上方结构化展示资产摘要，下方并列展示两档实际落点。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { VButton, VModal, VSpace, VStatusDot } from '@halo-dev/components'
import AssetThumb from './AssetThumb.vue'
import type { DecorationAsset, MetadataOptions, PublicIdentity } from '@/types'
import { loadRuntimeForPreview, type HipRuntime } from '@/utils/runtime-loader'
import { currentUserAvatar, PREVIEW_SCENES, sampleIdentityWith } from '@/utils/preview-identity'
import {
  formatDateTime,
  metadataLabel as metadataLabelOf,
  rarityColor as rarityColorOf,
  STATUS_LABELS,
  STATUS_STATES,
  tagChipStyle,
  TYPE_LABELS,
} from '@/utils/decoration'
import type { MetadataOptionKind } from '@/utils/decoration'

const props = defineProps<{
  asset: DecorationAsset
  /** 元数据选项（解析分类/标签/稀有度显示名与配置色）；不传则回退显示内部名 */
  metadataOptions?: MetadataOptions | null
}>()

const metadataLabel = (kind: MetadataOptionKind, name?: string) =>
  metadataLabelOf(props.metadataOptions, kind, name)

const rarityColor = computed(() =>
  rarityColorOf(props.metadataOptions, props.asset.spec.rarityName),
)

/** 标签 chip 配色：配置色只作描边、文字中性（对齐资产页 tagStyle 哲学）。 */
const tagStyle = (name: string) => tagChipStyle(props.metadataOptions, name)

const emit = defineEmits<{ (event: 'close'): void }>()

const modal = ref<InstanceType<typeof VModal> | null>(null)

// 容器按场景 id 登记。Map 本身不响应式，所以「数据先到、VModal 稍后挂容器」时，
// 不能只依赖 identity/runtime 的 watch；注册容器也必须主动排一次渲染。
const panes = new Map<string, HTMLElement>()
const runtime = shallowRef<HipRuntime | null>(null)
const identity = shallowRef<PublicIdentity | null>(null)
const failed = ref('')
let active = true
let renderQueued = false

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

/**
 * 合并同一轮的两次 ref 注册与数据更新，在 DOM 落定后渲染一次。
 * iframe 就绪与否由 runtime 的 renderPreview 内部等待，此处只负责合流。
 */
function queuePreviewRender(): void {
  if (!active || renderQueued) {
    return
  }
  renderQueued = true
  void nextTick(() => {
    renderQueued = false
    void renderPreviews()
  })
}

function registerPane(id: string, element: HTMLElement | null): void {
  const previous = panes.get(id)
  if (previous && previous !== element) {
    runtime.value?.disposePreview(previous)
  }
  if (!element) {
    panes.delete(id)
    return
  }
  // 内联箭头 ref 每次 patch 都会被回调（Vue 的函数 ref 语义）。元素没换就直接收手，
  // 否则任何一次无关重渲染都会把两个 iframe 拆了重建。
  if (previous === element) {
    return
  }
  panes.set(id, element)
  queuePreviewRender()
}

async function renderPreviews(): Promise<void> {
  const loadedRuntime = runtime.value
  const previewIdentity = identity.value
  if (!active || !loadedRuntime || !previewIdentity) {
    return
  }
  try {
    // 只渲染已接入文档的容器；晚到的那个会在自己的 ref 注册时再排一轮
    await Promise.all(
      PREVIEW_SCENES.map((scene) => {
        const container = panes.get(scene.id)
        if (!container?.isConnected) {
          return undefined
        }
        return loadedRuntime.renderPreview(container, {
          component: scene.components,
          data: previewIdentity,
          // fit 的基准是内容实际占地，装得下就 1:1 —— 行内组合不会被缩，
          // 只有 560 的用户卡在窄弹窗里才等比缩
          fit: true,
          locked: true,
        })
      }),
    )
  } catch (error) {
    // 模板自身的错误 runtime 会画进预览；这里接住的是 iframe / 生命周期级异常。
    // 必须落到 failed 上：无提示的空白区域对站长没有任何可操作信息。
    if (active) {
      failed.value = errorMessage(error)
    }
  }
}

onMounted(async () => {
  try {
    // 头像取真实值（头像框要套在真实头像上才看得准），其余走干净底座
    const [loaded, avatar] = await Promise.all([loadRuntimeForPreview(), currentUserAvatar()])
    if (!active) {
      return
    }
    runtime.value = loaded
    identity.value = sampleIdentityWith(
      {
        type: props.asset.spec.type,
        url: props.asset.spec.asset?.url,
        payload: props.asset.spec.payload,
        displayName: props.asset.spec.displayName,
        assetName: props.asset.metadata.name,
      },
      avatar,
    )
  } catch (e) {
    if (active) {
      failed.value = errorMessage(e)
    }
  }
})

// 数据与容器无论谁先就绪，后到的一方都会触发同一个幂等渲染队列。
watch(
  [identity, runtime],
  () => {
    queuePreviewRender()
  },
  { flush: 'post' },
)

onBeforeUnmount(() => {
  active = false
  renderQueued = false
  for (const container of panes.values()) {
    runtime.value?.disposePreview(container)
  }
  panes.clear()
})
</script>

<template>
  <!-- 780 是按用户卡定的：卡身 560 + 预览区两侧留白，减去弹窗内边距后仍够它 1:1 展示。
       更窄的屏由 fit 等比下缩，不会撑出横向滚动 -->
  <VModal
    ref="modal"
    title="装饰预览"
    :width="780"
    mount-to-body
    layer-closable
    @close="emit('close')"
  >
    <div class="hip-asset-preview">
      <section class="hip-asset-summary" aria-labelledby="hip-asset-summary-title">
        <div class="hip-asset-summary__top">
          <div class="hip-asset-summary__thumb">
            <AssetThumb
              :type="asset.spec.type"
              :asset="asset.spec.asset"
              :payload="asset.spec.payload"
              :display-name="asset.spec.displayName"
            />
          </div>
          <div class="hip-asset-summary__identity">
            <span class="hip-asset-summary__eyebrow">装饰信息</span>
            <div class="hip-asset-summary__title-row">
              <h2 id="hip-asset-summary-title" :title="asset.spec.displayName">
                {{ asset.spec.displayName }}
              </h2>
              <VStatusDot
                :state="STATUS_STATES[asset.spec.status]"
                :text="STATUS_LABELS[asset.spec.status]"
              />
            </div>
            <p class="hip-asset-summary__context">
              <span>{{
                asset.spec.submittedBy ? `由 ${asset.spec.submittedBy} 投稿` : '系统创建'
              }}</span>
              <span aria-hidden="true">·</span>
              <time :datetime="asset.metadata.creationTimestamp">
                {{ formatDateTime(asset.metadata.creationTimestamp) }}
              </time>
            </p>
          </div>
        </div>

        <dl class="hip-asset-summary__facts">
          <div class="hip-asset-summary__fact">
            <dt>类型</dt>
            <dd>{{ TYPE_LABELS[asset.spec.type] }}</dd>
          </div>
          <div class="hip-asset-summary__fact">
            <dt>分类</dt>
            <dd :title="metadataLabel('categories', asset.spec.categoryName)">
              {{
                asset.spec.categoryName
                  ? metadataLabel('categories', asset.spec.categoryName)
                  : '未分类'
              }}
            </dd>
          </div>
          <div class="hip-asset-summary__fact">
            <dt>稀有度</dt>
            <dd class="hip-asset-summary__rarity">
              <span
                v-if="rarityColor"
                class="hip-asset-summary__rarity-dot"
                :style="{ backgroundColor: rarityColor }"
              ></span>
              <span :title="metadataLabel('rarities', asset.spec.rarityName)">
                {{
                  asset.spec.rarityName
                    ? metadataLabel('rarities', asset.spec.rarityName)
                    : '未设置'
                }}
              </span>
            </dd>
          </div>
        </dl>

        <div class="hip-asset-summary__section">
          <span class="hip-asset-summary__label">标签</span>
          <div v-if="asset.spec.tagNames?.length" class="hip-asset-summary__tags">
            <span
              v-for="tag in asset.spec.tagNames"
              :key="tag"
              class="hip-asset-summary__tag"
              :style="tagStyle(tag)"
            >
              {{ metadataLabel('tags', tag) }}
            </span>
          </div>
          <span v-else class="hip-asset-summary__empty">暂无标签</span>
        </div>

        <div class="hip-asset-summary__section hip-asset-summary__section--description">
          <span class="hip-asset-summary__label">描述</span>
          <p :class="{ 'hip-asset-summary__empty': !asset.spec.description }">
            {{ asset.spec.description || '暂无描述' }}
          </p>
        </div>
      </section>

      <p v-if="failed" class="hip-asset-preview__error">预览不可用：{{ failed }}</p>
      <div v-else-if="!identity" class="hip-asset-preview__loading" aria-live="polite">
        正在准备预览…
      </div>
      <template v-else-if="identity">
        <section v-for="scene in PREVIEW_SCENES" :key="scene.id" class="hip-asset-preview__scene">
          <div class="hip-asset-preview__scene-header">
            <h3>{{ scene.label }}</h3>
            <span>前台实际效果</span>
          </div>
          <!-- 内部完全交给 runtime（它在里面建 iframe），Vue 不碰这层的子节点 -->
          <div
            :ref="(el) => registerPane(scene.id, el as HTMLElement | null)"
            class="hip-asset-preview__pane"
          ></div>
        </section>
      </template>
    </div>
    <template #footer>
      <VSpace>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped>
.hip-asset-preview {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 摘要区先回答“这是什么”，再进入实际场景预览；避免把不同语义的信息堆成一排 chip。 */
.hip-asset-summary {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--hip-border);
  border-radius: var(--hip-radius-card);
  background: var(--hip-bg-card);
}
.hip-asset-summary__top {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.hip-asset-summary__thumb {
  width: 96px;
  height: 72px;
  flex: none;
  overflow: hidden;
  border: 1px solid var(--hip-border-light);
  border-radius: var(--hip-radius-thumb);
  background: var(--hip-bg-thumb);
}
.hip-asset-summary__identity {
  flex: 1;
  min-width: 0;
}
.hip-asset-summary__eyebrow,
.hip-asset-summary__label,
.hip-asset-summary__fact dt {
  font-size: 11px;
  line-height: 16px;
  color: var(--hip-text-faint);
}
.hip-asset-summary__eyebrow {
  display: block;
  margin-bottom: 2px;
  font-weight: 500;
  letter-spacing: 0.04em;
}
.hip-asset-summary__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}
.hip-asset-summary__title-row h2 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--hip-text-primary);
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hip-asset-summary__context {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  margin: 4px 0 0;
  font-size: var(--hip-font-caption);
  line-height: 18px;
  color: var(--hip-text-muted);
}
.hip-asset-summary__facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--hip-border);
  border-radius: 8px;
  background: var(--hip-bg-subtle);
}
.hip-asset-summary__fact {
  min-width: 0;
  padding: 9px 12px;
}
.hip-asset-summary__fact + .hip-asset-summary__fact {
  border-left: 1px solid var(--hip-border-light);
}
.hip-asset-summary__fact dd {
  margin: 2px 0 0;
  overflow: hidden;
  font-size: var(--hip-font-body);
  line-height: 19px;
  color: var(--hip-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hip-asset-summary__rarity {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}
.hip-asset-summary__rarity-dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 999px;
}
.hip-asset-summary__section {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  align-items: start;
  gap: 10px;
}
.hip-asset-summary__label {
  padding-top: 2px;
}
.hip-asset-summary__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.hip-asset-summary__tag {
  max-width: 160px;
  overflow: hidden;
  padding: 1px 7px;
  border: 1px solid var(--hip-border);
  border-radius: 4px;
  color: var(--hip-text-secondary);
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hip-asset-summary__section--description {
  padding-top: 12px;
  border-top: 1px solid var(--hip-border-light);
}
.hip-asset-summary__section p {
  margin: 0;
  color: var(--hip-text-secondary);
  font-size: var(--hip-font-body);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.hip-asset-summary__empty {
  color: var(--hip-text-faint);
  font-size: var(--hip-font-caption);
  line-height: 20px;
}
/* 描述段的 p 选择器带类型、特异性高于单类；这里补一档而不是靠 !important 压 */
.hip-asset-summary__section p.hip-asset-summary__empty {
  color: var(--hip-text-faint);
}

.hip-asset-preview__scene {
  overflow: hidden;
  border: 1px solid var(--hip-border);
  border-radius: var(--hip-radius-card);
  background: var(--hip-bg-subtle);
}
.hip-asset-preview__scene-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border-bottom: 1px solid var(--hip-border-light);
  background: var(--hip-bg-card);
}
.hip-asset-preview__scene-header h3 {
  margin: 0;
  color: var(--hip-text-secondary);
  font-size: var(--hip-font-body);
  font-weight: 500;
  line-height: 20px;
}
.hip-asset-preview__scene-header span {
  font-size: var(--hip-font-caption);
  color: var(--hip-text-faint);
}
.hip-asset-preview__pane {
  background: var(--hip-bg-subtle);
  overflow: hidden;
}
.hip-asset-preview__loading,
.hip-asset-preview__error {
  padding: 16px;
  border: 1px dashed var(--hip-border);
  border-radius: var(--hip-radius-card);
  background: var(--hip-bg-subtle);
  font-size: var(--hip-font-caption);
  text-align: center;
  color: var(--hip-text-muted);
}
/* 错误面色由 --hip-danger 派生（color-mix 透明度混合），保持单一色源 */
.hip-asset-preview__error {
  border-color: color-mix(in srgb, var(--hip-danger) 30%, transparent);
  background: color-mix(in srgb, var(--hip-danger) 6%, transparent);
  color: var(--hip-danger);
}

@media (max-width: 640px) {
  .hip-asset-preview {
    gap: 16px;
  }
  .hip-asset-summary {
    gap: 12px;
    padding: 12px;
  }
  .hip-asset-summary__top {
    align-items: flex-start;
    gap: 12px;
  }
  .hip-asset-summary__thumb {
    width: 72px;
    height: 54px;
  }
  .hip-asset-summary__title-row {
    align-items: flex-start;
  }
  .hip-asset-summary__facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .hip-asset-summary__fact:nth-child(3) {
    grid-column: 1 / -1;
    border-top: 1px solid var(--hip-border-light);
    border-left: 0;
  }
  .hip-asset-summary__section {
    grid-template-columns: 44px minmax(0, 1fr);
    gap: 8px;
  }
}
</style>
