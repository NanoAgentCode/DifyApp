<template>
  <div class="portal-container">
    <!-- 顶部导航栏 -->
    <AppHeader v-model="isHeaderCollapsed" @command="handleCommand" />

    <!-- 主要内容区域 -->
    <div class="portal-content" :class="{ 'content-header-collapsed': isHeaderCollapsed }">
      <!-- 初始欢迎界面（无对话时显示） -->
      <div v-if="chatHistory.length === 0 && !isInputFocused" class="welcome-section">
        <div class="assistant-identity">
          <el-icon class="assistant-icon"><Service /></el-icon>
          <span class="assistant-name">NanoAgent</span>
        </div>
        <div class="welcome-message">
          你好！我是NanoAgent，很高兴为你提供帮助。有什么问题或需要协助的地方吗？
        </div>
        <div class="suggested-prompts">
          <div class="prompt-item" @click="handlePromptClick('最近有什么有趣的事情吗')">
            <span class="prompt-bullet">•</span>
            <span class="prompt-text">最近有什么有趣的事情吗</span>
          </div>
          <div class="prompt-item" @click="handlePromptClick('你喜欢什么样的音乐')">
            <span class="prompt-bullet">•</span>
            <span class="prompt-text">你喜欢什么样的音乐</span>
          </div>
          <div class="prompt-item" @click="handlePromptClick('有什么特别的兴趣爱好吗')">
            <span class="prompt-bullet">•</span>
            <span class="prompt-text">有什么特别的兴趣爱好吗</span>
          </div>
        </div>
      </div>

      <!-- 对话历史区域（有对话时显示） -->
      <div v-if="chatHistory.length > 0 || isInputFocused" class="chat-history-section">
        <MessageList
          :messages="chatHistory"
          :sending="sending"
          :on-regenerate="handleRegenerate"
          ref="messageListRef"
        />
      </div>
    </div>

    <!-- 中央输入区域 -->
    <div class="input-section">
      <div class="input-wrapper">
        <!-- 输入框 -->
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          :autosize="{ minRows: 3, maxRows: 8 }"
          placeholder="有问题尽管问ima"
          class="portal-input"
          @keydown.enter.exact.prevent="handleSend"
          @keydown.ctrl.enter="handleSend"
          @focus="handleInputFocus"
          @blur="handleInputBlur"
          :disabled="sending"
        />
      </div>

      <!-- 浮动控制选项 -->
      <div class="input-controls-float">
        <!-- 左侧控制按钮 -->
        <div class="input-left-controls">
          <el-dropdown 
            @command="handleModeChange" 
            trigger="click"
            placement="top-start"
          >
            <div class="control-item" :class="{ 'mode-selected': conversationMode === 'rag' }">
              <el-icon><ChatLineRound /></el-icon>
              <span>{{ conversationMode === 'rag' ? '知识库问答' : '对话模式' }}</span>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="chat" :class="{ 'is-selected': conversationMode === 'chat' }">
                  <span>普通对话</span>
                  <el-icon v-if="conversationMode === 'chat'" style="margin-left: 8px;"><Check /></el-icon>
                </el-dropdown-item>
                <el-dropdown-item command="rag" :class="{ 'is-selected': conversationMode === 'rag' }">
                  <span>知识库问答</span>
                  <el-icon v-if="conversationMode === 'rag'" style="margin-left: 8px;"><Check /></el-icon>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          
          <el-dropdown @command="handleModelChange" trigger="click">
            <div class="control-item">
              <el-icon><Connection /></el-icon>
              <span>{{ selectedModelName || 'DS V3.2' }}</span>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="model in availableModels"
                  :key="model.id"
                  :command="model.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center;">
                    <span>{{ model.name }}</span>
                    <el-tag v-if="model.isDefault" type="primary" size="small">默认</el-tag>
                  </div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- 知识库选择区域（仅在知识库问答模式下显示） -->
          <el-dropdown 
            v-if="conversationMode === 'rag'"
            @command="handleKnowledgeBaseSelect" 
            @visible-change="handleDropdownVisibleChange"
            trigger="click" 
            class="kb-selector-dropdown"
            popper-class="kb-dropdown-popper"
          >
            <div class="control-item kb-control-item" :class="{ 'kb-selected': selectedKnowledgeBaseId }">
              <span>@</span>
              <el-input
                v-model="selectedKnowledgeBaseName"
                placeholder="选择知识库"
                readonly
                class="kb-input-readonly"
              />
            </div>
            <template #dropdown>
              <el-dropdown-menu class="kb-dropdown-menu">
                <div v-if="availableKnowledgeBases.length === 0" style="padding: 20px; text-align: center; color: #909399;">
                  <el-icon style="font-size: 24px; margin-bottom: 8px;"><Document /></el-icon>
                  <div style="font-size: 14px;">暂无可用知识库</div>
                  <div style="font-size: 12px; margin-top: 4px;">请先在知识管理中创建知识库</div>
                </div>
                <template v-else>
                  <el-dropdown-item 
                    v-for="kb in availableKnowledgeBases"
                    :key="kb.id"
                    :command="kb.id"
                    class="kb-dropdown-item"
                  >
                    <div class="kb-item-content">
                      <el-radio 
                        :model-value="selectedKnowledgeBaseId === kb.id"
                        :label="kb.id"
                        @change="handleKnowledgeBaseSelect(kb.id)"
                        @click.stop
                      >
                        <div class="kb-item-info">
                          <div style="font-weight: 500;">{{ kb.name }}</div>
                          <div style="font-size: 12px; color: #909399;">{{ kb.documentCount || 0 }} 个文档</div>
                        </div>
                      </el-radio>
                    </div>
                  </el-dropdown-item>
                </template>
                <el-dropdown-item v-if="selectedKnowledgeBaseId" divided command="clear" class="kb-dropdown-item">
                  <span style="color: #909399;">清除选择</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- 右侧控制按钮 -->
        <div class="input-right-controls">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :on-change="handleFileChange"
            :show-file-list="false"
            accept="image/*"
            multiple
          >
            <div class="control-item">
              <el-icon><Paperclip /></el-icon>
            </div>
          </el-upload>

          <el-tooltip content="重置会话" placement="top">
            <div class="control-item" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
            </div>
          </el-tooltip>

          <el-button
            type="primary"
            :disabled="(!question.trim() && selectedFiles.length === 0) || sending"
            @click="handleSend"
            :loading="sending"
            class="send-button"
          >
            <el-icon><Promotion /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 已选择的附件预览 -->
      <div v-if="selectedFiles.length > 0" class="attachments-preview">
        <div
          v-for="(file, index) in selectedFiles"
          :key="index"
          class="attachment-item"
        >
          <el-image
            v-if="isImageFile(file)"
            :src="getFilePreview(file)"
            :preview-src-list="[getFilePreview(file)]"
            fit="contain"
            class="attachment-preview-image"
            loading="lazy"
            :preview-teleported="true"
            :z-index="3000"
          />
          <div class="attachment-info">
            <span class="attachment-name" :title="file.name">{{ file.name }}</span>
            <span class="attachment-size">{{ formatFileSize(file.size) }}</span>
          </div>
          <el-button
            type="danger"
            :icon="Delete"
            size="small"
            text
            @click="removeFile(index)"
            :disabled="sending"
          />
        </div>
      </div>
    </div>

    <!-- 底部免责声明 -->
    <div class="portal-footer">
      <span class="footer-text">内容由AI生成仅供参考</span>
    </div>

    <!-- 修改密码对话框 -->
    <ChangePasswordDialog
      v-model="showChangePasswordDialog"
      @success="handlePasswordChangeSuccess"
    />
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatLineRound,
  ArrowDown,
  ArrowUp,
  Connection,
  Paperclip,
  Promotion,
  Delete,
  Document,
  DocumentAdd,
  List,
  DataAnalysis,
  Clock,
  Picture,
  Refresh,
  Service,
  Check,
  User
} from '@element-plus/icons-vue'
import { chat, chatStream } from '@/api/chat'
import { getAvailableQAModels, getAvailableQAModelsForRAG } from '@/api/model'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { knowledgeBaseQAStream } from '@/api/knowledgeBaseQA'
import MessageList from '@/components/chat/MessageList.vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import AppHeader from '@/components/AppHeader.vue'

const router = useRouter()

const question = ref('')
const sending = ref(false)
const chatHistory = ref([])
const messageListRef = ref(null)
const conversationId = ref(null)
const availableModels = ref([])
const selectedModelId = ref(null)
const selectedFiles = ref([])
const isInputFocused = ref(false)
const uploadRef = ref(null)
const currentDate = ref('')
const currentTime = ref('')
const availableKnowledgeBases = ref([])
const selectedKnowledgeBaseId = ref(null)
const selectedKnowledgeBase = ref(null)
const conversationMode = ref('chat') // 'chat' 普通对话, 'rag' 知识库问答
const selectedKnowledgeBaseName = ref('')
const showChangePasswordDialog = ref(false)
const isHeaderCollapsed = ref(false)

const selectedModelName = computed(() => {
  if (!selectedModelId.value) return 'DS V3.2'
  const model = availableModels.value.find(m => m.id === selectedModelId.value)
  return model ? model.name : 'DS V3.2'
})

// 获取用户信息
const getUserInfo = () => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      return JSON.parse(userInfoStr)
    } catch (e) {
      console.error('解析用户信息失败', e)
    }
  }
  return null
}

// 处理下拉菜单命令
const handleCommand = (command) => {
  if (command === 'changePassword') {
    showChangePasswordDialog.value = true
  }
  // logout 命令由 AppHeader 组件内部处理
}

// 处理密码修改成功
const handlePasswordChangeSuccess = () => {
  ElMessage.success('密码修改成功')
  showChangePasswordDialog.value = false
}

// 处理输入框焦点
const handleInputFocus = () => {
  isInputFocused.value = true
}

const handleInputBlur = () => {
  // 延迟处理，以便点击按钮时不会立即失焦
  setTimeout(() => {
    if (!sending.value && chatHistory.value.length === 0) {
      isInputFocused.value = false
    }
  }, 200)
}

// 处理模式切换
const handleModeChange = (mode) => {
  conversationMode.value = mode
  if (mode === 'rag') {
    // 切换到知识库问答模式时，如果没有选择知识库，提示用户
    if (!selectedKnowledgeBaseId.value && availableKnowledgeBases.value.length > 0) {
      ElMessage.info('请先选择知识库（点击@符号）')
    }
  } else {
    // 切换到普通对话模式时，清除知识库选择
    selectedKnowledgeBaseId.value = null
    selectedKnowledgeBase.value = null
    selectedKnowledgeBaseName.value = ''
  }
  // 重新加载模型列表（因为知识库问答和普通对话使用的模型可能不同）
  loadAvailableModels()
}

// 处理模型切换
const handleModelChange = (modelId) => {
  selectedModelId.value = modelId
}

// 处理下拉菜单显示状态变化
const handleDropdownVisibleChange = (visible) => {
  if (visible) {
    // 下拉菜单打开时，更新宽度
    setDropdownWidth()
  }
}

// 处理知识库选择
const handleKnowledgeBaseSelect = (kbId) => {
  if (kbId === 'clear') {
    selectedKnowledgeBaseId.value = null
    selectedKnowledgeBase.value = null
    selectedKnowledgeBaseName.value = ''
    ElMessage.success('已清除知识库选择')
    return
  }
  
  const kb = availableKnowledgeBases.value.find(k => k.id === kbId)
  if (kb) {
    selectedKnowledgeBaseId.value = kbId
    selectedKnowledgeBase.value = kb
    selectedKnowledgeBaseName.value = kb.name
    ElMessage.success(`已选择知识库：${kb.name}`)
  }
}

// 处理文件选择
const handleFileChange = (file) => {
  if (!file.raw.type.startsWith('image/')) {
    ElMessage.warning('只能上传图片文件')
    return
  }
  
  if (file.raw.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过10MB')
    return
  }
  
  selectedFiles.value.push(file.raw)
}

// 移除文件
const removeFile = (index) => {
  selectedFiles.value.splice(index, 1)
}

// 判断是否为图片文件
const isImageFile = (file) => {
  return file.type && file.type.startsWith('image/')
}

// 获取文件预览URL
const getFilePreview = (file) => {
  return URL.createObjectURL(file)
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

// 处理功能点击
const handleFeatureClick = (feature) => {
  const userInfo = getUserInfo()
  const basePath = userInfo?.role === 1 ? '/admin' : '/user'
  
  switch (feature) {
    case 'kb-qa':
      router.push(`${basePath}/kb-qa`)
      break
    case 'document':
      router.push(`${basePath}/document-reader`)
      break
    case 'apps':
      router.push(`${basePath}/apps`)
      break
    case 'ai-drawio':
      router.push(`${basePath}/ai-drawio`)
      break
    case 'chat-history':
      router.push(`${basePath}/chat-history`)
      break
  }
}

// 发送消息
const handleSend = async () => {
  if ((!question.value.trim() && selectedFiles.value.length === 0) || sending.value) {
    return
  }

  const userQuestion = question.value.trim()
  
  // 构建用户消息内容（包含附件信息）
  let userMessageContent = userQuestion
  if (selectedFiles.value.length > 0) {
    const fileNames = selectedFiles.value.map(f => f.name).join('、')
    userMessageContent = `[已上传 ${selectedFiles.value.length} 张图片: ${fileNames}]\n\n${userQuestion}`
  }
  
  // 添加用户消息
  chatHistory.value.push({
    type: 'user',
    content: userMessageContent,
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    files: selectedFiles.value.map(f => ({
      name: f.name,
      size: f.size,
      type: f.type,
      preview: getFilePreview(f)
    }))
  })

  // 保存当前选择的文件（用于发送）
  const filesToSend = [...selectedFiles.value]
  
  // 清空输入框和文件列表
  question.value = ''
  selectedFiles.value = []
  
  // 确保输入框获得焦点
  isInputFocused.value = true
  
  // 滚动到底部
  await nextTick()
  scrollToBottom(true)

  // 添加AI回复占位
  const aiMessageIndex = chatHistory.value.length
  chatHistory.value.push({
    type: 'assistant',
    content: '',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    isLoading: true
  })
  
  sending.value = true

  // 构建历史对话
  const history = chatHistory.value
    .slice(0, -1)
    .filter(msg => !msg.isLoading)
    .map(msg => ({
      role: msg.type === 'user' ? 'user' : 'assistant',
      content: msg.content
    }))

  const userInfo = getUserInfo()
  const userId = userInfo ? userInfo.userId : null

  try {
    const currentConversationId = conversationId.value
    
    // 如果选择了知识库问答模式且选择了知识库，使用知识库问答API；否则使用普通聊天API
    if (conversationMode.value === 'rag' && selectedKnowledgeBaseId.value) {
      // 使用知识库问答流式响应
      await handleKnowledgeBaseStreamResponse(
        selectedKnowledgeBaseId.value,
        userQuestion,
        currentConversationId,
        userId,
        history,
        aiMessageIndex,
        selectedModelId.value
      )
    } else if (conversationMode.value === 'rag' && !selectedKnowledgeBaseId.value) {
      ElMessage.warning('请先选择知识库（点击@符号）')
      // 移除AI回复占位
      chatHistory.value.pop()
      sending.value = false
      return
    } else {
      // 使用普通聊天流式响应
      await handleStreamResponse(userQuestion, currentConversationId, userId, history, aiMessageIndex, selectedModelId.value, false, filesToSend)
    }
  } catch (error) {
    console.error('发送消息失败', error)
    ElMessage.error('发送消息失败：' + (error.message || '未知错误'))
    if (chatHistory.value[aiMessageIndex]) {
      chatHistory.value[aiMessageIndex].content = '抱歉，生成答案时发生错误，请重试。'
      chatHistory.value[aiMessageIndex].isLoading = false
    }
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom(false)
  }
}

// 处理知识库问答流式响应
const handleKnowledgeBaseStreamResponse = async (kbId, question, requestConversationId, userId, history, aiMessageIndex, modelId) => {
  let reader = null
  let response = null
  
  let rafId = null
  let pendingContent = null
  
  const scheduleUpdate = (content) => {
    pendingContent = content
    
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
    }
    
    rafId = requestAnimationFrame(() => {
      if (chatHistory.value[aiMessageIndex] && pendingContent !== null) {
        chatHistory.value[aiMessageIndex].content = pendingContent
        if (chatHistory.value[aiMessageIndex].isLoading) {
          chatHistory.value[aiMessageIndex].isLoading = false
        }
        
        if (messageListRef.value?.scrollToBottom) {
          messageListRef.value.scrollToBottom(false)
        }
      }
      rafId = null
    })
  }
  
  try {
    response = await knowledgeBaseQAStream(kbId, question, requestConversationId, userId, history, modelId)
    
    if (!response.ok) {
      const errorText = await response.text().catch(() => '未知错误')
      throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`)
    }

    if (!response.body) {
      throw new Error('响应体不可用，连接可能已断开')
    }

    reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let hasReceivedData = false

    try {
      while (true) {
        let readResult
        try {
          readResult = await reader.read()
        } catch (readError) {
          if (readError.message && readError.message.includes('disconnected')) {
            console.warn('流式连接已断开')
            if (rafId !== null) {
              cancelAnimationFrame(rafId)
              rafId = null
            }
            if (hasReceivedData && chatHistory.value[aiMessageIndex]?.content) {
              if (pendingContent !== null) {
                chatHistory.value[aiMessageIndex].content = pendingContent
                pendingContent = null
              }
              chatHistory.value[aiMessageIndex].isLoading = false
              break
            }
            throw new Error('连接已断开，请重试')
          }
          throw readError
        }
        
        const { done, value } = readResult
        
        if (done) {
          break
        }

        hasReceivedData = true
        buffer += decoder.decode(value, { stream: true })
        
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmedLine = line.trim()
          if (!trimmedLine) continue
          
          if (trimmedLine.startsWith('data:')) {
            const data = trimmedLine.slice(5).trim()
            if (data === '[DONE]' || data === '') continue

            try {
              const json = JSON.parse(data)
              
              if (json.conversationId) {
                conversationId.value = json.conversationId.toString()
              }
              
              if (json.answer !== undefined && json.answer !== null) {
                if (!chatHistory.value[aiMessageIndex]) {
                  continue
                }
                scheduleUpdate(json.answer)
              }

              if (json.finished) {
                if (rafId !== null) {
                  cancelAnimationFrame(rafId)
                  rafId = null
                }
                if (chatHistory.value[aiMessageIndex]) {
                  chatHistory.value[aiMessageIndex].isLoading = false
                  chatHistory.value[aiMessageIndex].content = json.answer || pendingContent || chatHistory.value[aiMessageIndex].content || ''
                  pendingContent = null
                }
                if (json.conversationId) {
                  conversationId.value = json.conversationId.toString()
                }
                break
              }
            } catch (e) {
              console.warn('解析流式数据失败', e, '原始数据:', data)
            }
          }
        }
      }
    } finally {
      if (rafId !== null) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
      if (pendingContent !== null && chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = pendingContent
        pendingContent = null
      }
      
      if (reader) {
        try {
          await reader.cancel()
        } catch (cancelError) {
          console.warn('取消reader失败', cancelError)
        }
      }
    }

    if (buffer.trim()) {
      const lines = buffer.split('\n')
      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine) continue
        
        if (trimmedLine.startsWith('data:')) {
          const data = trimmedLine.slice(5).trim()
          if (data === '[DONE]' || data === '') continue
          
          try {
            const json = JSON.parse(data)
            if (json.answer !== undefined && json.answer !== null && chatHistory.value[aiMessageIndex]) {
              chatHistory.value[aiMessageIndex].content = json.answer
              chatHistory.value[aiMessageIndex].isLoading = false
            }
          } catch (e) {
            console.warn('解析流式数据失败', e, '原始数据:', data)
          }
        }
      }
    }

    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].isLoading) {
      chatHistory.value[aiMessageIndex].isLoading = false
    }
    
  } catch (error) {
    console.error('知识库问答流式响应处理失败', error)
    
    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].content) {
      chatHistory.value[aiMessageIndex].isLoading = false
      chatHistory.value[aiMessageIndex].content += '\n\n⚠️ 连接中断，部分内容可能不完整。'
    } else {
      if (chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = '抱歉，连接已断开，请重试。'
        chatHistory.value[aiMessageIndex].isLoading = false
      }
    }
    
    throw error
  }
}

// 处理流式响应
const handleStreamResponse = async (question, requestConversationId, userId, history, aiMessageIndex, modelId, enableBrowserSearch, files = []) => {
  let reader = null
  let response = null
  
  let rafId = null
  let pendingContent = null
  
  const scheduleUpdate = (content) => {
    pendingContent = content
    
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
    }
    
    rafId = requestAnimationFrame(() => {
      if (chatHistory.value[aiMessageIndex] && pendingContent !== null) {
        chatHistory.value[aiMessageIndex].content = pendingContent
        if (chatHistory.value[aiMessageIndex].isLoading) {
          chatHistory.value[aiMessageIndex].isLoading = false
        }
        
        if (messageListRef.value?.scrollToBottom) {
          messageListRef.value.scrollToBottom(false)
        }
      }
      rafId = null
    })
  }
  
  try {
    response = await chatStream(question, requestConversationId, userId, history, modelId, enableBrowserSearch, files)
    
    if (!response.ok) {
      const errorText = await response.text().catch(() => '未知错误')
      throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`)
    }

    if (!response.body) {
      throw new Error('响应体不可用，连接可能已断开')
    }

    reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let hasReceivedData = false

    try {
      while (true) {
        let readResult
        try {
          readResult = await reader.read()
        } catch (readError) {
          if (readError.message && readError.message.includes('disconnected')) {
            console.warn('流式连接已断开')
            if (rafId !== null) {
              cancelAnimationFrame(rafId)
              rafId = null
            }
            if (hasReceivedData && chatHistory.value[aiMessageIndex]?.content) {
              if (pendingContent !== null) {
                chatHistory.value[aiMessageIndex].content = pendingContent
                pendingContent = null
              }
              chatHistory.value[aiMessageIndex].isLoading = false
              break
            }
            throw new Error('连接已断开，请重试')
          }
          throw readError
        }
        
        const { done, value } = readResult
        
        if (done) {
          break
        }

        hasReceivedData = true
        buffer += decoder.decode(value, { stream: true })
        
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmedLine = line.trim()
          if (!trimmedLine) continue
          
          if (trimmedLine.startsWith('data:')) {
            const data = trimmedLine.slice(5).trim()
            if (data === '[DONE]' || data === '') continue

            try {
              const json = JSON.parse(data)
              
              if (json.conversationId) {
                conversationId.value = json.conversationId.toString()
              }
              
              if (json.answer !== undefined && json.answer !== null) {
                if (!chatHistory.value[aiMessageIndex]) {
                  continue
                }
                scheduleUpdate(json.answer)
              }

              if (json.finished) {
                if (rafId !== null) {
                  cancelAnimationFrame(rafId)
                  rafId = null
                }
                if (chatHistory.value[aiMessageIndex]) {
                  chatHistory.value[aiMessageIndex].isLoading = false
                  chatHistory.value[aiMessageIndex].content = json.answer || pendingContent || chatHistory.value[aiMessageIndex].content || ''
                  pendingContent = null
                }
                if (json.conversationId) {
                  conversationId.value = json.conversationId.toString()
                }
                break
              }
            } catch (e) {
              console.warn('解析流式数据失败', e, '原始数据:', data)
            }
          }
        }
      }
    } finally {
      if (rafId !== null) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
      if (pendingContent !== null && chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = pendingContent
        pendingContent = null
      }
      
      if (reader) {
        try {
          await reader.cancel()
        } catch (cancelError) {
          console.warn('取消reader失败', cancelError)
        }
      }
    }

    if (buffer.trim()) {
      const lines = buffer.split('\n')
      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine) continue
        
        if (trimmedLine.startsWith('data:')) {
          const data = trimmedLine.slice(5).trim()
          if (data === '[DONE]' || data === '') continue
          
          try {
            const json = JSON.parse(data)
            if (json.answer !== undefined && json.answer !== null && chatHistory.value[aiMessageIndex]) {
              chatHistory.value[aiMessageIndex].content = json.answer
              chatHistory.value[aiMessageIndex].isLoading = false
            }
          } catch (e) {
            console.warn('解析流式数据失败', e, '原始数据:', data)
          }
        }
      }
    }

    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].isLoading) {
      chatHistory.value[aiMessageIndex].isLoading = false
    }
    
  } catch (error) {
    console.error('流式响应处理失败', error)
    
    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].content) {
      chatHistory.value[aiMessageIndex].isLoading = false
      chatHistory.value[aiMessageIndex].content += '\n\n⚠️ 连接中断，部分内容可能不完整。'
    } else {
      if (chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = '抱歉，连接已断开，请重试。'
        chatHistory.value[aiMessageIndex].isLoading = false
      }
    }
    
    throw error
  }
}

// 重新生成响应
const handleRegenerate = async (messageIndex) => {
  if (sending.value || messageIndex < 0 || messageIndex >= chatHistory.value.length) {
    return
  }

  const assistantMessage = chatHistory.value[messageIndex]
  if (assistantMessage.type !== 'assistant' || assistantMessage.isLoading) {
    return
  }

  if (messageIndex === 0 || chatHistory.value[messageIndex - 1].type !== 'user') {
    ElMessage.warning('无法找到对应的用户消息')
    return
  }

  const userMessage = chatHistory.value[messageIndex - 1]
  const userQuestion = userMessage.content

  chatHistory.value.splice(messageIndex, 1)

  await nextTick()
  scrollToBottom(true)

  const aiMessageIndex = chatHistory.value.length
  chatHistory.value.push({
    type: 'assistant',
    content: '',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    isLoading: true
  })

  sending.value = true

  const history = chatHistory.value
    .slice(0, -1)
    .filter(msg => !msg.isLoading)
    .map(msg => ({
      role: msg.type === 'user' ? 'user' : 'assistant',
      content: msg.content
    }))

  const userInfo = getUserInfo()
  const userId = userInfo ? userInfo.userId : null

  const filesToSend = []

  try {
    const currentConversationId = conversationId.value
    
    // 如果选择了知识库问答模式且选择了知识库，使用知识库问答API；否则使用普通聊天API
    if (conversationMode.value === 'rag' && selectedKnowledgeBaseId.value) {
      await handleKnowledgeBaseStreamResponse(
        selectedKnowledgeBaseId.value,
        userQuestion,
        currentConversationId,
        userId,
        history,
        aiMessageIndex,
        selectedModelId.value
      )
    } else if (conversationMode.value === 'rag' && !selectedKnowledgeBaseId.value) {
      ElMessage.warning('请先选择知识库（点击@符号）')
      // 移除AI回复占位
      chatHistory.value.pop()
      sending.value = false
      return
    } else {
      await handleStreamResponse(userQuestion, currentConversationId, userId, history, aiMessageIndex, selectedModelId.value, false, filesToSend)
    }
  } catch (error) {
    console.error('重新生成响应失败', error)
    ElMessage.error('重新生成响应失败：' + (error.message || '未知错误'))
    if (chatHistory.value[aiMessageIndex]) {
      chatHistory.value[aiMessageIndex].content = '抱歉，重新生成答案时发生错误，请重试。'
      chatHistory.value[aiMessageIndex].isLoading = false
    }
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom(false)
  }
}

// 滚动到底部
const scrollToBottom = (force = false) => {
  nextTick(() => {
    if (messageListRef.value?.$el) {
      if (!force && !isNearBottom()) {
        return
      }
      messageListRef.value.$el.scrollTop = messageListRef.value.$el.scrollHeight
    }
  })
}

// 检查是否在底部
const isNearBottom = () => {
  if (!messageListRef.value?.$el) return true
  const element = messageListRef.value.$el
  const threshold = 100
  return element.scrollHeight - element.scrollTop - element.clientHeight < threshold
}

// 更新日期和时间
const updateDateTime = () => {
  const now = new Date()
  currentDate.value = now.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}


// 处理刷新（重置会话）
const handleRefresh = () => {
  chatHistory.value = []
  conversationId.value = null
  question.value = ''
  selectedFiles.value = []
  isInputFocused.value = false
  ElMessage.success('会话已重置')
}


// 处理建议提示点击
const handlePromptClick = (prompt) => {
  question.value = prompt
  // 自动聚焦输入框
  nextTick(() => {
    const inputElement = document.querySelector('.portal-input textarea')
    if (inputElement) {
      inputElement.focus()
    }
  })
}

// 加载可用模型列表
const loadAvailableModels = async () => {
  try {
    // 如果是知识库问答模式，加载RAG模型；否则加载普通问答模型
    const api = conversationMode.value === 'rag' ? getAvailableQAModelsForRAG : getAvailableQAModels
    const models = await api()
    availableModels.value = models || []
    
    if (availableModels.value.length > 0) {
      const defaultModel = availableModels.value.find(m => m.isDefault)
      if (defaultModel) {
        selectedModelId.value = defaultModel.id
      } else {
        selectedModelId.value = availableModels.value[0].id
      }
    }
  } catch (error) {
    console.error('加载可用模型列表失败', error)
    availableModels.value = []
  }
}

// 加载知识库列表
const loadKnowledgeBases = async () => {
  try {
    // 获取用户信息
    const userInfo = getUserInfo()
    const userId = userInfo ? userInfo.userId : null
    const userRole = userInfo ? userInfo.role : null
    
    // 构建请求参数
    const params = {
      page: 1,
      pageSize: 100
    }
    
    // 普通用户只获取启用的知识库，管理员可以获取所有
    if (userRole !== 1) {
      params.status = 1 // 1 表示启用状态
    }
    
    // 传递用户ID，后端会根据权限过滤
    if (userId) {
      params.userId = userId
    }
    
    const response = await getKnowledgeBaseList(params)
    
    // 处理不同的响应格式
    let knowledgeBases = []
    if (response && response.content && Array.isArray(response.content)) {
      knowledgeBases = response.content
    } else if (response && response.list && Array.isArray(response.list)) {
      knowledgeBases = response.list
    } else if (Array.isArray(response)) {
      knowledgeBases = response
    } else if (response && response.data) {
      const data = response.data
      if (data.content && Array.isArray(data.content)) {
        knowledgeBases = data.content
      } else if (data.list && Array.isArray(data.list)) {
        knowledgeBases = data.list
      } else if (Array.isArray(data)) {
        knowledgeBases = data
      }
    }
    
    // 过滤出启用的知识库（status === 'active' 或 status === 1）
    availableKnowledgeBases.value = knowledgeBases.filter(kb => {
      const status = kb.status
      return status === 'active' || status === 1 || status === '1'
    })
    
    console.log('加载知识库列表成功，数量:', availableKnowledgeBases.value.length)
  } catch (error) {
    console.error('加载知识库列表失败', error)
    ElMessage.error('加载知识库列表失败：' + (error.message || '未知错误'))
    availableKnowledgeBases.value = []
  }
}

// 监听知识库选择变化，重新加载模型
watch(selectedKnowledgeBaseId, () => {
  if (conversationMode.value === 'rag') {
    loadAvailableModels()
  }
})

// 监听对话模式变化，重新加载模型
watch(conversationMode, () => {
  loadAvailableModels()
})

// 设置下拉菜单宽度（与@控制区域同宽）
const setDropdownWidth = () => {
  nextTick(() => {
    const kbControlItem = document.querySelector('.kb-control-item')
    if (kbControlItem) {
      const width = kbControlItem.offsetWidth
      document.documentElement.style.setProperty('--input-section-width', `${width}px`)
    }
  })
}

onMounted(() => {
  updateDateTime()
  // 每秒更新时间
  setInterval(updateDateTime, 1000)
  loadKnowledgeBases()
  loadAvailableModels()
  setDropdownWidth()
  // 监听窗口大小变化，更新下拉菜单宽度
  window.addEventListener('resize', setDropdownWidth)
})

onUnmounted(() => {
  window.removeEventListener('resize', setDropdownWidth)
})
</script>

<style scoped>
.portal-container {
  min-height: 100vh;
  background: var(--el-bg-color-page, #f5f7fa);
  display: flex;
  flex-direction: column;
  padding: 0;
  margin: 0;
  position: relative;
  width: 100%;
  overflow-x: hidden; /* 防止水平滚动 */
  z-index: 1; /* 确保在导航栏下方 */
}

/* 确保 Portal 页面的导航栏始终显示在最上层并占据顶部宽度 */
.portal-container :deep(.app-header) {
  z-index: 1000 !important;
  position: fixed !important;
  width: 100vw !important;
  max-width: 100vw !important;
  left: 0 !important;
  right: 0 !important;
  top: 0 !important;
}




/* 主要内容区域 */
.portal-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 40px 0; /* 只保留上下内边距，左右为0让滚动条靠右 */
  padding-top: calc(40px + 60px) !important; /* 为固定导航栏留出空间，确保不被覆盖 */
  width: 100%; /* 占据全宽，让滚动条在视口最右侧 */
  margin: 0;
  margin-top: 0 !important; /* 确保没有额外的上边距 */
  transition: padding-top 0.3s ease;
  position: relative;
  box-sizing: border-box;
}

/* 内容容器，用于居中显示内容 */
.portal-content > .welcome-section,
.portal-content > .chat-history-section {
  width: 100%;
  max-width: 1200px;
  min-width: 600px; /* 最小宽度，确保在小屏幕上也有良好显示 */
  margin: 0 auto;
  padding-left: 20px;
  padding-right: 20px;
  box-sizing: border-box;
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .portal-content > .welcome-section,
  .portal-content > .chat-history-section {
    min-width: 500px;
    max-width: 900px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .portal-content > .welcome-section,
  .portal-content > .chat-history-section {
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    width: 100%;
    padding-left: 16px;
    padding-right: 16px;
  }
}

.portal-content.content-header-collapsed {
  padding-top: 40px !important; /* 顶部收起时不需要留出导航栏空间 */
}

/* 顶部系统图标（在 padding-top 位置） */
/* 欢迎界面 */
.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 40px 20px !important; /* 覆盖父容器的padding设置 */
  width: 100%;
  max-width: 1200px;
  min-width: 600px; /* 最小宽度，确保在小屏幕上也有良好显示 */
  margin: 0 auto;
  box-sizing: border-box;
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .welcome-section {
    min-width: 500px;
    max-width: 900px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .welcome-section {
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    padding: 40px 16px !important;
  }
}

.assistant-identity {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
  color: #000000;
  font-size: 20px;
  position: relative; /* 确保定位正确 */
}

.assistant-icon {
  font-size: 24px;
  position: relative; /* 确保图标定位正确 */
  flex-shrink: 0; /* 防止图标被压缩 */
}

.assistant-name {
  font-weight: 500;
  font-size: 20px;
}

.welcome-message {
  font-size: 16px;
  line-height: 1.6;
  color: var(--el-text-color-primary, #303133);
  text-align: center;
  margin-bottom: 32px;
  max-width: 600px;
}

.suggested-prompts {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  max-width: 500px;
}

.prompt-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--el-bg-color, #ffffff);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

.prompt-item:hover {
  background: var(--el-fill-color-light, #f5f7fa);
  border-color: var(--el-color-primary, #409eff);
  transform: translateX(4px);
}

.prompt-bullet {
  color: var(--el-color-primary, #409eff);
  font-weight: bold;
}

.prompt-text {
  color: var(--el-text-color-regular, #606266);
  font-size: 14px;
}

.input-section {
  width: 100%;
  max-width: 1200px; /* 与问答区域同宽 */
  min-width: 600px; /* 最小宽度，确保在小屏幕上也有良好显示 */
  margin: 0 auto 20px;
  position: sticky;
  bottom: 0;
  background: var(--el-bg-color-page, #f5f7fa);
  padding: 16px 20px; /* 添加左右内边距，与问答区域保持一致 */
  z-index: 10;
  box-sizing: border-box;
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .input-section {
    min-width: 500px;
    max-width: 900px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .input-section {
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    width: 100%;
    padding: 16px;
  }
}

.input-wrapper {
  position: relative;
  background: var(--el-bg-color, #ffffff);
  border-radius: 16px;
  box-shadow: 0 2px 12px var(--el-box-shadow-light, rgba(0, 0, 0, 0.08));
  padding: 16px;
  transition: box-shadow 0.3s;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  margin-bottom: 8px;
}

.input-wrapper:focus-within {
  box-shadow: 0 4px 20px var(--el-box-shadow-base, rgba(0, 0, 0, 0.12));
  border-color: var(--el-color-primary, #409eff);
}

.input-controls-float {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: var(--el-bg-color, #ffffff);
  border-radius: 12px;
  box-shadow: 0 1px 4px var(--el-box-shadow-light, rgba(0, 0, 0, 0.06));
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

.input-left-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.control-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  user-select: none;
}

.control-item:hover {
  background-color: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-color-primary, #409eff);
}

.control-item .arrow {
  font-size: 12px;
  margin-left: 2px;
}

.control-item.kb-selected,
.control-item.mode-selected {
  background-color: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}

.kb-control-item {
  cursor: pointer;
}

.kb-input-readonly {
  margin-left: 8px;
  width: 120px;
  pointer-events: none;
}

.kb-input-readonly :deep(.el-input__inner) {
  cursor: pointer;
  background-color: transparent;
  border: none;
  padding: 0;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  pointer-events: none;
}

.kb-input-readonly :deep(.el-input__inner):focus {
  border: none;
  box-shadow: none;
}

.control-item.kb-selected .kb-input-readonly :deep(.el-input__inner) {
  color: var(--el-color-primary, #409eff);
}

/* 知识库下拉菜单样式 */
.kb-selector-dropdown {
  position: relative;
}

.kb-dropdown-menu {
  max-height: 400px;
  overflow-y: auto;
}

/* 使用 popper-class 来设置下拉菜单宽度，与@控制区域同宽 */
:deep(.kb-dropdown-popper) {
  width: var(--input-section-width, 300px) !important;
  min-width: 280px !important;
}

:deep(.kb-dropdown-popper .el-dropdown-menu) {
  width: 100%;
}

.kb-dropdown-item {
  padding: 8px 16px;
}

.kb-item-content {
  width: 100%;
}

.kb-item-content :deep(.el-radio) {
  width: 100%;
  margin: 0;
}

.kb-item-content :deep(.el-radio__label) {
  width: 100%;
  padding-left: 8px;
}

.kb-item-info {
  flex: 1;
  width: 100%;
}

.portal-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  line-height: 1.5;
  resize: none;
}

.portal-input :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 0;
  font-size: 16px;
  line-height: 1.5;
  resize: none;
}

.portal-input :deep(.el-textarea__inner):focus {
  border: none;
  box-shadow: none;
}

.input-right-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.send-button {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.attachments-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding: 8px;
  background: var(--el-bg-color, #ffffff);
  border-radius: 8px;
  box-shadow: 0 1px 4px var(--el-box-shadow-light, rgba(0, 0, 0, 0.06));
}

.attachment-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 4px;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  max-width: 100%;
}

.attachment-preview-image {
  max-width: 200px !important;
  max-height: 200px !important;
  width: auto !important;
  height: auto !important;
  border-radius: 4px;
  cursor: pointer;
}

.attachment-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.attachment-name {
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.attachment-size {
  font-size: 11px;
  color: var(--el-text-color-placeholder, #909399);
}

.features-section {
  display: flex;
  justify-content: center;
  gap: 40px;
  flex-wrap: wrap;
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: transform 0.2s;
  padding: 12px;
  border-radius: 12px;
}

.feature-item:hover {
  transform: translateY(-4px);
  background-color: var(--el-fill-color-lighter, rgba(255, 255, 255, 0.6));
}

.feature-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--el-bg-color, #ffffff);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px var(--el-box-shadow-light, rgba(0, 0, 0, 0.1));
  font-size: 24px;
  color: var(--el-color-primary, #409eff);
  transition: all 0.3s;
}

.feature-item:hover .feature-icon {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
  box-shadow: 0 4px 12px var(--el-box-shadow-base, rgba(64, 158, 255, 0.2));
}

.feature-label {
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  font-weight: 500;
  transition: color 0.3s;
}

.feature-item:hover .feature-label {
  color: var(--el-color-primary, #409eff);
}

.chat-history-section {
  width: 100%;
  max-width: 1200px; /* 与输入区域和欢迎区域同宽 */
  min-width: 600px; /* 最小宽度，确保在小屏幕上也有良好显示 */
  margin: 0 auto;
  flex: 1;
  overflow-y: auto;
  padding: 20px 20px !important; /* 覆盖父容器的padding设置 */
  box-sizing: border-box;
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .chat-history-section {
    min-width: 500px;
    max-width: 900px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .chat-history-section {
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    padding: 20px 16px !important;
  }
}

/* 底部免责声明 */
.portal-footer {
  text-align: center;
  padding: 12px 20px;
  background: var(--el-bg-color, #ffffff);
  border-top: 1px solid var(--el-border-color-lighter, #e4e7ed);
  position: sticky;
  bottom: 0;
  z-index: 10;
}

.footer-text {
  font-size: 12px;
  color: var(--el-text-color-placeholder, #909399);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .logo {
    font-size: 36px;
  }

  .input-section {
    max-width: 100%;
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    margin-bottom: 60px;
    padding: 16px;
  }

  .input-wrapper {
    padding: 10px 12px;
  }

  .control-item {
    padding: 4px 8px;
    font-size: 12px;
  }

  .control-item span {
    display: none;
  }

  .features-section {
    gap: 20px;
  }

  .feature-icon {
    width: 48px;
    height: 48px;
    font-size: 20px;
  }

  .feature-label {
    font-size: 12px;
  }
}
</style>

