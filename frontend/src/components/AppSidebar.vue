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
    <!-- 顶部横幅收起时，在导航栏顶部显示系统图标 -->
    <div v-if="isHeaderCollapsed" class="sidebar-logo">
      <img src="/logo.svg" alt="系统图标" class="sidebar-logo-img" />
    </div>
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
        <!-- 组件管理 -->
        <el-tooltip v-if="isAdmin" content="组件管理" placement="right" :show-after="200">
          <el-menu-item index="/admin/models">
            <el-icon><Setting /></el-icon>
            <span>组件管理</span>
          </el-menu-item>
        </el-tooltip>
        <!-- 用户管理 -->
        <el-tooltip v-if="isAdmin" content="用户管理" placement="right" :show-after="200">
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
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
        <el-tooltip v-if="isAdmin" content="数据分析" placement="right" :show-after="200">
          <el-menu-item index="/admin/data-analysis">
            <el-icon><Share /></el-icon>
            <span>数据分析</span>
          </el-menu-item>
        </el-tooltip>
        <el-tooltip v-if="isAdmin" content="行为日志" placement="right" :show-after="200">
          <el-menu-item index="/admin/user-action-logs">
            <el-icon><Document /></el-icon>
            <span>行为日志</span>
          </el-menu-item>
        </el-tooltip>
        <el-tooltip v-if="isAdmin" content="日志监控" placement="right" :show-after="200">
          <el-menu-item index="/admin/observability">
            <el-icon><Monitor /></el-icon>
            <span>日志监控</span>
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
  DataAnalysis, 
  Share,
  Setting,
  User,
  Tools,
  Document,
  Monitor
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
/* ========== 侧边栏基础样式 ========== */
.app-sidebar :deep(.el-aside),
.app-sidebar {
  width: var(--sidebar-width) !important;
  min-width: var(--sidebar-width) !important;
  max-width: var(--sidebar-width) !important;
}

.app-sidebar {
  width: var(--sidebar-width) !important;
  min-width: var(--sidebar-width) !important;
  max-width: var(--sidebar-width) !important;
  position: fixed;
  left: 0;
  top: var(--header-height) !important;
  z-index: var(--z-sticky);
  height: calc(100vh - var(--header-height)) !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
  margin: 0 !important;
  padding: 0 !important;
  background: var(--color-bg-primary);
  border-right: 1px solid var(--color-border-light);
  transition: top var(--transition-base), height var(--transition-base), box-shadow var(--transition-base);
  box-sizing: border-box;
  box-shadow: var(--shadow-sm);
}

.app-sidebar.portal-sidebar:not(.sidebar-header-collapsed) {
  top: var(--header-height) !important;
  height: calc(100vh - var(--header-height)) !important;
}

.app-sidebar.portal-sidebar.sidebar-header-collapsed {
  top: 0 !important;
  height: 100vh !important;
}

.app-sidebar:not(.portal-sidebar):not(.sidebar-header-collapsed-non-portal):not(.sidebar-header-collapsed) {
  top: var(--header-height) !important;
  height: calc(100vh - var(--header-height)) !important;
}

.app-sidebar.sidebar-header-collapsed-non-portal {
  height: 100vh !important;
  top: 0 !important;
}

/* ========== Logo 区域 ========== */
.sidebar-logo {
  width: var(--sidebar-width);
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  border-bottom: 1px solid var(--color-border-light);
  flex-shrink: 0;
  box-sizing: border-box;
  transition: all var(--transition-base);
}

.sidebar-logo:hover {
  background: linear-gradient(135deg, var(--color-primary-light-1) 0%, var(--color-primary) 100%);
}

.sidebar-logo-img {
  width: 32px;
  height: 32px;
  object-fit: contain;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}

/* ========== 菜单样式 ========== */
.menu {
  border-right: none;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--color-bg-primary);
  margin: 0 !important;
  padding: var(--spacing-sm) 0 !important;
  border-radius: 0;
  box-shadow: none;
}

.menu :deep(.el-menu--collapse) {
  width: var(--sidebar-width) !important;
  min-width: var(--sidebar-width) !important;
  max-width: var(--sidebar-width) !important;
  padding: 0 !important;
  margin: 0 !important;
  box-sizing: border-box;
  background: transparent;
}

.menu :deep(.el-menu) {
  padding: 0 !important;
  margin: 0 !important;
  border: none !important;
  background: transparent;
}

.menu :deep(.el-menu--collapse .el-menu-item) {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0 !important;
  height: 48px;
  width: 48px;
  margin: var(--spacing-xs) auto;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
  position: relative;
}

.menu :deep(.el-menu--collapse .el-menu-item::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  background: var(--color-primary);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  transition: height var(--transition-base);
}

.menu :deep(.el-menu--collapse .el-menu-item.is-active::before) {
  height: 24px;
}

.menu :deep(.el-menu--collapse .el-menu-item .el-icon) {
  margin: 0 !important;
  padding: 0 !important;
  font-size: 22px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-base);
}

.menu :deep(.el-menu--collapse .el-menu-item:hover) {
  background-color: var(--color-bg-hover);
  transform: translateX(2px);
}

.menu :deep(.el-menu--collapse .el-menu-item.is-active) {
  background-color: var(--color-bg-active);
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
}

.menu :deep(.el-menu--collapse .el-menu-item.is-active .el-icon) {
  color: var(--color-primary);
  transform: scale(1.1);
}

.menu :deep(.el-menu--collapse .el-sub-menu__title) {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0 !important;
  height: 48px;
  width: 48px;
  margin: var(--spacing-xs) auto;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.menu :deep(.el-menu--collapse .el-sub-menu__title:hover) {
  background-color: var(--color-bg-hover);
}

.menu :deep(.el-menu--collapse .el-sub-menu__title .el-icon) {
  margin: 0 !important;
  padding: 0 !important;
  font-size: 22px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ========== 工具提示样式增强 ========== */
:deep(.el-tooltip__popper) {
  background: var(--color-text-primary);
  color: var(--color-bg-primary);
  border-radius: var(--radius-md);
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: var(--font-size-sm);
  box-shadow: var(--shadow-lg);
  border: none;
}

:deep(.el-tooltip__popper .el-popper__arrow::before) {
  background: var(--color-text-primary);
  border: none;
}

/* ========== 响应式设计 ========== */
@media (max-width: 1024px) {
  .app-sidebar.portal-sidebar:not(.sidebar-header-collapsed) {
    top: 56px !important;
    height: calc(100vh - 56px) !important;
  }

  .app-sidebar.portal-sidebar.sidebar-header-collapsed {
    top: 0 !important;
    height: 100vh !important;
  }

  .app-sidebar:not(.portal-sidebar):not(.sidebar-header-collapsed-non-portal) {
    top: 56px !important;
    height: calc(100vh - 56px) !important;
  }

  .app-sidebar.sidebar-header-collapsed-non-portal {
    top: 0 !important;
    height: 100vh !important;
  }

  .menu :deep(.el-menu--collapse .el-menu-item) {
    height: 44px;
    width: 44px;
  }

  .menu :deep(.el-menu--collapse .el-menu-item .el-icon) {
    font-size: 20px;
    width: 20px;
    height: 20px;
  }
}
</style>
