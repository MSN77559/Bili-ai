<script setup lang="ts">
// PG-01 首页（06 §3.1 v2.0）：横幅 + 左侧分类竖栏（14 分类可点跳分类页）+ 轮播图（mock 维持）
// + 4 列卡片（API-VD02 真实数据 + 加载更多，TD-06 偿还；Phase 2 起卡片可点入）
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { CATEGORIES, SLIDES, SIDEBAR_TAGS, SIDEBAR_LINKS } from '@/mock/home'
import { getVideos } from '@/api'
import { useLoadMore } from '@/composables/useLoadMore'
import { toast } from '@/utils/toast'
import VideoCard from '@/components/VideoCard.vue'

// ---- 卡片区：真实数据 + 加载更多（FR-VD03：初始 page=1&size=12，「加载更多」追加下一页）----
const PAGE_SIZE = 12
const videos = useLoadMore((page) => getVideos({ page, size: PAGE_SIZE }))

async function onLoadMore() {
  const ok = await videos.loadMore()
  if (!ok && videos.error) toast('加载失败，请重试') // EX-3：toast + 按钮恢复，已加载内容保留
}

onMounted(videos.loadFirst)

// ---- 轮播（维持 mock）：4s 自动切、悬停暂停、箭头/指示点可切 ----
const current = ref(0)
let timer: number | undefined

function go(i: number) {
  current.value = (i + SLIDES.length) % SLIDES.length
}
function start() {
  stop()
  timer = window.setInterval(() => go(current.value + 1), 4000)
}
function stop() {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
}
function onCarouselEnter() {
  stop()
}
function onCarouselLeave() {
  start()
}
start()
onBeforeUnmount(stop) // 路由离开销毁定时器，防泄漏
</script>

<template>
  <div class="home">
    <!-- 绿色渐变横幅：纯装饰，无响应（ADR-09） -->
    <div class="home-banner" aria-hidden="true"></div>

    <div class="home-body container">
      <!-- 左侧分类竖栏（FR-HM04 变更后：14 分类可点跳分类页；标签行/功能链接 toast 占位，ADR-09 变更注记 2026-09-07） -->
      <aside class="sidebar">
        <div class="side-tags">
          <span v-for="t in SIDEBAR_TAGS" :key="t" class="side-tag" @click="toast('功能开发中')">{{ t }}</span>
        </div>
        <div class="side-cats">
          <router-link
            v-for="(c, i) in CATEGORIES"
            :key="c"
            class="cat"
            :to="`/category/${i + 1}`"
          >{{ c }}</router-link>
        </div>
        <div class="side-links">
          <span v-for="l in SIDEBAR_LINKS" :key="l" class="side-link" @click="toast('功能开发中')">{{ l }}</span>
        </div>
      </aside>

      <section class="main-col">
        <!-- 轮播图：箭头/指示点切换 + 自动轮播，slide 主体无响应（维持 mock） -->
        <div
          class="carousel"
          @mouseenter="onCarouselEnter"
          @mouseleave="onCarouselLeave"
        >
          <div
            v-for="(s, i) in SLIDES"
            :key="s.id"
            class="slide"
            :class="{ on: i === current }"
            :style="{ background: s.gradient }"
          >
            <div class="slide-text">
              <h3>{{ s.title }}</h3>
              <p>{{ s.subtitle }}</p>
            </div>
          </div>
          <button class="car-btn prev" aria-label="上一张" @click="go(current - 1)"></button>
          <button class="car-btn next" aria-label="下一张" @click="go(current + 1)"></button>
          <div class="dots">
            <span
              v-for="(s, i) in SLIDES"
              :key="s.id"
              class="dot"
              :class="{ on: i === current }"
              @click="go(i)"
            ></span>
          </div>
        </div>

        <!-- FR-HM05（变更后）：真实数据卡片网格，4 列，可点入；三态 + 加载更多 -->
        <div v-if="!videos.loaded.value && videos.loading.value" class="cards-state">加载中…</div>
        <div v-else-if="!videos.loaded.value && videos.error.value" class="cards-state">
          <p>{{ videos.error.value }}</p>
          <button class="retry-btn" @click="videos.loadFirst">重试</button>
        </div>
        <div v-else-if="videos.items.value.length === 0" class="cards-state">暂无视频</div>
        <template v-else>
          <div class="card-grid">
            <VideoCard v-for="v in videos.items.value" :key="v.videoId" :video="v" />
          </div>
          <div v-if="videos.hasMore.value" class="load-more-zone">
            <button class="load-more-btn" :disabled="videos.loading.value" @click="onLoadMore">
              {{ videos.loading.value ? '加载中…' : '加载更多' }}
            </button>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.container {
  max-width: var(--width-container);
  margin: 0 auto;
  padding: var(--space-block);
}
/* 横幅：绿色渐变 + 光点纹理（ADR-05 模拟插画） */
.home-banner {
  height: var(--height-home-banner);
  background:
    radial-gradient(rgba(255, 255, 255, 0.35) 2px, transparent 2px),
    radial-gradient(rgba(255, 255, 255, 0.25) 1.5px, transparent 1.5px),
    var(--gradient-home-banner);
  background-size: 120px 60px, 80px 80px, 100% 100%;
}
.home-body {
  display: flex;
  gap: var(--space-block);
  align-items: flex-start;
}
/* 左侧竖栏 */
.sidebar {
  width: var(--width-sidebar);
  flex-shrink: 0;
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 12px;
  position: sticky;
  top: calc(var(--size-nav-height) + var(--space-block));
}
.side-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.side-tag {
  flex: 1;
  text-align: center;
  padding: 6px 0;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer; /* ADR-09 变更注记 2026-09-07：toast 占位反馈 */
}
.side-tag:hover {
  background: var(--color-primary-light);
  color: var(--color-primary);
}
.side-tag:first-child {
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-weight: 600;
}
.side-cats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px;
  padding: 8px 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}
.cat {
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 14px;
  color: var(--color-text);
  text-decoration: none;
  cursor: pointer; /* FR-HM04 变更后：14 分类可点跳分类页 */
}
.cat:hover {
  background: var(--color-primary-light);
  color: var(--color-primary); /* EX-2：仅底色反馈，无跳转暗示 */
}
.side-links {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px;
  padding-top: 8px;
}
.side-link {
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer; /* ADR-09 变更注记 2026-09-07：toast 占位反馈 */
}
.side-link:hover {
  color: var(--color-primary);
}
/* 右侧主列 */
.main-col {
  flex: 1;
  min-width: 0;
}
/* 轮播 */
.carousel {
  position: relative;
  height: var(--height-carousel);
  border-radius: var(--radius-card);
  overflow: hidden;
  cursor: default; /* slide 主体无响应 */
  margin-bottom: var(--space-block);
}
.slide {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.5s ease;
  display: flex;
  align-items: flex-end;
}
.slide.on {
  opacity: 1;
}
.slide-text {
  padding: 20px 24px;
  color: #fff;
}
.slide-text h3 {
  margin: 0 0 4px;
  font-size: 22px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}
.slide-text p {
  margin: 0;
  font-size: 13px;
  opacity: 0.9;
}
/* 箭头按钮：CSS 三角 */
.car-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 56px;
  border: none;
  background: rgba(0, 0, 0, 0.35);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}
.carousel:hover .car-btn {
  opacity: 1;
}
.car-btn.prev {
  left: 0;
  border-radius: 0 6px 6px 0;
}
.car-btn.next {
  right: 0;
  border-radius: 6px 0 0 6px;
}
.car-btn.prev::before {
  content: '';
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  border: 7px solid transparent;
  border-right-color: #fff;
  border-left-width: 0;
}
.car-btn.next::before {
  content: '';
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  border: 7px solid transparent;
  border-left-color: #fff;
  border-right-width: 0;
}
.dots {
  position: absolute;
  right: 12px;
  bottom: 10px;
  display: flex;
  gap: 6px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  cursor: pointer;
}
.dot.on {
  background: #fff;
}
/* 卡片网格（FR-HM05 变更后：VideoCard 组件渲染，样式在组件内） */
.card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-block);
}
/* 卡片区三态 + 加载更多（FR-VD03） */
.cards-state {
  padding: 48px 0;
  text-align: center;
  color: var(--color-text-secondary);
  min-height: 160px;
}
.cards-state p {
  margin: 0 0 12px;
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
