<template>
  <div class="vector-database-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>数据库管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            添加配置
          </el-button>
        </div>
      </template>

      <div class="config-list-section">
        <el-table
          :data="configList"
          v-loading="loading"
          stripe
          border
          style="width: 100%"
          :row-class-name="getRowClassName"
        >
          <el-table-column prop="name" label="配置名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="type" label="数据库类型" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="getTypeTagType(row.type)">
                {{ getTypeLabel(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="url" label="连接地址/路径" min-width="200" show-overflow-tooltip />
          <el-table-column prop="timeout" label="超时时间（毫秒）" width="150" align="center">
            <template #default="{ row }">
              {{ row.type === 'faiss' ? '-' : (row.timeout || '-') }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled"
                @change="(val) => handleToggleEnabled(row, val)"
                :disabled="row.isDefault"
                active-text=""
                inactive-text=""
              />
            </template>
          </el-table-column>
          <el-table-column label="默认" width="100" align="center">
            <template #default="{ row }">
              <el-radio
                :model-value="getDefaultConfigId(row.type)"
                :label="row.id"
                @change="handleSetDefault(row)"
                :disabled="!row.enabled"
                style="--el-radio-input-width: 16px; --el-radio-input-height: 16px;"
              >
                <template #default></template>
              </el-radio>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right" align="center">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-button
                  size="small"
                  type="success"
                  @click="handleTest(row)"
                  :loading="row.testing"
                >
                  <el-icon><Link /></el-icon>
                  测试连接
                </el-button>
                <el-button
                  size="small"
                  type="primary"
                  @click="handleEdit(row)"
                >
                  <el-icon><Edit /></el-icon>
                  编辑
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  @click="handleDelete(row)"
                  :disabled="row.isDefault"
                >
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
      :lock-scroll="true"
    >
      <el-form
        :model="currentConfig"
        :rules="formRules"
        ref="formRef"
        label-width="120px"
      >
        <el-form-item label="配置名称" prop="name">
          <el-input
            v-model="currentConfig.name"
            placeholder="请输入配置名称，用于标识"
          />
        </el-form-item>

        <el-form-item label="数据库类型" prop="type">
          <el-select v-model="currentConfig.type" placeholder="请选择数据库类型" style="width: 100%" :disabled="isEdit">
            <el-option label="Qdrant" value="qdrant" />
            <el-option label="Milvus" value="milvus" />
            <el-option label="FAISS" value="faiss" />
            <el-option label="Chroma" value="chroma" />
          </el-select>
        </el-form-item>

        <el-form-item :label="currentConfig.type === 'faiss' ? '存储路径' : '连接地址'" prop="url">
          <el-input
            v-model="currentConfig.url"
            :placeholder="currentConfig.type === 'faiss' ? '例如: ./data/faiss 或 /path/to/faiss' : (currentConfig.type === 'chroma' ? '例如: http://localhost:8000' : '例如: http://localhost:6333 或 http://localhost:19530')"
          />
          <div v-if="currentConfig.type === 'milvus'" style="font-size: 12px; color: #909399; margin-top: 5px;">
            提示：Milvus 使用 gRPC 协议，配置 URL 格式（例如：http://localhost:19530）
          </div>
          <div v-if="currentConfig.type === 'chroma'" style="font-size: 12px; color: #909399; margin-top: 5px;">
            提示：Chroma 使用 HTTP REST API，默认端口为 8000（例如：http://localhost:8000）
          </div>
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey" v-if="currentConfig.type !== 'faiss'">
          <el-input
            v-model="currentConfig.apiKey"
            type="password"
            show-password
            placeholder="可选，如果数据库启用了认证，请配置API Key"
          />
        </el-form-item>

        <el-form-item label="超时时间（毫秒）" prop="timeout" v-if="currentConfig.type !== 'faiss'">
          <el-input-number
            v-model="currentConfig.timeout"
            :min="1000"
            :max="300000"
            :step="1000"
            style="width: 100%"
            placeholder="默认30000"
          />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="currentConfig.description"
            type="textarea"
            :rows="3"
            placeholder="请输入配置描述（可选）"
          />
        </el-form-item>

        <el-form-item label="是否启用" prop="enabled">
          <el-switch v-model="currentConfig.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Edit,
  Delete,
  Link
} from '@element-plus/icons-vue'
import { 
  getVectorDatabaseList, 
  updateVectorDatabaseConfig, 
  testVectorDatabaseConnection 
} from '@/api/vectorDatabase'

const loading = ref(false)
const saving = ref(false)
const configList = ref([])

// 对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('添加配置')
const isEdit = ref(false)
const formRef = ref(null)
const currentConfig = reactive({
  id: null,
  name: '',
  type: 'qdrant',
  url: '',
  apiKey: '',
  timeout: 30000,
  description: '',
  enabled: true
})

const formRules = {
  name: [
    { required: true, message: '请输入配置名称', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择数据库类型', trigger: 'change' }
  ],
  url: [
    { required: true, message: '请输入连接地址或存储路径', trigger: 'blur' }
  ],
  timeout: [
    { type: 'number', min: 1000, message: '超时时间不能小于 1000 毫秒', trigger: 'blur' }
  ]
}

// 获取默认配置ID（按类型）
const getDefaultConfigId = (type) => {
  const defaultConfig = configList.value.find(config => 
    config.type === type && config.isDefault
  )
  return defaultConfig ? defaultConfig.id : null
}

// 获取数据库类型标签类型
const getTypeTagType = (type) => {
  const map = {
    qdrant: 'primary',
    milvus: 'warning',
    faiss: 'success',
    chroma: 'info'
  }
  return map[type] || 'info'
}

// 获取数据库类型标签文本
const getTypeLabel = (type) => {
  const map = {
    qdrant: 'Qdrant',
    milvus: 'Milvus',
    faiss: 'FAISS',
    chroma: 'Chroma'
  }
  return map[type] || type
}

// 获取表格行类名（用于高亮默认配置）
const getRowClassName = ({ row }) => {
  return row.isDefault ? 'default-config-row' : ''
}

// 加载配置列表
const loadConfigs = async () => {
  loading.value = true
  try {
    const data = await getVectorDatabaseList()
    configList.value = (data || []).map(config => ({
      ...config,
      testing: false
    }))
  } catch (error) {
    ElMessage.error('加载配置列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 添加配置
const handleAdd = () => {
  dialogTitle.value = '添加配置'
  isEdit.value = false
  currentConfig.id = null
  currentConfig.name = ''
  currentConfig.type = 'qdrant' // 默认类型
  currentConfig.url = ''
  currentConfig.apiKey = ''
  currentConfig.timeout = 30000
  currentConfig.description = ''
  currentConfig.enabled = true
  dialogVisible.value = true
}

// 编辑配置
const handleEdit = (row) => {
  dialogTitle.value = '编辑配置'
  isEdit.value = true
  currentConfig.id = row.id
  currentConfig.name = row.name
  currentConfig.type = row.type
  currentConfig.url = row.url
  currentConfig.apiKey = row.apiKey || ''
  currentConfig.timeout = row.timeout || 30000
  currentConfig.description = row.description || ''
  currentConfig.enabled = row.enabled !== false
  dialogVisible.value = true
}

// 保存配置
const handleSave = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    
    saving.value = true
    
    const action = isEdit.value ? 'update' : 'add'
    const data = {
      action: action,
      configId: currentConfig.id,
      database: {
        id: currentConfig.id,
        name: currentConfig.name,
        type: currentConfig.type,
        url: currentConfig.url,
        apiKey: currentConfig.apiKey || null,
        timeout: currentConfig.timeout || (currentConfig.type === 'faiss' ? null : 30000),
        description: currentConfig.description || null,
        enabled: currentConfig.enabled
      }
    }
    
    const response = await updateVectorDatabaseConfig(data)
    
    ElMessage.success(isEdit.value ? '编辑成功' : '添加成功')
    dialogVisible.value = false
    loadConfigs()
  } catch (error) {
    if (error !== false) {
      ElMessage.error((isEdit.value ? '编辑' : '添加') + '失败：' + (error.message || '未知错误'))
    }
  } finally {
    saving.value = false
  }
}

// 删除配置
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除配置"${row.name}"吗？此操作不可恢复。`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await updateVectorDatabaseConfig({
        action: 'delete',
        configId: row.id
      })
      ElMessage.success('删除成功')
      loadConfigs()
    } catch (error) {
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }).catch(() => {
    // 取消操作
  })
}

// 设置默认配置
const handleSetDefault = async (row) => {
  try {
    await updateVectorDatabaseConfig({
      action: 'setDefault',
      configId: row.id
    })
    ElMessage.success('设置默认配置成功')
    loadConfigs()
  } catch (error) {
    ElMessage.error('设置默认配置失败：' + (error.message || '未知错误'))
    loadConfigs() // 重新加载以恢复状态
  }
}

// 切换启用状态
const handleToggleEnabled = async (row, enabled) => {
  try {
    await updateVectorDatabaseConfig({
      action: 'toggleEnabled',
      configId: row.id,
      enabled: enabled
    })
    ElMessage.success(enabled ? '已启用' : '已禁用')
    loadConfigs()
  } catch (error) {
    ElMessage.error('切换状态失败：' + (error.message || '未知错误'))
    loadConfigs() // 重新加载以恢复状态
  }
}

// 测试连接
const handleTest = async (row) => {
  const index = configList.value.findIndex(c => c.id === row.id)
  if (index === -1) return
  
  configList.value[index].testing = true
  
  try {
    await testVectorDatabaseConnection({
      type: row.type,
      url: row.url,
      apiKey: row.apiKey || null,
      timeout: row.timeout || 30000
    })
    ElMessage.success('连接测试成功')
  } catch (error) {
    ElMessage.error('连接测试失败：' + (error.message || '未知错误'))
  } finally {
    configList.value[index].testing = false
  }
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped>
.vector-database-management {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-list-section {
  margin-top: 20px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
}

.action-buttons .el-button {
  margin: 0;
}

/* 默认配置行高亮 */
:deep(.default-config-row) {
  background-color: #f0f9ff;
}

:deep(.default-config-row:hover) {
  background-color: #e0f2fe;
}
</style>

