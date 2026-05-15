<template>
  <div class="app-list" :class="{ 'is-embedded': embedded }">
    <el-card>
      <template v-if="!embedded" #header>
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

defineProps({
  embedded: {
    type: Boolean,
    default: false
  }
})

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
/* ========== 页面容器 ========== */
.app-list {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: var(--spacing-lg);
  background: var(--color-bg-secondary);
}

.app-list.is-embedded {
  padding: 0;
  background: transparent;
}

/* ========== 卡片样式 ========== */
:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  transition: box-shadow var(--transition-base);
}

:deep(.el-card:hover) {
  box-shadow: var(--card-shadow-hover);
}

:deep(.el-card__header) {
  padding: var(--spacing-md) var(--card-padding);
  border-bottom: 1px solid var(--color-border-lighter);
  background: var(--color-bg-tertiary);
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  min-height: 0;
  background: var(--color-bg-primary);
}

/* ========== 卡片头部 ========== */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.card-header span {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

/* ========== 搜索栏 ========== */
.search-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-lg);
  flex-shrink: 0;
  padding: var(--spacing-lg);
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-lighter);
  gap: var(--spacing-md);
}

.search-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex: 1;
}

.search-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* 搜索输入框样式增强 */
.search-left :deep(.el-input__wrapper) {
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.search-left :deep(.el-input__wrapper:hover) {
  box-shadow: var(--shadow-xs);
}

.search-left :deep(.el-input__wrapper.is-focus) {
  box-shadow: var(--shadow-primary);
}

/* 选择器样式 */
.search-left :deep(.el-select) {
  transition: all var(--transition-base);
}

.search-left :deep(.el-select:hover .el-input__wrapper) {
  box-shadow: var(--shadow-xs);
}

/* ========== 创建按钮 ========== */
.create-app-btn {
  font-weight: var(--font-weight-medium);
  border-radius: var(--radius-md);
  height: 40px;
  padding: 0 var(--spacing-lg);
  transition: all var(--transition-base);
  box-shadow: var(--shadow-xs);
}

.create-app-btn:hover {
  box-shadow: var(--shadow-primary);
  transform: translateY(-1px);
}

.create-app-btn:active {
  transform: translateY(0);
}

/* ========== 表格容器 ========== */
.table-container {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0 var(--spacing-lg);
}

/* ========== 表格样式增强 ========== */
:deep(.el-table) {
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--color-bg-primary);
}

:deep(.el-table__header) {
  background: var(--table-header-bg);
}

:deep(.el-table th) {
  background: var(--table-header-bg);
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
  border-bottom: 2px solid var(--color-border-base);
  padding: var(--spacing-md) 0;
}

:deep(.el-table td) {
  border-bottom: 1px solid var(--table-border-color);
  padding: var(--spacing-md) 0;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background-color: var(--color-bg-tertiary);
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--table-row-hover-bg);
  transition: background-color var(--transition-fast);
}

/* 减少应用名称和描述列之间的间距 */
:deep(.el-table .el-table__cell) {
  padding: var(--spacing-sm) 0;
}

:deep(.el-table .el-table__cell:nth-child(3)) {
  padding-right: var(--spacing-sm);
}

:deep(.el-table .el-table__cell:nth-child(4)) {
  padding-left: var(--spacing-sm);
}

/* ========== 操作按钮 ========== */
.row-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

:deep(.row-action-btn.el-button) {
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
  font-weight: var(--font-weight-normal);
}

:deep(.row-action-btn.el-button:hover) {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

:deep(.row-action-btn.el-button:active) {
  transform: translateY(0);
}

/* ========== 分页 ========== */
.pagination {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-lg);
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
  background: var(--color-bg-tertiary);
  border-top: 1px solid var(--color-border-lighter);
}

:deep(.el-pagination) {
  justify-content: flex-end;
}

:deep(.el-pagination .el-pager li) {
  border-radius: var(--radius-sm);
  transition: all var(--transition-base);
}

:deep(.el-pagination .el-pager li:hover) {
  background-color: var(--color-bg-hover);
}

:deep(.el-pagination .el-pager li.is-active) {
  background-color: var(--color-primary);
  color: #ffffff;
}

/* ========== 标签样式 ========== */
:deep(.el-tag) {
  border-radius: var(--radius-sm);
  font-weight: var(--font-weight-normal);
  border: none;
  padding: var(--spacing-xs) var(--spacing-sm);
}

/* ========== 响应式设计 ========== */
@media (max-width: 1024px) {
  .app-list {
    padding: var(--spacing-md);
  }

  .search-bar {
    flex-direction: column;
    align-items: stretch;
    gap: var(--spacing-sm);
  }

  .search-left {
    flex-direction: column;
    align-items: stretch;
  }

  .search-left :deep(.el-input),
  .search-left :deep(.el-select) {
    width: 100% !important;
  }

  .row-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .row-actions :deep(.el-button) {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .app-list {
    padding: var(--spacing-sm);
  }

  .search-bar {
    padding: var(--spacing-md);
  }

  .table-container {
    padding: 0 var(--spacing-sm);
  }

  .pagination {
    padding: var(--spacing-md);
  }

  :deep(.el-pagination) {
    flex-wrap: wrap;
  }
}
</style>

