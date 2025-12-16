<template>
  <div class="data-source-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>数据源管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            创建数据源
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索数据源名称或描述"
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
          placeholder="筛选数据库类型"
          clearable
          style="width: 150px; margin-left: 10px"
          @change="handleFilter"
        >
          <el-option label="全部" value="" />
          <el-option label="PostgreSQL" value="postgresql" />
          <el-option label="MySQL" value="mysql" />
          <el-option label="Oracle" value="oracle" />
          <el-option label="MongoDB" value="mongodb" />
          <el-option label="Neo4j" value="neo4j" />
        </el-select>
        <el-select
          v-model="filterStatus"
          placeholder="筛选状态"
          clearable
          style="width: 150px; margin-left: 10px"
          @change="handleFilter"
        >
          <el-option label="全部" value="" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </div>

      <!-- 数据源列表 -->
      <div class="table-container">
        <el-table
          :data="dataSources"
          v-loading="loading"
          stripe
        >
          <el-table-column prop="id" label="ID" width="80" align="center" />
          <el-table-column prop="name" label="数据源名称" min-width="200" />
          <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
          <el-table-column prop="type" label="数据库类型" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="getTypeTag(row.type)">{{ getTypeName(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="host" label="主机" width="150" />
          <el-table-column prop="port" label="端口" width="80" align="center" />
          <el-table-column prop="database" label="数据库" width="120" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="250" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="handleTestConnection(row.id)">测试连接</el-button>
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
      :title="isEdit ? '编辑数据源' : '创建数据源'"
      width="600px"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="数据源名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入数据源名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择数据库类型" style="width: 100%" @change="handleTypeChange">
            <el-option label="PostgreSQL" value="postgresql" />
            <el-option label="MySQL" value="mysql" />
            <el-option label="Oracle" value="oracle" />
            <el-option label="MongoDB" value="mongodb" />
            <el-option label="Neo4j" value="neo4j" />
          </el-select>
        </el-form-item>
        <el-form-item label="主机地址" prop="host">
          <el-input v-model="formData.host" placeholder="请输入主机地址" />
        </el-form-item>
        <el-form-item label="端口" prop="port">
          <el-input-number v-model="formData.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item label="数据库名称" prop="database">
          <el-input v-model="formData.database" placeholder="请输入数据库名称" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formData.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="formData.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <el-button @click="handleTestConnectionInDialog" :loading="testingConnection" type="info">
            测试连接
          </el-button>
          <div>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSubmit">确定</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  getDataSourceList,
  createDataSource,
  updateDataSource,
  deleteDataSource,
  testDataSourceConnection,
  testDataSourceConnectionConfig
} from '@/api/dataSource'

const dataSources = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterType = ref('')
const filterStatus = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const currentEditId = ref(null)
const testingConnection = ref(false)

const formData = ref({
  name: '',
  description: '',
  type: 'postgresql',
  host: '',
  port: 5432,
  database: '',
  username: '',
  password: '',
  status: 1
})

// 数据库类型对应的默认端口
const defaultPorts = {
  postgresql: 5432,
  mysql: 3306,
  oracle: 1521,
  mongodb: 27017,
  neo4j: 7687
}

// 处理数据库类型变化，自动更新默认端口
const handleTypeChange = (type) => {
  if (defaultPorts[type]) {
    formData.value.port = defaultPorts[type]
  }
}

const formRules = {
  name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }]
}

let searchTimer = null

onMounted(() => {
  loadDataSources()
})

const loadDataSources = async () => {
  loading.value = true
  try {
    const params = {}
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }
    if (filterType.value) {
      params.type = filterType.value
    }
    if (filterStatus.value !== '') {
      params.status = filterStatus.value
    }
    const response = await getDataSourceList(params)
    dataSources.value = Array.isArray(response) ? response : []
  } catch (error) {
    ElMessage.error('加载数据源列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    loadDataSources()
  }, 500)
}

const handleFilter = () => {
  loadDataSources()
}

const handleCreate = () => {
  isEdit.value = false
  currentEditId.value = null
  formData.value = {
    name: '',
    description: '',
    type: 'postgresql',
    host: '',
    port: defaultPorts.postgresql,
    database: '',
    username: '',
    password: '',
    status: 1
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  currentEditId.value = row.id
  formData.value = {
    name: row.name,
    description: row.description || '',
    type: row.type,
    host: row.host,
    port: row.port,
    database: row.database || '',
    username: row.username || '',
    password: '', // 不显示密码
    status: row.status
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value) {
          await updateDataSource(currentEditId.value, formData.value)
          ElMessage.success('更新成功')
        } else {
          await createDataSource(formData.value)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        loadDataSources()
      } catch (error) {
        ElMessage.error(error.response?.data?.error || error.message || '操作失败')
      }
    }
  })
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该数据源吗？', '提示', {
      type: 'warning'
    })
    await deleteDataSource(id)
    ElMessage.success('删除成功')
    loadDataSources()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const handleTestConnection = async (id) => {
  try {
    const response = await testDataSourceConnection(id)
    if (response.success) {
      ElMessage.success('连接成功')
    } else {
      ElMessage.error(response.message || '连接失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '测试连接失败')
  }
}

const handleTestConnectionInDialog = async () => {
  if (!formRef.value) return
  
  // 编辑时，如果有ID，直接使用ID测试（使用已有的密码）
  if (isEdit.value && currentEditId.value) {
    testingConnection.value = true
    try {
      const response = await testDataSourceConnection(currentEditId.value)
      if (response.success) {
        ElMessage.success('连接成功')
      } else {
        ElMessage.error(response.message || '连接失败')
      }
    } catch (error) {
      ElMessage.error(error.response?.data?.message || error.message || '测试连接失败')
    } finally {
      testingConnection.value = false
    }
    return
  }
  
  // 创建时，先保存数据源，然后使用ID测试
  // 先验证表单
  await formRef.value.validate(async (valid) => {
    if (!valid) {
      ElMessage.warning('请先填写完整的信息')
      return
    }
    
    testingConnection.value = true
    try {
      // 先创建数据源
      const createdDataSource = await createDataSource(formData.value)
      const newId = createdDataSource.id
      
      if (!newId) {
        ElMessage.error('创建数据源失败，无法测试连接')
        return
      }
      
      // 使用创建后的ID测试连接
      const response = await testDataSourceConnection(newId)
      
      if (response.success) {
        ElMessage.success('连接成功')
        // 测试成功后，刷新列表，但保持对话框打开，让用户决定是否继续
        await loadDataSources()
        // 更新当前编辑ID，这样用户点击确定时就是更新而不是创建
        currentEditId.value = newId
        isEdit.value = true
      } else {
        ElMessage.error(response.message || '连接失败')
        // 连接失败，删除刚创建的数据源
        try {
          await deleteDataSource(newId)
        } catch (deleteError) {
          console.error('删除测试数据源失败:', deleteError)
        }
      }
    } catch (error) {
      ElMessage.error(error.response?.data?.error || error.response?.data?.message || error.message || '测试连接失败')
    } finally {
      testingConnection.value = false
    }
  })
}

const getTypeName = (type) => {
  const typeMap = {
    postgresql: 'PostgreSQL',
    mysql: 'MySQL',
    oracle: 'Oracle',
    mongodb: 'MongoDB',
    neo4j: 'Neo4j'
  }
  return typeMap[type] || type
}

const getTypeTag = (type) => {
  const tagMap = {
    postgresql: 'primary',
    mysql: 'success',
    oracle: 'warning',
    mongodb: 'info',
    neo4j: 'danger'
  }
  return tagMap[type] || ''
}
</script>

<style scoped>
.data-source-management {
  padding: 0;
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

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: 18px 20px;
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 20px;
  min-height: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  margin-bottom: 20px;
  flex-shrink: 0;
}

.table-container {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}
</style>

