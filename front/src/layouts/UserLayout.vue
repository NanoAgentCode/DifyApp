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
        <h2>Dify应用平台</h2>
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
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, ArrowDown, List, Folder, ChatLineRound, Fold, Expand, Clock, Document } from '@element-plus/icons-vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import HelpFloatingButton from '@/components/HelpFloatingButton.vue'
import HelpDialog from '@/components/HelpDialog.vue'

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

// 切换收缩状态
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
  // 保存状态到本地存储
  localStorage.setItem('userMenuCollapse', String(isCollapse.value))
}

// 从本地存储恢复收缩状态和知识库配置
onMounted(() => {
  const savedCollapse = localStorage.getItem('userMenuCollapse')
  if (savedCollapse !== null) {
    isCollapse.value = savedCollapse === 'true'
  }
  
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      userInfo.value = JSON.parse(userInfoStr)
    } catch (e) {
      console.error('解析用户信息失败', e)
    }
  }
  
  // 从本地存储读取知识库配置（由管理端配置）
  const savedKBId = localStorage.getItem('helpKnowledgeBaseId')
  if (savedKBId) {
    helpKnowledgeBaseId.value = parseInt(savedKBId)
  }
  
  // 从本地存储读取模型配置（由管理端配置）
  const savedModelId = localStorage.getItem('helpModelId')
  if (savedModelId) {
    helpModelId.value = parseInt(savedModelId)
  }
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
  background: #409eff;
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
  background: #fff;
  border-right: 1px solid #e4e7ed;
  transition: width 0.3s;
}

.menu {
  border-right: none;
  height: 100%;
}

.main {
  background: #f5f7fa;
  padding: 0;
  overflow: hidden;
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  overflow: hidden;
  padding: 20px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>

