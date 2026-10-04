<script setup lang="ts">
// PG-14 消息页（06 §3.14，Phase 4 真实化）：/messages 仅本人（requiresAuth）
// tab：全部/回复我的/收到的赞/新粉丝（MS01 type 可选筛选，缺省全部）+ FollowListView 翻页范式；
// 消息行：未读圆点 + 发送者头像昵称（跳空间）+ 类型文案 + 内容快照（commentDeleted 置灰）+ 时间；
// 整行点击 = MS02 标记已读 + 按 type 跳转（1/2 → /video/:videoId，3 → /u/:senderId，ADR-19 无锚点）；
// 页头「全部已读」（MS03）；红点本地减算接 stores/message（时机③）。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyMessages, markMessageRead, markAllMessagesRead } from '@/api'
import type { MessageItem } from '@/types/api'
import { useMessageStore } from '@/stores/message'
import { defaultAvatar } from '@/utils/avatar'
import { toast } from '@/utils/toast'

const router = useRouter()
const messageStore = useMessageStore()

// ---- tab：全部（无 type）/ 1 回复我的 / 2 收到的赞 / 3 新粉丝（缺省第一项「全部」）----
const TABS: { key: '' | '1' | '2' | '3'; label: string }[] = [
  { key: '', label: '全部' },
  { key: '1', label: '回复我的' },
  { key: '2', label: '收到的赞' },
  { key: '3', label: '新粉丝' },
]
const tab = ref<'' | '1' | '2' | '3'>('')

// ---- 列表与分页（FollowListView 翻页范式）----
const items = ref<MessageItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20 // 06 §3.14
const loading = ref(true)
const errorMsg = ref('')
const listLoading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

const markingAll = ref(false)

// 类型文案（MS01：后端只给结构化字段，文案前端拼装）
function typeText(t: 1 | 2 | 3): string {
  return t === 1 ? '回复了你' : t === 2 ? '赞了你的视频' : '关注了你'
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await getMyMessages({
      type: tab.value === '' ? undefined : (Number(tab.value) as 1 | 2 | 3),
      page: page.value,
      size,
    })
    total.value = resp.total
    items.value = resp.list
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败'
    items.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(key: '' | '1' | '2' | '3') {
  if (key === tab.value) return
  tab.value = key
  page.value = 1
  load()
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value || p === page.value || listLoading.value) return
  page.value = p
  listLoading.value = true
  getMyMessages({
    type: tab.value === '' ? undefined : (Number(tab.value) as 1 | 2 | 3),
    page: page.value,
    size,
  })
    .then((resp) => {
      total.value = resp.total
      items.value = resp.list
      window.scrollTo({ top: 0 })
    })
    .catch((e) => toast((e as Error).message || '加载失败'))
    .finally(() => (listLoading.value = false))
}

// 整行点击 = MS02 标记已读（失败不阻断跳转）+ 按 type 跳转
function openMessage(m: MessageItem) {
  if (!m.isRead) {
    m.isRead = true // 本地即时消点
    messageStore.decrement() // 红点减算（时机③）
    markMessageRead(m.id).catch(() => {
      /* 已读标记尽力而为：失败回跳列表时由列表态兜底 */
    })
  }
  if (m.type === 3) {
    router.push(`/u/${m.senderId}`)
  } else if (m.videoId) {
    router.push(`/video/${m.videoId}`)
  }
}

// 头像/昵称点击仅跳空间（不标已读，06 §3.14）
function goSpace(senderId: number) {
  router.push(`/u/${senderId}`)
}

// 全部已读（MS03；幂等；本地圆点全消 + 红点清零）
async function onReadAll() {
  if (markingAll.value || messageStore.unreadCount === 0) return
  markingAll.value = true
  try {
    await markAllMessagesRead()
    items.value.forEach((m) => (m.isRead = true))
    messageStore.clearAll()
    toast('已全部标记为已读')
  } catch (e) {
    toast((e as Error).message || '操作失败，请重试')
  } finally {
    markingAll.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="messages-page container">
    <!-- 首次加载态 -->
    <div v-if="loading" class="state-box">加载中…</div>

    <!-- 错误态（重试） -->
    <div v-else-if="errorMsg" class="state-box">
      <p>{{ errorMsg }}</p>
      <button class="retry-btn" @click="load">重试</button>
    </div>

    <!-- 正常态 -->
    <div v-else class="card">
      <!-- 页头：消息 + 全部已读（有未读才可点） -->
      <div class="head">
        <h2 class="head-title">消息</h2>
        <button
          class="read-all-btn"
          :disabled="markingAll || !messageStore.hasUnread"
          @click="onReadAll"
        >
          {{ markingAll ? '处理中…' : '全部已读' }}
        </button>
      </div>

      <!-- tab 行 -->
      <div class="tab-row">
        <button
          v-for="t in TABS"
          :key="t.key"
          class="tab"
          :class="{ on: tab === t.key }"
          @click="switchTab(t.key)"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- 空态 -->
      <div v-if="items.length === 0" class="list-state empty">暂无消息</div>

      <!-- 消息行列表 -->
      <ul v-else class="list">
        <li
          v-for="m in items"
          :key="m.id"
          class="row"
          :class="{ unread: !m.isRead }"
          @click="openMessage(m)"
        >
          <!-- 未读圆点 -->
          <span class="dot" :class="{ show: !m.isRead }"></span>
          <img
            class="row-avatar"
            :src="m.senderAvatarUrl || defaultAvatar"
            alt="头像"
            @click.stop="goSpace(m.senderId)"
            @error="(e: Event) => (e.target as HTMLImageElement).src = defaultAvatar"
          />
          <div class="row-info">
            <p class="row-main">
              <span class="nickname" @click.stop="goSpace(m.senderId)">{{ m.senderNickname }}</span>
              <span class="type-text">{{ typeText(m.type) }}</span>
              <span v-if="m.type === 1 && m.content && !m.commentDeleted" class="snapshot">：{{ m.content }}</span>
              <span v-else-if="m.type === 1 && m.commentDeleted" class="snapshot deleted">（该评论已删除）</span>
            </p>
            <p class="row-time">{{ m.createdAt }}</p>
          </div>
        </li>
      </ul>

      <!-- 翻页 -->
      <div v-if="total > size" class="pager">
        <button class="page-btn" :disabled="page <= 1 || listLoading" @click="goPage(page - 1)">上一页</button>
        <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="page-btn" :disabled="page >= totalPages || listLoading" @click="goPage(page + 1)">下一页</button>
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
.card {
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 24px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
}
.head-title {
  margin: 0;
  font-size: 18px;
}
.read-all-btn {
  border: 1px solid var(--color-border);
  background: none;
  color: var(--color-text-secondary);
  border-radius: var(--radius-card);
  padding: 6px 16px;
  font-size: 13px;
  cursor: pointer;
}
.read-all-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.read-all-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.tab-row {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 4px;
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
  gap: 12px;
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
/* 未读行底色微标 + 圆点（已读占位透明防抖动） */
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: transparent;
  flex-shrink: 0;
}
.dot.show {
  background: var(--color-danger);
}
.row-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  cursor: pointer;
}
.row-info {
  flex: 1;
  min-width: 0;
}
.row-main {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nickname {
  font-weight: 600;
  cursor: pointer;
}
.nickname:hover {
  color: var(--color-primary);
}
.type-text {
  margin-left: 4px;
}
.snapshot {
  color: var(--color-text-secondary);
}
.snapshot.deleted {
  color: var(--color-text-secondary);
  font-style: italic; /* 置灰（该评论已删除） */
}
.row-time {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
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
