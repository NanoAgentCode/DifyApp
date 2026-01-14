<template>
  <el-dialog
    v-model="visible"
    title="记忆管理"
    width="980px"
    class="memory-dialog"
    destroy-on-close
    @open="handleOpen"
  >
    <div class="memory-dialog-body">
      <div class="memory-toolbar">
        <el-radio-group v-model="scopeType" size="small">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="chat">Chat</el-radio-button>
          <el-radio-button label="knowledge_base">知识库</el-radio-button>
          <el-radio-button label="app">应用</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="memoryType" size="small">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="long_term">长期</el-radio-button>
          <el-radio-button label="entity">实体</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="keyword"
          placeholder="搜索 Key / 内容"
          clearable
          style="width: 260px"
          @input="handleKeywordInput"
        />
        <el-button
          type="danger"
          plain
          :disabled="clearing"
          @click="handleClear"
        >
          清空
        </el-button>
        <el-button
          :loading="loading"
          @click="loadMemory"
        >
          刷新
        </el-button>
      </div>

      <div class="memory-table-wrapper" v-loading="loading">
        <el-table
          :data="pagedItems"
          border
          stripe
          height="100%"
          :header-cell-style="{ background: '#f5f7fa' }"
        >
          <el-table-column prop="scopeType" label="Scope" width="140" />
          <el-table-column prop="scopeId" label="Scope ID" width="120">
            <template #default="{ row }">
              <span>{{ row.scopeId ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="memoryType" label="类型" width="110" />
          <el-table-column prop="memoryKey" label="Key" min-width="180" show-overflow-tooltip />
          <el-table-column label="内容" min-width="320" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="memory-snippet" :title="String(row.content || '')">
                {{ makeSnippet(row.content) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="importance" label="重要度" width="90" align="center" />
          <el-table-column prop="updateTime" label="更新时间" width="190">
            <template #default="{ row }">
              <span>{{ formatTime(row.updateTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="copyRow(row)">复制</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="memory-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100, 200]"
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
import { clearMyMemory, getMyMemoryItems } from '@/api/memory'

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
const items = ref([])
const keyword = ref('')
const page = ref(1)
const pageSize = ref(50)

const scopeType = ref('all')
const memoryType = ref('all')

const handleOpen = async () => {
  await loadMemory()
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

const handlePageChange = (p) => {
  page.value = p
}

const handlePageSizeChange = (s) => {
  pageSize.value = s
  page.value = 1
}

const makeSnippet = (content) => {
  if (!content) return ''
  const text = String(content).replace(/\s+/g, ' ').trim()
  if (text.length <= 160) return text
  return text.slice(0, 159) + '…'
}

const formatTime = (val) => {
  if (!val) return '-'
  try {
    const d = new Date(val)
    if (Number.isNaN(d.getTime())) return String(val)
    return d.toLocaleString()
  } catch (e) {
    return String(val)
  }
}

const formatRowForCopy = (row) => {
  if (!row) return ''
  const header = [
    `scopeType: ${row.scopeType ?? ''}`,
    `scopeId: ${row.scopeId ?? ''}`,
    `memoryType: ${row.memoryType ?? ''}`,
    `memoryKey: ${row.memoryKey ?? ''}`,
    `importance: ${row.importance ?? ''}`,
    `updateTime: ${formatTime(row.updateTime)}`
  ].join('\n')
  return `${header}\n\n${row.content ?? ''}`
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

const copyRow = async (row) => {
  const ok = await copyText(formatRowForCopy(row))
  if (ok) ElMessage.success('已复制')
  else ElMessage.error('复制失败')
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

const handleClear = async () => {
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
.memory-dialog-body {
  display: flex;
  flex-direction: column;
  height: 680px;
  min-height: 0;
}

.memory-toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  padding-bottom: 10px;
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
</style>
