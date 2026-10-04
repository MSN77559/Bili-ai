<script setup lang="ts">
// PG-13 历史页（06 §3.13，Phase 4 真实化）：/history 仅本人（requiresAuth 路由守卫）
// 行列表（封面缩略+标题+UP+viewedAt）+ FollowListView 翻页范式（上一页/下一页，非加载更多）；
// 行内删单条（确认弹层 → HI02）+ 页头「清空」（二次确认 → HI03）；整行点击跳 /video/:videoId。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyHistory, deleteHistory, clearHistory } from '@/api'
import type { HistoryItem } from '@/types/api'
import { formatDuration, gradientFor } from '@/utils/format'
import { toast } from '@/utils/toast'

const router = useRouter()

// ---- 列表与分页（FollowListView 翻页范式）----
const items = ref<HistoryItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 20 // 06 §3.13 默认
const loading = ref(true)
const errorMsg = ref('') // 首次加载错误（重试）
const listLoading = ref(false) // 翻页加载
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

// ---- 确认弹层（CommentSection confirmTarget 范式）----
// target=null 关闭；{ videoId } 删单条；{ clear: true } 清空
const confirmTarget = ref<{ videoId: number; title: string } | { clear: true } | null>(null)
const confirming = ref(false)

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await getMyHistory(page.value, size)
    total.value = resp.total
    items.value = resp.list
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败'
    items.value = []
  } finally {
    loading.value = false
  }
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value || p === page.value || listLoading.value) return
  page.value = p
  listLoading.value = true
  getMyHistory(page.value, size)
    .then((resp) => {
      total.value = resp.total
      items.value = resp.list
      window.scrollTo({ top: 0 })
    })
    .catch((e) => toast((e as Error).message || '加载失败'))
    .finally(() => (listLoading.value = false))
}

// 行内删除（确认弹层 → HI02 → 列表移除该行；幂等接口重复删也 200）
async function doDelete() {
  const target = confirmTarget.value
  if (!target || confirming.value || 'clear' in target) return
  confirming.value = true
  try {
    await deleteHistory(target.videoId)
    items.value = items.value.filter((h) => h.videoId !== target.videoId)
    total.value = Math.max(0, total.value - 1)
    confirmTarget.value = null
    toast('已删除')
  } catch (e) {
    toast((e as Error).message || '删除失败，请重试')
  } finally {
    confirming.value = false
  }
}

// 清空（二次确认 → HI03 → 列表转空态）
async function doClear() {
  if (confirming.value) return
  confirming.value = true
  try {
    await clearHistory()
    items.value = []
    total.value = 0
    page.value = 1
    confirmTarget.value = null
    toast('已清空观看历史')
  } catch (e) {
    toast((e as Error).message || '操作失败，请重试')
  } finally {
    confirming.value = false
  }
}

function goVideo(videoId: number) {
  router.push(`/video/${videoId}`)
}

onMounted(load)
</script>

<template>
  <div class="history-page container">
    <!-- 首次加载态 -->
    <div v-if="loading" class="state-box">加载中…</div>

    <!-- 错误态（重试） -->
    <div v-else-if="errorMsg" class="state-box">
      <p>{{ errorMsg }}</p>
      <button class="retry-btn" @click="load">重试</button>
    </div>

    <!-- 正常态 -->
    <div v-else class="card">
      <!-- 页头：观看历史（共 N 条）+ 清空 -->
      <div class="head">
        <h2 class="head-title">观看历史（{{ total }}）</h2>
        <button v-if="total > 0" class="clear-btn" @click="confirmTarget = { clear: true }">清空</button>
      </div>

      <!-- 空态（EX-1） -->
      <div v-if="items.length === 0" class="list-state empty">暂无观看记录</div>

      <!-- 行列表 -->
      <ul v-else class="list">
        <li
          v-for="h in items"
          :key="h.videoId"
          class="row"
          @click="goVideo(h.videoId)"
        >
          <!-- 封面缩略（16:9，渐变兜底同 VideoCard 范式）+ 时长角标 -->
          <div class="row-cover" :style="{ background: gradientFor(h.videoId) }">
            <img
              v-if="h.coverUrl"
              :src="h.coverUrl"
              alt="封面"
              @error="(e: Event) => (e.target as HTMLImageElement).style.display = 'none'"
            />
            <span class="cover-duration">{{ formatDuration(h.duration) }}</span>
          </div>
          <div class="row-info">
            <p class="row-title">{{ h.title }}</p>
            <p class="row-meta">{{ h.upNickname }} · {{ h.viewedAt }}</p>
          </div>
          <button class="del-btn" @click.stop="confirmTarget = { videoId: h.videoId, title: h.title }">删除</button>
        </li>
      </ul>

      <!-- 翻页（上一页/下一页，首末页禁用） -->
      <div v-if="total > size" class="pager">
        <button class="page-btn" :disabled="page <= 1 || listLoading" @click="goPage(page - 1)">上一页</button>
        <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="page-btn" :disabled="page >= totalPages || listLoading" @click="goPage(page + 1)">下一页</button>
      </div>
    </div>

    <!-- 确认弹层（删单条 / 清空，cs-mask 范式） -->
    <div v-if="confirmTarget" class="mask" @click.self="confirmTarget = null">
      <div class="confirm">
        <p v-if="'clear' in confirmTarget">确认清空全部观看历史？</p>
        <p v-else>确认删除这条观看记录？</p>
        <div class="confirm-btns">
          <button class="cancel" @click="confirmTarget = null">取消</button>
          <button
            class="del"
            :disabled="confirming"
            @click="'clear' in confirmTarget ? doClear() : doDelete()"
          >
            {{ confirming ? '处理中…' : '确认' }}
          </button>
        </div>
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
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}
.head-title {
  margin: 0;
  font-size: 18px;
}
.clear-btn {
  border: none;
  background: none;
  color: var(--color-danger);
  font-size: 14px;
  cursor: pointer;
  padding: 4px 8px;
}
.clear-btn:hover {
  text-decoration: underline;
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
  padding: 12px 8px;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer; /* 整行可点 → /video/:videoId */
}
.row:last-child {
  border-bottom: none;
}
.row:hover {
  background: var(--color-bg);
}
/* 封面缩略 16:9（120px 宽） */
.row-cover {
  position: relative;
  width: 120px;
  aspect-ratio: 16 / 9;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.row-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-duration {
  position: absolute;
  right: 4px;
  bottom: 4px;
  padding: 0 5px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 11px;
  line-height: 16px;
}
.row-info {
  flex: 1;
  min-width: 0;
}
.row-title {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  /* 至多 2 行 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.row-meta {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.del-btn {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: none;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}
.del-btn:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
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
/* 确认弹层（CommentSection cs-mask 同款） */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.confirm {
  width: 320px;
  padding: 24px;
  background: var(--color-card);
  border-radius: var(--radius-card);
  text-align: center;
}
.confirm p {
  margin: 0 0 18px;
  font-size: 15px;
}
.confirm-btns {
  display: flex;
  justify-content: center;
  gap: 14px;
}
.cancel {
  padding: 7px 22px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: none;
  color: var(--color-text);
  font-size: 14px;
  cursor: pointer;
}
.del {
  padding: 7px 22px;
  border: none;
  border-radius: var(--radius-card);
  background: var(--color-danger);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.del:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
