<script setup lang="ts">
// SearchVideoCard 搜索结果横向卡片（06 §3.12 v2.1，2026-09-07 排版修复）：仅 PG-12 视频 tab 使用。
// 左 16:9 封面（封面/角标/渐变兜底规则与 VideoCard 完全一致）+ 右信息区
// （分类标签 + 标题 2 行 + UP·日期 + 播放/点赞/时长 meta 行）；整卡 router-link → /video/:videoId。
import { computed, ref } from 'vue'
import type { VideoItem } from '@/types/api'
import { CATEGORIES } from '@/mock/home'
import { formatCount, formatDateShort, formatDuration, gradientFor } from '@/utils/format'

const props = defineProps<{
  video: VideoItem
}>()

// 封面图加载失败 → 回退渐变（与 VideoCard 同规则）
const coverFailed = ref(false)
const showCoverImg = computed(() => !!props.video.coverUrl && !coverFailed.value)
const coverStyle = computed(() => ({ background: gradientFor(props.video.videoId) }))

// 分类标签：category 1~14 → CATEGORIES 名称（SSOT 在 mock/home.ts）
const categoryName = computed(() => CATEGORIES[props.video.category - 1] ?? '')

// meta 行：UP 名 · MM-DD（无 UP 名时只日期，与 VideoCard 同口径）
const upDateText = computed(() => {
  const up = props.video.upNickname ? `${props.video.upNickname} · ` : ''
  return `${up}${formatDateShort(props.video.createdAt)}`
})
</script>

<template>
  <router-link class="sv-card" :to="`/video/${video.videoId}`">
    <div class="cover" :style="coverStyle">
      <img v-if="showCoverImg" :src="video.coverUrl!" alt="" @error="coverFailed = true" />
      <div class="badges-left">
        <span class="badge"><i class="icon-play"></i>{{ formatCount(video.viewCount) }}</span>
        <span class="badge"><i class="icon-like"></i>{{ formatCount(video.likeCount) }}</span>
      </div>
      <span class="duration">{{ formatDuration(video.duration) }}</span>
    </div>
    <div class="info">
      <div class="info-top">
        <span class="cat-tag">{{ categoryName }}</span>
      </div>
      <p class="v-title">{{ video.title }}</p>
      <p class="v-meta">{{ upDateText }}</p>
      <p class="v-meta">
        {{ formatCount(video.viewCount) }}播放 · {{ formatCount(video.likeCount) }}点赞 ·
        {{ formatDuration(video.duration) }}
      </p>
    </div>
  </router-link>
</template>

<style scoped>
.sv-card {
  display: flex;
  gap: 14px;
  padding: 12px;
  background: var(--color-card);
  border-radius: var(--radius-card);
  text-decoration: none;
  color: var(--color-text);
  cursor: pointer;
  transition: all 0.2s ease;
}
.sv-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card-hover);
}
/* 左封面：固定宽 16:9，角标规则与 VideoCard 一致 */
.cover {
  position: relative;
  width: 200px;
  aspect-ratio: 16 / 9;
  flex-shrink: 0;
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
/* CSS 播放三角（与 VideoCard 同款） */
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
/* CSS 点赞图标（与 VideoCard 同款） */
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
/* 右信息区 */
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.info-top {
  min-height: 20px;
}
.cat-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: 12px;
}
.v-title {
  margin: 0;
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
