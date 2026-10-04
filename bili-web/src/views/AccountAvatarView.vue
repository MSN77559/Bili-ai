<script setup lang="ts">
// PG-07 我的头像子页（06 §3.7.2）：预览 + 更换（上传逻辑自 PG-04 迁入，FR-PF03 行为不变）
import { onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { getMyProfile, uploadAvatar } from '@/api'
import { defaultAvatar } from '@/utils/avatar'
import { toast } from '@/utils/toast'

const store = useUserStore()

// ---- 页面三态 ----
const loading = ref(true)
const loadError = ref('')

// ---- 头像状态 ----
const avatar = ref('')
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const MAX_SIZE = 2 * 1024 * 1024 // 2MB（FR-PF03）
const ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'webp']

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const p = await getMyProfile()
    avatar.value = p.avatarUrl
  } catch (e) {
    loadError.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

// ---- 头像上传（FR-PF03：选中即校验即上传，无确认步骤）----
function onPickFile() {
  if (uploading.value) return // EX-4：防并发上传
  fileInput.value?.click()
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 允许连续两次选同一文件
  if (!file) return
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (file.size > MAX_SIZE) {
    toast('图片不能超过 2MB') // EX-1：前端拦截不发请求（41301 文案与后端一致）
    return
  }
  if (!ALLOWED_EXT.includes(ext)) {
    toast('仅支持 jpg/png/webp 格式') // EX-2
    return
  }
  doUpload(file)
}

async function doUpload(file: File) {
  uploading.value = true
  try {
    const resp = await uploadAvatar(file)
    avatar.value = resp.avatarUrl
    toast('头像更新成功')
    store.applyUser(store.userId!, store.nickname, resp.avatarUrl) // NavBar 头像同步
  } catch (e) {
    toast('上传失败，请重试') // EX-5：预览保持旧头像
  } finally {
    uploading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="avatar-page">
    <!-- 加载态 -->
    <div v-if="loading" class="state-box">加载中…</div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="state-box">
      <p>{{ loadError }}</p>
      <button class="retry-btn" @click="load">重试</button>
    </div>

    <!-- 正常态（06 §3.7.2：标题 + 说明 + 预览 + 操作） -->
    <div v-else>
      <h2 class="sub-title">我的头像</h2>
      <p class="sub-desc">挑选一张图片作为你的头像，更换后立即生效</p>

      <div class="preview-row">
        <img
          class="avatar-preview"
          :src="avatar || defaultAvatar"
          alt="头像"
          @error="(e: Event) => (e.target as HTMLImageElement).src = defaultAvatar"
        />
        <div class="ops">
          <button class="change-btn" :disabled="uploading" @click="onPickFile">
            {{ uploading ? '上传中…' : '更换头像' }}
          </button>
          <p class="tip">jpg / png / webp，不超过 2MB</p>
        </div>
      </div>

      <input
        ref="fileInput"
        type="file"
        accept=".jpg,.jpeg,.png,.webp"
        class="hidden-input"
        @change="onFileChange"
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
.avatar-preview {
  width: 120px;
  height: 120px;
  border-radius: 50%;
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
