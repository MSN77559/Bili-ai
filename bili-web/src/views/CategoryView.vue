<script setup lang="ts">
// PG-11 分类页（06 §3.11，Phase 2）：页头（分类名 + 视频总数）+ 4 列卡片网格（API-VD02 category 参数）
// + 加载更多（FR-VD03 同范式）+ 空分类优雅空态；id 非 1~14 → 404（FR-VD04 EX-1）
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ref } from 'vue'
import { getVideos } from '@/api'
import { CATEGORIES } from '@/mock/home'
import { useLoadMore } from '@/composables/useLoadMore'
import { toast } from '@/utils/toast'
import VideoCard from '@/components/VideoCard.vue'
import NotFoundView from '@/views/NotFoundView.vue'

const route = useRoute()

// ---- id 校验（EX-1：非 1~14 渲染 404 页）----
const rawId = computed(() => String(route.params.id))
const categoryId = computed(() => {
  const n = Number(rawId.value)
  return /^\d+$/.test(rawId.value) && Number.isInteger(n) && n >= 1 && n <= 14 ? n : null
})

const categoryName = computed(() =>
  categoryId.value ? CATEGORIES[categoryId.value - 1] : '',
)

// ---- 卡片网格（与首页同范式，category 固定为当前分区）----
const PAGE_SIZE = 12
const videos = useLoadMore((page) =>
  categoryId.value ? getVideos({ page, size: PAGE_SIZE, category: categoryId.value }) : Promise.resolve({ total: 0, list: [] }),
)

async function onLoadMore() {
  const ok = await videos.loadMore()
  if (!ok && videos.error) toast('加载失败，请重试')
}

// EX-5：分类切换（竖栏/导航进入另一分类）重置 page=1 重新加载
watch(categoryId, () => {
  if (categoryId.value) videos.loadFirst()
})

onMounted(() => {
  if (categoryId.value) videos.loadFirst()
})
</script>

<template>
  <!-- EX-1：id 非 1~14 → 404 页（内联渲染 NotFoundView，URL 保留） -->
  <NotFoundView v-if="categoryId === null" />

  <div v-else class="category-page container">
    <!-- 页头：分类名 + 视频总数 + 返回首页 -->
    <div class="cat-header">
      <h1 class="cat-title">{{ categoryName }} <span class="cat-count">· 共 {{ videos.total.value }} 个视频</span></h1>
      <router-link to="/" class="back-home">返回首页</router-link>
    </div>

    <!-- 三态 + 网格 + 加载更多（FR-VD03 同范式） -->
    <div v-if="!videos.loaded.value && videos.loading.value" class="cards-state">加载中…</div>
    <div v-else-if="!videos.loaded.value && videos.error.value" class="cards-state">
      <p>{{ videos.error.value }}</p>
      <button class="retry-btn" @click="videos.loadFirst">重试</button>
    </div>
    <!-- EX-2：空分类优雅空态 -->
    <div v-else-if="videos.items.value.length === 0" class="cards-state">该分区暂无视频</div>
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
  </div>
</template>

<style scoped>
.category-page {
  padding: var(--space-block) 0 48px;
}
.cat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 16px 24px;
  margin-bottom: var(--space-block);
}
.cat-title {
  margin: 0;
  font-size: 20px;
}
.cat-count {
  font-size: 14px;
  font-weight: 400;
  color: var(--color-text-secondary);
}
.back-home {
  padding: 8px 24px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  border-radius: var(--radius-card);
  font-size: 14px;
  text-decoration: none;
}
.back-home:hover {
  background: var(--color-primary);
  color: #fff;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-block);
}
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
