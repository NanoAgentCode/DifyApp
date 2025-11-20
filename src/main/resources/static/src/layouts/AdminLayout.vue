<template>
  <el-container class="admin-layout">
    <el-header class="header">
      <div class="header-left">
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
        <el-button type="primary" @click="goToCreate" style="margin-left: 10px">
          <el-icon><Plus /></el-icon>
          创建应用
        </el-button>
      </div>
    </el-header>
    <el-container>
      <el-aside width="200px" class="aside">
        <el-menu
          :default-active="activeMenu"
          router
          class="menu"
        >
          <el-menu-item index="/admin/apps">
            <el-icon><List /></el-icon>
            <span>应用列表</span>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/admin/kb-qa">
            <el-icon><ChatLineRound /></el-icon>
            <span>知识库问答</span>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/admin/knowledge-base">
            <el-icon><Folder /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
    
    <ChangePasswordDialog
      v-model="showChangePasswordDialog"
      @success="handlePasswordChangeSuccess"
    />
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, List, User, ArrowDown, Folder, ChatLineRound } from '@element-plus/icons-vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => {
  return route.path
})
const userInfo = ref(null)
const isAdmin = computed(() => userInfo.value && userInfo.value.role === 1)
const showChangePasswordDialog = ref(false)

onMounted(() => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      userInfo.value = JSON.parse(userInfoStr)
    } catch (e) {
      console.error('解析用户信息失败', e)
    }
  }
})

const goToCreate = () => {
  router.push('/admin/apps/create')
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
</script>

<style scoped>
.admin-layout {
  height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #409eff;
  color: white;
  padding: 0 20px;
}

.header-left h2 {
  margin: 0;
  font-size: 20px;
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
}

.menu {
  border-right: none;
}

.main {
  background: #f5f7fa;
  padding: 20px;
}
</style>

