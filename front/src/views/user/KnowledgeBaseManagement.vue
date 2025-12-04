<template>
  <div class="knowledge-base-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的知识库</span>
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
        :data="paginatedKnowledgeBases"
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
            <el-tooltip :content="getStatusText(row.status)" placement="top">
              <el-icon :size="20" :color="isActive(row.status) ? '#67c23a' : '#909399'">
                <Check v-if="isActive(row.status)" />
                <Close v-else />
              </el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="向量化模型" width="180" align="center">
          <template #default="{ row }">
            <el-tag 
              v-if="getEmbeddingModelName(row.embeddingModelId)" 
              size="small"
              :style="getModelStyle(row.embeddingModelId)"
            >
              {{ getEmbeddingModelName(row.embeddingModelId) }}
            </el-tag>
            <span v-else style="color: #909399; font-size: 12px;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="向量存储" width="120" align="center">
          <template #default="{ row }">
            <el-tag 
              :type="getVectorStoreTypeTag(row.vectorStoreType)"
              size="small"
            >
              {{ getVectorStoreTypeName(row.vectorStoreType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
            <el-table-column label="操作" width="200" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-buttons-row">
                  <el-dropdown @command="(command) => handleDropdownCommand(command, row)">
                    <el-button size="small" type="primary">
                      更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="view">
                          <el-icon><View /></el-icon>
                          查看
                        </el-dropdown-item>
                        <el-dropdown-item command="edit">
                          <el-icon><Edit /></el-icon>
                          编辑
                        </el-dropdown-item>
                        <el-dropdown-item command="upload">
                          <el-icon><UploadFilled /></el-icon>
                          上传文档
                        </el-dropdown-item>
                        <el-dropdown-item command="list">
                          <el-icon><Document /></el-icon>
                          文件列表
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
                </div>
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
      width="700px"
      :close-on-click-modal="false"
      :lock-scroll="true"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
        label-position="right"
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
        <el-form-item label="可见性">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 0;"
          >
            <template #title>
              <span>普通用户只能创建私有知识库，只有创建者可以访问</span>
            </template>
          </el-alert>
        </el-form-item>
        <el-form-item label="向量化模型" prop="embeddingModelId">
          <el-select
            v-model="formData.embeddingModelId"
            :placeholder="isEdit && hasDocuments ? (getEmbeddingModelName(formData.embeddingModelId) || '默认模型') : '选择向量化模型（不选择则使用默认模型）'"
            clearable
            style="width: 100%"
            :disabled="isEdit && hasDocuments"
          >
            <el-option
              v-for="model in embeddingModels"
              :key="model.id"
              :label="model.name"
              :value="model.id"
            >
              <div style="display: flex; justify-content: space-between; align-items: center; width: 100%">
                <el-tag 
                  size="small"
                  :style="getModelStyle(model.id)"
                  style="flex-shrink: 0"
                >
                  {{ model.name }}
                </el-tag>
                <el-tag v-if="model.isDefault" type="primary" size="small" style="margin-left: 8px; flex-shrink: 0">
                  默认
                </el-tag>
              </div>
            </el-option>
          </el-select>
          <div v-if="isEdit && hasDocuments" class="form-item-hint form-item-hint-warning">
            <el-icon><Warning /></el-icon>
            <span>当前使用：<strong>{{ getEmbeddingModelName(formData.embeddingModelId) || '默认模型' }}</strong>。已有文档，无法修改。</span>
          </div>
          <div v-else class="form-item-hint">
            用于文档向量化的模型，如果不选择则使用系统默认向量化模型
          </div>
        </el-form-item>
        <el-form-item label="Top-K检索数量" prop="topK">
          <el-input-number
            v-model="formData.topK"
            :min="1"
            :max="50"
            :step="1"
            placeholder="不设置则使用全局配置"
            style="width: 100%"
            clearable
          />
          <div class="form-item-hint">
            检索时返回的最相关文档片段数量（1-50），如果不设置则使用系统全局配置
          </div>
        </el-form-item>
        <el-form-item label="向量存储类型" prop="vectorStoreType">
          <el-select
            v-model="formData.vectorStoreType"
            placeholder="选择向量存储类型"
            clearable
            style="width: 100%"
            :disabled="isEdit && hasDocuments"
          >
            <el-option 
              v-if="isVectorStoreTypeEnabled('qdrant')"
              label="Qdrant（向量数据库）" 
              value="qdrant"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>Qdrant（向量数据库）</span>
                <el-tag v-if="isDefaultVectorStoreType('qdrant')" type="primary" size="small">推荐</el-tag>
                <el-tag v-else type="primary" size="small">高性能</el-tag>
              </div>
            </el-option>
            <el-option 
              v-if="isVectorStoreTypeEnabled('faiss')"
              label="FAISS（本地文件存储）" 
              value="faiss"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>FAISS（本地文件存储）</span>
                <el-tag v-if="isDefaultVectorStoreType('faiss')" type="primary" size="small">推荐</el-tag>
                <el-tag v-else type="success" size="small">本地</el-tag>
              </div>
            </el-option>
            <el-option 
              v-if="isVectorStoreTypeEnabled('milvus')"
              label="Milvus（向量数据库）" 
              value="milvus"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>Milvus（向量数据库）</span>
                <el-tag v-if="isDefaultVectorStoreType('milvus')" type="primary" size="small">推荐</el-tag>
                <el-tag v-else type="warning" size="small">分布式</el-tag>
              </div>
            </el-option>
            <el-option 
              v-if="isVectorStoreTypeEnabled('chroma')"
              label="Chroma（向量数据库）" 
              value="chroma"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>Chroma（向量数据库）</span>
                <el-tag v-if="isDefaultVectorStoreType('chroma')" type="primary" size="small">推荐</el-tag>
                <el-tag v-else type="info" size="small">开源</el-tag>
              </div>
            </el-option>
            <el-option 
              v-if="isVectorStoreTypeEnabled('weaviate')"
              label="Weaviate（向量数据库）" 
              value="weaviate"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>Weaviate（向量数据库）</span>
                <el-tag v-if="isDefaultVectorStoreType('weaviate')" type="primary" size="small">推荐</el-tag>
                <el-tag v-else type="success" size="small">GraphQL</el-tag>
              </div>
            </el-option>
          </el-select>
          <div v-if="isEdit && hasDocuments" class="form-item-hint form-item-hint-warning">
            <el-icon><Warning /></el-icon>
            <span>当前使用：<strong>{{ getVectorStoreTypeName(formData.vectorStoreType) }}</strong>。已有文档，无法修改。</span>
          </div>
          <div v-else-if="formData.vectorStoreType" class="form-item-hint form-item-description">
            <span class="description-label">{{ getVectorStoreTypeName(formData.vectorStoreType) }}：</span>
            <span class="description-text">{{ getVectorStoreTypeDescription(formData.vectorStoreType) }}</span>
          </div>
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
      :lock-scroll="true"
    >
      <el-descriptions :column="2" border v-if="currentKB">
        <el-descriptions-item label="ID">{{ currentKB.id }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ currentKB.name }}</el-descriptions-item>
        <el-descriptions-item label="向量化模型" :span="2">
          <el-tag 
            v-if="getEmbeddingModelName(currentKB.embeddingModelId)" 
            :style="getModelStyle(currentKB.embeddingModelId)"
          >
            {{ getEmbeddingModelName(currentKB.embeddingModelId) }}
          </el-tag>
          <span v-else style="color: #909399;">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="Top-K检索数量" :span="2">
          <el-tag v-if="currentKB.topK" type="info">{{ currentKB.topK }}</el-tag>
          <span v-else style="color: #909399;">使用全局配置</span>
        </el-descriptions-item>
        <el-descriptions-item label="向量存储类型" :span="2">
          <el-tag 
            :type="getVectorStoreTypeTag(currentKB.vectorStoreType)"
          >
            {{ getVectorStoreTypeDisplayName(currentKB.vectorStoreType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="isActive(currentKB.status) ? 'success' : 'info'">
            {{ getStatusText(currentKB.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文档总数">{{ currentKB.documentCount || 0 }} 个</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(currentKB.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="成功文档">
          <el-tag type="success" size="small">{{ currentKB.successDocumentCount || 0 }} 个</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="失败文档">
          <el-tag type="danger" size="small">{{ currentKB.failedDocumentCount || 0 }} 个</el-tag>
        </el-descriptions-item>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElTooltip } from 'element-plus'
import { Plus, Search, Document, ArrowDown, UploadFilled, View, Edit, Check, Close, Warning } from '@element-plus/icons-vue'
import { 
  getKnowledgeBaseList, 
  createKnowledgeBase, 
  updateKnowledgeBase, 
  deleteKnowledgeBase,
  getKnowledgeBaseDetail
} from '@/api/knowledgeBase'
import { getModelConfig } from '@/api/model'
import { getModelStyle } from '@/utils/modelColor'
import { getVectorDatabaseList } from '@/api/vectorDatabase'

const knowledgeBases = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterStatus = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const router = useRouter()

const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const currentKB = ref(null)
const isEdit = ref(false)
const formRef = ref(null)
const currentEditId = ref(null)
const currentEditDocumentCount = ref(0)
const embeddingModels = ref([])
const vectorDatabases = ref([]) // 向量库配置列表
const enabledVectorStoreTypes = ref([]) // 启用的向量库类型列表

const formData = ref({
  name: '',
  description: '',
  status: 'active',
  isPublic: false, // 普通用户只能创建私有知识库
  embeddingModelId: null,
  topK: null,
  vectorStoreType: 'qdrant'
})

// 计算属性：是否有文档
const hasDocuments = computed(() => {
  return currentEditDocumentCount.value > 0
})

onMounted(() => {
  loadKnowledgeBases()
  loadEmbeddingModels()
  loadVectorDatabases()
})

// 加载向量化模型列表
const loadEmbeddingModels = async () => {
  try {
    const response = await getModelConfig()
    embeddingModels.value = (response.embeddingModels || []).filter(m => m.enabled)
  } catch (error) {
    console.error('加载向量化模型列表失败', error)
  }
}

// 获取默认向量存储类型
const getDefaultVectorStoreType = () => {
  try {
    // 确保 vectorDatabases 已初始化
    if (!vectorDatabases.value || vectorDatabases.value.length === 0) {
      return 'qdrant'
    }
    
    // 查找默认的向量库配置
    const defaultDb = vectorDatabases.value.find(db => db.isDefault && db.enabled)
    if (defaultDb && defaultDb.type) {
      return defaultDb.type.toLowerCase()
    }
    // 如果没有默认配置，使用第一个启用的配置
    const firstEnabledDb = vectorDatabases.value.find(db => db.enabled)
    if (firstEnabledDb && firstEnabledDb.type) {
      return firstEnabledDb.type.toLowerCase()
    }
    // 如果都没有，返回默认值
    return 'qdrant'
  } catch (error) {
    console.warn('获取默认向量存储类型时出错', error)
    return 'qdrant'
  }
}

// 加载向量库配置列表
const loadVectorDatabases = async () => {
  try {
    const response = await getVectorDatabaseList()
    vectorDatabases.value = response || []
    
    // 计算启用的向量库类型
    const enabledTypes = new Set()
    vectorDatabases.value.forEach(db => {
      if (db.enabled && db.type) {
        enabledTypes.add(db.type.toLowerCase())
      }
    })
    enabledVectorStoreTypes.value = Array.from(enabledTypes)
    
    // 如果表单还没有设置向量存储类型，或者当前是默认值，则更新为默认向量库类型
    if (!formData.value.vectorStoreType || formData.value.vectorStoreType === 'qdrant') {
      const defaultType = getDefaultVectorStoreType()
      formData.value.vectorStoreType = defaultType
      console.log('设置默认向量存储类型:', defaultType)
    }
  } catch (error) {
    console.error('加载向量库配置列表失败', error)
    // 如果加载失败，默认允许所有类型
    enabledVectorStoreTypes.value = ['qdrant', 'faiss', 'milvus', 'chroma', 'weaviate']
  }
}

// 检查向量库类型是否启用
const isVectorStoreTypeEnabled = (type) => {
  if (!type) return true // 如果没有指定类型，默认允许
  return enabledVectorStoreTypes.value.includes(type.toLowerCase())
}

// 检查是否为默认向量存储类型
const isDefaultVectorStoreType = (type) => {
  const defaultType = getDefaultVectorStoreType()
  return defaultType && defaultType.toLowerCase() === type.toLowerCase()
}

// 辅助函数：根据模型ID获取模型名称
const getEmbeddingModelName = (modelId) => {
  if (modelId) {
    const model = embeddingModels.value.find(m => m.id === modelId)
    return model ? model.name : null
  } else {
    // 如果没有指定模型ID，返回默认模型名称
    const defaultModel = embeddingModels.value.find(m => m.isDefault)
    return defaultModel ? defaultModel.name : null
  }
}

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

// 状态映射：前端使用active/inactive，后端使用1/0
const statusMap = {
  'active': 1,
  'inactive': 0,
  1: 'active',
  0: 'inactive'
}

// 辅助函数：判断状态是否为启用
const isActive = (status) => {
  if (typeof status === 'number') {
    return status === 1
  }
  return status === 'active'
}

// 辅助函数：获取状态文本
const getStatusText = (status) => {
  return isActive(status) ? '启用' : '禁用'
}


const filteredKnowledgeBases = computed(() => {
  let result = [...knowledgeBases.value]
  
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
    result = result.filter(kb => {
      const kbStatus = typeof kb.status === 'number' ? statusMap[kb.status] : kb.status
      return kbStatus === filterStatus.value
    })
  }
  
  return result
})

// 分页后的数据
const paginatedKnowledgeBases = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredKnowledgeBases.value.slice(start, end)
})

// 加载知识库列表（只加载当前用户的知识库）
const loadKnowledgeBases = async () => {
  loading.value = true
  try {
    // 获取当前用户ID
    const userInfoStr = localStorage.getItem('userInfo')
    let userId = null
    if (userInfoStr) {
      try {
        const userInfo = JSON.parse(userInfoStr)
        userId = userInfo.userId
      } catch (e) {
        console.error('解析用户信息失败', e)
      }
    }
    
    const params = {}
    if (userId) {
      params.userId = userId // 只获取当前用户的知识库
    }
    if (filterStatus.value) {
      params.status = statusMap[filterStatus.value]
    }
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }
    
    const response = await getKnowledgeBaseList(params)
    knowledgeBases.value = response.map(kb => ({
      ...kb,
      status: typeof kb.status === 'number' ? statusMap[kb.status] : kb.status
    }))
  } catch (error) {
    ElMessage.error('加载知识库列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadKnowledgeBases()
}

const handleFilter = () => {
  currentPage.value = 1
  loadKnowledgeBases()
}

const handleCreate = () => {
  isEdit.value = false
  currentEditId.value = null
  currentEditDocumentCount.value = 0
  try {
    // 获取默认向量存储类型，如果出错则使用 'qdrant' 作为后备
    let defaultVectorStoreType = 'qdrant'
    try {
      defaultVectorStoreType = getDefaultVectorStoreType()
    } catch (e) {
      console.warn('获取默认向量存储类型失败，使用默认值 qdrant', e)
    }
    
    formData.value = {
      name: '',
      description: '',
      status: 'active',
      isPublic: false, // 普通用户只能创建私有知识库
      embeddingModelId: null,
      topK: null,
      vectorStoreType: defaultVectorStoreType
    }
    dialogVisible.value = true
  } catch (error) {
    console.error('创建知识库对话框打开失败', error)
    ElMessage.error('打开创建对话框失败：' + (error.message || '未知错误'))
  }
}

const handleEdit = (row) => {
  isEdit.value = true
  currentEditId.value = row.id
  currentEditDocumentCount.value = row.documentCount || 0
  formData.value = {
    name: row.name,
    description: row.description || '',
    status: typeof row.status === 'number' ? statusMap[row.status] : row.status,
    isPublic: false, // 普通用户只能创建私有知识库
    embeddingModelId: row.embeddingModelId || null,
    topK: row.topK || null,
    vectorStoreType: row.vectorStoreType || 'qdrant'
  }
  dialogVisible.value = true
}

const handleView = async (row) => {
  try {
    const response = await getKnowledgeBaseDetail(row.id)
    currentKB.value = {
      ...response,
      status: typeof response.status === 'number' ? statusMap[response.status] : response.status
    }
    viewDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取知识库详情失败：' + (error.message || '未知错误'))
  }
}

const handleUploadDocs = (row) => {
  router.push(`/user/knowledge-base/${row.id}/documents/upload`)
}

const handleListDocs = (row) => {
  router.push(`/user/knowledge-base/${row.id}/documents/list`)
}

const handleDropdownCommand = (command, row) => {
  switch (command) {
    case 'view':
      handleView(row)
      break
    case 'edit':
      handleEdit(row)
      break
    case 'upload':
      handleUploadDocs(row)
      break
    case 'list':
      handleListDocs(row)
      break
  }
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
  ).then(async () => {
    try {
      await deleteKnowledgeBase(row.id)
      ElMessage.success('删除成功')
      loadKnowledgeBases()
    } catch (error) {
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }).catch(() => {
    // 取消操作
  })
}

const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const data = {
          name: formData.value.name,
          description: formData.value.description,
          status: statusMap[formData.value.status],
          isPublic: false // 普通用户只能创建私有知识库
        }
        
        // 添加向量化模型ID（如果选择了）
        if (formData.value.embeddingModelId) {
          data.embeddingModelId = formData.value.embeddingModelId
        }
        
        // 添加topK（如果设置了）
        if (formData.value.topK !== null && formData.value.topK !== undefined) {
          data.topK = formData.value.topK
        }
        
        // 添加vectorStoreType（如果设置了）
        if (formData.value.vectorStoreType) {
          data.vectorStoreType = formData.value.vectorStoreType
        }
        
        if (isEdit.value) {
          await updateKnowledgeBase(currentEditId.value, data)
          ElMessage.success('编辑成功')
        } else {
          await createKnowledgeBase(data)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        loadKnowledgeBases()
      } catch (error) {
        ElMessage.error((isEdit.value ? '编辑' : '创建') + '失败：' + (error.message || '未知错误'))
      }
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
  if (typeof date === 'string') {
    return date
  }
  if (date instanceof Date) {
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  }
  // 如果是时间戳
  const d = new Date(date)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 获取向量存储类型描述
const getVectorStoreTypeDescription = (type) => {
  if (!type) return ''
  const descriptions = {
    'qdrant': '分布式向量数据库，适合生产环境。',
    'faiss': '本地文件存储，无需额外服务，适合开发测试。',
    'milvus': '开源向量数据库，支持大规模向量检索，需要独立服务器，使用 gRPC 协议。',
    'chroma': '开源向量数据库，轻量级，易于部署，支持 HTTP REST API。',
    'weaviate': '开源向量数据库，支持 GraphQL 和 REST API，提供强大的语义搜索能力。'
  }
  return descriptions[type.toLowerCase()] || ''
}

// 获取向量存储类型名称（简短）
const getVectorStoreTypeName = (type) => {
  if (type === 'faiss') return 'FAISS'
  if (type === 'milvus') return 'Milvus'
  if (type === 'chroma') return 'Chroma'
  if (type === 'weaviate') return 'Weaviate'
  return 'Qdrant'
}

// 获取向量存储类型显示名称（完整）
const getVectorStoreTypeDisplayName = (type) => {
  if (type === 'faiss') return 'FAISS（本地文件存储）'
  if (type === 'milvus') return 'Milvus（向量数据库）'
  if (type === 'chroma') return 'Chroma（向量数据库）'
  if (type === 'weaviate') return 'Weaviate（向量数据库）'
  return 'Qdrant（向量数据库）'
}

// 获取向量存储类型标签类型
const getVectorStoreTypeTag = (type) => {
  if (type === 'faiss') return 'success'
  if (type === 'milvus') return 'warning'
  if (type === 'chroma') return 'info'
  if (type === 'weaviate') return 'success'
  return 'primary'
}

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

.doc-management {
  padding: 10px 0;
}

.upload-section {
  padding: 20px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  background-color: #fafafa;
}

.upload-actions {
  display: flex;
  gap: 10px;
}

.doc-list-section {
  margin-top: 20px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 10px;
}

/* 紧凑表格样式 */
.compact-table :deep(.el-table__cell) {
  padding: 8px 0;
}

.compact-table :deep(.el-button) {
  padding: 5px 10px;
  font-size: 12px;
}

.compact-table :deep(.el-tag) {
  font-size: 12px;
  padding: 0 6px;
  height: 22px;
  line-height: 22px;
}

.action-buttons-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.action-buttons-row .el-button {
  flex-shrink: 0;
  margin: 0;
}

.action-buttons-row .el-button + .el-button {
  margin-left: 0;
}

.form-item-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  line-height: 1.5;
}

.form-item-hint-warning {
  color: #e6a23c;
  display: flex;
  align-items: flex-start;
  gap: 4px;
}

.form-item-hint-warning .el-icon {
  margin-top: 2px;
  flex-shrink: 0;
}

.form-item-description {
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
  border-left: 3px solid #409eff;
}

.form-item-description .description-label {
  font-weight: 500;
  color: #303133;
}

.form-item-description .description-text {
  color: #606266;
}
</style>

