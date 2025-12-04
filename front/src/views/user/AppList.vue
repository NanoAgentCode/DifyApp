<template>
  <div class="user-app-list">
    <div class="page-header">
      <h1>智能应用</h1>
      <p class="subtitle">选择您要使用的应用</p>
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
              <el-button type="primary" size="large" @click.stop="handleUse(app)">
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
import { getAppList } from '@/api/aiApp'
import AppIcon from '@/components/AppIcon.vue'

const router = useRouter()
const loading = ref(false)
const appList = ref([])

const fetchAppList = async () => {
  loading.value = true
  try {
    // 获取当前用户ID
    const userInfoStr = localStorage.getItem('userInfo')
    let userId = null
    if (userInfoStr) {
      try {
        const userInfo = JSON.parse(userInfoStr)
        userId = userInfo.userId
      } catch (e) {
        console.error('解析用户信息失败', e)
      }
    }
    
    // 如果用户是管理员，不传userId，获取所有应用；否则传userId，获取可见应用
    const userInfoStr2 = localStorage.getItem('userInfo')
    let isAdmin = false
    if (userInfoStr2) {
      try {
        const userInfo = JSON.parse(userInfoStr2)
        isAdmin = userInfo.role === 1
      } catch (e) {
        // ignore
      }
    }
    
    const params = { status: 1 }
    if (!isAdmin && userId) {
      params.userId = userId
    }
    
    const res = await getAppList(params)
    appList.value = (res || []).filter(app => app.status === 1) // 再次过滤确保只显示启用的应用
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
  text-align: center;
  margin-bottom: 40px;
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

.app-action .el-button {
  width: 100%;
  font-size: 16px;
  padding: 12px 0;
}
</style>

