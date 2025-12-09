<template>
  <teleport to="body">
    <transition name="slide-fade">
      <div v-if="visible" class="help-chat-container" @click.self="handleClose">
        <div class="help-chat-window" @click.stop>
          <!-- 头部 -->
          <div class="chat-header">
            <div class="header-pattern"></div>
            <div class="header-content">
              <div class="header-top">
                <div class="header-top-left">
                  <el-icon class="chat-icon"><ChatLineRound /></el-icon>
                  <span class="header-title">聊天</span>
                </div>
                <div class="header-top-right">
                  <el-icon 
                    class="header-action-btn" 
                    title="开启新会话" 
                    @click="handleNewConversation"
                  >
                    <Refresh />
                  </el-icon>
                  <el-icon class="header-close-btn" @click="handleClose"><Close /></el-icon>
                </div>
              </div>
              <div class="header-main">
                <h3 class="header-main-title">告诉我您需要什么帮助</h3>
              </div>
            </div>
          </div>

          <!-- 对话区域 -->
          <div class="chat-body" ref="chatHistoryRef">
            <div class="chat-history-content">
              <div
                v-for="(message, index) in chatHistory"
                :key="index"
                :class="['message-item', message.type]"
              >
                <!-- 助手消息：头像在左侧 -->
                <template v-if="message.type === 'assistant'">
                  <div class="message-avatar">
                    <el-icon><Service /></el-icon>
                  </div>
                  <div class="message-content">
                    <div 
                      v-if="message.isLoading"
                      class="message-text loading"
                    >
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <span>AI正在思考中...</span>
                    </div>
                    <div 
                      v-else
                      class="message-text" 
                      v-html="renderMarkdown(message.content)"
                      :key="`content-${index}-${message.content.length}`"
                    ></div>
                  </div>
                </template>
                
                <!-- 用户消息：头像在右侧 -->
                <template v-else>
                  <div class="message-content">
                    <div 
                      v-if="message.isLoading"
                      class="message-text loading"
                    >
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <span>AI正在思考中...</span>
                    </div>
                    <div 
                      v-else
                      class="message-text" 
                      v-html="renderMarkdown(message.content)"
                      :key="`content-${index}-${message.content.length}`"
                    ></div>
                  </div>
                  <div class="message-avatar">
                    <el-icon><User /></el-icon>
                  </div>
                </template>
              </div>
            </div>
          </div>

          <!-- 输入区域 -->
          <div class="chat-input-area">
            <el-input
              v-model="question"
              type="textarea"
              :rows="2"
              placeholder="输入你的信息…"
              @keydown.ctrl.enter="handleSend"
              @keydown.enter.exact.prevent="handleSend"
              :disabled="sending"
              class="chat-input"
            />
            <div class="input-toolbar">
              <div class="input-icons">
                <el-icon class="toolbar-icon" title="表情"><ChatLineRound /></el-icon>
                <el-icon class="toolbar-icon" title="附件"><Link /></el-icon>
                <el-icon class="toolbar-icon" title="上传"><UploadFilled /></el-icon>
              </div>
            </div>
          </div>

          <!-- 关闭按钮 -->
          <div class="close-button" @click="handleClose">
            <el-icon><Close /></el-icon>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Service, Loading, ChatLineRound, Link, UploadFilled, Close, Refresh } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { chat, chatStream } from '@/api/chat'
import { knowledgeBaseQA, knowledgeBaseQAStream } from '@/api/knowledgeBaseQA'
import { getAvailableQAModels, getAvailableQAModelsForRAG } from '@/api/model'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  knowledgeBaseId: {
    type: Number,
    default: null
  },
  modelId: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const question = ref('')
const sending = ref(false)
const chatHistory = ref([])
const chatHistoryRef = ref(null)
const conversationId = ref(null)
const defaultModelId = ref(null)

// 初始化欢迎消息
const initWelcomeMessage = () => {
  if (chatHistory.value.length === 0) {
    chatHistory.value = [{
      type: 'assistant',
      content: '请问有什么可以帮您？',
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      isLoading: false
    }]
  }
}

// 监听 modelValue 变化
watch(() => props.modelValue, (newVal) => {
  visible.value = newVal
  if (newVal) {
    // 对话框打开时，初始化欢迎消息
    initWelcomeMessage()
    // 加载默认模型
    loadDefaultModel()
  }
})

// 监听 modelId 变化
watch(() => props.modelId, (newVal) => {
  if (newVal) {
    defaultModelId.value = newVal
  } else {
    // 如果配置的模型ID被清空，重新加载默认模型
    loadDefaultModel()
  }
})

// 监听 visible 变化
watch(visible, (newVal) => {
  emit('update:modelValue', newVal)
})

// 加载默认模型
const loadDefaultModel = async () => {
  try {
    // 如果传入了配置的模型ID，直接使用
    if (props.modelId) {
      defaultModelId.value = props.modelId
      console.log('使用配置的模型ID:', defaultModelId.value)
      return
    }
    
    // 如果绑定了知识库，使用RAG模型；否则使用普通问答模型
    const api = props.knowledgeBaseId ? getAvailableQAModelsForRAG : getAvailableQAModels
    const response = await api()
    console.log('模型列表响应:', response)
    
    // request拦截器已经提取了response.data，所以response可能是数组或对象
    const data = Array.isArray(response) ? response : (response?.data || [])
    
    if (data && data.length > 0) {
      // 查找默认模型，如果没有则使用第一个
      const defaultModel = data.find(m => m.isDefault) || data[0]
      defaultModelId.value = defaultModel.id
      console.log('已加载模型:', defaultModel.name, 'ID:', defaultModelId.value)
    } else {
      console.warn('没有可用的问答模型')
      ElMessage.warning('没有可用的问答模型，请先在"大模型管理"页面配置模型')
    }
  } catch (error) {
    console.error('加载模型失败:', error)
    const errorMessage = error.response?.data?.error || error.response?.data?.message || error.message || '未知错误'
    ElMessage.error(`加载模型失败: ${errorMessage}`)
  }
}

// 渲染 Markdown
const renderMarkdown = (text) => {
  if (!text) return ''
  try {
    return marked.parse(text)
  } catch (e) {
    return text
  }
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatHistoryRef.value) {
      chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
    }
  })
}

// 发送消息
const handleSend = async () => {
  if (!question.value.trim() || sending.value) {
    return
  }

  if (!defaultModelId.value) {
    ElMessage.warning('模型未加载，请稍后再试')
    return
  }

  const userQuestion = question.value.trim()
  question.value = ''

  // 添加用户消息
  const userMessage = {
    type: 'user',
    content: userQuestion,
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    isLoading: false
  }
  chatHistory.value.push(userMessage)

  // 添加AI加载消息
  const aiMessage = {
    type: 'assistant',
    content: '',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    isLoading: true
  }
  chatHistory.value.push(aiMessage)

  scrollToBottom()

  sending.value = true

  try {
    // 获取用户ID
    const userInfoStr = localStorage.getItem('userInfo')
    let userId = null
    if (userInfoStr) {
      try {
        const userInfo = JSON.parse(userInfoStr)
        userId = userInfo.id
      } catch (e) {
        console.error('解析用户信息失败', e)
      }
    }

    // 构建历史记录（只包含问题和答案，不包含加载状态）
    const history = chatHistory.value
      .filter(msg => !msg.isLoading)
      .map(msg => ({
        role: msg.type === 'user' ? 'user' : 'assistant',
        content: msg.content
      }))

    // 使用流式API
    const lastMessageIndex = chatHistory.value.length - 1
    const aiMessageRef = chatHistory.value[lastMessageIndex]
    
    // 调用流式API：如果绑定了知识库，使用知识库问答流式API；否则使用普通聊天流式API
    if (props.knowledgeBaseId) {
      // 使用知识库问答流式API
      console.log('调用知识库问答流式API:', {
        kbId: props.knowledgeBaseId,
        question: userQuestion,
        conversationId: conversationId.value,
        userId,
        historyLength: history.slice(0, -1).length,
        modelId: defaultModelId.value
      })
      await handleStreamResponse(
        knowledgeBaseQAStream(
          props.knowledgeBaseId,
          userQuestion,
          conversationId.value,
          userId,
          history.slice(0, -1), // 不包含当前问题
          defaultModelId.value
        ),
        lastMessageIndex
      )
    } else {
      // 使用普通聊天流式API
      console.log('调用普通聊天流式API:', {
        question: userQuestion,
        conversationId: conversationId.value,
        userId,
        historyLength: history.slice(0, -1).length,
        modelId: defaultModelId.value
      })
      await handleStreamResponse(
        chatStream(
          userQuestion,
          conversationId.value,
          userId,
          history.slice(0, -1), // 不包含当前问题
          defaultModelId.value,
          false
        ),
        lastMessageIndex
      )
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    console.error('错误详情:', {
      message: error.message,
      response: error.response,
      responseData: error.response?.data,
      stack: error.stack
    })
    
    // 提取错误信息
    let errorMessage = '发送消息失败，请稍后再试'
    if (error.response) {
      // HTTP错误响应
      errorMessage = error.response.data?.error || 
                     error.response.data?.message || 
                     error.response.data?.msg ||
                     error.message ||
                     `请求失败 (${error.response.status})`
    } else if (error.message) {
      // 网络错误或其他错误
      errorMessage = error.message
    }
    
    ElMessage.error(errorMessage)
    
    // 移除加载中的消息
    const lastMessageIndex = chatHistory.value.length - 1
    if (lastMessageIndex >= 0 && chatHistory.value[lastMessageIndex].isLoading) {
      chatHistory.value.pop()
    }
  } finally {
    sending.value = false
  }
}

// 处理流式响应
const handleStreamResponse = async (responsePromise, messageIndex) => {
  try {
    const response = await responsePromise
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let fullAnswer = ''

    while (true) {
      const { done, value } = await reader.read()
      
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      
      // 处理SSE格式：每行以data:开头，空行分隔
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // 保留最后一行（可能不完整）

      for (const line of lines) {
        const trimmedLine = line.trim()
        
        // 跳过空行
        if (!trimmedLine) continue
        
        // 处理SSE数据行：data: {...} 或 data:{...}
        if (trimmedLine.startsWith('data:')) {
          const data = trimmedLine.slice(5).trim()
          
          // 跳过结束标记和空数据
          if (data === '[DONE]' || data === '') continue

          try {
            const json = JSON.parse(data)
            
            // 更新答案内容
            if (json.answer !== undefined && json.answer !== null) {
              fullAnswer = json.answer
              // 清除加载状态，显示实际内容
              if (chatHistory.value[messageIndex] && chatHistory.value[messageIndex].isLoading) {
                chatHistory.value[messageIndex].isLoading = false
              }
              if (chatHistory.value[messageIndex]) {
                chatHistory.value[messageIndex].content = fullAnswer
              }
              
              // 实时滚动
              await nextTick()
              scrollToBottom()
            }

            // 更新会话ID
            if (json.conversationId) {
              conversationId.value = json.conversationId
            }

            // 流式响应完成
            if (json.finished) {
              if (chatHistory.value[messageIndex]) {
                chatHistory.value[messageIndex].content = fullAnswer || json.answer || ''
                chatHistory.value[messageIndex].isLoading = false
              }
              if (json.conversationId) {
                conversationId.value = json.conversationId
              }
              scrollToBottom()
              return
            }
          } catch (e) {
            console.warn('解析流式数据失败', e, data)
          }
        }
      }
    }
  } catch (error) {
    console.error('流式响应失败:', error)
    throw error
  }
}

// 开启新会话
const handleNewConversation = () => {
  // 保留欢迎消息
  chatHistory.value = [{
    type: 'assistant',
    content: '请问有什么可以帮您？',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    isLoading: false
  }]
  conversationId.value = null
  question.value = ''
  sending.value = false
  ElMessage.success('已开启新会话')
  
  // 滚动到底部
  nextTick(() => {
    scrollToBottom()
  })
}

// 关闭对话框
const handleClose = () => {
  visible.value = false
  // 可以选择保留历史记录或清空
  // 这里选择保留，用户下次打开时可以看到之前的对话
  // 如果需要清空，可以取消下面的注释
  // chatHistory.value = []
  // conversationId.value = null
}
</script>

<style scoped>
/* 容器 - 固定在左侧 */
.help-chat-container {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 2000;
  display: flex;
  align-items: flex-end;
  justify-content: flex-start;
  padding: 20px;
  pointer-events: auto;
  background: rgba(0, 0, 0, 0.3);
}

/* 聊天窗口 */
.help-chat-window {
  width: 400px;
  max-width: calc(100vw - 40px);
  height: 600px;
  max-height: calc(100vh - 40px);
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  pointer-events: auto;
  position: relative;
}

/* 头部 */
.chat-header {
  position: relative;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  color: white;
  padding: 20px;
  overflow: hidden;
}

.header-pattern {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  opacity: 0.1;
  background-image: 
    repeating-linear-gradient(45deg, transparent, transparent 10px, rgba(255,255,255,0.1) 10px, rgba(255,255,255,0.1) 20px);
}

.header-content {
  position: relative;
  z-index: 1;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.header-top-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-top-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-icon {
  font-size: 20px;
}

.header-action-btn {
  font-size: 20px;
  cursor: pointer;
  transition: all 0.3s;
  padding: 4px;
  border-radius: 4px;
}

.header-action-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.1);
}

.header-close-btn {
  font-size: 20px;
  cursor: pointer;
  transition: all 0.3s;
  padding: 4px;
  border-radius: 4px;
}

.header-close-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.1);
}

.header-title {
  font-size: 16px;
  font-weight: 500;
}

.header-main-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.header-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  opacity: 0.9;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: #409eff;
  border-radius: 50%;
  display: inline-block;
}

/* 对话区域 */
.chat-body {
  flex: 1;
  overflow-y: auto;
  background: white;
  padding: 20px;
}

.chat-history-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 欢迎消息 */
.welcome-message {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.welcome-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #606266;
  font-size: 18px;
}

.welcome-content {
  flex: 1;
}

.welcome-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 6px;
}

.welcome-bubble {
  background: #409eff;
  color: white;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  display: inline-block;
  max-width: 80%;
}

/* 消息项 */
.message-item {
  display: flex;
  gap: 12px;
  animation: fadeIn 0.3s ease-in;
  width: 100%;
}

.message-item.user {
  justify-content: flex-end;
}

.message-item.assistant {
  justify-content: flex-start;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}

.message-item.user .message-avatar {
  background: #409eff;
  color: white;
}

.message-item.assistant .message-avatar {
  background: #409eff;
  color: white;
}

.message-content {
  flex: 0 1 auto;
  min-width: 0;
  max-width: calc(100% - 48px);
  display: flex;
  flex-direction: column;
}

.message-item.user .message-content {
  align-items: flex-end;
}

.message-item.assistant .message-content {
  align-items: flex-start;
}

.message-text {
  background: white;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  word-wrap: break-word;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border: 1px solid #e4e7ed;
  display: inline-block;
}

.message-item.user .message-text {
  background: #409eff;
  color: white;
  border-color: #409eff;
}

.message-item.assistant .message-text {
  background: white;
  color: #303133;
}

.message-text.loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
}

.message-text :deep(p) {
  margin: 0.5em 0;
}

.message-text :deep(p:first-child) {
  margin-top: 0;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(code) {
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
}

.message-item.user .message-text :deep(code) {
  background: rgba(255, 255, 255, 0.2);
}

.message-text :deep(pre) {
  background: rgba(0, 0, 0, 0.05);
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 0.5em 0;
}

.message-item.user .message-text :deep(pre) {
  background: rgba(255, 255, 255, 0.2);
}

.message-text :deep(pre code) {
  background: transparent;
  padding: 0;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.message-text :deep(blockquote) {
  border-left: 3px solid #409eff;
  padding-left: 12px;
  margin: 0.5em 0;
  color: #606266;
}

/* 输入区域 */
.chat-input-area {
  padding: 16px;
  background: white;
  border-top: 1px solid #e4e7ed;
}

.chat-input :deep(.el-textarea__inner) {
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  resize: none;
  font-size: 14px;
}

.chat-input :deep(.el-textarea__inner):focus {
  border-color: #409eff;
}

.input-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.input-icons {
  display: flex;
  gap: 12px;
}

.toolbar-icon {
  font-size: 18px;
  color: #909399;
  cursor: pointer;
  transition: color 0.3s;
}

.toolbar-icon:hover {
  color: #409eff;
}

/* 关闭按钮 */
.close-button {
  position: absolute;
  bottom: -60px;
  left: 50%;
  transform: translateX(-50%);
  width: 56px;
  height: 56px;
  background: #409eff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
  transition: all 0.3s ease;
  color: white;
  font-size: 24px;
}

.close-button:hover {
  transform: translateX(-50%) scale(1.1);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.6);
}

.close-button:active {
  transform: translateX(-50%) scale(0.95);
}

/* 动画 */
.slide-fade-enter-active {
  transition: all 0.3s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.3s ease-in;
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
</style>

