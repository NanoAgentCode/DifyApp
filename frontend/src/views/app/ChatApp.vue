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

      <div class="chat-input">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="3"
          placeholder="请输入消息..."
          @keydown.ctrl.enter="handleSend"
        />
        <div class="input-actions">
          <el-button @click="handleClear">清空</el-button>
          <el-button type="primary" @click="handleSend" :loading="loading">
            发送 (Ctrl+Enter)
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, triggerRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAppDetail, chatApp, chatAppStream } from '@/api/aiApp'
import { renderMarkdown } from '@/composables/useMarkdown'
import AppIcon from '@/components/AppIcon.vue'

const route = useRoute()
const router = useRouter()
const appInfo = ref(null)
const inputText = ref('')
const messages = ref([])
const loading = ref(false)
const messagesRef = ref(null)
const conversationId = ref(null)
const updateKey = ref(0) // 用于强制更新
const isUserScrolling = ref(false) // 标记用户是否在手动滚动
const autoScrollEnabled = ref(true) // 是否启用自动滚动

const fetchAppInfo = async () => {
  try {
    const res = await getAppDetail(route.params.id)
    appInfo.value = res
  } catch (error) {
    ElMessage.error('获取应用信息失败')
  }
}

const handleSend = async () => {
  if (!inputText.value.trim()) {
    ElMessage.warning('请输入消息')
    return
  }

  const userMessage = {
    role: 'user',
    content: inputText.value,
    time: new Date()
  }
  messages.value.push(userMessage)
  const question = inputText.value
  inputText.value = ''
  loading.value = true

  await nextTick()
  autoScrollEnabled.value = true
  scrollToBottom()

  try {
    const requestData = {
      query: question,
      userId: 'user_' + Date.now(),
      conversationId: conversationId.value,
      stream: appInfo.value?.streamEnabled || false
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
  if (res.conversationId) {
    conversationId.value = res.conversationId
  }
  messages.value.push({
    role: 'assistant',
    content: res.answer || '无响应',
    time: new Date()
  })
}

const handleStreamChat = async (requestData) => {
  // 流式响应处理 - 使用完全替换数组的方式确保Vue响应式更新
  const messageIndex = messages.value.length
  let assistantContent = ''
  let updateTimer = null
  let lastUpdateTime = 0
  const UPDATE_INTERVAL = 100 // 每100ms更新一次DOM，减少更新频率
  
  // 创建初始消息
  messages.value = [...messages.value, {
    role: 'assistant',
    content: '',
    time: new Date()
  }]
  
  // 确保初始滚动到底部
  await nextTick()
  scrollToBottomSmooth()
  
  // 更新消息的辅助函数，带防抖和节流
  const updateMessage = (content) => {
    assistantContent = content
    const now = Date.now()
    
    // 立即更新数据，但限制DOM更新频率
    const newMessages = [...messages.value]
    newMessages[messageIndex] = {
      ...newMessages[messageIndex],
      content: assistantContent
    }
    messages.value = newMessages
    
    // 清除之前的定时器
    if (updateTimer) {
      clearTimeout(updateTimer)
    }
    
    // 使用节流，每100ms最多更新一次DOM和滚动
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
      // 如果距离上次更新时间太短，延迟执行
      updateTimer = setTimeout(() => {
        const elapsed = Date.now() - lastUpdateTime
        if (elapsed >= UPDATE_INTERVAL) {
          lastUpdateTime = Date.now()
          requestAnimationFrame(() => {
            nextTick(() => {
              if (autoScrollEnabled.value) {
                scrollToBottomSmooth()
              }
            })
          })
        }
      }, UPDATE_INTERVAL - (now - lastUpdateTime))
    }
  }

  try {
    const response = await fetch(`/api/ai-apps/${route.params.id}/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestData)
    })

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`)
    }
    
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let pendingData = ''

    const processData = (dataStr) => {
      if (!dataStr?.trim()) return

      const cleanedData = dataStr.trim().replace(/\n/g, ' ').replace(/\r/g, '')
      if (cleanedData === '[DONE]') return

      try {
        const json = JSON.parse(cleanedData)

        // 累积AI回答内容
        if (json.answer !== undefined && json.answer !== null) {
          assistantContent = assistantContent + json.answer
          updateMessage(assistantContent)
        }

        // 更新对话ID
        if (json.conversation_id || json.conversationId) {
          conversationId.value = json.conversation_id || json.conversationId
        }

        // 处理事件类型
        if (json.event) {
          if (json.event === 'error') {
            updateMessage('发生错误: ' + (json.answer || '未知错误'))
          } else if (json.event === 'message_end' || json.event === 'workflow_finished') {
            if (updateTimer) clearTimeout(updateTimer)
            updateMessage(assistantContent)
          }
        }
      } catch (e) {
        // JSON解析失败，静默处理
      }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        // 处理剩余数据
        if (pendingData) {
          if (buffer.trim()) {
            pendingData += buffer.trim()
            buffer = ''
          }
          processData(pendingData)
          pendingData = ''
        } else if (buffer.trim()) {
          const trimmed = buffer.trim()
          if (trimmed.startsWith('data: ')) {
            processData(trimmed.substring(6).trim())
          } else if (trimmed.startsWith('data:')) {
            processData(trimmed.substring(5).trim())
          } else if (trimmed && !trimmed.startsWith('event:') && !trimmed.startsWith('id:') && !trimmed.startsWith(':')) {
            processData(trimmed)
          }
        }
        break
      }

      buffer += decoder.decode(value, { stream: true })
      
      // 按行处理
      let newlineIndex
      while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
        const line = buffer.substring(0, newlineIndex)
        buffer = buffer.substring(newlineIndex + 1)
        const trimmed = line.trim()
        
        // 空行表示事件结束
        if (!trimmed) {
          if (pendingData) {
            processData(pendingData)
            pendingData = ''
          }
          continue
        }

        // 解析data行
        let dataContent = null
        if (trimmed.startsWith('data: ')) {
          dataContent = trimmed.substring(6).trim()
        } else if (trimmed.startsWith('data:')) {
          dataContent = trimmed.substring(5).trim()
        } else if (line.match(/^data:\s*(.*)$/)) {
          dataContent = line.match(/^data:\s*(.*)$/)[1]
        }
        
        if (dataContent) {
          // 合并pending数据
          if (pendingData) {
            dataContent = pendingData + dataContent
            pendingData = ''
          }
          
          // 检查JSON完整性
          if (isCompleteJSON(dataContent)) {
            processData(dataContent)
          } else {
            pendingData = dataContent
          }
        } else if (!trimmed.startsWith('event:') && !trimmed.startsWith('id:') && !trimmed.startsWith(':')) {
          // 可能是直接的JSON数据
          processData(trimmed)
        }
      }
      
      // 合并buffer到pending数据
      if (pendingData && buffer.trim() && 
          !buffer.trim().startsWith('data:') && 
          !buffer.trim().startsWith('event:') && 
          !buffer.trim().startsWith('id:')) {
        pendingData += buffer.trim()
        buffer = ''
      }
    }
  } catch (error) {
    updateMessage('抱歉，流式响应处理出错: ' + (error.message || '未知错误'))
    if (updateTimer) clearTimeout(updateTimer)
    throw error
  } finally {
    // 确保最终更新
    if (updateTimer) {
      clearTimeout(updateTimer)
    }
    // 最终更新一次，确保所有内容都显示
    const newMessages = [...messages.value]
    newMessages[messageIndex] = {
      ...newMessages[messageIndex],
      content: assistantContent
    }
    messages.value = newMessages
    await nextTick()
    // 流式输出完成后，重新启用自动滚动并滚动到底部
    autoScrollEnabled.value = true
    scrollToBottom()
  }
}

const handleClear = () => {
  messages.value = []
  conversationId.value = null
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
  height: 0; /* 配合 flex: 1 使用 */
  scroll-behavior: auto; /* 禁用平滑滚动，避免跳动 */
  /* 优化滚动性能 */
  will-change: scroll-position;
  transform: translateZ(0); /* 启用硬件加速 */
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
  background: #409eff;
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

.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
  gap: 10px;
}
</style>

