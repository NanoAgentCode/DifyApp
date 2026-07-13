<template>
  <div class="knowledge-base-management">
    <KnowledgeBaseListPanel v-model:search-keyword="searchKeyword" v-model:filter-vector-store-type="filterVectorStoreType" v-model:filter-status="filterStatus" v-model:current-page="currentPage" v-model:page-size="pageSize" :user-mode="userMode" :knowledge-bases="tableKnowledgeBases" :loading="loading" :display-total="displayTotal" :get-status-text="getStatusText" :is-active="isActive" :get-embedding-model-name="getEmbeddingModelName" :get-model-plain-style="getModelPlainStyle" :get-vector-store-type-tag="getVectorStoreTypeTag" :get-vector-store-instance-name="getVectorStoreInstanceName" :format-date="formatDate" @search="handleSearch" @filter="handleFilter" @create="handleCreate" @import="handleImport" @back="handleBack" @summary="handleGenerateSummary" @delete="handleDelete" @dropdown="({ command, row }) => handleDropdownCommand(command, row)" @size-change="handleSizeChange" @page-change="handlePageChange" />
    <KnowledgeBaseFormDialog ref="formRef" v-model:visible="dialogVisible" :dialog-title="dialogTitle" :form-data="formData" :form-rules="formRules" :is-admin="isAdmin" :is-edit="isEdit" :has-documents="hasDocuments" :embedding-models="embeddingModels" :creatable-vector-databases="creatableVectorDatabases" :get-embedding-model-name="getEmbeddingModelName" :get-model-plain-style="getModelPlainStyle" :get-vector-database-document-count="getVectorDatabaseDocumentCount" :get-vector-store-type-tag="getVectorStoreTypeTag" :get-vector-store-type-name="getVectorStoreTypeName" :get-vector-store-type-name-from-value="getVectorStoreTypeNameFromValue" :get-vector-store-type-description-from-value="getVectorStoreTypeDescriptionFromValue" @close="handleDialogClose" @submit="handleSubmit" />
    <KnowledgeBaseDetailDialog v-model:visible="viewDialogVisible" :current-k-b="currentKB" :get-embedding-model-name="getEmbeddingModelName" :get-model-plain-style="getModelPlainStyle" :get-vector-store-type-tag="getVectorStoreTypeTag" :get-vector-store-type-display-name="getVectorStoreTypeDisplayName" :is-active="isActive" :get-status-text="getStatusText" :format-date="formatDate" @generate-summary="handleGenerateSummaryFromView" />
    <KnowledgeBaseImportDialog ref="importFormRef" v-model:visible="importDialogVisible" :import-file-list="importFileList" :importing="importing" :preview-files="previewFiles" :import-form="importForm" :import-rules="importRules" :is-admin="isAdmin" :format-file-size="formatFileSize" @file-change="handleFileChange" @reselect-file="handleReSelectFile" @confirm-import="handleConfirmImport" />
  </div>
</template>
<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getKnowledgeBaseList,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  getKnowledgeBaseDetail,
  generateKnowledgeBaseSummary,
  exportKnowledgeBase,
  importKnowledgeBase,
  previewZipFile
} from '@/api/knowledgeBase'
import { getModelConfig } from '@/api/model'
import { getModelPlainStyle } from '@/utils/modelColor'
import { getVectorDatabaseList } from '@/api/vectorDatabase'
import KnowledgeBaseListPanel from '@/components/knowledgebase/KnowledgeBaseListPanel.vue'
import KnowledgeBaseFormDialog from '@/components/knowledgebase/KnowledgeBaseFormDialog.vue'
import KnowledgeBaseDetailDialog from '@/components/knowledgebase/KnowledgeBaseDetailDialog.vue'
import KnowledgeBaseImportDialog from '@/components/knowledgebase/KnowledgeBaseImportDialog.vue'

const props = defineProps({
  userMode: {
    type: Boolean,
    default: false
  }
})

const knowledgeBases = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterStatus = ref('')
const filterVectorStoreType = ref('')
const currentPage = ref(1)
const pageSize = ref(10) // 默认每页10条
const total = ref(0)
let searchTimer = null // 搜索防抖定时器
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const currentKB = ref(null)
const isEdit = ref(false)
const formRef = ref(null)
const currentEditId = ref(null)
const currentEditDocumentCount = ref(0) // 当前编辑的知识库的文档数量

const formData = ref({
  name: '',
  description: '',
  status: 'active',
  isPublic: false,
  embeddingModelId: null,
  topK: null,
  vectorStoreType: 'qdrant'
})

const embeddingModels = ref([])
const vectorDatabases = ref([]) // 向量库配置列表
const enabledVectorStoreTypes = ref([]) // 启用的向量库类型列表
const creatableVectorDatabases = computed(() =>
  (vectorDatabases.value || []).filter(db => db.enabled && (db.allowCreateKnowledgeBase !== false))
)

// 获取用户信息，判断是否是管理员
const userInfo = ref(null)
const isAdmin = computed(() => {
  return userInfo.value && userInfo.value.role === 1
})

const userMode = computed(() => props.userMode)

onMounted(() => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      userInfo.value = JSON.parse(userInfoStr)
    } catch (e) {
      console.error('解析用户信息失败', e)
    }
  }
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
    if (!formData.value.vectorStoreType || formData.value.vectorStoreType === 'qdrant' || !formData.value.vectorStoreType.includes('_')) {
      const defaultType = getDefaultVectorStoreType()
      // 查找默认实例，转换为 type_id 格式
      const defaultDb = vectorDatabases.value.find(db =>
        db.type === defaultType && db.isDefault && db.enabled
      )
      if (defaultDb) {
        formData.value.vectorStoreType = `${defaultDb.type}_${defaultDb.id}`
      } else {
        const firstDb = vectorDatabases.value.find(db =>
          db.type === defaultType && db.enabled
        )
        if (firstDb) {
          formData.value.vectorStoreType = `${firstDb.type}_${firstDb.id}`
        } else {
          formData.value.vectorStoreType = defaultType
        }
      }
      console.log('设置默认向量存储类型:', formData.value.vectorStoreType)
    }
  } catch (error) {
    console.error('加载向量库配置列表失败', error)
    // 如果加载失败，默认允许所有类型
    enabledVectorStoreTypes.value = ['qdrant', 'faiss', 'milvus', 'chroma', 'weaviate', 'elasticsearch', 'pgvector']
  }
}

// 检查向量库类型是否启用
const isVectorStoreTypeEnabled = (type) => {
  if (!type) return true // 如果没有指定类型，默认允许
  return enabledVectorStoreTypes.value.includes(type.toLowerCase())
}

// 获取指定类型的所有启用的向量库实例
const getEnabledVectorDatabasesByType = (type) => {
  if (!vectorDatabases.value || vectorDatabases.value.length === 0) {
    return []
  }
  return vectorDatabases.value.filter(db =>
    db.type && db.type.toLowerCase() === type.toLowerCase() && db.enabled
  )
}

// 从 type_id 格式中提取类型
const extractTypeFromValue = (value) => {
  if (!value) return ''
  if (value.includes('_')) {
    const parts = value.split('_')
    return parts.slice(0, -1).join('_')
  }
  return value
}

// 从 value 获取类型名称（支持 type_id 格式）
const getVectorStoreTypeNameFromValue = (value) => {
  const type = extractTypeFromValue(value)
  return getVectorStoreTypeName(type)
}

// 从 value 获取类型描述（支持 type_id 格式）
const getVectorStoreTypeDescriptionFromValue = (value) => {
  const type = extractTypeFromValue(value)
  return getVectorStoreTypeDescription(type)
}

// 检查是否为默认向量存储类型
const isDefaultVectorStoreType = (type) => {
  const defaultType = getDefaultVectorStoreType()
  return defaultType && defaultType.toLowerCase() === type.toLowerCase()
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

const filteredKnowledgeBases = computed(() => {
  if (!userMode.value) {
    return knowledgeBases.value
  }

  let result = [...knowledgeBases.value]
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(kb =>
      kb.name.toLowerCase().includes(keyword) ||
      (kb.description && kb.description.toLowerCase().includes(keyword))
    )
  }
  if (filterStatus.value) {
    result = result.filter(kb => kb.status === filterStatus.value)
  }
  if (filterVectorStoreType.value) {
    result = result.filter(kb => kb.vectorStoreType === filterVectorStoreType.value)
  }
  return result
})

const tableKnowledgeBases = computed(() => {
  if (!userMode.value) {
    return knowledgeBases.value
  }
  const start = (currentPage.value - 1) * pageSize.value
  return filteredKnowledgeBases.value.slice(start, start + pageSize.value)
})

const displayTotal = computed(() => userMode.value ? filteredKnowledgeBases.value.length : total.value)


// 加载知识库列表
const loadKnowledgeBases = async () => {
  loading.value = true
  try {
    const params = userMode.value
      ? { userId: userInfo.value?.userId }
      : { page: currentPage.value, pageSize: pageSize.value }
    if (filterStatus.value) {
      params.status = statusMap[filterStatus.value]
    }
    if (!userMode.value && filterVectorStoreType.value) {
      params.vectorStoreType = filterVectorStoreType.value
    }
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }

    const response = await getKnowledgeBaseList(params)

    // 检查是否是分页响应
    if (response && typeof response === 'object' && 'content' in response && 'total' in response) {
      // 分页响应
      knowledgeBases.value = (response.content || []).map(kb => ({
        ...kb,
        status: typeof kb.status === 'number' ? statusMap[kb.status] : kb.status
      }))
      total.value = response.total || 0
    } else {
      // 兼容旧接口（非分页响应）
      knowledgeBases.value = (Array.isArray(response) ? response : []).map(kb => ({
        ...kb,
        status: typeof kb.status === 'number' ? statusMap[kb.status] : kb.status
      }))
      total.value = knowledgeBases.value.length
    }
  } catch (error) {
    ElMessage.error('加载知识库列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 搜索防抖处理
const handleSearch = () => {
  if (userMode.value) {
    currentPage.value = 1
    loadKnowledgeBases()
    return
  }
  // 清除之前的定时器
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  // 设置新的定时器，500ms后执行搜索
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    loadKnowledgeBases()
  }, 500)
}

const handleFilter = () => {
  currentPage.value = 1
  loadKnowledgeBases()
}

const handleCreate = () => {
  isEdit.value = false
  currentEditId.value = null
  try {
    // 获取默认向量存储实例，优先选择 isDefault=true 的实例
    let defaultVectorStoreValue = 'qdrant'
    if (vectorDatabases.value && vectorDatabases.value.length > 0) {
      // 查找默认实例（isDefault=true 且 enabled=true）
    const defaultDb = vectorDatabases.value.find(db => db.isDefault && db.enabled && (db.allowCreateKnowledgeBase !== false))
    if (defaultDb) {
      defaultVectorStoreValue = `${defaultDb.type}_${defaultDb.id}`
    } else {
      // 如果没有默认实例，使用第一个启用的且允许新建知识库的实例
      const firstDb = vectorDatabases.value.find(db => db.enabled && (db.allowCreateKnowledgeBase !== false))
        if (firstDb) {
          defaultVectorStoreValue = `${firstDb.type}_${firstDb.id}`
        } else {
          // 如果都没有启用的实例，使用默认类型
          try {
            const defaultType = getDefaultVectorStoreType()
            defaultVectorStoreValue = defaultType
          } catch (e) {
            console.warn('获取默认向量存储类型失败，使用默认值 qdrant', e)
          }
        }
      }
    } else {
      // 如果向量库列表还未加载，使用默认类型
      try {
        const defaultType = getDefaultVectorStoreType()
        defaultVectorStoreValue = defaultType
      } catch (e) {
        console.warn('获取默认向量存储类型失败，使用默认值 qdrant', e)
      }
    }

    formData.value = {
      name: '',
      description: '',
      status: 'active',
      isPublic: false, // 默认私有
      embeddingModelId: null,
      topK: null,
      vectorStoreType: defaultVectorStoreValue
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
    isPublic: row.isPublic !== undefined ? row.isPublic : false,
    embeddingModelId: row.embeddingModelId || null,
    topK: row.topK || null,
    vectorStoreType: row.vectorStoreType || 'qdrant'
  }
  dialogVisible.value = true
}

const handleView = async (row) => {
  try {
    const response = await getKnowledgeBaseDetail(row.id)
    // request工具已经返回response.data，所以直接使用response
    currentKB.value = {
      ...response,
      status: typeof response.status === 'number' ? statusMap[response.status] : response.status
    }
    viewDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取知识库详情失败：' + (error.message || '未知错误'))
  }
}

const handleGenerateSummaryFromView = async () => {
  if (!currentKB.value) return
  try {
    ElMessageBox.confirm(
      `确定要为知识库"${currentKB.value.name}"生成智能摘要吗？这将基于知识库中的文档内容自动生成摘要。`,
      '生成摘要确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    ).then(async () => {
      try {
        ElMessage.info('正在生成摘要，请稍候...')
        const response = await generateKnowledgeBaseSummary(currentKB.value.id)
        ElMessage.success('摘要生成成功')
        // 刷新知识库详情
        const updatedResponse = await getKnowledgeBaseDetail(currentKB.value.id)
        currentKB.value = {
          ...updatedResponse,
          status: typeof updatedResponse.status === 'number' ? statusMap[updatedResponse.status] : updatedResponse.status
        }
      } catch (error) {
        ElMessage.error('生成摘要失败：' + (error.message || '未知错误'))
      }
    }).catch(() => {
      // 取消操作
    })
  } catch (error) {
    ElMessage.error('操作失败：' + (error.message || '未知错误'))
  }
}

const router = useRouter()

const handleBack = () => {
  router.push('/user/chat')
}

const handleDocuments = (row) => {
  const prefix = userMode.value ? '/user' : '/admin'
  router.push(`${prefix}/knowledge-base/${row.id}/documents`)
}

const handleGenerateSummary = async (row) => {
  try {
    ElMessageBox.confirm(
      `确定要为知识库"${row.name}"生成智能摘要吗？这将基于知识库中的文档内容自动生成摘要。`,
      '生成摘要确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    ).then(async () => {
      try {
        ElMessage.info('正在生成摘要，请稍候...')
        const response = await generateKnowledgeBaseSummary(row.id)
        ElMessage.success('摘要生成成功')
        // 刷新知识库列表
        await loadKnowledgeBases()
      } catch (error) {
        ElMessage.error('生成摘要失败：' + (error.message || '未知错误'))
      }
    }).catch(() => {
      // 取消操作
    })
  } catch (error) {
    ElMessage.error('操作失败：' + (error.message || '未知错误'))
  }
}

const handleDropdownCommand = (command, row) => {
  switch (command) {
    case 'view':
      handleView(row)
      break
    case 'edit':
      handleEdit(row)
      break
    case 'documents':
      handleDocuments(row)
      break
    case 'export':
      handleExport(row)
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

// 导出知识库
const handleExport = async (row) => {
  try {
    ElMessage.info('正在导出知识库，请稍候...')
    const response = await exportKnowledgeBase(row.id)

    // 创建下载链接
    const url = window.URL.createObjectURL(response)
    const link = document.createElement('a')
    link.href = url
    link.download = `knowledge-base-${row.id}-${Date.now()}.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败：' + (error.message || '未知错误'))
  }
}

// 导入相关状态
const importDialogVisible = ref(false)
const importFormRef = ref(null)
const importFileList = ref([])
const importing = ref(false)
const previewFiles = ref([])
const defaultName = ref('')

const importForm = ref({
  name: '',
  description: '',
  vectorStoreType: '',
  isPublic: false
})

const importRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 1, max: 100, message: '长度在 1 到 100 个字符', trigger: 'blur' }
  ]
}

// 打开导入对话框
const handleImport = () => {
  importDialogVisible.value = true
  importForm.value = {
    name: '',
    description: '',
    vectorStoreType: '',
    isPublic: false
  }
  importFileList.value = []
  previewFiles.value = []
  defaultName.value = ''
}

// 提取知识库名称的工具函数
const extractKnowledgeBaseName = (fileName) => {
  // 去掉.zip扩展名（不区分大小写）
  let name = fileName.replace(/\.zip$/i, '')

  // 去掉路径（只取文件名，支持Windows和Unix路径分隔符）
  name = name.split('/').pop().split('\\').pop()

  // 如果提取失败或为空，使用默认值
  return name.trim() || '导入的知识库'
}

// 文件上传处理
const handleFileChange = (file, fileList) => {
  importFileList.value = fileList

  if (fileList.length > 0) {
    const zipFile = fileList[0].raw || fileList[0]

    // 提取默认知识库名称
    const fileName = zipFile.name
    const extractedName = extractKnowledgeBaseName(fileName)

    // 如果知识库名称为空，自动填充默认名称
    if (!importForm.value.name) {
      importForm.value.name = extractedName
    }

    // 保存默认名称用于显示
    defaultName.value = extractedName

    // 可选：预览ZIP内容
    previewZipContent(zipFile)
  }
}

// 重新选择文件
const handleReSelectFile = () => {
  importFileList.value = []
  previewFiles.value = []
  defaultName.value = ''
  importForm.value.name = ''
}

// 预览ZIP内容（可选）
const previewZipContent = async (file) => {
  try {
    const formData = new FormData()
    formData.append('file', file)
    const result = await previewZipFile(formData)
    previewFiles.value = result.files || []
  } catch (error) {
    console.error('预览ZIP失败:', error)
    // 预览失败不影响导入
  }
}

// 确认导入
const handleConfirmImport = async () => {
  if (!importFormRef.value) return

  await importFormRef.value.validate(async (valid) => {
    if (!valid) return

    if (importFileList.value.length === 0) {
      ElMessage.warning('请选择ZIP文件')
      return
    }

    importing.value = true
    try {
      const formData = new FormData()
      formData.append('file', importFileList.value[0].raw)
      formData.append('knowledgeBaseName', importForm.value.name)

      if (importForm.value.description) {
        formData.append('description', importForm.value.description)
      }
      if (importForm.value.vectorStoreType) {
        formData.append('vectorStoreType', importForm.value.vectorStoreType)
      }
      if (importForm.value.isPublic !== undefined) {
        formData.append('isPublic', importForm.value.isPublic)
      }

      const result = await importKnowledgeBase(formData)

      if (result.status === 'SUCCESS') {
        ElMessage.success(`导入成功！共导入 ${result.successCount} 个文档`)
      } else if (result.status === 'PARTIAL_SUCCESS') {
        ElMessage.warning(`部分导入成功！成功: ${result.successCount}，失败: ${result.failedCount}`)
        if (result.errors && result.errors.length > 0) {
          console.error('导入错误:', result.errors)
        }
      } else {
        ElMessage.error('导入失败：' + (result.message || '未知错误'))
      }

      // 关闭对话框并刷新列表
      importDialogVisible.value = false
      loadKnowledgeBases()
    } catch (error) {
      ElMessage.error('导入失败：' + (error.message || '未知错误'))
    } finally {
      importing.value = false
    }
  })
}

const doSubmit = async (force = false) => {
  const data = {
    name: formData.value.name,
    description: formData.value.description,
    status: statusMap[formData.value.status]
  }

  // 只有管理员可以设置公开/私有，普通用户创建的知识库强制为私有
  if (isAdmin.value && formData.value.isPublic !== undefined) {
    data.isPublic = formData.value.isPublic
  }

  // 添加向量化模型ID（如果选择了）
  if (formData.value.embeddingModelId) {
    data.embeddingModelId = formData.value.embeddingModelId
  }

  // 添加topK（如果设置了）
  if (formData.value.topK !== null && formData.value.topK !== undefined) {
    data.topK = formData.value.topK
  }

  // 添加vectorStoreType和vectorDatabaseId（如果设置了）
  // 如果value是 type_id 格式，提取type和ID
  if (formData.value.vectorStoreType) {
    const vectorStoreValue = formData.value.vectorStoreType
    // 检查是否是 type_id 格式
    if (vectorStoreValue.includes('_')) {
      const parts = vectorStoreValue.split('_')
      // 提取类型部分（除了最后一个下划线后的ID）
      const type = parts.slice(0, -1).join('_')
      // 提取ID部分（最后一个下划线后的数字）
      const idStr = parts[parts.length - 1]
      const id = parseInt(idStr, 10)
      if (!isNaN(id)) {
        data.vectorDatabaseId = id
        data.vectorStoreType = type
      } else {
        // 如果ID解析失败，只设置类型
        data.vectorStoreType = type
      }
    } else {
      // 兼容旧格式（只有类型）
      data.vectorStoreType = vectorStoreValue
    }
  }

  if (isEdit.value) {
    await updateKnowledgeBase(currentEditId.value, data)
    ElMessage.success('编辑成功')
    dialogVisible.value = false
    loadKnowledgeBases()
        } else {
          try {
            await createKnowledgeBase(data, force)
            ElMessage.success('创建成功')
            dialogVisible.value = false
            loadKnowledgeBases()
          } catch (error) {
            // 检查是否是重复名称错误（通过状态码或错误代码）
            const isDuplicateError = error.response && (
              error.response.status === 409 ||
              (error.response.data && error.response.data.code === 'DUPLICATE_NAME')
            )

            if (isDuplicateError && !force) {
              // 显示确认对话框
              try {
                const errorMessage = error.response?.data?.error || `已存在名称为 "${formData.value.name}" 的知识库，是否继续创建？`
                await ElMessageBox.confirm(
                  errorMessage,
                  '提示',
                  {
                    confirmButtonText: '继续创建',
                    cancelButtonText: '取消',
                    type: 'warning'
                  }
                )
                // 用户确认，强制创建
                await doSubmit(true)
              } catch (confirmError) {
                // 用户取消，不做任何操作
                if (confirmError !== 'cancel') {
                  throw confirmError
                }
              }
              return
            }
            throw error
          }
        }
}

const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await doSubmit(false)
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error((isEdit.value ? '编辑' : '创建') + '失败：' + (error.message || '未知错误'))
        }
      }
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  // 重置表单数据，使用动态获取的默认向量存储类型
  let defaultVectorStoreValue = getDefaultVectorStoreType()
  // 查找默认实例，转换为 type_id 格式
  if (vectorDatabases.value && vectorDatabases.value.length > 0) {
    const defaultDb = vectorDatabases.value.find(db =>
      db.type === defaultVectorStoreValue && db.isDefault && db.enabled
    )
    if (defaultDb) {
      defaultVectorStoreValue = `${defaultDb.type}_${defaultDb.id}`
    } else {
      const firstDb = vectorDatabases.value.find(db =>
        db.type === defaultVectorStoreValue && db.enabled
      )
      if (firstDb) {
        defaultVectorStoreValue = `${firstDb.type}_${firstDb.id}`
      }
    }
  }
  formData.value = {
    name: '',
    description: '',
    status: 'active',
    isPublic: false,
    embeddingModelId: null,
    topK: null,
    vectorStoreType: defaultVectorStoreValue
  }
  currentEditDocumentCount.value = 0
}

// 计算属性：判断当前编辑的知识库是否有文档
const hasDocuments = computed(() => {
  return currentEditDocumentCount.value > 0
})

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  if (!userMode.value) {
    loadKnowledgeBases()
  }
}

const handlePageChange = (page) => {
  currentPage.value = page
  if (!userMode.value) {
    loadKnowledgeBases()
  }
}

// 组件卸载时清理定时器
onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
})

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
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

// 获取向量存储类型名称（简短）
// 获取向量存储类型描述
const getVectorStoreTypeDescription = (type) => {
  if (!type) return ''
  const descriptions = {
    'qdrant': '分布式向量数据库，适合生产环境。',
    'faiss': '本地文件存储，无需额外服务，适合开发测试。',
    'milvus': '开源向量数据库，支持大规模向量检索，需要独立服务器，使用 gRPC 协议。',
    'chroma': '开源向量数据库，轻量级，易于部署，支持 HTTP REST API。',
    'weaviate': '开源向量数据库，支持 GraphQL 和 REST API，提供强大的语义搜索能力。',
    'elasticsearch': '企业级分布式搜索和分析引擎，支持向量搜索、全文检索和混合搜索。具备高可用性、水平扩展能力强，适合大规模生产环境。',
    'pgvector': 'PostgreSQL 的向量扩展，利用 PostgreSQL 的成熟生态和 ACID 特性，适合需要事务支持的场景。'
  }
  return descriptions[type.toLowerCase()] || ''
}

const getVectorStoreTypeName = (type) => {
  if (type === 'faiss') return 'FAISS'
  if (type === 'milvus') return 'Milvus'
  if (type === 'chroma') return 'Chroma'
  if (type === 'weaviate') return 'Weaviate'
  if (type === 'elasticsearch') return 'Elasticsearch'
  return 'Qdrant'
}

// 获取向量存储类型显示名称（完整）
const getVectorStoreTypeDisplayName = (type) => {
  if (type === 'faiss') return 'FAISS（本地文件存储）'
  if (type === 'milvus') return 'Milvus（向量数据库）'
  if (type === 'chroma') return 'Chroma（向量数据库）'
  if (type === 'weaviate') return 'Weaviate（向量数据库）'
  if (type === 'elasticsearch') return 'Elasticsearch（向量数据库）'
  return 'Qdrant（向量数据库）'
}

// 根据类型获取向量库实例名称
const getVectorStoreInstanceName = (row) => {
  if (!row) return '-'
  if (!vectorDatabases.value || vectorDatabases.value.length === 0) {
    // 如果还没有加载向量库列表，返回类型名称作为后备
    return getVectorStoreTypeName(row.vectorStoreType)
  }

  // 优先使用 vectorDatabaseId 进行精确匹配
  if (row.vectorDatabaseId) {
    const db = vectorDatabases.value.find(db =>
      db.id === row.vectorDatabaseId && db.enabled
    )
    if (db) {
      return db.name
    }
  }

  // 兼容旧数据：如果没有 vectorDatabaseId，则按类型查找
  const type = row.vectorStoreType
  if (!type) return '-'

  // 查找该类型的默认实例
  const defaultDb = vectorDatabases.value.find(db =>
    db.type === type && db.isDefault && db.enabled
  )
  if (defaultDb) {
    return defaultDb.name
  }
  // 如果没有默认实例，使用第一个启用的实例
  const firstDb = vectorDatabases.value.find(db =>
    db.type === type && db.enabled
  )
  if (firstDb) {
    return firstDb.name
  }
  // 如果找不到实例，返回类型名称作为后备
  return getVectorStoreTypeName(type)
}

// 获取向量存储类型标签类型
const getVectorStoreTypeTag = (type) => {
  if (type === 'faiss') return 'success'
  if (type === 'milvus') return 'warning'
  if (type === 'chroma') return 'info'
  if (type === 'weaviate') return 'success'
  if (type === 'elasticsearch') return 'warning'
  return 'primary'
}

// 获取向量库实例的文档数量（只统计当前管理员的文档）
const getVectorDatabaseDocumentCount = (db) => {
  if (!knowledgeBases.value || knowledgeBases.value.length === 0) {
    return 0
  }

  // 获取当前管理员ID
  const userInfoStr = localStorage.getItem('userInfo')
  let currentUserId = null
  if (userInfoStr) {
    try {
      const userInfo = JSON.parse(userInfoStr)
      currentUserId = userInfo.userId
    } catch (e) {
      console.error('解析用户信息失败', e)
    }
  }

  // 根据向量库实例ID精确统计文档数量（只统计当前管理员的知识库）
  let totalCount = 0
  knowledgeBases.value.forEach(kb => {
    // 只统计当前管理员创建的知识库
    if (currentUserId && kb.creatorId !== currentUserId) {
      return // 跳过其他用户的知识库
    }

    // 优先使用 vectorDatabaseId 进行精确匹配
    if (kb.vectorDatabaseId === db.id) {
      totalCount += (kb.documentCount || 0)
    } else if (!kb.vectorDatabaseId && kb.vectorStoreType === db.type) {
      // 兼容旧数据：如果没有 vectorDatabaseId，则按类型匹配
      totalCount += (kb.documentCount || 0)
    }
  })
  return totalCount
}

</script>

<style src="./KnowledgeBaseManagement.css"></style>
