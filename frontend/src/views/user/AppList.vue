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
import { useAppNavigation } from '@/composables/useAppNavigation'
import { getStoredUserId, isAdminUser } from '@/utils/userSession'

const router = useRouter()
const { navigateToApp } = useAppNavigation()
const loading = ref(false)
const appList = ref([])

const fetchAppList = async () => {
  loading.value = true
  try {
    const isAdmin = isAdminUser()
    const userId = getStoredUserId()
    
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
  navigateToApp(app)
}

const handleBack = () => {
  router.push('/user/chat')
}

onMounted(() => {
  fetchAppList()
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.user-app-list {
  width: 100%;
  max-width: var(--content-max-width);
  margin: 0 auto;
  padding: var(--spacing-lg);
  box-sizing: border-box;
  background: var(--color-bg-secondary);
}

/* ========== 页面头部 ========== */
.page-header {
  margin-bottom: var(--spacing-xl);
  text-align: center;
}

.header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-md);
}

.back-btn {
  margin-right: var(--spacing-md);
  transition: all var(--transition-base);
}

.back-btn:hover {
  color: var(--color-primary);
  transform: translateX(-2px);
}

.header-title {
  text-align: center;
}

.page-header h1 {
  font-size: var(--font-size-3xl);
  color: var(--color-text-primary);
  margin: 0 0 var(--spacing-sm) 0;
  font-weight: var(--font-weight-semibold);
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.subtitle {
  font-size: var(--font-size-md);
  color: var(--color-text-secondary);
  margin: 0;
}

/* ========== 应用卡片容器 ========== */
.app-cards-container {
  min-height: 400px;
}

.app-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-lg);
  width: 100%;
}

/* ========== 应用卡片 ========== */
.app-card {
  cursor: pointer;
  transition: all var(--transition-base);
  height: 100%;
  width: 100%;
  margin: 0;
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  border: 1px solid var(--color-border-lighter);
  overflow: hidden;
}

.app-card:hover {
  transform: translateY(-8px);
  box-shadow: var(--shadow-xl);
  border-color: var(--color-primary);
}

.app-card-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--spacing-lg);
  min-height: 280px;
  background: var(--color-bg-primary);
}

.app-icon-wrapper {
  margin-bottom: var(--spacing-lg);
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-bg-secondary) 0%, var(--color-bg-tertiary) 100%);
  border-radius: var(--radius-xl);
  padding: var(--spacing-sm);
  transition: all var(--transition-base);
  box-shadow: var(--shadow-sm);
}

.app-card:hover .app-icon-wrapper {
  transform: scale(1.1);
  box-shadow: var(--shadow-md);
  background: linear-gradient(135deg, var(--color-primary-light-5) 0%, var(--color-bg-secondary) 100%);
}

.app-info {
  flex: 1;
  width: 100%;
  text-align: center;
  margin-bottom: var(--spacing-lg);
}

.app-name {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0 0 var(--spacing-md) 0;
  line-height: var(--line-height-tight);
}

.app-description {
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  line-height: var(--line-height-normal);
  margin: 0 0 var(--spacing-md) 0;
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
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

:deep(.app-tags .el-tag) {
  border-radius: var(--radius-sm);
  font-weight: var(--font-weight-normal);
  border: none;
  padding: var(--spacing-xs) var(--spacing-sm);
}

.app-action {
  width: 100%;
  display: flex;
  justify-content: center;
  margin-top: auto;
}

.app-use-btn {
  width: 100%;
  height: 40px;
  border-radius: var(--radius-md);
  font-weight: var(--font-weight-medium);
  transition: all var(--transition-base);
  box-shadow: var(--shadow-xs);
}

.app-use-btn:hover {
  box-shadow: var(--shadow-primary);
  transform: translateY(-1px);
}

.app-use-btn:active {
  transform: translateY(0);
}

/* ========== 响应式设计 ========== */
@media (max-width: 1024px) {
  .app-cards {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: var(--spacing-md);
  }
}

@media (max-width: 768px) {
  .user-app-list {
    padding: var(--spacing-md);
  }

  .app-cards {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: var(--spacing-sm);
  }

  .page-header h1 {
    font-size: var(--font-size-2xl);
  }
}
</style>

