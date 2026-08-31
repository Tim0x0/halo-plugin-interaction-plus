import { HipElement } from './base'
import type { HipComponent } from './hip-data'

/** hip-user-avatar：头像 + 头像框。尺寸由模板 `:host([scene=…])` 决定；调用方可设 `--hip-avatar-size` 覆盖这一棵。 */
export class HipUserAvatar extends HipElement {
  protected get componentKey(): HipComponent {
    return 'avatar'
  }
}
