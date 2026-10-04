<script setup lang="ts">
// VideoCard 视频卡片（06 §5，FR-HM05）：首页/分类页/空间投稿网格共用。
// 16:9 封面（coverUrl 有值渲染图片（onerror 回退渐变）；null 按 videoId 取模 GRADIENTS 渐变兜底 ADR-17）
// + 左下播放/点赞双角标（万级格式化）+ 右下时长角标 + 标题 2 行 + UP 名 · 短日期。
// Phase 2 起可点入：整卡 router-link → /video/:videoId（cursor pointer + hover 上浮）。
import { computed, ref } from 'vue'
import type { VideoItem } from '@/types/api'
import { formatCount, formatDateShort, formatDuration, gradientFor } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    video: VideoItem
    /** 是否显示 UP 名（VD02 条目有；VD04 空间投稿主人页头已知，默认 true） */
    showUp?: boolean
  }>(),
  { showUp: true },
)

// 封面图加载失败 → 回退渐变（06 §3.1 卡片封面规则）
const coverFailed = ref(false)
const showCoverImg = computed(() => !!props.video.coverUrl && !coverFailed.value)
const coverStyle = computed(() => ({ background: gradientFor(props.video.videoId) }))

// meta 行：UP 名 · MM-DD（无 UP 名时只日期）
const metaText = computed(() => {
  const up = props.showUp && props.video.upNickname ? `${props.video.upNickname} · ` : ''
  return `${up}${formatDateShort(props.video.createdAt)}`
})
</script>

<template>
  <router-link class="video-card" :to="`/video/${video.videoId}`">
    <div class="cover" :style="coverStyle">
      <img v-if="showCoverImg" :src="video.coverUrl!" alt="" @error="coverFailed = true" />
      <div class="badges-left">
        <span class="badge"><i class="icon-play"></i>{{ formatCount(video.viewCount) }}</span>
        <span class="badge"><i class="icon-like"></i>{{ formatCount(video.likeCount) }}</span>
      </div>
      <span class="duration">{{ formatDuration(video.duration) }}</span>
    </div>
    <p class="v-title">{{ video.title }}</p>
    <p class="v-meta">{{ metaText }}</p>
  </router-link>
</template>

<style scoped>
.video-card {
  transition: all 0.2s ease;
  cursor: pointer; /* FR-HM05 EX-1：Phase 2 起可点入 */
  border-radius: var(--radius-card);
  text-decoration: none;
  color: var(--color-text);
  display: block;
}
.video-card:hover {
  transform: translateY(-2px); /* EX-2：hover 上浮反馈（Phase 1 既有） */
  box-shadow: var(--shadow-card-hover);
}
.cover {
  position: relative;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-card);
  overflow: hidden;
}
.cover img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
/* 左下双角标（FR-HM05）：播放 + 点赞并排，半透明黑底白字胶囊 */
.badges-left {
  position: absolute;
  left: 6px;
  bottom: 6px;
  display: flex;
  gap: 6px;
}
.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
}
/* CSS 播放三角（ADR-05：无二进制图标资源） */
.icon-play {
  width: 0;
  height: 0;
  border: 4px solid transparent;
  border-left-color: #fff;
  border-left-width: 6px;
  border-right-width: 0;
  flex-shrink: 0;
  margin-left: 1px;
}
/* CSS 点赞图标：45° 正方形 + 双圆伪元素近似拇指/心形 */
.icon-like {
  position: relative;
  width: 8px;
  height: 7px;
  transform: rotate(-45deg);
  background: #fff;
  border-radius: 0 0 0 1px;
  flex-shrink: 0;
  margin: 2px 2px 0 1px;
}
.icon-like::before,
.icon-like::after {
  content: '';
  position: absolute;
  width: 5px;
  height: 5px;
  background: #fff;
  border-radius: 50%;
}
.icon-like::before {
  left: 0;
  top: -2.5px;
}
.icon-like::after {
  left: -2.5px;
  top: 0;
}
.duration {
  position: absolute;
  right: 6px;
  bottom: 6px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
}
.v-title {
  margin: 8px 0 2px;
  font-size: 14px;
  line-height: 1.4;
  height: 2.8em;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
}
.v-title:hover {
  color: var(--color-primary);
}
.v-meta {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
