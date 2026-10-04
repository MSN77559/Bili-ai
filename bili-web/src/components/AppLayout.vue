<script setup lang="ts">
// 页面骨架（06 §5）：NavBar + 内容区 + 全局 toast 容器
import NavBar from '@/components/NavBar.vue'
import AppToast from '@/components/AppToast.vue'
</script>

<template>
  <div class="layout">
    <NavBar />
    <main class="layout-main">
      <router-view />
    </main>
    <AppToast />
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.layout-main {
  flex: 1;
}
</style>
