<template>
  <div class="app-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>应用列表</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            创建应用
          </el-button>
        </div>
      </template>

      <el-table :data="appList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图标" width="80" align="center">
          <template #default="{ row }">
            <AppIcon :icon="row.icon" :size="32" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="应用名称" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'success' : 'info'">
              {{ row.type === 1 ? 'Chat Flow' : 'Workflow' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="streamEnabled" label="流式响应" width="100">
          <template #default="{ row }">
            <el-tag :type="row.streamEnabled ? 'success' : 'info'">
              {{ row.streamEnabled ? '支持' : '不支持' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleDetail(row.id)">详情</el-button>
            <el-button size="small" type="primary" @click="handleEdit(row.id)">编辑</el-button>
            <el-button size="small" type="success" @click="handleUse(row)">使用</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getAppList, deleteApp } from '@/api/aiApp'
import AppIcon from '@/components/AppIcon.vue'

const router = useRouter()
const appList = ref([])
const loading = ref(false)

const fetchAppList = async () => {
  loading.value = true
  try {
    const res = await getAppList()
    appList.value = res || []
  } catch (error) {
    ElMessage.error('获取应用列表失败')
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  router.push('/admin/apps/create')
}

const handleEdit = (id) => {
  router.push(`/admin/apps/edit/${id}`)
}

const handleDetail = (id) => {
  router.push(`/admin/apps/detail/${id}`)
}

const handleUse = (app) => {
  if (app.type === 1) {
    router.push(`/app/chat/${app.id}`)
  } else {
    router.push(`/app/workflow/${app.id}`)
  }
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该应用吗？', '提示', {
      type: 'warning'
    })
    await deleteApp(id)
    ElMessage.success('删除成功')
    fetchAppList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchAppList()
})
</script>

<style scoped>
.app-list {
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

