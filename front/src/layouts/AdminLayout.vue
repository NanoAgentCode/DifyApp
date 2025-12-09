<template>
  <el-container class="admin-layout">
    <el-header class="header">
      <div class="header-left">
        <el-button
          text
          @click="toggleCollapse"
          class="collapse-btn"
        >
          <el-icon><Fold v-if="!isCollapse" /><Expand v-else /></el-icon>
        </el-button>
        <h2>Dify应用管理平台</h2>
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
          <!-- 核心功能 -->
          <el-menu-item index="/admin/chat">
            <el-icon><ChatLineRound /></el-icon>
            <span>智能问答</span>
          </el-menu-item>
          <!-- 知识库相关 -->
          <el-menu-item v-if="isAdmin" index="/admin/kb-qa">
            <el-icon><Document /></el-icon>
            <span>知识库问答</span>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/admin/knowledge-base">
            <el-icon><Folder /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/apps">
            <el-icon><List /></el-icon>
            <span>应用列表</span>
          </el-menu-item>
          <!-- 工具 -->
          <el-menu-item v-if="isAdmin" index="/admin/text2sql">
            <el-icon><Search /></el-icon>
            <span>SQL 生成</span>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/admin/data-sources">
            <el-icon><Connection /></el-icon>
            <span>数据源管理</span>
          </el-menu-item>
          <!-- 系统管理 -->
          <el-menu-item v-if="isAdmin" index="/admin/models">
            <el-icon><Setting /></el-icon>
            <span>大模型管理</span>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/admin/vector-databases">
            <el-icon><Box /></el-icon>
            <span>向量数据库</span>
          </el-menu-item>
          <!-- 记录查看 -->
          <!-- 用户管理 -->
          <el-menu-item v-if="isAdmin" index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/admin/chat-history">
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
    <HelpFloatingButton @click="handleHelpButtonClick" />
    
    <!-- 知识库配置对话框 -->
    <el-dialog
      v-model="showKBConfigDialog"
      title="配置用户手册智能问答"
      width="500px"
    >
      <el-form>
        <el-form-item label="选择知识库">
          <el-select
            v-model="selectedKnowledgeBaseId"
            placeholder="请选择知识库（可选，不选择则使用普通问答）"
            style="width: 100%"
            filterable
            clearable
            @change="handleKBChange"
          >
            <el-option
              v-for="kb in knowledgeBaseList"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选择模型">
          <el-select
            v-model="selectedModelId"
            placeholder="请选择模型"
            style="width: 100%"
            filterable
            clearable
          >
            <el-option
              v-for="model in availableModels"
              :key="model.id"
              :label="model.name"
              :value="model.id"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>{{ model.name }}</span>
                <el-tag v-if="model.isDefault" type="primary" size="small">默认</el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showKBConfigDialog = false">取消</el-button>
        <el-button type="primary" @click="saveKBConfig">保存</el-button>
      </template>
    </el-dialog>
    
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
import { List, User, ArrowDown, Folder, ChatLineRound, Fold, Expand, Clock, Setting, Document, Box, Connection, Search } from '@element-plus/icons-vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import HelpFloatingButton from '@/components/HelpFloatingButton.vue'
import HelpDialog from '@/components/HelpDialog.vue'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { getAvailableQAModels, getAvailableQAModelsForRAG } from '@/api/model'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => {
  // 如果当前路径是 /admin，默认选中智能问答
  if (route.path === '/admin' || route.path === '/admin/') {
    return '/admin/chat'
  }
  return route.path
})
const userInfo = ref(null)
const isAdmin = computed(() => userInfo.value && userInfo.value.role === 1)
const showChangePasswordDialog = ref(false)
const showHelpDialog = ref(false)
const showKBConfigDialog = ref(false)
const knowledgeBaseList = ref([])
const selectedKnowledgeBaseId = ref(null)
const helpKnowledgeBaseId = ref(null)
const availableModels = ref([])
const selectedModelId = ref(null)
const helpModelId = ref(null)

// 导航菜单收缩状态
const isCollapse = ref(false)

// 切换收缩状态
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
  // 保存状态到本地存储
  localStorage.setItem('adminMenuCollapse', String(isCollapse.value))
}

// 从本地存储恢复收缩状态和知识库配置
onMounted(async () => {
  const savedCollapse = localStorage.getItem('adminMenuCollapse')
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
  
  // 加载知识库列表
  await loadKnowledgeBaseList()
  
  // 从本地存储恢复知识库配置
  const savedKBId = localStorage.getItem('helpKnowledgeBaseId')
  if (savedKBId) {
    helpKnowledgeBaseId.value = parseInt(savedKBId)
    selectedKnowledgeBaseId.value = parseInt(savedKBId)
  }
  
  // 从本地存储恢复模型配置
  const savedModelId = localStorage.getItem('helpModelId')
  if (savedModelId) {
    helpModelId.value = parseInt(savedModelId)
    selectedModelId.value = parseInt(savedModelId)
  }
  
  // 加载模型列表
  await loadModelList()
})

// 加载知识库列表
const loadKnowledgeBaseList = async () => {
  try {
    const response = await getKnowledgeBaseList({ page: 1, pageSize: 100 })
    console.log('知识库列表响应:', response)
    
    // 处理不同的响应格式
    // 根据日志，响应格式是：{content: Array(11), total: 11, page: 1, pageSize: 100, totalPages: 1}
    // request.js 的响应拦截器已经返回了 response.data，所以这里直接访问 response
    if (response && response.content && Array.isArray(response.content)) {
      // 分页响应格式：{ content: [...], total: ... }
      knowledgeBaseList.value = response.content
    } else if (response && response.list && Array.isArray(response.list)) {
      // 分页响应格式：{ list: [...], total: ... }
      knowledgeBaseList.value = response.list
    } else if (Array.isArray(response)) {
      // 直接返回数组的情况
      knowledgeBaseList.value = response
    } else if (response && response.data) {
      const data = response.data
      if (data.content && Array.isArray(data.content)) {
        knowledgeBaseList.value = data.content
      } else if (data.list && Array.isArray(data.list)) {
        knowledgeBaseList.value = data.list
      } else if (Array.isArray(data)) {
        knowledgeBaseList.value = data
      }
    }
    
    console.log('解析后的知识库列表:', knowledgeBaseList.value)
    console.log('知识库列表数量:', knowledgeBaseList.value.length)
  } catch (error) {
    console.error('加载知识库列表失败:', error)
    ElMessage.error('加载知识库列表失败：' + (error.message || '未知错误'))
  }
}

// 处理知识库变更
const handleKBChange = async () => {
  // 当知识库变更时，重新加载对应的模型列表
  await loadModelList()
}

// 加载模型列表
const loadModelList = async () => {
  try {
    // 如果选择了知识库，使用RAG模型；否则使用普通问答模型
    const api = selectedKnowledgeBaseId.value ? getAvailableQAModelsForRAG : getAvailableQAModels
    const response = await api()
    
    // request拦截器已经提取了response.data，所以response可能是数组或对象
    const data = Array.isArray(response) ? response : (response?.data || [])
    availableModels.value = data || []
    
    // 如果当前选中的模型不在新列表中，清空选择
    if (selectedModelId.value && !availableModels.value.find(m => m.id === selectedModelId.value)) {
      selectedModelId.value = null
    }
    
    // 如果没有选中模型，默认选择第一个或默认模型
    if (!selectedModelId.value && availableModels.value.length > 0) {
      const defaultModel = availableModels.value.find(m => m.isDefault) || availableModels.value[0]
      selectedModelId.value = defaultModel.id
    }
  } catch (error) {
    console.error('加载模型列表失败:', error)
    ElMessage.error('加载模型列表失败：' + (error.message || '未知错误'))
    availableModels.value = []
  }
}

// 保存知识库配置
const saveKBConfig = () => {
  if (selectedKnowledgeBaseId.value) {
    helpKnowledgeBaseId.value = selectedKnowledgeBaseId.value
    localStorage.setItem('helpKnowledgeBaseId', String(selectedKnowledgeBaseId.value))
  } else {
    helpKnowledgeBaseId.value = null
    localStorage.removeItem('helpKnowledgeBaseId')
  }
  
  // 保存模型配置
  if (selectedModelId.value) {
    helpModelId.value = selectedModelId.value
    localStorage.setItem('helpModelId', String(selectedModelId.value))
  } else {
    helpModelId.value = null
    localStorage.removeItem('helpModelId')
  }
  
  ElMessage.success('配置已保存')
  // 配置完成后，如果用户是通过帮助按钮触发的，自动打开帮助对话框
  showHelpDialog.value = true
  showKBConfigDialog.value = false
}


const handleMenuClick = (path) => {
  router.push(path).catch(err => {
    // 忽略重复导航错误
    if (err.name !== 'NavigationDuplicated') {
      console.error('导航错误:', err)
    }
  })
}

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

// 处理帮助按钮点击
const handleHelpButtonClick = () => {
  // 如果还没有配置知识库或模型，先显示配置对话框
  if (!helpKnowledgeBaseId.value || !helpModelId.value) {
    // 打开配置对话框前，确保模型列表已加载
    if (availableModels.value.length === 0) {
      loadModelList()
    }
    showKBConfigDialog.value = true
  } else {
    showHelpDialog.value = true
  }
}
</script>


<style scoped>
.admin-layout {
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
  flex-shrink: 0;
  height: 60px;
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
  gap: 10px;
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
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.3s;
}

.menu {
  border-right: none;
  height: 100%;
}

.main {
  background: #f5f7fa;
  padding: 20px;
  overflow: hidden;
  height: calc(100vh - 60px); /* 减去header高度 */
  display: flex;
  flex-direction: column;
}

.main-content {
  height: 100%;
  width: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>

