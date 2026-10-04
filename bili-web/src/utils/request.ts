import axios, { AxiosError } from 'axios'
import type { ApiResult } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { useMessageStore } from '@/stores/message'
import router from '@/router'

/**
 * axios 实例与拦截器（03 §5.2/§5.4）：
 * - 请求拦截：附加 Bearer
 * - 响应拦截：40101/40102/40103 统一清态跳登录；
 *   豁免：登录/注册/忘记密码接口（表单内联展示，不全局跳转）、
 *   /api/users/me 的恢复请求（静默清态，03 §5.3）
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 豁免全局 401 处理的接口（05 §7 注意事项 2）
const AUTH_URLS = ['/auth/login', '/auth/register', '/auth/password/reset-code', '/auth/password/verify-code', '/auth/password/reset']
const isAuthUrl = (url?: string) => !!url && AUTH_URLS.some((u) => url.includes(u))
const isRecoverUrl = (url?: string) =>
  !!url && url.includes('/users/me') && !url.includes('avatar') && !url.includes('banner')

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('bili_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (resp) => resp,
  (error: AxiosError<ApiResult<unknown>>) => {
    const code = error.response?.data?.code
    const url = error.config?.url
    if (code === 40101 || code === 40102 || code === 40103) {
      const store = useUserStore()
      store.clearLocal()
      useMessageStore().reset() // 未读红点时机④（FR-MS03）：登出/失效清零
      // 登录态恢复：只清态不跳转（03 §5.3）
      if (isRecoverUrl(url)) {
        // 静默
      } else if (!isAuthUrl(url)) {
        // 全局 401：跳登录并带 redirect（03 §5.4）
        const current = router.currentRoute.value
        const redirect = current.path === '/login' ? undefined : current.fullPath
        router.push({ path: '/login', query: redirect ? { redirect } : {} })
      }
    }
    return Promise.reject(error)
  },
)

/** 统一请求入口：直接返回 data 字段，业务错误转 reject（message 可读） */
export async function api<T>(config: Parameters<typeof request.request>[0]): Promise<T> {
  const resp = await request.request<ApiResult<T>>(config)
  const body = resp.data
  if (body.code !== 0) {
    throw Object.assign(new Error(body.message || '请求失败'), { code: body.code })
  }
  return body.data
}

export default request
