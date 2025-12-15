<template>
  <el-container class="user-layout">
    <el-header class="header">
      <div class="header-left">
        <el-button
          text
          @click="toggleCollapse"
          class="collapse-btn"
        >
          <el-icon><Fold v-if="!isCollapse" /><Expand v-else /></el-icon>
        </el-button>
        <h2>智能应用工作台</h2>
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
    <el-container>
      <el-aside :width="isCollapse ? '64px' : '200px'" class="aside">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          router
          class="menu"
        >
          <el-menu-item index="/user/chat">
            <el-icon><ChatLineRound /></el-icon>
            <span>智能问答</span>
          </el-menu-item>
          <el-menu-item index="/user/kb-qa">
            <el-icon><Document /></el-icon>
            <span>知识库问答</span>
          </el-menu-item>
          <el-menu-item index="/user/knowledge-base">
            <el-icon><Folder /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
          <el-menu-item index="/user/apps">
            <el-icon><List /></el-icon>
            <span>智能应用</span>
          </el-menu-item>
          <el-menu-item index="/user/ai-drawio">
            <el-icon><DataAnalysis /></el-icon>
            <span>智能框图</span>
          </el-menu-item>
          <el-menu-item index="/user/chat-history">
            <el-icon><Clock /></el-icon>
            <span>会话历史</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="main">
        <div class="main-content">
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
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, ArrowDown, List, Folder, ChatLineRound, Fold, Expand, Clock, Document, DataAnalysis } from '@element-plus/icons-vue'
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
const userInfo = ref(null)
const showChangePasswordDialog = ref(false)
const showHelpDialog = ref(false)
const helpKnowledgeBaseId = ref(null)
const helpModelId = ref(null)

// 导航菜单收缩状态
const isCollapse = ref(false)

// 侧边栏正常显示的最小宽度（侧边栏200px + 主内容最小宽度600px）
const MIN_WIDTH_FOR_SIDEBAR = 1024

// 检查窗口大小并自动调整侧边栏
const checkAndAutoCollapse = () => {
  const windowWidth = window.innerWidth
  // 如果窗口宽度小于阈值，且用户没有手动展开，则自动折叠侧边栏
  if (windowWidth < MIN_WIDTH_FOR_SIDEBAR) {
    const manualExpand = localStorage.getItem('userMenuManualExpand') === 'true'
    if (!isCollapse.value && !manualExpand) {
      isCollapse.value = true
      // 不保存到localStorage，因为这是自动行为
    }
  } else {
    // 窗口宽度足够时，清除手动展开标志
    localStorage.removeItem('userMenuManualExpand')
  }
}

// 切换收缩状态
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
  // 手动切换时，保存用户的选择到本地存储
  // 无论窗口大小，都保存用户的手动选择
  localStorage.setItem('userMenuCollapse', String(isCollapse.value))
  // 如果用户手动展开，设置一个标志，暂时忽略自动折叠
  if (!isCollapse.value) {
    localStorage.setItem('userMenuManualExpand', 'true')
  } else {
    localStorage.removeItem('userMenuManualExpand')
  }
}

// 窗口大小变化监听器
const handleResize = () => {
  checkAndAutoCollapse()
}

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
  // 先从本地存储恢复用户的手动选择
  const savedCollapse = localStorage.getItem('userMenuCollapse')
  if (savedCollapse !== null) {
    isCollapse.value = savedCollapse === 'true'
  }
  
  // 然后检查窗口大小，决定是否需要自动折叠
  // 如果用户手动展开且窗口宽度足够，则保持展开状态
  checkAndAutoCollapse()
  
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
  
  // 添加窗口大小变化监听
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  // 移除窗口大小变化监听
  window.removeEventListener('resize', handleResize)
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

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--el-color-primary, #409EFF);
  color: white;
  padding: 0 20px;
  height: 60px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  margin: 0;
  font-size: 20px;
}

.collapse-btn {
  color: white;
  font-size: 18px;
  padding: 8px;
}

.collapse-btn:hover {
  background-color: rgba(255, 255, 255, 0.1);
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

.menu {
  border-right: none;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background: #fff;
  margin: 8px;
  border-radius: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
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
}

.main-content {
  flex: 1;
  overflow: hidden;
  padding: 20px;
  display: flex;
  flex-direction: column;
  min-height: 0;
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

  .collapse-btn {
    font-size: 16px;
    padding: 6px;
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

