<script setup lang="ts">
// 404 页（PG-06 / 06 §3.6）：复用 PlaceholderPage 布局
</script>

<template>
  <div class="placeholder">
    <p class="ph-text">404 - 页面不存在</p>
    <router-link to="/" class="ph-btn">返回首页</router-link>
  </div>
</template>

<style scoped>
.placeholder {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-block);
}
.ph-text {
  font-size: 18px;
  color: var(--color-text-secondary);
}
.ph-btn {
  padding: 8px 24px;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
  border-radius: var(--radius-card);
}
.ph-btn:hover {
  background: var(--color-primary);
  color: #fff;
}
</style>
