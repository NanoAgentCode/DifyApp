<template>
  <div class="memo-list">
    <el-card>
      <div class="memo-toolbar">
        <div class="toolbar-main">
          <el-radio-group v-model="statusFilter" size="large">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="pending">待提醒</el-radio-button>
            <el-radio-button label="done">已提醒</el-radio-button>
            <el-radio-button label="cancelled">已取消</el-radio-button>
          </el-radio-group>
          <el-button :icon="Refresh" :loading="loading" @click="loadMemos">刷新</el-button>
        </div>
        <div class="toolbar-actions">
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            添加备忘录
          </el-button>
        </div>
      </div>

      <div class="memo-overview">
        <div class="overview-item is-primary">
          <div class="overview-icon">
            <el-icon><Bell /></el-icon>
          </div>
          <div>
            <div class="overview-value">{{ memoStats.pending }}</div>
            <div class="overview-label">待提醒</div>
          </div>
        </div>
        <div class="overview-item">
          <div class="overview-icon">
            <el-icon><Clock /></el-icon>
          </div>
          <div>
            <div class="overview-value">{{ nextMemoTime }}</div>
            <div class="overview-label">下次提醒</div>
          </div>
        </div>
        <div class="overview-item">
          <div class="overview-icon">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div>
            <div class="overview-value">{{ memoStats.done }}</div>
            <div class="overview-label">已提醒</div>
          </div>
        </div>
        <div class="overview-item">
          <div class="overview-icon">
            <el-icon><CircleClose /></el-icon>
          </div>
          <div>
            <div class="overview-value">{{ memoStats.cancelled }}</div>
            <div class="overview-label">已取消</div>
          </div>
        </div>
      </div>

      <div class="create-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>支持自然语言创建提醒，例如：三分钟后提醒我喝水、每40分钟提醒我喝水、明天9点提醒吃药。</span>
      </div>

      <div class="table-container">
        <el-table
          :data="filteredMemos"
          v-loading="loading"
          stripe
          style="width: 100%"
          :row-class-name="getRowClassName"
        >
          <el-table-column prop="content" label="提醒内容" min-width="320" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="content-cell">
                <el-icon class="content-icon"><Bell /></el-icon>
                <span>{{ row.content }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="提醒方式" width="120" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.intervalMinutes" type="primary" effect="plain" size="small">
                {{ formatInterval(row.intervalMinutes) }}
              </el-tag>
              <el-tag v-else type="info" effect="plain" size="small">单次</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remindAt" label="下次提醒" width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="time-cell">
                <span>{{ formatTime(row.remindAt) }}</span>
                <small v-if="row.status === 'pending'">{{ formatRelativeTime(row.remindAt) }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="getStatusTagType(row.status)" size="small">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="136" align="center" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <template v-if="row.status === 'pending'">
                  <ElTooltip content="标记已提醒" placement="top">
                    <el-button :icon="Check" circle size="small" type="success" plain @click="handleMarkDone(row.id)" />
                  </ElTooltip>
                  <ElTooltip content="取消提醒" placement="top">
                    <el-button :icon="Close" circle size="small" type="warning" plain @click="handleCancel(row.id)" />
                  </ElTooltip>
                </template>
                <ElTooltip content="删除" placement="top">
                  <el-button :icon="Delete" circle size="small" type="danger" plain @click="handleDelete(row.id)" />
                </ElTooltip>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">
              <el-icon><Bell /></el-icon>
              <p>暂无备忘录</p>
              <el-button type="primary" plain @click="showCreateDialog">添加第一条提醒</el-button>
            </div>
          </template>
        </el-table>
      </div>
    </el-card>

    <el-dialog
      v-model="createVisible"
      title="添加备忘录"
      width="520px"
      :close-on-click-modal="false"
      class="memo-dialog"
    >
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="0">
        <el-form-item prop="rawInput">
          <el-input
            v-model="createForm.rawInput"
            type="textarea"
            :rows="3"
            placeholder="例如：三分钟后提醒我喝水、每40分钟提醒我喝水、明天早上9点提醒吃药"
          />
        </el-form-item>
      </el-form>
      <div class="dialog-examples">
        <span>示例</span>
        <el-tag
          v-for="example in memoExamples"
          :key="example"
          size="small"
          effect="plain"
          @click="createForm.rawInput = example"
        >
          {{ example }}
        </el-tag>
      </div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="saving">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElTooltip } from 'element-plus'
import { Bell, Check, CircleCheck, CircleClose, Clock, Close, Delete, InfoFilled, Plus, Refresh } from '@element-plus/icons-vue'
import {
  getMemos,
  createMemo,
  markMemoDone,
  cancelMemo,
  deleteMemo
} from '@/api/memo'

const memos = ref([])
const loading = ref(false)
const statusFilter = ref('all')
const createVisible = ref(false)
const saving = ref(false)
const createFormRef = ref(null)
const createForm = ref({ rawInput: '' })
const memoExamples = ['三分钟后提醒我喝水', '每40分钟提醒我活动一下', '明天9点提醒吃药']
const createRules = {
  rawInput: [{ required: true, message: '请输入提醒内容', trigger: 'blur' }]
}

const filteredMemos = computed(() => {
  if (statusFilter.value === 'all') return memos.value
  return memos.value.filter(memo => memo.status === statusFilter.value)
})

const memoStats = computed(() => {
  return memos.value.reduce((stats, memo) => {
    if (memo.status === 'pending') stats.pending += 1
    else if (memo.status === 'done') stats.done += 1
    else stats.cancelled += 1
    return stats
  }, { pending: 0, done: 0, cancelled: 0 })
})

const nextMemoTime = computed(() => {
  const pending = memos.value
    .filter(memo => memo.status === 'pending' && memo.remindAt)
    .sort((a, b) => new Date(a.remindAt) - new Date(b.remindAt))
  return pending.length ? formatRelativeTime(pending[0].remindAt) : '暂无'
})

onMounted(() => {
  loadMemos()
})

async function loadMemos() {
  loading.value = true
  try {
    const data = await getMemos({ page: 0, size: 100 })
    memos.value = Array.isArray(data) ? data : (data?.content || [])
  } catch (e) {
    console.error('加载备忘录失败', e)
    ElMessage.error(e.response?.data?.message || e.message || '加载备忘录失败')
    memos.value = []
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  createForm.value = { rawInput: '' }
  createVisible.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await createMemo({ rawInput: createForm.value.rawInput.trim() })
    ElMessage.success('已添加备忘录')
    createVisible.value = false
    loadMemos()
  } catch (e) {
    const msg = e.response?.data?.message || e.message || '添加失败'
    ElMessage.error(msg)
  } finally {
    saving.value = false
  }
}

async function handleMarkDone(id) {
  try {
    await markMemoDone(id)
    ElMessage.success('已标记')
    loadMemos()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '操作失败')
  }
}

async function handleCancel(id) {
  try {
    await ElMessageBox.confirm('确定取消该备忘录？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelMemo(id)
    ElMessage.success('已取消')
    loadMemos()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || e.message || '操作失败')
  }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除该备忘录？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteMemo(id)
    ElMessage.success('已删除')
    loadMemos()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || e.message || '操作失败')
  }
}

function formatTime(t) {
  if (!t) return ''
  const date = new Date(t)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatRelativeTime(t) {
  if (!t) return ''
  const date = new Date(t)
  if (Number.isNaN(date.getTime())) return ''
  const diff = date.getTime() - Date.now()
  const abs = Math.abs(diff)
  const minutes = Math.round(abs / 60000)
  if (minutes < 1) return diff >= 0 ? '即将提醒' : '刚刚到期'
  if (minutes < 60) return diff >= 0 ? `${minutes}分钟后` : `已过${minutes}分钟`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return diff >= 0 ? `${hours}小时后` : `已过${hours}小时`
  const days = Math.round(hours / 24)
  return diff >= 0 ? `${days}天后` : `已过${days}天`
}

function formatInterval(minutes) {
  if (minutes == null || minutes < 1) return ''
  if (minutes < 60) return `每${minutes}分钟`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (m === 0) return `每${h}小时`
  return `每${h}小时${m}分`
}

function getStatusText(status) {
  if (status === 'pending') return '待提醒'
  if (status === 'done') return '已提醒'
  return '已取消'
}

function getStatusTagType(status) {
  if (status === 'pending') return 'warning'
  if (status === 'done') return 'success'
  return 'info'
}

function getRowClassName({ row }) {
  return row.status ? `memo-row-${row.status}` : ''
}
</script>

<style scoped>
.memo-list {
  height: 100%;
  min-height: 0;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-secondary);
}

:deep(.el-card) {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  border: 1px solid var(--color-border-lighter);
}

:deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
  background: var(--color-bg-primary);
}

.memo-toolbar {
  flex-shrink: 0;
  padding: var(--spacing-lg);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  background: var(--color-bg-primary);
  border-bottom: 1px solid var(--color-border-lighter);
}

.toolbar-main,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  min-width: 0;
}

.memo-overview {
  flex-shrink: 0;
  padding: var(--spacing-lg);
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--spacing-md);
  background: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border-lighter);
}

.overview-item {
  min-width: 0;
  padding: var(--spacing-md);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

.overview-item.is-primary {
  border-color: var(--color-primary-light-7);
  background: var(--color-primary-light-9);
}

.overview-icon {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  color: var(--color-primary);
  background: var(--color-primary-light-9);
  font-size: 18px;
}

.overview-value {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-xl);
  line-height: var(--line-height-tight);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.overview-label {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.create-tip {
  flex-shrink: 0;
  margin: var(--spacing-lg) var(--spacing-lg) 0;
  padding: var(--spacing-sm) var(--spacing-md);
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
}

.table-container {
  flex: 1;
  min-height: 0;
  padding: var(--spacing-lg);
  overflow: auto;
}

:deep(.el-table) {
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  box-shadow: var(--shadow-xs);
}

:deep(.el-table__inner-wrapper::before) {
  display: none;
}

:deep(.el-table th) {
  background: var(--table-header-bg);
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
}

:deep(.el-table th .cell) {
  height: 40px;
  display: flex;
  align-items: center;
  padding: 0 14px;
}

:deep(.el-table td) {
  padding: 10px 0;
}

:deep(.el-table .cell) {
  padding: 0 14px;
}

:deep(.el-table__body tr:hover > td) {
  background: var(--table-row-hover-bg);
}

:deep(.memo-row-done td),
:deep(.memo-row-cancelled td) {
  color: var(--color-text-secondary);
}

.content-cell {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--color-text-regular);
  font-weight: var(--font-weight-medium);
}

.content-cell span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content-icon {
  flex-shrink: 0;
  color: var(--color-primary);
  font-size: 15px;
}

.time-cell {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--color-text-regular);
  line-height: var(--line-height-tight);
}

.time-cell span,
.time-cell small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-cell small {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: nowrap;
}

.row-actions :deep(.el-button) {
  margin-left: 0;
}

.table-empty {
  padding: var(--spacing-xl) 0;
  text-align: center;
  color: var(--color-text-secondary);
}

.table-empty .el-icon {
  font-size: 38px;
  color: var(--color-text-placeholder);
}

.table-empty p {
  margin: var(--spacing-sm) 0 var(--spacing-md);
  font-size: var(--font-size-base);
}

.dialog-examples {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.dialog-examples .el-tag {
  cursor: pointer;
}

:deep(.memo-dialog .el-dialog__body) {
  padding-bottom: var(--spacing-md);
}

@media (max-width: 900px) {
  .memo-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-main,
  .toolbar-actions {
    width: 100%;
    justify-content: space-between;
  }

  .memo-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .memo-list {
    padding: var(--spacing-md);
  }

  .memo-overview {
    grid-template-columns: 1fr;
  }
}
</style>
