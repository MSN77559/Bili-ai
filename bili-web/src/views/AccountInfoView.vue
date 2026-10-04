<script setup lang="ts">
// PG-07 我的信息子页（06 §3.7.1）：基础资料表单 + 空间展示开关（自 PG-04 迁入，行为不变）
// 字段规则 SSOT 在 FR-PF02；不显示邮箱（ADR-08）；头像/背景区块在独立子页（我的头像/我的背景）
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { getMyProfile, updateMyProfile } from '@/api'
import { toast } from '@/utils/toast'

const store = useUserStore()

// ---- 页面三态（06 §6）----
const loading = ref(true)
const loadError = ref('')

// ---- 表单状态 ----
const nickname = ref('')
const gender = ref(0)
const birthday = ref('') // 空 = null（清空）
const sign = ref('')
// 空间展示开关（FR-PF05）+ 生日公开开关（FR-PF06）+ 关注/粉丝列表开关（FR-FO06，Phase 4）：1 公开 0 仅自己可见
const showContribute = ref(1)
const showFavorites = ref(1)
const showLikes = ref(1)
const showBirthday = ref(1)
const showFollows = ref(1)
const errors = ref<Record<string, string>>({})
const formError = ref('')
const saving = ref(false)

// 头像值仅用于保存成功后同步 NavBar（上传/预览在「我的头像」子页）
const avatar = ref('')

// 生日不可晚于当天（FR-PF02 EX-2：date 输入 max=今天）
const today = computed(() => {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const p = await getMyProfile()
    avatar.value = p.avatarUrl
    nickname.value = p.nickname
    gender.value = p.gender
    birthday.value = p.birthday ?? ''
    sign.value = p.sign
    showContribute.value = p.showContribute ? 1 : 0
    showFavorites.value = p.showFavorites ? 1 : 0
    showLikes.value = p.showLikes ? 1 : 0
    showBirthday.value = p.showBirthday ? 1 : 0
    showFollows.value = p.showFollows ? 1 : 0
  } catch (e) {
    loadError.value = (e as Error).message || '加载失败'
  } finally {
    loading.value = false
  }
}

// ---- 字段校验（blur + 提交双层，错误文案与后端一致 FR-PF02）----
function validateNickname(): boolean {
  const len = nickname.value.trim().length
  if (len < 2 || len > 16) {
    errors.value.nickname = '昵称长度须为 2-16 字符'
    return false
  }
  delete errors.value.nickname
  return true
}

function validateSign(): boolean {
  if (sign.value.length > 100) {
    errors.value.sign = '签名最长 100 字符'
    return false
  }
  delete errors.value.sign
  return true
}

// ---- 保存（API-US03）----
async function onSave() {
  formError.value = ''
  const ok = [validateNickname(), validateSign()].every(Boolean)
  if (!ok) return
  saving.value = true
  try {
    await updateMyProfile({
      nickname: nickname.value.trim(),
      gender: gender.value,
      birthday: birthday.value || null, // 空 = 清空
      sign: sign.value,
      showContribute: showContribute.value,
      showFavorites: showFavorites.value,
      showLikes: showLikes.value,
      showBirthday: showBirthday.value,
      showFollows: showFollows.value,
    })
    toast('保存成功')
    store.applyUser(store.userId!, nickname.value.trim(), avatar.value) // NavBar 昵称同步（FR-PF02 主流程 5）
  } catch (e) {
    formError.value = (e as Error).message || '保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="info-page">
    <!-- 加载态 -->
    <div v-if="loading" class="state-box">加载中…</div>

    <!-- 加载失败（FR-PF01 EX-1：错误态 + 重试） -->
    <div v-else-if="loadError" class="state-box">
      <p>{{ loadError }}</p>
      <button class="retry-btn" @click="load">重试</button>
    </div>

    <!-- 表单（06 §3.7.1） -->
    <div v-else class="form">
      <!-- 昵称 -->
      <div class="form-item">
        <label class="label">昵称</label>
        <input v-model="nickname" type="text" maxlength="16" @blur="validateNickname" />
        <p v-if="errors.nickname" class="err">{{ errors.nickname }}</p>
      </div>

      <!-- 性别 -->
      <div class="form-item">
        <label class="label">性别</label>
        <div class="radios">
          <label><input v-model.number="gender" type="radio" :value="0" />保密</label>
          <label><input v-model.number="gender" type="radio" :value="1" />男</label>
          <label><input v-model.number="gender" type="radio" :value="2" />女</label>
        </div>
      </div>

      <!-- 生日 -->
      <div class="form-item">
        <label class="label">生日</label>
        <input v-model="birthday" type="date" :max="today" />
        <p class="field-tip">清空即删除生日</p>
      </div>

      <!-- 签名 -->
      <div class="form-item">
        <label class="label">签名</label>
        <textarea v-model="sign" rows="3" maxlength="100" @blur="validateSign"></textarea>
        <p v-if="errors.sign" class="err">{{ errors.sign }}</p>
      </div>

      <!-- 空间展示（FR-PF05）+ 生日公开（FR-PF06）：关闭后对应内容对他人/游客隐藏，自己始终可见 -->
      <div class="form-item">
        <label class="label">空间展示</label>
        <div class="switches">
          <label class="switch-row">
            <input v-model.number="showContribute" type="checkbox" :true-value="1" :false-value="0" />
            <span>投稿列表公开</span>
          </label>
          <label class="switch-row">
            <input v-model.number="showFavorites" type="checkbox" :true-value="1" :false-value="0" />
            <span>收藏夹公开</span>
          </label>
          <label class="switch-row">
            <input v-model.number="showLikes" type="checkbox" :true-value="1" :false-value="0" />
            <span>点赞列表公开</span>
          </label>
          <label class="switch-row">
            <input v-model.number="showBirthday" type="checkbox" :true-value="1" :false-value="0" />
            <span>生日公开</span>
          </label>
          <label class="switch-row">
            <input v-model.number="showFollows" type="checkbox" :true-value="1" :false-value="0" />
            <span>公开关注/粉丝列表</span>
          </label>
        </div>
        <p class="field-tip">关闭后对应内容仅自己可见，其他用户与游客访问你的空间时不显示</p>
      </div>

      <p v-if="formError" class="err form-err">{{ formError }}</p>

      <button class="save-btn" :disabled="saving" @click="onSave">
        {{ saving ? '保存中…' : '保存' }}
      </button>
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
.form-item input[type='date'],
.form-item textarea {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 8px 12px;
  font-size: 14px;
  outline: none;
  font-family: inherit;
  box-sizing: border-box;
}
.form-item input:focus,
.form-item textarea:focus {
  border-color: var(--color-primary);
}
.radios {
  display: flex;
  gap: 24px;
  padding: 8px 0;
}
.radios label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  cursor: pointer;
}
.switches {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 0;
}
.switch-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  cursor: pointer;
}
.field-tip {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 0;
}
.err {
  color: var(--color-danger);
  font-size: 12px;
  margin: 4px 0 0;
}
.form-err {
  margin-bottom: 12px;
}
.save-btn {
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
.save-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
