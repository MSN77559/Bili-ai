<script setup lang="ts">
// PG-07 我的背景子页（06 §3.7.3）：横条预览 + 更换（上传逻辑自 PG-04 迁入，FR-PF07 行为不变）
import { onMounted, ref } from 'vue'
import { getMyProfile, uploadBanner } from '@/api'
import { defaultBanner } from '@/utils/avatar'
import { toast } from '@/utils/toast'

// ---- 页面三态 ----
const loading = ref(true)
const loadError = ref('')

// ---- 背景图状态（bannerUrl 恒非空，默认 default.png）----
const banner = ref('')
const bannerUploading = ref(false)
const bannerInput = ref<HTMLInputElement | null>(null)

const MAX_SIZE = 2 * 1024 * 1024 // 2MB（FR-PF07，与头像同规）
const ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'webp']

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const p = await getMyProfile()
    banner.value = p.bannerUrl
  } catch (e) {
    loadError.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

// ---- 背景图上传（FR-PF07 主流程：选中即校验即上传即生效，与头像同构）----
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
    toast('图片不能超过 2MB') // EX-1
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
    banner.value = resp.bannerUrl
    toast('背景更新成功')
  } catch (e) {
    toast((e as Error).message || '上传失败，请重试') // EX-5：banner 保持旧值
  } finally {
    bannerUploading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="banner-page">
    <!-- 加载态 -->
    <div v-if="loading" class="state-box">加载中…</div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="state-box">
      <p>{{ loadError }}</p>
      <button class="retry-btn" @click="load">重试</button>
    </div>

    <!-- 正常态（06 §3.7.3：标题 + 说明 + 预览 + 操作） -->
    <div v-else>
      <h2 class="sub-title">我的背景</h2>
      <p class="sub-desc">背景图展示在你的个人空间主页顶部，对所有访客可见</p>

      <div class="preview-row">
        <div class="banner-preview">
          <!-- banner 恒非空（DB 列默认值），|| 仅为初始加载前兜底 -->
          <img
            class="banner-img"
            :src="banner || defaultBanner"
            alt="背景图预览"
            @error="(e: Event) => (e.target as HTMLImageElement).src = defaultBanner"
          />
        </div>
        <div class="ops">
          <button class="change-btn" :disabled="bannerUploading" @click="onBannerPick">
            {{ bannerUploading ? '上传中…' : '更换背景图' }}
          </button>
          <p class="tip">jpg / png / webp，不超过 2MB；未设置时使用默认背景图</p>
        </div>
      </div>

      <input
        ref="bannerInput"
        type="file"
        accept=".jpg,.jpeg,.png,.webp"
        class="hidden-input"
        @change="onBannerChange"
      />
    </div>
  </div>
</template>

<style scoped>
.state-box {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-block);
  color: var(--color-text-secondary);
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
.sub-title {
  margin: 0 0 4px;
  font-size: 18px;
}
.sub-desc {
  margin: 0 0 24px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.preview-row {
  display: flex;
  align-items: center;
  gap: 24px;
}
/* 横条预览（自设图/默认图双态，恒有图） */
.banner-preview {
  position: relative;
  width: 240px;
  height: 120px;
  border-radius: var(--radius-card);
  overflow: hidden;
  flex-shrink: 0;
}
.banner-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ops {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.change-btn {
  width: fit-content;
  padding: 8px 20px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  background: none;
  border-radius: var(--radius-card);
  font-size: 14px;
  cursor: pointer;
}
.change-btn:hover {
  background: var(--color-primary);
  color: #fff;
}
.change-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.tip {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 0;
}
.hidden-input {
  display: none;
}
</style>
