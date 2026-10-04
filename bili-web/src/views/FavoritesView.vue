<script setup lang="ts">
// PG-15 收藏管理页（06 §3.15，Phase 4 真实化）：/favorites 仅本人（requiresAuth）
// 两视图：夹列表（名称/计数/默认标记 + 新建/重命名/删除，默认夹按钮禁用 + 40908 兜底）
// 与夹内容（返回链接 + VideoCard 网格 + 加载更多 + 删单条=IN04 整体取消）。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getMyFolders, createFolder, renameFolder, deleteFolder,
  getFolderFavorites, toggleFavorite,
} from '@/api'
import type { FavoriteFolderItem } from '@/types/api'
import { useLoadMore } from '@/composables/useLoadMore'
import { toast } from '@/utils/toast'
import VideoCard from '@/components/VideoCard.vue'

const route = useRoute()
const router = useRouter()

// ---- 夹列表视图 ----
const folders = ref<FavoriteFolderItem[]>([])
const loading = ref(true)
const errorMsg = ref('')

// 新建：内联表单
const creating = ref(false)
const newName = ref('')
const createActing = ref(false)

// 重命名：行内编辑（同一时间仅一行）
const renamingId = ref<number | null>(null)
const renameName = ref('')
const renameActing = ref(false)

// 删除：确认弹层
const deleteTarget = ref<FavoriteFolderItem | null>(null)
const deleteActing = ref(false)

const defaultFolder = computed(() => folders.value.find((f) => f.isDefault) ?? null)

// ---- 夹内容视图（点入某夹；?folder= 参数驱动，可分享/回退）----
const folder = ref<FavoriteFolderItem | null>(null)
const videos = useLoadMore((page) =>
  folder.value ? getFolderFavorites(folder.value.folderId, page, 12) : Promise.resolve({ total: 0, list: [] }),
)
const contentLoading = ref(true)
const contentError = ref('')

function parseFolderParam(): number | null {
  const raw = route.query.folder
  if (raw === undefined || raw === null || raw === '') return null
  const s = String(raw)
  const n = Number(s)
  return /^\d+$/.test(s) && Number.isInteger(n) && n > 0 ? n : -1 // -1 = 非法（页内错误态）
}

async function loadFolders() {
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await getMyFolders()
    folders.value = resp.list
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

// 入口：先拉夹列表，再按 ?folder= 决定视图
async function init() {
  await loadFolders()
  const fid = parseFolderParam()
  if (fid !== null) {
    if (fid === -1) {
      contentError.value = '收藏夹不存在'
      folder.value = null
      contentLoading.value = false
      return
    }
    openFolderById(fid, false)
  }
}

function openFolderById(folderId: number, push = true) {
  const f = folders.value.find((x) => x.folderId === folderId)
  if (!f) {
    // 40405（非本人夹/不存在）→ 页内错误态不崩溃（EX-2/EX-4）
    folder.value = null
    contentError.value = '收藏夹不存在'
    contentLoading.value = false
    return
  }
  folder.value = f
  contentError.value = ''
  contentLoading.value = true
  if (push) router.push({ path: '/favorites', query: { folder: String(folderId) } })
  videos.loadFirst().finally(() => (contentLoading.value = false))
}

function goFolderList() {
  folder.value = null
  router.push({ path: '/favorites' })
  loadFolders() // 重拉夹计数（删单条后 count 已变）
}

// ---- 新建 ----
async function doCreate() {
  const name = newName.value.trim()
  if (!name || createActing.value) return
  createActing.value = true
  try {
    const resp = await createFolder(name)
    folders.value.push(resp) // 尾部插入新行（count=0）
    creating.value = false
    newName.value = ''
    toast('收藏夹已创建')
  } catch (e) {
    toast((e as Error).message || '创建失败，请重试') // 40001/40909 toast（EX-1/EX-2）
  } finally {
    createActing.value = false
  }
}

// ---- 重命名 ----
function startRename(f: FavoriteFolderItem) {
  renamingId.value = f.folderId
  renameName.value = f.name
}

async function doRename() {
  const id = renamingId.value
  const name = renameName.value.trim()
  if (id === null || !name || renameActing.value) return
  renameActing.value = true
  try {
    await renameFolder(id, name)
    const row = folders.value.find((f) => f.folderId === id)
    if (row) row.name = name
    if (folder.value && folder.value.folderId === id) folder.value.name = name
    renamingId.value = null
    toast('已重命名')
  } catch (e) {
    toast((e as Error).message || '重命名失败，请重试') // 40908/40909/40405 toast
  } finally {
    renameActing.value = false
  }
}

// ---- 删除（二次确认：夹内条目移入默认夹）----
async function doDelete() {
  const target = deleteTarget.value
  if (!target || deleteActing.value) return
  deleteActing.value = true
  try {
    await deleteFolder(target.folderId)
    const moved = target.count
    folders.value = folders.value.filter((f) => f.folderId !== target.folderId)
    if (defaultFolder.value) defaultFolder.value.count += moved // 默认夹 count 增加（EX-5）
    deleteTarget.value = null
    toast(`已删除「${target.name}」`)
  } catch (e) {
    toast((e as Error).message || '删除失败，请重试')
  } finally {
    deleteActing.value = false
  }
}

// ---- 夹内容删单条 = IN04 整体取消收藏 → 卡片移除、夹 count-1 ----
const removingIds = ref<Set<number>>(new Set())

async function onRemoveCard(videoId: number) {
  if (removingIds.value.has(videoId) || !folder.value) return
  removingIds.value.add(videoId)
  try {
    await toggleFavorite(videoId)
    videos.items.value = videos.items.value.filter((v) => v.videoId !== videoId)
    videos.total.value = Math.max(0, videos.total.value - 1)
    folder.value.count = Math.max(0, folder.value.count - 1)
    toast('已取消收藏')
  } catch (e) {
    toast((e as Error).message || '操作失败，请重试')
  } finally {
    removingIds.value.delete(videoId)
  }
}

async function onVideoLoadMore() {
  const ok = await videos.loadMore()
  if (!ok && videos.error.value) toast('加载失败，请重试')
}

onMounted(init)
</script>

<template>
  <div class="favorites-page container">
    <!-- 首次加载态 -->
    <div v-if="loading" class="state-box">加载中…</div>

    <!-- 错误态（夹列表失败，重试） -->
    <div v-else-if="errorMsg" class="state-box">
      <p>{{ errorMsg }}</p>
      <button class="retry-btn" @click="loadFolders">重试</button>
    </div>

    <!-- ══ 夹内容视图（?folder=）══ -->
    <div v-else-if="folder" class="card">
      <div class="head">
        <button class="back-btn" @click="goFolderList">← 返回收藏夹列表</button>
        <h2 class="head-title">{{ folder.name }}（{{ folder.count }} 个内容）</h2>
      </div>

      <div v-if="contentLoading" class="list-state">加载中…</div>
      <div v-else-if="contentError" class="list-state">{{ contentError }}</div>
      <div v-else-if="videos.items.value.length === 0" class="list-state empty">
        暂无收藏<br /><span class="empty-tip">去首页发现感兴趣的视频吧</span>
      </div>
      <template v-else>
        <div class="card-grid">
          <div v-for="v in videos.items.value" :key="v.videoId" class="cell">
            <VideoCard :video="v" />
            <button
              class="cell-del"
              :disabled="removingIds.has(v.videoId)"
              @click="onRemoveCard(v.videoId)"
            >
              {{ removingIds.has(v.videoId) ? '删除中…' : '取消收藏' }}
            </button>
          </div>
        </div>
        <div v-if="videos.hasMore.value" class="load-more-zone">
          <button class="load-more-btn" :disabled="videos.loading.value" @click="onVideoLoadMore">
            {{ videos.loading.value ? '加载中…' : '加载更多' }}
          </button>
        </div>
      </template>
    </div>

    <!-- ══ 夹列表视图（默认）══ -->
    <div v-else class="card">
      <div class="head">
        <h2 class="head-title">我的收藏夹</h2>
        <button class="new-btn" @click="creating = !creating">＋ 新建收藏夹</button>
      </div>

      <!-- 非法 ?folder=（如 folder=99999 非本人夹已在上面错误态；此处兜底非法参数提示） -->
      <div v-if="contentError" class="list-state">{{ contentError }}</div>

      <!-- 新建内联表单 -->
      <div v-if="creating" class="create-form">
        <input
          v-model="newName"
          type="text"
          maxlength="20"
          placeholder="收藏夹名称（1-20 字）"
          @keyup.enter="doCreate"
        />
        <button class="form-ok" :disabled="createActing || !newName.trim()" @click="doCreate">
          {{ createActing ? '创建中…' : '创建' }}
        </button>
        <button class="form-cancel" @click="((creating = false), (newName = ''))">取消</button>
      </div>

      <!-- 夹列表（默认夹恒首位） -->
      <ul class="folder-list">
        <li
          v-for="f in folders"
          :key="f.folderId"
          class="folder-row"
          @click="openFolderById(f.folderId)"
        >
          <span class="folder-icon" aria-hidden="true"></span>
          <span class="folder-name">{{ f.name }}</span>
          <span v-if="f.isDefault" class="folder-default">默认</span>
          <span class="folder-count">{{ f.count }} 个内容</span>

          <!-- 行操作：默认夹按钮禁用（40908 服务端兜底）；行内重命名/删除 -->
          <template v-if="renamingId === f.folderId">
            <input
              v-model="renameName"
              type="text"
              maxlength="20"
              @keyup.enter="doRename"
              @click.stop
            />
            <button class="row-act ok" :disabled="renameActing || !renameName.trim()" @click.stop="doRename">
              {{ renameActing ? '保存中…' : '保存' }}
            </button>
            <button class="row-act" @click.stop="((renamingId = null))">取消</button>
          </template>
          <template v-else>
            <button class="row-act" :disabled="f.isDefault" title="默认收藏夹不可重命名" @click.stop="startRename(f)">
              重命名
            </button>
            <button class="row-act danger" :disabled="f.isDefault" title="默认收藏夹不可删除" @click.stop="deleteTarget = f">
              删除
            </button>
          </template>
        </li>
      </ul>
    </div>

    <!-- 删除确认弹层（提示夹内条目移入默认夹） -->
    <div v-if="deleteTarget" class="mask" @click.self="deleteTarget = null">
      <div class="confirm">
        <p>
          确认删除「{{ deleteTarget.name }}」？<br />
          <span class="confirm-tip">夹内 {{ deleteTarget.count }} 条收藏将移入默认收藏夹</span>
        </p>
        <div class="confirm-btns">
          <button class="cancel" @click="deleteTarget = null">取消</button>
          <button class="del" :disabled="deleteActing" @click="doDelete">
            {{ deleteActing ? '删除中…' : '确认删除' }}
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
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}
.head-title {
  margin: 0;
  font-size: 18px;
  flex: 1;
}
.back-btn {
  border: none;
  background: none;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}
.back-btn:hover {
  color: var(--color-primary);
}
.new-btn {
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  background: none;
  border-radius: var(--radius-card);
  padding: 7px 16px;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}
.new-btn:hover {
  background: var(--color-primary);
  color: #fff;
}
.create-form {
  display: flex;
  gap: 10px;
  padding: 16px 8px 8px;
}
.create-form input,
.folder-row input {
  flex: 0 0 220px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg);
  font-size: 13px;
  outline: none;
}
.create-form input:focus,
.folder-row input:focus {
  border-color: var(--color-primary);
}
.form-ok {
  height: 32px;
  padding: 0 18px;
  border: none;
  border-radius: var(--radius-card);
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.form-ok:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.form-cancel,
.row-act {
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: none;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}
.form-cancel:hover,
.row-act:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.row-act.danger:hover:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}
.row-act:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.row-act.ok {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}
.folder-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.folder-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 8px;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}
.folder-row:last-child {
  border-bottom: none;
}
.folder-row:hover {
  background: var(--color-bg);
}
.folder-icon {
  width: 36px;
  height: 28px;
  border-radius: 3px 8px 3px 3px;
  background: var(--color-primary-light);
  border: 1px solid var(--color-primary);
  flex-shrink: 0;
  position: relative;
}
.folder-icon::after {
  content: '';
  position: absolute;
  left: -1px;
  top: -6px;
  width: 14px;
  height: 6px;
  border-radius: 3px 3px 0 0;
  background: var(--color-primary);
}
.folder-name {
  font-size: 14px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.folder-default {
  font-size: 11px;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: 4px;
  padding: 0 4px;
  flex-shrink: 0;
}
.folder-count {
  flex: 1;
  text-align: right;
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.list-state {
  padding: 48px 0;
  text-align: center;
  color: var(--color-text-secondary);
  line-height: 1.8;
}
.empty-tip {
  font-size: 12px;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-card-gap);
}
@media (max-width: 900px) {
  .card-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
.cell {
  position: relative;
}
.cell-del {
  margin-top: 6px;
  width: 100%;
  padding: 6px 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: none;
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.cell-del:hover:not(:disabled) {
  border-color: var(--color-danger);
  color: var(--color-danger);
}
.cell-del:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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
/* 删除确认弹层 */
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
  width: 340px;
  padding: 24px;
  background: var(--color-card);
  border-radius: var(--radius-card);
  text-align: center;
}
.confirm p {
  margin: 0 0 18px;
  font-size: 15px;
  line-height: 1.7;
}
.confirm-tip {
  font-size: 13px;
  color: var(--color-text-secondary);
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
