// 「加载更多」分页组合式函数（Phase 2，FR-VD03 范式）：
// 首屏 page=1 + 追加下一页（不替换）；无更多（已加载 ≥ total 或空 list）隐藏按钮；
// 首次失败错误态可重试、追加失败由调用方 toast（已加载内容保留）。
// seq 令牌防过期响应：切分类/切用户时旧请求返回被丢弃。
import { computed, ref } from 'vue'
import type { Ref } from 'vue'

export function useLoadMore<T>(fetcher: (page: number) => Promise<{ total: number; list: T[] }>) {
  const items = ref<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const page = ref(0) // 已加载到的页码（0=未加载）
  const loading = ref(false)
  const error = ref('')
  const loaded = ref(false) // 首次加载是否完成（区分「初始错误」与「空数据」）
  let seq = 0 // 过期响应防护

  async function run(reset: boolean): Promise<boolean> {
    if (loading.value) return false
    const my = ++seq
    loading.value = true
    error.value = ''
    try {
      const resp = await fetcher(reset ? 1 : page.value + 1)
      if (my !== seq) return false // 已被新一次加载取代，丢弃
      page.value = reset ? 1 : page.value + 1
      total.value = resp.total
      items.value = reset ? resp.list : items.value.concat(resp.list)
      loaded.value = true
      return true
    } catch (e) {
      if (my === seq) error.value = (e as Error).message || '加载失败'
      return false
    } finally {
      if (my === seq) loading.value = false
    }
  }

  /** 首屏/重置（重试、切分类、切用户） */
  const loadFirst = () => run(true)
  /** 追加下一页 */
  const loadMore = () => run(false)

  /** 是否还有下一页（EX-4 恰好整页：total=12 已加载 12 → false） */
  const hasMore = computed(() => items.value.length < total.value)

  return { items, total, page, loading, error, loaded, hasMore, loadFirst, loadMore }
}
