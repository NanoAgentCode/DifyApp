<template>
  <div>
    <!-- 顶部系统图标占位 -->
    <div class="top-logo-placeholder" :class="{ 'logo-collapsed': isCollapsed }">
      <img src="/logo.svg" alt="系统图标" class="top-logo-img" />
    </div>
    
    <!-- 顶部导航栏 -->
    <div class="app-header" :class="{ 'header-collapsed': isCollapsed }">
      <div class="header-left">
        <div class="system-icon">
          <img src="/logo.svg" alt="系统图标" class="system-logo" />
        </div>
        <h2>NanoAgent Workbench</h2>
        <el-tooltip :content="isCollapsed ? '展开顶部' : '收起顶部'" placement="bottom">
          <el-button type="text" @click="toggleCollapse" class="collapse-header-button">
            <el-icon><ArrowUp v-if="!isCollapsed" /><ArrowDown v-else /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
      <div class="header-right">
        <slot name="extra-buttons"></slot>
        <el-dropdown>
          <span class="user-info">
            <el-icon><User /></el-icon>
            <span>{{ userInfo?.username || '用户' }}</span>
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleCommand('changePassword')">修改密码</el-dropdown-item>
              <el-dropdown-item @click="handleCommand('memory')">记忆管理</el-dropdown-item>
              <el-dropdown-item divided @click="handleCommand('logout')">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 顶部收起时的展开按钮 -->
    <div v-if="isCollapsed" class="expand-header-button" @click="toggleCollapse">
      <el-tooltip content="展开顶部" placement="right">
        <el-button type="primary" circle>
          <el-icon><ArrowDown /></el-icon>
        </el-button>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, ArrowDown, ArrowUp } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'command'])

const router = useRouter()
const userInfo = ref(null)

// localStorage 键名
const HEADER_COLLAPSED_KEY = 'headerCollapsed'

// 从 localStorage 读取收起状态
const loadCollapsedState = () => {
  const savedState = localStorage.getItem(HEADER_COLLAPSED_KEY)
  if (savedState !== null) {
    return savedState === 'true'
  }
  return false // 默认展开
}

// 保存收起状态到 localStorage
const saveCollapsedState = (collapsed) => {
  localStorage.setItem(HEADER_COLLAPSED_KEY, String(collapsed))
}

// 初始化时优先使用 localStorage 的值，如果没有则使用 props.modelValue
const initialCollapsed = loadCollapsedState()
const isCollapsed = ref(initialCollapsed)

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

// 切换收起/展开
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  saveCollapsedState(isCollapsed.value)
  emit('update:modelValue', isCollapsed.value)
}

// 处理下拉菜单命令
const handleCommand = (command) => {
  emit('command', command)
  
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
      .then(() => {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        ElMessage.success('已退出登录')
        router.push('/login')
      })
      .catch(() => {})
  }
}

// 监听外部传入的 modelValue 变化
watch(() => props.modelValue, (newVal) => {
  isCollapsed.value = newVal
  saveCollapsedState(newVal)
})

onMounted(() => {
  userInfo.value = getUserInfo()
  // 确保父组件同步 localStorage 中的状态
  if (isCollapsed.value !== props.modelValue) {
    emit('update:modelValue', isCollapsed.value)
  }
})
</script>

<style scoped>
.top-logo-placeholder {
  position: fixed;
  top: 0;
  left: 0;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-color-primary, #409EFF);
  z-index: 1001;
  border-bottom: 1px solid var(--el-border-color-primary-dark-2, rgba(64, 158, 255, 0.8));
  border-right: 1px solid var(--el-border-color-primary-dark-2, rgba(64, 158, 255, 0.8));
  box-sizing: border-box;
  transition: transform 0.3s ease;
}

.top-logo-img {
  width: 40px;
  height: 40px;
  object-fit: contain;
}

.top-logo-placeholder.logo-collapsed {
  transform: translateY(-100%);
}

.app-header {
  display: flex !important;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  width: 100vw !important;
  max-width: 100vw !important;
  height: 60px;
  background: var(--el-color-primary, #409EFF);
  border-bottom: 1px solid var(--el-border-color-primary-dark-2, rgba(64, 158, 255, 0.8));
  position: fixed !important;
  top: 0 !important;
  left: 0 !important;
  right: 0 !important;
  z-index: 1000 !important;
  transition: transform 0.3s ease;
  box-sizing: border-box;
  visibility: visible !important;
  opacity: 1 !important;
}

.app-header.header-collapsed {
  transform: translateY(-100%);
}

.header-left {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  position: relative;
}

.system-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.system-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.header-left h2 {
  margin: 0;
  font-size: 20px;
  color: #ffffff;
  font-weight: 500;
  white-space: nowrap;
}

.collapse-header-button {
  padding: 8px;
  color: #ffffff;
  background-color: transparent;
  transition: color 0.2s, background-color 0.2s;
  flex-shrink: 0;
}

.collapse-header-button:hover,
.collapse-header-button:focus {
  color: #ffffff;
  background-color: rgba(128, 128, 128, 0.3);
}

.collapse-header-button:focus-visible {
  outline: none;
  background-color: rgba(128, 128, 128, 0.3);
}

.header-right {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1001;
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

.expand-header-button {
  position: fixed;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 999;
  animation: slideDown 0.3s ease;
  pointer-events: auto;
}

.expand-header-button :deep(.el-button) {
  background-color: transparent !important;
  border-color: transparent !important;
  color: #909399 !important;
}

.expand-header-button :deep(.el-button:hover),
.expand-header-button :deep(.el-button:focus) {
  background-color: rgba(128, 128, 128, 0.3) !important;
  border-color: rgba(128, 128, 128, 0.3) !important;
  color: #909399 !important;
}

.expand-header-button :deep(.el-button:focus-visible) {
  outline: none;
  background-color: rgba(128, 128, 128, 0.3) !important;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

@media (max-width: 1024px) {
  .app-header {
    padding: 0 12px;
    height: 50px;
  }

  .header-left h2 {
    font-size: 16px;
  }
}

@media (max-width: 768px) {
  .header-left h2 {
    font-size: 14px;
  }
}
</style>
