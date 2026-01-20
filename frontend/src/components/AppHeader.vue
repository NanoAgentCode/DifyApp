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
      </div>
      <!-- 标题居中显示 -->
      <div class="header-center">
        <h2 class="header-title">NanoAgent Workbench</h2>
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
/* ========== 顶部 Logo 占位 ========== */
.top-logo-placeholder {
  position: fixed;
  top: 0;
  left: 0;
  width: var(--header-height);
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  z-index: calc(var(--z-fixed) + 1);
  box-sizing: border-box;
  transition: transform var(--transition-base);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(10px);
}

.top-logo-img {
  width: 36px;
  height: 36px;
  object-fit: contain;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}

.top-logo-placeholder.logo-collapsed {
  transform: translateY(-100%);
}

/* ========== 主头部 ========== */
.app-header {
  display: flex !important;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-lg);
  width: 100vw !important;
  max-width: 100vw !important;
  height: var(--header-height);
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  position: fixed !important;
  top: 0 !important;
  left: 0 !important;
  right: 0 !important;
  z-index: var(--z-fixed) !important;
  transition: transform var(--transition-base);
  box-sizing: border-box;
  visibility: visible !important;
  opacity: 1 !important;
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(10px);
}

.app-header.header-collapsed {
  transform: translateY(-100%);
}

/* ========== 左侧区域 ========== */
.header-left {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: var(--spacing-md);
  position: relative;
}

.system-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: var(--radius-md);
  padding: var(--spacing-xs);
  transition: all var(--transition-base);
  position: relative;
  z-index: 2;
}

.system-icon:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.05);
}

.system-logo {
  width: 28px;
  height: 28px;
  object-fit: contain;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}

/* ========== 中间区域（标题和收起按钮） ========== */
.header-center {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  z-index: calc(var(--z-fixed) + 1);
}

/* ========== 标题居中 ========== */
.header-title {
  margin: 0;
  padding-left: 15%; /* 60px / 1920px ≈ 3.125% (基于常见桌面宽度) */
  font-size: var(--font-size-xl);
  color: #ffffff;
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
  letter-spacing: 0.5px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  pointer-events: none;
  min-width: max-content;
  text-align: center;
}

.collapse-header-button {
  padding: var(--spacing-sm);
  color: rgba(255, 255, 255, 0.9);
  background-color: rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.1);
  position: relative;
  z-index: 2;
  pointer-events: auto;
}

.collapse-header-button:hover,
.collapse-header-button:focus {
  color: #ffffff;
  background-color: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.collapse-header-button:active {
  transform: translateY(0);
}

.collapse-header-button:focus-visible {
  outline: 2px solid rgba(255, 255, 255, 0.5);
  outline-offset: 2px;
}

/* ========== 右侧区域 ========== */
.header-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-md);
  position: relative;
  z-index: calc(var(--z-fixed) + 1);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: rgba(255, 255, 255, 0.95);
  cursor: pointer;
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.1);
  font-weight: var(--font-weight-medium);
}

.user-info:hover {
  background-color: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.user-info:active {
  transform: translateY(0);
}

.user-info .el-icon {
  font-size: 18px;
}

.user-info span {
  font-size: var(--font-size-sm);
}

/* ========== 展开按钮 ========== */
.expand-header-button {
  position: fixed;
  top: var(--spacing-md);
  left: 50%;
  transform: translateX(-50%);
  z-index: calc(var(--z-fixed) - 1);
  animation: slideDown var(--transition-base);
  pointer-events: auto;
}

.expand-header-button :deep(.el-button) {
  background-color: rgba(255, 255, 255, 0.95) !important;
  border-color: var(--color-border-light) !important;
  color: var(--color-text-primary) !important;
  box-shadow: var(--shadow-md) !important;
  transition: all var(--transition-base) !important;
}

.expand-header-button :deep(.el-button:hover),
.expand-header-button :deep(.el-button:focus) {
  background-color: #ffffff !important;
  border-color: var(--color-primary) !important;
  color: var(--color-primary) !important;
  box-shadow: var(--shadow-primary) !important;
  transform: translateY(-2px) !important;
}

.expand-header-button :deep(.el-button:active) {
  transform: translateY(0) !important;
}

.expand-header-button :deep(.el-button:focus-visible) {
  outline: 2px solid var(--color-primary-light-3);
  outline-offset: 2px;
}

/* ========== 动画 ========== */
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

/* ========== 响应式设计 ========== */
@media (max-width: 1024px) {
  .app-header {
    padding: 0 var(--spacing-md);
    height: 56px;
  }

  .top-logo-placeholder {
    width: 56px;
    height: 56px;
  }

  .header-title {
    font-size: var(--font-size-lg);
  }

  .header-center {
    gap: var(--spacing-xs);
  }

  .system-icon {
    width: 32px;
    height: 32px;
  }

  .system-logo {
    width: 24px;
    height: 24px;
  }
}

@media (max-width: 768px) {
  .app-header {
    padding: 0 var(--spacing-sm);
    height: 52px;
  }

  .top-logo-placeholder {
    width: 52px;
    height: 52px;
  }

  .header-left {
    gap: var(--spacing-sm);
  }

  .header-center {
    gap: var(--spacing-xs);
  }

  .header-title {
    font-size: var(--font-size-md);
    /* 在小屏幕上，如果空间不足，可以稍微调整位置 */
    max-width: calc(100vw - 200px);
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .user-info span {
    display: none;
  }

  .user-info {
    padding: var(--spacing-xs) var(--spacing-sm);
  }
}
</style>
