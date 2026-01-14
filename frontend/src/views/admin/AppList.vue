<template>
  <div class="app-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <el-button type="text" @click="handleBack" style="margin-right: 10px">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <span>应用列表</span>
        </div>
      </template>
      <!-- 搜索栏 -->
      <div class="search-bar">
        <div class="search-left">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索应用名称或描述"
            clearable
            style="width: 300px"
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select
            v-model="filterType"
            placeholder="筛选类型"
            clearable
            style="width: 150px"
            @change="handleFilter"
          >
            <el-option label="全部" value="" />
            <el-option label="Chat Flow" :value="1" />
            <el-option label="Workflow" :value="2" />
          </el-select>
          <el-select
            v-model="filterStatus"
            placeholder="筛选状态"
            clearable
            style="width: 150px"
            @change="handleFilter"
          >
            <el-option label="全部" value="" />
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </div>
        <div class="search-right">
          <el-button class="create-app-btn" type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            创建应用
          </el-button>
        </div>
      </div>

      <div class="table-container" style="padding: 0 20px;">
        <el-table 
          :data="appList" 
          v-loading="loading" 
          stripe
          :lazy="false"
          :row-key="row => row.id"
          :default-sort="{ prop: 'createTime', order: 'descending' }"
        >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图标" width="80" align="center">
          <template #default="{ row }">
            <AppIcon :icon="row.icon" :size="32" />
          </template>
        </el-table-column>
        <el-table-column label="应用名称" width="160">
          <template #default="{ row }">
            <span :title="row.name">{{ truncateName(row.name) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip min-width="200" />
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
            <div class="row-actions">
              <el-button class="row-action-btn" size="small" type="info" plain @click="handleDetail(row.id)">详情</el-button>
              <el-button class="row-action-btn" size="small" type="primary" plain @click="handleEdit(row.id)">编辑</el-button>
              <el-button class="row-action-btn" size="small" type="primary" @click="handleUse(row)">使用</el-button>
              <el-button class="row-action-btn" size="small" type="danger" plain @click="handleDelete(row.id)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        </el-table>
      </div>
      
      <!-- 分页 -->
      <div class="pagination" style="padding: 0 20px 20px 20px;">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100, 200]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, ArrowLeft } from '@element-plus/icons-vue'
import { getAppList, deleteApp } from '@/api/aiApp'
import AppIcon from '@/components/AppIcon.vue'
import { useAppNavigation } from '@/composables/useAppNavigation'

const router = useRouter()
const { navigateToApp } = useAppNavigation()
const appList = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterType = ref('')
const filterStatus = ref('')
const currentPage = ref(1)
const pageSize = ref(20) // 默认每页20条，提升性能
const total = ref(0)
let searchTimer = null // 搜索防抖定时器

const fetchAppList = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }
    if (filterType.value !== '') {
      params.type = filterType.value
    }
    if (filterStatus.value !== '') {
      params.status = filterStatus.value
    }
    const response = await getAppList(params)
    
    // 检查是否是分页响应
    if (response && typeof response === 'object' && 'content' in response && 'total' in response) {
      // 分页响应
      appList.value = response.content || []
      total.value = response.total || 0
    } else {
      // 兼容旧接口（非分页响应）
      appList.value = Array.isArray(response) ? response : []
      total.value = appList.value.length
    }
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
  navigateToApp(app)
}

const handleBack = () => {
  router.push('/admin/chat')
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

const truncateName = (name) => {
  if (!name) return ''
  if (name.length <= 10) return name
  return name.substring(0, 10) + '...'
}

// 重置到第一页并刷新列表
const resetAndFetch = () => {
  currentPage.value = 1
  fetchAppList()
}

// 搜索防抖处理
const handleSearch = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(resetAndFetch, 500)
}

const handleFilter = resetAndFetch

const handleSizeChange = (size) => {
  pageSize.value = size
  resetAndFetch()
}

const handlePageChange = (page) => {
  currentPage.value = page
  fetchAppList()
}

onMounted(() => {
  fetchAppList()
})

// 组件卸载时清理定时器
onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
})
</script>

<style scoped>
.app-list {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  min-height: 0;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-shrink: 0;
  padding: 20px 20px 0 20px;
}

.search-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-right {
  display: flex;
  align-items: center;
}

.create-app-btn {
  font-weight: 600;
  border-radius: 10px;
  height: 40px;
  padding: 0 16px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

:deep(.row-action-btn.el-button) {
  border-radius: 10px;
}

.table-container {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

/* 减少应用名称和描述列之间的间距 */
:deep(.el-table) {
  .el-table__cell {
    padding: 8px 0;
  }
  
  /* 应用名称列右侧间距 */
  .el-table__cell:nth-child(3) {
    padding-right: 8px;
  }
  
  /* 描述列左侧间距 */
  .el-table__cell:nth-child(4) {
    padding-left: 8px;
  }
}
</style>

