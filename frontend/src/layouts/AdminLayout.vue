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
          <!-- 核心功能 -->
          <el-tooltip v-if="isCollapse" content="智能问答" placement="right" :show-after="200">
            <el-menu-item index="/admin/chat">
              <el-icon><ChatLineRound /></el-icon>
              <span>智能问答</span>
            </el-menu-item>
          </el-tooltip>
          <el-menu-item v-else index="/admin/chat">
            <el-icon><ChatLineRound /></el-icon>
            <span>智能问答</span>
          </el-menu-item>
          <!-- 知识库相关 -->
          <el-tooltip v-if="isAdmin && isCollapse" content="知识问答" placement="right" :show-after="200">
            <el-menu-item index="/admin/kb-qa">
              <el-icon><Document /></el-icon>
              <span>知识问答</span>
            </el-menu-item>
          </el-tooltip>
          <el-menu-item v-else-if="isAdmin" index="/admin/kb-qa">
            <el-icon><Document /></el-icon>
            <span>知识问答</span>
          </el-menu-item>
          <el-tooltip v-if="isAdmin && isCollapse" content="知识管理" placement="right" :show-after="200">
            <el-menu-item index="/admin/knowledge-base">
              <el-icon><Folder /></el-icon>
              <span>知识管理</span>
            </el-menu-item>
          </el-tooltip>
          <el-menu-item v-else-if="isAdmin" index="/admin/knowledge-base">
            <el-icon><Folder /></el-icon>
            <span>知识管理</span>
          </el-menu-item>
          <el-tooltip v-if="isCollapse" content="应用列表" placement="right" :show-after="200">
            <el-menu-item index="/admin/apps">
              <el-icon><List /></el-icon>
              <span>应用列表</span>
            </el-menu-item>
          </el-tooltip>
          <el-menu-item v-else index="/admin/apps">
            <el-icon><List /></el-icon>
            <span>应用列表</span>
          </el-menu-item>
          <!-- 工具 -->
          <el-tooltip v-if="isAdmin && isCollapse" content="高级功能" placement="right" :show-after="200">
            <el-menu-item index="/admin/text2sql">
              <el-icon><Search /></el-icon>
              <span>高级功能</span>
            </el-menu-item>
          </el-tooltip>
          <el-menu-item v-else-if="isAdmin" index="/admin/text2sql">
            <el-icon><Search /></el-icon>
            <span>高级功能</span>
          </el-menu-item>
          <!-- 系统管理 -->
          <el-tooltip v-if="isAdmin && isCollapse" content="LLM管理" placement="right" :show-after="200">
            <el-menu-item index="/admin/models">
              <el-icon><Setting /></el-icon>
              <span>LLM管理</span>
            </el-menu-item>
          </el-tooltip>
          <el-menu-item v-else-if="isAdmin" index="/admin/models">
            <el-icon><Setting /></el-icon>
            <span>LLM管理</span>
          </el-menu-item>
          <!-- 记录查看 -->
          <!-- 用户管理 -->
          <el-tooltip v-if="isAdmin && isCollapse" content="用户管理" placement="right" :show-after="200">
            <el-menu-item index="/admin/users">
              <el-icon><User /></el-icon>
              <span>用户管理</span>
            </el-menu-item>
          </el-tooltip>
          <el-menu-item v-else-if="isAdmin" index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-tooltip v-if="isAdmin && isCollapse" content="会话历史" placement="right" :show-after="200">
            <el-menu-item index="/admin/chat-history">
              <el-icon><Clock /></el-icon>
              <span>会话历史</span>
            </el-menu-item>
          </el-tooltip>
          <el-menu-item v-else-if="isAdmin" index="/admin/chat-history">
            <el-icon><Clock /></el-icon>
            <span>会话历史</span>
          </el-menu-item>
          <el-tooltip v-if="isAdmin && isCollapse" content="系统配置" placement="right" :show-after="200">
            <el-menu-item index="/admin/system-config">
              <el-icon><Tools /></el-icon>
              <span>系统配置</span>
            </el-menu-item>
          </el-tooltip>
          <el-menu-item v-else-if="isAdmin" index="/admin/system-config">
            <el-icon><Tools /></el-icon>
            <span>系统配置</span>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { List, User, ArrowDown, Folder, ChatLineRound, Fold, Expand, Clock, Setting, Document, Box, Connection, Search, Tools, DataAnalysis, Edit } from '@element-plus/icons-vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import HelpFloatingButton from '@/components/HelpFloatingButton.vue'
import HelpDialog from '@/components/HelpDialog.vue'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { getAvailableQAModels, getAvailableQAModelsForRAG } from '@/api/model'
import { getConfigValue, setOrUpdateConfig, getConfigsByGroup } from '@/api/systemConfig'

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

// 侧边栏正常显示的最小宽度（侧边栏200px + 主内容最小宽度600px）
const MIN_WIDTH_FOR_SIDEBAR = 1024

// 检查窗口大小并自动调整侧边栏
const checkAndAutoCollapse = () => {
  const windowWidth = window.innerWidth
  // 如果窗口宽度小于阈值，且用户没有手动展开，则自动折叠侧边栏
  if (windowWidth < MIN_WIDTH_FOR_SIDEBAR) {
    const manualExpand = localStorage.getItem('adminMenuManualExpand') === 'true'
    if (!isCollapse.value && !manualExpand) {
      isCollapse.value = true
      // 不保存到localStorage，因为这是自动行为
    }
  } else {
    // 窗口宽度足够时，清除手动展开标志
    localStorage.removeItem('adminMenuManualExpand')
  }
}

// 切换收缩状态
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
  // 手动切换时，保存用户的选择到本地存储
  // 无论窗口大小，都保存用户的手动选择
  localStorage.setItem('adminMenuCollapse', String(isCollapse.value))
  // 如果用户手动展开，设置一个标志，暂时忽略自动折叠
  if (!isCollapse.value) {
    localStorage.setItem('adminMenuManualExpand', 'true')
  } else {
    localStorage.removeItem('adminMenuManualExpand')
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
    console.error('从数据库加载配置失败:', error)
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
  // 先从本地存储恢复用户的手动选择
  const savedCollapse = localStorage.getItem('adminMenuCollapse')
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
  
  // 加载知识库列表
  await loadKnowledgeBaseList()
  
  // 从数据库加载配置
  await loadConfigFromDB()
  
  // 加载模型列表
  await loadModelList()
  
  // 添加窗口大小变化监听
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  // 移除窗口大小变化监听
  window.removeEventListener('resize', handleResize)
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
    console.error('保存配置失败:', error)
    ElMessage.error('保存配置失败：' + (error.message || '未知错误'))
  }
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
  background: var(--el-color-primary, #409EFF);
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
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.3s ease;
  height: calc(100vh - 60px); /* 减去header高度 */
  display: flex;
  flex-direction: column;
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
  padding: 20px;
  overflow: hidden;
  height: calc(100vh - 60px); /* 减去header高度 */
  display: flex;
  flex-direction: column;
  transition: margin-left 0.3s ease; /* 添加过渡效果 */
  flex: 1; /* 允许主内容区域自动调整 */
  min-width: 0; /* 允许主内容区域缩小 */
}

.main-content {
  height: 100%;
  width: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
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
    padding: 12px;
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

