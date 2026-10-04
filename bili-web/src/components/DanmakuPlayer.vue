<script setup lang="ts">
// DanmakuPlayer 弹幕播放器（06 §5，Phase 3，FR-IN01/IN02）：
// 原生 <video controls> 包裹 + CSS 自绘弹幕层（轨道池 + transform 位移 + rAF 调度，ADR-05 纪律不引第三方库）。
// - DL02 一次拉齐全量弹幕 → 按 timeOffset 建待发射队列；timeupdate 驱动：currentTime 越过 offset 即发射。
// - 轨道池固定 8 条：新弹幕选「空闲且尾部已足够远离右缘」的轨道；全忙则跳过（EX-3 不堆积）。
// - seek：已发射弹幕不回退重放，未发射的按新位置继续（EX-5）。
// - 弹幕开关：display 层面显隐，不停止调度（不持久化）。
// - 输入条：登录者可发（1~100 字 + 发送即上屏落库）；游客只看不发——输入条不渲染（EX-4）。
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import type { DanmakuItem } from '@/types/api'
import { toast } from '@/utils/toast'

const props = defineProps<{
  videoUrl: string
  poster?: string
  danmakuList: DanmakuItem[]
  loggedIn: boolean
  /** 顶部一层是否显示错误态占位（视频 src 失效由父组件处理时传 true） */
  videoDisabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'send', content: string, timeOffset: number): void
}>()

const videoEl = ref<HTMLVideoElement | null>(null)
const layerEl = ref<HTMLDivElement | null>(null)

const danmakuOn = ref(true) // 弹幕开关（FR-IN02 主流程 4）
const inputText = ref('')
const inputError = ref('')

// ---- 调度状态（切视频全量重置，FR-IN02 EX-5）----
const TRACKS = 8 // 轨道数（约 8 条）
const DURATION_MS = 8000 // 单条弹幕横穿时长（课程项目固定速度）
interface LiveDanmaku {
  id: number
  content: string
  track: number
  startMs: number // rAF 时间戳
  width: number
  x: number // 当前左缘位移（rAF 更新）
}
const live = reactive<LiveDanmaku[]>([])
const trackFreeAt = new Array(TRACKS).fill(0) // 各轨道「尾部进入时刻」——新弹幕须距其够远避免追尾
let pending: DanmakuItem[] = [] // 待发射（timeOffset 升序）
let launchedIds = new Set<number>() // 已发射 id（seek 后不重放）
let lastVideoMs = -1 // 上次 timeupdate 的 currentTime 毫秒（正向播放时才调度）
let rafId = 0
let liveSeq = 0

/** 弹幕层宽度（轨道分配的追尾判断用） */
function layerWidth(): number {
  return layerEl.value?.clientWidth ?? 800
}

/** 轨道分配：空闲（尾部已完全离开右缘）则返回轨道号，全忙返回 -1（EX-3 跳过） */
function pickTrack(now: number): number {
  const w = layerWidth()
  for (let i = 0; i < TRACKS; i++) {
    const since = now - trackFreeAt[i]
    // 上一条进入 DURATION_MS*(1 - w/(w+text)) 后尾部已离开右缘，经验系数 0.35 放宽（同一轨道不追尾即可）
    if (since >= DURATION_MS * 0.35 || trackFreeAt[i] === 0) return i
  }
  return -1
}

function launch(d: DanmakuItem, now: number) {
  const track = pickTrack(now)
  if (track < 0) return // 轨道全忙：跳过不堆积（EX-3）
  liveSeq += 1
  const width = Math.min(400, [...d.content].length * 16) // 估算宽度（15px 字号）
  live.push({ id: liveSeq, content: d.content, track, startMs: now, width, x: layerWidth() })
  trackFreeAt[track] = now
  launchedIds.add(d.danmakuId)
}

/** 单条弹幕行内样式（轨道高度百分比定位 + transform 位移） */
function dmStyle(d: LiveDanmaku) {
  return {
    top: `${(d.track + 0.25) * (100 / TRACKS)}%`,
    transform: `translateX(${d.x}px)`,
  }
}

/** rAF 帧：位移更新 + 移出左缘回收 */
function frame(now: number) {
  const w = layerWidth()
  for (let i = live.length - 1; i >= 0; i--) {
    const d = live[i]
    const progress = (now - d.startMs) / DURATION_MS
    if (progress >= 1) {
      live.splice(i, 1)
      continue
    }
    // 从右缘（w）平移到左缘外（-(d.width)），随 progress 线性插值
    d.x = w - (w + d.width) * progress
  }
  rafId = requestAnimationFrame(frame)
}

function onTimeUpdate() {
  const v = videoEl.value
  if (!v) return
  const ms = v.currentTime * 1000
  if (ms < lastVideoMs) {
    // seek 回退：清掉未发射队列中已过期的，按新位置继续
    lastVideoMs = ms
    return
  }
  lastVideoMs = ms
  // 到期弹幕入轨（容忍 500ms 抖动）
  while (pending.length && pending[0].timeOffset <= ms + 100) {
    const d = pending.shift()!
    if (launchedIds.has(d.danmakuId)) continue
    launch(d, performance.now())
  }
}

function resetSchedule() {
  live.splice(0)
  trackFreeAt.fill(0)
  launchedIds = new Set()
  lastVideoMs = -1
  pending = [...props.danmakuList].sort((a, b) => a.timeOffset - b.timeOffset)
}

// 弹幕数据变化（首次拉齐 / 切视频全量重载）→ 重置调度
watch(
  () => props.danmakuList,
  () => resetSchedule(),
)

/** 父组件调用：发送成功的弹幕立即上屏（FR-IN01 主流程 5，本地直接入队当前轨道） */
function pushLocal(content: string, danmakuId: number) {
  launch({ danmakuId, content, timeOffset: 0, mode: 1, nickname: '' }, performance.now())
}

function onSend() {
  const text = inputText.value.trim()
  if ([...text].length === 0) {
    inputError.value = '弹幕不能为空'
    return
  }
  if ([...text].length > 100) {
    inputError.value = '弹幕长度须为 1-100 字符'
    return
  }
  inputError.value = ''
  const ms = Math.round((videoEl.value?.currentTime ?? 0) * 1000)
  emit('send', text, ms) // 父组件调 DL01，成功后清空输入框并 pushLocal
}

function clearInput() {
  inputText.value = ''
  inputError.value = ''
}

defineExpose({ pushLocal, clearInput })

onMounted(() => {
  rafId = requestAnimationFrame(frame)
})
onBeforeUnmount(() => cancelAnimationFrame(rafId))
</script>

<template>
  <div class="danmaku-player">
    <div class="video-wrap">
      <video
        v-if="!videoDisabled"
        ref="videoEl"
        controls
        :src="videoUrl"
        :poster="poster"
        @timeupdate="onTimeUpdate"
      ></video>
      <div v-else class="player-error">视频加载失败</div>

      <!-- 弹幕层：绝对定位铺满、pointer-events:none；开关只控制 display -->
      <div v-show="danmakuOn" ref="layerEl" class="danmaku-layer">
        <span
          v-for="d in live"
          :key="d.id"
          class="dm"
          :style="dmStyle(d)"
        >{{ d.content }}</span>
      </div>
    </div>

    <!-- 弹幕输入条：登录者渲染（游客只看不发，EX-4） -->
    <div v-if="loggedIn" class="dm-input-bar">
      <button
        class="dm-toggle"
        :class="{ off: !danmakuOn }"
        :title="danmakuOn ? '关闭弹幕' : '开启弹幕'"
        @click="danmakuOn = !danmakuOn"
      >
        <i class="dot" :class="{ hidden: !danmakuOn }"></i>弹
      </button>
      <input
        v-model="inputText"
        class="dm-input"
        type="text"
        maxlength="100"
        placeholder="发个弹幕见证当下"
        @keyup.enter="onSend"
        @compositionend="inputError = ''"
      />
      <button class="dm-send" :disabled="!inputText.trim()" @click="onSend">发送</button>
      <p v-if="inputError" class="dm-error">{{ inputError }}</p>
    </div>
  </div>
</template>

<style scoped>
.danmaku-player {
  background: #000;
  border-radius: var(--radius-card);
  overflow: hidden;
}
.video-wrap {
  position: relative;
}
.video-wrap video {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
}
.player-error {
  aspect-ratio: 16 / 9;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
}
/* 弹幕层：铺满视频、不拦截交互 */
.danmaku-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
/* 单条弹幕：白字深描边（对齐 2026-08-26 截图样式），从右向左位移由 rAF 驱动 */
.dm {
  position: absolute;
  left: 100%;
  white-space: nowrap;
  font-size: 15px;
  line-height: 1;
  color: #fff;
  text-shadow:
    0 0 2px rgba(0, 0, 0, 0.9),
    1px 1px 2px rgba(0, 0, 0, 0.8);
  will-change: transform;
}
/* 弹幕输入条：底部横贯（开关 + 输入框 + 发送） */
.dm-input-bar {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #101014;
}
.dm-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: none;
  border: none;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  padding: 2px 4px;
}
.dm-toggle .dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  border: 1px solid #fff;
  background: var(--color-primary);
}
.dm-toggle.off .dot {
  background: transparent;
}
.dm-input {
  flex: 1;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #3a3a42;
  border-radius: 4px;
  background: #1c1c22;
  color: #fff;
  font-size: 13px;
  outline: none;
}
.dm-input:focus {
  border-color: var(--color-primary);
}
.dm-input::placeholder {
  color: #7a7a85;
}
.dm-send {
  height: 30px;
  padding: 0 18px;
  border: 1px solid var(--color-primary);
  border-radius: 4px;
  background: transparent;
  color: var(--color-primary);
  font-size: 13px;
  cursor: pointer;
}
.dm-send:hover:not(:disabled) {
  background: var(--color-primary);
  color: #fff;
}
.dm-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.dm-error {
  position: absolute;
  right: 12px;
  top: -22px;
  margin: 0;
  font-size: 12px;
  color: var(--color-danger);
  background: #fff;
  padding: 2px 8px;
  border-radius: 3px;
}
</style>
