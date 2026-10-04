<script setup lang="ts">
// PG-09 投稿页（06 §3.9，Phase 2）：视频文件选择（loadedmetadata 自动读时长，ADR-17）
// + 可选封面 + 标题/分区/简介表单（blur+提交双层校验，NFR-05）→ API-VD01 → 跳 /video/:videoId
// 字段规则 SSOT 在 FR-VD01；路由 requiresAuth（游客被守卫拦截带 redirect 回跳）
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { publishVideo } from '@/api'
import { CATEGORIES } from '@/mock/home'
import { formatDuration } from '@/utils/format'
import { toast } from '@/utils/toast'

const router = useRouter()

// ---- 视频文件（选中即校验 + loadedmetadata 读时长）----
const VIDEO_EXTS = ['mp4', 'webm']
const VIDEO_MAX_BYTES = 500 * 1024 * 1024
const videoInput = ref<HTMLInputElement | null>(null)
const videoFile = ref<File | null>(null)
const duration = ref(0) // 秒；0=未读出（发布按钮禁用）
const readingDuration = ref(false)

// ---- 封面（可选，≤2MB，预览）----
const COVER_EXTS = ['jpg', 'jpeg', 'png', 'webp']
const COVER_MAX_BYTES = 2 * 1024 * 1024
const coverInput = ref<HTMLInputElement | null>(null)
const coverFile = ref<File | null>(null)
const coverPreview = ref('') // objectURL
let coverUrl: string | null = null

// ---- 表单字段（NFR-05：blur + 提交双层校验，错误内联）----
const title = ref('')
const category = ref('') // select 字符串，提交转数字
const description = ref('')
const errors = ref<Record<string, string>>({})
const formError = ref('')
const saving = ref(false)

const canSubmit = computed(
  () => !!videoFile.value && duration.value > 0 && !readingDuration.value && !saving.value,
)

function extOf(name: string): string {
  return name.split('.').pop()?.toLowerCase() ?? ''
}

function onPickVideo() {
  videoInput.value?.click()
}

function onVideoChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 允许重复选择同一文件
  if (!file) return
  if (!VIDEO_EXTS.includes(extOf(file.name))) {
    toast('仅支持 mp4/webm 格式') // EX-1：不合规 toast 拦截，不发请求
    return
  }
  if (file.size > VIDEO_MAX_BYTES) {
    toast('视频不能超过 500MB') // EX-2：超大文件前端 file.size 预判拦截
    return
  }
  videoFile.value = file
  duration.value = 0
  readDuration(file) // 合规则 <video> loadedmetadata 自动读时长
}

/** 离屏 <video> 读元数据（ADR-17 时长前端读取）；解析失败按 EX-4 处理 */
function readDuration(file: File) {
  readingDuration.value = true
  const url = URL.createObjectURL(file)
  const el = document.createElement('video')
  el.preload = 'metadata'
  el.src = url
  const done = () => URL.revokeObjectURL(url)
  el.onloadedmetadata = () => {
    done()
    readingDuration.value = false
    const d = Math.round(el.duration)
    if (!isFinite(d) || d < 1 || d > 86400) {
      // 浏览器无法解析元数据（如 Infinity 的流式 webm）→ 提示重选并清空
      toast('无法读取视频时长，请重新选择')
      videoFile.value = null
      duration.value = 0
      return
    }
    duration.value = d
  }
  el.onerror = () => {
    done()
    readingDuration.value = false
    toast('无法读取视频时长，请重新选择')
    videoFile.value = null
    duration.value = 0
  }
}

function onPickCover() {
  coverInput.value?.click()
}

function onCoverChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!COVER_EXTS.includes(extOf(file.name))) {
    toast('封面仅支持 jpg/png/webp 格式')
    return
  }
  if (file.size > COVER_MAX_BYTES) {
    toast('图片不能超过 2MB') // EX-3
    return
  }
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
  coverFile.value = file
  coverPreview.value = URL.createObjectURL(file)
}

/** 移除已选封面（回到 coverUrl=null 走渐变兜底） */
function removeCover() {
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
  coverFile.value = null
  coverPreview.value = ''
}

// ---- 字段校验（错误文案与后端一致 FR-VD01）----
function validateTitle(): boolean {
  const len = [...title.value.trim()].length // 码点计数（与后端 codePointCount 对齐）
  if (len < 2 || len > 80) {
    errors.value.title = '标题长度须为 2-80 字符'
    return false
  }
  delete errors.value.title
  return true
}

function validateCategory(): boolean {
  const n = Number(category.value)
  if (!category.value || !Number.isInteger(n) || n < 1 || n > 14) {
    errors.value.category = '请选择分区' // EX-6
    return false
  }
  delete errors.value.category
  return true
}

function validateDescription(): boolean {
  if ([...description.value].length > 500) {
    errors.value.description = '简介最长 500 字符'
    return false
  }
  delete errors.value.description
  return true
}

// ---- 发布（API-VD01；EX-7 期间按钮 disabled 防重复；EX-8 失败恢复保留表单与文件）----
async function onPublish() {
  formError.value = ''
  const ok = [validateTitle(), validateCategory(), validateDescription()].every(Boolean)
  if (!ok || !canSubmit.value) return
  saving.value = true
  try {
    const form = new FormData()
    form.append('file', videoFile.value!)
    if (coverFile.value) form.append('cover', coverFile.value)
    form.append('title', title.value.trim())
    form.append('category', category.value)
    form.append('description', description.value)
    form.append('duration', String(duration.value))
    const resp = await publishVideo(form)
    toast('发布成功')
    router.push(`/video/${resp.videoId}`) // 跳新视频播放页（FR-VD01 主流程 6）
  } catch (e) {
    formError.value = (e as Error).message || '发布失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(() => {
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value)
})
</script>

<template>
  <div class="upload-page container">
    <div class="upload-card">
      <h1 class="page-title">投稿</h1>

      <!-- 视频文件选择 -->
      <div class="form-item">
        <label class="label">视频文件</label>
        <input
          ref="videoInput"
          type="file"
          accept=".mp4,.webm"
          class="hidden-input"
          @change="onVideoChange"
        />
        <div class="pick-zone" @click="onPickVideo">
          <template v-if="!videoFile">
            <p class="pick-main">{{ readingDuration ? '正在读取视频信息…' : '选择视频（点击选择文件）' }}</p>
            <p class="pick-sub">mp4 / webm，不超过 500MB</p>
          </template>
          <template v-else>
            <p class="pick-file">{{ videoFile.name }}</p>
            <p class="pick-sub">
              {{ (videoFile.size / 1024 / 1024).toFixed(1) }}MB ·
              {{ readingDuration ? '读取时长中…' : duration > 0 ? `时长 ${formatDuration(duration)}（${duration} 秒）` : '时长读取失败' }}
            </p>
            <p class="pick-sub tip">点击可重新选择</p>
          </template>
        </div>
      </div>

      <!-- 封面（可选） -->
      <div class="form-item">
        <label class="label">封面（可选）</label>
        <input
          ref="coverInput"
          type="file"
          accept=".jpg,.jpeg,.png,.webp"
          class="hidden-input"
          @change="onCoverChange"
        />
        <div class="cover-row">
          <div v-if="coverPreview" class="cover-preview">
            <img :src="coverPreview" alt="封面预览" />
            <button class="cover-remove" type="button" @click="removeCover">移除</button>
          </div>
          <button v-else class="cover-pick" type="button" @click="onPickCover">
            选择封面 jpg/png/webp ≤2MB
          </button>
          <p class="field-tip">不上传封面时将使用渐变色作为封面</p>
        </div>
      </div>

      <!-- 标题 -->
      <div class="form-item">
        <label class="label">标题</label>
        <input v-model="title" type="text" maxlength="80" placeholder="2-80 字符" @blur="validateTitle" />
        <p v-if="errors.title" class="err">{{ errors.title }}</p>
      </div>

      <!-- 分区 -->
      <div class="form-item">
        <label class="label">分区</label>
        <select v-model="category">
          <option value="" disabled>请选择分区</option>
          <option v-for="(c, i) in CATEGORIES" :key="c" :value="String(i + 1)">{{ c }}</option>
        </select>
        <p v-if="errors.category" class="err">{{ errors.category }}</p>
      </div>

      <!-- 简介 -->
      <div class="form-item">
        <label class="label">简介</label>
        <textarea v-model="description" rows="4" maxlength="500" placeholder="最多 500 字符（可空）"></textarea>
        <p v-if="errors.description" class="err">{{ errors.description }}</p>
      </div>

      <p v-if="formError" class="err form-err">{{ formError }}</p>

      <button class="publish-btn" :disabled="!canSubmit" @click="onPublish">
        {{ saving ? '发布中…' : '发布' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.upload-page {
  padding: var(--space-block) 0 48px;
}
.upload-card {
  max-width: 720px;
  margin: 0 auto;
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 32px 40px;
}
.page-title {
  font-size: 22px;
  margin: 0 0 24px;
}
.hidden-input {
  display: none;
}
.pick-zone {
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-card);
  padding: 28px 16px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s;
}
.pick-zone:hover {
  border-color: var(--color-primary);
}
.pick-main {
  margin: 0 0 6px;
  font-size: 15px;
}
.pick-file {
  margin: 0 0 6px;
  font-size: 14px;
  word-break: break-all;
}
.pick-sub {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.pick-sub.tip {
  margin-top: 6px;
}
.cover-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.cover-preview {
  position: relative;
}
.cover-preview img {
  width: 160px;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border-radius: var(--radius-card);
  display: block;
}
.cover-remove {
  position: absolute;
  right: 4px;
  bottom: 4px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  border: none;
  border-radius: 3px;
  font-size: 11px;
  padding: 2px 8px;
  cursor: pointer;
}
.cover-pick {
  border: 1px dashed var(--color-border);
  background: none;
  color: var(--color-text-secondary);
  border-radius: var(--radius-card);
  padding: 20px 24px;
  font-size: 13px;
  cursor: pointer;
}
.cover-pick:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
.form-item {
  margin-bottom: 18px;
}
.label {
  display: block;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}
.form-item input[type='text'],
.form-item select,
.form-item textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 8px 12px;
  font-size: 14px;
  outline: none;
  font-family: inherit;
  box-sizing: border-box;
  background: var(--color-card);
}
.form-item input:focus,
.form-item select:focus,
.form-item textarea:focus {
  border-color: var(--color-primary);
}
.field-tip {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 6px 0 0;
}
.err {
  color: var(--color-danger);
  font-size: 12px;
  margin: 4px 0 0;
}
.form-err {
  margin-bottom: 12px;
}
.publish-btn {
  width: 100%;
  height: 40px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-card);
  font-size: 15px;
  cursor: pointer;
  margin-top: 8px;
}
.publish-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
