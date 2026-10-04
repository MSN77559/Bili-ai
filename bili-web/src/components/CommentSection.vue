<script setup lang="ts">
// CommentSection 两级评论区（06 §5，Phase 3，FR-IN03~IN05）：
// - 发布框（登录者）：1~500 字 + Ctrl+Enter / 点发布；成功父评论插列表头部（总数 +1 不重拉）。
// - 父评论分页（CM02 时间倒序，20/页）+ 「加载更多」追加；游客顶部为「请先登录」占位条（只读）。
// - 楼中楼：首屏聚合 replies（≤2 条）随父行返回；点「共 n 条回复」展开拉全量（CM02 parent，时间正序，20/页加载更多）。
// - 本人删除：确认弹层 → CM03 → 列表移除（父删含整块楼中楼，总数/replyCount 相应减少）。
// - 回复：楼中楼内点「回复」带出 replyToUserId（回复某条回复时），发布成功插楼中楼尾部。
import { computed, onMounted, reactive, ref } from 'vue'
import type { Ref } from 'vue'
import { useRouter } from 'vue-router'
import { getComments, getReplies, sendComment, deleteComment } from '@/api'
import type { CommentItem, ParentCommentItem } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { defaultAvatar } from '@/utils/avatar'
import { toast } from '@/utils/toast'

const props = defineProps<{
  videoId: number
  loggedIn: boolean
}>()

const router = useRouter()
const store = useUserStore()

// ---- 父评论列表（CM02 分页 + 加载更多）----
interface ParentUI {
  data: ParentCommentItem
  expanded: boolean // 楼中楼展开态
  replies: CommentItem[] // 展开后累积的回复（含首屏聚合 2 条）
  replyLoaded: boolean // 是否已拉过全量
  replyPage: number
  replyLoading: boolean
  replyTotal: number
}

const comments = ref([]) as Ref<ParentUI[]>
const total = ref(0)
const page = ref(0)
const loading = ref(false)
const loaded = ref(false)
const error = ref('')
const PAGE_SIZE = 20

/** 父评论 UI 包装：data=接口行 + 楼中楼展开态本地字段（reactive 包装便于状态管理） */
interface ParentUI {
  data: ParentCommentItem
  expanded: boolean // 楼中楼展开态
  replies: CommentItem[] // 展开后累积的回复（含首屏聚合 2 条）
  replyLoaded: boolean // 是否已拉过全量
  replyPage: number
  replyLoading: boolean
  replyTotal: number
}

function wrapParent(p: ParentCommentItem): ParentUI {
  return reactive({
    data: p,
    expanded: false,
    replies: [...p.replies],
    replyLoaded: false,
    replyPage: 0,
    replyLoading: false,
    replyTotal: p.replyCount,
  })
}

async function loadFirst() {
  return loadMore(true)
}

async function loadMore(reset = false): Promise<boolean> {
  if (loading.value) return false
  loading.value = true
  error.value = ''
  try {
    const resp = await getComments(props.videoId, reset ? 1 : page.value + 1, PAGE_SIZE)
    page.value = reset ? 1 : page.value + 1
    total.value = resp.total
    // 展开态本地字段（reactive 包装便于楼中楼状态管理）
    comments.value = reset
      ? resp.list.map(wrapParent)
      : comments.value.concat(resp.list.map(wrapParent))
    loaded.value = true
    return true
  } catch (e) {
    error.value = (e as Error).message || '加载失败'
    return false
  } finally {
    loading.value = false
  }
}

const hasMore = computed(() => comments.value.length < total.value)

// ---- 楼中楼展开（CM02 parent，时间正序）----
async function toggleExpand(ui: ParentUI) {
  if (ui.expanded) {
    ui.expanded = false
    return
  }
  ui.expanded = true
  if (!ui.replyLoaded && ui.replyTotal > 0) {
    await loadReplies(ui)
  }
}

async function loadReplies(ui: ParentUI) {
  if (ui.replyLoading) return
  ui.replyLoading = true
  try {
    const resp = await getReplies(props.videoId, ui.data.commentId, ui.replyPage + 1, PAGE_SIZE)
    ui.replyPage += 1
    ui.replyTotal = resp.total
    ui.replies = ui.replyPage === 1 ? resp.list : dedupeAppend(ui.replies, resp.list)
    ui.replyLoaded = true
  } catch (e) {
    toast((e as Error).message || '回复加载失败')
  } finally {
    ui.replyLoading = false
  }
}

/** 追加去重（首屏聚合 2 条与分页结果可能重叠） */
function dedupeAppend(base: CommentItem[], add: CommentItem[]): CommentItem[] {
  const seen = new Set(base.map((r) => r.commentId))
  return base.concat(add.filter((r) => !seen.has(r.commentId)))
}

// ---- 发布框（父评论）----
const draft = ref('')
const publishing = ref(false)

async function publish() {
  const text = draft.value.trim()
  const len = [...text].length
  if (len === 0) {
    toast('评论不能为空')
    return
  }
  if (len > 500) {
    toast('评论长度须为 1-500 字符')
    return
  }
  if (publishing.value) return
  publishing.value = true
  try {
    const item = await sendComment(props.videoId, text)
    comments.value.unshift(wrapParent({ ...item, replyCount: 0, replies: [] }))
    total.value += 1
    draft.value = ''
    toast('评论成功')
  } catch (e) {
    toast((e as Error).message || '发布失败，请重试')
  } finally {
    publishing.value = false
  }
}

// ---- 楼中楼回复 ----
const replyDraft = reactive<{ parentId: number; toUserId: number | null; toName: string; text: string }>({
  parentId: 0,
  toUserId: null,
  toName: '',
  text: '',
})
const replyPublishing = ref(false)

function startReply(ui: ParentUI, reply?: CommentItem) {
  replyDraft.parentId = ui.data.commentId
  replyDraft.toUserId = reply ? reply.userId : null
  replyDraft.toName = reply ? reply.nickname : ui.data.nickname
  replyDraft.text = ''
}

function cancelReply() {
  replyDraft.parentId = 0
  replyDraft.toUserId = null
  replyDraft.toName = ''
  replyDraft.text = ''
}

async function publishReply() {
  const text = replyDraft.text.trim()
  const len = [...text].length
  if (len === 0) {
    toast('回复不能为空')
    return
  }
  if (len > 500) {
    toast('评论长度须为 1-500 字符')
    return
  }
  if (replyPublishing.value) return
  replyPublishing.value = true
  try {
    const item = await sendComment(
      props.videoId,
      text,
      replyDraft.parentId,
      replyDraft.toUserId ?? undefined,
    )
    const ui = comments.value.find((c) => c.data.commentId === replyDraft.parentId)
    if (ui) {
      ui.replies.push(item)
      ui.replyTotal += 1
      if (!ui.expanded) ui.expanded = true
    }
    cancelReply()
    toast('回复成功')
  } catch (e) {
    toast((e as Error).message || '发布失败，请重试')
  } finally {
    replyPublishing.value = false
  }
}

// ---- 删除（本人，确认弹层）----
const confirmTarget = ref<{ ui: ParentUI; reply?: CommentItem } | null>(null)
const deleting = ref(false)

function askDelete(ui: ParentUI, reply?: CommentItem) {
  confirmTarget.value = { ui, reply }
}

async function doDelete() {
  const target = confirmTarget.value
  if (!target || deleting.value) return
  deleting.value = true
  try {
    await deleteComment(target.reply ? target.reply.commentId : target.ui.data.commentId)
    if (target.reply) {
      // 回复删除：楼中楼移除该条
      target.ui.replies = target.ui.replies.filter((r) => r.commentId !== target.reply!.commentId)
      target.ui.replyTotal -= 1
      target.ui.data.replyCount = target.ui.replyTotal
    } else {
      // 父删除：整块移除（含楼中楼，级联逻辑删）
      comments.value = comments.value.filter((c) => c.data.commentId !== target.ui.data.commentId)
      total.value -= 1
    }
    confirmTarget.value = null
    toast('已删除')
  } catch (e) {
    toast((e as Error).message || '删除失败，请重试')
  } finally {
    deleting.value = false
  }
}

function goLogin() {
  router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
}

const isOwn = (userId: number) => props.loggedIn && store.userId === userId

onMounted(loadFirst)
</script>

<template>
  <div class="comment-section">
    <h3 class="cs-title">评论 <span class="cs-total">{{ total }}</span></h3>

    <!-- 发布框：登录者（游客为「请先登录」占位条） -->
    <div v-if="loggedIn" class="cs-publisher">
      <img class="cs-avatar" :src="store.avatarUrl || defaultAvatar" alt="" />
      <div class="cs-input-zone">
        <textarea
          v-model="draft"
          class="cs-textarea"
          rows="2"
          maxlength="500"
          placeholder="发一条友善的评论"
          @keydown.ctrl.enter.prevent="publish"
          @keydown.meta.enter.prevent="publish"
        ></textarea>
        <div class="cs-pub-row">
          <span class="cs-hint">Ctrl + Enter 发送</span>
          <button class="cs-pub-btn" :disabled="publishing || !draft.trim()" @click="publish">
            {{ publishing ? '发布中…' : '发布' }}
          </button>
        </div>
      </div>
    </div>
    <button v-else class="cs-guest-bar" @click="goLogin">请先登录后发表评论</button>

    <!-- 列表三态 -->
    <div v-if="!loaded && loading" class="cs-state">加载中…</div>
    <div v-else-if="!loaded && error" class="cs-state">
      <p>{{ error }}</p>
      <button class="cs-retry" @click="loadFirst">重试</button>
    </div>
    <div v-else-if="comments.length === 0" class="cs-state">暂无评论</div>

    <template v-else>
      <!-- 父评论 -->
      <div v-for="ui in comments" :key="ui.data.commentId" class="cs-item">
        <router-link :to="`/u/${ui.data.userId}`">
          <img class="cs-avatar" :src="ui.data.avatarUrl || defaultAvatar" alt=""
            @error="(e: Event) => (e.target as HTMLImageElement).src = defaultAvatar" />
        </router-link>
        <div class="cs-body">
          <div class="cs-head">
            <router-link class="cs-nick" :to="`/u/${ui.data.userId}`">{{ ui.data.nickname }}</router-link>
          </div>
          <p class="cs-content">{{ ui.data.content }}</p>
          <div class="cs-actions">
            <span class="cs-time">{{ ui.data.createdAt }}</span>
            <button v-if="loggedIn" class="cs-act" @click="toggleExpand(ui); startReply(ui)">回复</button>
            <button v-if="isOwn(ui.data.userId)" class="cs-act cs-del" @click="askDelete(ui)">删除</button>
          </div>

          <!-- 楼中楼 -->
          <div v-if="ui.expanded" class="cs-sub">
            <div v-for="r in ui.replies" :key="r.commentId" class="cs-sub-item">
              <router-link :to="`/u/${r.userId}`">
                <img class="cs-sub-avatar" :src="r.avatarUrl || defaultAvatar" alt=""
                  @error="(e: Event) => (e.target as HTMLImageElement).src = defaultAvatar" />
              </router-link>
              <div class="cs-body">
                <div class="cs-head">
                  <router-link class="cs-nick cs-sub-nick" :to="`/u/${r.userId}`">{{ r.nickname }}</router-link>
                  <template v-if="r.replyToNickname">
                    <span class="cs-reply-arrow"></span>
                    <span class="cs-reply-to">{{ r.replyToNickname }}</span>
                  </template>
                </div>
                <p class="cs-content">{{ r.content }}</p>
                <div class="cs-actions">
                  <span class="cs-time">{{ r.createdAt }}</span>
                  <button v-if="loggedIn" class="cs-act" @click="startReply(ui, r)">回复</button>
                  <button v-if="isOwn(r.userId)" class="cs-act cs-del" @click="askDelete(ui, r)">删除</button>
                </div>
              </div>
            </div>
            <div v-if="ui.replyLoading" class="cs-sub-more">加载中…</div>
            <button
              v-else-if="ui.replies.length < ui.replyTotal"
              class="cs-sub-more"
              @click="loadReplies(ui)"
            >加载更多回复（{{ ui.replies.length }}/{{ ui.replyTotal }}）</button>
            <button v-else class="cs-sub-more" @click="ui.expanded = false">收起</button>
          </div>
          <!-- 折叠条：有回复未展开时显示 -->
          <button v-else-if="ui.replyTotal > 0" class="cs-sub-toggle" @click="toggleExpand(ui)">
            共 {{ ui.replyTotal }} 条回复，点击查看
          </button>
        </div>
      </div>

      <!-- 父评论加载更多 -->
      <div v-if="hasMore" class="cs-more-zone">
        <button class="cs-more-btn" :disabled="loading" @click="loadMore()">
          {{ loading ? '加载中…' : '加载更多评论' }}
        </button>
      </div>
    </template>

    <!-- 楼中楼回复输入框（点「回复」带出） -->
    <div v-if="replyDraft.parentId" class="cs-reply-bar">
      <span class="cs-reply-tip">回复 @{{ replyDraft.toName }}：</span>
      <input
        v-model="replyDraft.text"
        class="cs-reply-input"
        type="text"
        maxlength="500"
        @keyup.enter="publishReply"
      />
      <button class="cs-pub-btn" :disabled="replyPublishing" @click="publishReply">
        {{ replyPublishing ? '发布中…' : '回复' }}
      </button>
      <button class="cs-cancel" @click="cancelReply">取消</button>
    </div>

    <!-- 删除确认弹层 -->
    <div v-if="confirmTarget" class="cs-mask" @click.self="confirmTarget = null">
      <div class="cs-confirm">
        <p>确认删除这条评论？</p>
        <div class="cs-confirm-btns">
          <button class="cs-cancel" @click="confirmTarget = null">取消</button>
          <button class="cs-del-btn" :disabled="deleting" @click="doDelete">
            {{ deleting ? '删除中…' : '确认' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment-section {
  margin-top: var(--space-block);
  padding: 20px;
  background: var(--color-card);
  border-radius: var(--radius-card);
}
.cs-title {
  margin: 0 0 16px;
  font-size: 16px;
}
.cs-total {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 400;
  margin-left: 4px;
}
/* 发布框 */
.cs-publisher {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.cs-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.cs-input-zone {
  flex: 1;
}
.cs-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  outline: none;
}
.cs-textarea:focus {
  border-color: var(--color-primary);
}
.cs-pub-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.cs-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.cs-pub-btn {
  padding: 6px 22px;
  border: none;
  border-radius: var(--radius-card);
  background: var(--color-primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.cs-pub-btn:hover:not(:disabled) {
  opacity: 0.9;
}
.cs-pub-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
/* 游客占位条 */
.cs-guest-bar {
  width: 100%;
  padding: 12px;
  margin-bottom: 20px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-card);
  background: none;
  color: var(--color-text-secondary);
  font-size: 14px;
  cursor: pointer;
}
.cs-guest-bar:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
/* 列表三态 */
.cs-state {
  padding: 32px 0;
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 14px;
}
.cs-state p {
  margin: 0 0 12px;
}
.cs-retry {
  padding: 6px 20px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  background: none;
  border-radius: var(--radius-card);
  cursor: pointer;
}
/* 父评论 */
.cs-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
}
.cs-item:last-of-type {
  border-bottom: none;
}
.cs-body {
  flex: 1;
  min-width: 0;
}
.cs-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.cs-nick {
  color: var(--color-text-secondary);
  text-decoration: none;
}
.cs-nick:hover {
  color: var(--color-primary);
}
.cs-sub-nick {
  font-size: 12px;
}
.cs-reply-arrow {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.cs-reply-arrow::before {
  content: '↦';
}
.cs-reply-to {
  color: var(--color-primary);
  font-size: 12px;
}
.cs-content {
  margin: 6px 0;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.cs-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}
.cs-time {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.cs-act {
  background: none;
  border: none;
  padding: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.cs-act:hover {
  color: var(--color-primary);
}
.cs-del:hover {
  color: var(--color-danger);
}
/* 楼中楼 */
.cs-sub {
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--color-bg);
  border-radius: var(--radius-card);
}
.cs-sub-item {
  display: flex;
  gap: 10px;
  padding: 8px 0;
}
.cs-sub-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.cs-sub-item .cs-content {
  font-size: 13px;
}
.cs-sub-toggle,
.cs-sub-more {
  margin-top: 6px;
  background: none;
  border: none;
  padding: 0;
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}
.cs-sub-toggle:hover,
.cs-sub-more:hover {
  text-decoration: underline;
}
.cs-more-zone {
  text-align: center;
  padding: 16px 0 4px;
}
.cs-more-btn {
  padding: 8px 32px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
  border-radius: 999px;
  font-size: 14px;
  cursor: pointer;
}
.cs-more-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.cs-more-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
/* 楼中楼回复条 */
.cs-reply-bar {
  position: sticky;
  bottom: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  box-shadow: var(--shadow-card-hover);
}
.cs-reply-tip {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.cs-reply-input {
  flex: 1;
  height: 32px;
  border: none;
  outline: none;
  font-size: 14px;
}
/* 删除确认弹层 */
.cs-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.cs-confirm {
  width: 320px;
  padding: 24px;
  background: var(--color-card);
  border-radius: var(--radius-card);
  text-align: center;
}
.cs-confirm p {
  margin: 0 0 18px;
  font-size: 15px;
}
.cs-confirm-btns {
  display: flex;
  justify-content: center;
  gap: 14px;
}
.cs-cancel {
  padding: 7px 22px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: none;
  color: var(--color-text);
  font-size: 14px;
  cursor: pointer;
}
.cs-del-btn {
  padding: 7px 22px;
  border: none;
  border-radius: var(--radius-card);
  background: var(--color-danger);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.cs-del-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
