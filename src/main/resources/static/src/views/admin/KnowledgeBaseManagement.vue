<template>
  <div class="knowledge-base-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>知识库管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            创建知识库
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索知识库名称或描述"
          clearable
          style="width: 300px"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="filterStatus"
          placeholder="筛选状态"
          clearable
          style="width: 150px; margin-left: 10px"
          @change="handleFilter"
        >
          <el-option label="全部" value="" />
          <el-option label="启用" value="active" />
          <el-option label="禁用" value="inactive" />
        </el-select>
      </div>

      <!-- 知识库列表 -->
      <el-table
        :data="filteredKnowledgeBases"
        v-loading="loading"
        stripe
        style="margin-top: 20px"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column label="知识库名称" min-width="200">
          <template #default="{ row }">
            <div class="kb-name-cell">
              <el-icon class="kb-icon"><Document /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column label="文档数量" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="info">{{ row.documentCount || 0 }} 个</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看</el-button>
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="success" @click="handleManageDocs(row)">管理文档</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredKnowledgeBases.length"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="知识库名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            placeholder="请输入知识库描述"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio label="active">启用</el-radio>
            <el-radio label="inactive">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog
      v-model="viewDialogVisible"
      title="知识库详情"
      width="700px"
    >
      <el-descriptions :column="2" border v-if="currentKB">
        <el-descriptions-item label="ID">{{ currentKB.id }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ currentKB.name }}</el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="currentKB.status === 'active' ? 'success' : 'info'">
            {{ currentKB.status === 'active' ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文档数量">{{ currentKB.documentCount || 0 }} 个</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(currentKB.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentKB.description || '无' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Document } from '@element-plus/icons-vue'

// 模拟数据
const mockKnowledgeBases = ref([
  {
    id: 1,
    name: '产品使用手册',
    description: '包含产品功能说明、操作指南、常见问题等',
    documentCount: 15,
    status: 'active',
    createTime: '2025-01-15 10:30:00'
  },
  {
    id: 2,
    name: '技术文档库',
    description: 'API文档、开发指南、架构设计等技术相关文档',
    documentCount: 32,
    status: 'active',
    createTime: '2025-01-10 14:20:00'
  },
  {
    id: 3,
    name: '客户服务知识库',
    description: '客户常见问题、服务流程、政策说明等',
    documentCount: 28,
    status: 'active',
    createTime: '2025-01-08 09:15:00'
  },
  {
    id: 4,
    name: '内部培训资料',
    description: '员工培训材料、操作规范、最佳实践等',
    documentCount: 12,
    status: 'inactive',
    createTime: '2025-01-05 16:45:00'
  }
])

const loading = ref(false)
const searchKeyword = ref('')
const filterStatus = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const currentKB = ref(null)
const isEdit = ref(false)
const formRef = ref(null)

const formData = ref({
  name: '',
  description: '',
  status: 'active'
})

const formRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' }
  ]
}

const dialogTitle = computed(() => {
  return isEdit.value ? '编辑知识库' : '创建知识库'
})

const filteredKnowledgeBases = computed(() => {
  let result = [...mockKnowledgeBases.value]
  
  // 搜索过滤
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(kb => 
      kb.name.toLowerCase().includes(keyword) ||
      (kb.description && kb.description.toLowerCase().includes(keyword))
    )
  }
  
  // 状态过滤
  if (filterStatus.value) {
    result = result.filter(kb => kb.status === filterStatus.value)
  }
  
  return result
})

const handleSearch = () => {
  currentPage.value = 1
}

const handleFilter = () => {
  currentPage.value = 1
}

const handleCreate = () => {
  isEdit.value = false
  formData.value = {
    name: '',
    description: '',
    status: 'active'
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  formData.value = {
    name: row.name,
    description: row.description,
    status: row.status
  }
  dialogVisible.value = true
}

const handleView = (row) => {
  currentKB.value = row
  viewDialogVisible.value = true
}

const handleManageDocs = (row) => {
  ElMessage.info(`管理知识库"${row.name}"的文档`)
  // 这里可以跳转到文档管理页面
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除知识库"${row.name}"吗？此操作不可恢复。`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    const index = mockKnowledgeBases.value.findIndex(kb => kb.id === row.id)
    if (index > -1) {
      mockKnowledgeBases.value.splice(index, 1)
      ElMessage.success('删除成功')
    }
  }).catch(() => {
    // 取消操作
  })
}

const handleSubmit = () => {
  formRef.value.validate((valid) => {
    if (valid) {
      if (isEdit.value) {
        ElMessage.success('编辑成功')
      } else {
        // 创建新知识库
        const newKB = {
          id: mockKnowledgeBases.value.length + 1,
          ...formData.value,
          documentCount: 0,
          createTime: new Date().toLocaleString('zh-CN')
        }
        mockKnowledgeBases.value.unshift(newKB)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
}

const handlePageChange = (page) => {
  currentPage.value = page
}

const formatDate = (date) => {
  if (!date) return ''
  return date
}

onMounted(() => {
  // 页面加载时的初始化操作
})
</script>

<style scoped>
.knowledge-base-management {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.kb-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kb-icon {
  color: #409eff;
  font-size: 18px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>

