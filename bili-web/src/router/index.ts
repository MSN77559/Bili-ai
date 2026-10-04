import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useMessageStore } from '@/stores/message'

/**
 * 路由表（06 §2 v2.0）：14 条路由 + 守卫（Phase 2：/upload 真实化、/anime 重定向、新增 /video /category）。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    // 番剧导航即分区入口（06 §2 v2.0）：/anime 重定向番剧分区页
    { path: '/anime', name: 'anime', redirect: '/category/2' },
    // PG-12 搜索结果页（06 §2 v2.2，Phase 4）：公开，keyword/tab 由 query 驱动
    { path: '/search', name: 'search', component: () => import('@/views/SearchView.vue') },
    // PG-14 消息页（06 §2 v2.2，Phase 4 真实化）：仅本人
    { path: '/messages', name: 'messages', component: () => import('@/views/MessagesView.vue'), meta: { requiresAuth: true } },
    // PG-15 收藏管理页（06 §2 v2.2，Phase 4 真实化）：仅本人；?folder= 夹内容视图
    { path: '/favorites', name: 'favorites', component: () => import('@/views/FavoritesView.vue'), meta: { requiresAuth: true } },
    // PG-13 历史页（06 §2 v2.2，Phase 4 真实化）：仅本人
    { path: '/history', name: 'history', component: () => import('@/views/HistoryView.vue'), meta: { requiresAuth: true } },
    // PG-09 投稿页（Phase 2 真实化，FR-PL01 范围收缩）
    { path: '/upload', name: 'upload', component: () => import('@/views/UploadView.vue'), meta: { requiresAuth: true } },
    // PG-10 播放页 / PG-11 分类页（06 §2 v2.0，Phase 2 新增，公开）
    { path: '/video/:videoId', name: 'video', component: () => import('@/views/VideoPlayerView.vue') },
    { path: '/category/:id', name: 'category', component: () => import('@/views/CategoryView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/u/:userId', name: 'user-space', component: () => import('@/views/UserSpaceView.vue') },
    // PG-08 关注/粉丝列表（06 §2 v1.9）：公开，两路由共用组件
    { path: '/u/:userId/followings', name: 'user-followings', component: () => import('@/views/FollowListView.vue') },
    { path: '/u/:userId/followers', name: 'user-followers', component: () => import('@/views/FollowListView.vue') },
    // PG-07 个人中心（06 §2 v1.9）：嵌套路由，父路由 requiresAuth 子路由继承
    {
      path: '/account',
      component: () => import('@/views/AccountLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/account/info' },
        { path: 'info', name: 'account-info', component: () => import('@/views/AccountInfoView.vue') },
        { path: 'avatar', name: 'account-avatar', component: () => import('@/views/AccountAvatarView.vue') },
        { path: 'banner', name: 'account-banner', component: () => import('@/views/AccountBannerView.vue') },
        { path: 'coin', name: 'account-coin', component: () => import('@/views/AccountCoinView.vue') },
      ],
    },
    // 旧编辑资料页已并入 PG-07：301 重定向保留旧入口（空间页按钮、书签兼容）
    { path: '/settings/profile', name: 'settings-profile', redirect: '/account/info' },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const store = useUserStore()
  // 需登录页：无 token → 跳登录带 redirect（FR-PL01 EX-2 / 03 §5.4）
  if (to.meta.requiresAuth && !store.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 已登录访问 /login → 跳首页（FR-LG04）
  if (to.path === '/login' && store.isLoggedIn) {
    return { path: '/' }
  }
})

// 未读红点时机②（FR-MS03，Phase 4）：每次导航后（仅登录态）再拉一次；失败静默（store 内部吞）
router.afterEach(() => {
  const store = useUserStore()
  if (store.isLoggedIn) {
    void useMessageStore().refresh()
  }
})

export default router
