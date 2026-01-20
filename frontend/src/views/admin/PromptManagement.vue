<template>
  <div class="prompt-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>提示词管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            创建提示词
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索提示词标题或内容"
          clearable
          style="width: 300px"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- 提示词列表 -->
      <div class="table-container">
        <el-table
          :data="prompts"
          v-loading="loading"
          stripe
        >
          <el-table-column prop="id" label="ID" width="80" align="center" />
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="content" label="内容" min-width="400" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="content-preview">{{ row.content }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.updateTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑提示词' : '创建提示词'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入提示词标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="formData.content"
            type="textarea"
            :rows="15"
            placeholder="请输入提示词内容（支持Markdown格式）"
          />
          <div class="form-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>提示词内容支持Markdown格式，与智能问答的格式要求相同</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="saving">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, InfoFilled } from '@element-plus/icons-vue'
import {
  getPrompts,
  createPrompt,
  updatePrompt,
  deletePrompt
} from '@/api/prompt'

const prompts = ref([])
const loading = ref(false)
const saving = ref(false)
const searchKeyword = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const currentEditId = ref(null)

const formData = ref({
  title: '',
  content: ''
})

const formRules = {
  title: [{ required: true, message: '请输入提示词标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入提示词内容', trigger: 'blur' }]
}

let searchTimer = null

onMounted(() => {
  loadPrompts()
})

const loadPrompts = async () => {
  loading.value = true
  try {
    const keyword = searchKeyword.value ? searchKeyword.value.trim() : null
    const data = await getPrompts(keyword)
    prompts.value = data || []
  } catch (error) {
    console.error('加载提示词列表失败', error)
    ElMessage.error('加载提示词列表失败')
    prompts.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    loadPrompts()
  }, 300)
}

const handleCreate = () => {
  isEdit.value = false
  currentEditId.value = null
  formData.value = {
    title: '',
    content: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  currentEditId.value = row.id
  formData.value = {
    title: row.title,
    content: row.content
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await updatePrompt(currentEditId.value, formData.value)
      ElMessage.success('更新提示词成功')
    } else {
      await createPrompt(formData.value)
      ElMessage.success('创建提示词成功')
    }
    dialogVisible.value = false
    loadPrompts()
  } catch (error) {
    console.error('保存提示词失败', error)
    const errorMessage = error.response?.data?.error || error.message || '保存提示词失败'
    ElMessage.error(errorMessage)
  } finally {
    saving.value = false
  }
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该提示词吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await deletePrompt(id)
    ElMessage.success('删除提示词成功')
    loadPrompts()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除提示词失败', error)
      const errorMessage = error.response?.data?.error || error.message || '删除提示词失败'
      ElMessage.error(errorMessage)
    }
  }
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<style scoped>
/* ========== 页面容器 ========== */
.prompt-management {
  padding: var(--spacing-lg);
  background: var(--color-bg-secondary);
}

/* ========== 卡片样式 ========== */
:deep(.el-card) {
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  border: 1px solid var(--color-border-lighter);
  transition: box-shadow var(--transition-base);
}

:deep(.el-card:hover) {
  box-shadow: var(--card-shadow-hover);
}

:deep(.el-card__header) {
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-lighter);
  padding: var(--spacing-md) var(--card-padding);
}

:deep(.el-card__body) {
  background: var(--color-bg-primary);
  padding: var(--card-padding);
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

.card-header .el-button {
  transition: all var(--transition-base);
}

.card-header .el-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-primary);
}

/* ========== 搜索栏 ========== */
.search-bar {
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-md);
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-lighter);
}

.search-bar :deep(.el-input__wrapper) {
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.search-bar :deep(.el-input__wrapper:hover) {
  box-shadow: var(--shadow-xs);
}

.search-bar :deep(.el-input__wrapper.is-focus) {
  box-shadow: var(--shadow-primary);
}

/* ========== 表格容器 ========== */
.table-container {
  margin-top: var(--spacing-lg);
}

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

.content-preview {
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-regular);
}

/* ========== 表单提示 ========== */
.form-tip {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.form-tip .el-icon {
  font-size: var(--font-size-sm);
  color: var(--color-primary);
}

/* ========== 对话框样式 ========== */
:deep(.el-dialog) {
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-2xl);
}

:deep(.el-dialog__header) {
  padding: var(--spacing-lg) var(--spacing-lg) var(--spacing-md);
  border-bottom: 1px solid var(--color-border-lighter);
}

:deep(.el-dialog__body) {
  padding: var(--spacing-lg);
}

:deep(.el-form-item__label) {
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner) {
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

:deep(.el-input__wrapper:hover),
:deep(.el-textarea__inner:hover) {
  box-shadow: var(--shadow-xs);
}

:deep(.el-input__wrapper.is-focus),
:deep(.el-textarea__inner:focus) {
  box-shadow: var(--shadow-primary);
}
</style>
