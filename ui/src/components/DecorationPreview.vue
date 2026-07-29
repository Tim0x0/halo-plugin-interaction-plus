<script lang="ts" setup>
// 装饰预览：轻量身份行 / 用户悬浮卡片
// 通过 scenes 可只渲染部分场景（默认全部）
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { PreviewData, PreviewIdentityMark, PreviewScene } from '@/types'
import { nameStyleCss, titleCss } from '@/utils/decoration'

// 兼容既有引用：类型已迁移至 @/types 集中维护
export type { PreviewData, PreviewIdentityMark, PreviewScene }

const props = withDefaults(
  defineProps<{
    data: PreviewData
    /** 要渲染的场景，默认全部 */
    scenes?: PreviewScene[]
    /** 布局：stack 竖向堆叠（默认）/ row 场景横向并排 */
    layout?: 'stack' | 'row'
    /** 展柜格数上限：UC 传显示设置的 userCardShowcaseBadgeLimit（可调 0-8），缺省对齐设置默认值 */
    showcaseLimit?: number
  }>(),
  {
    scenes: () => ['identity_line', 'user_card'],
    layout: 'stack',
    showcaseLimit: 5,
  },
)

// 图片 404 兜底，分位置对齐 runtime hip-user-card 的口径（两侧改动必须同步）：
// 头像 / 标识图标 / 整图称号裂图切文字占位（字母 / 文字牌），
// 头像框 / 勋章 / 展柜格裂图直接隐藏（失败 = 该件缺失）
const brokenUrls = ref(new Set<string>())

function markBrokenUrl(url?: string) {
  if (url) {
    brokenUrls.value.add(url)
  }
}

/** 头像框 / 勋章 / 展柜格专用：失败 = 缺失，直接隐藏。 */
function hideBrokenImg(event: Event) {
  ;(event.target as HTMLElement).style.display = 'none'
}

function hasScene(scene: PreviewScene): boolean {
  return props.scenes.includes(scene)
}

const nameCss = computed(() => nameStyleCss(props.data.nameStyle))

// PreviewData 的称号字段与 AssetPayload 同名，直接复用统一的称号样式规则
const titleStyle = computed(() => titleCss(props.data))

// 整图称号：有图且形态为 image 时渲染 <img>，否则渲染文字牌（裂图也回落文字牌）
const isImageTitle = computed(
  () =>
    props.data.titleMode === 'image' &&
    !!props.data.titleImageUrl &&
    !brokenUrls.value.has(props.data.titleImageUrl),
)

const displayName = computed(() => props.data.displayName || '示例用户')

const marks = computed(() => props.data.identityMarks ?? [])

const showcase = computed(() => props.data.badgeShowcaseUrls ?? [])

const cardBackgroundStyle = computed(() =>
  props.data.cardBackgroundUrl ? { backgroundImage: `url(${props.data.cardBackgroundUrl})` } : {},
)

// ── 用户悬浮卡：统计与展柜计数（真实值优先，缺省回落示例值） ──
// UC 传当前用户的真实 stats / 勋章总数 / 注册时间；Console 素材预览不传 → 示例值。
// 实际运行时数据来自 PublicIdentity.stats 聚合（内置文章 / 评论 / 勋章 +
// UserStatContributor 扩展点贡献项，行内串 wrap 扩容）
const SAMPLE_BADGE_TOTAL = 12
const SAMPLE_STATS = [
  { label: '文章', value: '128' },
  { label: '评论', value: '356' },
  { label: '勋章', value: String(SAMPLE_BADGE_TOTAL) },
]
const cardStatItems = computed(() => (props.data.stats?.length ? props.data.stats : SAMPLE_STATS))

const badgeTotal = computed(() => props.data.badgeTotal ?? SAMPLE_BADGE_TOTAL)

/** 「YYYY 年 M 月加入」（同步 runtime hip-user-card 的 formatJoined）；无真实值用示例文案。 */
const joinedText = computed(() => {
  if (props.data.registeredAt) {
    const date = new Date(props.data.registeredAt)
    if (!Number.isNaN(date.getTime())) {
      return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月加入`
    }
  }
  return '2021 年 3 月加入'
})

/** 展柜格数跟随显示设置（UC 经 showcaseLimit 传入真实值），总数扣除已展示数为「+N」。 */
const shelfShowcase = computed(() => showcase.value.slice(0, props.showcaseLimit))
const shelfMore = computed(() =>
  shelfShowcase.value.length ? Math.max(0, badgeTotal.value - shelfShowcase.value.length) : 0,
)

// ── 用户悬浮卡：等比缩放（所见即所得小样） ──
// 卡片按 560px 基准渲染（与 runtime hip-user-card 同宽），容器不足时整卡等比缩小；
// transform:scale 不改变布局占位，由 stage 层按「视觉尺寸」定宽高并水平居中。
// 场景经 :scenes 动态切换（编辑弹窗 / UC 均传单场景数组），卡片 DOM 随 v-if 重建——
// 观察器必须跟随 ref 变化挂 / 解，不能只在 mounted 挂一次（否则切换后不再测量）。
const CARD_BASE_WIDTH = 560

const cardViewport = ref<HTMLElement | null>(null)
const cardEl = ref<HTMLElement | null>(null)
const scale = ref(1)
const cardNaturalHeight = ref(0)

let resizeObserver: ResizeObserver | null = null

function measureCard(): void {
  const viewport = cardViewport.value
  const card = cardEl.value
  // 场景未渲染（v-if 切走）或容器尚无宽度（弹窗过渡 / display:none）时不写入，保留上次有效值
  if (!viewport || !card) {
    return
  }
  const width = viewport.clientWidth
  if (width <= 0) {
    return
  }
  scale.value = Math.min(1, width / CARD_BASE_WIDTH)
  cardNaturalHeight.value = card.offsetHeight
}

function observeCardRef(el: HTMLElement | null, old: HTMLElement | null | undefined): void {
  if (old) {
    resizeObserver?.unobserve(old)
  }
  if (el) {
    resizeObserver?.observe(el)
    measureCard()
  }
}

onMounted(() => {
  resizeObserver = new ResizeObserver(() => measureCard())
  // 初始场景就是卡片时，ref 赋值早于 observer 创建，这里补挂
  if (cardViewport.value) {
    resizeObserver.observe(cardViewport.value)
  }
  if (cardEl.value) {
    resizeObserver.observe(cardEl.value)
  }
  measureCard()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

// 场景切换（v-if 重建 DOM）后接管新元素的观察
watch(cardViewport, observeCardRef, { flush: 'post' })
watch(cardEl, observeCardRef, { flush: 'post' })

// 数据变化可能改变卡片自然高（说明行数恒定，但称号 / 展柜有无会影响），下一帧补测
watch(
  () => props.data,
  () => requestAnimationFrame(() => measureCard()),
  { deep: true },
)

/** 舞台层：按缩放后的视觉尺寸定宽高（水平居中、不裁剪，卡片阴影完整呈现）。 */
const stageStyle = computed(() => ({
  width: `${Math.round(CARD_BASE_WIDTH * scale.value)}px`,
  height: cardNaturalHeight.value
    ? `${Math.ceil(cardNaturalHeight.value * scale.value)}px`
    : 'auto',
}))

const cardScaleStyle = computed(() => ({
  transform: `scale(${scale.value})`,
}))
</script>

<template>
  <div class="hip-preview" :class="`hip-preview--${layout}`">
    <!-- 场景一：轻量身份行 -->
    <div v-if="hasScene('identity_line')" class="hip-preview__section">
      <div class="hip-preview__label">轻量身份行</div>
      <div class="hip-preview__identity-line">
        <span class="hip-preview__avatar hip-preview__avatar--sm">
          <img
            v-if="data.avatar && !brokenUrls.has(data.avatar)"
            class="hip-preview__avatar-img"
            :src="data.avatar"
            alt=""
            @error="markBrokenUrl(data.avatar)"
          />
          <span v-else class="hip-preview__avatar-fallback">{{ displayName[0] }}</span>
          <img
            v-if="data.avatarFrameUrl"
            class="hip-preview__frame"
            :src="data.avatarFrameUrl"
            alt=""
            @error="hideBrokenImg"
          />
        </span>
        <span class="hip-preview__name" :style="nameCss">{{ displayName }}</span>
        <template v-if="marks[0]">
          <img
            v-if="marks[0].icon && !brokenUrls.has(marks[0].icon)"
            class="hip-preview__mark-icon"
            :src="marks[0].icon"
            :alt="marks[0].displayName"
            :title="marks[0].displayName"
            @error="markBrokenUrl(marks[0].icon)"
          />
          <span
            v-else
            class="hip-preview__mark"
            :style="{ borderColor: marks[0].color, color: marks[0].color }"
          >
            {{ marks[0].displayName }}
          </span>
        </template>
        <img
          v-if="isImageTitle"
          class="hip-preview__title-img"
          :src="data.titleImageUrl"
          :alt="data.titleText"
          :title="data.titleText"
          @error="markBrokenUrl(data.titleImageUrl)"
        />
        <span v-else-if="data.titleText" class="hip-preview__title" :style="titleStyle">{{
          data.titleText
        }}</span>
        <img
          v-if="data.primaryBadgeUrl"
          class="hip-preview__badge"
          :src="data.primaryBadgeUrl"
          alt=""
          @error="hideBrokenImg"
        />
      </div>
    </div>

    <!-- 场景二：用户悬浮卡片
         ⚠ 双实现同步清单：本场景与 runtime/src/hip-user-card.ts 是同一张卡的两份实现
         （尺寸恒定 560×296 / 60px 背景露出带 / 85% 素玻璃内容层 / 头像 96 骑缝 /
         说明固定三行 / 数据行行内串 / 展柜行），改结构、token 或行为时必须两侧同改。 -->
    <div v-if="hasScene('user_card')" class="hip-preview__section">
      <div class="hip-preview__label">用户悬浮卡片</div>
      <div ref="cardViewport" class="hip-preview__viewport">
        <div class="hip-preview__stage" :style="stageStyle">
          <div ref="cardEl" class="hip-preview__card" :style="cardScaleStyle">
            <div class="hip-preview__card-bg" :style="cardBackgroundStyle"></div>
            <div class="hip-preview__card-inner">
              <div class="hip-preview__surface">
                <span class="hip-preview__avatar hip-preview__avatar--card">
                  <img
                    v-if="data.avatar && !brokenUrls.has(data.avatar)"
                    class="hip-preview__avatar-img"
                    :src="data.avatar"
                    alt=""
                    @error="markBrokenUrl(data.avatar)"
                  />
                  <span v-else class="hip-preview__avatar-fallback">{{ displayName[0] }}</span>
                  <img
                    v-if="data.avatarFrameUrl"
                    class="hip-preview__frame"
                    :src="data.avatarFrameUrl"
                    alt=""
                    @error="hideBrokenImg"
                  />
                </span>
                <div class="hip-preview__head">
                  <div class="hip-preview__card-name-row">
                    <span class="hip-preview__card-name" :style="nameCss">{{ displayName }}</span>
                    <template v-for="(mark, index) in marks.slice(0, 3)" :key="index">
                      <img
                        v-if="mark.icon && !brokenUrls.has(mark.icon)"
                        class="hip-preview__mark-icon"
                        :src="mark.icon"
                        :alt="mark.displayName"
                        :title="mark.displayName"
                        @error="markBrokenUrl(mark.icon)"
                      />
                      <span
                        v-else
                        class="hip-preview__mark"
                        :style="{ borderColor: mark.color, color: mark.color }"
                      >
                        {{ mark.displayName }}
                      </span>
                    </template>
                    <img
                      v-if="data.primaryBadgeUrl"
                      class="hip-preview__badge"
                      :src="data.primaryBadgeUrl"
                      alt=""
                      @error="hideBrokenImg"
                    />
                  </div>
                  <div v-if="isImageTitle || data.titleText" class="hip-preview__title-line">
                    <img
                      v-if="isImageTitle"
                      class="hip-preview__title-img"
                      :src="data.titleImageUrl"
                      :alt="data.titleText"
                      :title="data.titleText"
                      @error="markBrokenUrl(data.titleImageUrl)"
                    />
                    <span v-else class="hip-preview__card-title" :style="titleStyle">{{
                      data.titleText
                    }}</span>
                  </div>
                </div>
                <div class="hip-preview__bio" :class="{ 'hip-preview__bio--empty': !data.bio }">
                  {{ data.bio || 'TA 还没有留下个人说明' }}
                </div>
                <div class="hip-preview__dstats">
                  <span v-for="item in cardStatItems" :key="item.label" class="hip-preview__di">
                    <b>{{ item.value }}</b
                    >{{ item.label }}
                  </span>
                </div>
                <div class="hip-preview__shelf-row">
                  <div class="hip-preview__shelf">
                    <span
                      v-for="(url, index) in shelfShowcase"
                      :key="index"
                      class="hip-preview__slot"
                    >
                      <img :src="url" alt="" @error="hideBrokenImg" />
                    </span>
                    <span
                      v-if="shelfMore > 0"
                      class="hip-preview__slot hip-preview__slot--more"
                      :title="`共 ${badgeTotal} 枚`"
                    >
                      +{{ shelfMore }}
                    </span>
                  </div>
                  <span class="hip-preview__joined">{{ joinedText }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hip-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
/* 横向并排：场景各占一列，窄屏自动换行 */
.hip-preview--row {
  flex-direction: row;
  flex-wrap: wrap;
}
.hip-preview--row .hip-preview__section {
  flex: 1 1 240px;
  min-width: 240px;
}
.hip-preview__section {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  background: #fff;
  min-width: 0;
}
.hip-preview__label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 8px;
}
.hip-preview__identity-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.hip-preview__avatar {
  position: relative;
  display: inline-flex;
  flex: none;
}
.hip-preview__avatar-img,
.hip-preview__avatar-fallback {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  background: #e5e7eb;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-weight: 600;
}
.hip-preview__avatar--sm {
  width: 24px;
  height: 24px;
  font-size: 12px;
}
.hip-preview__frame {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 124%;
  height: 124%;
  /* 宿主 Tailwind preflight 设有 img { max-width:100%; height:auto }，
     不显式覆盖会把 124% 宽钳制回 100% 导致头像框比例失调与错位 */
  max-width: none;
  max-height: none;
  transform: translate(-50%, -50%);
  object-fit: contain;
  pointer-events: none;
}
.hip-preview__name {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
}
.hip-preview__mark {
  display: inline-flex;
  align-items: center;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1;
  padding: 2px 5px;
  color: #4b5563;
}
/* 身份标识图标（有图标只显图标）。行内场景与主勋章统一 18
   （≈ 预览 14px 正文的 1.25em，模拟 runtime 的 min(1.25em, 20px)） */
.hip-preview__mark-icon {
  width: 18px;
  height: 18px;
  object-fit: contain;
  vertical-align: middle;
}
.hip-preview__title {
  display: inline-flex;
  align-items: center;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  padding: 3px 8px;
  background: #f3f4f6;
  color: #4b5563;
}
.hip-preview__badge {
  width: 18px;
  height: 18px;
  object-fit: contain;
}
/* 整图称号（行内）：行高硬约束锁 20px 上限，宽度随素材比例自适应；
   限宽只作保险丝（200px = 20px 高 × 10:1），与 runtime 默认值保持零漂移 */
.hip-preview__title-img {
  max-height: 20px;
  max-width: 200px;
  height: auto;
  object-fit: contain;
  vertical-align: middle;
}

/* ════════ 用户悬浮卡片（与 runtime hip-user-card 同规则）════════ */
/* 测量层：只提供可用宽度，不裁剪（卡片阴影完整呈现） */
.hip-preview__viewport {
  position: relative;
}
/* 舞台层：按缩放后的视觉尺寸定宽高（脚本注入），水平居中 */
.hip-preview__stage {
  position: relative;
  margin: 0 auto;
}
.hip-preview__card {
  position: relative;
  width: 560px;
  transform-origin: top left;
  border-radius: 12px;
  overflow: hidden;
  background: #f0f1f3;
  /* 预览为平铺展示场景，阴影较 runtime 悬浮态减一档（阴影非装扮内容，不属于同步项），
     避免大投影压出分区边框 */
  box-shadow: 0 3px 14px rgba(31, 35, 40, 0.12);
}
/* 背景层：装扮素材全彩 cover，水平垂直居中（主流锚定）。
   卡片总高恒定（surface 定高），cover 裁切基准 560×296 固定——
   同一素材在编辑预览 / UC / 真卡上构图零漂移 */
.hip-preview__card-bg {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(160deg, #e3e7ec, #dde3ea 55%, #e7e4e0);
  background-size: cover;
  background-position: center;
}
/* 顶部 60px 背景露出带 + 85% 素玻璃内容层（无模糊无渐变） */
.hip-preview__card-inner {
  position: relative;
  padding-top: 60px;
}
.hip-preview__surface {
  position: relative;
  /* 高度定死使卡片总高恒为 296（60 露出带 + 236，取设计稿满配自然高 + 防压缩余量）：
     不随预览内容浮动，背景裁切基准固定；余量由数据行起的元信息组沉底吸收（同步 hip-user-card）。
     ⚠ 不可加 overflow:hidden——头像骑缝上探出 surface 会被裁半 */
  height: 236px;
  background: rgba(255, 255, 255, 0.85);
  border-radius: 12px 12px 0 0;
  padding: 14px 26px 13px;
  display: flex;
  flex-direction: column;
  gap: 9px;
}
/* 头像 96：骑内容层上缘，上半浸在背景露出带里（同步 runtime，用户定稿值勿擅调） */
.hip-preview__avatar--card {
  position: absolute;
  left: 26px;
  top: -48px;
  width: 96px;
  height: 96px;
  font-size: 34px;
}
.hip-preview__avatar--card .hip-preview__avatar-img,
.hip-preview__avatar--card .hip-preview__avatar-fallback {
  box-shadow: 0 3px 10px rgba(11, 18, 32, 0.3);
}
.hip-preview__head {
  margin-left: 112px;
  min-height: 44px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hip-preview__card-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.hip-preview__card-name {
  font-size: 20px;
  font-weight: 600;
  color: #24292f;
  line-height: 1.25;
}
/* 卡片场景主勋章与标识图标统一 20（同排图元尺寸统一；行内场景维持原值，
   同步 runtime 的场景分化默认） */
.hip-preview__card-name-row .hip-preview__badge,
.hip-preview__card-name-row .hip-preview__mark-icon {
  width: 20px;
  height: 20px;
}
/* 卡片场景标识文字牌 12（卡上 meta 统一字号；行内场景维持 11 近似） */
.hip-preview__card-name-row .hip-preview__mark {
  font-size: 12px;
}
/* 称号行：文字 / 整图两形态统一行高（取整图 32px 上限），换形态不改卡内节奏（同步 hip-user-card） */
.hip-preview__title-line {
  display: flex;
  align-items: center;
  min-height: 32px;
}
.hip-preview__card-title {
  display: inline-flex;
  align-items: center;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  padding: 4px 10px;
  background: #f0f1f3;
  color: #57606a;
}
/* 卡片场景整图称号：32px 上限、兼容经典 88×31 原尺寸（同步 hip-user-card 默认值） */
.hip-preview__title-line .hip-preview__title-img {
  max-height: 32px;
  max-width: 100%;
}
/* 个人说明区固定三行：空值占位渲染，卡高与说明内容解耦 */
.hip-preview__bio {
  height: 5.25em;
  font-size: 13.5px;
  color: #38404a;
  line-height: 1.75;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}
.hip-preview__bio--empty {
  color: #a3abb5;
}
/* 数据行：行内串 wrap 扩容，数字等宽；与展柜行成元信息组沉底，
   固定卡高的中部留白落在说明区与本行之间（正文 / 元信息分界处） */
.hip-preview__dstats {
  margin-top: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 22px;
}
.hip-preview__di {
  font-size: 12px;
  color: #8b949e;
}
.hip-preview__di b {
  font-size: 15px;
  color: #24292f;
  font-weight: 600;
  margin-right: 4px;
  font-variant-numeric: tabular-nums;
}
/* 勋章展柜行：34px 方形收藏格，hover 底色加深（无浮动动效）；
   数据行缺席时接棒沉底，有数据行时归零并入元信息组（间距回落 gap 9px） */
.hip-preview__shelf-row {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid rgba(31, 35, 40, 0.1);
  padding-top: 7px;
}
.hip-preview__dstats ~ .hip-preview__shelf-row {
  margin-top: 0;
}
.hip-preview__shelf {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hip-preview__slot {
  width: 34px;
  height: 34px;
  border-radius: 4px;
  background: rgba(31, 35, 40, 0.045);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.12s ease;
}
.hip-preview__slot:hover {
  background: rgba(31, 35, 40, 0.1);
}
.hip-preview__slot img {
  width: 24px;
  height: 24px;
  object-fit: contain;
}
.hip-preview__slot--more {
  font-size: 12px;
  font-weight: 600;
  color: #8b949e;
  background: none;
  border: 1px dashed rgba(31, 35, 40, 0.25);
}
.hip-preview__joined {
  font-size: 12px;
  color: #8b949e;
}
</style>
