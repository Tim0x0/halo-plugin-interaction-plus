import { axiosInstance } from '@halo-dev/api-client'
import type {
  CustomTemplate,
  CustomTemplateParam,
  DecorationAsset,
  DecorationAssetParam,
  DecorationGrant,
  DecorationMetadata,
  GrantParam,
  GrantResult,
  GrantView,
  IdentityMarkMappingParam,
  IdentityMarkMappingView,
  InventoryItem,
  ListResult,
  MetadataOptions,
  MetadataParam,
  ProfileSaveParam,
  ProfileView,
  RoleGrantParam,
} from '@/types'

export const CONSOLE_BASE = '/apis/console.api.interaction-plus.timxs.com/v1alpha1'
const UC_BASE = '/apis/uc.api.interaction-plus.timxs.com/v1alpha1'

export interface ListQuery {
  page?: number
  size?: number
  sort?: string
  keyword?: string
  [key: string]: string | number | boolean | undefined
}

// ── Console：资产 ──────────────────────────────────────

export const assetApi = {
  list: (params: ListQuery = {}) =>
    axiosInstance
      .get<ListResult<DecorationAsset>>(`${CONSOLE_BASE}/assets`, { params })
      .then((res) => res.data),
  get: (name: string) =>
    axiosInstance.get<DecorationAsset>(`${CONSOLE_BASE}/assets/${name}`).then((res) => res.data),
  create: (param: DecorationAssetParam) =>
    axiosInstance.post<DecorationAsset>(`${CONSOLE_BASE}/assets`, param).then((res) => res.data),
  update: (name: string, param: DecorationAssetParam) =>
    axiosInstance
      .put<DecorationAsset>(`${CONSOLE_BASE}/assets/${name}`, param)
      .then((res) => res.data),
  remove: (name: string) => axiosInstance.delete(`${CONSOLE_BASE}/assets/${name}`),
  /** 有效授予数（删除确认提示影响面用） */
  activeGrantCount: (name: string) =>
    axiosInstance
      .get<{ count: number }>(`${CONSOLE_BASE}/assets/${name}/active-grant-count`)
      .then((res) => res.data.count),
  activate: (name: string) =>
    axiosInstance
      .post<DecorationAsset>(`${CONSOLE_BASE}/assets/${name}/activate`)
      .then((res) => res.data),
  disable: (name: string) =>
    axiosInstance
      .post<DecorationAsset>(`${CONSOLE_BASE}/assets/${name}/disable`)
      .then((res) => res.data),
  archive: (name: string) =>
    axiosInstance
      .post<DecorationAsset>(`${CONSOLE_BASE}/assets/${name}/archive`)
      .then((res) => res.data),
  unarchive: (name: string) =>
    axiosInstance
      .post<DecorationAsset>(`${CONSOLE_BASE}/assets/${name}/unarchive`)
      .then((res) => res.data),
}

// ── Console：授予 ──────────────────────────────────────

export const grantApi = {
  list: (params: ListQuery = {}) =>
    axiosInstance
      .get<ListResult<GrantView>>(`${CONSOLE_BASE}/grants`, { params })
      .then((res) => res.data),
  grant: (param: GrantParam) =>
    axiosInstance.post<GrantResult>(`${CONSOLE_BASE}/grants`, param).then((res) => res.data),
  grantByRoleSnapshot: (param: RoleGrantParam) =>
    axiosInstance
      .post<GrantResult>(`${CONSOLE_BASE}/grants/-/grant-by-role-snapshot`, param)
      .then((res) => res.data),
  revoke: (name: string, reason?: string) =>
    axiosInstance
      .post<DecorationGrant>(`${CONSOLE_BASE}/grants/${name}/revoke`, { reason })
      .then((res) => res.data),
  /** 全部撤销：撤销某用户某装饰全部来源的有效授予（彻底收回） */
  revokeHeld: (userName: string, assetName: string, reason?: string) =>
    axiosInstance
      .post<{ revokedCount: number }>(`${CONSOLE_BASE}/grants/-/revoke-held`, {
        userName,
        assetName,
        reason,
      })
      .then((res) => res.data),
}

// ── Console：元数据 ────────────────────────────────────

function metadataApiOf(resource: 'categories' | 'tags' | 'rarities') {
  return {
    list: (params: ListQuery = {}) =>
      axiosInstance
        .get<ListResult<DecorationMetadata>>(`${CONSOLE_BASE}/${resource}`, { params })
        .then((res) => res.data),
    create: (param: MetadataParam) =>
      axiosInstance
        .post<DecorationMetadata>(`${CONSOLE_BASE}/${resource}`, param)
        .then((res) => res.data),
    update: (name: string, param: MetadataParam) =>
      axiosInstance
        .put<DecorationMetadata>(`${CONSOLE_BASE}/${resource}/${name}`, param)
        .then((res) => res.data),
    remove: (name: string) => axiosInstance.delete(`${CONSOLE_BASE}/${resource}/${name}`),
    /** 引用数（删除确认提示影响面用） */
    usageCount: (name: string) =>
      axiosInstance
        .get<{ count: number }>(`${CONSOLE_BASE}/${resource}/${name}/usage-count`)
        .then((res) => res.data.count),
  }
}

export const categoryApi = metadataApiOf('categories')
export const tagApi = metadataApiOf('tags')
export const rarityApi = metadataApiOf('rarities')

// ── Console：身份标识 ──────────────────────────────────

export const identityMarkApi = {
  list: (params: ListQuery = {}) =>
    axiosInstance
      .get<ListResult<IdentityMarkMappingView>>(`${CONSOLE_BASE}/identity-mark-mappings`, {
        params,
      })
      .then((res) => res.data),
  create: (param: IdentityMarkMappingParam) =>
    axiosInstance.post(`${CONSOLE_BASE}/identity-mark-mappings`, param).then((res) => res.data),
  update: (name: string, param: IdentityMarkMappingParam) =>
    axiosInstance
      .put(`${CONSOLE_BASE}/identity-mark-mappings/${name}`, param)
      .then((res) => res.data),
  remove: (name: string) => axiosInstance.delete(`${CONSOLE_BASE}/identity-mark-mappings/${name}`),
}

// ── Console：自定义模板 ────────────────────────────────

export const customTemplateApi = {
  /** 三条固定记录（identity / avatar / card），含未启用 */
  list: () =>
    axiosInstance.get<CustomTemplate[]>(`${CONSOLE_BASE}/custom-templates`).then((res) => res.data),
  /** 按组件保存；无创建 / 删除接口，三条是固定槽位 */
  save: (component: string, param: CustomTemplateParam) =>
    axiosInstance
      .put<CustomTemplate>(`${CONSOLE_BASE}/custom-templates/${component}`, param)
      .then((res) => res.data),
}

// ── UC ────────────────────────────────────────────────

export const ucApi = {
  inventory: () =>
    axiosInstance.get<InventoryItem[]>(`${UC_BASE}/inventory`).then((res) => res.data),
  getProfile: () => axiosInstance.get<ProfileView>(`${UC_BASE}/profile`).then((res) => res.data),
  saveProfile: (param: ProfileSaveParam) =>
    axiosInstance.put<ProfileView>(`${UC_BASE}/profile`, param).then((res) => res.data),
  /** 仅更新「公开装扮墙」开关（不影响佩戴） */
  updateVisibility: (publicDecorationsVisible: boolean) =>
    axiosInstance
      .put<ProfileView>(`${UC_BASE}/profile/visibility`, { publicDecorationsVisible })
      .then((res) => res.data),
  listSubmissions: (params: ListQuery = {}) =>
    axiosInstance
      .get<ListResult<DecorationAsset>>(`${UC_BASE}/submissions`, { params })
      .then((res) => res.data),
  createSubmission: (param: DecorationAssetParam) =>
    axiosInstance.post<DecorationAsset>(`${UC_BASE}/submissions`, param).then((res) => res.data),
  updateSubmission: (name: string, param: DecorationAssetParam) =>
    axiosInstance
      .put<DecorationAsset>(`${UC_BASE}/submissions/${name}`, param)
      .then((res) => res.data),
  deleteSubmission: (name: string) => axiosInstance.delete(`${UC_BASE}/submissions/${name}`),
  metadataOptions: () =>
    axiosInstance.get<MetadataOptions>(`${UC_BASE}/metadata`).then((res) => res.data),
}
