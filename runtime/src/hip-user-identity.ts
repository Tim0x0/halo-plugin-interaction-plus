import { HipElement } from './base'
import { escapeHtml } from './identity'
import { BOX_SIZING_CSS, identityMarksHtml, nameHtml, titleHtml } from './card-parts'

/**
 * hip-user-identity：昵称（含样式）+ 身份标识 + 称号 + 主勋章。
 * 对应 identity_line 轻量身份行场景。
 */
export class HipUserIdentity extends HipElement {
  protected render(): void {
    const identity = this.identity
    if (!identity) {
      // 静默降级：只显示用户名
      const fallback = this.getAttribute('user-name') || ''
      this.shadow.innerHTML = `
        <style>:host { display: inline-flex; align-items: center; }</style>
        <span>${escapeHtml(fallback)}</span>
      `
      return
    }
    const display = identity.display
    const marksLimit = display?.identityLineIdentityLimit ?? 1
    const showBadge = display?.identityLineShowPrimaryBadge !== false
    const badge = identity.decorations?.primaryBadge
    const badgeHtml =
      showBadge && badge?.url
        ? `<img class="badge" src="${escapeHtml(badge.url)}" alt="${escapeHtml(badge.displayName || '')}" title="${escapeHtml(badge.displayName || '')}" loading="lazy" onerror="this.style.display='none'" />`
        : ''

    this.shadow.innerHTML = `
      <style>
        ${BOX_SIZING_CSS}
        :host {
          display: inline-flex;
          align-items: center;
          gap: var(--hip-identity-gap, 4px);
          vertical-align: middle;
        }
        .name {
          font-weight: 500;
          color: var(--hip-text-color, inherit);
        }
        .mark {
          display: inline-flex;
          align-items: center;
          gap: 2px;
          border: 1px solid #d1d5db;
          border-radius: var(--hip-title-radius, 4px);
          font-size: 0.75em;
          line-height: 1;
          padding: 2px 5px;
          color: var(--hip-muted-color, #6b7280);
        }
        .mark img {
          width: 1em;
          height: 1em;
          object-fit: contain;
        }
        /* 行内图元（标识图标 / 主勋章 / 整图称号上限）统一 1.25em 纯相对制：
           同排尺寸一致、随正文字号缩放；正文行高普遍 ≥1.4 > 1.25，
           故任何字号下都不撑高文字行——不撑行的真约束是相对的，勿加绝对封顶 */
        .mark-icon {
          width: 1.25em;
          height: 1.25em;
          object-fit: contain;
          vertical-align: middle;
        }
        .title {
          display: inline-flex;
          align-items: center;
          border-radius: var(--hip-title-radius, 4px);
          font-size: 0.75em;
          line-height: 1;
          padding: 2px 6px;
          background: #f3f4f6;
          color: var(--hip-muted-color, #6b7280);
        }
        /* 整图称号：上限默认 1.25em（16px 正文下即 20px，随正文缩放不撑行），
           宽度随素材比例自适应；限宽只作保险丝（200px ≈ 20px 高 × 10:1），
           正常横幅素材不应撞到 */
        .title-img {
          max-height: var(--hip-title-img-height, 1.25em);
          max-width: var(--hip-title-img-max-width, 200px);
          height: auto;
          object-fit: contain;
          vertical-align: middle;
        }
        .badge {
          width: var(--hip-badge-size, 1.25em);
          height: var(--hip-badge-size, 1.25em);
          object-fit: contain;
        }
      </style>
      ${nameHtml(identity)}
      ${identityMarksHtml(identity, marksLimit)}
      ${titleHtml(identity)}
      ${badgeHtml}
    `
  }
}
