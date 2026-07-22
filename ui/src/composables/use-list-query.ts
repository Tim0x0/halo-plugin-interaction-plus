// 列表查询 composable：统一消化审查报告 F2（乱序保护）、F3（分页双请求）、
// F5（错误态与重试）、F11（删除当前页最后一条后回退页码）
import { ref, shallowRef } from 'vue'
import type { ListResult } from '@/types'

export interface ListQueryFetchParams {
  page: number
  size: number
}

export interface UseListQueryOptions {
  /** 默认每页数量 */
  defaultSize?: number
}

/**
 * 分页列表加载逻辑。
 *
 * - 模块级自增请求序号，仅最新请求的响应写入状态（防快速筛选 / 翻页乱序）。
 * - 失败时置 error 状态，由页面渲染错误态与重试入口；不再依赖拦截器静默。
 * - 分页只消费 VPagination 的 `change` 事件（payload 含最终 page/size，单次 emit），
 *   避免 update:page / update:size 连发导致的双请求与脏请求。
 */
export function useListQuery<T>(
  fetcher: (params: ListQueryFetchParams) => Promise<ListResult<T>>,
  options: UseListQueryOptions = {},
) {
  const items = shallowRef<T[]>([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(options.defaultSize ?? 20)
  const loading = ref(false)
  const error = ref(false)

  let requestSeq = 0

  async function load() {
    const seq = ++requestSeq
    loading.value = true
    error.value = false
    try {
      const result = await fetcher({ page: page.value, size: size.value })
      if (seq !== requestSeq) return
      items.value = result.items
      total.value = result.total
    } catch (e) {
      if (seq !== requestSeq) return
      error.value = true
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  /** 筛选条件变化：回到第 1 页重新加载。 */
  function search() {
    page.value = 1
    load()
  }

  /** VPagination @change 事件处理（只监听 change，单次加载）。 */
  function onPaginationChange(value: { page: number; size: number }) {
    page.value = value.page
    size.value = value.size
    load()
  }

  /**
   * 删除 / 状态转换使条目离开当前页后调用：
   * 当前页只剩被移除的条目且不在第 1 页时回退一页，避免停留空页。
   */
  function reloadAfterRemove(removedCount = 1) {
    if (items.value.length <= removedCount && page.value > 1) {
      page.value -= 1
    }
    load()
  }

  return {
    items,
    total,
    page,
    size,
    loading,
    error,
    load,
    search,
    onPaginationChange,
    reloadAfterRemove,
  }
}
