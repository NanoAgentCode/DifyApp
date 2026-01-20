<template>
  <div class="app-detail">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button type="text" @click="handleBack" style="margin-right: 10px">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <AppIcon v-if="appDetail?.icon" :icon="appDetail.icon" :size="32" class="app-icon" />
            <span>应用详情</span>
          </div>
          <div>
            <el-button type="primary" @click="handleEdit">编辑</el-button>
            <el-button type="primary" @click="handleUse">使用</el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="2" border v-if="appDetail">
        <el-descriptions-item label="应用图标" v-if="appDetail.icon">
          <AppIcon :icon="appDetail.icon" :size="48" />
        </el-descriptions-item>
        <el-descriptions-item label="应用ID">{{ appDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="应用名称">{{ appDetail.name }}</el-descriptions-item>
        <el-descriptions-item label="应用描述" :span="2">{{ appDetail.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="应用类型">
          <el-tag :type="appDetail.type === 1 ? 'success' : 'info'">
            {{ appDetail.type === 1 ? 'Chat Flow' : 'Workflow' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="appDetail.status === 1 ? 'success' : 'danger'">
            {{ appDetail.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Dify API Key">{{ appDetail.appId }}</el-descriptions-item>
        <el-descriptions-item label="API Base URL">{{ appDetail.apiBaseUrl || '使用默认' }}</el-descriptions-item>
        <el-descriptions-item label="流式响应">
          <el-tag :type="appDetail.streamEnabled ? 'success' : 'info'">
            {{ appDetail.streamEnabled ? '支持' : '不支持' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="租户编号">{{ appDetail.tenantId }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(appDetail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDate(appDetail.updateTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getAppDetail } from '@/api/aiApp'
import AppIcon from '@/components/AppIcon.vue'
import { useAppNavigation } from '@/composables/useAppNavigation'

const route = useRoute()
const router = useRouter()
const { navigateToAppById } = useAppNavigation()
const appDetail = ref(null)
const loading = ref(false)

const fetchAppDetail = async () => {
  loading.value = true
  try {
    const res = await getAppDetail(route.params.id)
    appDetail.value = res
  } catch (error) {
    ElMessage.error('获取应用详情失败')
  } finally {
    loading.value = false
  }
}

const handleEdit = () => {
  router.push(`/admin/apps/edit/${route.params.id}`)
}

const handleUse = () => {
  navigateToAppById(route.params.id, appDetail.value.type)
}

const handleBack = () => {
  router.push('/admin/apps')
}

const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchAppDetail()
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.app-detail {
  width: 100%;
  padding: var(--spacing-lg);
  background: var(--color-bg-secondary);
}

/* ========== 卡片样式 ========== */
:deep(.el-card) {
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  border: 1px solid var(--color-border-lighter);
  transition: box-shadow var(--transition-base);
}

:deep(.el-card:hover) {
  box-shadow: var(--card-shadow-hover);
}

:deep(.el-card__header) {
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-lighter);
  padding: var(--spacing-md) var(--card-padding);
}

/* ========== 卡片头部 ========== */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.header-left span {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.header-left .app-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xs);
  transition: all var(--transition-base);
}

.header-left .app-icon:hover {
  background: var(--color-bg-hover);
  transform: scale(1.05);
}

.card-header .el-button {
  transition: all var(--transition-base);
}

.card-header .el-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

/* ========== 描述列表样式 ========== */
:deep(.el-descriptions) {
  background: var(--color-bg-primary);
}

:deep(.el-descriptions__header) {
  margin-bottom: var(--spacing-md);
}

:deep(.el-descriptions__title) {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

:deep(.el-descriptions__label) {
  color: var(--color-text-regular);
  font-weight: var(--font-weight-medium);
  background: var(--color-bg-tertiary);
}

:deep(.el-descriptions__content) {
  color: var(--color-text-primary);
}

:deep(.el-descriptions__table) {
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  overflow: hidden;
}

:deep(.el-descriptions__table td),
:deep(.el-descriptions__table th) {
  border-color: var(--color-border-lighter);
}

/* ========== 标签样式 ========== */
:deep(.el-tag) {
  border-radius: var(--radius-sm);
  font-weight: var(--font-weight-normal);
  border: none;
  padding: var(--spacing-xs) var(--spacing-sm);
}

/* ========== 响应式设计 ========== */
@media (max-width: 768px) {
  .app-detail {
    padding: var(--spacing-md);
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-md);
  }

  .card-header > div:last-child {
    width: 100%;
    display: flex;
    gap: var(--spacing-sm);
  }

  .card-header .el-button {
    flex: 1;
  }
}
</style>

