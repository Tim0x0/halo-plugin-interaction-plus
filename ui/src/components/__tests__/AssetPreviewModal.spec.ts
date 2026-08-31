import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DecorationAsset } from '@/types'

const mocks = vi.hoisted(() => ({
  currentUserAvatar: vi.fn(),
  disposePreview: vi.fn(),
  loadRuntimeForPreview: vi.fn(),
  renderPreview: vi.fn(),
  sampleIdentityWith: vi.fn(),
}))

vi.mock('@halo-dev/components', async () => {
  const { defineComponent, h, onMounted, ref } = await import('vue')

  const passthrough = (name: string) =>
    defineComponent({
      name,
      setup(_props, { slots }) {
        return () => h('div', slots.default?.())
      },
    })

  return {
    VButton: passthrough('VButton'),
    VSpace: passthrough('VSpace'),
    VStatusDot: passthrough('VStatusDot'),
    // Halo VModal 会在挂载后初始化 Teleport、Transition 与 OverlayScrollbars。
    // 延后一拍渲染 slot，可稳定覆盖「数据先到、预览容器后到」这一竞态。
    VModal: defineComponent({
      name: 'VModal',
      setup(_props, { expose, slots }) {
        const bodyReady = ref(false)
        onMounted(() => {
          window.setTimeout(() => {
            bodyReady.value = true
          }, 24)
        })
        expose({ close: vi.fn() })
        return () =>
          h('div', [
            bodyReady.value ? h('main', slots.default?.()) : null,
            h('footer', slots.footer?.()),
          ])
      },
    }),
  }
})

vi.mock('@/utils/runtime-loader', () => ({
  loadRuntimeForPreview: mocks.loadRuntimeForPreview,
}))

vi.mock('@/utils/decoration', () => ({
  STATUS_LABELS: { active: '已启用' },
  STATUS_STATES: { active: 'success' },
  TYPE_LABELS: { badge: '勋章' },
  formatDateTime: (value?: string) => value || '-',
  metadataLabel: (_options: unknown, _kind: string, name?: string) => name || '',
  rarityColor: () => undefined,
  tagChipStyle: () => undefined,
}))

vi.mock('@/utils/preview-identity', () => ({
  PREVIEW_SCENES: [
    { id: 'inline', label: '头像 + 身份行', components: ['avatar', 'identity'] },
    { id: 'card', label: '用户卡', components: ['card'] },
  ],
  currentUserAvatar: mocks.currentUserAvatar,
  sampleIdentityWith: mocks.sampleIdentityWith,
}))

vi.mock('../AssetThumb.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      name: 'AssetThumb',
      setup() {
        return () => h('div')
      },
    }),
  }
})

import AssetPreviewModal from '../AssetPreviewModal.vue'

const asset: DecorationAsset = {
  metadata: {
    name: 'preview-race-asset',
    creationTimestamp: '2026-08-29T00:00:00Z',
  },
  spec: {
    type: 'badge',
    displayName: '竞态回归样本',
    status: 'active',
  },
}

async function advanceOpening(): Promise<void> {
  // 先让立即完成的 runtime / 头像 Promise 推进，此时 VModal 还没有挂 slot。
  await flushPromises()
  await vi.advanceTimersByTimeAsync(24)
  // queuePreviewRender 用 nextTick 合并两个 pane 的 ref 注册；renderPreview 自身是异步的。
  await flushPromises()
  await flushPromises()
}

describe('AssetPreviewModal', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.currentUserAvatar.mockResolvedValue(undefined)
    mocks.sampleIdentityWith.mockReturnValue({ userName: 'sample' })
    mocks.renderPreview.mockImplementation(async (container: HTMLElement) => {
      container.dataset.rendered = 'true'
    })
    mocks.loadRuntimeForPreview.mockResolvedValue({
      renderPreview: mocks.renderPreview,
      disposePreview: mocks.disposePreview,
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.useRealTimers()
  })

  it('容器晚于 runtime 挂载时仍渲染全部场景', async () => {
    const wrapper = mount(AssetPreviewModal, {
      attachTo: document.body,
      props: { asset },
    })

    await flushPromises()
    expect(mocks.renderPreview).not.toHaveBeenCalled()

    await advanceOpening()

    expect(mocks.renderPreview).toHaveBeenCalledTimes(2)
    expect(mocks.renderPreview.mock.calls.map(([, options]) => options.component)).toEqual([
      ['avatar', 'identity'],
      ['card'],
    ])
    expect(wrapper.findAll('[data-rendered="true"]')).toHaveLength(2)

    wrapper.unmount()
  })

  it('iframe 级渲染异常会显示错误而不是静默空白', async () => {
    mocks.renderPreview.mockRejectedValueOnce(new Error('iframe document unavailable'))
    const wrapper = mount(AssetPreviewModal, {
      attachTo: document.body,
      props: { asset },
    })

    await advanceOpening()

    expect(wrapper.text()).toContain('预览不可用：iframe document unavailable')
    wrapper.unmount()
  })

  it('无关重渲染不会重复重建预览', async () => {
    const wrapper = mount(AssetPreviewModal, {
      attachTo: document.body,
      props: { asset },
    })
    await advanceOpening()
    expect(mocks.renderPreview).toHaveBeenCalledTimes(2)

    // 内联箭头 ref 每次 patch 都会被回调；容器元素没换就不该再排一轮渲染。
    await wrapper.setProps({ metadataOptions: { categories: [], tags: [], rarities: [] } })
    await flushPromises()
    await flushPromises()

    expect(mocks.renderPreview).toHaveBeenCalledTimes(2)
    expect(mocks.disposePreview).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('弹窗提前卸载后不执行迟到的预览任务', async () => {
    const wrapper = mount(AssetPreviewModal, {
      attachTo: document.body,
      props: { asset },
    })
    await flushPromises()
    wrapper.unmount()

    await vi.runAllTimersAsync()
    await flushPromises()

    expect(mocks.renderPreview).not.toHaveBeenCalled()
  })
})
