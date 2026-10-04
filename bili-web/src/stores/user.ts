import { defineStore } from 'pinia'
import type { MyProfile } from '@/types/api'

/**
 * 用户态（03 §5.3）：token + 当前用户摘要。
 * 刷新恢复流程：main.ts 启动时若有 token 调 /users/me 回填（阻塞路由放行）。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('bili_token') as string | null,
    /** 登录态恢复完成标志：NavBar 依据它显示占位防闪烁（FR-AU04） */
    restored: false,
    userId: null as number | null,
    nickname: '' as string,
    avatarUrl: '' as string,
    email: '' as string,
  }),
  getters: {
    isLoggedIn: (s) => !!s.token && !!s.userId,
  },
  actions: {
    setLogin(token: string, user: { userId: number; nickname: string; avatarUrl: string }) {
      this.token = token
      localStorage.setItem('bili_token', token)
      this.applyUser(user.userId, user.nickname, user.avatarUrl)
    },
    applyUser(userId: number, nickname: string, avatarUrl: string) {
      this.userId = userId
      this.nickname = nickname
      this.avatarUrl = avatarUrl
    },
    applyProfile(p: MyProfile) {
      this.userId = p.userId
      this.nickname = p.nickname
      this.avatarUrl = p.avatarUrl
      this.email = p.email
    },
    clearLocal() {
      this.token = null
      this.userId = null
      this.nickname = ''
      this.avatarUrl = ''
      this.email = ''
      localStorage.removeItem('bili_token')
    },
  },
})
