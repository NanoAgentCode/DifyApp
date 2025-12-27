/**
 * Chat 功能 Composables
 * 提取 Chat 组件的公共逻辑，提升代码复用
 */
import { ref, reactive, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { chat } from '@/api/chat'
import { getAvailableQAModels } from '@/api/model'
import { getModelStyle } from '@/utils/modelColor'
import { renderMarkdown } from '@/composables/useMarkdown'

export function useChat(options = {}) {
  const {
    enableBrowserSearch = false, // 是否启用浏览器搜索
    enableLocation = false, // 是否启用地理位置
    enableTime = false, // 是否启用时间服务
    onMessageUpdate = null // 消息更新回调
  } = options

  // 状态管理
  const question = ref('')
  const chatHistory = ref([])
  const sending = ref(false)
  const useStream = ref(true)
  const selectedModelId = ref(null)
  const availableModels = ref([])
  const chatHistoryRef = ref(null)
  const enableBrowserSearchValue = ref(enableBrowserSearch)
  const enableLocationValue = ref(enableLocation)
  const enableTimeValue = ref(enableTime)

  // 流式响应相关
  let abortController = null
  let currentStreamIndex = -1

  /**
   * 加载可用模型列表
   */
  const loadModels = async () => {
    try {
      const models = await getAvailableQAModels()
      availableModels.value = models || []
      
      // 设置默认模型
      if (availableModels.value.length > 0 && !selectedModelId.value) {
        const defaultModel = availableModels.value.find(m => m.isDefault)
        selectedModelId.value = defaultModel?.id || availableModels.value[0].id
      }
    } catch (error) {
      console.error('加载模型列表失败:', error)
      ElMessage.error('加载模型列表失败')
    }
  }

  /**
   * 获取模型样式
   */
  const getModelStyleById = (modelId) => {
    return getModelStyle(modelId)
  }

  /**
   * 滚动到底部
   */
  const scrollToBottom = () => {
    nextTick(() => {
      if (chatHistoryRef.value) {
        chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
      }
    })
  }

  /**
   * 添加用户消息
   */
  const addUserMessage = (content) => {
    chatHistory.value.push({
      type: 'user',
      content: content.trim(),
      time: new Date().toLocaleString('zh-CN'),
      isLoading: false
    })
    scrollToBottom()
  }

  /**
   * 添加 AI 消息（加载中）
   */
  const addLoadingMessage = () => {
    currentStreamIndex = chatHistory.value.length
    chatHistory.value.push({
      type: 'assistant',
      content: '',
      time: new Date().toLocaleString('zh-CN'),
      isLoading: true
    })
    scrollToBottom()
  }

  /**
   * 更新 AI 消息内容
   */
  const updateAIMessage = (content, isLoading = false) => {
    if (currentStreamIndex >= 0 && currentStreamIndex < chatHistory.value.length) {
      chatHistory.value[currentStreamIndex].content = content
      chatHistory.value[currentStreamIndex].isLoading = isLoading
      scrollToBottom()
      
      // 触发回调
      if (onMessageUpdate) {
        onMessageUpdate(chatHistory.value[currentStreamIndex])
      }
    }
  }

  /**
   * 发送消息（非流式）
   */
  const sendMessage = async () => {
    if (!question.value.trim() || sending.value) return

    const userQuestion = question.value.trim()
    question.value = ''
    addUserMessage(userQuestion)
    addLoadingMessage()

    sending.value = true

    try {
      const response = await chat(
        userQuestion,
        null, // conversationId
        null, // userId
        null, // history
        selectedModelId.value,
        enableBrowserSearchValue.value
      )

      updateAIMessage(response.answer || '抱歉，没有收到回复', false)
    } catch (error) {
      const errorMsg = error?.response?.data?.error || error?.message || '发送消息失败'
      updateAIMessage(`错误: ${errorMsg}`, false)
      ElMessage.error(errorMsg)
    } finally {
      sending.value = false
      currentStreamIndex = -1
    }
  }

  /**
   * 发送消息（流式）
   */
  const sendStreamMessage = async () => {
    if (!question.value.trim() || sending.value) return

    const userQuestion = question.value.trim()
    question.value = ''
    addUserMessage(userQuestion)
    addLoadingMessage()

    sending.value = true
    abortController = new AbortController()

    try {
      // 创建带 AbortSignal 的 fetch 请求
      const token = localStorage.getItem('token')
      const headers = {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      }
      if (token) {
        headers['Authorization'] = `Bearer ${token}`
      }

      const response = await fetch('/api/chat/stream', {
        method: 'POST',
        headers,
        credentials: 'include',
        signal: abortController.signal,
        body: JSON.stringify({
          question: userQuestion,
          conversationId: null,
          userId: null,
          history: null,
          stream: true,
          modelId: selectedModelId.value,
          enableBrowserSearch: enableBrowserSearchValue.value
        })
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      // 处理流式响应
      const { processSSEStream } = await import('@/composables/useSSEStream')
      
      await processSSEStream(response, {
        cumulative: true,
        contentFields: ['content', 'text', 'message'],
        onData: (json, cumulativeContent) => {
          if (cumulativeContent) {
            updateAIMessage(cumulativeContent, true)
          }
        },
        onComplete: () => {
          const finalContent = chatHistory.value[currentStreamIndex]?.content
          updateAIMessage(finalContent || '抱歉，没有收到回复', false)
        }
      })
    } catch (error) {
      if (error.name === 'AbortError') {
        updateAIMessage('请求已取消', false)
      } else {
        const errorMsg = error?.message || '发送消息失败'
        updateAIMessage(`错误: ${errorMsg}`, false)
        ElMessage.error(errorMsg)
      }
    } finally {
      sending.value = false
      currentStreamIndex = -1
      abortController = null
    }
  }

  /**
   * 发送消息（根据 useStream 选择方式）
   */
  const handleSend = async () => {
    if (useStream.value) {
      await sendStreamMessage()
    } else {
      await sendMessage()
    }
  }

  /**
   * 取消当前请求
   */
  const cancelRequest = () => {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
  }

  /**
   * 清空历史
   */
  const clearHistory = () => {
    chatHistory.value = []
    cancelRequest()
  }

  /**
   * 新建对话
   */
  const newConversation = () => {
    clearHistory()
    question.value = ''
  }

  // 组件卸载时取消请求
  onUnmounted(() => {
    cancelRequest()
  })

  return {
    // 状态
    question,
    chatHistory,
    sending,
    useStream,
    selectedModelId,
    availableModels,
    chatHistoryRef,
    enableBrowserSearch: enableBrowserSearchValue,
    enableLocation: enableLocationValue,
    enableTime: enableTimeValue,
    
    // 方法
    loadModels,
    getModelStyleById,
    handleSend,
    clearHistory,
    newConversation,
    cancelRequest,
    renderMarkdown,
    scrollToBottom
  }
}

