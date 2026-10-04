import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'
import { useMessageStore } from './stores/message'
import { api } from './utils/request'
import type { MyProfile } from './types/api'
import './assets/tokens.css'

/**
 * 启动流程（03 §5.3，FR-AU04）：登录态恢复必须阻塞路由放行。
 */
async function bootstrap() {
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)

  const store = useUserStore()
  if (store.token) {
    try {
      const profile = await api<MyProfile>({ method: 'get', url: '/users/me' })
      store.applyProfile(profile)
      // 未读红点时机①（FR-MS03）：登录态恢复成功后拉一次；失败静默（store 内部吞）
      await useMessageStore().refresh()
    } catch {
      // 401 已由拦截器静默清态（isRecoverUrl 豁免跳转）；网络失败按游客渲染
    }
  }
  store.restored = true

  app.use(router)
  app.mount('#app')
}

bootstrap()
