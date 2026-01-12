<template>
  <div v-if="shouldShow" class="home-floating-button">
    <div class="split-button-container" tabindex="0">
      <!-- 左半部分：回到主页 -->
      <el-tooltip content="回到主页" placement="left" :show-after="300">
        <div
          class="split-button split-button-left"
          :class="{ 'btn-focused': isHomeFocused }"
          @click="handleGoToHome"
          @mouseenter="isHomeFocused = true"
          @mouseleave="isHomeFocused = false"
        >
          <el-icon :size="18"><HomeFilled /></el-icon>
        </div>
      </el-tooltip>
      
      <!-- 右半部分：用户手册智能问答 -->
      <el-tooltip content="用户手册智能问答" placement="left" :show-after="300">
        <div
          class="split-button split-button-right"
          :class="{ 'btn-focused': isHelpFocused }"
          @click="handleHelpClick"
          @mouseenter="isHelpFocused = true"
          @mouseleave="isHelpFocused = false"
        >
          <el-icon :size="18"><QuestionFilled /></el-icon>
        </div>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled, QuestionFilled } from '@element-plus/icons-vue'

const props = defineProps({
  onHelpClick: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['help-click'])

const route = useRoute()
const router = useRouter()

// 判断是否应该显示按钮（主页不显示）
const shouldShow = computed(() => {
  const path = route.path
  // 主页路径不显示
  return path !== '/user/chat' && path !== '/admin/chat'
})

// 按钮焦点状态
const isHomeFocused = ref(false)
const isHelpFocused = ref(false)

// 获取用户信息
const getUserInfo = () => {
  try {
    const userInfoStr = localStorage.getItem('userInfo')
    return userInfoStr ? JSON.parse(userInfoStr) : null
  } catch (e) {
    return null
  }
}

// 回到主页
const handleGoToHome = () => {
  const userInfo = getUserInfo()
  const isAdmin = userInfo?.role === 1
  router.push(isAdmin ? '/admin/chat' : '/user/chat')
}

// 用户手册智能问答
const handleHelpClick = () => {
  if (props.onHelpClick) {
    props.onHelpClick()
  } else {
    emit('help-click')
  }
}
</script>

<style scoped>
.home-floating-button {
  position: fixed;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1000;
}

.split-button-container {
  display: flex;
  flex-direction: column;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  border: 2px solid var(--el-color-primary);
  background-color: transparent;
  transform: translateX(50%);
}

.home-floating-button:hover .split-button-container,
.home-floating-button:focus-within .split-button-container {
  transform: translateX(0);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}

.split-button {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s ease;
  color: var(--el-color-primary);
  background-color: transparent;
  position: relative;
  width: 100%;
}

.split-button-left {
  border-bottom: 1px solid var(--el-color-primary);
}

.split-button-right {
  border-top: 1px solid var(--el-color-primary);
}

.split-button:hover,
.split-button.btn-focused {
  background-color: var(--el-color-primary);
  color: white;
}

.split-button-left:hover,
.split-button-left.btn-focused {
  border-bottom-color: rgba(255, 255, 255, 0.3);
}

.split-button-right:hover,
.split-button-right.btn-focused {
  border-top-color: rgba(255, 255, 255, 0.3);
}

@media (max-width: 768px) {
  .home-floating-button {
    right: 0;
  }
  
  .split-button-container {
    width: 48px;
    height: 48px;
    transform: translateX(50%);
  }
}
</style>

