<template>
  <div class="user-action-log">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户行为日志</span>
        </div>
      </template>
      
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchUsername"
          placeholder="搜索用户名"
          clearable
          style="width: 200px"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        
        <el-select
          v-model="filterModule"
          placeholder="操作模块"
          clearable
          style="width: 150px; margin-left: 10px"
         @change="handleSearch"
        >
          <el-option label="全部模块" value="" />
          <el-option
            v-for="m in moduleOptions"
            :key="m"
            :label="m"
            :value="m"
          />
        </el-select>
        
        <el-select
          v-model="filterActionType"
          placeholder="操作类型"
          clearable
          style="width: 150px; margin-left: 10px"
          @change="handleSearch"
        >
        <el-option label="全部类型" value="" />
        <el-option
            v-for="actionType in actionTypeOptions"
            :key="actionType"
            :label="actionType"
            :value="actionType"
          />
        </el-select>        
        <el-select
          v-model="filterResult"
          placeholder="执行结果"
          clearable
          style="width: 150px; margin-left: 10px"
          @change="handleSearch"
        >
          <el-option label="全部结果" value="" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILURE" />
        </el-select>
        
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 100px; margin-left: 10px"
          @change="handleSearch"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
        
        <el-button
          type="primary"
          style="margin-left: 10px"
          @click="handleSearch"
        >
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        
        <el-button
          style="margin-left: 10px"
          @click="handleReset"
        >
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
      </div>
      
      <!-- 简化的表格 -->
      <div class="table-wrapper">
        <el-table
          :data="logList"
          v-loading="loading"
          style="width: 100%"
          border
          stripe
          fit
        >
          <el-table-column prop="username" label="用户名" min-width="100" show-overflow-tooltip align="center"/>
          <el-table-column prop="module" label="操作模块" min-width="100" align="center">
            <template #default="scope">
              <el-tag :style="getModuleStyle(scope.row.module)" size="small">
                {{ scope.row.module }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="description" label="操作描述" min-width="180" show-overflow-tooltip />
          <el-table-column prop="requestPath" label="请求路径" min-width="180" show-overflow-tooltip />
          <el-table-column prop="actionType" label="操作类型" min-width="100" align="center">
            <template #default="scope">
              <el-tag :style="getActionTypeStyle(scope.row.actionType)" size="small">
                {{ scope.row.actionType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="result" label="执行结果" width="100" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
                {{ scope.row.result === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="executionTime" label="执行时长" width="120" align="center">
            <template #default="scope">
              <span :class="getExecutionTimeClass(scope.row.executionTime)">
                {{ scope.row.executionTime }}ms
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="操作时间" width="220" align="center">
            <template #default="scope">
              {{ formatDate(scope.row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" align="center" fixed="right">
            <template #default="scope">
              <el-button
                type="primary"
                size="small"
                @click="handleViewDetail(scope.row)"
              >
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      
      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
    
    <!-- 详情对话框 -->
    <el-dialog
      v-model="showDetailDialog"
      title="日志详情"
      width="700px"
      :close-on-click-modal="false"
    >
      <div v-if="currentLog" class="log-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户名">{{ currentLog.username }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ currentLog.userId || '未知' }}</el-descriptions-item>
          <el-descriptions-item label="操作模块">
            <el-tag :style="getModuleStyle(currentLog.module)" size="small">
              {{ currentLog.module }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作类型">
            <el-tag :style="getActionTypeStyle(currentLog.actionType)" size="small">
              {{ currentLog.actionType }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="执行结果">
            <el-tag :type="currentLog.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ currentLog.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="执行时长">
            <span :class="getExecutionTimeClass(currentLog.executionTime)">
              {{ currentLog.executionTime }} ms
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="操作描述" :span="2">{{ currentLog.description || '无' }}</el-descriptions-item>
          <el-descriptions-item label="请求方法">
            <el-tag :type="getMethodTag(currentLog.method)" size="small">
              {{ currentLog.method }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="请求路径">{{ currentLog.requestPath }}</el-descriptions-item>
          <el-descriptions-item label="IP地址">{{ currentLog.ipAddress }}</el-descriptions-item>
          <el-descriptions-item label="操作时间">{{ formatDate(currentLog.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="请求参数" :span="2">
            <div class="code-block">
              <pre>{{ formatJson(currentLog.requestParams) }}</pre>
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentLog.errorMsg" label="异常信息" :span="2">
            <div class="error-msg">
              {{ currentLog.errorMsg }}
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="User Agent" :span="2">
            <div class="user-agent">{{ currentLog.userAgent || '未知' }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshLeft } from '@element-plus/icons-vue'
import { getUserActionLogs, getUserActionLogActionTypes, getUserActionLogModules } from '@/api/userActionLog'

const loading = ref(false)
const logList = ref([])
const showDetailDialog = ref(false)
const currentLog = ref(null)
const searchUsername = ref('')
const filterModule = ref('')
const filterActionType = ref('')
const filterResult = ref('')
const dateRange = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const actionTypeOptions = ref([])
const moduleOptions = ref([])

const loadLogs = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    
    if (searchUsername.value) {
      params.username = searchUsername.value
    }
    if (filterModule.value) {
      params.module = filterModule.value
    }
    if (filterActionType.value) {
      params.actionType = filterActionType.value
    }
    if (filterResult.value) {
      params.result = filterResult.value
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }
    
    const response = await getUserActionLogs(params)
    
    // 检查是否是分页响应
    if (response && typeof response === 'object' && 'content' in response && 'total' in response) {
      logList.value = response.content || []
      total.value = response.total || 0
    } else {
      logList.value = Array.isArray(response) ? response : []
      total.value = logList.value.length
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '获取日志列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadLogs()
}

const handleReset = () => {
  searchUsername.value = ''
  filterModule.value = ''
  filterActionType.value = ''
  filterResult.value = ''
  dateRange.value = []
  currentPage.value = 1
  loadLogs()
}

const loadActionTypes = async () => {
  try {
    const types = await getUserActionLogActionTypes()
    if (Array.isArray(types)) {
      actionTypeOptions.value = types
    } else {
      actionTypeOptions.value = []
    }
  } catch (error) {
    actionTypeOptions.value = []
  }
}

const loadModules = async () => {
  try {
    const modules = await getUserActionLogModules()
    moduleOptions.value = Array.isArray(modules) ? modules : []
  } catch (error) {
    moduleOptions.value = []
  }
}

const handleViewDetail = (log) => {
  currentLog.value = log
  showDetailDialog.value = true
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadLogs()
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadLogs()
}

const actionTypeColorMap = {
  '创建': '#22C55E',
  '更新': '#FACC15',
  '删除': '#3B82F6',
  '查询': '#60A5FA',
  '用户登录': '#10B981',
  '登出': '#38BDF8',
  '禁用用户': '#F59E0B',
  '启用用户': '#34D399',
  'Workflow执行': '#0EA5E9',
  '导入': '#14B8A6',
  '导出': '#FDE047',
  '审核': '#FFD166',
  '配置变更': '#6366F1'
}
const hexToRgb = (hex) => {
  const h = hex.replace('#','')
  const bigint = parseInt(h,16)
  const r = (bigint >> 16) & 255
  const g = (bigint >> 8) & 255
  const b = bigint & 255
  return { r, g, b }
}
const getActionTypeStyle = (actionType) => {
  const bg = actionTypeColorMap[actionType] || '#3B82F6'
  const { r, g, b } = hexToRgb(bg)
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  const text = lum > 0.7 ? '#303133' : '#fff'
  return { backgroundColor: bg, borderColor: bg, color: text }
}

const getMethodTag = (method) => {
  const tagMap = {
    'GET': 'info',
    'POST': 'success',
    'PUT': 'warning',
    'DELETE': 'danger',
    'PATCH': 'warning'
  }
  return tagMap[method] || ''
}

const moduleColorMap = {
  '用户管理': '#3B82F6',
  'AI应用管理': '#22C55E',
  'AI应用': '#60A5FA',
  '智能问答': '#10B981',
  '对话管理': '#0EA5E9',
  '数据源管理': '#FACC15',
  '知识库管理': '#34D399',
  '文档管理': '#38BDF8',
  '系统配置': '#F59E0B',
  '模型管理': '#6366F1',
  '认证': '#2DD4BF'
}
const getModuleStyle = (module) => {
  const bg = moduleColorMap[module] || '#22C55E'
  const { r, g, b } = hexToRgb(bg)
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  const text = lum > 0.7 ? '#303133' : '#fff'
  return { backgroundColor: bg, borderColor: bg, color: text }
}

const getExecutionTimeClass = (time) => {
  if (time < 100) return 'time-fast'
  if (time < 500) return 'time-normal'
  return 'time-slow'
}

const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleString('zh-CN')
}

const formatJson = (jsonStr) => {
  if (!jsonStr) return '无'
  try {
    const obj = JSON.parse(jsonStr)
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return jsonStr
  }
}

onMounted(() => {
  loadActionTypes()
  loadModules()
  loadLogs()
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.user-action-log {
  padding: var(--spacing-lg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-secondary);
}

/* ========== 卡片样式 ========== */
:deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin: 0;
  overflow: hidden;
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  transition: box-shadow var(--transition-base);
}

:deep(.el-card:hover) {
  box-shadow: var(--card-shadow-hover);
}

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: var(--spacing-md) var(--card-padding);
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-lighter);
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: var(--card-padding);
  overflow: hidden;
  min-height: 0;
  background: var(--color-bg-primary);
}

/* ========== 卡片头部 ========== */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header span {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

/* ========== 搜索栏 ========== */
.search-bar {
  margin-bottom: var(--spacing-lg);
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-lighter);
  align-items: center;
}

.search-bar :deep(.el-input__wrapper),
.search-bar :deep(.el-select .el-input__wrapper) {
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.search-bar :deep(.el-input__wrapper:hover) {
  box-shadow: var(--shadow-xs);
}

.search-bar :deep(.el-input__wrapper.is-focus) {
  box-shadow: var(--shadow-primary);
}

.search-bar .el-button {
  transition: all var(--transition-base);
}

.search-bar .el-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

/* ========== 表格容器 ========== */
.table-wrapper {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

:deep(.el-table) {
  height: 100%;
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
}

:deep(.el-table td) {
  border-bottom: 1px solid var(--table-border-color);
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background-color: var(--color-bg-tertiary);
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--table-row-hover-bg);
  transition: background-color var(--transition-fast);
}

/* ========== 分页 ========== */
.pagination-wrapper {
  flex-shrink: 0;
  padding-top: var(--spacing-lg);
  padding: var(--spacing-md);
  display: flex;
  justify-content: flex-end;
  background: var(--color-bg-tertiary);
  border-top: 1px solid var(--color-border-lighter);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
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

/* ========== 日志详情 ========== */
.log-detail {
  max-height: 600px;
  overflow-y: auto;
}

.code-block {
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  padding: var(--spacing-sm);
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--color-border-lighter);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.code-block pre {
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #303133;
}

.error-msg {
  color: #f56c6c;
  background: #fef0f0;
  border-left: 4px solid #f56c6c;
  padding: 10px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
}

.user-agent {
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}

.time-fast {
  color: #67c23a;
  font-weight: 600;
}

.time-normal {
  color: #e6a23c;
  font-weight: 600;
}

.time-slow {
  color: #f56c6c;
  font-weight: 600;
}

:deep(.date-range-compact.el-date-editor--datetimerange) {
  width: 160px !important;
  min-width: 160px !important;
}
:deep(.date-range-compact.el-date-editor--datetimerange .el-range-input) {
  width: 64px !important;
}
:deep(.date-range-compact.el-date-editor--datetimerange .el-range-separator) {
  padding: 0 2px !important;
  width: 10px !important;
}
:deep(.date-range-compact.el-date-editor--datetimerange .el-range__icon),
:deep(.date-range-compact.el-date-editor--datetimerange .el-range__close-icon) {
  display: none !important;
}
</style>
