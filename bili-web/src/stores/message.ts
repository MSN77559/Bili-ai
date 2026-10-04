import { defineStore } from 'pinia'
import { getUnreadCount } from '@/api'

/**
 * 消息未读态（FR-MS03，Phase 4 / ADR-19）：仅未读数 + 红点，独立轻量 store（不膨胀 user store）。
 * 无轮询四时机刷新（TD-14「打开页一次拉齐」哲学，不引入 WebSocket/轮询）：
 * ① 登录态恢复成功后（main.ts 既有 await 恢复点之后）；
 * ② router.afterEach（仅登录态）再拉；
 * ③ 消息页内已读操作本地即时减算；
 * ④ 退出登录清零。
 * 失败一律静默（红点隐藏，不 toast 不阻塞导航）。
 */
export const useMessageStore = defineStore('message', {
  state: () => ({
    /** null=未知（不渲染红点）；>0 渲染数值/圆点 */
    unreadCount: null as number | null,
  }),
  getters: {
    hasUnread: (s) => (s.unreadCount ?? 0) > 0,
  },
  actions: {
    /** 拉一次未读数（时机①②）；未登录/失败静默置 null */
    async refresh() {
      try {
        const resp = await getUnreadCount()
        this.unreadCount = resp.unreadCount
      } catch {
        this.unreadCount = null // 静默：红点隐藏
      }
    },
    /** 页内单条已读后本地减算（时机③；服务端为准，减到 0 停） */
    decrement() {
      if (this.unreadCount !== null && this.unreadCount > 0) {
        this.unreadCount--
      }
    },
    /** 全部已读后置 0（时机③） */
    clearAll() {
      this.unreadCount = 0
    },
    /** 退出登录清零（时机④） */
    reset() {
      this.unreadCount = null
    },
  },
})
