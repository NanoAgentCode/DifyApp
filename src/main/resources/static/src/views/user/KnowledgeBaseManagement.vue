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
            <el-tag :type="isActive(row.status) ? 'success' : 'info'">
              {{ getStatusText(row.status) }}
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
        <el-form-item>
          <el-alert
            type="info"
            :closable="false"
            show-icon
          >
            <template #title>
              <span>普通用户只能创建私有知识库</span>
            </template>
          </el-alert>
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

    <!-- 文件管理对话框 -->
    <el-dialog
      v-model="docDialogVisible"
      :title="`管理文档 - ${currentKBForDocs?.name || ''}`"
      width="900px"
      @close="handleDocDialogClose"
    >
      <div class="doc-management">
        <!-- 文件上传区域 -->
        <div class="upload-section">
          <el-upload
            ref="uploadRef"
            :action="uploadAction"
            :headers="uploadHeaders"
            :data="uploadData"
            :file-list="fileList"
            :auto-upload="false"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :before-upload="beforeUpload"
            :limit="10"
            drag
            multiple
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              将文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持上传 pdf、doc、docx、txt、md、xls、xlsx、ppt、pptx、png、jpg、jpeg、gif 格式文件，单个文件不超过100MB
              </div>
            </template>
          </el-upload>
          <div class="upload-actions" style="margin-top: 10px">
            <el-button type="primary" @click="handleUploadFiles" :loading="uploading">
              开始上传
            </el-button>
            <el-button @click="clearFileList">清空列表</el-button>
          </div>
        </div>

        <!-- 文件列表 -->
        <div class="doc-list-section" style="margin-top: 30px">
          <div class="section-title">
            <span>文档列表</span>
            <el-button size="small" @click="loadDocuments" :loading="docLoading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
          <el-table
            :data="documents"
            v-loading="docLoading"
            stripe
            size="small"
            style="margin-top: 10px"
            class="compact-table"
          >
            <el-table-column prop="originalFileName" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column label="类型" width="70" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.fileType || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="90" align="center">
              <template #default="{ row }">
                {{ formatFileSize(row.fileSize) }}
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="上传时间" width="150" align="center">
              <template #default="{ row }">
                {{ formatDate(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="向量化状态" width="110" align="center">
              <template #default="{ row }">
                <el-tooltip 
                  v-if="row.vectorizedError" 
                  :content="row.vectorizedError" 
                  placement="top"
                >
                  <el-tag :type="getVectorizedStatusType(row.vectorizedStatus)" size="small" effect="plain">
                    {{ getVectorizedStatusText(row.vectorizedStatus) }}
                  </el-tag>
                </el-tooltip>
                <el-tag 
                  v-else
                  :type="getVectorizedStatusType(row.vectorizedStatus)" 
                  size="small"
                  effect="plain"
                >
                  {{ getVectorizedStatusText(row.vectorizedStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right" align="center">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="handleDownloadDoc(row)">下载</el-button>
                <el-button 
                  size="small" 
                  type="warning" 
                  @click="handleReindexDoc(row)"
                  :loading="reindexingDocId === row.id"
                  :disabled="row.vectorizedStatus === 1"
                >
                  重向量化
                </el-button>
                <el-button size="small" type="danger" @click="handleDeleteDoc(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="docDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Document, UploadFilled, Refresh } from '@element-plus/icons-vue'
import { 
  getKnowledgeBaseList, 
  createKnowledgeBase, 
  updateKnowledgeBase, 
  deleteKnowledgeBase,
  getKnowledgeBaseDetail
} from '@/api/knowledgeBase'
import {
  getDocumentList,
  uploadDocument,
  deleteDocument,
  downloadDocument,
  reindexDocument
} from '@/api/knowledgeBaseDocument'

const knowledgeBases = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterStatus = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const docDialogVisible = ref(false)
const currentKB = ref(null)
const currentKBForDocs = ref(null)
const isEdit = ref(false)
const formRef = ref(null)
const currentEditId = ref(null)
const uploadRef = ref(null)
const fileList = ref([])
const uploading = ref(false)
const documents = ref([])
const docLoading = ref(false)
const refreshTimer = ref(null)
const reindexingDocId = ref(null)

const formData = ref({
  name: '',
  description: '',
  status: 'active',
  isPublic: false // 普通用户只能创建私有知识库
})

onMounted(() => {
  loadKnowledgeBases()
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

// 向量化状态相关函数
const getVectorizedStatusText = (status) => {
  if (status === null || status === undefined) return '未向量化'
  switch (status) {
    case 0:
      return '未向量化'
    case 1:
      return '向量化中'
    case 2:
      return '向量化成功'
    case 3:
      return '向量化失败'
    default:
      return '未知'
  }
}

const getVectorizedStatusType = (status) => {
  if (status === null || status === undefined) return 'info'
  switch (status) {
    case 0:
      return 'info'
    case 1:
      return 'warning'
    case 2:
      return 'success'
    case 3:
      return 'danger'
    default:
      return 'info'
  }
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
  formData.value = {
    name: '',
    description: '',
    status: 'active',
    isPublic: false // 普通用户只能创建私有知识库
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  currentEditId.value = row.id
  formData.value = {
    name: row.name,
    description: row.description || '',
    status: typeof row.status === 'number' ? statusMap[row.status] : row.status,
    isPublic: false // 普通用户只能创建私有知识库
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

const handleManageDocs = (row) => {
  currentKBForDocs.value = row
  docDialogVisible.value = true
  loadDocuments()
}

// 文件上传相关
const uploadAction = computed(() => {
  if (!currentKBForDocs.value) return ''
  return `/api/knowledge-bases/${currentKBForDocs.value.id}/documents/upload`
})

const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
})

const uploadData = computed(() => {
  return {}
})

const handleFileChange = (file, files) => {
  fileList.value = files
}

const handleFileRemove = (file, files) => {
  fileList.value = files
}

const beforeUpload = (file) => {
  // 验证文件大小（100MB）
  const maxSize = 100 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过100MB')
    return false
  }
  
  // 验证文件类型
  const allowedTypes = ['pdf', 'doc', 'docx', 'txt', 'md', 'xls', 'xlsx', 'ppt', 'pptx', 'png', 'jpg', 'jpeg', 'gif']
  const fileExtension = file.name.split('.').pop()?.toLowerCase()
  if (!fileExtension || !allowedTypes.includes(fileExtension)) {
    ElMessage.error('不支持的文件类型')
    return false
  }
  
  return true
}

const clearFileList = () => {
  fileList.value = []
  uploadRef.value?.clearFiles()
}

const handleUploadFiles = async () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('请先选择要上传的文件')
    return
  }
  
  uploading.value = true
  const uploadPromises = []
  
  for (const fileItem of fileList.value) {
    if (fileItem.raw) {
      const formData = new FormData()
      formData.append('file', fileItem.raw)
      
      uploadPromises.push(
        uploadDocument(currentKBForDocs.value.id, formData)
          .then(() => {
            ElMessage.success(`文件 ${fileItem.name} 上传成功`)
          })
          .catch((error) => {
            ElMessage.error(`文件 ${fileItem.name} 上传失败: ${error.message || '未知错误'}`)
            throw error
          })
      )
    }
  }
  
  try {
    await Promise.all(uploadPromises)
    clearFileList()
    await loadDocuments() // 等待文档列表加载完成
    loadKnowledgeBases() // 刷新知识库列表以更新文档数量
    // 上传成功后启动自动刷新，以便实时显示向量化状态
    startAutoRefresh()
  } catch (error) {
    // 错误已在Promise中处理
  } finally {
    uploading.value = false
  }
}

// 文档列表相关
const loadDocuments = async () => {
  if (!currentKBForDocs.value) return
  
  docLoading.value = true
  try {
    const response = await getDocumentList(currentKBForDocs.value.id)
    documents.value = response || []
    
    // 检查是否有正在向量化的文档，如果有则启动定时刷新
    const hasVectorizing = documents.value.some(doc => doc.vectorizedStatus === 1)
    if (hasVectorizing) {
      startAutoRefresh()
    } else {
      stopAutoRefresh()
    }
  } catch (error) {
    ElMessage.error('加载文档列表失败：' + (error.message || '未知错误'))
  } finally {
    docLoading.value = false
  }
}

// 自动刷新文档列表（用于实时显示向量化状态）
const startAutoRefresh = () => {
  if (refreshTimer.value) return // 如果已经有定时器，不重复创建
  
  refreshTimer.value = setInterval(() => {
    if (docDialogVisible.value && currentKBForDocs.value) {
      // 静默刷新，不显示loading
      getDocumentList(currentKBForDocs.value.id)
        .then(response => {
          documents.value = response || []
          // 如果没有正在向量化的文档，停止定时刷新
          const hasVectorizing = documents.value.some(doc => doc.vectorizedStatus === 1)
          if (!hasVectorizing) {
            stopAutoRefresh()
          }
        })
        .catch(() => {
          // 静默失败，不影响用户体验
        })
    } else {
      stopAutoRefresh()
    }
  }, 3000) // 每3秒刷新一次
}

const stopAutoRefresh = () => {
  if (refreshTimer.value) {
    clearInterval(refreshTimer.value)
    refreshTimer.value = null
  }
}

const handleDeleteDoc = (doc) => {
  ElMessageBox.confirm(
    `确定要删除文档"${doc.originalFileName}"吗？此操作不可恢复。`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await deleteDocument(currentKBForDocs.value.id, doc.id)
      ElMessage.success('删除成功')
      loadDocuments()
      loadKnowledgeBases() // 刷新知识库列表以更新文档数量
    } catch (error) {
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }).catch(() => {
    // 取消操作
  })
}

const handleDownloadDoc = async (doc) => {
  try {
    const blob = await downloadDocument(currentKBForDocs.value.id, doc.id)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = doc.originalFileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('下载成功')
  } catch (error) {
    ElMessage.error('下载失败：' + (error.message || '未知错误'))
  }
}

const handleReindexDoc = async (doc) => {
  try {
    reindexingDocId.value = doc.id
    await reindexDocument(currentKBForDocs.value.id, doc.id)
    ElMessage.success('重新向量化任务已提交，请稍后查看状态')
    // 刷新文档列表
    await loadDocuments()
    // 启动自动刷新以查看向量化进度
    startAutoRefresh()
  } catch (error) {
    ElMessage.error('重新向量化失败：' + (error.message || '未知错误'))
  } finally {
    reindexingDocId.value = null
  }
}

const handleDocDialogClose = () => {
  clearFileList()
  documents.value = []
  currentKBForDocs.value = null
  stopAutoRefresh() // 关闭对话框时停止自动刷新
}

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
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

// 组件卸载时清理定时器
onUnmounted(() => {
  stopAutoRefresh()
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
</style>

