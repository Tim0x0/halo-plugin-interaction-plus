import { HipElement } from './base'
import { avatarHtml, AVATAR_FRAME_CSS, AVATAR_IMG_CSS } from './card-parts'

/**
 * hip-user-avatar：头像 + 头像框。
 * 头像框跟随头像容器自动缩放，不写死像素尺寸。
 *
 * 属性：user-name / data / size（可选，默认继承容器，如 "40px"）
 */
export class HipUserAvatar extends HipElement {
  static get observedAttributes(): string[] {
    return [...super.observedAttributes, 'size']
  }

  protected render(): void {
    const size = this.getAttribute('size') || '40px'
    const fallbackName = this.getAttribute('user-name') || ''

    this.shadow.innerHTML = `
      <style>
        :host {
          display: inline-block;
          position: relative;
          width: ${size};
          height: ${size};
          vertical-align: middle;
        }
        .wrapper {
          /* 必须 block：span 默认 inline 时 width/height 失效，
             绝对定位的 frame/img.avatar 包含块会塌成字体行高（框显示为头像的一半） */
          display: block;
          position: relative;
          width: 100%;
          height: 100%;
        }
        .avatar {
          width: 100%;
          height: 100%;
          border-radius: 50%;
          object-fit: cover;
          display: inline-flex;
          align-items: center;
          justify-content: center;
        }
        .avatar--fallback {
          background: #e5e7eb;
          color: #6b7280;
          font-weight: 600;
          font-size: calc(${size} / 2.2);
        }
        ${AVATAR_IMG_CSS}
        ${AVATAR_FRAME_CSS}
      </style>
      <span class="wrapper">${avatarHtml(this.identity, fallbackName)}</span>
    `
  }
}
