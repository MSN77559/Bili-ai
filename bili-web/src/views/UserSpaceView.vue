<script setup lang="ts">
// PG-03 个人主页（06 §3.3 v2.1）：资料头部 + 关注/拉黑按钮 + 空间页签
// Phase 2：投稿 tab 与主页 tab 接真实内容（API-VD04，FR-VD05）
// Phase 3：收藏夹/点赞 tab 真实化（API-IN05/IN06，FR-IN09/IN10；服务端隐私过滤，前端不重复判断）
// 2026-08-17 变更：移除 UID/注册时间展示；生日按 FR-PF06 可见性；新增拉黑（FR-BK01/02）
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPublicProfile, blockUser, unblockUser, uploadBanner, getUserVideos, getUserFavorites, getUserLikes } from '@/api'
import type { PublicProfile, VideoPageResp } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { defaultAvatar, defaultBanner } from '@/utils/avatar'
import { useLoadMore } from '@/composables/useLoadMore'
import { toast } from '@/utils/toast'
import FollowButton from '@/components/FollowButton.vue'
import VideoCard from '@/components/VideoCard.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const profile = ref<PublicProfile | null>(null)
const loading = ref(true)
const errorMsg = ref('')
const blockActing = ref(false) // 拉黑按钮请求中（FR-BK01 请求中态）

// ---- 背景图（FR-PF07）：默认图兜底 + 本人上传 ----
const bannerFailed = ref(false) // 图片加载失败 → 回退 data URI 默认图（EX-7）
const bannerUploading = ref(false)
const bannerInput = ref<HTMLInputElement | null>(null)

// 展示源：bannerUrl 加载失败时用 data URI 默认图（EX-7）；bannerUrl 恒非空（DB 列默认值）
const bannerSrc = computed(() =>
  bannerFailed.value || !profile.value?.bannerUrl ? defaultBanner : profile.value.bannerUrl,
)

const MAX_SIZE = 2 * 1024 * 1024 // 2MB（FR-PF07，与头像同规）
const ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'webp']

// 空间页签（2026-08-17 需求变更）：静态骨架，Phase 2/3 填充真实内容
// FR-PF05：tab 可见性由后端按视角返回（本人恒可见；他人/游客按主人开关）
const ALL_TABS = [
  { key: 'home', label: '主页', visible: true },
  { key: 'contribute', label: '投稿', visible: 'showContribute' },
  { key: 'favorites', label: '收藏夹', visible: 'showFavorites' },
  { key: 'likes', label: '点赞', visible: 'showLikes' },
] as const
const activeTab = ref<string>('home')

const visibleTabs = computed(() =>
  ALL_TABS.filter((t) => t.visible === true || !!(profile.value as Record<string, unknown> | null)?.[t.visible]),
)

const genderText = computed(() => {
  if (!profile.value) return ''
  return profile.value.gender === 1 ? '男' : profile.value.gender === 2 ? '女' : ''
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  const raw = String(route.params.userId)
  const uid = Number(raw)
  if (!/^\d+$/.test(raw) || !Number.isInteger(uid) || uid <= 0) {
    loading.value = false
    errorMsg.value = '用户不存在' // FR-PR03 EX-1：非正整数按不存在处理
    return
  }
  try {
    profile.value = await getPublicProfile(uid)
    activeTab.value = 'home' // 切换用户后重置页签（防残留被隐藏 tab）
    bannerFailed.value = false // 重置回退标记
    videos.loadFirst() // FR-VD05：投稿数据（主页 tab 默认激活即取数）
  } catch (e) {
    errorMsg.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

// ---- 投稿列表（API-VD04，FR-VD05）：主页 tab 前 4 条网格 + 投稿 tab 完整网格共用 ----
// show_contribute=0 时他人/游客 tab 不渲染（上方 visibleTabs）+ 接口空列表双保险（服务端过滤）
const videos = useLoadMore((page) => getUserVideos(uidForVideos(), page, 12))

function uidForVideos(): number {
  return profile.value?.userId ?? 0 // load() 成功后才有值；fetcher 只在 loadFirst 之后执行
}

// ---- 收藏/点赞列表（API-IN05/IN06，FR-IN09/IN10）：首次激活 tab 时取数 ----
// 服务端按 show_favorites/show_likes 过滤（他人/游客且开关关 → 空列表 200，前端不重复判断）
const favorites = useLoadMore((page) => getUserFavorites(uidForVideos(), page, 12))
const likes = useLoadMore((page) => getUserLikes(uidForVideos(), page, 12))

/** 当前 tab 对应的数据源（contribute/favorites/likes；home 复用 videos 前 4 条） */
const activeList = computed(() => {
  if (activeTab.value === 'favorites') return favorites
  if (activeTab.value === 'likes') return likes
  return videos
})

/** tab 首次激活取数（已加载过不重拉） */
watch(activeTab, (tab) => {
  if (tab === 'favorites' && !favorites.loaded.value) favorites.loadFirst()
  if (tab === 'likes' && !likes.loaded.value) likes.loadFirst()
})

async function onLoadMore() {
  const ok = await activeList.value.loadMore()
  if (!ok && activeList.value.error) toast('加载失败，请重试')
}

// ---- 背景图上传（FR-PF07 主流程：选中即校验即上传即生效）----
function onBannerPick() {
  if (bannerUploading.value) return // EX-4：防并发上传
  bannerInput.value?.click()
}

function onBannerChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 允许连续两次选同一文件
  if (!file) return
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (file.size > MAX_SIZE) {
    toast('图片不能超过 2MB') // EX-1：前端拦截不发请求
    return
  }
  if (!ALLOWED_EXT.includes(ext)) {
    toast('背景图仅支持 jpg/png/webp 格式') // EX-2
    return
  }
  doBannerUpload(file)
}

async function doBannerUpload(file: File) {
  bannerUploading.value = true
  try {
    const resp = await uploadBanner(file)
    if (profile.value) {
      profile.value.bannerUrl = resp.bannerUrl
      bannerFailed.value = false
    }
    toast('背景更新成功')
  } catch (e) {
    toast((e as Error).message || '上传失败，请重试') // EX-5：banner 保持旧值
  } finally {
    bannerUploading.value = false
  }
}

// FR-FO04：关注按钮三态（Phase 2 起抽 FollowButton 组件，空间页与播放页复用；
// 本地刷新用返回最新值，不重拉——FR-FO03）
function onFollowUpdate(isFollowed: boolean, followerCount: number) {
  if (!profile.value) return
  profile.value.isFollowed = isFollowed
  profile.value.followerCount = followerCount
}

// FR-BK01/BK02：拉黑/取消拉黑（均无确认框，与 FR-FO02 取关风格一致）
async function onBlockClick() {
  if (!profile.value || blockActing.value) return
  if (!store.isLoggedIn) {
    // 游客：跳登录带回跳（FR-BK01 EX-4，与关注按钮同范式）
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  blockActing.value = true
  try {
    const resp = profile.value.isBlocked
      ? await unblockUser(profile.value.userId) // 已拉黑 → 直接取消无确认框（FR-BK02）
      : await blockUser(profile.value.userId) // 直接拉黑无确认框（FR-BK01）
    // 本地 patch：isBlocked/isFollowed/followerCount（双向取关后 isFollowed 必为 false）
    profile.value.isBlocked = resp.isBlocked
    profile.value.isFollowed = false
    profile.value.followerCount = resp.followerCount
    // followingCount 若对方曾关注我方视角存在短暂陈旧，下次进页自然修正（FR-BK01 设计备注）
  } catch (e) {
    toast((e as Error).message || '操作失败')
  } finally {
    blockActing.value = false
  }
}

onMounted(load)
watch(() => route.params.userId, load)
</script>

<template>
  <div class="space">
    <!-- 加载态 -->
    <div v-if="loading" class="state-box loading-box">加载中…</div>

    <!-- 错误态（FR-PR03：用户不存在） -->
    <div v-else-if="errorMsg" class="state-box error-box">
      <p>{{ errorMsg }}</p>
      <router-link to="/" class="back-home">返回首页</router-link>
    </div>

    <!-- 正常态（06 §3.3 v1.5：banner + 信息条 + 功能入口行 + 内容区） -->
    <div v-else-if="profile" class="space">
      <!-- ① 背景 banner（FR-PF07）：始终显示图片；未设置/加载失败回退 data URI 默认图 -->
      <div class="space-banner">
        <img
          class="banner-img"
          :src="bannerSrc"
          alt="空间背景图"
          @error="bannerFailed = true"
        />
        <!-- 本人视角悬停出现更换入口（FR-PF07 主流程入口①） -->
        <button
          v-if="profile.isSelf"
          class="banner-change"
          :disabled="bannerUploading"
          @click="onBannerPick"
        >{{ bannerUploading ? '上传中…' : '更换背景' }}</button>
        <input
          ref="bannerInput"
          type="file"
          accept=".jpg,.jpeg,.png,.webp"
          class="banner-hidden-input"
          @change="onBannerChange"
        />
      </div>

      <!-- ② 白色信息条：大头像压 banner + 资料 + 操作按钮矩阵 -->
      <div class="space-info container">
        <img class="avatar" :src="profile.avatarUrl || defaultAvatar" alt="头像"
          @error="(e: Event) => (e.target as HTMLImageElement).src = defaultAvatar" />
        <div class="info">
          <div class="row-1">
            <h2 class="nickname">{{ profile.nickname }}</h2>
            <!-- 操作按钮（FR-FO04 + FR-BK01/BK02 矩阵）：本人=编辑；已拉黑=仅「已拉黑」；否则关注三态（FollowButton）+ 拉黑入口 -->
            <div class="actions">
              <template v-if="profile.isBlocked && !profile.isSelf">
                <button class="btn outline" :disabled="blockActing" @click="onBlockClick">
                  {{ blockActing ? '处理中…' : '已拉黑' }}
                </button>
              </template>
              <template v-else>
                <FollowButton
                  :user-id="profile.userId"
                  :is-self="profile.isSelf"
                  :is-followed="profile.isFollowed"
                  @update="onFollowUpdate"
                />
                <button v-if="!profile.isSelf" class="block-btn" :disabled="blockActing" @click="onBlockClick">拉黑</button>
              </template>
            </div>
          </div>
          <p v-if="profile.sign" class="sign">{{ profile.sign }}</p>
          <p v-if="genderText || profile.birthday" class="meta-line">
            <span v-if="genderText">性别：{{ genderText }}</span>
            <span v-if="profile.birthday">生日：{{ profile.birthday }}</span>
          </p>
        </div>
      </div>

      <!-- ③ 功能入口行：空间页签（静态骨架）+ 右侧计数 -->
      <div class="space-nav container">
        <div class="space-tabs">
          <button
            v-for="t in visibleTabs"
            :key="t.key"
            class="space-tab"
            :class="{ on: activeTab === t.key }"
            @click="activeTab = t.key"
          >{{ t.label }}</button>
        </div>
        <!-- FR-FO03：计数可点入列表（Phase 1.5，FR-FO05）；三视角均可点 -->
        <div class="counts">
          <router-link class="count" :to="`/u/${profile.userId}/followings`">
            <b>{{ profile.followingCount }}</b>关注
          </router-link>
          <router-link class="count" :to="`/u/${profile.userId}/followers`">
            <b>{{ profile.followerCount }}</b>粉丝
          </router-link>
        </div>
      </div>

      <!-- ④ 内容区（Phase 3）：主页 tab 最新投稿网格（前 4 条）+ 投稿/收藏夹/点赞 tab 完整网格 + 加载更多 -->
      <div class="container content-zone">
        <!-- 主页 tab：最新投稿网格（同数据源取前 4，FR-VD05 主流程 3） -->
        <template v-if="activeTab === 'home'">
          <div v-if="videos.loading.value && !videos.loaded.value" class="content-placeholder">加载中…</div>
          <div v-else-if="videos.items.value.length === 0" class="content-placeholder">暂无投稿</div>
          <div v-else class="card-grid">
            <VideoCard
              v-for="v in videos.items.value.slice(0, 4)"
              :key="v.videoId"
              :video="v"
              :show-up="false"
            />
          </div>
        </template>

        <!-- 投稿/收藏夹/点赞 tab：完整网格 + 加载更多（同范式，数据源按 tab 切换） -->
        <template v-else>
          <div v-if="!activeList.loaded.value && activeList.loading.value" class="content-placeholder">加载中…</div>
          <div v-else-if="!activeList.loaded.value && activeList.error.value" class="content-placeholder">
            <p>{{ activeList.error.value }}</p>
            <button class="retry-btn" @click="activeList.loadFirst">重试</button>
          </div>
          <!-- 空三态：本人=真无收藏/点赞；他人视角空也可能是隐私过滤（服务端已过滤，前端不区分） -->
          <div v-else-if="activeList.items.value.length === 0" class="content-placeholder">
            {{ activeTab === 'favorites' ? '暂无收藏' : activeTab === 'likes' ? '暂无点赞' : '暂无投稿' }}
          </div>
          <template v-else>
            <div class="card-grid">
              <VideoCard
                v-for="v in activeList.items.value"
                :key="v.videoId"
                :video="v"
                :show-up="activeTab !== 'contribute'"
              />
            </div>
            <div v-if="activeList.hasMore.value" class="load-more-zone">
              <button class="load-more-btn" :disabled="activeList.loading.value" @click="onLoadMore">
                {{ activeList.loading.value ? '加载中…' : '加载更多' }}
              </button>
            </div>
          </template>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.container {
  max-width: var(--width-container);
  margin: 0 auto;
  padding: 0 var(--space-block);
}
.state-box.container {
  /* 三态兜底：保留原内边距节奏 */
  padding-top: var(--space-block);
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
/* ① banner（FR-PF07）：始终为 img（默认图 data URI 兜底） */
.space-banner {
  position: relative;
  height: 200px;
  overflow: hidden;
}
.banner-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
/* 本人悬停出现更换入口（右上角半透明按钮） */
.banner-change {
  position: absolute;
  top: 12px;
  right: 16px;
  padding: 6px 16px;
  border: none;
  border-radius: var(--radius-card);
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}
.space-banner:hover .banner-change {
  opacity: 1;
}
.banner-change:hover {
  background: rgba(0, 0, 0, 0.6);
}
.banner-change:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.banner-hidden-input {
  display: none;
}
/* ② 信息条：白卡片压 banner */
.space-info {
  display: flex;
  gap: 20px;
  background: var(--color-card);
  margin-top: -32px;
  border-radius: var(--radius-card);
  padding: 24px;
  position: relative;
}
.avatar {
  width: var(--size-avatar);
  height: var(--size-avatar);
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #fff;
  margin-top: -36px; /* 头像上缘顶进 banner */
  flex-shrink: 0;
  align-self: flex-start;
}
.info {
  flex: 1;
}
.row-1 {
  display: flex;
  align-items: center;
  gap: 16px;
}
.nickname {
  margin: 0;
  font-size: 20px;
}
/* 操作区容器收敛 auto margin（FR-BK01：关注+拉黑双按钮并排） */
.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.btn {
  height: 32px;
  padding: 0 20px;
  border-radius: 999px;
  font-size: 14px;
  cursor: pointer;
}
.btn.solid {
  background: var(--color-primary);
  color: #fff;
  border: none;
}
.btn.outline {
  background: var(--color-card);
  color: var(--color-text);
  border: 1px solid var(--color-border);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
/* 拉黑入口：次要文字按钮（danger 色，06 §3.3 按钮矩阵） */
.block-btn {
  background: none;
  border: none;
  font-size: 12px;
  color: var(--color-danger);
  cursor: pointer;
  padding: 0 4px;
}
.block-btn:hover {
  text-decoration: underline;
}
.block-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.sign {
  margin: 8px 0 0;
  font-size: 14px;
}
.meta-line {
  margin: 8px 0 0;
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
/* ③ 功能入口行 */
.space-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-card);
  border-radius: var(--radius-card);
  margin-top: var(--space-block);
  padding: 0 24px;
}
.space-tabs {
  display: flex;
  gap: 28px;
}
.space-tab {
  background: none;
  border: none;
  font-size: 15px;
  padding: 12px 4px;
  cursor: pointer;
  color: var(--color-text-secondary);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.space-tab:hover {
  color: var(--color-primary);
}
.space-tab.on {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: 600;
}
/* FR-FO03：数字加粗 + 标签灰；Phase 1.5 起可点入列表（hover 主色下划线） */
.counts {
  display: flex;
  gap: 24px;
}
.count {
  color: var(--color-text-secondary);
  font-size: 12px;
  text-decoration: none;
}
.count:hover b {
  color: var(--color-primary);
}
.count:hover {
  text-decoration: underline;
  text-decoration-color: var(--color-primary);
}
.count b {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
  margin-right: 4px;
}
/* ④ 内容区（Phase 2：投稿网格 + 占位并存） */
.content-zone {
  margin-top: var(--space-block);
}
.content-placeholder {
  padding: 48px 0;
  text-align: center;
  color: var(--color-text-secondary);
  min-height: 160px;
}
.content-placeholder p {
  margin: 0 0 12px;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-block);
}
.retry-btn {
  padding: 8px 24px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  background: none;
  border-radius: var(--radius-card);
  cursor: pointer;
}
.retry-btn:hover {
  background: var(--color-primary);
  color: #fff;
}
.load-more-zone {
  text-align: center;
  padding: 16px 0 8px;
}
.load-more-btn {
  padding: 8px 32px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
  border-radius: 999px;
  font-size: 14px;
  cursor: pointer;
}
.load-more-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.load-more-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
