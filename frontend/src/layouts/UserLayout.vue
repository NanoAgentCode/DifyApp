<template>
  <el-container class="user-layout" :class="{ 'portal-mode': isPortalMode }">
    <el-header v-if="!isPortalMode" class="header">
      <div class="header-left">
        <h2>NanoAgent智能应用工作台</h2>
      </div>
      <div class="header-right">
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-icon><User /></el-icon>
            <span>{{ userInfo?.username || '用户' }}</span>
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="changePassword">修改密码</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-container :class="{ 'portal-container': isPortalMode }">
      <el-aside width="64px" class="aside portal-sidebar">
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
      <el-main class="main" :class="{ 'portal-main': isPortalMode }">
        <div class="main-content" :class="{ 'portal-content': isPortalMode }">
          <router-view />
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
import { User, ArrowDown, List, Folder, ChatLineRound, Clock, Document, DataAnalysis, Reading } from '@element-plus/icons-vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import HelpFloatingButton from '@/components/HelpFloatingButton.vue'
import HelpDialog from '@/components/HelpDialog.vue'
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
  } else if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      ElMessage.success('已退出登录')
      router.push('/login')
    }).catch(() => {
      // 取消操作
    })
  }
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

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--el-color-primary, #409EFF);
  color: white;
  padding: 0 20px;
  height: 60px;
  flex-shrink: 0;
  position: relative;
}

.header-left {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  margin: 0;
  font-size: 20px;
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
}


.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 5px;
  color: white;
  cursor: pointer;
  padding: 5px 10px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-info:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.aside {
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
  transition: width 0.3s ease;
  height: calc(100vh - 60px); /* 减去header高度 */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0; /* 防止侧边栏被压缩 */
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
  transition: margin-left 0.3s ease; /* 添加过渡效果 */
  flex: 1; /* 允许主内容区域自动调整 */
  min-width: 0; /* 允许主内容区域缩小 */
  margin-left: 64px !important; /* 为收缩的侧边栏留出空间 */
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
  }

  /* 小屏幕默认折叠侧边栏，但允许手动展开 */
  .aside {
    /* 移除 !important，允许手动展开时覆盖 */
  }

  .main {
    height: calc(100vh - 50px);
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

