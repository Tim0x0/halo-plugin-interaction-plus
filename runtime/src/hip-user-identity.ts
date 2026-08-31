import { HipElement } from './base'
import type { HipComponent } from './hip-data'

/** hip-user-identity：昵称 + 身份标识 + 称号 + 主勋章。 */
export class HipUserIdentity extends HipElement {
  protected get componentKey(): HipComponent {
    return 'identity'
  }
}
