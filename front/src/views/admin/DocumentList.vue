<template>
  <div class="document-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button type="text" @click="goBack" style="margin-right: 10px">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <span>文件列表 - {{ currentKB?.name || '未知知识库' }}</span>
          </div>
        </div>
      </template>

      <!-- 文件列表 -->
      <div class="doc-list-section">
        <el-card shadow="never">
          <template #header>
            <div class="section-title">
              <span class="section-title-text">
                <el-icon><Document /></el-icon>
                文档列表
                <el-tag v-if="documents.length > 0" type="info" size="small" style="margin-left: 8px">
                  {{ documents.length }} 个文件
                </el-tag>
              </span>
              <el-button size="small" @click="loadDocuments" :loading="docLoading">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>
          <el-table
            :data="documents"
            v-loading="docLoading"
            stripe
            size="default"
            class="documents-table"
            empty-text="暂无文档"
          >
            <el-table-column prop="originalFileName" label="文件名" min-width="25%" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="file-name-cell">
                  <el-icon class="file-icon"><Document /></el-icon>
                  <span>{{ row.originalFileName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型" min-width="10%" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" type="info">{{ row.fileType || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="大小" min-width="12%" align="center">
              <template #default="{ row }">
                <span class="file-size">{{ formatFileSize(row.fileSize) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="上传时间" min-width="15%" align="center">
              <template #default="{ row }">
                <span class="upload-time">{{ formatDate(row.createTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="向量化状态" min-width="13%" align="center">
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
            <el-table-column label="操作" min-width="15%" fixed="right" align="center">
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
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh, Document, Download, Delete, CircleCheck, Loading, CircleClose, InfoFilled } from '@element-plus/icons-vue'
import { getKnowledgeBaseDetail } from '@/api/knowledgeBase'
import {
  getDocumentList,
  deleteDocument,
  downloadDocument,
  reindexDocument
} from '@/api/knowledgeBaseDocument'

const route = useRoute()
const router = useRouter()

const currentKB = ref(null)
const documents = ref([])
const docLoading = ref(false)
const refreshTimer = ref(null)
const reindexingDocId = ref(null)

// 从路由参数获取知识库ID
const kbId = computed(() => {
  return route.params.kbId ? parseInt(route.params.kbId) : null
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

// 文档列表相关
const loadDocuments = async () => {
  if (!kbId.value) return
  
  docLoading.value = true
  try {
    const response = await getDocumentList(kbId.value)
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
  if (refreshTimer.value) return
  
  refreshTimer.value = setInterval(() => {
    if (kbId.value) {
      getDocumentList(kbId.value)
        .then(response => {
          documents.value = response || []
          // 如果没有正在向量化的文档，停止定时刷新
          const hasVectorizing = documents.value.some(doc => doc.vectorizedStatus === 1)
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
  try {
    reindexingDocId.value = doc.id
    await reindexDocument(kbId.value, doc.id)
    ElMessage.success('重新向量化任务已提交，请稍后查看状态')
    await loadDocuments()
    startAutoRefresh()
  } catch (error) {
    ElMessage.error('重新向量化失败：' + (error.message || '未知错误'))
  } finally {
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
})
</script>

<style scoped>
.document-list {
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

.doc-list-section {
  margin-top: 0;
}

.documents-table {
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

/* 向量化状态列和操作列表头背景颜色一致 */
:deep(.el-table__header-wrapper .el-table__header tr th:nth-child(5)),
:deep(.el-table__header-wrapper .el-table__header tr th:nth-child(6)) {
  background: #f0f2f5;
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

