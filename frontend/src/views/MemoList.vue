<template>
  <div class="memo-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>备忘录</span>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            添加备忘录
          </el-button>
        </div>
      </template>

      <div class="create-tip">
        支持自然语言，例如：三分钟后提醒我喝水、每40分钟提醒我喝水、30分钟后开会、明天9点提醒吃药
      </div>

      <div class="table-container">
        <el-table :data="memos" v-loading="loading" stripe border style="width: 100%">
          <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="content-cell">{{ row.content }}</span>
            </template>
          </el-table-column>
          <el-table-column label="周期" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.intervalMinutes" class="interval-cell">{{ formatInterval(row.intervalMinutes) }}</span>
              <span v-else class="interval-none">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="remindAt" label="下次提醒" width="170" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="time-cell">{{ formatTime(row.remindAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'pending'" type="warning" size="small">待提醒</el-tag>
              <el-tag v-else-if="row.status === 'done'" type="success" size="small">已提醒</el-tag>
              <el-tag v-else type="info" size="small">已取消</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'pending'">
                <ElTooltip content="标记已提醒">
                  <el-button size="small" type="success" @click="handleMarkDone(row.id)">标记</el-button>
                </ElTooltip>
                <el-dropdown @command="(cmd) => handleMoreCommand(cmd, row)" trigger="click">
                  <el-button size="small">
                    更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="cancel">取消</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
              <template v-else>
                <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
              </template>
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">
              <p>暂无备忘录</p>
              <el-button type="primary" plain @click="showCreateDialog">添加第一条</el-button>
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
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate" :loading="saving">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElTooltip } from 'element-plus'
import { Plus, ArrowDown } from '@element-plus/icons-vue'
import {
  getMemos,
  createMemo,
  markMemoDone,
  cancelMemo,
  deleteMemo
} from '@/api/memo'

const memos = ref([])
const loading = ref(false)
const createVisible = ref(false)
const saving = ref(false)
const createFormRef = ref(null)
const createForm = ref({ rawInput: '' })
const createRules = {
  rawInput: [{ required: true, message: '请输入提醒内容', trigger: 'blur' }]
}

onMounted(() => {
  loadMemos()
})

async function loadMemos() {
  loading.value = true
  try {
    const data = await getMemos({ page: 0, size: 100 })
    memos.value = data || []
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

function handleMoreCommand(cmd, row) {
  if (cmd === 'cancel') handleCancel(row.id)
  else if (cmd === 'delete') handleDelete(row.id)
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
  return new Date(t).toLocaleString('zh-CN')
}

function formatInterval(minutes) {
  if (minutes == null || minutes < 1) return ''
  if (minutes < 60) return `每${minutes}分钟`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (m === 0) return `每${h}小时`
  return `每${h}小时${m}分`
}
</script>

<style scoped>
/* ========== 页面容器 ========== */
.memo-list {
  padding: var(--spacing-lg, 20px);
  background: var(--color-bg-secondary, #f5f7fa);
}

/* ========== 卡片样式 ========== */
:deep(.el-card) {
  border-radius: var(--card-border-radius, 12px);
  box-shadow: var(--card-shadow, 0 2px 12px rgba(0, 0, 0, 0.08));
  border: 1px solid var(--color-border-lighter, #ebeef5);
  transition: box-shadow var(--transition-base, 0.2s ease);
}

:deep(.el-card:hover) {
  box-shadow: var(--card-shadow-hover, 0 4px 16px rgba(0, 0, 0, 0.12));
}

:deep(.el-card__header) {
  background: var(--color-bg-tertiary, #fafafa);
  border-bottom: 1px solid var(--color-border-lighter, #ebeef5);
  padding: var(--spacing-md, 16px) var(--card-padding, 20px);
}

:deep(.el-card__body) {
  background: var(--color-bg-primary, #fff);
  padding: var(--card-padding, 20px);
}

/* ========== 卡片头部 ========== */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header span {
  font-size: var(--font-size-lg, 16px);
  font-weight: var(--font-weight-semibold, 600);
  color: var(--color-text-primary, #303133);
}

.card-header .el-button {
  transition: all var(--transition-base, 0.2s ease);
}

.card-header .el-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-primary, 0 2px 8px rgba(64, 158, 255, 0.3));
}

/* ========== 说明提示 ========== */
.create-tip {
  margin-bottom: var(--spacing-lg, 20px);
  padding: var(--spacing-sm, 12px) var(--spacing-md, 16px);
  font-size: var(--font-size-sm, 13px);
  color: var(--color-text-secondary, #909399);
  background: var(--color-bg-tertiary, #fafafa);
  border-radius: var(--radius-md, 8px);
  border: 1px solid var(--color-border-lighter, #ebeef5);
}

/* ========== 表格容器 ========== */
.table-container {
  margin-top: var(--spacing-md, 16px);
  width: 100%;
  overflow-x: auto;
}

:deep(.el-table) {
  width: 100%;
  border-radius: var(--radius-lg, 10px);
  overflow: hidden;
  background: var(--color-bg-primary, #fff);
}

:deep(.el-table__header) {
  background: var(--table-header-bg, #f5f7fa);
}

:deep(.el-table th) {
  background: var(--table-header-bg, #f5f7fa);
  color: var(--color-text-primary, #303133);
  font-weight: var(--font-weight-medium, 500);
  border-bottom: 2px solid var(--color-border-base, #dcdfe6);
}

:deep(.el-table td) {
  border-bottom: 1px solid var(--table-border-color, #ebeef5);
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background-color: var(--color-bg-tertiary, #fafafa);
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--table-row-hover-bg, #f5f7fa);
  transition: background-color var(--transition-fast, 0.15s ease);
}

/* 内容列：单行截断，超出用 tooltip 展示 */
.content-cell {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-regular, #606266);
  line-height: 1.5;
}

.time-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-regular, #606266);
}

.interval-cell {
  font-size: var(--font-size-sm, 13px);
  color: var(--color-primary, #409eff);
}

.interval-none {
  color: var(--color-text-placeholder, #c0c4cc);
}

/* ========== 空状态 ========== */
.table-empty {
  padding: var(--spacing-xl, 32px) 0;
  text-align: center;
}

.table-empty p {
  margin: 0 0 var(--spacing-md, 16px);
  font-size: var(--font-size-base, 14px);
  color: var(--color-text-secondary, #909399);
}

.table-empty .el-button {
  margin-top: var(--spacing-xs, 8px);
}
</style>
