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
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
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
    console.log('开始流式请求:', requestData)
    const response = await fetch(`/api/ai-apps/${route.params.id}/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestData)
    })

    if (!response.ok) {
      const errorText = await response.text()
      console.error('流式请求失败:', response.status, errorText)
      throw new Error(`请求失败: ${response.status}`)
    }

    console.log('收到响应，Content-Type:', response.headers.get('Content-Type'))
    
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let rawDataLog = [] // 用于调试：记录原始数据
    let pendingData = '' // 用于处理跨chunk的JSON数据

    const processData = (dataStr) => {
      if (!dataStr || !dataStr.trim()) {
        console.log('⚠️ 空数据，跳过')
        return
      }

      // 清理数据：移除可能的换行符和空白字符
      const cleanedData = dataStr.trim().replace(/\n/g, ' ').replace(/\r/g, '')
      console.log('🔍 处理数据 (长度:', cleanedData.length, '):', cleanedData.substring(0, 200))

      if (cleanedData === '[DONE]') {
        console.log('✅ 收到结束标记')
        return
      }

      try {
        const json = JSON.parse(cleanedData)
        console.log('✅ 解析JSON成功:', {
          event: json.event,
          answerLength: json.answer ? json.answer.length : 0,
          answerPreview: json.answer ? json.answer.substring(0, 50) : null,
          conversationId: json.conversation_id || json.conversationId,
          finished: json.finished
        })

        // Dify API 返回的格式：{event, answer, conversation_id, message_id, ...}
        // answer 字段在流式响应中是增量内容，需要追加
        if (json.answer !== undefined && json.answer !== null) {
          // 累积内容（包括空字符串）
          assistantContent = assistantContent + json.answer
          updateMessage(assistantContent)
          console.log('📝 更新消息内容，当前长度:', assistantContent.length, '本次增量:', json.answer.length)
        }

        // 更新对话ID（支持两种格式：conversation_id 和 conversationId）
        if (json.conversation_id) {
          conversationId.value = json.conversation_id
          console.log('💬 更新对话ID:', json.conversation_id)
        } else if (json.conversationId) {
          conversationId.value = json.conversationId
          console.log('💬 更新对话ID:', json.conversationId)
        }

        // 检查是否完成
        if (json.finished) {
          console.log('✅ 流式响应已完成 (finished=true)')
        }

        // 处理事件类型
        if (json.event) {
          console.log('📌 事件类型:', json.event)
          if (json.event === 'error') {
            const errorMsg = '发生错误: ' + (json.answer || '未知错误')
            updateMessage(errorMsg)
            console.error('❌ 错误事件:', errorMsg)
          } else if (json.event === 'message_end' || json.event === 'workflow_finished') {
            if (updateTimer) {
              clearTimeout(updateTimer)
            }
            updateMessage(assistantContent)
            console.log('✅ 流式响应已完成（通过事件类型判断）')
          }
        }
      } catch (e) {
        console.error('❌ 解析JSON失败:', e)
        console.error('原始数据 (前500字符):', cleanedData.substring(0, 500))
        console.error('原始数据 (完整):', cleanedData)
        // 尝试直接显示原始数据的前100个字符
        if (cleanedData.trim().length > 0) {
          updateMessage('解析错误，原始数据: ' + cleanedData.substring(0, 100))
        }
      }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        console.log('✅ 流式响应完成')
        // 处理剩余的buffer和pendingData
        if (pendingData) {
          if (buffer.trim()) {
            // 合并buffer到pendingData
            pendingData += buffer.trim()
            buffer = ''
          }
          console.log('处理最后剩余的pending数据:', pendingData.substring(0, 200))
          // 即使JSON不完整，也尝试处理
          processData(pendingData)
          pendingData = ''
        } else if (buffer.trim()) {
          console.log('处理最后剩余的buffer:', buffer)
          // 尝试处理剩余的buffer
          const trimmed = buffer.trim()
          if (trimmed.startsWith('data: ')) {
            processData(trimmed.substring(6).trim())
          } else if (trimmed.startsWith('data:')) {
            processData(trimmed.substring(5).trim())
          } else if (trimmed && !trimmed.startsWith('event:') && !trimmed.startsWith('id:') && !trimmed.startsWith(':')) {
            // 可能是直接的JSON数据
            processData(trimmed)
          }
        }
        break
      }

      const chunk = decoder.decode(value, { stream: true })
      rawDataLog.push(chunk)
      if (rawDataLog.length > 10) rawDataLog.shift() // 只保留最近10个chunk用于调试
      
      buffer += chunk
      
      // 按行处理，但要处理可能跨行的JSON数据
      let newlineIndex
      while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
        const line = buffer.substring(0, newlineIndex)
        buffer = buffer.substring(newlineIndex + 1)
        
        // 保留原始格式以便检查是否以data:开头
        const originalLine = line
        const trimmed = line.trim()
        
        // 空行表示事件结束
        if (!trimmed) {
          // 如果pendingData有内容，尝试处理它
          if (pendingData) {
            console.log('📥 遇到空行，处理pending数据:', pendingData.substring(0, 150))
            processData(pendingData)
            pendingData = ''
          }
          continue
        }

        console.log('📥 收到SSE行:', trimmed.substring(0, 150))

        // 检查是否是data行
        let dataContent = null
        let isDataLine = false
        
        if (trimmed.startsWith('data: ')) {
          // 标准SSE格式：data: {...}
          dataContent = trimmed.substring(6).trim()
          isDataLine = true
        } else if (trimmed.startsWith('data:')) {
          // SSE格式：data:{...} (没有空格)
          dataContent = trimmed.substring(5).trim()
          isDataLine = true
        } else if (originalLine.startsWith('data: ') || originalLine.startsWith('data:')) {
          // 即使trim后不是以data开头，但原始行是，说明可能有空格问题
          const match = originalLine.match(/^data:\s*(.*)$/)
          if (match) {
            dataContent = match[1]
            isDataLine = true
          }
        }
        
        if (isDataLine && dataContent) {
          // 检查JSON是否完整（简单的括号匹配检查）
          const openBraces = (dataContent.match(/{/g) || []).length
          const closeBraces = (dataContent.match(/}/g) || []).length
          const openBrackets = (dataContent.match(/\[/g) || []).length
          const closeBrackets = (dataContent.match(/\]/g) || []).length
          
          // 如果有pending数据，先合并
          if (pendingData) {
            dataContent = pendingData + dataContent
            pendingData = ''
          }
          
          // 检查是否完整
          if (openBraces === closeBraces && openBrackets === closeBrackets && dataContent.trim().endsWith('}')) {
            // JSON看起来完整，处理它
            processData(dataContent)
          } else {
            // JSON可能不完整，保存到pendingData
            console.log('⚠️ JSON可能不完整，等待更多数据. 当前:', dataContent.substring(0, 100))
            pendingData = dataContent
          }
        } else if (trimmed.startsWith('event: ')) {
          // event: message
          console.log('📌 SSE事件类型:', trimmed.substring(7).trim())
        } else if (trimmed.startsWith('event:')) {
          // event:message (没有空格)
          console.log('📌 SSE事件类型:', trimmed.substring(6).trim())
        } else if (trimmed.startsWith('id: ')) {
          // id: 123
          console.log('🆔 SSE ID:', trimmed.substring(4).trim())
        } else if (trimmed.startsWith('id:')) {
          // id:123 (没有空格)
          console.log('🆔 SSE ID:', trimmed.substring(3).trim())
        } else if (trimmed.startsWith(':')) {
          // 注释行，忽略
          continue
        } else {
          // 可能是直接的JSON数据（某些实现可能不包含"data: "前缀）
          console.log('⚠️ 收到未标记的数据行，尝试作为JSON解析:', trimmed.substring(0, 150))
          processData(trimmed)
        }
      }
      
      // 如果buffer中还有pendingData的延续，需要合并
      if (pendingData && buffer.trim()) {
        // 检查buffer是否包含JSON的延续部分
        const trimmedBuffer = buffer.trim()
        if (!trimmedBuffer.startsWith('data:') && !trimmedBuffer.startsWith('event:') && !trimmedBuffer.startsWith('id:')) {
          // 可能是JSON的延续
          pendingData += trimmedBuffer
          buffer = ''
          console.log('📥 合并buffer到pending数据，当前长度:', pendingData.length)
        }
      }
    }

    // 调试：输出原始数据日志
    console.log('📊 原始数据日志（最后10个chunk）:', rawDataLog)
  } catch (error) {
    console.error('流式请求错误:', error)
    updateMessage('抱歉，流式响应处理出错: ' + (error.message || '未知错误'))
    if (updateTimer) {
      clearTimeout(updateTimer)
    }
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
  router.push('/admin/apps')
}

// 配置 marked 使用 highlight.js 进行代码高亮
marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (err) {
        console.error('代码高亮错误:', err)
      }
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true, // 支持 GitHub 风格的换行
  gfm: true // 启用 GitHub 风格的 Markdown
})

const formatMessage = (content) => {
  if (!content) return ''
  try {
    // 使用 marked 渲染 markdown
    return marked.parse(content)
  } catch (error) {
    console.error('Markdown 渲染错误:', error)
    // 如果渲染失败，回退到简单的换行处理
    return content.replace(/\n/g, '<br>')
  }
}

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

.message.user {
  justify-content: flex-end;
}

.message.assistant {
  justify-content: flex-start;
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
  background-color: rgba(27, 31, 35, 0.05);
  border-radius: 3px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
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

.message-text :deep(pre code) {
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

