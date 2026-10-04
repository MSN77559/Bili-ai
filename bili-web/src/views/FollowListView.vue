<script setup lang="ts">
// PG-08 关注/粉丝列表页（06 §3.8）：/u/:userId/followings 与 /followers 共用（FR-FO05）
// 页头复用 API-US02（昵称/计数）；列表调 API-FO03/FO04（公开分页）
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPublicProfile, getFollowings, getFollowers, followUser, unfollowUser } from '@/api'
import type { FollowPageItem } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { defaultAvatar } from '@/utils/avatar'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// ---- tab：由路由 name 推导（06 §5 组件清单）----
const tab = computed<'followings' | 'followers'>(() =>
  route.name === 'user-followers' ? 'followers' : 'followings',
)
const tabLabel = computed(() => (tab.value === 'followings' ? '关注' : '粉丝'))

// ---- 页面三态 ----
const loading = ref(true)
const errorMsg = ref('') // 页头/致命错误（用户不存在等）

// ---- 页头（US02 复用）----
const nickname = ref('')
const headCount = ref(0) // 当前 tab 的计数（followingCount / followerCount）

// ---- 列表与分页 ----
const items = ref<FollowPageItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20 // 05 §1 默认
const listLoading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

// ---- 行内关注按钮请求中集合（按 userId 防并发）----
const actingIds = ref<Set<number>>(new Set())

// userId 校验（FR-PR03 范式：非正整数按不存在处理，不发请求）
function parseUid(): number | null {
  const raw = String(route.params.userId)
  const uid = Number(raw)
  if (!/^\d+$/.test(raw) || !Number.isInteger(uid) || uid <= 0) return null
  return uid
}

// 加载页头（US02：昵称 + 计数）
async function loadHead(uid: number) {
  const p = await getPublicProfile(uid)
  nickname.value = p.nickname
  headCount.value = tab.value === 'followings' ? p.followingCount : p.followerCount
}

// 加载列表（FO03/FO04）
async function loadList(uid: number) {
  listLoading.value = true
  try {
    const resp =
      tab.value === 'followings'
        ? await getFollowings(uid, page.value, size)
        : await getFollowers(uid, page.value, size)
    total.value = resp.total
    items.value = resp.list
  } catch (e) {
    toast((e as Error).message || '加载失败') // 列表失败 toast，保留页头
    items.value = []
  } finally {
    listLoading.value = false
  }
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  page.value = 1
  const uid = parseUid()
  if (uid === null) {
    loading.value = false
    errorMsg.value = '用户不存在'
    return
  }
  try {
    await loadHead(uid)
    await loadList(uid)
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败' // 页头失败（40401 等）→ 页内错误态
  } finally {
    loading.value = false
  }
}

// 翻页
function goPage(p: number) {
  if (p < 1 || p > totalPages.value || p === page.value) return
  page.value = p
  const uid = parseUid()
  if (uid !== null) {
    loadList(uid)
    window.scrollTo({ top: 0 })
  }
}

// 行内关注按钮（FR-FO04 三态范式，幂等）
async function onToggleFollow(item: FollowPageItem) {
  if (actingIds.value.has(item.userId)) return
  if (!store.isLoggedIn) {
    // 游客：跳登录带回跳
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  actingIds.value.add(item.userId)
  try {
    const resp = item.isFollowed
      ? await unfollowUser(item.userId) // 已关注 → 直接取关无确认框（FR-FO02）
      : await followUser(item.userId)
    item.isFollowed = resp.isFollowed // 本地翻转，列表条目不消失（EX-6）
    // 关注关系变动影响页头计数（他人主页视角）：+关注使对方粉丝数+1；仅 followers tab 本地同步
    if (tab.value === 'followers') {
      headCount.value = resp.followerCount
      total.value = resp.followerCount
    }
  } catch (e) {
    toast((e as Error).message || '操作失败')
  } finally {
    actingIds.value.delete(item.userId)
  }
}

// 整行点击跳空间
function goSpace(userId: number) {
  router.push(`/u/${userId}`)
}

onMounted(load)
// 路由参数变化（userId / tab 切换）重新加载
watch(() => [route.params.userId, route.name], load)
</script>

<template>
  <div class="follow-page container">
    <!-- 加载态 -->
    <div v-if="loading" class="state-box">加载中…</div>

    <!-- 错误态（用户不存在 / 页头失败） -->
    <div v-else-if="errorMsg" class="state-box">
      <p>{{ errorMsg }}</p>
      <router-link to="/" class="back-home">返回首页</router-link>
    </div>

    <!-- 正常态 -->
    <div v-else class="card">
      <!-- 页头：返回空间 + 昵称 + 计数 -->
      <div class="head">
        <router-link :to="`/u/${route.params.userId}`" class="back-link">← 返回空间</router-link>
        <h2 class="head-title">{{ nickname }} 的{{ tabLabel }}（{{ headCount }}）</h2>
      </div>

      <!-- 列表 -->
      <div v-if="listLoading" class="list-state">加载中…</div>
      <div v-else-if="items.length === 0" class="list-state empty">
        {{ tab === 'followings' ? '暂无关注' : '暂无粉丝' }}
      </div>
      <ul v-else class="list">
        <li
          v-for="item in items"
          :key="item.userId"
          class="row"
          @click="goSpace(item.userId)"
        >
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
          <!-- 本人行不渲染按钮（FR-FO05 主流程 4） -->
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

      <!-- 分页控件（首末页禁用） -->
      <div v-if="total > size" class="pager">
        <button class="page-btn" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
        <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="page-btn" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.container {
  max-width: var(--width-container);
  margin: 0 auto;
  padding: var(--space-block);
}
.state-box {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-block);
  color: var(--color-text-secondary);
}
.back-home {
  padding: 8px 24px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  border-radius: var(--radius-card);
}
.back-home:hover {
  background: var(--color-primary);
  color: #fff;
}
.card {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 24px;
}
.head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}
.back-link {
  width: fit-content;
  font-size: 13px;
  color: var(--color-text-secondary);
  text-decoration: none;
}
.back-link:hover {
  color: var(--color-primary);
}
.head-title {
  margin: 0;
  font-size: 18px;
}
.list-state {
  padding: 48px 0;
  text-align: center;
  color: var(--color-text-secondary);
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
  cursor: pointer; /* 整行可点（FR-FO05 主流程 3） */
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
  padding-top: 16px;
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
