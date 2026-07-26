import { HipElement } from './base'
import {
  AVATAR_FRAME_CSS,
  AVATAR_IMG_CSS,
  avatarHtml,
  escapeCssUrl,
  escapeHtml,
  identityMarksHtml,
  titleHtml,
  nameHtml,
} from './card-parts'

/** 格式化加入时间为「YYYY 年 M 月加入」；非法值返回空串。 */
function formatJoined(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月加入`
}

/** 统计数值展示：≥1 万折算为「x.x万」，其余千分位。 */
function formatCount(value: number): string {
  if (!Number.isFinite(value) || value < 0) {
    return '0'
  }
  if (value >= 10000) {
    const w = value / 10000
    const text = w >= 100 ? Math.round(w).toString() : w.toFixed(1).replace(/\.0$/, '')
    return `${text}万`
  }
  return value.toLocaleString('en-US')
}

/**
 * hip-user-card：完整用户悬浮卡片。
 *
 * 结构（2026-07 重设计，与 Console 预览 DecorationPreview.vue 为同一规则的两份实现）：
 * 整卡背景全彩 cover + 顶部 60px 背景露出带 + 85% 半透明内容层（素玻璃，无模糊无渐变）；
 * 头像 96px 骑内容层上缘；个人说明区固定三行（空值占位渲染，卡高与说明内容解耦）；
 * 数据行按项列表驱动（内置文章 / 评论 / 勋章 + 扩展点贡献项，wrap 扩容）；
 * 勋章展柜行（方形收藏格 + 「+N」计数格）。
 * 风格 token：卡片零彩度（颜色只来自装扮），圆角两档 12/4，字重上限 600。
 * 尺寸恒定 560×296（设计稿 .dev/card-style-system.html 的满配自然高），不随内容浮动。
 *
 * 全平台点击触发（主流论坛用户卡同款：再点触发元素即关闭；键盘 Enter/Space 等效）；
 * 外点、Esc、窄屏滚动关闭。桌面端锚定触发元素并做视口钳制（shift 语义），
 * 窄屏（≤640px）切换为固定全宽卡 + 半透明遮罩（移动端主流形态），
 * 桌面端 560×296 恒定尺寸与内容布局不受影响。
 */
export class HipUserCard extends HipElement {
  /** 窄屏断点：达到即切换为固定全宽卡形态。 */
  private static readonly MOBILE_QUERY = '(max-width: 640px)'

  private cardVisible = false

  private onDocumentClick = (event: MouseEvent) => {
    if (this.cardVisible && !event.composedPath().includes(this)) {
      this.toggleCard(false)
    }
  }

  private onKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && this.cardVisible) {
      this.toggleCard(false)
    }
  }

  /** 窄屏卡为覆盖态，滚动即关（移动端主流行为）。 */
  private onWindowScroll = () => {
    if (this.cardVisible) {
      this.toggleCard(false)
    }
  }

  connectedCallback(): void {
    super.connectedCallback()
    document.addEventListener('click', this.onDocumentClick)
    document.addEventListener('keydown', this.onKeydown)
  }

  disconnectedCallback(): void {
    document.removeEventListener('click', this.onDocumentClick)
    document.removeEventListener('keydown', this.onKeydown)
    window.removeEventListener('scroll', this.onWindowScroll)
  }

  private isMobileViewport(): boolean {
    return window.matchMedia(HipUserCard.MOBILE_QUERY).matches
  }

  private toggleCard(visible: boolean): void {
    this.cardVisible = visible
    if (visible) {
      this.updatePlacement()
    }
    this.shadow.querySelector('.card')?.classList.toggle('card--visible', visible)
    // 遮罩仅窄屏有视觉（CSS 媒体查询控制），类切换统一做，无需分支
    this.shadow.querySelector('.cloak')?.classList.toggle('cloak--visible', visible)
    if (visible && this.isMobileViewport()) {
      window.addEventListener('scroll', this.onWindowScroll, { passive: true })
    } else {
      window.removeEventListener('scroll', this.onWindowScroll)
    }
  }

  /**
   * 桌面端视口防溢出：卡片期望与触发元素左对齐，放不下时向视口内平移
   * （Floating UI shift 的钳制语义：保持在视口内优先于贴住锚点）。
   * 窄屏走 CSS 固定全宽模式，这里只需清掉桌面残留的内联偏移。
   */
  private updatePlacement(): void {
    const card = this.shadow.querySelector<HTMLElement>('.card')
    if (!card) {
      return
    }
    if (this.isMobileViewport()) {
      card.style.left = ''
      return
    }
    const rect = this.getBoundingClientRect()
    const padding = 12
    const cardWidth = card.offsetWidth || Math.min(560, window.innerWidth - padding * 2)
    const clamped = Math.min(
      Math.max(rect.left, padding),
      window.innerWidth - cardWidth - padding,
    )
    // .card 的 left:0 基准即触发元素左缘，内联 left 写入相对钳制偏移
    card.style.left = `${clamped - rect.left}px`
  }

  protected render(): void {
    const identity = this.identity
    const fallbackName = this.getAttribute('user-name') || ''
    if (!identity) {
      // 静默降级：只渲染默认插槽内容（通常是触发元素）
      this.shadow.innerHTML = `
        <style>:host { display: inline-block; position: relative; }</style>
        <slot></slot>
      `
      return
    }

    const display = identity.display
    const marksLimit = display?.userCardIdentityLimit ?? 3
    const showcaseLimit = display?.userCardShowcaseBadgeLimit ?? 5

    const primary = identity.decorations?.primaryBadge
    const primaryHtml = primary?.url
      ? `<img class="badge" src="${escapeHtml(primary.url)}" alt="" title="${escapeHtml(primary.displayName || '')}" loading="lazy" onerror="this.style.display='none'" />`
      : ''

    const backgroundUrl = identity.decorations?.cardBackground?.url
    const backgroundStyle = backgroundUrl
      ? ` style="background-image:url('${escapeHtml(escapeCssUrl(backgroundUrl))}')"`
      : ''

    const marksInner = identityMarksHtml(identity, marksLimit)
    const titleInner = titleHtml(identity)

    // 个人说明区固定三行：空值占位渲染，卡片高度与说明内容解耦、全站结构恒定
    const bioHtml = identity.bio
      ? `<div class="bio">${escapeHtml(identity.bio)}</div>`
      : '<div class="bio bio--empty">TA 还没有留下个人说明</div>'

    // 数据行按项列表驱动：内置三项 + 扩展点贡献项，wrap 扩容；stats 缺失时整行不渲染
    const stats = identity.stats
    const statItems: Array<{ label: string; value: string }> = []
    if (stats) {
      statItems.push({ label: '文章', value: formatCount(stats.posts) })
      statItems.push({ label: '评论', value: formatCount(stats.comments) })
      if (stats.decorations) {
        statItems.push({ label: '勋章', value: formatCount(stats.decorations.badge) })
      }
      for (const extra of stats.extras || []) {
        if (extra?.label && extra?.value) {
          statItems.push({ label: extra.label, value: extra.value })
        }
      }
    }
    const statsHtml = statItems.length
      ? `<div class="dstats">${statItems
          .map(
            (item) =>
              `<span class="di"><b>${escapeHtml(item.value)}</b>${escapeHtml(item.label)}</span>`,
          )
          .join('')}</div>`
      : ''

    // 勋章展柜行：方形收藏格 + 「+N」计数格（勋章总数不可用时不渲染计数格）。
    // 「+N」以展柜非空为门槛：上限设 0（整柜关闭）或无佩戴时不渲染孤立计数格
    // （同步 DecorationPreview 的 shelfMore 口径，两侧改动必须同步）
    const showcase = (identity.decorations?.badgeShowcase || [])
      .filter((badge) => badge?.url)
      .slice(0, showcaseLimit)
    const badgeTotal = stats?.decorations?.badge
    const moreCount =
      showcase.length > 0 && typeof badgeTotal === 'number' && badgeTotal > showcase.length
        ? badgeTotal - showcase.length
        : 0
    const slotsHtml = showcase
      .map(
        (badge) =>
          `<span class="slot" title="${escapeHtml(badge.displayName || '')}"><img src="${escapeHtml(badge.url!)}" alt="${escapeHtml(badge.displayName || '')}" loading="lazy" onerror="this.style.display='none'" /></span>`,
      )
      .join('')
    const moreHtml = moreCount
      ? `<span class="slot slot--more" title="共 ${badgeTotal} 枚">+${moreCount}</span>`
      : ''
    const joinedHtml = identity.registeredAt
      ? `<span class="joined">${escapeHtml(formatJoined(identity.registeredAt))}</span>`
      : ''
    const shelfHtml =
      slotsHtml || moreHtml || joinedHtml
        ? `<div class="shelf-row"><div class="shelf">${slotsHtml}${moreHtml}</div>${joinedHtml}</div>`
        : ''

    this.shadow.innerHTML = `
      <style>
        :host {
          display: inline-block;
          position: relative;
        }
        .trigger {
          cursor: pointer;
          display: inline-flex;
        }
        /* 卡片容器：560 宽大卡，总高恒定（见 .surface 注释） */
        .card {
          position: absolute;
          top: calc(100% + 8px);
          left: 0;
          z-index: 9999;
          width: var(--hip-card-width, 560px);
          max-width: calc(100vw - 24px);
          border-radius: var(--hip-card-radius, 12px);
          background: var(--hip-card-fallback-bg, #f0f1f3);
          box-shadow: var(--hip-card-shadow, 0 8px 28px rgba(31, 35, 40, 0.16));
          overflow: hidden;
          opacity: 0;
          visibility: hidden;
          transform: translateY(4px);
          transition: opacity 0.15s ease, transform 0.15s ease, visibility 0.15s;
        }
        .card--visible {
          opacity: 1;
          visibility: visible;
          transform: translateY(0);
        }
        /* 窄屏遮罩：仅移动端全宽形态有视觉（媒体查询内定义），桌面恒不可见 */
        .cloak {
          display: none;
        }
        /* 窄屏（≤640px）：弃锚定，切固定全宽卡（移动端主流用户卡形态）——
           贴顶、放开定高、内部滚动、半透明遮罩。桌面端布局与恒定尺寸不受此块影响 */
        @media (max-width: 640px) {
          .card {
            position: fixed;
            left: 12px !important; /* 压过桌面钳制写入的内联 left（如跨断点 resize 残留） */
            right: 12px;
            top: 12px;
            width: auto;
            max-width: none;
            max-height: 85vh;
            overflow-y: auto;
          }
          .surface {
            height: auto;
          }
          .cloak--visible {
            display: block;
            position: fixed;
            inset: 0;
            z-index: 9998;
            background: rgba(0, 0, 0, 0.5);
          }
        }
        /* 背景层：装扮素材全彩 cover，水平垂直居中（主流锚定）。
           卡片总高恒定（surface 定高），cover 裁切基准 560×296 固定——
           同一素材在编辑预览 / UC / 真卡上构图零漂移，居中锚定无代价 */
        .card-bg {
          position: absolute;
          inset: 0;
          background-image: var(--hip-card-hero-fallback,
            linear-gradient(160deg, #e3e7ec, #dde3ea 55%, #e7e4e0));
          background-size: cover;
          background-position: center;
        }
        /* 顶部背景露出带：背景唯一完整可见区 */
        .inner {
          position: relative;
          padding-top: var(--hip-card-hero-height, 60px);
        }
        /* 内容层：平色半透明素玻璃（无模糊无渐变），暗色主题覆盖 surface 变量组即可。
           高度定死使卡片总高恒为 296（60 露出带 + 236，取设计稿满配自然高 + 防压缩余量）：
           不随佩戴内容浮动，背景裁切基准固定、全站名片规格统一；余量由展柜行沉底吸收。
           ⚠ 此处不可加 overflow:hidden——头像骑缝上探出 surface，会被裁掉一半；
           极端内容的截断由卡片层的 overflow:hidden 兜底（超出即出卡） */
        .surface {
          position: relative;
          height: var(--hip-card-surface-height, 236px);
          background: var(--hip-card-surface, rgba(255, 255, 255, 0.85));
          border-radius: var(--hip-card-radius, 12px) var(--hip-card-radius, 12px) 0 0;
          padding: 14px 26px 13px;
          display: flex;
          flex-direction: column;
          gap: 9px;
        }
        /* 头像 96：骑内容层上缘，上半浸在背景露出带里（用户定稿值，勿擅调） */
        .avatar-wrap {
          position: absolute;
          left: 26px;
          top: -48px;
          width: 96px;
          height: 96px;
        }
        .avatar {
          width: 100%;
          height: 100%;
          border-radius: 50%;
          object-fit: cover;
          box-shadow: 0 3px 10px rgba(11, 18, 32, 0.3);
        }
        .avatar--fallback {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          background: #e5e7eb;
          color: #6b7280;
          font-weight: 600;
          font-size: 34px;
        }
        ${AVATAR_IMG_CSS}
        ${AVATAR_FRAME_CSS}
        /* 名字块：让位头像（头像宽 96 + 16 间距） */
        .head {
          margin-left: 112px;
          min-height: 44px;
          display: flex;
          flex-direction: column;
          gap: 6px;
        }
        .name-row {
          display: flex;
          align-items: center;
          gap: 8px;
          flex-wrap: wrap;
        }
        .name {
          font-size: 20px;
          font-weight: 600;
          color: var(--hip-card-text, #24292f);
          line-height: 1.25;
        }
        /* 卡片场景主勋章 20（行内场景 18，场景分化默认；⚠ 同步 DecorationPreview） */
        .badge {
          width: var(--hip-badge-size, 20px);
          height: var(--hip-badge-size, 20px);
          object-fit: contain;
        }
        .marks,
        .mark {
          display: inline-flex;
          align-items: center;
        }
        /* 卡上元数据（标识牌 / 称号牌 / 加入时间 / +N）统一 12px：
           对齐主流卡片 meta 字号，且不留亚像素级伪层级 */
        .mark {
          gap: 2px;
          border: 1px solid #d1d5db;
          border-radius: 4px;
          font-size: 12px;
          line-height: 1;
          padding: 2.5px 6px;
          color: var(--hip-card-text-secondary, #57606a);
        }
        .mark img {
          width: 1em;
          height: 1em;
          object-fit: contain;
        }
        /* 卡片场景标识图标与主勋章同 20：同排图元尺寸统一（2px 级差读作不一致
           而非层级，印记语义由紧贴昵称的位置表达）；行内场景 1.25em 相对制不受影响。
           ⚠ 同步 DecorationPreview */
        .mark-icon {
          width: 20px;
          height: 20px;
          object-fit: contain;
          vertical-align: middle;
        }
        .title-row {
          display: flex;
          align-items: center;
        }
        .title {
          display: inline-flex;
          align-items: center;
          border-radius: 4px;
          font-size: 12px;
          line-height: 1;
          padding: 4px 10px;
          background: #f0f1f3;
          color: var(--hip-card-text-secondary, #57606a);
        }
        /* 整图称号：卡片内独占一行，32px 上限兼容经典 88×31 规格原尺寸显示 */
        .title-img {
          max-height: var(--hip-title-img-height, 32px);
          max-width: var(--hip-title-img-max-width, 100%);
          height: auto;
          object-fit: contain;
          vertical-align: middle;
        }
        /* 个人说明区固定三行（13.5 × 1.75 × 3）：有无说明、长短如何，卡高恒定；超三行截断 */
        .bio {
          height: 5.25em;
          font-size: 13.5px;
          color: var(--hip-card-text-secondary, #38404a);
          line-height: 1.75;
          display: -webkit-box;
          -webkit-box-orient: vertical;
          -webkit-line-clamp: 3;
          overflow: hidden;
        }
        .bio--empty {
          color: var(--hip-card-text-muted, #a3abb5);
        }
        /* 数据行：行内串 wrap 扩容，数字等宽 */
        .dstats {
          display: flex;
          flex-wrap: wrap;
          gap: 6px 22px;
        }
        .di {
          font-size: 12px;
          color: var(--hip-card-text-muted, #8b949e);
        }
        .di b {
          font-size: 14px;
          color: var(--hip-card-text, #24292f);
          font-weight: 600;
          margin-right: 4px;
          font-variant-numeric: tabular-nums;
        }
        /* 勋章展柜行：34px 方形收藏格，hover 底色加深（无浮动动效）；
           沉底贴卡底（名片惯例），固定卡高下的中部留白由此吸收 */
        .shelf-row {
          margin-top: auto;
          display: flex;
          align-items: center;
          justify-content: space-between;
          border-top: 1px solid var(--hip-card-line, rgba(31, 35, 40, 0.1));
          padding-top: 7px;
        }
        .shelf {
          display: flex;
          align-items: center;
          gap: 8px;
        }
        .slot {
          width: 34px;
          height: 34px;
          border-radius: 4px;
          background: var(--hip-card-slot-bg, rgba(31, 35, 40, 0.045));
          display: inline-flex;
          align-items: center;
          justify-content: center;
          transition: background 0.12s ease;
        }
        .slot:hover {
          background: var(--hip-card-slot-hover-bg, rgba(31, 35, 40, 0.1));
        }
        .slot img {
          width: 24px;
          height: 24px;
          object-fit: contain;
        }
        .slot--more {
          font-size: 12px;
          font-weight: 600;
          color: var(--hip-card-text-muted, #8b949e);
          background: none;
          border: 1px dashed var(--hip-card-line, rgba(31, 35, 40, 0.25));
        }
        .joined {
          font-size: 12px;
          color: var(--hip-card-text-muted, #8b949e);
        }
      </style>
      <span class="trigger" tabindex="0"><slot>${escapeHtml(identity.displayName)}</slot></span>
      <div class="cloak"></div>
      <div class="card" role="tooltip">
        <div class="card-bg"${backgroundStyle}></div>
        <div class="inner">
          <div class="surface">
            <span class="avatar-wrap">${avatarHtml(identity, fallbackName)}</span>
            <div class="head">
              <div class="name-row">${nameHtml(identity)}${marksInner}${primaryHtml}</div>
              ${titleInner ? `<div class="title-row">${titleInner}</div>` : ''}
            </div>
            ${bioHtml}
            ${statsHtml}
            ${shelfHtml}
          </div>
        </div>
      </div>
    `

    // 全平台点击切换（再点触发元素即关闭）；触发器是 span，需补键盘等效
    // （原 :focus-within 显示规则已随 hover 触发一并移除，Enter/Space 保住键盘可达性）
    const trigger = this.shadow.querySelector('.trigger')
    trigger?.addEventListener('click', (event) => {
      event.stopPropagation()
      this.toggleCard(!this.cardVisible)
    })
    trigger?.addEventListener('keydown', (event) => {
      const key = (event as KeyboardEvent).key
      if (key === 'Enter' || key === ' ') {
        event.preventDefault()
        this.toggleCard(!this.cardVisible)
      }
    })
    // 遮罩在 shadow 内，document 级外点关闭（composedPath 含宿主）拦不到，单独接
    this.shadow.querySelector('.cloak')?.addEventListener('click', () => this.toggleCard(false))
  }
}
