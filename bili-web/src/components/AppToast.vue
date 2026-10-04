<script setup lang="ts">
// 全局轻提示容器（06 §6）：消费 toastState
import { toastState } from '@/utils/toast'
</script>

<template>
  <div class="toast-wrap">
    <transition-group name="toast">
      <div v-for="t in toastState.items" :key="t.id" class="toast-item">{{ t.text }}</div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-wrap {
  position: fixed;
  top: 72px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}
.toast-item {
  background: rgba(24, 25, 28, 0.88);
  color: #fff;
  padding: 10px 20px;
  border-radius: 6px;
  font-size: 14px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
