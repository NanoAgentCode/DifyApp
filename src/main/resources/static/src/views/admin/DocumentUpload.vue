<template>
  <div class="document-upload">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button type="text" @click="goBack" style="margin-right: 10px">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <span>文档上传 - {{ currentKB?.name || '未知知识库' }}</span>
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
          <el-table :data="recentUploads" stripe size="default" class="recent-uploads-table">
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
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, UploadFilled, Document, Download, Delete, CircleCheck, Loading, CircleClose, InfoFilled } from '@element-plus/icons-vue'
import { getKnowledgeBaseDetail } from '@/api/knowledgeBase'
import {
  getDocumentList,
  uploadDocument,
  downloadDocument
} from '@/api/knowledgeBaseDocument'

const route = useRoute()
const router = useRouter()

const currentKB = ref(null)
const uploadRef = ref(null)
const fileList = ref([])
const uploading = ref(false)
const refreshTimer = ref(null)
const recentUploads = ref([]) // 本次上传的文件列表

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

// 自动刷新文档列表（用于实时显示向量化状态）
const startAutoRefresh = () => {
  if (refreshTimer.value) return
  
  refreshTimer.value = setInterval(() => {
    if (kbId.value) {
      getDocumentList(kbId.value)
        .then(response => {
          const documents = response || []
          // 更新 recentUploads 中的向量化状态
          recentUploads.value = recentUploads.value.map(recent => {
            const updated = documents.find(doc => doc.id === recent.id)
            return updated || recent
          })
          // 如果没有正在向量化的文档，停止定时刷新
          const hasVectorizing = recentUploads.value.some(doc => doc.vectorizedStatus === 1)
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
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.document-upload {
  padding: 0;
}

:deep(.el-card) {
  border-radius: 8px;
}

:deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}

:deep(.el-card__body) {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-left .el-button {
  color: #606266;
  font-size: 14px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
}

.section-title-text {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: #303133;
}

.upload-section {
  margin-bottom: 24px;
}

.upload-card,
.recent-uploads-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.upload-content {
  padding: 10px 0;
}

.upload-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  justify-content: center;
}

.upload-actions .el-button {
  min-width: 100px;
}

:deep(.upload-dragger) {
  width: 100%;
}

:deep(.upload-dragger .el-upload-dragger) {
  width: 100%;
  height: 180px;
  border: 2px dashed #d9d9d9;
  border-radius: 6px;
  background: #fafafa;
  transition: all 0.3s;
}

:deep(.upload-dragger .el-upload-dragger:hover) {
  border-color: #409eff;
  background: #f0f7ff;
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

