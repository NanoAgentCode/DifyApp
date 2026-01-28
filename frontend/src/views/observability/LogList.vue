<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>日志监控</span>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-select
          v-model="form.model"
          placeholder="选择模型"
          clearable
          filterable
          style="width: 180px"
          @change="handleFilter"
        >
          <el-option
            v-for="item in modelOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>

        <el-select
          v-model="form.provider"
          placeholder="选择供应商"
          clearable
          filterable
          style="width: 180px; margin-left: 10px"
          @change="handleFilter"
        >
          <el-option
            v-for="item in providerOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>

        <el-select
          v-model="form.traceSource"
          placeholder="选择来源"
          clearable
          filterable
          style="width: 180px; margin-left: 10px"
          @change="handleFilter"
        >
          <el-option
            v-for="item in traceSourceOptions"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>

        <el-input
          v-model="form.conversationId"
          placeholder="会话 ID"
          clearable
          style="width: 200px; margin-left: 10px"
          @clear="handleFilter"
          @keyup.enter="handleFilter"
        />

        <el-date-picker
          v-model="form.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm:ss"
          style="width: 320px; margin-left: 10px"
          @change="handleFilter"
        />

        <el-button
          type="primary"
          style="margin-left: 10px"
          @click="handleFilter"
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

      <!-- 表格 -->
      <div class="table-wrapper">
        <el-table
          v-loading="loading"
          :data="tableData"
          style="width: 100%"
          border
          stripe
          fit
        >
          <el-table-column prop="traceId" label="链路ID" width="160" align="center" show-overflow-tooltip>
            <template #default="scope">
              <el-tooltip :content="scope.row.traceId" placement="top">
                <code class="trace-id">{{ formatId(scope.row.traceId) }}</code>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="spanId" label="调用ID" width="160" align="center" show-overflow-tooltip>
            <template #default="scope">
              <el-tooltip :content="scope.row.spanId || scope.row.esDocId" placement="top">
                <code class="span-id">{{ formatId(scope.row.spanId || scope.row.esDocId) }}</code>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="180" align="center">
            <template #default="scope">
              {{ formatTime(scope.row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="traceSource" label="来源" width="180" align="center">
            <template #default="scope">
              <el-tag 
                :style="getSourceTagStyle(scope.row.traceSource)" 
                size="small"
                class="source-tag"
              >
                {{ scope.row.traceSource || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="model" label="模型/供应商" min-width="180" align="center" show-overflow-tooltip>
            <template #default="scope">
              <div class="model-info">
                <span class="provider">{{ scope.row.provider }}</span>
                <span class="divider">/</span>
                <span class="model-name">{{ scope.row.model }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="latency" label="延迟" width="120" align="center">
             <template #default="scope">
              <span :class="getLatencyClass(scope.row.latency)" class="latency-cell">
                {{ scope.row.latency }}ms
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="totalTokens" label="Tokens" width="100" align="center">
            <template #default="scope">
              <div class="token-cell">
                {{ scope.row.totalTokens || 0 }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
                {{ scope.row.status === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="180" align="center">
            <template #default="scope">
              <el-button
                type="primary"
                size="small"
                @click="handleDetail(scope.row)"
              >
                详情
              </el-button>
              <el-button
                type="danger"
                size="small"
                @click="handleDelete(scope.row)"
              >
                删除
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
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <log-detail ref="detailRef" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listTraces, deleteTrace, getModels, getProviders, getTraceSources } from '@/api/observability'
import LogDetail from './LogDetail.vue'
import dayjs from 'dayjs'
import { Search, RefreshLeft, Clock, Timer, View } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const form = reactive({
  model: '',
  provider: '',
  conversationId: '',
  traceSource: '',
  timeRange: []
})

// 筛选选项列表（从 ES 聚合获取）
const modelOptions = ref([])
const providerOptions = ref([])
const traceSourceOptions = ref([])

const detailRef = ref(null)

const fetchData = async () => {
  loading.value = true
  const params = {
    page: currentPage.value,
    size: pageSize.value,
    model: form.model,
    provider: form.provider,
    traceSource: form.traceSource,
    conversationId: form.conversationId,
    startTime: form.timeRange?.[0],
    endTime: form.timeRange?.[1]
  }
  
  try {
    const res = await listTraces(params)
    if(res.success) {
      tableData.value = res.data.content
      total.value = res.data.total
    }
  } catch (error) {
    console.error('获取数据失败:', error)
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  currentPage.value = 1
  fetchData()
}

const handleReset = () => {
  form.model = ''
  form.provider = ''
  form.traceSource = ''
  form.conversationId = ''
  form.timeRange = []
  currentPage.value = 1
  fetchData()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  fetchData()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  fetchData()
}

const handleDetail = (row) => {
  // 使用 esDocId 作为唯一标识
  const id = row.esDocId
  if (!id) {
    ElMessage.warning('无法获取文档ID')
    return
  }
  detailRef.value.open(id)
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    '确认删除该条日志吗？删除后无法恢复。',
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(async () => {
      try {
        // 使用 esDocId 作为唯一标识
        const id = row.esDocId
        if (!id) {
          ElMessage.warning('无法获取文档ID')
          return
        }
        await deleteTrace(id)
        ElMessage.success('删除成功')
        fetchData()
      } catch (error) {
        console.error('删除失败:', error)
        ElMessage.error('删除失败')
      }
    })
    .catch(() => {})
}

const formatTime = (time) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

// 格式化 ID，只显示前8位
const formatId = (id) => {
  if (!id) return '-'
  return id.length > 8 ? id.substring(0, 8) + '...' : id
}

const getLatencyClass = (latency) => {
  if (latency < 1000) return 'time-fast'
  if (latency < 3000) return 'time-normal'
  return 'time-slow'
}

const sourceColorMap = {
  'Chat': '#3B82F6',
  'Knowledge Base QA': '#22C55E',
  'DrawIO Generate': '#FACC15',
  'Context Compression': '#60A5FA',
  'Memory Extraction': '#0EA5E9',
  'Dialog Summary': '#10B981'
}

const hexToRgb = (hex) => {
  if (!hex) return { r: 59, g: 130, b: 246 }
  const h = hex.replace('#','')
  const bigint = parseInt(h,16)
  const r = (bigint >> 16) & 255
  const g = (bigint >> 8) & 255
  const b = bigint & 255
  return { r, g, b }
}

const getSourceTagStyle = (source) => {
  const bg = sourceColorMap[source] || '#3B82F6'
  const { r, g, b } = hexToRgb(bg)
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
  const text = lum > 0.7 ? '#303133' : '#fff'
  return { backgroundColor: bg, borderColor: bg, color: text }
}

// 加载筛选选项（从 ES 聚合获取）
const loadFilterOptions = async () => {
  try {
    // 并行加载所有选项
    const [modelsRes, providersRes, traceSourcesRes] = await Promise.all([
      getModels(),
      getProviders(),
      getTraceSources()
    ])
    
    if (modelsRes.success) {
      modelOptions.value = modelsRes.data || []
    }
    if (providersRes.success) {
      providerOptions.value = providersRes.data || []
    }
    if (traceSourcesRes.success) {
      traceSourceOptions.value = traceSourcesRes.data || []
    }
  } catch (error) {
    console.error('加载筛选选项失败:', error)
  }
}

onMounted(() => {
  loadFilterOptions()
  fetchData()
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.app-container {
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

.trace-id, .span-id {
  font-family: 'Fira Code', 'Courier New', Courier, monospace;
  font-size: 11px;
  color: var(--color-text-secondary);
  background: var(--color-bg-secondary);
  padding: 2px 6px;
  border-radius: 4px;
  display: inline-block;
  max-width: 100%;
  cursor: pointer;
}

.trace-id:hover, .span-id:hover {
  background: var(--color-bg-hover);
}

.source-tag {
  font-weight: 500;
}

.model-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 13px;
}

.provider {
  color: var(--color-text-secondary);
  font-weight: 600;
  text-transform: capitalize;
}

.divider {
  color: var(--color-border-base);
}

.model-name {
  color: var(--color-text-primary);
  font-weight: 500;
}

.latency-cell {
  font-weight: 600;
}

.token-cell {
  font-weight: 500;
  color: var(--color-text-primary);
}

</style>
