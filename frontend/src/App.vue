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
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html {
  height: 100%;
  overflow: hidden !important;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  height: 100%;
  overflow: hidden !important;
}

/* 确保对话框打开时 body 不滚动 */
body.el-popup-parent--hidden {
  overflow: hidden !important;
}

#app {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

/* 全局滚动条样式 - 统一美化内容区域的滚动条 */
/* Webkit 浏览器 (Chrome, Safari, Edge) - 应用到所有元素 */
*::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

*::-webkit-scrollbar-track {
  background: transparent;
  border-radius: 4px;
}

*::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.2);
  border-radius: 4px;
  transition: background 0.3s;
}

*::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.3);
}

/* Firefox - 应用到所有元素 */
* {
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.2) transparent;
}

/* 小屏幕全局适配 (1024x768及以下) */
@media (max-width: 1024px) {
  /* 减小表格字体和间距 */
  :deep(.el-table) {
    font-size: 13px;
  }

  :deep(.el-table th),
  :deep(.el-table td) {
    padding: 8px 6px;
  }

  /* 减小卡片内边距 */
  :deep(.el-card__body) {
    padding: 12px;
  }

  /* 减小按钮和输入框尺寸 */
  :deep(.el-button) {
    padding: 8px 12px;
    font-size: 13px;
  }

  :deep(.el-input__inner),
  :deep(.el-textarea__inner) {
    font-size: 13px;
  }

  /* 减小对话框内边距 */
  :deep(.el-dialog__body) {
    padding: 16px;
  }

  /* 减小表单标签和输入框间距 */
  :deep(.el-form-item__label) {
    font-size: 13px;
    padding-bottom: 4px;
  }

  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}

/* 超小屏幕全局适配 (768px及以下) */
@media (max-width: 768px) {
  /* 进一步减小字体 */
  :deep(.el-table) {
    font-size: 12px;
  }

  :deep(.el-table th),
  :deep(.el-table td) {
    padding: 6px 4px;
  }

  :deep(.el-card__body) {
    padding: 8px;
  }

  :deep(.el-button) {
    padding: 6px 10px;
    font-size: 12px;
  }

  :deep(.el-dialog__body) {
    padding: 12px;
  }

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  /* 对话框在小屏幕上全屏显示 */
  :deep(.el-dialog) {
    width: 95% !important;
    margin: 5vh auto !important;
  }
}

/* ==================== 全局页面过渡动画 ==================== */
/* 统一的页面切入切出过渡动画 - 用于所有路由切换 */
.fade-slide-enter-active {
  transition: opacity 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  z-index: 1;
  will-change: opacity, transform; /* 硬件加速 */
}

.fade-slide-leave-active {
  transition: opacity 0.2s cubic-bezier(0.4, 0, 1, 1),
              transform 0.2s cubic-bezier(0.4, 0, 1, 1);
  position: relative;
  z-index: 0;
  will-change: opacity, transform; /* 硬件加速 */
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(15px) scale(0.98); /* 轻微缩放效果 */
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
  transform: translateX(-15px) scale(0.98); /* 轻微缩放效果 */
}

/* 确保路由切换动画不会影响导航栏 */
.fade-slide-enter-active .app-header,
.fade-slide-leave-active .app-header {
  z-index: 1000 !important;
  position: fixed !important;
}
</style>

