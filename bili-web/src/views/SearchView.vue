<script setup lang="ts">
// PG-12 搜索结果页（06 §3.12，Phase 4）：/search?keyword=&tab=videos|users 公开
// 页内搜索框回写 URL query（可分享/可回退）；视频 tab 复用 VideoCard 网格 + 加载更多；
// 用户 tab 复用 FO03 条目行形态（头像/昵称/签名/关注三态）+ 翻页。
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchVideos, searchUsers, followUser, unfollowUser } from '@/api'
import type { SearchUserItem, VideoItem } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { useLoadMore } from '@/composables/useLoadMore'
import { defaultAvatar } from '@/utils/avatar'
import { toast } from '@/utils/toast'
import SearchVideoCard from '@/components/SearchVideoCard.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// ---- URL query 为 SSOT：keyword / tab（缺省 videos）----
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const tab = ref<'videos' | 'users'>(route.query.tab === 'users' ? 'users' : 'videos')

// ---- 视频 tab：useLoadMore（初始 size=12，追加下一页）----
const PAGE_SIZE = 12
const videos = useLoadMore<VideoItem>((page) => searchVideos(keyword.value, page, PAGE_SIZE))

// ---- 用户 tab：翻页范式（FollowListView 同款）----
const users = ref<SearchUserItem[]>([])
const userTotal = ref(0)
const userPage = ref(1)
const USER_SIZE = 20
const userLoading = ref(false)
const userError = ref('')
const userTotalPages = computed(() => Math.max(1, Math.ceil(userTotal.value / USER_SIZE)))

// ---- 行内关注按钮请求中集合 ----
const actingIds = ref<Set<number>>(new Set())

// 页面三态（视频 tab 用 useLoadMore 自带；用户 tab 用 userError/userLoading）
const firstLoading = ref(true) // 首次加载遮罩
const pageError = ref('') // 致命错误（关键词非法等首次请求失败）

async function loadUserList() {
  userLoading.value = true
  userError.value = ''
  try {
    const resp = await searchUsers(keyword.value, userPage.value, USER_SIZE)
    userTotal.value = resp.total
    users.value = resp.list
  } catch (e) {
    userError.value = (e as Error).message || '加载失败'
    users.value = []
  } finally {
    userLoading.value = false
  }
}

// 入口加载：按当前 tab 拉对应结果
async function load() {
  firstLoading.value = true
  pageError.value = ''
  if (tab.value === 'videos') {
    const ok = await videos.loadFirst()
    if (!ok) pageError.value = videos.error.value || '搜索失败'
    firstLoading.value = false
  } else {
    userPage.value = 1
    await loadUserList()
    firstLoading.value = false
    if (userError.value) pageError.value = userError.value
  }
}

// 改词重新搜索：回写 URL query（可分享/可回退）；page 重置
function onSearch() {
  const kw = keyword.value.trim()
  if (!kw) return // 空输入无响应（FR-HM03）
  keyword.value = kw
  router.push({ path: '/search', query: { keyword: kw, tab: tab.value } })
}

// tab 切换：更新 query（关键词保留、结果重拉、page 重置）
function switchTab(t: 'videos' | 'users') {
  if (t === tab.value) return
  router.push({ path: '/search', query: { keyword: keyword.value, tab: t } })
}

// 用户 tab 翻页
function goPage(p: number) {
  if (p < 1 || p > userTotalPages.value || p === userPage.value) return
  userPage.value = p
  loadUserList()
  window.scrollTo({ top: 0 })
}

// 行内关注按钮（FO03 行内范式：游客跳登录带回跳）
async function onToggleFollow(item: SearchUserItem) {
  if (actingIds.value.has(item.userId)) return
  if (!store.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  actingIds.value.add(item.userId)
  try {
    const resp = item.isFollowed ? await unfollowUser(item.userId) : await followUser(item.userId)
    item.isFollowed = resp.isFollowed
  } catch (e) {
    toast((e as Error).message || '操作失败')
  } finally {
    actingIds.value.delete(item.userId)
  }
}

function goSpace(userId: number) {
  router.push(`/u/${userId}`)
}

async function onVideoLoadMore() {
  const ok = await videos.loadMore()
  if (!ok && videos.error) toast('加载失败，请重试')
}

onMounted(() => {
  if (keyword.value.trim()) {
    load()
  } else {
    // 无关键词（直接访问 /search）：空态展示，不发请求
    firstLoading.value = false
  }
})

// URL query 变化（改词/切 tab/回退/分享进入）→ 重拉
watch(
  () => [route.query.keyword, route.query.tab],
  ([kw, t]) => {
    keyword.value = typeof kw === 'string' ? kw : ''
    tab.value = t === 'users' ? 'users' : 'videos'
    if (keyword.value.trim()) {
      load()
    } else {
      firstLoading.value = false
    }
  },
)
</script>

<template>
  <div class="search-page container">
    <!-- 页内搜索框：初值 = query.keyword；回写 URL -->
    <div class="search-row">
      <div class="search-box">
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索视频或用户"
          maxlength="50"
          @keyup.enter="onSearch"
        />
        <button class="search-btn" @click="onSearch">搜索</button>
      </div>
    </div>

    <!-- tab 行：视频 / 用户（缺省视频；切换保留关键词） -->
    <div class="tab-row" v-if="keyword.trim()">
      <button class="tab" :class="{ on: tab === 'videos' }" @click="switchTab('videos')">视频</button>
      <button class="tab" :class="{ on: tab === 'users' }" @click="switchTab('users')">用户</button>
    </div>

    <!-- 首次加载态 -->
    <div v-if="firstLoading" class="state-box">搜索中…</div>

    <!-- 错误态（重试） -->
    <div v-else-if="pageError" class="state-box">
      <p>{{ pageError }}</p>
      <button class="retry-btn" @click="load">重试</button>
    </div>

    <!-- 空态：无关键词或无结果 -->
    <div v-else-if="!keyword.trim()" class="state-box">输入关键词开始搜索</div>

    <!-- 视频 tab -->
    <template v-else-if="tab === 'videos'">
      <div v-if="videos.items.value.length === 0" class="state-box">什么都没找到</div>
      <template v-else>
        <div class="result-list">
          <SearchVideoCard v-for="v in videos.items.value" :key="v.videoId" :video="v" />
        </div>
        <div v-if="videos.hasMore.value" class="load-more-zone">
          <button class="load-more-btn" :disabled="videos.loading.value" @click="onVideoLoadMore">
            {{ videos.loading.value ? '加载中…' : '加载更多' }}
          </button>
        </div>
      </template>
    </template>

    <!-- 用户 tab -->
    <template v-else>
      <div v-if="userLoading" class="state-box">搜索中…</div>
      <div v-else-if="users.length === 0" class="state-box">什么都没找到</div>
      <div v-else class="user-card">
        <ul class="list">
          <li v-for="item in users" :key="item.userId" class="row" @click="goSpace(item.userId)">
            <img
              class="row-avatar"
              :src="item.avatarUrl || defaultAvatar"
              alt="头像"
              @error="(e: Event) => (e.target as HTMLImageElement).src = defaultAvatar"
            />
            <div class="row-info">
              <p class="row-nickname">{{ item.nickname }}</p>
              <p v-if="item.sign" class="row-sign">{{ item.sign }}</p>
            </div>
            <button
              v-if="store.userId !== item.userId"
              class="row-btn"
              :class="item.isFollowed ? 'outline' : 'solid'"
              :disabled="actingIds.has(item.userId)"
              @click.stop="onToggleFollow(item)"
            >
              {{ actingIds.has(item.userId) ? '…' : item.isFollowed ? '已关注' : '+ 关注' }}
            </button>
          </li>
        </ul>
        <div v-if="userTotal > USER_SIZE" class="pager">
          <button class="page-btn" :disabled="userPage <= 1" @click="goPage(userPage - 1)">上一页</button>
          <span class="page-info">第 {{ userPage }} / {{ userTotalPages }} 页</span>
          <button class="page-btn" :disabled="userPage >= userTotalPages" @click="goPage(userPage + 1)">下一页</button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.container {
  max-width: var(--width-container);
  margin: 0 auto;
  padding: var(--space-block);
}
.search-row {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}
.search-box {
  width: 100%;
  max-width: 560px;
  display: flex;
  align-items: center;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  height: 42px;
  padding-left: 14px;
  overflow: hidden;
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
.search-btn {
  height: 100%;
  padding: 0 20px;
  border: none;
  background: var(--color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.search-btn:hover {
  background: #f0568a;
}
.tab-row {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 20px;
}
.tab {
  border: none;
  background: none;
  font-size: 15px;
  color: var(--color-text-secondary);
  padding: 8px 2px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.tab.on {
  color: var(--color-primary);
  font-weight: 600;
  border-bottom-color: var(--color-primary);
}
.state-box {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--color-text-secondary);
}
.retry-btn {
  padding: 8px 24px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  border-radius: var(--radius-card);
  background: none;
  cursor: pointer;
}
.retry-btn:hover {
  background: var(--color-primary);
  color: #fff;
}
/* 视频 tab 结果：两列横向卡片（2026-09-07 排版修复，对齐真实 B 站搜索页形态） */
.result-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-card-gap);
}
@media (max-width: 720px) {
  .result-list {
    grid-template-columns: 1fr;
  }
}
.load-more-zone {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
.load-more-btn {
  padding: 10px 48px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  border-radius: var(--radius-card);
  cursor: pointer;
  font-size: 14px;
}
.load-more-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.load-more-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.user-card {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 8px 24px;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 8px;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}
.row:last-child {
  border-bottom: none;
}
.row:hover {
  background: var(--color-bg);
}
.row-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.row-info {
  flex: 1;
  min-width: 0;
}
.row-nickname {
  margin: 0;
  font-size: 14px;
}
.row-sign {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-btn {
  height: 30px;
  padding: 0 18px;
  border-radius: 999px;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}
.row-btn.solid {
  background: var(--color-primary);
  color: #fff;
  border: none;
}
.row-btn.outline {
  background: var(--color-card);
  color: var(--color-text);
  border: 1px solid var(--color-border);
}
.row-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 16px 0;
}
.page-btn {
  padding: 6px 16px;
  border: 1px solid var(--color-border);
  background: none;
  border-radius: var(--radius-card);
  font-size: 13px;
  cursor: pointer;
}
.page-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.page-info {
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
