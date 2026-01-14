<template>
  <div class="user-app-list">
    <div class="page-header">
      <div class="header-bar">
        <div class="header-left">
          <el-button type="text" @click="handleBack" class="back-btn">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
        </div>
        <div class="header-right"></div>
      </div>
      <div class="header-title">
        <h1>智能应用</h1>
        <p class="subtitle">选择您要使用的应用</p>
      </div>
    </div>
    
    <div v-loading="loading" class="app-cards-container">
      <el-empty v-if="!loading && appList.length === 0" description="暂无可用应用" />
      <div v-else class="app-cards">
        <el-card
          v-for="app in appList"
          :key="app.id"
          class="app-card"
          shadow="hover"
          @click="handleUse(app)"
        >
          <div class="app-card-content">
            <div class="app-icon-wrapper">
              <AppIcon :icon="app.icon" :size="64" />
            </div>
            <div class="app-info">
              <h3 class="app-name">{{ app.name }}</h3>
              <p class="app-description">{{ app.description || '暂无描述' }}</p>
              <div class="app-tags">
                <el-tag :type="app.type === 1 ? 'success' : 'info'" size="small">
                  {{ app.type === 1 ? 'Chat Flow' : 'Workflow' }}
                </el-tag>
                <el-tag v-if="app.streamEnabled" type="warning" size="small" style="margin-left: 8px">
                  流式响应
                </el-tag>
              </div>
            </div>
            <div class="app-action">
              <el-button class="app-use-btn" type="primary" @click.stop="handleUse(app)">
                使用
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getAppList } from '@/api/aiApp'
import AppIcon from '@/components/AppIcon.vue'

const router = useRouter()
const loading = ref(false)
const appList = ref([])

// 获取用户信息
const getUserInfo = () => {
  try {
    const userInfoStr = localStorage.getItem('userInfo')
    return userInfoStr ? JSON.parse(userInfoStr) : null
  } catch (e) {
    return null
  }
}

const fetchAppList = async () => {
  loading.value = true
  try {
    const userInfo = getUserInfo()
    const isAdmin = userInfo?.role === 1
    const userId = userInfo?.userId
    
    const params = { status: 1 }
    if (!isAdmin && userId) {
      params.userId = userId
    }
    
    const res = await getAppList(params)
    // 优化：使用for循环替代filter
    const allApps = res || []
    const filteredApps = []
    for (let i = 0; i < allApps.length; i++) {
      if (allApps[i].status === 1) {
        filteredApps.push(allApps[i])
      }
    }
    appList.value = filteredApps
  } catch (error) {
    ElMessage.error('获取应用列表失败')
  } finally {
    loading.value = false
  }
}

const handleUse = (app) => {
  if (app.type === 1) {
    router.push(`/app/chat/${app.id}`)
  } else {
    router.push(`/app/workflow/${app.id}`)
  }
}

const handleBack = () => {
  router.push('/user/chat')
}

onMounted(() => {
  fetchAppList()
})
</script>

<style scoped>
.user-app-list {
  width: 100%;
  margin: 0 auto;
  padding: 0 20px;
  box-sizing: border-box;
}

.page-header {
  margin-bottom: 24px;
}

.header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.back-btn {
  margin-right: 10px;
}

.header-title {
  text-align: center;
}

.page-header h1 {
  font-size: 32px;
  color: #303133;
  margin: 0 0 10px 0;
}

.subtitle {
  font-size: 16px;
  color: #909399;
  margin: 0;
}

.app-cards-container {
  min-height: 400px;
}

.app-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
  width: 100%;
}

.app-card {
  cursor: pointer;
  transition: all 0.3s ease;
  height: 100%;
  width: 100%;
  margin: 0;
}

.app-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
}

.app-card-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  min-height: 280px;
}

.app-icon-wrapper {
  margin-bottom: 20px;
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e9ecef 100%);
  border-radius: 16px;
  padding: 8px;
}

.app-info {
  flex: 1;
  width: 100%;
  text-align: center;
  margin-bottom: 20px;
}

.app-name {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px 0;
  line-height: 1.4;
}

.app-description {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin: 0 0 16px 0;
  min-height: 44px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.app-tags {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.app-action {
  width: 100%;
  display: flex;
  justify-content: center;
}

.app-use-btn {
  width: 100%;
  height: 40px;
  border-radius: 10px;
  font-weight: 600;
}
</style>

