<template>
  <router-view v-slot="{ Component, route }">
    <transition name="fade-slide" mode="out-in">
      <component :is="Component" :key="route.path" />
    </transition>
  </router-view>
</template>

<script setup>
import { onMounted } from 'vue'
import { getConfigValue } from '@/api/systemConfig'
import { loadAndApplyGlobalTheme } from '@/utils/globalTheme'

// 加载并应用全局主题色
onMounted(async () => {
  await loadAndApplyGlobalTheme(getConfigValue)
})
</script>

<style>
/* ========== 全局重置 ========== */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  height: 100%;
  font-family: var(--font-family-base);
  overflow: hidden !important;
}

body.el-popup-parent--hidden {
  overflow: hidden !important;
}

#app {
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background-color: var(--color-bg-secondary);
}

/* ========== 页面过渡动画 ========== */
.fade-slide-enter-active {
  transition: opacity var(--transition-base),
              transform var(--transition-base);
  position: relative;
  z-index: var(--z-base);
}

.fade-slide-leave-active {
  transition: opacity var(--transition-fast),
              transform var(--transition-fast);
  position: relative;
  z-index: 0;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(12px) scale(0.98);
}

.fade-slide-enter-to {
  opacity: 1;
  transform: translateX(0) scale(1);
}

.fade-slide-leave-from {
  opacity: 1;
  transform: translateX(0) scale(1);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-12px) scale(0.98);
}

.fade-slide-enter-active .app-header,
.fade-slide-leave-active .app-header {
  z-index: var(--z-fixed) !important;
  position: fixed !important;
}
</style>
