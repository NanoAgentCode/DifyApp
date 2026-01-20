<template>
  <div class="document-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button type="text" @click="goBack" style="margin-right: 10px">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <span>文件管理 - {{ currentKB?.name || '未知知识库' }}</span>
          </div>
        </div>
      </template>

      <!-- 文件上传区域 -->
      <div class="upload-section">
        <el-card shadow="never" class="upload-card">
          <template #header>
            <div class="section-title">
              <span class="section-title-text">
                <el-icon><UploadFilled /></el-icon>
                上传文件
              </span>
            </div>
          </template>
          <div class="upload-content">
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
              class="upload-dragger"
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
            <div class="upload-actions">
              <el-button type="primary" @click="handleUploadFiles" :loading="uploading" :disabled="fileList.length === 0">
                <el-icon><UploadFilled /></el-icon>
                开始上传
              </el-button>
              <el-button @click="clearFileList" :disabled="fileList.length === 0">
                <el-icon><Delete /></el-icon>
                清空列表
              </el-button>
            </div>
          </div>
        </el-card>

        <!-- 本次上传的文件信息 -->
        <el-card v-if="recentUploads.length > 0" shadow="never" class="recent-uploads-card">
          <template #header>
            <div class="section-title">
              <span class="section-title-text">
                <el-icon><Document /></el-icon>
                本次上传的文件 ({{ recentUploads.length }})
              </span>
              <el-button type="text" size="small" @click="clearRecentUploads">
                <el-icon><Delete /></el-icon>
                清空
              </el-button>
            </div>
          </template>
          <el-table :data="recentUploads" stripe size="default" class="recent-uploads-table" row-key="id">
            <el-table-column prop="originalFileName" label="文件名" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="file-name-cell">
                  <el-icon class="file-icon"><Document /></el-icon>
                  <span>{{ row.originalFileName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="100" align="center">
              <template #default="{ row }">
                <span class="file-size">{{ formatFileSize(row.fileSize) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="上传时间" width="180" align="center">
              <template #default="{ row }">
                <span class="upload-time">{{ formatDate(row.uploadTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="向量化状态" width="130" align="center">
              <template #default="{ row }">
                <el-tooltip 
                  :content="row.vectorizedError || getVectorizedStatusText(row.vectorizedStatus)" 
                  placement="top"
                >
                  <el-icon :class="['status-icon', `status-icon-${getVectorizedStatusType(row.vectorizedStatus)}`]">
                    <component :is="getVectorizedStatusIcon(row.vectorizedStatus)" />
                  </el-icon>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="{ row }">
                <el-button size="small" type="primary" text @click="handleDownloadDoc(row)">
                  <el-icon><Download /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <!-- 文件列表 -->
      <div class="doc-list-section">
        <el-card shadow="never">
          <template #header>
            <div class="section-title">
              <span class="section-title-text">
                <el-icon><Document /></el-icon>
                文档列表
                <el-tag v-if="total > 0" type="info" size="small" style="margin-left: 8px">
                  共 {{ total }} 个文件
                </el-tag>
              </span>
              <el-button size="small" @click="loadDocuments" :loading="docLoading">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>
          
          <!-- 搜索和过滤栏 -->
          <div class="search-filter-bar">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索文件名"
              clearable
              style="width: 250px"
              @input="handleSearch"
              @clear="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select
              v-model="filterVectorizedStatus"
              placeholder="向量化状态"
              clearable
              style="width: 150px; margin-left: 10px"
              @change="handleFilter"
            >
              <el-option label="全部" value="" />
              <el-option label="未向量化" :value="0" />
              <el-option label="向量化中" :value="1" />
              <el-option label="向量化成功" :value="2" />
              <el-option label="向量化失败" :value="3" />
            </el-select>
            <el-select
              v-model="filterFileType"
              placeholder="文件类型"
              clearable
              style="width: 150px; margin-left: 10px"
              @change="handleFilter"
            >
              <el-option label="全部" value="" />
              <el-option label="PDF" value="pdf" />
              <el-option label="Word" value="doc" />
              <el-option label="Word (docx)" value="docx" />
              <el-option label="文本" value="txt" />
              <el-option label="Markdown" value="md" />
              <el-option label="Excel" value="xls" />
              <el-option label="Excel (xlsx)" value="xlsx" />
              <el-option label="PowerPoint" value="ppt" />
              <el-option label="PowerPoint (pptx)" value="pptx" />
              <el-option label="图片" value="png" />
            </el-select>
          </div>
          
          <!-- 表格容器 -->
          <div class="table-wrapper">
            <el-table
            :data="documents"
            v-loading="docLoading"
            stripe
            size="default"
            class="documents-table"
            empty-text="暂无文档"
            row-key="id"
          >
            <el-table-column prop="originalFileName" label="文件名" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="file-name-cell">
                  <el-icon class="file-icon"><Document /></el-icon>
                  <span>{{ row.originalFileName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" type="info">{{ row.fileType || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="100" align="center">
              <template #default="{ row }">
                <span class="file-size">{{ formatFileSize(row.fileSize) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="uploadTime" label="上传时间" width="170" align="center">
              <template #default="{ row }">
                <span class="upload-time">{{ formatDate(row.uploadTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="向量化状态" width="130" align="center">
              <template #default="{ row }">
                <el-tooltip 
                  v-if="row.vectorizedError" 
                  :content="row.vectorizedError" 
                  placement="top"
                >
                  <el-icon :class="['status-icon', `status-icon-${getVectorizedStatusType(row.vectorizedStatus)}`]">
                    <component :is="getVectorizedStatusIcon(row.vectorizedStatus)" />
                  </el-icon>
                </el-tooltip>
                <el-tooltip 
                  v-else
                  :content="getVectorizedStatusText(row.vectorizedStatus)" 
                  placement="top"
                >
                  <el-icon :class="['status-icon', `status-icon-${getVectorizedStatusType(row.vectorizedStatus)}`]">
                    <component :is="getVectorizedStatusIcon(row.vectorizedStatus)" />
                  </el-icon>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-buttons">
                  <el-button size="small" type="primary" text @click="handleDownloadDoc(row)" title="下载">
                    <el-icon><Download /></el-icon>
                  </el-button>
                  <el-button 
                    size="small" 
                    type="warning" 
                    text
                    @click="handleReindexDoc(row)"
                    :loading="reindexingDocId === row.id"
                    :disabled="row.vectorizedStatus === 1"
                    title="重向量化"
                  >
                    <el-icon><Refresh /></el-icon>
                  </el-button>
                  <el-button size="small" type="danger" text @click="handleDeleteDoc(row)" title="删除">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </template>
            </el-table-column>
            </el-table>
          </div>
          
          <!-- 分页 -->
          <div class="pagination" v-if="total > 0">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, UploadFilled, Refresh, Document, Download, Delete, CircleCheck, Loading, CircleClose, InfoFilled, Search } from '@element-plus/icons-vue'
import { getKnowledgeBaseDetail } from '@/api/knowledgeBase'
import {
  getDocumentList,
  uploadDocument,
  deleteDocument,
  downloadDocument,
  reindexDocument
} from '@/api/knowledgeBaseDocument'

const route = useRoute()
const router = useRouter()

const currentKB = ref(null)
const uploadRef = ref(null)
const fileList = ref([])
const uploading = ref(false)
const documents = ref([])
const docLoading = ref(false)
const refreshTimer = ref(null)
const reindexingDocId = ref(null)
const recentUploads = ref([]) // 本次上传的文件列表

// 分页相关
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
let searchTimer = null // 搜索防抖定时器

// 搜索和过滤
const searchKeyword = ref('')
const filterVectorizedStatus = ref('')
const filterFileType = ref('')

// 从路由参数获取知识库ID
const kbId = computed(() => {
  return route.params.kbId ? parseInt(route.params.kbId) : null
})

// 上传相关配置
const uploadAction = computed(() => {
  return kbId.value ? `/api/knowledge-bases/${kbId.value}/documents/upload` : ''
})

const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
})

const uploadData = computed(() => {
  return {}
})

// 加载知识库信息
const loadKnowledgeBase = async () => {
  if (!kbId.value) {
    ElMessage.error('知识库ID不存在')
    router.push('/admin/knowledge-base')
    return
  }
  
  try {
    const kb = await getKnowledgeBaseDetail(kbId.value)
    currentKB.value = kb
  } catch (error) {
    ElMessage.error('加载知识库信息失败：' + (error.message || '未知错误'))
    router.push('/admin/knowledge-base')
  }
}

// 文件上传相关
const handleFileChange = (file, files) => {
  fileList.value = files
}

const handleFileRemove = (file, files) => {
  fileList.value = files
}

const beforeUpload = (file) => {
  const maxSize = 100 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过100MB')
    return false
  }
  
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
        uploadDocument(kbId.value, formData)
          .then((response) => {
            ElMessage.success(`文件 ${fileItem.name} 上传成功`)
            return response
          })
          .catch((error) => {
            ElMessage.error(`文件 ${fileItem.name} 上传失败: ${error.message || '未知错误'}`)
            throw error
          })
      )
    }
  }
  
  try {
    const results = await Promise.all(uploadPromises)
    // 将本次上传成功的文件添加到 recentUploads
    recentUploads.value = [...results, ...recentUploads.value]
    clearFileList()
    await loadDocuments()
    // 上传成功后启动自动刷新，以便实时显示向量化状态
    startAutoRefresh()
  } catch (error) {
    // 错误已在Promise中处理
  } finally {
    uploading.value = false
  }
}

const clearRecentUploads = () => {
  recentUploads.value = []
}

// 文档列表相关
const loadDocuments = async () => {
  if (!kbId.value) return
  
  docLoading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value.trim()
    }
    
    if (filterVectorizedStatus.value !== '') {
      params.vectorizedStatus = filterVectorizedStatus.value
    }
    
    if (filterFileType.value) {
      params.fileType = filterFileType.value
    }
    
    const response = await getDocumentList(kbId.value, params)
    
    // 检查是否是分页响应
    if (response && typeof response === 'object' && 'content' in response && 'total' in response) {
      // 分页响应
      documents.value = response.content || []
      total.value = response.total || 0
    } else {
      // 兼容旧接口（非分页响应）
      documents.value = Array.isArray(response) ? response : []
      total.value = documents.value.length
    }
    
    // 检查是否有正在向量化的文档，如果有则启动定时刷新（优化：使用for循环）
    let hasVectorizing = false
    const docs = documents.value
    for (let i = 0; i < docs.length; i++) {
      if (docs[i].vectorizedStatus === 1) {
        hasVectorizing = true
        break
      }
    }
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

// 搜索防抖处理
const handleSearch = () => {
  // 清除之前的定时器
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  // 设置新的定时器，500ms后执行搜索
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    loadDocuments()
  }, 500)
}

// 过滤处理
const handleFilter = () => {
  currentPage.value = 1
  loadDocuments()
}

// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadDocuments()
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadDocuments()
}

// 自动刷新文档列表（用于实时显示向量化状态）
const startAutoRefresh = () => {
  if (refreshTimer.value) return
  
  refreshTimer.value = setInterval(() => {
    if (kbId.value) {
      const params = {
        page: currentPage.value,
        pageSize: pageSize.value
      }
      
      if (searchKeyword.value) {
        params.keyword = searchKeyword.value.trim()
      }
      
      if (filterVectorizedStatus.value !== '') {
        params.vectorizedStatus = filterVectorizedStatus.value
      }
      
      if (filterFileType.value) {
        params.fileType = filterFileType.value
      }
      
      getDocumentList(kbId.value, params)
        .then(response => {
          // 检查是否是分页响应
          if (response && typeof response === 'object' && 'content' in response && 'total' in response) {
            documents.value = response.content || []
            total.value = response.total || 0
          } else {
            documents.value = Array.isArray(response) ? response : []
            total.value = documents.value.length
          }
          
          // 更新 recentUploads 中的向量化状态（优化：使用Map提升查找性能）
          const docMap = new Map()
          for (let i = 0; i < documents.value.length; i++) {
            const doc = documents.value[i]
            if (doc && doc.id) {
              docMap.set(doc.id, doc)
            }
          }
          for (let i = 0; i < recentUploads.value.length; i++) {
            const recent = recentUploads.value[i]
            if (recent && recent.id) {
              const updated = docMap.get(recent.id)
              if (updated) {
                recentUploads.value[i] = updated
              }
            }
          }
          // 如果没有正在向量化的文档，停止定时刷新（优化：使用for循环）
          let hasVectorizing = false
          const docs = documents.value
          for (let i = 0; i < docs.length; i++) {
            if (docs[i].vectorizedStatus === 1) {
              hasVectorizing = true
              break
            }
          }
          if (!hasVectorizing) {
            stopAutoRefresh()
          }
        })
        .catch(() => {
          // 静默失败
        })
    } else {
      stopAutoRefresh()
    }
  }, 3000)
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
      await deleteDocument(kbId.value, doc.id)
      ElMessage.success('删除成功')
      loadDocuments()
      // 从 recentUploads 中移除
      // 优化：使用for循环替代filter
      const filtered = []
      for (let i = 0; i < recentUploads.value.length; i++) {
        if (recentUploads.value[i].id !== doc.id) {
          filtered.push(recentUploads.value[i])
        }
      }
      recentUploads.value = filtered
    } catch (error) {
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }).catch(() => {
    // 取消操作
  })
}

const handleDownloadDoc = async (doc) => {
  try {
    const blob = await downloadDocument(kbId.value, doc.id)
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
  if (!doc || !doc.id) {
    ElMessage.error('文档信息错误，无法重新向量化')
    return
  }
  
  const docId = doc.id
  const currentKbId = kbId.value
  
  if (!currentKbId) {
    ElMessage.error('知识库ID错误，无法重新向量化')
    return
  }
  
  try {
    reindexingDocId.value = docId
    await reindexDocument(currentKbId, docId)
    ElMessage.success('重新向量化任务已提交，请稍后查看状态')
    // 延迟刷新，确保后端已处理请求
    setTimeout(async () => {
      await loadDocuments()
      startAutoRefresh()
      reindexingDocId.value = null
    }, 500)
  } catch (error) {
    ElMessage.error('重新向量化失败：' + (error.message || '未知错误'))
    reindexingDocId.value = null
  }
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

const getVectorizedStatusIcon = (status) => {
  if (status === null || status === undefined) return InfoFilled
  switch (status) {
    case 0:
      return InfoFilled  // 未向量化
    case 1:
      return Loading  // 向量化中
    case 2:
      return CircleCheck  // 向量化成功
    case 3:
      return CircleClose  // 向量化失败
    default:
      return InfoFilled
  }
}

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

const goBack = () => {
  router.push('/admin/knowledge-base')
}

onMounted(() => {
  loadKnowledgeBase()
  loadDocuments()
})

onUnmounted(() => {
  stopAutoRefresh()
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.document-management {
  padding: var(--spacing-lg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-secondary);
}

/* ========== 卡片样式 ========== */
:deep(.el-card) {
  border-radius: var(--card-border-radius);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--card-shadow);
  transition: box-shadow var(--transition-base);
}

:deep(.el-card:hover) {
  box-shadow: var(--card-shadow-hover);
}

:deep(.el-card__header) {
  padding: var(--spacing-md) var(--card-padding);
  border-bottom: 1px solid var(--color-border-lighter);
  background: var(--color-bg-tertiary);
  flex-shrink: 0;
}

:deep(.el-card__body) {
  padding: var(--card-padding);
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
  background: var(--color-bg-primary);
}

/* ========== 卡片头部 ========== */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  gap: var(--spacing-md);
}

.header-left .el-button {
  color: var(--color-text-regular);
  font-size: var(--font-size-sm);
  transition: all var(--transition-base);
}

.header-left .el-button:hover {
  color: var(--color-primary);
}

/* ========== 区域标题 ========== */
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: var(--font-weight-medium);
}

.section-title-text {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
}

.upload-section {
  margin-bottom: 24px;
}

.upload-card,
.recent-uploads-card {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  transition: all var(--transition-base);
}

.upload-card:hover,
.recent-uploads-card:hover {
  border-color: var(--color-border-base);
  box-shadow: var(--shadow-sm);
}

.upload-content {
  padding: var(--spacing-sm) 0;
}

.upload-actions {
  display: flex;
  gap: var(--spacing-md);
  margin-top: var(--spacing-md);
  justify-content: center;
}

.upload-actions .el-button {
  min-width: 100px;
  transition: all var(--transition-base);
}

.upload-actions .el-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

:deep(.upload-dragger) {
  width: 100%;
}

:deep(.upload-dragger .el-upload-dragger) {
  width: 100%;
  height: 180px;
  border: 2px dashed var(--color-border-base);
  border-radius: var(--radius-md);
  background: var(--color-bg-tertiary);
  transition: all var(--transition-base);
}

:deep(.upload-dragger .el-upload-dragger:hover) {
  border-color: var(--color-primary);
  background: var(--color-primary-light-5);
  box-shadow: var(--shadow-xs);
}

:deep(.upload-dragger .el-icon--upload) {
  font-size: 48px;
  color: #8c939d;
  margin-bottom: 16px;
}

:deep(.upload-dragger .el-upload__text) {
  color: #606266;
  font-size: 14px;
}

:deep(.upload-dragger .el-upload__text em) {
  color: #409eff;
  font-style: normal;
}

:deep(.upload-dragger .el-upload__tip) {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
}

.recent-uploads-card {
  margin-top: 20px;
}

.recent-uploads-table {
  margin-top: 0;
}

.search-filter-bar {
  display: flex;
  align-items: center;
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-lighter);
}

.search-filter-bar :deep(.el-input__wrapper) {
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.search-filter-bar :deep(.el-input__wrapper:hover) {
  box-shadow: var(--shadow-xs);
}

.search-filter-bar :deep(.el-input__wrapper.is-focus) {
  box-shadow: var(--shadow-primary);
}

.doc-list-section {
  margin-top: 24px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.doc-list-section :deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.doc-list-section :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.search-filter-bar {
  flex-shrink: 0;
  margin-bottom: 16px;
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.documents-table {
  height: 100%;
}

.pagination {
  flex-shrink: 0;
  margin-top: var(--spacing-lg);
  padding: var(--spacing-md);
  display: flex;
  justify-content: flex-end;
  background: var(--color-bg-tertiary);
  border-top: 1px solid var(--color-border-lighter);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}

:deep(.el-pagination .el-pager li) {
  border-radius: var(--radius-sm);
  transition: all var(--transition-base);
}

:deep(.el-pagination .el-pager li:hover) {
  background-color: var(--color-bg-hover);
}

:deep(.el-pagination .el-pager li.is-active) {
  background-color: var(--color-primary);
  color: #ffffff;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-icon {
  color: #409eff;
  font-size: 16px;
  flex-shrink: 0;
}

.file-size {
  color: #606266;
  font-size: 13px;
}

.upload-time {
  color: #909399;
  font-size: 13px;
}

.status-icon {
  font-size: 20px;
  cursor: pointer;
}

.status-icon-success {
  color: #67c23a;
}

.status-icon-warning {
  color: #e6a23c;
  animation: rotate 2s linear infinite;
}

.status-icon-danger {
  color: #f56c6c;
}

.status-icon-info {
  color: #909399;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.action-buttons {
  display: flex;
  gap: 4px;
  justify-content: center;
  align-items: center;
}

.action-buttons .el-button {
  padding: 5px 8px;
}

.action-buttons .el-button + .el-button {
  margin-left: 0;
}

.action-buttons .el-icon {
  font-size: 18px;
}

/* 表格优化 */
:deep(.el-table) {
  border-radius: 4px;
  overflow: hidden;
}

:deep(.el-table th) {
  background: #f5f7fa;
  color: #606266;
  font-weight: 600;
  padding: 12px 0;
}

:deep(.el-table td) {
  padding: 12px 0;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: #fafafa;
}

:deep(.el-table .el-table__row:hover > td) {
  background: #f5f7fa;
}

/* 空状态优化 */
:deep(.el-table__empty-block) {
  padding: 40px 0;
}

:deep(.el-table__empty-text) {
  color: #909399;
}
</style>

