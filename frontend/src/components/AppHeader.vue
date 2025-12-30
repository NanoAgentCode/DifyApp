<template>
  <div>
    <!-- 顶部导航栏 -->
    <div class="app-header" :class="{ 'header-collapsed': isCollapsed }">
      <div class="header-left">
        <div class="system-icon">
          <img src="/logo.svg" alt="系统图标" class="system-logo" />
        </div>
        <h2>NanoAgent智能应用工作台</h2>
        <el-tooltip :content="isCollapsed ? '展开顶部' : '收起顶部'" placement="bottom">
          <el-button type="text" @click="toggleCollapse" class="collapse-header-button">
            <el-icon><ArrowUp v-if="!isCollapsed" /><ArrowDown v-else /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
      <div class="header-right">
        <slot name="extra-buttons"></slot>
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
const isCollapsed = ref(props.modelValue)

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
})

onMounted(() => {
  userInfo.value = getUserInfo()
})
</script>

<style scoped>
/* 顶部导航栏 */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  margin: 0;
  width: 100%;
  height: 60px;
  background: var(--el-color-primary, #409EFF);
  border-bottom: 1px solid var(--el-border-color-primary-dark-2, rgba(64, 158, 255, 0.8));
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  transition: transform 0.3s ease;
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
  display: block;
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
  transition: color 0.2s, background-color 0.2s;
  flex-shrink: 0;
}

.collapse-header-button:hover {
  color: #ffffff;
  background-color: rgba(255, 255, 255, 0.1);
}

.header-right {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1;
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

/* 顶部收起时的展开按钮 */
.expand-header-button {
  position: fixed;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 99;
  animation: slideDown 0.3s ease;
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

/* 小屏幕适配 */
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

