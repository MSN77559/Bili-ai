<script setup lang="ts">
// PG-10 视频播放页（06 §3.10，Phase 3）：播放器 + 弹幕层/输入条（FR-IN01/IN02）
// + 互动栏写路径（点赞/投币/收藏/分享，FR-IN06/07/08）+ UP 主条（FollowButton 复用）
// + 简介/分区标签 + 两级评论区（FR-IN03~IN05）+ 右侧「接下来播放」（同分类 20 条）
// 行为 SSOT 在 FR-VD02/FR-IN01~IN08；接口 API-VD03（view_count+1）/VD02/DL01/DL02/CM/IN01~IN04
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getVideoDetail, getVideos, getDanmakuList, sendDanmaku,
  getInteractions, toggleLike, coinVideo, toggleFavorite, getMyFolders,
} from '@/api'
import type { VideoDetail, VideoItem, DanmakuItem, InteractionState, FavoriteFolderItem } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { CATEGORIES } from '@/mock/home'
import { defaultAvatar } from '@/utils/avatar'
import { formatCount, formatDate, formatDuration, gradientFor } from '@/utils/format'
import { toast } from '@/utils/toast'
import FollowButton from '@/components/FollowButton.vue'
import DanmakuPlayer from '@/components/DanmakuPlayer.vue'
import CommentSection from '@/components/CommentSection.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// ---- videoId 解析（EX-1：非正整数直接页内错误态，不发请求）----
const invalidId = computed(() => {
  const raw = String(route.params.videoId)
  const n = Number(raw)
  return !/^\d+$/.test(raw) || !Number.isInteger(n) || n <= 0
})

const detail = ref<VideoDetail | null>(null)
const loading = ref(true)
const errorMsg = ref('')
const videoError = ref(false) // EX-3：视频文件加载失败，仅播放区错误态

// ---- 弹幕（DL02 一次拉齐；发送成功 pushLocal 上屏）----
const danmakuList = ref<DanmakuItem[]>([])
const danmakuPlayer = ref<InstanceType<typeof DanmakuPlayer> | null>(null)
let danmakuSeq = 1000000 // 本地新增弹幕临时 id（负向不与服务端冲突）

async function loadDanmaku(videoId: number) {
  try {
    const resp = await getDanmakuList(videoId)
    danmakuList.value = resp.list // 失败静默为空（FR-IN02 EX-2）
  } catch {
    danmakuList.value = []
  }
}

/** 弹幕发送（FR-IN01：校验→DL01→成功即上屏+清空；失败 toast 内容保留由子组件处理） */
async function onDanmakuSend(content: string, timeOffset: number) {
  if (!detail.value) return
  try {
    const resp = await sendDanmaku(detail.value.videoId, content, timeOffset)
    danmakuPlayer.value?.pushLocal(content, resp.danmakuId || --danmakuSeq)
    danmakuPlayer.value?.clearInput()
  } catch (e) {
    toast((e as Error).message || '发送失败，请重试') // 输入框内容保留（子组件不清空）
  }
}

// ---- 互动状态（IN02 一次拉齐：三状态 + 三计数 + 已投枚数）----
const interaction = ref<InteractionState | null>(null)
const myCoin = ref<number | null>(null) // 投币弹层余额展示（登录者）

async function loadInteraction(videoId: number) {
  try {
    interaction.value = await getInteractions(videoId)
  } catch {
    interaction.value = null // 静默：互动栏退化为详情页计数
  }
}

const likeCount = computed(() => interaction.value?.likeCount ?? detail.value?.likeCount ?? 0)
const coinCount = computed(() => interaction.value?.coinCount ?? 0)
const collectCount = computed(() => interaction.value?.collectCount ?? 0)

// ---- 右侧「接下来播放」（API-VD02 category 参数，一次固定 20 条，排除当前视频）----
// 同分类不足 20 条时回退全站最新补足（B 站相关推荐同款不限于同分区的呈现口径；
// 种子数据每分类仅 1~2 条，无降级时侧栏近乎恒空——06 §3.10 设计备注留痕）
const related = ref<VideoItem[]>([])
const relatedLoading = ref(false)
const RELATED_SIZE = 20

async function loadRelated(category: number) {
  relatedLoading.value = true
  related.value = []
  try {
    const seen = new Set<number>()
    const selfId = detail.value?.videoId
    const push = (list: VideoItem[]) => {
      for (const v of list) {
        if (v.videoId === selfId || seen.has(v.videoId)) continue
        seen.add(v.videoId)
        related.value.push(v)
      }
    }
    const sameCat = await getVideos({ page: 1, size: RELATED_SIZE, category })
    push(sameCat.list)
    if (related.value.length < RELATED_SIZE) {
      // 同分类不足：全站最新补足（无算法，仅补位）
      const fallback = await getVideos({ page: 1, size: RELATED_SIZE })
      push(fallback.list)
    }
  } catch {
    related.value = [] // 侧栏失败静默（主内容不受影响）
  } finally {
    relatedLoading.value = false
  }
}

const categoryName = computed(() =>
  detail.value ? CATEGORIES[detail.value.category - 1] ?? `分区${detail.value.category}` : '',
)

async function load() {
  if (invalidId.value) return // 非法 id 不发请求
  loading.value = true
  errorMsg.value = ''
  detail.value = null
  videoError.value = false
  interaction.value = null
  danmakuList.value = []
  const videoId = Number(route.params.videoId)
  try {
    detail.value = await getVideoDetail(videoId)
    loadDanmaku(videoId)
    loadInteraction(videoId)
    loadRelated(detail.value.category)
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败' // EX-2：40403 →「视频不存在」
  } finally {
    loading.value = false
  }
}

// ---- 游客引导登录（互动栏/弹幕/评论统一，FollowButton 范式）----
function requireLogin(): boolean {
  if (store.isLoggedIn) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

// ---- 点赞（FR-IN06：乐观更新 + 失败回滚 + 响应校准）----
const likeActing = ref(false)

async function onLike() {
  if (!detail.value || likeActing.value) return
  if (!requireLogin()) return
  if (!interaction.value) interaction.value = {
    liked: false, coined: false, favorited: false,
    likeCount: detail.value.likeCount, coinCount: 0, collectCount: 0, coinedCount: 0, folderId: null,
  }
  const prev = { ...interaction.value }
  // 乐观更新
  interaction.value.liked = !interaction.value.liked
  interaction.value.likeCount += interaction.value.liked ? 1 : -1
  likeActing.value = true
  try {
    const resp = await toggleLike(detail.value.videoId)
    interaction.value.liked = resp.liked
    interaction.value.likeCount = resp.likeCount
  } catch (e) {
    interaction.value.liked = prev.liked
    interaction.value.likeCount = prev.likeCount
    toast((e as Error).message || '操作失败，请重试')
  } finally {
    likeActing.value = false
  }
}

// ---- 收藏弹层（FR-IN15，Phase 4）：夹列表勾选式，替代直接 toggle ----
// 打开：IN07 拉夹列表 + IN02 folderId 初始化当前夹勾选；
// 勾选未收藏夹 → IN04 带 folderId（入夹/移夹）；取消勾选当前夹 → IN04 不带（整体取消）。
const favPanelOpen = ref(false)
const favFolders = ref<FavoriteFolderItem[]>([])
const favLoading = ref(false)
const favActing = ref(false) // 行级请求中（按 folderId 防并发）

function openFavPanel() {
  if (!requireLogin()) return
  favPanelOpen.value = true
  if (favFolders.value.length === 0) {
    favLoading.value = true
    getMyFolders()
      .then((resp) => (favFolders.value = resp.list))
      .catch((e) => {
        toast((e as Error).message || '收藏夹加载失败')
        favPanelOpen.value = false
      })
      .finally(() => (favLoading.value = false))
  }
}

/** 勾选某夹：未收藏 → 入该夹；已收藏他夹 → 移入该夹；已在该夹 → 幂等 no-op（不发请求） */
async function checkFolder(folder: FavoriteFolderItem) {
  if (!detail.value || favActing.value) return
  if (!interaction.value) interaction.value = {
    liked: false, coined: false, favorited: false,
    likeCount: detail.value.likeCount, coinCount: 0, collectCount: 0, coinedCount: 0, folderId: null,
  }
  const inter = interaction.value
  if (inter.favorited && inter.folderId === folder.folderId) return // 同夹重复勾选 no-op（EX-1）
  const prev = { ...inter }
  // 乐观更新：入夹/移夹 collectCount 不变；未收藏→收藏 +1
  if (!inter.favorited) inter.collectCount += 1
  inter.favorited = true
  favActing.value = true
  try {
    const resp = await toggleFavorite(detail.value.videoId, folder.folderId)
    inter.favorited = resp.favorited
    inter.collectCount = resp.collectCount
    inter.folderId = folder.folderId
    // 夹计数本地联动（重开弹层会重拉，此处仅刷新已加载列表）
    if (!prev.favorited) {
      folder.count += 1
    } else if (prev.folderId !== null) {
      const from = favFolders.value.find((f) => f.folderId === prev.folderId)
      if (from) from.count = Math.max(0, from.count - 1)
      folder.count += 1
    }
    toast(prev.favorited ? `已移入「${folder.name}」` : '收藏成功')
  } catch (e) {
    inter.favorited = prev.favorited
    inter.collectCount = prev.collectCount
    inter.folderId = prev.folderId
    toast((e as Error).message || '操作失败，请重试')
  } finally {
    favActing.value = false
  }
}

/** 取消勾选当前夹 → 整体取消收藏（不带 folderId，IN04 旧语义） */
async function uncheckFolder() {
  if (!detail.value || favActing.value) return
  if (!interaction.value || !interaction.value.favorited) return
  const prev = { ...interaction.value }
  const inter = interaction.value
  inter.favorited = false
  inter.collectCount -= 1
  favActing.value = true
  try {
    const resp = await toggleFavorite(detail.value.videoId)
    inter.favorited = resp.favorited
    inter.collectCount = resp.collectCount
    inter.folderId = null
    const from = favFolders.value.find((f) => f.folderId === prev.folderId)
    if (from) from.count = Math.max(0, from.count - 1)
    toast('已取消收藏')
  } catch (e) {
    inter.favorited = prev.favorited
    inter.collectCount = prev.collectCount
    inter.folderId = prev.folderId
    toast((e as Error).message || '操作失败，请重试')
  } finally {
    favActing.value = false
  }
}

/** 勾选行统一入口：点已勾选的当前夹 = 取消；点其他夹 = 勾选 */
function onToggleFolder(folder: FavoriteFolderItem) {
  const inter = interaction.value
  if (inter && inter.favorited && inter.folderId === folder.folderId) {
    uncheckFolder()
  } else {
    void checkFolder(folder)
  }
}

// ---- 投币弹层（FR-IN07：选 1/2 枚 → IN03；40905/40907 toast 弹层保留）----
const coinPanelOpen = ref(false)
const coinCountSel = ref(1)
const coinActing = ref(false)

function openCoinPanel() {
  if (!requireLogin()) return
  coinCountSel.value = 1
  coinPanelOpen.value = true
}

/** 已投满 2 枚后按钮置灰语义：coinedCount >= 2 时仍可打开弹层看提示，但确认按钮禁用 */
const coinCapped = computed(() => (interaction.value?.coinedCount ?? 0) >= 2)

async function doCoin() {
  if (!detail.value || coinActing.value || coinCapped.value) return
  coinActing.value = true
  try {
    const resp = await coinVideo(detail.value.videoId, coinCountSel.value)
    if (!interaction.value) interaction.value = {
      liked: false, coined: true, favorited: false,
      likeCount: 0, coinCount: 0, collectCount: 0, coinedCount: 0, folderId: null,
    }
    interaction.value.coinCount = resp.coinCount
    interaction.value.coinedCount = resp.coinedCount
    interaction.value.coined = resp.coinedCount > 0
    myCoin.value = resp.coin // 余额联动
    coinPanelOpen.value = false
    toast('投币成功')
  } catch (e) {
    // 40905 硬币不足 / 40907 已投满：toast 后弹层保留（FR-IN07 EX-1/EX-2）
    toast((e as Error).message || '投币失败，请重试')
  } finally {
    coinActing.value = false
  }
}

// ---- 分享（复制链接 + toast；降级 execCommand）----
async function onShare() {
  const link = window.location.href
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(link)
    } else {
      const ta = document.createElement('textarea')
      ta.value = link
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    toast('链接已复制')
  } catch {
    toast('复制失败，请手动复制地址栏链接')
  }
}

function goVideo(videoId: number) {
  router.push(`/video/${videoId}`) // 同路由参数变化 → watch 重载
}

onMounted(load)
// 路由参数变化（右侧列表点击切视频）：重载全部区块并回顶
watch(() => route.params.videoId, () => {
  load()
  window.scrollTo(0, 0)
})
</script>

<template>
  <div class="video-page container">
    <!-- EX-1：videoId 非正整数 → 页内错误态（不发请求） -->
    <div v-if="invalidId" class="state-box">
      <p>视频不存在</p>
      <router-link to="/" class="back-home">返回首页</router-link>
    </div>

    <!-- 加载态 -->
    <div v-else-if="loading" class="state-box">加载中…</div>

    <!-- 错误态（EX-2 40403 / 网络失败，可重试） -->
    <div v-else-if="errorMsg" class="state-box">
      <p>{{ errorMsg }}</p>
      <button class="back-home" @click="load">重试</button>
      <router-link to="/" class="back-home">返回首页</router-link>
    </div>

    <!-- 正常态 -->
    <div v-else-if="detail" class="layout">
      <div class="main-col">
        <!-- 播放器：DanmakuPlayer（原生 controls + 弹幕层 + 弹幕输入条，游客只看不发）；
             EX-3 视频文件加载失败仅播放区错误态 -->
        <DanmakuPlayer
          v-if="!videoError"
          ref="danmakuPlayer"
          :video-url="detail.videoUrl"
          :poster="detail.coverUrl ?? undefined"
          :danmaku-list="danmakuList"
          :logged-in="store.isLoggedIn"
          @send="onDanmakuSend"
        />
        <div v-else class="player-error">视频加载失败</div>

        <!-- 标题行 -->
        <h1 class="v-title">{{ detail.title }}</h1>
        <p class="v-stats">
          {{ formatCount(detail.viewCount) }}播放 · {{ formatCount(likeCount) }}点赞 · {{ formatDate(detail.createdAt) }}
        </p>

        <!-- 互动栏（FR-IN06/07/08）：点赞/收藏幂等切换乐观更新；投币弹层；分享复制链接；
             点赞高亮态 = liked；游客点击任一写按钮统一跳登录 -->
        <div class="interact-bar">
          <button class="act-item" :class="{ on: interaction?.liked }" @click="onLike">
            <i class="icon icon-like"></i><span>{{ formatCount(likeCount) }}</span>
          </button>
          <div class="coin-wrap">
            <button class="act-item" :class="{ on: interaction?.coined }" @click="openCoinPanel">
              <i class="icon icon-coin"></i><span>{{ formatCount(coinCount) }}</span>
            </button>
            <!-- 投币弹层：余额 + 1/2 枚选项 + 已投枚数提示 + 确认 -->
            <div v-if="coinPanelOpen" class="coin-panel-mask" @click.self="coinPanelOpen = false">
              <div class="coin-panel">
              <p class="cp-title">投币</p>
              <p class="cp-meta">我的硬币：{{ myCoin ?? '—' }} · 已投 {{ interaction?.coinedCount ?? 0 }}/2 枚</p>
              <div class="cp-options">
                <button class="cp-opt" :class="{ sel: coinCountSel === 1 }" :disabled="coinCapped" @click="coinCountSel = 1">
                  <i class="icon icon-coin"></i>投 1 枚
                </button>
                <button class="cp-opt" :class="{ sel: coinCountSel === 2 }" :disabled="coinCapped || (interaction?.coinedCount ?? 0) >= 1" @click="coinCountSel = 2">
                  <i class="icon icon-coin"></i><i class="icon icon-coin"></i>投 2 枚
                </button>
              </div>
              <p v-if="coinCapped" class="cp-capped">该视频已投满 2 枚硬币</p>
              <div class="cp-btns">
                <button class="cp-cancel" @click="coinPanelOpen = false">取消</button>
                <button class="cp-confirm" :disabled="coinActing || coinCapped" @click="doCoin">
                  {{ coinActing ? '投币中…' : '确认' }}
                </button>
              </div>
              </div>
            </div>
          </div>
          <!-- 收藏弹层（FR-IN15）：勾选式多夹，复用投币弹层范式 -->
          <div class="coin-wrap">
            <button class="act-item" :class="{ on: interaction?.favorited }" @click="openFavPanel">
              <i class="icon icon-star"></i><span>{{ formatCount(collectCount) }}</span>
            </button>
            <div v-if="favPanelOpen" class="coin-panel-mask" @click.self="favPanelOpen = false">
              <div class="coin-panel fav-panel">
                <p class="cp-title">收藏至</p>
                <div v-if="favLoading" class="fp-loading">加载中…</div>
                <template v-else>
                  <ul class="fp-list">
                    <li
                      v-for="f in favFolders"
                      :key="f.folderId"
                      class="fp-row"
                      :class="{ checked: interaction?.favorited && interaction?.folderId === f.folderId }"
                      @click="onToggleFolder(f)"
                    >
                      <span class="fp-check" aria-hidden="true"></span>
                      <span class="fp-name">{{ f.name }}</span>
                      <span v-if="f.isDefault" class="fp-default">默认</span>
                      <span class="fp-count">{{ f.count }}</span>
                    </li>
                  </ul>
                  <p class="fp-tip">勾选收藏 / 换夹移动；取消勾选即取消收藏</p>
                </template>
              </div>
            </div>
          </div>
          <button class="act-item" @click="onShare">
            <i class="icon icon-share"></i><span>分享</span>
          </button>
        </div>

        <!-- UP 主条：头像/昵称点击进空间 + 粉丝数 + 签名 + FollowButton 三态 -->
        <div class="up-bar">
          <router-link :to="`/u/${detail.up.userId}`" class="up-link">
            <img
              class="up-avatar"
              :src="detail.up.avatarUrl || defaultAvatar"
              alt="UP 头像"
              @error="(e: Event) => (e.target as HTMLImageElement).src = defaultAvatar"
            />
            <div class="up-info">
              <span class="up-nickname">{{ detail.up.nickname }}</span>
              <span class="up-fans">粉丝 {{ formatCount(detail.up.followerCount) }}</span>
            </div>
          </router-link>
          <p v-if="detail.up.sign" class="up-sign">{{ detail.up.sign }}</p>
          <FollowButton
            :user-id="detail.up.userId"
            :is-self="detail.isSelf"
            :is-followed="detail.isFollowed"
            @update="(f: boolean) => detail && (detail.isFollowed = f)"
          />
        </div>

        <!-- 简介与分区标签 -->
        <div v-if="detail.description || categoryName" class="desc-zone">
          <p v-if="detail.description" class="desc">{{ detail.description }}</p>
          <router-link class="cat-tag" :to="`/category/${detail.category}`">{{ categoryName }}</router-link>
        </div>

        <!-- 评论区（两级楼中楼，FR-IN03~IN05；游客只读） -->
        <CommentSection :video-id="detail.videoId" :logged-in="store.isLoggedIn" />
      </div>

      <!-- 右侧「接下来播放」（同分类 20 条；封面 + 标题 + UP名·播放数；点击页内切视频） -->
      <aside class="side-col">
        <h3 class="side-title">接下来播放</h3>
        <div v-if="relatedLoading" class="side-loading">加载中…</div>
        <template v-else-if="related.length">
          <button v-for="v in related" :key="v.videoId" class="rel-card" @click="goVideo(v.videoId)">
            <span class="rel-cover" :style="{ background: gradientFor(v.videoId) }">
              <img v-if="v.coverUrl" :src="v.coverUrl" alt="" />
              <span class="rel-dur">{{ formatDuration(v.duration) }}</span>
            </span>
            <span class="rel-info">
              <span class="rel-title">{{ v.title }}</span>
              <span class="rel-meta">{{ v.upNickname ? v.upNickname + ' · ' : '' }}{{ formatCount(v.viewCount) }}播放</span>
            </span>
          </button>
        </template>
        <div v-else class="side-loading">暂无更多</div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.video-page {
  padding: var(--space-block) 0 48px;
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
.state-box p {
  margin: 0;
}
.back-home {
  padding: 8px 24px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  border-radius: var(--radius-card);
  background: none;
  cursor: pointer;
  font-size: 14px;
  text-decoration: none;
}
.back-home:hover {
  background: var(--color-primary);
  color: #fff;
}
/* 左主栏自适应 + 右侧栏 350（对齐真实截图分栏比例） */
.layout {
  display: flex;
  gap: var(--space-block);
  align-items: flex-start;
}
.main-col {
  flex: 1;
  min-width: 0;
}
/* 播放区错误态（DanmakuPlayer 自带黑底圆角，此处同规格） */
.player-error {
  aspect-ratio: 16 / 9;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  background: #000;
  border-radius: var(--radius-card);
}
.v-title {
  font-size: 18px; /* 对齐真实截图标题字号 */
  margin: 16px 0 4px;
}
.v-stats {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
/* 互动栏：无容器裸排列（对齐真实截图），线性图标+文字同行，hover 只变主色；
   on=已赞/已投/已藏高亮主色 */
.interact-bar {
  display: flex;
  gap: 4px;
  margin: 14px 0 16px;
}
.act-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  background: none;
  border: none;
  font-size: 14px;
  color: var(--color-text);
  cursor: pointer;
  padding: 8px 20px;
}
.act-item:first-child {
  padding-left: 0; /* 首项与标题左缘对齐 */
}
.act-item:hover,
.act-item.on {
  color: var(--color-primary);
}
/* 四个 16px 纯 CSS 图标统一 currentColor（随文字灰↔hover 主色） */
.icon {
  flex-shrink: 0;
  position: relative;
  display: inline-block;
}
/* 点赞：心形（方块 rotate -45 + 两圆），色值取 currentColor */
.icon-like {
  width: 15px;
  height: 15px;
  background: currentColor;
  transform: rotate(-45deg);
  margin-right: 4px; /* 补偿 rotate 视觉偏移 */
}
.icon-like::before,
.icon-like::after {
  content: '';
  position: absolute;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  background: inherit;
}
.icon-like::before {
  top: -7.5px;
  left: 0;
}
.icon-like::after {
  top: 0;
  left: 7.5px;
}
/* 投币：圆环 + 中心竖槽 */
.icon-coin {
  width: 15px;
  height: 15px;
  border: 1.5px solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
}
.icon-coin::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 3px;
  height: 6px;
  background: currentColor;
  transform: translate(-50%, -50%);
  border-radius: 1px;
}
/* 收藏：五角星（clip-path 一次成型） */
.icon-star {
  width: 16px;
  height: 16px;
  background: currentColor;
  clip-path: polygon(50% 0%, 61.8% 35.2%, 98.1% 35.2%, 68.7% 57.2%, 79.9% 92.8%, 50% 71.7%, 20.1% 92.8%, 31.3% 57.2%, 1.9% 35.2%, 38.2% 35.2%);
}
/* 分享：底座 L 角 + 斜箭头 */
.icon-share {
  width: 15px;
  height: 15px;
  /* 底座 L 角：两段 box-shadow 画左竖线+下横线（伪元素已用于箭头） */
  box-shadow:
    -7px 6px 0 -6px currentColor,
    -7px 6px 0 -5px currentColor,
    -6px 8px 0 -6px currentColor,
    -5px 8px 0 -6px currentColor,
    -4px 8px 0 -6px currentColor,
    -3px 8px 0 -6px currentColor;
}
.icon-share::before {
  /* 斜箭头杆：旋转 45° 细条 */
  content: '';
  position: absolute;
  right: 1px;
  top: -2px;
  width: 2px;
  height: 15px;
  background: currentColor;
  border-radius: 1px;
  transform: rotate(45deg);
  transform-origin: bottom center;
}
.icon-share::after {
  /* 箭头两翼：border 画 V 字 */
  content: '';
  position: absolute;
  right: 0;
  top: 1px;
  width: 6px;
  height: 6px;
  border-top: 2px solid currentColor;
  border-right: 2px solid currentColor;
  transform: rotate(-45deg);
}
/* 投币弹层：浮于按钮下方（06 §3.10 投币弹层） */
.coin-wrap {
  position: relative;
  display: inline-flex;
}
.coin-panel-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  background: rgba(0, 0, 0, 0.25);
}
.coin-panel {
  position: absolute;
  top: 44px;
  left: 0;
  z-index: 91;
  width: 280px;
  padding: 18px;
  background: var(--color-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card-hover);
}
.cp-title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
}
.cp-meta {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.cp-options {
  display: flex;
  gap: 10px;
}
.cp-opt {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: none;
  font-size: 14px;
  color: var(--color-text);
  cursor: pointer;
}
.cp-opt.sel {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-primary-light);
}
.cp-opt:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.cp-opt .icon-coin {
  width: 13px;
  height: 13px;
}
.cp-capped {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--color-danger);
}
.cp-btns {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}
.cp-cancel {
  padding: 7px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: none;
  color: var(--color-text);
  font-size: 13px;
  cursor: pointer;
}
.cp-confirm {
  padding: 7px 18px;
  border: none;
  border-radius: var(--radius-card);
  background: var(--color-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.cp-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
/* 收藏弹层（FR-IN15）：夹列表勾选式 */
.fav-panel {
  width: 300px;
}
.fp-loading {
  padding: 24px 0;
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.fp-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 240px;
  overflow-y: auto;
}
.fp-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}
.fp-row:hover {
  background: var(--color-bg);
}
.fp-check {
  width: 16px;
  height: 16px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  flex-shrink: 0;
  position: relative;
}
.fp-row.checked .fp-check {
  background: var(--color-primary);
  border-color: var(--color-primary);
}
.fp-row.checked .fp-check::after {
  content: '';
  position: absolute;
  left: 4.5px;
  top: 1px;
  width: 4px;
  height: 8px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}
.fp-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fp-default {
  font-size: 11px;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: 4px;
  padding: 0 4px;
  flex-shrink: 0;
}
.fp-count {
  font-size: 12px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
.fp-tip {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
/* UP 主条 */
.up-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: var(--color-card);
  border-radius: var(--radius-card);
  flex-wrap: wrap;
}
.up-link {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
  color: var(--color-text);
}
.up-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
}
.up-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.up-nickname {
  font-size: 15px;
  font-weight: 600;
}
.up-link:hover .up-nickname {
  color: var(--color-primary);
}
.up-fans {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.up-sign {
  flex: 1;
  min-width: 120px;
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
/* 简介与分区标签 */
.desc-zone {
  margin-top: var(--space-block);
  padding: 16px;
  background: var(--color-card);
  border-radius: var(--radius-card);
}
.desc {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.cat-tag {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 999px;
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: 12px;
  text-decoration: none;
}
.cat-tag:hover {
  background: var(--color-primary);
  color: #fff;
}
/* 右侧栏（对齐真实截图：350px 白卡片） */
.side-col {
  width: 350px;
  flex-shrink: 0;
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 16px;
}
.side-title {
  margin: 0 0 12px;
  font-size: 15px;
}
.side-loading {
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 12px 0;
}
.rel-card {
  display: flex;
  gap: 10px;
  width: 100%;
  background: none;
  border: none;
  padding: 6px 0;
  cursor: pointer;
  text-align: left;
  border-bottom: 1px solid var(--color-border);
}
.rel-card:last-child {
  border-bottom: none;
}
.rel-cover {
  position: relative;
  width: 160px;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-card);
  overflow: hidden;
  flex-shrink: 0;
}
.rel-cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.rel-dur {
  position: absolute;
  right: 4px;
  bottom: 4px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 3px;
}
.rel-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  justify-content: space-between; /* 标题区与 meta 行上下撑满，对齐截图节奏 */
  padding: 1px 0;
}
.rel-title {
  font-size: 13px;
  line-height: 1.4;
  color: var(--color-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.rel-card:hover .rel-title {
  color: var(--color-primary);
}
.rel-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
