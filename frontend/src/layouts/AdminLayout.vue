<template>
  <el-container class="admin-layout" :class="{ 'portal-mode': isPortalMode }">
    <AppHeader v-if="!isPortalMode" v-model="isHeaderCollapsed" @command="handleCommand" />
    <el-container :class="{ 'portal-container': isPortalMode }">
      <AppSidebar 
        type="admin" 
        :is-header-collapsed="isHeaderCollapsed"
        :is-portal-mode="isPortalMode"
      />
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

    <UserMemoryDialog v-model="showUserMemoryDialog" />
    
    <!-- 回到主页悬浮按钮（集成用户手册智能问答） -->
    <HomeFloatingButton @help-click="handleHelpButtonClick" />
    
    <!-- 用户手册配置对话框 -->
    <el-dialog
      v-model="showKBConfigDialog"
      title="配置用户手册智能问答"
      width="500px"
      @open="handleConfigDialogOpen"
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
      :show-config-button="isAdmin"
      @config="showKBConfigDialog = true"
    />
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import HomeFloatingButton from '@/components/HomeFloatingButton.vue'
import HelpDialog from '@/components/HelpDialog.vue'
import AppHeader from '@/components/AppHeader.vue'
import UserMemoryDialog from '@/components/UserMemoryDialog.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { getAvailableQAModels, getAvailableQAModelsForRAG } from '@/api/model'
import { getConfigValue, setOrUpdateConfig, getConfigsByGroup } from '@/api/systemConfig'
import { logger } from '@/utils/logger'
import { useMemoReminder } from '@/composables/useMemoReminder'
import { useUserStore } from '@/stores/user'

useMemoReminder()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 判断是否为门户模式
const isPortalMode = computed(() => {
  return route.path === '/admin/chat'
})

const userInfo = ref(null)
const isAdmin = computed(() => userInfo.value && userInfo.value.role === 1)
const showChangePasswordDialog = ref(false)
const showUserMemoryDialog = ref(false)
const showHelpDialog = ref(false)
const showKBConfigDialog = ref(false)
const knowledgeBaseList = ref([])
const selectedKnowledgeBaseId = ref(null)
const helpKnowledgeBaseId = ref(null)
const availableModels = ref([])
const selectedModelId = ref(null)
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

// 存储需要清理的资源
const headerCollapsedIntervalId = ref(null)
const handleStorageChangeRef = ref(null)

// 从数据库加载配置
const loadConfigFromDB = async () => {
  try {
    // 加载帮助配置组的所有配置
    const configs = await getConfigsByGroup('help')
    logger.debug('从数据库加载的配置，数量:', configs?.length || 0)
    
    // 查找知识库ID配置
    const kbConfig = configs.find(c => c.configKey === 'help.knowledgeBaseId')
    if (kbConfig && kbConfig.configValue) {
      const kbId = parseInt(kbConfig.configValue)
      if (!isNaN(kbId)) {
        helpKnowledgeBaseId.value = kbId
        selectedKnowledgeBaseId.value = kbId
      }
    }
    
    // 查找模型ID配置
    const modelConfig = configs.find(c => c.configKey === 'help.modelId')
    if (modelConfig && modelConfig.configValue) {
      const modelId = parseInt(modelConfig.configValue)
      if (!isNaN(modelId)) {
        helpModelId.value = modelId
        selectedModelId.value = modelId
      }
    }
  } catch (error) {
    logger.error('从数据库加载配置失败:', error)
    // 如果数据库加载失败，尝试从本地存储恢复（兼容旧数据）
    const savedKBId = localStorage.getItem('helpKnowledgeBaseId')
    if (savedKBId) {
      helpKnowledgeBaseId.value = parseInt(savedKBId)
      selectedKnowledgeBaseId.value = parseInt(savedKBId)
    }
    const savedModelId = localStorage.getItem('helpModelId')
    if (savedModelId) {
      helpModelId.value = parseInt(savedModelId)
      selectedModelId.value = parseInt(savedModelId)
    }
  }
}

// 从本地存储恢复收缩状态和知识库配置
onMounted(async () => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      userInfo.value = JSON.parse(userInfoStr)
    } catch (e) {
      logger.error('解析用户信息失败', e)
    }
  }
  
  // 加载知识库列表
  await loadKnowledgeBaseList()
  
  // 从数据库加载配置
  await loadConfigFromDB()
  
  // 加载模型列表
  await loadModelList()
  
  // 监听 localStorage 中 headerCollapsed 的变化（用于门户模式下同步状态）
  const handleStorageChange = (e) => {
    if (e.key === 'headerCollapsed') {
      isHeaderCollapsed.value = e.newValue === 'true'
    }
  }
  handleStorageChangeRef.value = handleStorageChange
  window.addEventListener('storage', handleStorageChange)
  
  // 使用定时器轮询检查 localStorage 变化（因为同源页面的 storage 事件可能不触发）
  const checkHeaderCollapsed = () => {
    const currentState = loadHeaderCollapsedState()
    if (currentState !== isHeaderCollapsed.value) {
      isHeaderCollapsed.value = currentState
    }
  }
  headerCollapsedIntervalId.value = setInterval(checkHeaderCollapsed, 100)
})

// 组件卸载时清理资源
onUnmounted(() => {
  if (handleStorageChangeRef.value) {
    window.removeEventListener('storage', handleStorageChangeRef.value)
    handleStorageChangeRef.value = null
  }
  if (headerCollapsedIntervalId.value) {
    clearInterval(headerCollapsedIntervalId.value)
    headerCollapsedIntervalId.value = null
  }
})

// 加载知识库列表
const loadKnowledgeBaseList = async () => {
  try {
    const response = await getKnowledgeBaseList({ page: 1, pageSize: 100 })
    logger.debug('知识库列表响应')
    
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
    
    logger.debug('知识库列表加载完成，数量:', knowledgeBaseList.value.length)
  } catch (error) {
    logger.error('加载知识库列表失败:', error)
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
    logger.error('加载模型列表失败:', error)
    ElMessage.error('加载模型列表失败：' + (error.message || '未知错误'))
    availableModels.value = []
  }
}

// 配置对话框打开时的处理
const handleConfigDialogOpen = async () => {
  // 加载模型列表
  await loadModelList()
}

// 保存知识库配置到数据库
const saveKBConfig = async () => {
  try {
    // 保存知识库ID配置
    if (selectedKnowledgeBaseId.value) {
      await setOrUpdateConfig({
        configKey: 'help.knowledgeBaseId',
        configValue: String(selectedKnowledgeBaseId.value),
        configGroup: 'help',
        configType: 'number',
        description: '用户手册智能问答绑定的知识库ID'
      })
      helpKnowledgeBaseId.value = selectedKnowledgeBaseId.value
    } else {
      // 如果清空了选择，删除配置（可选，也可以保留为空值）
      // 这里选择保留为空值，不删除配置
      await setOrUpdateConfig({
        configKey: 'help.knowledgeBaseId',
        configValue: '',
        configGroup: 'help',
        configType: 'number',
        description: '用户手册智能问答绑定的知识库ID'
      })
      helpKnowledgeBaseId.value = null
    }
    
    // 保存模型ID配置
    if (selectedModelId.value) {
      await setOrUpdateConfig({
        configKey: 'help.modelId',
        configValue: String(selectedModelId.value),
        configGroup: 'help',
        configType: 'number',
        description: '用户手册智能问答使用的模型ID'
      })
      helpModelId.value = selectedModelId.value
    } else {
      await setOrUpdateConfig({
        configKey: 'help.modelId',
        configValue: '',
        configGroup: 'help',
        configType: 'number',
        description: '用户手册智能问答使用的模型ID'
      })
      helpModelId.value = null
    }
    
    ElMessage.success('配置已保存到数据库')
    // 配置完成后，如果用户是通过帮助按钮触发的，自动打开帮助对话框
    showHelpDialog.value = true
    showKBConfigDialog.value = false
  } catch (error) {
    logger.error('保存配置失败:', error)
    ElMessage.error('保存配置失败：' + (error.message || '未知错误'))
  }
}


const handleMenuClick = (path) => {
  router.push(path).catch(err => {
    // 忽略重复导航错误
    if (err.name !== 'NavigationDuplicated') {
      logger.error('导航错误:', err)
    }
  })
}

const handleCommand = (command) => {
  if (command === 'changePassword') {
    showChangePasswordDialog.value = true
  } else if (command === 'memory') {
    showUserMemoryDialog.value = true
  }
  // logout 命令由 AppHeader 组件内部处理
}

const handlePasswordChangeSuccess = () => {
  showChangePasswordDialog.value = false
  userStore.logout()
  router.replace('/login')
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

.admin-layout.portal-mode {
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
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
  height: calc(100vh - 60px); /* 减去header高度 */
  display: flex;
  flex-direction: column;
  transition: margin-left 0.3s ease, margin-top 0.3s ease, height 0.3s ease; /* 添加过渡效果 */
  flex: 1; /* 允许主内容区域自动调整 */
  min-width: 0; /* 允许主内容区域缩小 */
  margin-left: 56px !important; /* 为收缩的侧边栏留出空间（AppSidebar宽度为56px） */
  margin-top: 60px !important; /* 为顶部导航栏留出空间 */
}

.main.main-header-collapsed {
  margin-top: 0 !important;
  height: 100vh;
}

.main.portal-main {
  height: 100vh;
  background: #f5f5f5;
  padding: 0;
  margin: 0;
  margin-left: 64px; /* 为收缩的侧边栏留出空间 */
  margin-top: 0 !important; /* 门户模式不需要顶部间距 */
}

.main-content {
  width: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.main-content.portal-content {
  padding: 0;
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
    padding: 12px;
    margin-top: 50px !important; /* 小屏幕header高度为50px */
  }
}

/* 超小屏幕适配 (768px及以下) */
@media (max-width: 768px) {
  .header-left h2 {
    font-size: 14px;
  }

  .main {
    padding: 8px;
  }
}
</style>

