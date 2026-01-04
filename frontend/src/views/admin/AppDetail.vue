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

const route = useRoute()
const router = useRouter()
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
  if (appDetail.value.type === 1) {
    router.push(`/app/chat/${route.params.id}`)
  } else {
    router.push(`/app/workflow/${route.params.id}`)
  }
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
.app-detail {
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left .app-icon {
  flex-shrink: 0;
}
</style>

