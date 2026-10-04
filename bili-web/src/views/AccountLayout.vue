<script setup lang="ts">
// PG-07 个人中心骨架（06 §3.7）：页面标题 + 左侧菜单 + 右内容区（B 站个人中心同构布局）
import { useRoute } from 'vue-router'

const route = useRoute()

// 四子页菜单（06 §3.7 区块清单：B 站子集，其余菜单项无对应功能不建）
const menus = [
  { label: '我的信息', to: '/account/info' },
  { label: '我的头像', to: '/account/avatar' },
  { label: '我的背景', to: '/account/banner' },
  { label: '我的硬币', to: '/account/coin' },
]

// 当前项高亮：按 path 精确比对（子路由不嵌套，精确匹配即可）
function isActive(to: string): boolean {
  return route.path === to
}
</script>

<template>
  <div class="account-page container">
    <h1 class="page-title">个人中心</h1>
    <div class="account-body">
      <!-- 左侧菜单（200px） -->
      <aside class="menu">
        <router-link
          v-for="m in menus"
          :key="m.to"
          :to="m.to"
          class="menu-item"
          :class="{ on: isActive(m.to) }"
        >{{ m.label }}</router-link>
      </aside>
      <!-- 右内容区：白卡片，子页渲染于此 -->
      <div class="content-card">
        <router-view />
      </div>
    </div>
  </div>
</template>

<style scoped>
.container {
  max-width: var(--width-container);
  margin: 0 auto;
  padding: var(--space-block);
}
.page-title {
  margin: 0 0 var(--space-block);
  font-size: 22px;
}
.account-body {
  display: flex;
  gap: var(--space-block);
  align-items: flex-start;
}
.menu {
  width: 200px;
  flex-shrink: 0;
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.menu-item {
  padding: 12px 16px;
  border-radius: 6px;
  font-size: 14px;
  color: var(--color-text);
}
.menu-item:hover {
  color: var(--color-primary);
}
.menu-item.on {
  color: var(--color-primary);
  background: var(--color-primary-light);
  font-weight: 600;
}
.content-card {
  flex: 1;
  min-width: 0;
  background: var(--color-card);
  border-radius: var(--radius-card);
  padding: 32px;
}
</style>
