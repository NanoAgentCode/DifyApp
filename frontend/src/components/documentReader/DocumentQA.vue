<template>
  <div class="document-qa">
    <!-- 收起按钮（仅在展开时显示） -->
    <div v-if="isFocused" class="collapse-button" @click="handleCollapse">
      <el-icon><ArrowUp /></el-icon>
    </div>
    <div class="qa-messages" ref="messagesContainerRef" v-show="isFocused">
      <div v-if="messages.length === 0 && isFocused" class="empty-messages">
        <el-icon class="empty-icon"><ChatLineRound /></el-icon>
        <p>开始与文档对话</p>
      </div>
      <div v-else-if="messages.length > 0 && isFocused" class="messages-list">
        <div
          v-for="(message, index) in messages"
          :key="index"
          :class="['message-item', message.role === 'user' ? 'user-message' : 'assistant-message']"
        >
          <div class="message-content">
            <div v-if="message.role === 'user'" class="message-text">
              {{ message.content }}
            </div>
            <div 
              v-else-if="message.isLoading"
              class="message-text loading"
            >
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>AI正在思考中...</span>
            </div>
            <div v-else-if="message.content && message.content.trim()" class="message-text" v-html="renderedMessage(message.content)"></div>
            <div v-else class="message-text loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>等待响应...</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <div class="qa-input">
      <!-- 选中文本提示 -->
      <div v-if="selectedText && !question.includes(selectedText)" class="selected-text-hint">
        <div class="hint-content">
          <el-icon><DocumentCopy /></el-icon>
          <span class="hint-text">已选中文本：{{ selectedTextPreview }}</span>
          <el-button type="text" size="small" @click="insertSelectedText">插入</el-button>
          <el-button type="text" size="small" @click="clearSelectedText">清除</el-button>
        </div>
      </div>
      <el-input
        ref="inputRef"
        v-model="question"
        type="textarea"
        :rows="2"
        :placeholder="selectedText ? '请输入您的问题，或点击插入使用选中内容...' : '请输入您的问题...'"
        @keydown.ctrl.enter="handleSend"
        @keydown.enter.exact.prevent="handleSend"
        @focus="handleFocus"
        @blur="handleBlur"
        :disabled="sending"
      />
      <div class="input-actions">
        <span class="input-tips">按 Ctrl + Enter 或 Enter 发送</span>
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
</template>

<script setup>
import { ref, watch, nextTick, onMounted, computed, defineExpose } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatLineRound, Promotion, Loading, ArrowUp, DocumentCopy } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { documentQA, documentQAStream } from '@/api/documentReader'
import { createConversation } from '@/api/chat'
import { renderMarkdown } from '@/composables/useMarkdown'
import { processSSEStream } from '@/composables/useSSEStream'
import { extractContent, updateConversationId, updateMessage } from '@/composables/useResponseHandler'

const router = useRouter()

const props = defineProps({
  docId: {
    type: Number,
    required: true
  },
  modelId: {
    type: Number,
    default: null
  },
  useStream: {
    type: Boolean,
    default: true
  },
  selectedText: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['focus', 'blur', 'textUsed'])

const messages = ref([])
const question = ref('')
const sending = ref(false)
const messagesContainerRef = ref(null)
const inputRef = ref(null)
const conversationId = ref(null)
const isFocused = ref(false)

// 获取用户ID
const getUserId = () => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      const userInfo = JSON.parse(userInfoStr)
      // 兼容不同的字段名：id 或 userId
      return userInfo.id || userInfo.userId || null
    } catch (e) {
      console.error('解析用户信息失败:', e)
      return null
    }
  }
  return null
}

// 处理焦点事件
const handleFocus = () => {
  if (!isFocused.value) {
    isFocused.value = true
    emit('focus')
  }
}

// 处理失焦事件
const handleBlur = () => {
  // 延迟处理，以便点击按钮时不会立即失焦
  setTimeout(() => {
    // 如果正在发送消息，保持焦点状态，不收起
    if (sending.value) {
      return
    }
    
    // 检查是否真的失焦（没有活动元素或活动元素不在问答区域内）
    const activeElement = document.activeElement
    const qaElement = inputRef.value?.$el || inputRef.value
    if (!qaElement?.contains(activeElement)) {
      isFocused.value = false
      emit('blur')
    }
  }, 200)
}

// 选中文本预览（截取前50个字符）
const selectedTextPreview = computed(() => {
  if (!props.selectedText) return ''
  return props.selectedText.length > 50 
    ? props.selectedText.substring(0, 50) + '...' 
    : props.selectedText
})

// 过滤空行，保留有内容的行
const filterEmptyLines = (text) => {
  if (!text) return ''
  // 按行分割，过滤掉空行和只包含空白字符的行，然后重新组合
  return text
    .split('\n')
    .filter(line => line.trim().length > 0)
    .join('\n')
}

// 插入文本的通用方法
const insertTextWithPrefix = (textToInsert, prefix) => {
  // 优先使用传入的参数，如果没有则使用props中的selectedText
  const rawText = textToInsert || props.selectedText
  if (!rawText) return
  
  // 过滤空行
  const text = filterEmptyLines(rawText)
  if (!text?.trim()) return
  
  // 构建问题文本
  if (question.value.trim()) {
    question.value = `${question.value}\n\n【选中内容】\n${text}\n\n${prefix}以上内容。`
  } else {
    question.value = `${prefix}以下内容：\n\n${text}`
  }
  
  // 聚焦到输入框
  nextTick(() => {
    const textarea = inputRef.value?.$el?.querySelector('textarea')
    if (textarea) {
      textarea.focus()
    }
  })
  
  emit('textUsed')
}

// 插入选中文本（解读）
const insertSelectedText = (textToInsert = null) => {
  insertTextWithPrefix(textToInsert, '请解读')
}

// 插入翻译文本
const insertTranslateText = (textToInsert = null) => {
  insertTextWithPrefix(textToInsert, '请翻译')
}

// 清除选中文本
const clearSelectedText = () => {
  emit('textUsed')
}

// 处理收起按钮点击
const handleCollapse = () => {
  // 失焦输入框
  if (inputRef.value?.$el) {
    const textarea = inputRef.value.$el.querySelector('textarea')
    if (textarea) {
      textarea.blur()
    }
  }
  // 直接设置焦点状态为false并触发blur事件
  isFocused.value = false
  emit('blur')
}

// 渲染消息内容
const renderedMessage = (content) => {
  if (!content) return ''
  return renderMarkdown(content)
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainerRef.value) {
      messagesContainerRef.value.scrollTop = messagesContainerRef.value.scrollHeight
    }
  })
}

// 发送消息
const handleSend = async () => {
  if (!question.value.trim() || sending.value) return
  
  const userQuestion = question.value.trim()
  question.value = ''
  
  // 清除选中文本（已使用）
  if (props.selectedText) {
    emit('textUsed')
  }
  
  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: userQuestion
  })
  
  sending.value = true
  
  try {
    const userId = getUserId()
    if (!userId) {
      // 检查是否有token，如果有token但无法获取用户信息，可能是token过期
      const token = localStorage.getItem('token')
      if (token) {
        ElMessage.error('无法获取用户信息，请重新登录')
        // 清除可能过期的token和userInfo
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        // 跳转到登录页
        setTimeout(() => {
          router.push('/login')
        }, 1500)
      } else {
        ElMessage.error('请先登录')
        setTimeout(() => {
          router.push('/login')
        }, 1500)
      }
      return
    }
    
    // 滚动到底部（强制滚动，因为这是新消息）
    await nextTick()
    scrollToBottom()
    
    // 添加AI回复占位（采用与知识库问答相同的方式）
    const aiMessageIndex = messages.value.length
    messages.value.push({
      role: 'assistant',
      content: '',
      isLoading: true
    })
    
    // 如果没有会话ID，创建新会话（文档问答类型设为3）
    if (!conversationId.value) {
      try {
        const docInfo = localStorage.getItem(`docInfo_${props.docId}`)
        let docTitle = '文档问答'
        if (docInfo) {
          try {
            const info = JSON.parse(docInfo)
            docTitle = `文档问答 - ${info.originalFileName || info.fileName || '未命名文档'}`
          } catch (e) {
            // 忽略解析错误
          }
        }
        const newConversation = await createConversation(docTitle, null, props.docId, 3)
        if (newConversation && newConversation.id) {
          conversationId.value = newConversation.id
        }
      } catch (error) {
        console.error('创建会话失败:', error)
        // 继续执行，不阻止问答
      }
    }
    
    // 构建历史对话
    const history = messages.value
      .slice(0, -1) // 排除当前占位的AI消息
      .filter(msg => msg.role === 'assistant' && !msg.isLoading)
      .slice(-5)
      .map(msg => ({
        role: msg.role,
        content: msg.content
      }))
    
    if (props.useStream) {
      // 流式响应
      await handleStreamResponse(userQuestion, userId, history, aiMessageIndex)
    } else {
      // 非流式响应
      await handleNormalResponse(userQuestion, userId, history, aiMessageIndex)
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    ElMessage.error('发送失败：' + (error.message || '未知错误'))
    // 移除用户消息和失败的AI消息
    if (messages.value.length > 0 && messages.value[messages.value.length - 1].role === 'user') {
      messages.value.pop()
    }
    if (messages.value.length > 0 && messages.value[messages.value.length - 1].role === 'assistant' && messages.value[messages.value.length - 1].isLoading) {
      messages.value[messages.value.length - 1].content = '抱歉，生成答案时发生错误，请重试。'
      messages.value[messages.value.length - 1].isLoading = false
    }
  } finally {
    sending.value = false
    scrollToBottom()
    
    // 发送完成后，重新聚焦到输入框，保持问答区域展开
    nextTick(() => {
      if (inputRef.value?.$el) {
        const textarea = inputRef.value.$el.querySelector('textarea')
        if (textarea) {
          textarea.focus()
        }
      }
    })
  }
}

// 处理流式响应
const handleStreamResponse = async (userQuestion, userId, history, aiMessageIndex) => {
  try {
    const response = await documentQAStream(
      props.docId,
      userQuestion,
      conversationId.value,
      userId,
      history,
      props.modelId
    )
    
    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let lastReceivedContent = '' // 记录最后收到的内容，用于流结束时检查
    
    console.log('开始读取SSE流，消息索引:', aiMessageIndex)
    
    while (true) {
      const { done, value } = await reader.read()
      
      if (done) {
        console.log('SSE流结束，剩余buffer长度:', buffer.length, 'buffer内容:', buffer.substring(0, 200))
        // 流结束时，处理剩余的buffer
        if (buffer.trim()) {
          const lines = buffer.split('\n')
          for (const line of lines) {
            const trimmedLine = line.trim()
            if (trimmedLine && (trimmedLine.startsWith('data: ') || trimmedLine.startsWith('{'))) {
              let data = trimmedLine
              if (data.startsWith('data: ')) {
                data = data.slice(6).trim()
              }
              if (data && data !== '[DONE]') {
                try {
                  const parsed = JSON.parse(data)
                  console.log('流结束时解析到数据:', {
                    hasContent: !!parsed.content,
                    contentLength: parsed.content ? parsed.content.length : 0,
                    finished: parsed.finished,
                    rawData: data.substring(0, 100)
                  })
                  const hasContent = parsed.content !== undefined || parsed.answer !== undefined
                  const content = parsed.content !== undefined ? parsed.content : (parsed.answer !== undefined ? parsed.answer : null)
                  if (hasContent && messages.value[aiMessageIndex]) {
                    messages.value[aiMessageIndex].content = content !== null ? content : ''
                    lastReceivedContent = content !== null ? content : '' // 更新最后收到的内容
                    messages.value[aiMessageIndex].isLoading = false
                    console.log('流结束时更新内容，长度:', content ? content.length : 0, '内容预览:', content ? content.substring(0, 50) : '空')
                    if (parsed.sources) {
                      messages.value[aiMessageIndex].sources = parsed.sources
                    }
                  } else if (messages.value[aiMessageIndex]) {
                    // 即使没有content字段，也要清除加载状态
                    messages.value[aiMessageIndex].isLoading = false
                    if (parsed.sources) {
                      messages.value[aiMessageIndex].sources = parsed.sources
                    }
                  }
                } catch (e) {
                  console.warn('解析SSE数据失败:', e, '原始数据:', data.substring(0, 100))
                }
              }
            }
          }
        }
        // 流结束，确保最终内容已更新和状态已清除
        if (messages.value[aiMessageIndex]) {
          messages.value[aiMessageIndex].isLoading = false
          // 如果内容为空，检查是否在流处理过程中已经收到过内容
          // 如果从未收到过内容，才显示提示
          const currentContent = messages.value[aiMessageIndex].content
          const hasReceivedContent = (currentContent && currentContent.trim() !== '') || (lastReceivedContent && lastReceivedContent.trim() !== '')
          
          if (!hasReceivedContent) {
            console.warn('流结束但未收到任何内容，消息索引:', aiMessageIndex, '当前内容:', currentContent, '最后收到:', lastReceivedContent)
            messages.value[aiMessageIndex].content = '响应已完成，但未收到内容。'
          } else {
            // 如果流结束时buffer中没有内容，但之前收到过内容，使用最后收到的内容
            if (!currentContent || currentContent.trim() === '') {
              if (lastReceivedContent && lastReceivedContent.trim() !== '') {
                console.log('流结束，使用最后收到的内容，长度:', lastReceivedContent.length)
                messages.value[aiMessageIndex].content = lastReceivedContent
              }
            }
            console.log('流结束，最终内容已存在，长度:', messages.value[aiMessageIndex].content ? messages.value[aiMessageIndex].content.length : 0)
          }
        }
        break
      }
      
      const chunk = decoder.decode(value, { stream: true })
      buffer += chunk
      
      // 按行分割，保留最后一行（可能不完整）
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      
        for (const line of lines) {
          const trimmedLine = line.trim()
          
          if (!trimmedLine) continue
          
          // 处理SSE格式：data: {json} 或 data:{json}（兼容两种格式）
          let data = null
          if (trimmedLine.startsWith('data:')) {
            // 兼容 data: 和 data: 两种格式（知识库问答使用 data:，文档问答可能使用 data: ）
            data = trimmedLine.slice(5).trim() // 去掉 'data:' 前缀（5个字符），然后trim
          } else if (trimmedLine.startsWith('{')) {
            // 直接是JSON，没有data:前缀（Spring的ServerSentEvent可能不包含前缀）
            data = trimmedLine
          }
          
          if (!data || data === '[DONE]' || data === '') {
            continue // 跳过空数据或结束标记
          }
          
          try {
            const parsed = JSON.parse(data)
            console.log('解析SSE数据成功:', {
              hasContent: !!(parsed.content || parsed.answer),
              contentLength: parsed.content ? parsed.content.length : (parsed.answer ? parsed.answer.length : 0),
              finished: parsed.finished,
              conversationId: parsed.conversationId
            })
            
            if (parsed.error) {
              // 处理错误信息
              console.error('SSE错误:', parsed.error)
              ElMessage.error(parsed.error)
              if (messages.value[aiMessageIndex]) {
                messages.value[aiMessageIndex].content = '抱歉，生成答案时发生错误：' + parsed.error
                messages.value[aiMessageIndex].isLoading = false
              }
              return
            }
            
            // 检查是否有内容字段（后端发送的是content字段，但兼容answer字段）
            // 注意：即使content是空字符串，也要更新（因为可能是累积内容）
            const hasContent = parsed.content !== undefined || parsed.answer !== undefined
            // 优先使用content字段（文档问答后端发送的是content），如果没有则使用answer（兼容知识库问答）
            const content = parsed.content !== undefined ? parsed.content : (parsed.answer !== undefined ? parsed.answer : null)
            
            if (hasContent && content !== null) {
              // 直接更新消息数组中的内容（采用与知识库问答相同的方式）
              if (!messages.value[aiMessageIndex]) {
                console.warn('消息对象不存在，索引:', aiMessageIndex)
                continue
              }
              // 收到内容时，立即清除加载状态（即使内容为空字符串）
              if (messages.value[aiMessageIndex].isLoading) {
                messages.value[aiMessageIndex].isLoading = false
              }
              // 更新内容（即使是空字符串也要更新，因为可能是累积内容的一部分）
              messages.value[aiMessageIndex].content = content
              lastReceivedContent = content // 记录最后收到的内容
              console.log('更新消息内容，索引:', aiMessageIndex, '内容长度:', content.length, '预览:', content.substring(0, 50))
              await nextTick()
              scrollToBottom()
            }
            
            if (parsed.conversationId !== undefined && parsed.conversationId !== null) {
              conversationId.value = parsed.conversationId
            }
            
            // 如果finished为true，明确标记流结束
            if (parsed.finished === true) {
              console.log('收到finished=true，结束流')
              // 流结束，确保最终内容已更新
              if (messages.value[aiMessageIndex]) {
                // 使用最后收到的content或answer，或者使用当前消息的内容
                const finalContent = parsed.content !== undefined ? parsed.content : 
                                   (parsed.answer !== undefined ? parsed.answer : 
                                   (messages.value[aiMessageIndex].content || ''))
                if (finalContent) {
                  messages.value[aiMessageIndex].content = finalContent
                }
                messages.value[aiMessageIndex].isLoading = false
                if (parsed.sources) {
                  messages.value[aiMessageIndex].sources = parsed.sources
                }
                console.log('流结束，最终内容长度:', finalContent.length)
              }
              return
            }
          } catch (e) {
            console.warn('解析SSE数据失败:', e, '原始数据:', data.substring(0, 200))
            // 忽略解析错误，继续处理下一行
          }
        }
    }
  } catch (error) {
    console.error('流式响应处理失败:', error)
    throw error
  }
}

// 处理非流式响应
const handleNormalResponse = async (userQuestion, userId, history, aiMessageIndex) => {
  const response = await documentQA(
    props.docId,
    userQuestion,
    conversationId.value,
    userId,
    history,
    props.modelId
  )
  
  const content = extractContent(response, ['answer', 'content'], '抱歉，未能生成答案。')
  
  updateMessage({
    messages: messages.value,
    messageIndex: aiMessageIndex,
    content,
    isLoading: false,
    metadata: response?.sources ? { sources: response.sources } : {}
  })
  
  updateConversationId(response, conversationId)
}

// 监听消息变化，自动滚动
watch(messages, () => {
  scrollToBottom()
}, { deep: true })

// 暴露方法供父组件调用
const sendQuestion = (questionText) => {
  if (!questionText || !questionText.trim()) return
  
  // 设置问题并发送
  question.value = questionText.trim()
  handleSend()
}

// 设置输入框文本但不发送
const setQuestionText = (text) => {
  console.log('setQuestionText被调用，文本:', text)
  if (!text) {
    console.log('文本为空，返回')
    return
  }
  
  // 设置输入框内容
  question.value = text
  console.log('输入框内容已设置，question.value:', question.value)
  
  // 聚焦到输入框
  nextTick(() => {
    console.log('nextTick执行，inputRef.value:', inputRef.value)
    if (inputRef.value?.$el) {
      const textarea = inputRef.value.$el.querySelector('textarea')
      console.log('找到textarea:', textarea)
      if (textarea) {
        textarea.focus()
        // 将光标移到末尾
        textarea.setSelectionRange(textarea.value.length, textarea.value.length)
        console.log('输入框已聚焦，光标已移到末尾')
      }
    }
  })
}

defineExpose({
  sendQuestion,
  setQuestionText,
  insertSelectedText,
  insertTranslateText
})

onMounted(() => {
  scrollToBottom()
})
</script>

<style scoped>
.document-qa {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color-page, #f5f7fa);
  position: relative;
  min-height: fit-content;
}

.qa-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color, #ffffff);
}

.empty-messages {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: #909399;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-item {
  display: flex;
  margin-bottom: 12px;
}

.user-message {
  justify-content: flex-end;
}

.assistant-message {
  justify-content: flex-start;
  padding-left: 16px;
}

.message-content {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: 8px;
}

.user-message .message-content {
  background: #409eff;
  color: white;
}

.assistant-message .message-content {
  background: #f0f2f5;
  color: #303133;
}

.message-text {
  line-height: 1.6;
  word-wrap: break-word;
}

.message-text.loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
}

.message-text.loading .is-loading {
  animation: rotating 2s linear infinite;
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.message-text :deep(p) {
  margin: 0 0 8px 0;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.typing-indicator {
  display: inline-block;
  animation: blink 1s infinite;
  margin-left: 4px;
}

@keyframes blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}

.qa-input {
  padding: 12px 16px;
  margin: 0;
  border-top: 1px solid #e4e7ed;
  flex-shrink: 0;
  position: sticky;
  bottom: 0;
  background: var(--el-bg-color, #ffffff);
  z-index: 10;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.input-tips {
  font-size: 12px;
  color: #909399;
}

.collapse-button {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  z-index: 20;
  transition: all 0.2s ease;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.collapse-button:hover {
  background: var(--el-bg-color-page, #f5f7fa);
  border-color: var(--el-color-primary, #409eff);
  color: var(--el-color-primary, #409eff);
}

.collapse-button:active {
  transform: scale(0.95);
}

.selected-text-hint {
  margin-bottom: 8px;
  padding: 8px 12px;
  background: var(--el-color-primary-light-9, #ecf5ff);
  border: 1px solid var(--el-color-primary-light-7, #b3d8ff);
  border-radius: 4px;
  font-size: 12px;
}

.hint-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hint-content .el-icon {
  color: var(--el-color-primary, #409eff);
  font-size: 16px;
}

.hint-text {
  flex: 1;
  color: var(--el-text-color-regular, #606266);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hint-content .el-button {
  padding: 0 8px;
  font-size: 12px;
}
</style>

