import { HipElement } from './base'
import type { HipComponent } from './hip-data'

/** hip-user-card：用户卡。交互与样式都在内置模板里。 */
export class HipUserCard extends HipElement {
  protected get componentKey(): HipComponent {
    return 'card'
  }
}
