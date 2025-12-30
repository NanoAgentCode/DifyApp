<template>
  <el-aside 
    width="56px" 
    class="app-sidebar"
    style="width: 56px !important; min-width: 56px !important; max-width: 56px !important;" 
    :class="{ 
      'portal-sidebar': isPortalMode,
      'sidebar-header-collapsed': isHeaderCollapsed && isPortalMode, 
      'sidebar-header-collapsed-non-portal': isHeaderCollapsed && !isPortalMode 
    }"
  >
    <el-menu
      :default-active="activeMenu"
      :collapse="true"
      router
      class="menu"
    >
      <!-- 用户端菜单 -->
      <template v-if="type === 'user'">
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
      </template>

      <!-- 管理员端菜单 -->
      <template v-else-if="type === 'admin'">
        <!-- 核心功能 -->
        <el-tooltip content="智能问答" placement="right" :show-after="200">
          <el-menu-item index="/admin/chat">
            <el-icon><ChatLineRound /></el-icon>
            <span>智能问答</span>
          </el-menu-item>
        </el-tooltip>
        <!-- 知识库相关 -->
        <el-tooltip v-if="isAdmin" content="知识问答" placement="right" :show-after="200">
          <el-menu-item index="/admin/kb-qa">
            <el-icon><Document /></el-icon>
            <span>知识问答</span>
          </el-menu-item>
        </el-tooltip>
        <el-tooltip v-if="isAdmin" content="知识管理" placement="right" :show-after="200">
          <el-menu-item index="/admin/knowledge-base">
            <el-icon><Folder /></el-icon>
            <span>知识管理</span>
          </el-menu-item>
        </el-tooltip>
        <el-tooltip content="文档解读" placement="right" :show-after="200">
          <el-menu-item index="/admin/document-reader">
            <el-icon><Reading /></el-icon>
            <span>文档解读</span>
          </el-menu-item>
        </el-tooltip>
        <el-tooltip content="应用列表" placement="right" :show-after="200">
          <el-menu-item index="/admin/apps">
            <el-icon><List /></el-icon>
            <span>应用列表</span>
          </el-menu-item>
        </el-tooltip>
        <!-- 工具 -->
        <el-tooltip v-if="isAdmin" content="高级功能" placement="right" :show-after="200">
          <el-menu-item index="/admin/text2sql">
            <el-icon><Search /></el-icon>
            <span>高级功能</span>
          </el-menu-item>
        </el-tooltip>
        <!-- 系统管理 -->
        <el-tooltip v-if="isAdmin" content="LLM管理" placement="right" :show-after="200">
          <el-menu-item index="/admin/models">
            <el-icon><Setting /></el-icon>
            <span>LLM管理</span>
          </el-menu-item>
        </el-tooltip>
        <!-- 用户管理 -->
        <el-tooltip v-if="isAdmin" content="用户管理" placement="right" :show-after="200">
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
        </el-tooltip>
        <el-tooltip v-if="isAdmin" content="会话历史" placement="right" :show-after="200">
          <el-menu-item index="/admin/chat-history">
            <el-icon><Clock /></el-icon>
            <span>会话历史</span>
          </el-menu-item>
        </el-tooltip>
        <el-tooltip v-if="isAdmin" content="系统配置" placement="right" :show-after="200">
          <el-menu-item index="/admin/system-config">
            <el-icon><Tools /></el-icon>
            <span>系统配置</span>
          </el-menu-item>
        </el-tooltip>
        <el-tooltip v-if="isAdmin" content="数据统计" placement="right" :show-after="200">
          <el-menu-item index="/admin/statistics">
            <el-icon><DataAnalysis /></el-icon>
            <span>数据统计</span>
          </el-menu-item>
        </el-tooltip>
      </template>
    </el-menu>
  </el-aside>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { 
  ChatLineRound, 
  Document, 
  Folder, 
  List, 
  Reading, 
  DataAnalysis, 
  Clock,
  Search,
  Setting,
  User,
  Tools
} from '@element-plus/icons-vue'

const props = defineProps({
  type: {
    type: String,
    required: true,
    validator: (value) => ['user', 'admin'].includes(value)
  },
  isHeaderCollapsed: {
    type: Boolean,
    default: false
  },
  isPortalMode: {
    type: Boolean,
    default: false
  }
})

const route = useRoute()
const userInfo = ref(null)

const activeMenu = computed(() => {
  const basePath = props.type === 'user' ? '/user' : '/admin'
  if (route.path === basePath || route.path === `${basePath}/`) {
    return `${basePath}/chat`
  }
  return route.path
})

// 判断当前是否在智能问答页面（Portal页面）
const isOnChatPage = computed(() => {
  const chatPath = props.type === 'user' ? '/user/chat' : '/admin/chat'
  // 使用 startsWith 匹配，因为路由可能包含查询参数或其他路径
  return route.path === chatPath || route.path.startsWith(chatPath + '/') || route.path.startsWith(chatPath + '?')
})

const isAdmin = computed(() => {
  if (props.type !== 'admin') return false
  return userInfo.value && userInfo.value.role === 1
})

// 获取用户信息
const getUserInfo = () => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      return JSON.parse(userInfoStr)
    } catch (e) {
      console.error('解析用户信息失败', e)
    }
  }
  return null
}

onMounted(() => {
  userInfo.value = getUserInfo()
})
</script>

<style scoped>
/* 强制覆盖 Element Plus el-aside 的默认宽度 */
.app-sidebar :deep(.el-aside),
.app-sidebar {
  width: 56px !important; /* 明确设置宽度 */
  min-width: 56px !important; /* 设置最小宽度 */
  max-width: 56px !important; /* 设置最大宽度 */
}

.app-sidebar {
  width: 56px !important; /* 明确设置宽度 */
  min-width: 56px !important; /* 设置最小宽度 */
  max-width: 56px !important; /* 设置最大宽度 */
  position: fixed; /* 统一使用固定定位 */
  left: 0;
  top: 60px !important; /* 顶部展开时，导航栏从顶部导航栏下方开始 */
  z-index: 50; /* 低于顶部导航栏（1000） */
  height: calc(100vh - 60px) !important; /* 减去顶部导航栏高度 */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0; /* 防止侧边栏被压缩 */
  margin: 0 !important; /* 移除所有外边距 */
  padding: 0 !important; /* 移除所有内边距 */
  background: #fff;
  border-right: 1px solid #e4e7ed;
  transition: top 0.3s ease, height 0.3s ease;
  box-sizing: border-box;
}

/* 门户模式下，根据横幅展开/收起状态调整位置 */
.app-sidebar.portal-sidebar:not(.sidebar-header-collapsed) {
  top: 60px !important; /* 门户模式下横幅展开时，从60px开始 */
  height: calc(100vh - 60px) !important;
}

.app-sidebar.portal-sidebar.sidebar-header-collapsed {
  top: 0 !important; /* 门户模式下横幅收起时，紧贴顶部 */
  height: 100vh !important;
}

/* 非门户模式，顶部展开时从 60px 开始 */
.app-sidebar:not(.portal-sidebar):not(.sidebar-header-collapsed-non-portal):not(.sidebar-header-collapsed) {
  top: 60px !important; /* 顶部展开时，导航栏被向下推 */
  height: calc(100vh - 60px) !important;
}

.app-sidebar.sidebar-header-collapsed-non-portal {
  height: 100vh !important;
  top: 0 !important; /* 顶部收起时，导航栏贴着顶部 */
}

.menu {
  border-right: none;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background: #fff;
  margin: 0 !important;
  padding: 0 !important; /* 确保没有内边距 */
  border-radius: 0;
  box-shadow: none;
}

.menu :deep(.el-menu--collapse) {
  width: 56px !important; /* 明确设置菜单宽度 */
  min-width: 56px !important;
  max-width: 56px !important;
  padding: 0 !important;
  margin: 0 !important;
  box-sizing: border-box;
}

.menu :deep(.el-menu) {
  padding: 0 !important;
  margin: 0 !important;
  border: none !important;
}

.menu :deep(.el-menu--collapse .el-menu-item) {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0 !important;
  height: 56px; /* 与导航栏宽度一致，保持方形 */
  width: 56px; /* 确保宽度与高度一致 */
  margin: 0;
  box-sizing: border-box;
}

.menu :deep(.el-menu--collapse .el-menu-item .el-icon) {
  margin: 0 !important;
  padding: 0 !important;
  font-size: 22px; /* 适当增大图标 */
  width: 22px; /* 图标宽度 */
  height: 22px; /* 图标高度，保持方形 */
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu :deep(.el-menu--collapse .el-sub-menu__title) {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0 !important;
  height: 56px; /* 与导航栏宽度一致，保持方形 */
  width: 56px; /* 确保宽度与高度一致 */
  margin: 0;
  box-sizing: border-box;
}

.menu :deep(.el-menu--collapse .el-sub-menu__title .el-icon) {
  margin: 0 !important;
  padding: 0 !important;
  font-size: 22px; /* 适当增大图标 */
  width: 22px; /* 图标宽度 */
  height: 22px; /* 图标高度，保持方形 */
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu :deep(.el-menu-item.is-active) {
  background-color: #ecf5ff;
  color: #409eff;
}

.menu :deep(.el-menu-item:hover) {
  background-color: #f5f7fa;
}

/* 小屏幕适配 */
@media (max-width: 1024px) {
  /* 门户模式下，根据横幅展开/收起状态调整位置 */
  .app-sidebar.portal-sidebar:not(.sidebar-header-collapsed) {
    top: 50px !important; /* 小屏幕门户模式下横幅展开时，从50px开始 */
    height: calc(100vh - 50px) !important;
  }

  .app-sidebar.portal-sidebar.sidebar-header-collapsed {
    top: 0 !important; /* 小屏幕门户模式下横幅收起时，紧贴顶部 */
    height: 100vh !important;
  }

  /* 非门户模式，顶部展开时从 50px 开始 */
  .app-sidebar:not(.portal-sidebar):not(.sidebar-header-collapsed-non-portal) {
    top: 50px !important; /* 小屏幕顶部导航栏高度为50px */
    height: calc(100vh - 50px) !important;
  }

  /* 非门户模式，顶部收起时紧贴顶部 */
  .app-sidebar.sidebar-header-collapsed-non-portal {
    top: 0 !important;
    height: 100vh !important;
  }
}
</style>

