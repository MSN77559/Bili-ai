<script setup lang="ts">
// PG-02 登录注册页（06 §3.2）：三界面（登录/注册/忘记密码）
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { register, login, sendResetCode, verifyResetCode, resetPassword } from '@/api'
import { toast } from '@/utils/toast'

const EMAIL_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

const route = useRoute()
const router = useRouter()
const store = useUserStore()

type Panel = 'login' | 'register' | 'forgot'
const panel = ref<Panel>('login')

// ---- 登录/注册共用状态（切换清空，FR-LG01）----
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const errors = ref<Record<string, string>>({})
const formError = ref('')
const submitting = ref(false)

// ---- 忘记密码（FR-AU05 三步向导）----
const fpStep = ref(1)
const fpCode = ref('')
const fpNewPassword = ref('')
const fpConfirm = ref('')
const fpToken = ref('')
const countdown = ref(0)

function switchPanel(p: Panel) {
  panel.value = p
  // 每次切换清空全部输入与错误（FR-LG01 AC-1）
  email.value = ''
  password.value = ''
  confirmPassword.value = ''
  errors.value = {}
  formError.value = ''
  fpStep.value = 1
  fpCode.value = ''
  fpNewPassword.value = ''
  fpConfirm.value = ''
  fpToken.value = ''
}

function validateEmail(): boolean {
  if (!email.value.trim()) {
    errors.value.email = '邮箱不能为空'
  } else if (!EMAIL_RE.test(email.value.trim())) {
    errors.value.email = '邮箱格式不正确'
  } else {
    delete errors.value.email
    return true
  }
  return false
}

function validatePassword(): boolean {
  if (!password.value) {
    errors.value.password = '密码不能为空'
  } else if (password.value.length < 6 || password.value.length > 32) {
    errors.value.password = '密码长度须为 6-32 位'
  } else {
    delete errors.value.password
    return true
  }
  return false
}

function validateConfirm(): boolean {
  if (confirmPassword.value !== password.value) {
    errors.value.confirmPassword = '两次输入的密码不一致'
    return false
  }
  delete errors.value.confirmPassword
  return true
}

/** redirect 回跳规则（FR-AU02 备注）：仅接受以 / 开头的站内路径 */
function afterLogin() {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/')) {
    router.push(redirect)
  } else {
    router.push('/')
  }
}

async function doLogin() {
  formError.value = ''
  const ok = [validateEmail(), validatePassword()].every(Boolean)
  if (!ok) return
  submitting.value = true
  try {
    const resp = await login(email.value.trim(), password.value)
    store.setLogin(resp.token, resp.user)
    toast('登录成功')
    afterLogin()
  } catch (e) {
    formError.value = (e as Error).message || '登录失败'
  } finally {
    submitting.value = false
  }
}

async function doRegister() {
  formError.value = ''
  const ok = [validateEmail(), validatePassword(), validateConfirm()].every(Boolean)
  if (!ok) return
  submitting.value = true
  try {
    const resp = await register(email.value.trim(), password.value)
    store.setLogin(resp.token, resp.user) // 注册即登录（ADR-12）
    toast('注册成功')
    router.push('/')
  } catch (e) {
    formError.value = (e as Error).message || '注册失败'
  } finally {
    submitting.value = false
  }
}

// ---- 忘记密码三步 ----
function startCountdown() {
  countdown.value = 60
  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function doSendCode() {
  delete errors.value.email
  if (!validateEmail()) return
  submitting.value = true
  try {
    await sendResetCode(email.value.trim())
    toast('验证码已发送')
    startCountdown()
    fpStep.value = 2
  } catch (e) {
    errors.value.email = (e as Error).message
  } finally {
    submitting.value = false
  }
}

async function doVerifyCode() {
  if (!/^\d{6}$/.test(fpCode.value)) {
    errors.value.code = '验证码应为 6 位数字'
    return
  }
  delete errors.value.code
  submitting.value = true
  try {
    const resp = await verifyResetCode(email.value.trim(), fpCode.value)
    fpToken.value = resp.resetToken
    fpStep.value = 3
  } catch (e) {
    errors.value.code = (e as Error).message
  } finally {
    submitting.value = false
  }
}

async function doReset() {
  if (fpNewPassword.value.length < 6 || fpNewPassword.value.length > 32) {
    errors.value.newPassword = '密码长度须为 6-32 位'
    return
  }
  delete errors.value.newPassword
  if (fpConfirm.value !== fpNewPassword.value) {
    errors.value.fpConfirm = '两次输入的密码不一致'
    return
  }
  delete errors.value.fpConfirm
  submitting.value = true
  try {
    await resetPassword(fpToken.value, fpNewPassword.value)
    toast('密码已重置，请使用新密码登录')
    // 重置不自动登录（FR-AU05）：切回登录界面并预填邮箱
    const keepEmail = email.value.trim()
    switchPanel('login')
    email.value = keepEmail
  } catch (e) {
    formError.value = (e as Error).message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="auth-panel">
      <!-- 登录/注册切换标签 -->
      <div v-if="panel !== 'forgot'" class="tabs">
        <button :class="{ on: panel === 'login' }" @click="switchPanel('login')">登录</button>
        <button :class="{ on: panel === 'register' }" @click="switchPanel('register')">注册</button>
      </div>

      <!-- 登录界面 -->
      <form v-if="panel === 'login'" @submit.prevent="doLogin">
        <div class="field">
          <input v-model="email" type="text" placeholder="邮箱" @blur="validateEmail" />
          <p v-if="errors.email" class="err">{{ errors.email }}</p>
        </div>
        <div class="field">
          <input v-model="password" type="password" placeholder="密码（6-32 位）" @blur="validatePassword" />
          <p v-if="errors.password" class="err">{{ errors.password }}</p>
        </div>
        <p v-if="formError" class="err form-err">{{ formError }}</p>
        <button class="submit" type="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
        <a class="forgot-link" @click.prevent="switchPanel('forgot')">忘记密码？</a>
      </form>

      <!-- 注册界面 -->
      <form v-else-if="panel === 'register'" @submit.prevent="doRegister">
        <div class="field">
          <input v-model="email" type="text" placeholder="邮箱" @blur="validateEmail" />
          <p v-if="errors.email" class="err">{{ errors.email }}</p>
        </div>
        <div class="field">
          <input v-model="password" type="password" placeholder="密码（6-32 位）" @blur="validatePassword" />
          <p v-if="errors.password" class="err">{{ errors.password }}</p>
        </div>
        <div class="field">
          <input v-model="confirmPassword" type="password" placeholder="确认密码" @blur="validateConfirm" />
          <p v-if="errors.confirmPassword" class="err">{{ errors.confirmPassword }}</p>
        </div>
        <p v-if="formError" class="err form-err">{{ formError }}</p>
        <button class="submit" type="submit" :disabled="submitting">
          {{ submitting ? '注册中…' : '注册' }}
        </button>
      </form>

      <!-- 忘记密码三步向导 -->
      <div v-else class="forgot">
        <h3 class="fp-title">忘记密码</h3>

        <template v-if="fpStep === 1">
          <div class="field">
            <input v-model="email" type="text" placeholder="注册邮箱" @blur="validateEmail" />
            <p v-if="errors.email" class="err">{{ errors.email }}</p>
          </div>
          <button class="submit" :disabled="submitting || countdown > 0" @click="doSendCode">
            {{ countdown > 0 ? `重新发送(${countdown}s)` : submitting ? '发送中…' : '发送验证码' }}
          </button>
        </template>

        <template v-else-if="fpStep === 2">
          <p class="fp-tip">验证码已发送至 {{ email }}（开发环境请查看后端控制台 MockMail 日志）</p>
          <div class="field">
            <input v-model="fpCode" type="text" maxlength="6" placeholder="6 位验证码" />
            <p v-if="errors.code" class="err">{{ errors.code }}</p>
          </div>
          <button class="submit" :disabled="submitting" @click="doVerifyCode">下一步</button>
        </template>

        <template v-else>
          <div class="field">
            <input v-model="fpNewPassword" type="password" placeholder="新密码（6-32 位）" />
            <p v-if="errors.newPassword" class="err">{{ errors.newPassword }}</p>
          </div>
          <div class="field">
            <input v-model="fpConfirm" type="password" placeholder="确认新密码" />
            <p v-if="errors.fpConfirm" class="err">{{ errors.fpConfirm }}</p>
          </div>
          <p v-if="formError" class="err form-err">{{ formError }}</p>
          <button class="submit" :disabled="submitting" @click="doReset">重置密码</button>
        </template>

        <a class="forgot-link" @click.prevent="switchPanel('login')">返回登录</a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: calc(100vh - var(--size-nav-height));
  display: flex;
  align-items: center;
  justify-content: center;
}
.auth-panel {
  width: 400px;
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 32px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}
.tabs {
  display: flex;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--color-border);
}
.tabs button {
  flex: 1;
  background: none;
  border: none;
  font-size: 16px;
  padding: 10px;
  cursor: pointer;
  color: var(--color-text-secondary);
  border-bottom: 2px solid transparent;
}
.tabs button.on {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  font-weight: 600;
}
.field {
  margin-bottom: 16px;
}
.field input {
  width: 100%;
  height: 40px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  padding: 0 12px;
  outline: none;
  font-size: 14px;
}
.field input:focus {
  border-color: var(--color-primary);
}
.err {
  color: var(--color-danger);
  font-size: 12px;
  margin: 4px 0 0;
  text-align: left;
}
.form-err {
  margin-bottom: 12px;
}
.submit {
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
.submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.forgot-link {
  display: block;
  text-align: center;
  margin-top: 16px;
  color: var(--color-primary);
  font-size: 13px;
  cursor: pointer;
}
.fp-title {
  margin: 0 0 20px;
  font-size: 16px;
}
.fp-tip {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 0 0 12px;
  line-height: 1.6;
}
</style>
