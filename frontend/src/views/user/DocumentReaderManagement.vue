<template>
  <div class="document-reader-management">
    <el-card>
      <!-- 隐藏的文件上传组件 -->
      <el-upload
        ref="uploadRef"
        :file-list="fileList"
        :auto-upload="false"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :before-upload="beforeUpload"
        :limit="10"
        multiple
        :show-file-list="false"
        style="display: none"
      >
      </el-upload>

      <!-- 文件列表 -->
      <div class="doc-list-section">
        <el-card shadow="never">
          <template #header>
            <div class="section-title">
              <div class="section-title-left">
                <el-button type="text" @click="handleBack" style="margin-right: 10px">
                  <el-icon><ArrowLeft /></el-icon>
                  返回
                </el-button>
                <span class="section-title-text">
                  <el-icon><Document /></el-icon>
                  文档列表
                  <el-tag v-if="total > 0" type="info" size="small" style="margin-left: 8px">
                    共 {{ total }} 个文件
                  </el-tag>
                </span>
              </div>
            </div>
          </template>
          
          <!-- 搜索和过滤栏 -->
          <div class="search-filter-bar">
            <div class="search-left">
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
            <div class="search-right">
              <el-button type="primary" @click="handleNewDocument" :icon="Plus">
                新建
              </el-button>
              <el-button @click="loadDocuments" :loading="docLoading" :icon="Refresh">
                刷新
              </el-button>
            </div>
          </div>
          
          <!-- 卡片容器 -->
          <div v-loading="docLoading" class="cards-wrapper">
            <div v-if="documents.length === 0" class="empty-state">
              <el-icon class="empty-icon"><Document /></el-icon>
              <p>暂无文档</p>
            </div>
            <div v-else class="documents-grid">
              <el-card
                v-for="doc in documents"
                :key="doc.id"
                class="document-card"
                shadow="hover"
                @click="handleOpenDocument(doc)"
              >
                <div class="card-content">
                  <div class="card-header">
                    <el-icon class="file-type-icon" :class="getFileTypeClass(doc.fileType)">
                      <Document v-if="!isImageType(doc.fileType)" />
                      <Picture v-else />
                    </el-icon>
                    <div class="header-tags">
                      <el-tag size="small" effect="plain" type="info" class="file-type-tag">
                        {{ doc.fileType?.toUpperCase() || '-' }}
                      </el-tag>
                      <el-tooltip
                        v-if="doc.vectorizedError"
                        :content="doc.vectorizedError"
                        placement="top"
                      >
                        <el-icon 
                          :class="['vectorized-status-icon', `status-icon-${getVectorizedStatusType(doc.vectorizedStatus)}`]"
                        >
                          <component :is="getVectorizedStatusIcon(doc.vectorizedStatus)" />
                        </el-icon>
                      </el-tooltip>
                      <el-tooltip
                        v-else
                        :content="getVectorizedStatusText(doc.vectorizedStatus)"
                        placement="top"
                      >
                        <el-icon 
                          :class="['vectorized-status-icon', `status-icon-${getVectorizedStatusType(doc.vectorizedStatus)}`]"
                        >
                          <component :is="getVectorizedStatusIcon(doc.vectorizedStatus)" />
                        </el-icon>
                      </el-tooltip>
                    </div>
                  </div>
                  <div class="card-body">
                    <div class="file-name" :title="doc.originalFileName || doc.fileName">
                      {{ doc.originalFileName || doc.fileName }}
                    </div>
                    <div class="file-info">
                      <span class="file-size">
                        <el-icon><FolderOpened /></el-icon>
                        {{ formatFileSize(doc.fileSize) }}
                      </span>
                      <span class="upload-time">
                        <el-icon><Clock /></el-icon>
                        {{ formatDate(doc.uploadTime || doc.createTime) }}
                      </span>
                    </div>
                  </div>
                  <div class="card-footer" @click.stop>
                    <el-button
                      type="primary"
                      size="small"
                      :icon="View"
                      circle
                      @click="handleOpenDocument(doc)"
                      title="查看详情"
                    />
                    <el-button
                      type="success"
                      size="small"
                      :icon="Download"
                      circle
                      @click="handleDownloadDoc(doc)"
                      title="下载文档"
                    />
                    <el-button
                      type="warning"
                      size="small"
                      :icon="Refresh"
                      circle
                      @click="handleReindexDocument(doc)"
                      :disabled="doc.vectorizedStatus === 1"
                      :loading="reindexingDocIds.includes(doc.id)"
                      title="重新向量化"
                    />
                    <el-button
                      type="danger"
                      size="small"
                      :icon="Delete"
                      circle
                      @click="handleDeleteDoc(doc)"
                      title="删除文档"
                    />
                  </div>
                </div>
              </el-card>
            </div>
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
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Refresh, Document, Delete, Search, View, Picture, FolderOpened, Clock, Plus, InfoFilled, Loading, CircleCheck, CircleClose, ArrowLeft, Download } from '@element-plus/icons-vue'
import {
  getDocumentList,
  uploadDocument,
  deleteDocument,
  reindexDocument,
  downloadDocument
} from '@/api/documentReader'

const router = useRouter()

const handleBack = () => {
  router.push('/user/chat')
}

const uploadRef = ref(null)
const fileList = ref([])
const uploading = ref(false)
const documents = ref([])
const docLoading = ref(false)
const reindexingDocIds = ref([]) // 正在重新向量化的文档ID列表
let autoRefreshTimer = null // 自动刷新定时器

// 分页相关
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
let searchTimer = null

// 搜索和过滤
const searchKeyword = ref('')
const filterFileType = ref('')

// 文件上传相关
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

// 新建文档（触发文件选择）
const handleNewDocument = () => {
  // 创建一个临时的input元素来触发文件选择
  const input = document.createElement('input')
  input.type = 'file'
  input.multiple = true
  input.accept = '.pdf,.doc,.docx,.txt,.md,.xls,.xlsx,.ppt,.pptx,.png,.jpg,.jpeg,.gif'
  input.style.display = 'none'
  
  input.onchange = async (event) => {
    const files = Array.from(event.target.files)
    if (files.length === 0) return
    
    uploading.value = true
    const uploadPromises = []
    
    for (const file of files) {
      // 验证文件
      if (!beforeUpload(file)) {
        continue
      }
      
      const formData = new FormData()
      formData.append('file', file)
      
      uploadPromises.push(
        uploadDocument(formData)
          .then(() => {
            ElMessage.success(`文件 ${file.name} 上传成功`)
          })
          .catch((error) => {
            ElMessage.error(`文件 ${file.name} 上传失败: ${error.message || '未知错误'}`)
          })
      )
    }
    
    try {
      await Promise.all(uploadPromises)
      await loadDocuments()
    } catch (error) {
      // 错误已在Promise中处理
    } finally {
      uploading.value = false
      // 清理临时input
      document.body.removeChild(input)
    }
  }
  
  document.body.appendChild(input)
  input.click()
}

// 文件选择后自动上传
const handleFileChange = async (file, files) => {
  fileList.value = files
  
  // 自动上传选中的文件
  if (file.raw) {
    uploading.value = true
    try {
      const formData = new FormData()
      formData.append('file', file.raw)
      
      await uploadDocument(formData)
      ElMessage.success(`文件 ${file.name} 上传成功`)
      
      // 清空文件列表
      clearFileList()
      // 刷新文档列表
      await loadDocuments()
    } catch (error) {
      ElMessage.error(`文件 ${file.name} 上传失败: ${error.message || '未知错误'}`)
      clearFileList()
    } finally {
      uploading.value = false
    }
  }
}

// 文档列表相关
const loadDocuments = async () => {
  docLoading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value.trim()
    }
    
    if (filterFileType.value) {
      params.fileType = filterFileType.value
    }
    
    const response = await getDocumentList(params)
    
    // 检查是否是分页响应
    if (response && typeof response === 'object' && 'content' in response && 'total' in response) {
      documents.value = response.content || []
      total.value = response.total || 0
    } else if (response && typeof response === 'object' && 'list' in response && 'total' in response) {
      documents.value = response.list || []
      total.value = response.total || 0
    } else {
      documents.value = Array.isArray(response) ? response : []
      total.value = documents.value.length
    }
  } catch (error) {
    ElMessage.error('加载文档列表失败：' + (error.message || '未知错误'))
  } finally {
    docLoading.value = false
  }
}

// 搜索防抖处理
const handleSearch = () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    loadDocuments()
  }, 500)
}

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

// 打开文档
const handleOpenDocument = (row) => {
  router.push(`/user/document-reader/${row.id}`)
}

// 下载文档
const handleDownloadDoc = async (doc) => {
  try {
    const blob = await downloadDocument(doc.id)
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', doc.originalFileName || doc.fileName || 'document')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('开始下载...')
  } catch (error) {
    ElMessage.error('下载失败：' + (error.message || '未知错误'))
  }
}

// 删除文档
const handleDeleteDoc = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除文档 "${row.originalFileName || row.fileName}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await deleteDocument(row.id)
    ElMessage.success('删除成功')
    await loadDocuments()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }
}

// 重新向量化文档
const handleReindexDocument = async (doc) => {
  try {
    await ElMessageBox.confirm(
      `确定要重新向量化文档 "${doc.originalFileName || doc.fileName}" 吗？这将删除旧的向量数据并重新生成。`,
      '确认重新向量化',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 添加到正在重新向量化的列表
    if (!reindexingDocIds.value.includes(doc.id)) {
      reindexingDocIds.value.push(doc.id)
    }
    
    const response = await reindexDocument(doc.id)
    
    if (response && response.success) {
      ElMessage.success(response.message || '重新向量化任务已提交，请稍后查看状态')
      // 刷新文档列表以更新向量化状态
      await loadDocuments()
    } else {
      ElMessage.error(response?.message || '重新向量化失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('重新向量化失败：' + (error.message || '未知错误'))
    }
  } finally {
    // 从正在重新向量化的列表中移除
    const index = reindexingDocIds.value.indexOf(doc.id)
    if (index > -1) {
      reindexingDocIds.value.splice(index, 1)
    }
  }
}

// 工具函数
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 判断是否为图片类型
const isImageType = (fileType) => {
  return ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(fileType?.toLowerCase())
}

// 获取文件类型样式类
const getFileTypeClass = (fileType) => {
  if (!fileType) return ''
  const type = fileType.toLowerCase()
  if (['pdf'].includes(type)) return 'file-type-pdf'
  if (['doc', 'docx'].includes(type)) return 'file-type-word'
  if (['xls', 'xlsx'].includes(type)) return 'file-type-excel'
  if (['ppt', 'pptx'].includes(type)) return 'file-type-ppt'
  if (['txt', 'md'].includes(type)) return 'file-type-text'
  if (['png', 'jpg', 'jpeg', 'gif'].includes(type)) return 'file-type-image'
  return 'file-type-default'
}

// 获取向量化状态文本
const getVectorizedStatusText = (status) => {
  if (status === null || status === undefined) return '未向量化'
  switch (status) {
    case 0:
      return '未向量化'
    case 1:
      return '向量化中'
    case 2:
      return '向量化完成'
    case 3:
      return '向量化失败'
    default:
      return '未知状态'
  }
}

// 获取向量化状态类型（用于样式）
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

// 获取向量化状态图标
const getVectorizedStatusIcon = (status) => {
  if (status === null || status === undefined) return InfoFilled
  switch (status) {
    case 0:
      return InfoFilled  // 未向量化
    case 1:
      return Loading  // 向量化中
    case 2:
      return CircleCheck  // 向量化完成
    case 3:
      return CircleClose  // 向量化失败
    default:
      return InfoFilled
  }
}

// 检查是否有文档正在向量化中
const hasVectorizingDocuments = () => {
  // 优化：使用for循环替代some
  const docs = documents.value
  for (let i = 0; i < docs.length; i++) {
    if (docs[i].vectorizedStatus === 1) {
      return true
    }
  }
  return false
}

// 启动自动刷新（当有文档正在向量化时）
const startAutoRefresh = () => {
  // 如果已经有定时器在运行，先清除
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
  }
  
  // 每3秒刷新一次文档列表
  autoRefreshTimer = setInterval(() => {
    if (hasVectorizingDocuments()) {
      loadDocuments()
    } else {
      // 如果没有文档在向量化中，停止自动刷新
      stopAutoRefresh()
    }
  }, 3000)
}

// 停止自动刷新
const stopAutoRefresh = () => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

// 监听文档列表变化，自动启动/停止刷新
watch(() => documents.value, (newDocs) => {
  if (hasVectorizingDocuments()) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}, { deep: true })

onMounted(() => {
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
.document-reader-management {
  height: 100%;
  width: 100%;
  box-sizing: border-box;
  overflow-x: hidden;
}

.document-reader-management :deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.document-reader-management :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 20px; /* 确保与内层 card body 的 padding 一致 */
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
}

.section-title-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-list-section :deep(.el-card__header) {
  padding: 18px 20px;
}

.section-title-text {
  display: flex;
  align-items: center;
  gap: 8px;
}

.doc-list-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.doc-list-section :deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.doc-list-section :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  padding: 20px;
}

.search-filter-bar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.search-left {
  display: flex;
  align-items: center;
  flex: 1;
}

.search-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cards-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
  padding: 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 400px;
  color: var(--el-text-color-secondary);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.documents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
  padding: 0;
}

/* 响应式布局 */
@media (max-width: 1200px) {
  .documents-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  }
}

@media (max-width: 768px) {
  .documents-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 12px;
  }
}

.document-card {
  cursor: pointer;
  transition: all 0.3s ease;
  width: 100%;
  min-height: 240px;
  max-height: 320px;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.document-card :deep(.el-card__body) {
  padding: 0;
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.document-card:hover {
  transform: translateY(-4px) scale(1.02);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

.card-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0;
}

.card-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 8px;
  padding: 12px 8px 0;
}

.file-type-icon {
  font-size: 48px;
  color: var(--el-color-primary);
  margin-bottom: 8px;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .file-type-icon {
    font-size: 40px;
  }
}

.file-type-icon.file-type-pdf {
  color: #f40f02;
}

.file-type-icon.file-type-word {
  color: #2b579a;
}

.file-type-icon.file-type-excel {
  color: #1d6f42;
}

.file-type-icon.file-type-ppt {
  color: #d04423;
}

.file-type-icon.file-type-text {
  color: #666;
}

.file-type-icon.file-type-image {
  color: #ff6b6b;
}

.file-type-icon.file-type-default {
  color: var(--el-color-primary);
}

.header-tags {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 4px;
}

.file-type-tag {
  flex-shrink: 0;
  font-size: 10px;
  padding: 2px 6px;
  height: 18px;
  line-height: 14px;
}

.vectorized-status-icon {
  font-size: 14px;
  cursor: pointer;
  flex-shrink: 0;
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

.card-body {
  flex: 1;
  margin-bottom: 8px;
  padding: 0 10px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.file-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.4;
  text-align: center;
  word-break: break-word;
  min-height: 36px;
  max-height: 36px;
}

.file-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 10px;
  color: var(--el-text-color-secondary);
  margin-top: auto;
}

.file-info span {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.file-size,
.upload-time {
  display: flex;
  align-items: center;
  gap: 3px;
}

.file-size .el-icon,
.upload-time .el-icon {
  font-size: 11px;
}

.card-footer {
  display: flex;
  gap: 6px;
  justify-content: center;
  padding: 8px 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  margin-top: auto;
}

.card-footer .el-button {
  width: 28px;
  height: 28px;
  padding: 0;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>

