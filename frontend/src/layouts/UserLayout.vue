<template>
  <el-container class="user-layout" :class="{ 'portal-mode': isPortalMode }">
    <AppHeader v-if="!isPortalMode" v-model="isHeaderCollapsed" @command="handleCommand" />
    <el-container :class="{ 'portal-container': isPortalMode }">
      <el-aside width="64px" class="aside portal-sidebar" :class="{ 'aside-header-collapsed': isHeaderCollapsed && isPortalMode, 'aside-header-collapsed-non-portal': isHeaderCollapsed && !isPortalMode }">
        <el-menu
          :default-active="activeMenu"
          :collapse="true"
          router
          class="menu"
        >
          <el-tooltip content="智能问答" placement="right" :show-after="200">
            <el-menu-item index="/user/chat">
              <el-icon><ChatLineRound /></el-icon>
              <span>智能问答</span>
            </el-menu-item>
          </el-tooltip>
          <el-tooltip content="知识检索" placement="right" :show-after="200">
            <el-menu-item index="/user/kb-qa">
              <el-icon><Document /></el-icon>
              <span>知识检索</span>
            </el-menu-item>
          </el-tooltip>
          <el-tooltip content="知识管理" placement="right" :show-after="200">
            <el-menu-item index="/user/knowledge-base">
              <el-icon><Folder /></el-icon>
              <span>知识管理</span>
            </el-menu-item>
          </el-tooltip>
          <el-tooltip content="智能应用" placement="right" :show-after="200">
            <el-menu-item index="/user/apps">
              <el-icon><List /></el-icon>
              <span>智能应用</span>
            </el-menu-item>
          </el-tooltip>
          <el-tooltip content="文档解读" placement="right" :show-after="200">
            <el-menu-item index="/user/document-reader">
              <el-icon><Reading /></el-icon>
              <span>文档解读</span>
            </el-menu-item>
          </el-tooltip>
          <el-tooltip content="智能框图" placement="right" :show-after="200">
            <el-menu-item index="/user/ai-drawio">
              <el-icon><DataAnalysis /></el-icon>
              <span>智能框图</span>
            </el-menu-item>
          </el-tooltip>
          <el-tooltip content="会话历史" placement="right" :show-after="200">
            <el-menu-item index="/user/chat-history">
              <el-icon><Clock /></el-icon>
              <span>会话历史</span>
            </el-menu-item>
          </el-tooltip>
        </el-menu>
      </el-aside>
      <el-main class="main" :class="{ 'portal-main': isPortalMode, 'main-header-collapsed': isHeaderCollapsed && !isPortalMode }">
        <div class="main-content" :class="{ 'portal-content': isPortalMode }">
          <router-view v-slot="{ Component }">
            <transition name="fade-slide" mode="out-in">
              <component :is="Component" />
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
    
    <!-- 帮助对话框 -->
    <HelpDialog 
      v-model="showHelpDialog" 
      :knowledge-base-id="helpKnowledgeBaseId"
      :model-id="helpModelId"
    />
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { List, Folder, ChatLineRound, Clock, Document, DataAnalysis, Reading } from '@element-plus/icons-vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import HelpFloatingButton from '@/components/HelpFloatingButton.vue'
import HelpDialog from '@/components/HelpDialog.vue'
import AppHeader from '@/components/AppHeader.vue'
import { getConfigsByGroup } from '@/api/systemConfig'

const route = useRoute()
const router = useRouter()
const activeMenu = computed(() => {
  // 如果当前路径是 /user，默认选中智能问答
  if (route.path === '/user' || route.path === '/user/') {
    return '/user/chat'
  }
  return route.path
})

// 判断是否为门户模式
const isPortalMode = computed(() => {
  return route.path === '/user/chat' || route.path === '/admin/chat'
})

const userInfo = ref(null)
const showChangePasswordDialog = ref(false)
const showHelpDialog = ref(false)
const helpKnowledgeBaseId = ref(null)
const helpModelId = ref(null)
const isHeaderCollapsed = ref(false)

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


.aside {
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
  transition: width 0.3s ease, margin-top 0.3s ease;
  height: calc(100vh - 60px); /* 减去header高度 */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0; /* 防止侧边栏被压缩 */
  margin-top: 60px; /* 为顶部导航栏留出空间 */
}

.aside.aside-header-collapsed-non-portal {
  margin-top: 0 !important;
  height: 100vh;
}

.aside.portal-sidebar {
  position: fixed;
  left: 0;
  top: 60px;
  z-index: 100;
  height: calc(100vh - 60px);
  margin: 0 !important;
  padding: 0 !important;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  transition: top 0.3s ease, height 0.3s ease;
}

.aside.portal-sidebar.aside-header-collapsed {
  top: 0;
  height: 100vh;
}

.menu {
  border-right: none;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background: #fff;
  margin: 0 !important;
  border-radius: 0;
  box-shadow: none;
}

/* 收缩状态下图标居中 */
.menu :deep(.el-menu--collapse) {
  width: 100%;
  padding: 0 !important;
}

.menu :deep(.el-menu--collapse .el-menu-item) {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0 !important;
  margin: 0 !important;
  height: 48px;
}

.menu :deep(.el-menu--collapse .el-menu-item .el-icon) {
  margin: 0 !important;
  padding: 0 !important;
}

.menu :deep(.el-menu--collapse .el-sub-menu__title) {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0 !important;
  margin: 0 !important;
  height: 48px;
}

.menu :deep(.el-menu--collapse .el-sub-menu__title .el-icon) {
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
  margin-left: 64px !important; /* 为收缩的侧边栏留出空间 */
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
  margin-left: 64px !important; /* 为收缩的侧边栏留出空间 */
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

/* 路由切换过渡动画 - 优化版 */
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

  .aside {
    height: calc(100vh - 50px);
    margin-top: 50px; /* 小屏幕header高度为50px */
  }

  /* 小屏幕默认折叠侧边栏，但允许手动展开 */
  .aside {
    /* 移除 !important，允许手动展开时覆盖 */
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

