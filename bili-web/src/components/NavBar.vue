<script setup lang="ts">
// 导航栏（06 §4）：行为矩阵 + 圆形按钮双态 + 悬停下拉退出 + 消息未读红点（Phase 4）
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useMessageStore } from '@/stores/message'
import request from '@/utils/request'
import { defaultAvatar } from '@/utils/avatar'

const router = useRouter()
const store = useUserStore()
const messageStore = useMessageStore()
const keyword = ref('')
const menuOpen = ref(false)

// 导航项（06 §4.2 行为矩阵）：路由守卫负责 requiresAuth 拦截与 redirect
const navItems = [
  { label: '首页', to: '/' },
  { label: '番剧', to: '/anime' },
  { label: '消息', to: '/messages' },
  { label: '收藏', to: '/favorites' },
  { label: '历史', to: '/history' },
]

function isActive(to: string): boolean {
  const path = router.currentRoute.value.path
  // 番剧导航即分区入口（06 §4.2 选中态，2026-09-07 收窄）：仅番剧分区页 /category/2 高亮番剧项，
  // 其余 13 个分区页不再高亮（原 startsWith('/category/') 造成「点任何分类都像跳到番剧」的观感）
  if (to === '/anime') return path === '/category/2'
  return path === to
}

// FR-HM03（Phase 4 真实化）：回车/点放大镜跳 /search?keyword=；空输入无响应、输入保留
function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) return
  router.push({ path: '/search', query: { keyword: kw, tab: 'videos' } })
}

// PG-12：进入搜索页时 NavBar 搜索框回填 keyword（页内改词重新搜索同步）
watch(
  () => router.currentRoute.value,
  (route) => {
    if (route.path === '/search' && typeof route.query.keyword === 'string') {
      keyword.value = route.query.keyword
    }
  },
)

// FR-AU04：圆形按钮双态
function onAvatarClick() {
  if (!store.restored) return // 恢复中占位无动作
  if (store.isLoggedIn) {
    router.push(`/u/${store.userId}`)
  } else {
    router.push('/login')
  }
}

// PG-07 入口：下拉菜单「个人中心」（退出登录之上）
function goAccount() {
  menuOpen.value = false
  router.push('/account/info')
}

// FR-AU03：退出登录（接口尽力而为，失败也本地登出）
async function logout() {
  menuOpen.value = false
  try {
    await request.post('/auth/logout')
  } catch {
    /* EX-1：网络失败不影响本地登出 */
  }
  store.clearLocal()
  messageStore.reset() // 未读红点时机④：登出清零（FR-MS03）
  const current = router.currentRoute.value
  if (current.meta.requiresAuth) {
    router.push('/') // EX-2：退出后停留受限页 → 跳首页
  }
}
</script>

<template>
  <header class="navbar">
    <div class="nav-inner">
      <!-- ① logo（左） -->
      <router-link to="/" class="logo">bili仿站</router-link>

      <!-- ② 分类链接（logo 右，06 §4.1）；消息项带未读红点徽标（FR-MS03，Phase 4） -->
      <nav class="nav-links">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-link"
          :class="{ active: isActive(item.to) }"
        >{{ item.label }}<span
            v-if="item.to === '/messages' && messageStore.hasUnread"
            class="nav-badge"
          >{{ (messageStore.unreadCount ?? 0) > 99 ? '99+' : messageStore.unreadCount }}</span></router-link>
      </nav>

      <!-- ③ 搜索框（居中，FR-HM03 Phase 4 真实化：回车/点放大镜跳 /search） -->
      <div class="search-box">
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索"
          @keyup.enter="onSearch"
        />
        <button class="search-btn" title="搜索" aria-label="搜索" @click="onSearch"></button>
      </div>

      <!-- ④ 用户区（右）：圆形按钮 + 投稿 -->
      <div class="nav-user">
        <!-- 圆形按钮（FR-AU04）：登录注册选项卡即此按钮（06 §4.2 澄清，同一元素） -->
        <div class="avatar-zone" @mouseenter="menuOpen = store.isLoggedIn" @mouseleave="menuOpen = false">
          <button class="round-btn" :title="store.isLoggedIn ? store.nickname : '登录'" @click="onAvatarClick">
            <template v-if="!store.restored"><span class="placeholder-dot" /></template>
            <template v-else-if="store.isLoggedIn">
              <img :src="store.avatarUrl || defaultAvatar" alt="头像" @error="(e: Event) => (e.target as HTMLImageElement).src = defaultAvatar" />
            </template>
            <template v-else>登录</template>
          </button>
          <div v-if="menuOpen" class="dropdown">
            <div class="dd-nickname">{{ store.nickname }}</div>
            <button class="dd-item" @click="goAccount">个人中心</button>
            <button class="dd-item" @click="logout">退出登录</button>
          </div>
        </div>

        <router-link to="/upload" class="upload-btn">投稿</router-link>
      </div>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  height: var(--size-nav-height);
  background: var(--color-card);
  border-bottom: 1px solid var(--color-border);
  position: sticky;
  top: 0;
  z-index: 100;
}
.nav-inner {
  max-width: var(--width-container);
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 16px;
}
.logo {
  color: var(--color-primary);
  font-weight: 700;
  font-size: 20px;
  white-space: nowrap;
}
.nav-links {
  display: flex;
  align-items: center;
  gap: 22px;
  white-space: nowrap;
}
.nav-link {
  color: var(--color-text);
  font-size: 14px;
}
.nav-link:hover {
  color: var(--color-primary);
}
.nav-link.active {
  color: var(--color-primary);
  font-weight: 600;
}
/* 未读红点徽标（FR-MS03）：1~99 显示数字、≥99 显示 99+、0/未知隐藏 */
.nav-badge {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  margin-left: 4px;
  border-radius: 8px;
  background: var(--color-danger);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  vertical-align: 2px;
}
.search-box {
  flex: 1;
  max-width: 440px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  height: 40px;
  padding: 0 4px 0 14px;
  transition: border-color 0.2s;
}
.search-box:focus-within {
  border-color: var(--color-primary);
}
.search-box input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  height: 100%;
  font-size: 14px;
}
/* CSS 放大镜：圆环 + 45° 柄 */
.search-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: none;
  cursor: pointer;
  position: relative;
  border-radius: 6px;
  flex-shrink: 0;
}
.search-btn::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 6px;
  width: 11px;
  height: 11px;
  border: 2px solid var(--color-text-secondary);
  border-radius: 50%;
  box-sizing: border-box;
}
.search-btn::after {
  content: '';
  position: absolute;
  left: 17px;
  top: 17px;
  width: 8px;
  height: 2px;
  background: var(--color-text-secondary);
  transform: rotate(45deg);
}
.search-btn:hover::before {
  border-color: var(--color-primary);
}
.search-btn:hover::after {
  background: var(--color-primary);
}
.nav-user {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-left: auto;
  white-space: nowrap;
}
.avatar-zone {
  position: relative;
}
.round-btn {
  width: var(--size-nav-avatar);
  height: var(--size-nav-avatar);
  border-radius: 50%;
  border: 1px solid var(--color-primary);
  background: var(--color-card);
  color: var(--color-primary);
  font-size: 12px;
  cursor: pointer;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}
.round-btn img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.placeholder-dot {
  width: 100%;
  height: 100%;
  background: var(--color-border);
  border-radius: 50%;
}
.dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  min-width: 120px;
  padding: 8px;
}
.dd-nickname {
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 4px 8px 8px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 4px;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dd-item {
  display: block;
  width: 100%;
  text-align: left;
  background: none;
  border: none;
  padding: 8px;
  font-size: 14px;
  cursor: pointer;
  border-radius: 4px;
}
.dd-item:hover {
  background: var(--color-bg);
}
.upload-btn {
  background: var(--color-primary);
  color: #fff;
  border-radius: 8px;
  padding: 0 16px;
  height: 36px;
  display: flex;
  align-items: center;
  font-size: 14px;
}
.upload-btn:hover {
  background: #f0568a;
}
</style>
