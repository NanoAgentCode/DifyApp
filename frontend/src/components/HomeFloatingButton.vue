<template>
  <div v-if="shouldShow" class="home-floating-button">
    <el-tooltip content="回到主页" placement="left" :show-after="300">
      <el-button
        :type="isFocused ? 'primary' : 'default'"
        circle
        size="large"
        @click="handleGoToHome"
        @mouseenter="isFocused = true"
        @mouseleave="isFocused = false"
        @focus="isFocused = true"
        @blur="isFocused = false"
        class="floating-btn"
        :class="{ 'btn-focused': isFocused }"
      >
        <el-icon :size="20"><HomeFilled /></el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// 判断是否应该显示按钮（主页不显示）
const shouldShow = computed(() => {
  const path = route.path
  // 主页路径不显示
  return path !== '/user/chat' && path !== '/admin/chat'
})

// 按钮焦点状态
const isFocused = ref(false)

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
</script>

<style scoped>
.home-floating-button {
  position: fixed;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.floating-btn {
  width: 56px;
  height: 56px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
  border: 2px solid var(--el-color-primary);
  background-color: transparent;
  color: var(--el-color-primary);
}

.floating-btn:hover,
.floating-btn:focus,
.floating-btn.btn-focused {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  background-color: var(--el-color-primary);
  color: white;
  border-color: var(--el-color-primary);
}

@media (max-width: 768px) {
  .home-floating-button {
    right: 10px;
  }
  
  .floating-btn {
    width: 48px;
    height: 48px;
  }
}
</style>

