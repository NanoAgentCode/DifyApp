<template>
  <div class="vector-database-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>向量数据库</span>
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
          <el-table-column label="新建知识库" width="150" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.allowCreateKnowledgeBase !== false"
                @change="(val) => handleToggleAllowCreateKnowledgeBase(row, val)"
                active-text=""
                inactive-text=""
              />
            </template>
          </el-table-column>
          <el-table-column label="默认" width="100" align="center">
            <template #default="{ row }">
              <el-radio
                v-model="defaultConfigId"
                :label="row.id"
                @change="handleSetDefault(row)"
                :disabled="!row.enabled"
                style="--el-radio-input-width: 16px; --el-radio-input-height: 16px;"
                class="hide-radio-label"
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
                  测试
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
            <el-option label="Weaviate" value="weaviate" />
            <el-option label="Elasticsearch" value="elasticsearch" />
          </el-select>
        </el-form-item>

        <el-form-item prop="url">
          <template #label>
            <span>{{ currentConfig.type === 'faiss' ? '存储路径' : '连接地址' }}</span>
            <el-tooltip
              v-if="currentConfig.type === 'milvus'"
              content="Milvus 使用 gRPC 协议，配置 URL 格式（例如：http://localhost:19530）"
              placement="top"
            >
              <el-icon style="margin-left: 4px; color: #909399; cursor: help;">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
            <el-tooltip
              v-if="currentConfig.type === 'chroma'"
              content="Chroma 使用 HTTP REST API，默认端口为 8000（例如：http://localhost:8000）"
              placement="top"
            >
              <el-icon style="margin-left: 4px; color: #909399; cursor: help;">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
            <el-tooltip
              v-if="currentConfig.type === 'weaviate'"
              content="Weaviate 使用 HTTP REST API 和 GraphQL，默认端口为 8080（例如：http://localhost:8080）"
              placement="top"
            >
              <el-icon style="margin-left: 4px; color: #909399; cursor: help;">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
            <el-tooltip
              v-if="currentConfig.type === 'elasticsearch'"
              content="Elasticsearch 使用 HTTP REST API，默认端口为 9200（例如：http://localhost:9200）"
              placement="top"
            >
              <el-icon style="margin-left: 4px; color: #909399; cursor: help;">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </template>
          <el-input
            v-model="currentConfig.url"
            :placeholder="currentConfig.type === 'faiss' ? '例如: ./data/faiss 或 /path/to/faiss' : (currentConfig.type === 'chroma' ? '例如: http://localhost:8000' : (currentConfig.type === 'weaviate' ? '例如: http://localhost:8080' : (currentConfig.type === 'elasticsearch' ? '例如: http://localhost:9200' : '例如: http://localhost:6333 或 http://localhost:19530')))"
          />
        </el-form-item>

        <!-- Elasticsearch 用户名密码认证 -->
        <template v-if="currentConfig.type === 'elasticsearch'">
          <el-form-item prop="username">
            <template #label>
              <span>用户名/密码</span>
              <el-tooltip
                content="如果Elasticsearch启用了安全功能，请配置用户名和密码"
                placement="top"
              >
                <el-icon style="margin-left: 4px; color: #909399; cursor: help;">
                  <QuestionFilled />
                </el-icon>
              </el-tooltip>
            </template>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-input
                  v-model="currentConfig.username"
                  placeholder="用户名（可选）"
                />
              </el-col>
              <el-col :span="12">
                <el-input
                  v-model="currentConfig.password"
                  type="password"
                  show-password
                  placeholder="密码（可选）"
                />
              </el-col>
            </el-row>
          </el-form-item>
        </template>
        
        <!-- 其他数据库的 API Key -->
        <el-form-item label="API Key" prop="apiKey" v-if="currentConfig.type !== 'faiss' && currentConfig.type !== 'elasticsearch'">
          <el-input
            v-model="currentConfig.apiKey"
            type="password"
            show-password
            placeholder="可选，如果数据库启用了认证，请配置API Key"
          />
        </el-form-item>
        
        <!-- Elasticsearch 也支持 API Key（作为备选） -->
        <el-form-item prop="apiKey" v-if="currentConfig.type === 'elasticsearch'">
          <template #label>
            <span>API Key（备选）</span>
            <el-tooltip
              content="Elasticsearch 支持用户名密码或 API Key 认证，优先使用用户名密码"
              placement="top"
            >
              <el-icon style="margin-left: 4px; color: #909399; cursor: help;">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </template>
          <el-input
            v-model="currentConfig.apiKey"
            type="password"
            show-password
            placeholder="可选，如果使用API Key认证而不是用户名密码，请配置API Key"
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
        <el-form-item>
          <template #label>
            <span>新建知识库</span>
            <el-tooltip
              content="控制是否允许使用该向量库创建新知识库。如果设置为不允许，创建知识库时该向量库将不会出现在下拉菜单中。"
              placement="top"
            >
              <el-icon style="margin-left: 4px; color: #909399; cursor: help;">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </template>
          <el-switch
            v-model="currentConfig.allowCreateKnowledgeBase"
            active-text="允许"
            inactive-text="禁止"
          />
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
  Link,
  QuestionFilled
} from '@element-plus/icons-vue'
import { 
  getVectorDatabaseList, 
  updateVectorDatabaseConfig, 
  testVectorDatabaseConnection 
} from '@/api/vectorDatabase'

const loading = ref(false)
const saving = ref(false)
const configList = ref([])
const defaultConfigId = ref(null) // 用于响应式更新单选按钮状态

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
  username: '', // Elasticsearch 用户名
  password: '', // Elasticsearch 密码
  timeout: 30000,
  description: '',
  enabled: true,
  allowCreateKnowledgeBase: true // 默认允许新建知识库
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

// 获取默认配置ID（按类型）- 保留用于向后兼容
const getDefaultConfigId = (type) => {
  const defaultConfig = configList.value.find(config => 
    config.type === type && config.isDefault
  )
  return defaultConfig ? defaultConfig.id : null
}

// 获取全局默认配置ID（所有类型中只有一个默认配置）- 保留用于向后兼容
const getGlobalDefaultConfigId = () => {
  return defaultConfigId.value
}

// 获取数据库类型标签类型
const getTypeTagType = (type) => {
  const map = {
    qdrant: 'primary',
    milvus: 'warning',
    faiss: 'success',
    chroma: 'info',
    weaviate: 'success',
    elasticsearch: 'danger'
  }
  return map[type] || 'info'
}

// 获取数据库类型标签文本
const getTypeLabel = (type) => {
  const map = {
    qdrant: 'Qdrant',
    milvus: 'Milvus',
    faiss: 'FAISS',
    chroma: 'Chroma',
    weaviate: 'Weaviate',
    elasticsearch: 'Elasticsearch'
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
    // 更新默认配置ID
    const defaultConfig = configList.value.find(config => config.isDefault)
    defaultConfigId.value = defaultConfig ? defaultConfig.id : null
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
  currentConfig.username = ''
  currentConfig.password = ''
  currentConfig.timeout = 30000
  currentConfig.description = ''
  currentConfig.enabled = true
  currentConfig.allowCreateKnowledgeBase = true // 默认允许新建知识库
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
  currentConfig.allowCreateKnowledgeBase = row.allowCreateKnowledgeBase !== undefined ? row.allowCreateKnowledgeBase : true
  
  // 解析 extraConfig 获取用户名和密码（针对 Elasticsearch）
  if (row.type === 'elasticsearch' && row.extraConfig) {
    try {
      const extraConfig = JSON.parse(row.extraConfig)
      currentConfig.username = extraConfig.username || ''
      currentConfig.password = extraConfig.password || ''
    } catch (e) {
      currentConfig.username = ''
      currentConfig.password = ''
    }
  } else {
    currentConfig.username = ''
    currentConfig.password = ''
  }
  
  dialogVisible.value = true
}

// 保存配置
const handleSave = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    
    saving.value = true
    
    const action = isEdit.value ? 'update' : 'add'
    
    // 构建 extraConfig（针对 Elasticsearch 的用户名密码）
    let extraConfig = null
    if (currentConfig.type === 'elasticsearch' && 
        (currentConfig.username || currentConfig.password)) {
      extraConfig = JSON.stringify({
        username: currentConfig.username || '',
        password: currentConfig.password || ''
      })
    }
    
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
        enabled: currentConfig.enabled,
        allowCreateKnowledgeBase: currentConfig.allowCreateKnowledgeBase !== undefined ? currentConfig.allowCreateKnowledgeBase : true,
        extraConfig: extraConfig
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
  // 立即更新本地状态，提供即时反馈
  const previousDefaultId = defaultConfigId.value
  defaultConfigId.value = row.id
  
  try {
    await updateVectorDatabaseConfig({
      action: 'setDefault',
      configId: row.id
    })
    ElMessage.success('设置默认配置成功')
    // 重新加载配置列表以确保数据同步
    await loadConfigs()
  } catch (error) {
    // 如果失败，恢复之前的状态
    defaultConfigId.value = previousDefaultId
    ElMessage.error('设置默认配置失败：' + (error.message || '未知错误'))
    // 重新加载以恢复状态
    await loadConfigs()
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

// 切换允许新建知识库状态
const handleToggleAllowCreateKnowledgeBase = async (row, allowCreate) => {
  try {
    await updateVectorDatabaseConfig({
      action: 'update',
      configId: row.id,
      database: {
        id: row.id,
        name: row.name,
        type: row.type,
        url: row.url,
        apiKey: row.apiKey || null,
        timeout: row.timeout || null,
        description: row.description || null,
        enabled: row.enabled,
        allowCreateKnowledgeBase: allowCreate,
        extraConfig: row.extraConfig || null // 保留 extraConfig
      }
    })
    ElMessage.success(allowCreate ? '已允许新建知识库' : '已禁止新建知识库')
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
    // 构建测试连接请求数据
    const testData = {
      type: row.type,
      url: row.url,
      apiKey: row.apiKey || null,
      timeout: row.timeout || 30000
    }
    
    // 对于 Elasticsearch，传递 extraConfig（包含用户名密码）
    if (row.type === 'elasticsearch' && row.extraConfig) {
      testData.extraConfig = row.extraConfig
    }
    
    await testVectorDatabaseConnection(testData)
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

/* 隐藏单选按钮的label文本（id） */
:deep(.hide-radio-label) {
  margin-right: 0;
}

:deep(.hide-radio-label .el-radio__label) {
  display: none;
  padding-left: 0;
}
</style>

