<script setup lang="ts">
// FollowButton 关注按钮（06 §5，FR-FO04 三态幂等）：空间页与播放页 UP 主条复用。
// 游客 → 跳登录带回跳；本人 → 「编辑资料」跳个人中心；否则 +关注/已关注幂等调用，
// 成功 emit update(isFollowed, followerCount) 由父组件本地刷新（不重拉）。
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { followUser, unfollowUser } from '@/api'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/toast'

const props = defineProps<{
  userId: number
  /** 访问者是否本人（本人态显示「编辑资料」，FR-FO04） */
  isSelf: boolean
  isFollowed: boolean
}>()

const emit = defineEmits<{
  (e: 'update', isFollowed: boolean, followerCount: number): void
}>()

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const acting = ref(false)

async function onClick() {
  if (acting.value) return
  if (!store.isLoggedIn) {
    // 游客：跳登录带回跳（FR-FO01 EX-4）
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  acting.value = true
  try {
    const resp = props.isFollowed
      ? await unfollowUser(props.userId) // 已关注 → 直接取关无确认框（FR-FO02）
      : await followUser(props.userId)
    emit('update', resp.isFollowed, resp.followerCount) // 用返回最新值，不重拉（FR-FO03）
  } catch (e) {
    toast((e as Error).message || '操作失败')
  } finally {
    acting.value = false
  }
}

function goEdit() {
  router.push('/account/info') // 编辑资料并入个人中心（Phase 1.5）
}
</script>

<template>
  <!-- 本人态：编辑资料（跳个人中心） -->
  <button v-if="isSelf" class="btn outline" @click="goEdit">编辑资料</button>
  <!-- 关注三态 -->
  <button v-else-if="isFollowed" class="btn outline" :disabled="acting" @click="onClick">
    {{ acting ? '处理中…' : '已关注' }}
  </button>
  <button v-else class="btn solid" :disabled="acting" @click="onClick">
    {{ acting ? '处理中…' : '+ 关注' }}
  </button>
</template>

<style scoped>
.btn {
  height: 32px;
  padding: 0 20px;
  border-radius: 999px;
  font-size: 14px;
  cursor: pointer;
}
.btn.solid {
  background: var(--color-primary);
  color: #fff;
  border: none;
}
.btn.outline {
  background: var(--color-card);
  color: var(--color-text);
  border: 1px solid var(--color-border);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
