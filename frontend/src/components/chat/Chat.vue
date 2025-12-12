<template>
  <div class="chat">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div class="header-actions">
            <ModelSelector
              v-model="selectedModelId"
              :models="availableModels"
              :disabled="sending"
              style="margin-right: 10px"
            />
            <el-checkbox v-model="useStream" size="small">流式响应</el-checkbox>
            <el-button type="primary" @click="handleClearHistory">
              <el-icon><Delete /></el-icon>
              清空历史
            </el-button>
          </div>
        </div>
      </template>

      <div class="chat-container">
        <!-- 对话历史 -->
        <MessageList
          :messages="chatHistory"
          :sending="sending"
          :on-regenerate="handleRegenerate"
          ref="messageListRef"
        />

        <!-- 输入区域 -->
        <div class="input-area">
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            placeholder="请输入您的问题..."
            @keydown.ctrl.enter="handleSend"
            @keydown.enter.exact.prevent="handleSend"
            :disabled="sending"
          />
          <div class="input-actions">
            <div class="input-tips">
              <span class="tips-text">按 Ctrl + Enter 或 Enter 发送</span>
            </div>
            <div class="input-buttons">
              <el-button
                v-if="showNewConversationButton"
                @click="handleNewConversation"
                :disabled="sending"
              >
                <el-icon><Plus /></el-icon>
                开启新对话
              </el-button>
              <div v-if="showBrowserSearch" style="display: flex; align-items: center; gap: 8px; margin-right: 8px;">
                <span style="font-size: 14px; color: #606266;">联网搜索</span>
                <el-switch
                  v-model="enableBrowserSearch"
                  :disabled="sending"
                />
              </div>
              <el-button
                type="primary"
                :disabled="!question.trim() || sending"
                @click="handleSend"
                :loading="sending"
              >
                <el-icon><Promotion /></el-icon>
                发送
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, Promotion } from '@element-plus/icons-vue'
import { chat, chatStream, getConversationMessages } from '@/api/chat'
import { getAvailableQAModels } from '@/api/model'
import { renderMarkdown } from '@/composables/useMarkdown'
import MessageList from '@/components/chat/MessageList.vue'
import ModelSelector from '@/components/chat/ModelSelector.vue'

const props = defineProps({
  title: {
    type: String,
    default: '智能问答'
  },
  showNewConversationButton: {
    type: Boolean,
    default: false
  },
  showBrowserSearch: {
    type: Boolean,
    default: true
  },
  enableBrowserSearchDefault: {
    type: Boolean,
    default: false
  }
})

const question = ref('')
const sending = ref(false)
const chatHistory = ref([])
const messageListRef = ref(null)
const conversationId = ref(null)
const useStream = ref(true)
const enableBrowserSearch = ref(props.enableBrowserSearchDefault)
const currentStreamingMessage = ref(null)
const availableModels = ref([])
const selectedModelId = ref(null)

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

// 检查是否在底部（允许一定的误差范围）
const isNearBottom = () => {
  if (!messageListRef.value?.$el) return true
  const element = messageListRef.value.$el
  const threshold = 100
  return element.scrollHeight - element.scrollTop - element.clientHeight < threshold
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

const handleSend = async () => {
  if (!question.value.trim() || sending.value) {
    return
  }

  const userQuestion = question.value.trim()
  
  // 添加用户消息
  chatHistory.value.push({
    type: 'user',
    content: userQuestion,
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  })

  // 清空输入框
  question.value = ''
  
  // 滚动到底部（强制滚动，因为这是新消息）
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
  
  currentStreamingMessage.value = aiMessageIndex
  sending.value = true

  // 构建历史对话
  const history = chatHistory.value
    .slice(0, -1) // 排除当前占位的AI消息
    .filter(msg => !msg.isLoading)
    .map(msg => ({
      role: msg.type === 'user' ? 'user' : 'assistant',
      content: msg.content
    }))

  const userInfo = getUserInfo()
  const userId = userInfo ? userInfo.userId : null

  try {
    const currentConversationId = conversationId.value
    console.log('发送消息，当前 conversationId:', currentConversationId)
    
    if (useStream.value) {
      // 流式响应
      await handleStreamResponse(userQuestion, currentConversationId, userId, history, aiMessageIndex, selectedModelId.value, enableBrowserSearch.value)
    } else {
      // 非流式响应
      await handleNormalResponse(userQuestion, currentConversationId, userId, history, aiMessageIndex, selectedModelId.value, enableBrowserSearch.value)
    }
  } catch (error) {
    console.error('发送消息失败', error)
    ElMessage.error('发送消息失败：' + (error.message || '未知错误'))
    // 移除失败的AI消息
    if (chatHistory.value[aiMessageIndex]) {
      chatHistory.value[aiMessageIndex].content = '抱歉，生成答案时发生错误，请重试。'
      chatHistory.value[aiMessageIndex].isLoading = false
    }
  } finally {
    sending.value = false
    currentStreamingMessage.value = null
    await nextTick()
    scrollToBottom(false) // 流式响应完成后，只在用户还在底部时滚动
  }
}

// 处理流式响应
const handleStreamResponse = async (question, requestConversationId, userId, history, aiMessageIndex, modelId, enableBrowserSearch) => {
  let reader = null
  let response = null
  
  // 使用 requestAnimationFrame 优化更新，保持流畅度
  let rafId = null
  let pendingContent = null
  
  const scheduleUpdate = (content) => {
    pendingContent = content
    
    // 取消之前的更新请求（防抖：只保留最后一次更新）
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
    }
    
    // 使用 requestAnimationFrame 在下一次重绘前更新
    // 这样可以与浏览器的渲染周期同步，获得最佳性能
    rafId = requestAnimationFrame(() => {
      if (chatHistory.value[aiMessageIndex] && pendingContent !== null) {
        chatHistory.value[aiMessageIndex].content = pendingContent
        if (chatHistory.value[aiMessageIndex].isLoading) {
          chatHistory.value[aiMessageIndex].isLoading = false
        }
        
        // 触发滚动（使用 requestAnimationFrame 优化）
        if (messageListRef.value?.scrollToBottom) {
          messageListRef.value.scrollToBottom(false)
        }
      }
      rafId = null
    })
  }
  
  try {
    response = await chatStream(question, requestConversationId, userId, history, modelId, enableBrowserSearch)
    
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
            // 取消待处理的更新
            if (rafId !== null) {
              cancelAnimationFrame(rafId)
              rafId = null
            }
            if (hasReceivedData && chatHistory.value[aiMessageIndex]?.content) {
              // 确保最终内容被更新
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
        
        // 处理SSE格式：每行以data:开头，空行分隔
        const lines = buffer.split('\n')
        buffer = lines.pop() || '' // 保留最后一行（可能不完整）

        for (const line of lines) {
          const trimmedLine = line.trim()
          if (!trimmedLine) continue
          
          // 处理SSE数据行：data: {...} 或 data:{...}
          if (trimmedLine.startsWith('data:')) {
            const data = trimmedLine.slice(5).trim()
            if (data === '[DONE]' || data === '') continue

            try {
              const json = JSON.parse(data)
              
              // 更新会话ID（如果响应中包含 conversationId，立即更新，不等待 finished）
              if (json.conversationId) {
                conversationId.value = json.conversationId.toString()
                console.log('流式响应中更新 conversationId:', conversationId.value)
              }
              
              // 更新答案内容（使用 requestAnimationFrame 优化）
              if (json.answer !== undefined && json.answer !== null) {
                if (!chatHistory.value[aiMessageIndex]) {
                  console.warn('消息对象不存在，索引:', aiMessageIndex)
                  continue
                }
                scheduleUpdate(json.answer)
              }

              // 流式响应完成
              if (json.finished) {
                // 取消待处理的更新，立即更新最终内容
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
                  console.log('流式响应完成，最终 conversationId:', conversationId.value)
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
      // 确保最终内容被更新
      if (rafId !== null) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
      // 立即更新最终内容
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

    // 处理剩余的buffer
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

    // 如果连接正常结束但没有收到完成标记，确保清除加载状态
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

// 处理非流式响应
const handleNormalResponse = async (question, requestConversationId, userId, history, aiMessageIndex, modelId, enableBrowserSearch) => {
  try {
    const response = await chat(question, requestConversationId, userId, history, modelId, enableBrowserSearch)
    if (chatHistory.value[aiMessageIndex]) {
      chatHistory.value[aiMessageIndex].content = response.answer || '抱歉，未能生成答案。'
      chatHistory.value[aiMessageIndex].isLoading = false
    }
    
    // 更新会话ID
    if (response.conversationId) {
      conversationId.value = response.conversationId.toString()
      console.log('非流式响应完成，更新 conversationId:', conversationId.value)
    }
    
    await nextTick()
    scrollToBottom(false)
  } catch (error) {
    console.error('非流式响应处理失败', error)
    throw error
  }
}

// 开启新对话
const handleNewConversation = () => {
  chatHistory.value = []
  conversationId.value = null
  question.value = ''
  sending.value = false
  currentStreamingMessage.value = null
  ElMessage.success('已开启新对话')
  scrollToBottom(true)
}

// 清空历史
const handleClearHistory = () => {
  if (chatHistory.value.length === 0) {
    ElMessage.info('当前没有对话历史')
    return
  }
  
  chatHistory.value = []
  conversationId.value = null
  ElMessage.success('已清空对话历史')
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

  // 找到对应的用户消息（应该是前一条消息）
  if (messageIndex === 0 || chatHistory.value[messageIndex - 1].type !== 'user') {
    ElMessage.warning('无法找到对应的用户消息')
    return
  }

  const userMessage = chatHistory.value[messageIndex - 1]
  const userQuestion = userMessage.content

  // 移除当前的助手消息
  chatHistory.value.splice(messageIndex, 1)

  // 滚动到底部
  await nextTick()
  scrollToBottom(true)

  // 添加新的AI回复占位
  const aiMessageIndex = chatHistory.value.length
  chatHistory.value.push({
    type: 'assistant',
    content: '',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    isLoading: true
  })

  currentStreamingMessage.value = aiMessageIndex
  sending.value = true

  // 构建历史对话（排除刚移除的助手消息和当前占位的AI消息）
  const history = chatHistory.value
    .slice(0, -1) // 排除当前占位的AI消息
    .filter(msg => !msg.isLoading)
    .map(msg => ({
      role: msg.type === 'user' ? 'user' : 'assistant',
      content: msg.content
    }))

  const userInfo = getUserInfo()
  const userId = userInfo ? userInfo.userId : null

  try {
    const currentConversationId = conversationId.value
    console.log('重新生成响应，当前 conversationId:', currentConversationId)

    if (useStream.value) {
      // 流式响应
      await handleStreamResponse(userQuestion, currentConversationId, userId, history, aiMessageIndex, selectedModelId.value, enableBrowserSearch.value)
    } else {
      // 非流式响应
      await handleNormalResponse(userQuestion, currentConversationId, userId, history, aiMessageIndex, selectedModelId.value, enableBrowserSearch.value)
    }
  } catch (error) {
    console.error('重新生成响应失败', error)
    ElMessage.error('重新生成响应失败：' + (error.message || '未知错误'))
    // 移除失败的AI消息
    if (chatHistory.value[aiMessageIndex]) {
      chatHistory.value[aiMessageIndex].content = '抱歉，重新生成答案时发生错误，请重试。'
      chatHistory.value[aiMessageIndex].isLoading = false
    }
  } finally {
    sending.value = false
    currentStreamingMessage.value = null
    await nextTick()
    scrollToBottom(false)
  }
}

// 加载历史对话记录
const loadConversationHistory = async (convId) => {
  try {
    const messages = await getConversationMessages(convId)
    if (messages && messages.length > 0) {
      chatHistory.value = messages.map(msg => ({
        type: msg.role === 'user' ? 'user' : 'assistant',
        content: msg.content || '',
        time: msg.createTime ? new Date(msg.createTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
        isLoading: false
      }))
      
      conversationId.value = convId.toString()
      await nextTick()
      scrollToBottom(true)
      ElMessage.success('已加载历史对话记录')
    }
  } catch (error) {
    console.error('加载历史对话失败', error)
    ElMessage.warning('加载历史对话失败')
  }
}

// 加载可用模型列表
const loadAvailableModels = async () => {
  try {
    const models = await getAvailableQAModels()
    availableModels.value = models || []
    
    if (availableModels.value.length > 0) {
      const defaultModel = availableModels.value.find(m => m.isDefault)
      if (defaultModel) {
        selectedModelId.value = defaultModel.id
      } else {
        selectedModelId.value = availableModels.value[0].id
      }
    } else {
      console.warn('没有可用的问答模型')
      ElMessage.warning('当前没有可用的问答模型，请联系管理员配置')
    }
  } catch (error) {
    console.error('加载可用模型列表失败', error)
    const errorMessage = error.response?.data?.error || error.response?.data?.message || error.message || '未知错误'
    ElMessage.error(`加载模型列表失败: ${errorMessage}`)
    availableModels.value = []
  }
}

onMounted(async () => {
  loadAvailableModels()
  // 检查是否有继续对话的标记
  const continueConvId = localStorage.getItem('continueConversationId')
  if (continueConvId) {
    localStorage.removeItem('continueConversationId')
    await loadConversationHistory(continueConvId)
  } else {
    scrollToBottom(true)
  }
})
</script>

<style scoped>
.chat {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: 18px 20px;
}

:deep(.el-card__body) {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 15px;
}

.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.input-area {
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  background: white;
  flex-shrink: 0;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.input-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
}

.input-tips {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.tips-text {
  font-size: 12px;
}
</style>
