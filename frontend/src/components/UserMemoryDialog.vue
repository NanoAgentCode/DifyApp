<template>
  <el-dialog
    v-model="visible"
    title="记忆管理"
    width="900px"
    class="memory-dialog"
    destroy-on-close
    @open="handleOpen"
  >
    <template #header>
      <div class="dialog-header">
        <div class="dialog-title">记忆管理</div>
        <div class="dialog-subtitle">{{ currentUserLabel }}</div>
      </div>
    </template>
    <div class="memory-dialog-body">
      <div class="memory-toolbar">
        <el-select v-model="scopeType" size="small" style="width: 140px" @change="handleScopeChange">
          <el-option label="所有范围" value="all" />
          <el-option label="智能对话" value="chat" />
          <el-option label="知识问答" value="knowledge_base" />
          <el-option label="智能应用" value="app" />
        </el-select>
        <el-select v-model="memoryType" size="small" style="width: 140px" @change="handleTypeChange">
          <el-option label="全部类型" value="all" />
          <el-option label="长期记忆" value="long_term" />
          <el-option label="实体记忆" value="entity" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索 Key / 内容"
          clearable
          style="width: 280px"
          @input="handleKeywordInput"
        />
        <el-button
          type="warning"
          plain
          @click="handleClearFilters"
        >
          重置
        </el-button>
                <el-button
          type="primary"
          plain
          :loading="loading"
          @click="loadMemory"
        >
          刷新
        </el-button>
        <el-button
          type="danger"
          plain
          :disabled="clearing"
          @click="handleClearMemory"
        >
          清空
        </el-button>

      </div>

      <div class="memory-table-wrapper">
        <el-table :data="pagedItems" v-loading="loading" border stripe style="width: 100%" height="100%">
          <el-table-column type="expand" width="44">
            <template #default="{ row }">
              <div class="expand-header">
                <div class="expand-title">
                  <el-tag :type="row.memoryType === 'entity' ? 'info' : 'success'" size="small">
                    {{ row.memoryType === 'entity' ? '实体' : '长期' }}
                  </el-tag>
                  <el-tag style="margin-left: 8px;" size="small">
                    {{ row.scopeType || 'chat' }}
                  </el-tag>
                  <span style="margin-left: 8px;">{{ row.memoryKey }}</span>
                </div>
                <el-button size="small" plain @click="copyMemory(row)">复制内容</el-button>
              </div>
              <div class="memory-meta">
                <span>首次记录：{{ formatDate(row.firstSeenTime) }}</span>
                <span>最近提及：{{ formatDate(row.lastMentionedTime) }}</span>
                <span>最近使用：{{ formatDate(row.lastAccessedTime) }}</span>
                <span>使用次数：{{ row.accessCount || 0 }}</span>
              </div>
              <pre class="memory-content">{{ formatMemoryContent(row) }}</pre>
            </template>
          </el-table-column>
          <el-table-column prop="scopeType" label="Scope" width="140" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ row.scopeType || 'chat' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="memoryType" label="类型" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.memoryType === 'entity' ? 'info' : 'success'" size="small">
                {{ row.memoryType === 'entity' ? '实体' : '长期' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="memoryKey" label="Key" min-width="160" show-overflow-tooltip />
          <el-table-column prop="importance" label="重要度" width="90" align="center" />
          <el-table-column prop="lastMentionedTime" label="最近提及" width="180" align="center">
            <template #default="{ row }">
              {{ formatDate(row.lastMentionedTime || row.updateTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="danger" plain :disabled="deleting" @click="handleDeleteRow(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="memory-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100, 200]"
          :total="filteredItems.length"
          layout="total, sizes, prev, pager, next"
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearMyMemory, deleteMyMemoryItem, getMyMemoryItems } from '@/api/memory'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const clearing = ref(false)
const deleting = ref(false)
const items = ref([])
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)

const scopeType = ref('all')
const memoryType = ref('all')

const currentUserLabel = ref('')

const handleOpen = async () => {
  currentUserLabel.value = getCurrentUserLabel()
  await loadMemory()
}

const getCurrentUserLabel = () => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (!userInfoStr) return ''
  try {
    const userInfo = JSON.parse(userInfoStr)
    const username = userInfo?.username
    const userId = userInfo?.userId ?? userInfo?.id
    if (username && userId != null) return `用户：${username}（ID: ${userId}）`
    if (username) return `用户：${username}`
    if (userId != null) return `用户ID：${userId}`
    return ''
  } catch (e) {
    return ''
  }
}

const buildListParams = () => {
  const params = { page: 1, size: 200 }
  if (memoryType.value !== 'all') params.type = memoryType.value
  if (scopeType.value !== 'all') params.scopeType = scopeType.value
  return params
}

const loadMemory = async () => {
  loading.value = true
  try {
    const data = await getMyMemoryItems(buildListParams())
    items.value = Array.isArray(data) ? data : []
    page.value = 1
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '获取记忆失败')
    items.value = []
  } finally {
    loading.value = false
  }
}

const filteredItems = computed(() => {
  const list = Array.isArray(items.value) ? items.value : []
  const k = (keyword.value || '').trim().toLowerCase()
  if (!k) return list
  return list.filter(item => {
    const key = (item.memoryKey || '').toString().toLowerCase()
    const content = (item.content || '').toString().toLowerCase()
    return key.includes(k) || content.includes(k)
  })
})

const pagedItems = computed(() => {
  const p = Math.max(1, page.value)
  const s = Math.max(1, pageSize.value)
  const start = (p - 1) * s
  return filteredItems.value.slice(start, start + s)
})

const handleKeywordInput = () => {
  page.value = 1
}

const handleScopeChange = async () => {
  page.value = 1
  await loadMemory()
}

const handleTypeChange = async () => {
  page.value = 1
  await loadMemory()
}

const handlePageChange = (p) => {
  page.value = p
}

const handlePageSizeChange = (s) => {
  pageSize.value = s
  page.value = 1
}

const makeMemorySnippet = (content) => {
  if (!content) return ''
  const text = String(content).replace(/\s+/g, ' ').trim()
  if (text.length <= 120) return text
  return text.slice(0, 119) + '…'
}

const formatDate = (date) => {
  if (!date) return '-'
  try {
    return new Date(date).toLocaleString('zh-CN')
  } catch (e) {
    return String(date)
  }
}

const formatMemoryContent = (row) => {
  if (!row || !row.content) return ''
  if (row.memoryType === 'entity') {
    try {
      const obj = JSON.parse(row.content)
      return JSON.stringify(obj, null, 2)
    } catch (e) {
      return String(row.content)
    }
  }
  return String(row.content)
}

const copyText = async (text) => {
  const value = text == null ? '' : String(text)
  try {
    await navigator.clipboard.writeText(value)
    return true
  } catch (e) {
    try {
      const textarea = document.createElement('textarea')
      textarea.value = value
      textarea.style.position = 'fixed'
      textarea.style.left = '-9999px'
      textarea.style.top = '-9999px'
      document.body.appendChild(textarea)
      textarea.focus()
      textarea.select()
      const ok = document.execCommand('copy')
      document.body.removeChild(textarea)
      return ok
    } catch (err) {
      return false
    }
  }
}

const copyMemory = async (row) => {
  const ok = await copyText(formatMemoryContent(row))
  if (ok) ElMessage.success('已复制')
  else ElMessage.error('复制失败')
}

const handleDeleteRow = async (row) => {
  const id = row?.id
  if (!id) {
    ElMessage.error('缺少记忆ID')
    return
  }

  try {
    await ElMessageBox.confirm('确定要删除这条记忆吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  deleting.value = true
  try {
    await deleteMyMemoryItem(id)
    ElMessage.success('已删除')
    await loadMemory()
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

const buildClearParams = () => {
  const params = {}
  if (scopeType.value !== 'all') params.scopeType = scopeType.value
  return params
}

const clearConfirmText = computed(() => {
  if (scopeType.value === 'all') return '确定要清空你的全部记忆吗？'
  if (scopeType.value === 'chat') return '确定要清空你的 Chat 记忆吗？'
  if (scopeType.value === 'knowledge_base') {
    return '确定要清空你的知识库记忆吗？'
  }
  if (scopeType.value === 'app') {
    return '确定要清空你的应用记忆吗？'
  }
  return '确定要清空你的记忆吗？'
})

const handleClearFilters = () => {
  keyword.value = ''
  scopeType.value = 'all'
  memoryType.value = 'all'
  page.value = 1
  pageSize.value = 10
  ElMessage.success('查询条件已重置')
}

const handleClearMemory = async () => {
  try {
    await ElMessageBox.confirm(clearConfirmText.value, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  clearing.value = true
  try {
    await clearMyMemory(buildClearParams())
    ElMessage.success('已清空记忆')
    await loadMemory()
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '清空失败')
  } finally {
    clearing.value = false
  }
}
</script>

<style scoped>
.dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dialog-title {
  font-weight: 600;
  color: #303133;
}

.dialog-subtitle {
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.memory-dialog-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.memory-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-shrink: 0;
  min-height: 40px;
  margin-bottom: 8px;
}

.memory-tabs {
  flex-shrink: 0;
}

.memory-table-wrapper {
  flex: 1;
  min-height: 0;
}

.memory-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  flex-shrink: 0;
}

.memory-snippet {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.expand-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.expand-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.memory-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.memory-content {
  overflow: auto;
  max-height: 380px;
  background: #0b1020;
  color: #e6edf3;
  padding: 12px;
  border-radius: 6px;
  margin: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 12px;
  line-height: 1.5;
}

:deep(.memory-dialog .el-dialog__body) {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

:deep(.memory-dialog .el-dialog) {
  height: 720px;
  display: flex;
  flex-direction: column;
}

:deep(.memory-dialog .el-tabs__header) {
  margin: 0 0 8px 0;
}

:deep(.memory-dialog .el-table__body-wrapper .el-scrollbar__wrap) {
  overflow-y: scroll;
}
</style>
