<template>
  <div class="chat-app">
    <el-card class="chat-container">
      <template #header>
        <div class="chat-header">
          <div class="chat-header-left">
            <AppIcon :icon="appInfo?.icon" :size="32" class="app-icon" />
            <h3>{{ appInfo?.name || '聊天应用' }}</h3>
          </div>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <div class="chat-messages" ref="messagesRef" @scroll="handleScroll">
        <div
          v-for="(message, index) in messages"
          :key="`msg-${index}-${message.role}`"
          :class="['message', message.role]"
        >
          <div class="message-content">
            <div class="message-time">{{ formatTime(message.time) }}</div>
            <div class="message-text" v-html="formatMessage(message.content)"></div>
          </div>
        </div>
        <div v-if="loading && (!messages.length || messages[messages.length - 1]?.role !== 'assistant' || !messages[messages.length - 1]?.content)" class="message assistant">
          <div class="message-content">
            <div class="message-text">正在思考...</div>
          </div>
        </div>
      </div>

      <div v-if="isInputEnabled || isFileUploadEnabled" class="chat-input">
        <div class="chat-input-main">
          <div v-if="isFileUploadEnabled" class="upload-header">
            <div class="upload-tip">
              单个文件不超过10MB,选择文件后将立即上传到Dify。
            </div>
            <div v-if="fileList.length" class="upload-file-list">
              <div class="upload-file-item" v-for="file in fileList" :key="file.uid">
                <div class="file-info">
                  <el-icon class="file-icon">
                    <Document v-if="!isImageFile(file)" />
                    <Picture v-else />
                  </el-icon>
                  <span class="file-name">{{ file.name }}</span>
                  <span class="file-size">{{ formatFileSize(file.size) }}</span>
                  <span class="file-status-inline">
                    <el-tag
                      :type="getFileStatusType(file.status)"
                      size="small"
                      class="file-status"
                    >
                      {{ getFileStatusText(file.status) }}
                    </el-tag>
                  </span>
                  <el-button link type="danger" class="file-remove" @click="removeFileFromList(file)">
                    移除
                  </el-button>
                </div>
                
              </div>
            </div>
          </div>

        <el-input
          v-if="isInputEnabled"
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="请输入消息..."
          @keydown.ctrl.enter="handleSend"
          class="chat-input-textarea"
        />
          <div class="input-actions">
            <el-upload
              v-if="isFileUploadEnabled"
              ref="uploadRef"
              v-model:file-list="fileList"
              :auto-upload="false"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              :limit="10"
              multiple
              list-type="text"
              :show-file-list="false"
            >
              <template #trigger>
                <el-button type="primary" plain class="action-btn action-btn--upload">
                  <el-icon><UploadFilled /></el-icon>
                  选择文件
                </el-button>
              </template>
            </el-upload>
            <el-button class="action-btn action-btn--clear" @click="handleClear">
              <el-icon><Delete /></el-icon>
              清空
            </el-button>
            <el-button class="action-btn action-btn--send" type="primary" @click="handleSend" :loading="loading" :disabled="!canSend">
              <el-icon><Promotion /></el-icon>
              发送 (Ctrl+Enter)
            </el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAppDetail, chatApp, chatAppStream, uploadFile } from '@/api/aiApp'
import { renderMarkdown } from '@/composables/useMarkdown'
import { processSSEStream } from '@/composables/useSSEStream'
import { extractContent, updateConversationId } from '@/composables/useResponseHandler'
import { getFullAPIUrl } from '@/config/api'
import AppIcon from '@/components/AppIcon.vue'
import { UploadFilled, Document, Picture, Delete, Promotion } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const appInfo = ref(null)
const inputText = ref('')
const messages = ref([])
const loading = ref(false)
const messagesRef = ref(null)
const conversationId = ref(null)
const isUserScrolling = ref(false) // 标记用户是否在手动滚动
const autoScrollEnabled = ref(true) // 是否启用自动滚动

const uploadRef = ref(null)
const fileList = ref([])
const sessionUserId = ref('user_' + Date.now())

const isInputEnabled = computed(() => appInfo.value?.inputEnabled !== false)
const isFileUploadEnabled = computed(() => appInfo.value?.fileUploadEnabled === true)

const hasUploadingFiles = computed(() => (fileList.value || []).some(f => f.status === 'uploading'))
const hasValidContent = computed(() => {
  const hasText = inputText.value && inputText.value.trim().length > 0
  const hasFiles = (fileList.value || []).some(f => f.status === 'success' && f.uploadFileId)
  return hasText || hasFiles
})
const canSend = computed(() => !loading.value && !hasUploadingFiles.value && hasValidContent.value)

const fetchAppInfo = async () => {
  try {
    const res = await getAppDetail(route.params.id)
    appInfo.value = res
  } catch (error) {
    ElMessage.error('获取应用信息失败')
  }
}

const isImageFile = (file) => {
  const type = file?.raw?.type || file?.type || ''
  return type.startsWith('image/')
}

const formatFileSize = (bytes) => {
  if (!bytes && bytes !== 0) return ''
  if (bytes === 0) return '0B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + sizes[i]
}

const getFileStatusType = (status) => {
  if (status === 'success') return 'success'
  if (status === 'fail') return 'danger'
  if (status === 'uploading') return 'warning'
  return 'info'
}

const getFileStatusText = (status) => {
  if (status === 'success') return '上传成功'
  if (status === 'fail') return '上传失败'
  if (status === 'uploading') return '上传中'
  return '待上传'
}

const getFileType = (file) => {
  const type = file?.type || ''
  if (type.startsWith('image/')) return 'image'
  if (type.includes('pdf')) return 'document'
  if (type.includes('text') || type.includes('html')) return 'document'
  if (type.includes('word') || type.includes('officedocument')) return 'document'
  if (type.includes('excel') || type.includes('spreadsheet')) return 'document'
  if (type.includes('powerpoint') || type.includes('presentation')) return 'document'
  return 'document'
}

const uploadSingleFile = async (fileItem) => {
  if (!fileItem?.raw) return
  fileItem.status = 'uploading'
  try {
    const formData = new FormData()
    formData.append('file', fileItem.raw)
    formData.append('user', sessionUserId.value)
    const result = await uploadFile(route.params.id, formData)
    if (result && result.id) {
      fileItem.status = 'success'
      fileItem.uploadFileId = result.id
      fileItem.uploadFileType = getFileType(fileItem.raw)
      fileItem.uploadResult = result
      return result
    }
    throw new Error('上传响应中缺少文件ID')
  } catch (e) {
    fileItem.status = 'fail'
    throw e
  }
}

const handleFileChange = async (file, files) => {
  if (!isFileUploadEnabled.value) return
  const tooLarge = file?.raw?.size && file.raw.size > 10 * 1024 * 1024
  if (tooLarge) {
    ElMessage.error(`文件 ${file.name} 超过10MB限制`)
    const index = fileList.value.findIndex(f => f.uid === file.uid)
    if (index > -1) fileList.value.splice(index, 1)
    return
  }

  // 覆盖为新数组以确保响应式更新
  fileList.value = Array.isArray(files) ? [...files] : []

  // 批量处理所有待上传/失败的文件（含刚选择的）
  for (const f of fileList.value) {
    if (f.status === 'success' && f.uploadFileId) continue
    if (!f.raw) continue
    try {
      await uploadSingleFile(f)
    } catch (e) {
      ElMessage.error(`文件 ${f.name} 上传失败: ${e.message || '未知错误'}`)
    }
  }
}

const handleFileRemove = (file, files) => {
  fileList.value = files
}

const removeFileFromList = (file) => {
  const nextFiles = (fileList.value || []).filter(f => f.uid !== file.uid)
  handleFileRemove(file, nextFiles)
}

const getUploadedFiles = () => {
  const uploadedFiles = []
  for (const fileItem of fileList.value || []) {
    if (fileItem.status === 'success' && fileItem.uploadFileId) {
      uploadedFiles.push({
        transfer_method: 'local_file',
        upload_file_id: fileItem.uploadFileId,
        type: fileItem.uploadFileType || getFileType(fileItem.raw)
      })
    }
  }
  return uploadedFiles
}

const handleSend = async () => {
  if (hasUploadingFiles.value) {
    ElMessage.warning('文件正在上传中，请稍后发送')
    return
  }
  if (!hasValidContent.value) {
    ElMessage.warning('请输入消息或上传文件')
    return
  }

  const uploadedFiles = isFileUploadEnabled.value ? getUploadedFiles() : []
  const userMessage = {
    role: 'user',
    content: inputText.value.trim() || (uploadedFiles.length ? `已发送 ${uploadedFiles.length} 个文件` : ''),
    time: new Date()
  }
  messages.value.push(userMessage)
  const question = inputText.value.trim()
  const queryToSend = question || '请分析上传的文件。'
  inputText.value = ''
  fileList.value = []
  loading.value = true

  await nextTick()
  autoScrollEnabled.value = true
  scrollToBottom()

  try {
    const requestData = {
      query: queryToSend,
      userId: sessionUserId.value,
      conversationId: conversationId.value,
      stream: appInfo.value?.streamEnabled || false,
      inputs: {},
      files: uploadedFiles.length ? uploadedFiles : undefined
    }

    if (appInfo.value?.streamEnabled) {
      await handleStreamChat(requestData)
    } else {
      await handleNormalChat(requestData)
    }
  } catch (error) {
    ElMessage.error('发送消息失败')
    messages.value.push({
      role: 'assistant',
      content: '抱歉，发生了错误，请稍后重试。',
      time: new Date()
    })
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

const handleNormalChat = async (requestData) => {
  const res = await chatApp(route.params.id, requestData)
  
  updateConversationId(res, conversationId)
  
  messages.value.push({
    role: 'assistant',
    content: extractContent(res, ['answer'], '无响应'),
    time: new Date()
  })
}

const handleStreamChat = async (requestData) => {
  const messageIndex = messages.value.length
  let updateTimer = null
  let lastUpdateTime = 0
  const UPDATE_INTERVAL = 100
  
  // 创建初始消息
  messages.value = [...messages.value, {
    role: 'assistant',
    content: '',
    time: new Date()
  }]
  
  await nextTick()
  scrollToBottomSmooth()
  
  // 节流更新消息内容
  const updateMessage = (content) => {
    const now = Date.now()
    
    messages.value[messageIndex].content = content
    
    if (updateTimer) clearTimeout(updateTimer)
    
    if (now - lastUpdateTime >= UPDATE_INTERVAL) {
      lastUpdateTime = now
      updateTimer = setTimeout(() => {
        requestAnimationFrame(() => {
          nextTick(() => {
            if (autoScrollEnabled.value) {
              scrollToBottomSmooth()
            }
          })
        })
      }, 0)
    } else {
      updateTimer = setTimeout(() => {
        if (Date.now() - lastUpdateTime >= UPDATE_INTERVAL) {
          lastUpdateTime = Date.now()
          requestAnimationFrame(() => {
            nextTick(() => {
              if (autoScrollEnabled.value) scrollToBottomSmooth()
            })
          })
        }
      }, UPDATE_INTERVAL - (now - lastUpdateTime))
    }
  }

  try {
    const response = await fetch(getFullAPIUrl(`/api/ai-apps/${route.params.id}/chat/stream`), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      },
      body: JSON.stringify(requestData)
    })

    await processSSEStream(response, {
      cumulative: true,
      contentFields: ['answer'],
      onData: (json, cumulativeContent) => {
        // 更新消息内容
        if (cumulativeContent !== null) {
          updateMessage(cumulativeContent)
        }
        
        // 更新对话ID
        if (json.conversation_id || json.conversationId) {
          conversationId.value = json.conversation_id || json.conversationId
        }
        
        // 处理错误事件
        if (json.event === 'error') {
          updateMessage('发生错误: ' + (json.answer || '未知错误'))
        }
      },
      onError: (error) => {
        updateMessage('抱歉，流式响应处理出错: ' + (error.message || '未知错误'))
      },
      onComplete: () => {
        if (updateTimer) clearTimeout(updateTimer)
      }
    })
  } finally {
    if (updateTimer) clearTimeout(updateTimer)
    messages.value[messageIndex].content = messages.value[messageIndex].content || ''
    await nextTick()
    autoScrollEnabled.value = true
    scrollToBottom()
  }
}

const handleClear = () => {
  messages.value = []
  conversationId.value = null
  fileList.value = []
  inputText.value = ''
}

const handleBack = () => {
  // 根据用户角色返回不同页面
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      const userInfo = JSON.parse(userInfoStr)
      router.push(userInfo.role === 1 ? '/admin/apps' : '/user/apps')
    } catch (e) {
      router.push('/admin/apps')
    }
  } else {
    router.push('/admin/apps')
  }
}

// 使用统一的 Markdown 渲染函数（支持数学公式、代码高亮等）
const formatMessage = renderMarkdown

const formatTime = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleTimeString('zh-CN')
}

// 检查用户是否接近底部（距离底部50px以内）
const isNearBottom = () => {
  if (!messagesRef.value) return true
  const { scrollTop, scrollHeight, clientHeight } = messagesRef.value
  return scrollHeight - scrollTop - clientHeight < 50
}

// 处理滚动事件
const handleScroll = () => {
  if (!messagesRef.value) return
  // 如果用户滚动到接近底部，重新启用自动滚动
  if (isNearBottom()) {
    autoScrollEnabled.value = true
    isUserScrolling.value = false
  } else {
    // 用户手动滚动，暂时禁用自动滚动
    autoScrollEnabled.value = false
    isUserScrolling.value = true
  }
}

// 平滑滚动到底部（用于流式输出，避免跳动）
const scrollToBottomSmooth = () => {
  if (!messagesRef.value || !autoScrollEnabled.value) return
  
  requestAnimationFrame(() => {
    if (messagesRef.value) {
      const container = messagesRef.value
      const targetScrollTop = container.scrollHeight - container.clientHeight
      
      // 直接设置 scrollTop，不使用平滑滚动，避免跳动
      // 只在用户接近底部时才自动滚动
      if (isNearBottom()) {
        container.scrollTop = targetScrollTop
      }
    }
  })
}

// 立即滚动到底部（用于初始化和完成时）
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      requestAnimationFrame(() => {
        if (messagesRef.value) {
          messagesRef.value.scrollTop = messagesRef.value.scrollHeight
        }
      })
    }
  })
}

onMounted(() => {
  fetchAppInfo()
})
</script>

<style scoped>
.chat-app {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.chat-container {
  width: 100%;
  max-width: 1200px;
  height: 90vh;
  display: flex;
  flex-direction: column;
}

/* 确保 el-card 的 body 也使用 flex 布局 */
.chat-container :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  padding: 0;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-header-left .app-icon {
  flex-shrink: 0;
}

.chat-header h3 {
  margin: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px;
  background: #f5f7fa;
  min-height: 0; /* 确保 flex 子元素可以缩小 */
  scroll-behavior: auto; /* 禁用平滑滚动，避免跳动 */
  /* 优化滚动性能 */
  will-change: scroll-position;
  transform: translateZ(0); /* 启用硬件加速 */
}

.upload-file-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.file-icon {
  flex-shrink: 0;
  color: #909399;
}

.file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  flex-shrink: 0;
  color: #909399;
  font-size: 12px;
}

.file-status {
  flex-shrink: 0;
}

.file-status-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.message {
  margin-bottom: 20px;
  display: flex;
}

/* 用户消息：右对齐，从右侧滑入动画 */
.message.user {
  justify-content: flex-end;
  animation: slideInFromRight 0.4s ease-out;
}

/* 助手消息：左对齐，从左向右动画 */
.message.assistant {
  justify-content: flex-start;
  animation: slideInFromLeft 0.4s ease-out;
}

@keyframes slideInFromLeft {
  from {
    opacity: 0;
    transform: translateX(-30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes slideInFromRight {
  from {
    opacity: 0;
    transform: translateX(30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 8px;
}

.message.user .message-content {
  background: var(--el-color-primary, #409eff);
  color: white;
}

.message.assistant .message-content {
  background: white;
  color: #333;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.message-text {
  word-wrap: break-word;
  line-height: 1.6;
}

/* Markdown 样式 */
.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3),
.message-text :deep(h4),
.message-text :deep(h5),
.message-text :deep(h6) {
  margin-top: 1em;
  margin-bottom: 0.5em;
  font-weight: 600;
  line-height: 1.25;
}

.message-text :deep(h1) {
  font-size: 2em;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 0.3em;
}

.message-text :deep(h2) {
  font-size: 1.5em;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 0.3em;
}

.message-text :deep(h3) {
  font-size: 1.25em;
}

.message-text :deep(p) {
  margin-bottom: 1em;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  margin-bottom: 1em;
  padding-left: 2em;
}

.message-text :deep(li) {
  margin-bottom: 0.25em;
}

.message-text :deep(blockquote) {
  padding: 0 1em;
  color: #6a737d;
  border-left: 0.25em solid #dfe2e5;
  margin-bottom: 1em;
}

.message-text :deep(code) {
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  background-color: rgba(64, 158, 255, 0.15) !important; /* 浅蓝色背景，提高对比度 */
  color: #303133 !important; /* 深色文字，确保可读性 */
  border-radius: 3px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
  border: 1px solid rgba(64, 158, 255, 0.2); /* 添加边框增强可见性 */
}

.message-text :deep(pre) {
  padding: 16px;
  overflow: auto;
  font-size: 85%;
  line-height: 1.45;
  background-color: #1e1e1e;
  border-radius: 6px;
  margin-bottom: 1em;
}

.message-text :deep(pre code),
.message-content :deep(pre code) {
  display: inline;
  max-width: auto;
  padding: 0;
  margin: 0;
  overflow: visible;
  line-height: inherit;
  word-wrap: normal;
  background-color: transparent;
  border: 0;
  font-size: 100%;
}

.message-text :deep(table) {
  border-spacing: 0;
  border-collapse: collapse;
  margin-bottom: 1em;
  width: 100%;
}

.message-text :deep(table th),
.message-text :deep(table td) {
  padding: 6px 13px;
  border: 1px solid #dfe2e5;
}

.message-text :deep(table th) {
  font-weight: 600;
  background-color: #f6f8fa;
}

.message-text :deep(table tr:nth-child(2n)) {
  background-color: #f6f8fa;
}

.message-text :deep(a) {
  color: #0366d6;
  text-decoration: none;
}

.message-text :deep(a:hover) {
  text-decoration: underline;
}

.message-text :deep(hr) {
  height: 0.25em;
  padding: 0;
  margin: 1.5em 0;
  background-color: #e1e4e8;
  border: 0;
}

.message-text :deep(img) {
  max-width: 100%;
  height: auto;
  margin-bottom: 1em;
  border-radius: 4px;
}

.message-text :deep(strong) {
  font-weight: 600;
}

.message-text :deep(em) {
  font-style: italic;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  margin-bottom: 4px;
}

.chat-input {
  padding: 20px;
  border-top: 1px solid #e4e7ed;
  flex-shrink: 0; /* 防止输入框被压缩 */
}

.chat-input-main {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.upload-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.2;
}

.upload-file-list {
  max-height: 140px;
  overflow: auto;
  padding: 4px 0;
}

.file-remove {
  flex-shrink: 0;
}

.chat-input-textarea :deep(.el-textarea__inner) {
  resize: none;
}

.chat-input-textarea :deep(.el-textarea__inner) {
  height: 60px;
  min-height: 60px;
}

.input-actions {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
  align-items: stretch;
  margin-top: 8px;
}

.input-actions :deep(.el-upload),
.input-actions :deep(.el-button) {
  width: 100%;
}

.input-actions :deep(.el-button) {
  height: 40px;
}

.input-actions :deep(.el-upload) {
  display: flex;
}

.input-actions :deep(.el-upload .el-button) {
  width: 100%;
}

.input-actions :deep(.action-btn) {
  justify-content: center;
  gap: 8px;
  border-radius: 10px;
  font-weight: 600;
}

.input-actions :deep(.action-btn--upload) {
  border-color: #409eff;
  color: #409eff;
}

.input-actions :deep(.action-btn--clear) {
  border-color: #f56c6c;
  color: #f56c6c;
}

.input-actions :deep(.action-btn--send) {
  background-color: #67c23a;
  border-color: #67c23a;
}
</style>

