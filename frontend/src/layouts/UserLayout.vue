<template>
  <el-container class="user-layout" :class="{ 'portal-mode': isPortalMode }">
    <AppHeader v-if="!isPortalMode" v-model="isHeaderCollapsed" @command="handleCommand" />
    <el-container :class="{ 'portal-container': isPortalMode }">
      <el-main class="main" :class="{ 'portal-main': isPortalMode, 'main-header-collapsed': isHeaderCollapsed && !isPortalMode }">
        <div class="main-content" :class="{ 'portal-content': isPortalMode }">
          <router-view v-slot="{ Component, route }">
            <transition name="fade-slide" mode="out-in">
              <component :is="Component" :key="route.path" />
            </transition>
          </router-view>
        </div>
      </el-main>
    </el-container>
    
    <ChangePasswordDialog
      v-model="showChangePasswordDialog"
      @success="handlePasswordChangeSuccess"
    />
    
    <!-- 帮助悬浮按钮 -->
    <HelpFloatingButton @click="showHelpDialog = true" />
    
    <!-- 回到主页悬浮按钮 -->
    <HomeFloatingButton />
    
    <!-- 帮助对话框 -->
    <HelpDialog 
      v-model="showHelpDialog" 
      :knowledge-base-id="helpKnowledgeBaseId"
      :model-id="helpModelId"
    />
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import HelpFloatingButton from '@/components/HelpFloatingButton.vue'
import HomeFloatingButton from '@/components/HomeFloatingButton.vue'
import HelpDialog from '@/components/HelpDialog.vue'
import AppHeader from '@/components/AppHeader.vue'
import { getConfigsByGroup } from '@/api/systemConfig'

const route = useRoute()
const router = useRouter()

// 判断是否为门户模式
const isPortalMode = computed(() => {
  return route.path === '/user/chat' || route.path === '/admin/chat'
})

const userInfo = ref(null)
const showChangePasswordDialog = ref(false)
const showHelpDialog = ref(false)
const helpKnowledgeBaseId = ref(null)
const helpModelId = ref(null)

// 从 localStorage 读取初始状态
const loadHeaderCollapsedState = () => {
  const savedState = localStorage.getItem('headerCollapsed')
  if (savedState !== null) {
    return savedState === 'true'
  }
  return false // 默认展开
}

const isHeaderCollapsed = ref(loadHeaderCollapsedState())

// 从数据库加载配置
const loadConfigFromDB = async () => {
  try {
    // 加载帮助配置组的所有配置
    const configs = await getConfigsByGroup('help')
    console.log('从数据库加载的配置:', configs)
    
    // 查找知识库ID配置
    const kbConfig = configs.find(c => c.configKey === 'help.knowledgeBaseId')
    if (kbConfig && kbConfig.configValue) {
      const kbId = parseInt(kbConfig.configValue)
      if (!isNaN(kbId)) {
        helpKnowledgeBaseId.value = kbId
      }
    }
    
    // 查找模型ID配置
    const modelConfig = configs.find(c => c.configKey === 'help.modelId')
    if (modelConfig && modelConfig.configValue) {
      const modelId = parseInt(modelConfig.configValue)
      if (!isNaN(modelId)) {
        helpModelId.value = modelId
      }
    }
  } catch (error) {
    console.error('从数据库加载配置失败:', error)
    // 如果数据库加载失败，尝试从本地存储恢复（兼容旧数据）
    const savedKBId = localStorage.getItem('helpKnowledgeBaseId')
    if (savedKBId) {
      helpKnowledgeBaseId.value = parseInt(savedKBId)
    }
    const savedModelId = localStorage.getItem('helpModelId')
    if (savedModelId) {
      helpModelId.value = parseInt(savedModelId)
    }
  }
}

onMounted(async () => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      userInfo.value = JSON.parse(userInfoStr)
    } catch (e) {
      console.error('解析用户信息失败', e)
    }
  }
  
  // 从数据库加载配置
  await loadConfigFromDB()
  
  // 监听 localStorage 中 headerCollapsed 的变化（用于门户模式下同步状态）
  const handleStorageChange = (e) => {
    if (e.key === 'headerCollapsed') {
      isHeaderCollapsed.value = e.newValue === 'true'
    }
  }
  window.addEventListener('storage', handleStorageChange)
  
  // 使用定时器轮询检查 localStorage 变化（因为同源页面的 storage 事件可能不触发）
  const checkHeaderCollapsed = () => {
    const currentState = loadHeaderCollapsedState()
    if (currentState !== isHeaderCollapsed.value) {
      isHeaderCollapsed.value = currentState
    }
  }
  const intervalId = setInterval(checkHeaderCollapsed, 100)
  
  // 清理函数
  onUnmounted(() => {
    window.removeEventListener('storage', handleStorageChange)
    clearInterval(intervalId)
  })
})

const handleCommand = (command) => {
  if (command === 'changePassword') {
    showChangePasswordDialog.value = true
  }
  // logout 命令由 AppHeader 组件内部处理
}

const handlePasswordChangeSuccess = () => {
  // 密码修改成功后，可以选择退出登录或刷新页面
  ElMessageBox.confirm('密码已修改，需要重新登录', '提示', {
    confirmButtonText: '重新登录',
    cancelButtonText: '稍后',
    type: 'success'
  }).then(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    router.push('/login')
  }).catch(() => {
    // 用户选择稍后
  })
}

</script>

<style scoped>
.user-layout {
  height: 100vh;
  overflow: hidden;
}

.user-layout.portal-mode {
  height: 100vh;
  overflow: visible;
}

.portal-container {
  margin: 0 !important;
  padding: 0 !important;
}

.portal-container :deep(.el-container) {
  margin: 0 !important;
  padding: 0 !important;
}

.main {
  background: #f5f7fa;
  padding: 0;
  overflow: hidden;
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  transition: margin-left 0.3s ease, margin-top 0.3s ease, height 0.3s ease; /* 添加过渡效果 */
  flex: 1; /* 允许主内容区域自动调整 */
  min-width: 0; /* 允许主内容区域缩小 */
  margin-left: 0 !important; /* 用户端不显示侧边栏 */
  margin-top: 60px !important; /* 为顶部导航栏留出空间 */
}

.main.main-header-collapsed {
  margin-top: 0 !important;
  height: 100vh;
}

.main.portal-main {
  height: 100vh;
  background: #f5f5f5;
  margin: 0 !important;
  margin-left: 0 !important; /* 用户端不显示侧边栏 */
  padding: 0 !important;
}

.main.portal-main :deep(.el-main) {
  padding: 0 !important;
  margin: 0 !important;
}

.main-content {
  flex: 1;
  overflow: hidden;
  padding: 20px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.main-content.portal-content {
  padding: 0;
  margin: 0;
  overflow: visible;
}

/* 路由切换过渡动画使用全局样式（定义在 App.vue 中） */

/* 小屏幕适配 (1024x768及以下) */
@media (max-width: 1024px) {
  .header {
    padding: 0 12px;
    height: 50px;
  }

  .header-left h2 {
    font-size: 16px;
  }

  .user-info {
    font-size: 14px;
    padding: 4px 8px;
  }

  .main {
    height: calc(100vh - 50px);
    margin-top: 50px !important; /* 小屏幕header高度为50px */
  }

  .main-content {
    padding: 12px;
  }
}

/* 超小屏幕适配 (768px及以下) */
@media (max-width: 768px) {
  .header-left h2 {
    font-size: 14px;
  }

  .main-content {
    padding: 8px;
  }
}

</style>

