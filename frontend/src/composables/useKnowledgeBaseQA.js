/**
 * Knowledge Base QA Composables
 * 提取知识库问答的公共逻辑，提升代码复用
 */
import { ref, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { knowledgeBaseQA, knowledgeBaseQAStream } from '@/api/knowledgeBaseQA'
import { getModelConfig, getAvailableQAModelsForRAG } from '@/api/model'
import { getVectorDatabaseList } from '@/api/vectorDatabase'
import { getConversationMessages, getConversation } from '@/api/chat'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { renderMarkdown, cleanupMarkdown } from '@/composables/useMarkdown'
import { logger } from '@/utils/logger'

export function useKnowledgeBaseQA(options = {}) {
  const {
    enableConversationHistory = false, // 是否启用对话历史功能（user版本启用）
    isAdmin = false // 是否是管理员（影响知识库列表加载）
  } = options

  const { handleError } = useErrorHandler()

  // 状态管理
  const knowledgeBases = ref([])
  const selectedKB = ref(null)
  const embeddingModels = ref([])
  const question = ref('')
  const sending = ref(false)
  const chatHistory = ref([])
  const chatHistoryRef = ref(null)
  const conversationId = ref(null)
  const useStream = ref(true)
  const currentStreamingMessage = ref(null)
  const kbSearchKeyword = ref('')
  const displayedCount = ref(50)
  const loadingMore = ref(false)
  const searchDebounceTimer = ref(null)
  const availableQAModels = ref([])
  const selectedModelId = ref(null)
  const vectorDatabases = ref([])
  const enabledVectorStoreTypes = ref([])

  // 计算属性（优化：缓存keyword，避免重复toLowerCase）
  const filteredKnowledgeBases = computed(() => {
    if (!kbSearchKeyword.value) {
      return knowledgeBases.value
    }
    const keyword = kbSearchKeyword.value.toLowerCase().trim()
    if (!keyword) {
      return knowledgeBases.value
    }
    // 优化：使用for循环替代filter，提前退出
    const result = []
    for (let i = 0; i < knowledgeBases.value.length; i++) {
      const kb = knowledgeBases.value[i]
      if (kb.name.toLowerCase().includes(keyword) ||
          (kb.description && kb.description.toLowerCase().includes(keyword))) {
        result.push(kb)
      }
    }
    return result
  })

  const displayedKnowledgeBases = computed(() => {
    return filteredKnowledgeBases.value.slice(0, displayedCount.value)
  })

  const hasMoreToLoad = computed(() => {
    return displayedCount.value < filteredKnowledgeBases.value.length
  })

  const remainingCount = computed(() => {
    return filteredKnowledgeBases.value.length - displayedCount.value
  })

  // 知识库搜索（防抖）
  const handleKbSearch = () => {
    // 重置显示数量
    displayedCount.value = 50
    
    // 清除之前的定时器
    if (searchDebounceTimer.value) {
      clearTimeout(searchDebounceTimer.value)
    }
    
    // 设置新的定时器（300ms 防抖）
    searchDebounceTimer.value = setTimeout(() => {
      // 搜索时自动展开所有结果
      if (kbSearchKeyword.value) {
        displayedCount.value = filteredKnowledgeBases.value.length
      } else {
        displayedCount.value = 50
      }
      searchDebounceTimer.value = null
    }, 300)
  }

  // 加载更多知识库
  const loadMore = () => {
    if (loadingMore.value || !hasMoreToLoad.value) return
    loadingMore.value = true
    setTimeout(() => {
      displayedCount.value = Math.min(
        displayedCount.value + 50,
        filteredKnowledgeBases.value.length
      )
      loadingMore.value = false
    }, 300)
  }

  // 选择知识库
  const selectKB = (kb) => {
    selectedKB.value = kb
    chatHistory.value = []
    conversationId.value = null
    scrollToBottom(true)
  }

  // 加载知识库列表
  const loadKnowledgeBases = async () => {
    try {
      const userInfoStr = localStorage.getItem('userInfo')
      let userId = null
      let userRole = null
      
      if (userInfoStr) {
        try {
          const userInfo = JSON.parse(userInfoStr)
          userId = userInfo.userId
          userRole = userInfo.role
        } catch (e) {
          logger.debug('解析用户信息失败', e)
        }
      }
      
      // 根据角色和isAdmin参数决定加载策略
      const params = {}
      if (!isAdmin && userRole !== 1) {
        // 普通用户只获取启用的知识库
        params.status = 1
      }
      // 管理员不传status参数，可以获取所有状态的知识库
      
      if (userId) {
        params.userId = userId
      }
      
      const res = await getKnowledgeBaseList(params)
      const data = Array.isArray(res) ? res : (res?.data || [])
      
      if (data && data.length > 0) {
        knowledgeBases.value = data.map(kb => ({
          id: kb.id,
          name: kb.name,
          description: kb.description || '',
          summary: kb.summary || '',
          documentCount: kb.documentCount || 0,
          status: kb.status === 1 ? 'active' : 'inactive',
          embeddingModelId: kb.embeddingModelId || null,
          vectorStoreType: kb.vectorStoreType || 'qdrant'
        }))
        
        displayedCount.value = 50
        
        if (knowledgeBases.value.length > 0) {
          selectKB(knowledgeBases.value[0])
        }
      } else {
             ElMessage.info('暂无可用知识库，请先创建知识库')
           }
         } catch (error) {
           handleError(error, '加载知识库列表失败')
         }
  }

  // 加载向量化模型列表（包括禁用的，用于检查状态）
  const loadEmbeddingModels = async () => {
    try {
      const res = await getModelConfig()
      // 加载所有模型（包括禁用的），以便检查状态
      embeddingModels.value = res?.embeddingModels || res?.embedding || []
         } catch (error) {
           handleError(error, '加载向量化模型列表失败', { showMessage: false, logError: true })
         }
  }

  // 获取向量化模型名称
  const getEmbeddingModelName = (modelId) => {
    if (modelId) {
      const model = embeddingModels.value.find(m => m.id === modelId)
      return model ? model.name : null
    } else {
      // 如果没有指定模型ID，返回默认模型名称
      const defaultModel = embeddingModels.value.find(m => m.isDefault)
      return defaultModel ? defaultModel.name : null
    }
  }

  // 检查向量化模型是否启用
  const isEmbeddingModelEnabled = (modelId) => {
    if (modelId) {
      const model = embeddingModels.value.find(m => m.id === modelId)
      return model ? model.enabled : false
    } else {
      // 如果没有指定模型ID，检查默认模型是否启用
      const defaultModel = embeddingModels.value.find(m => m.isDefault)
      return defaultModel ? defaultModel.enabled : false
    }
  }

  // 加载向量库配置列表
  const loadVectorDatabases = async () => {
    try {
      const res = await getVectorDatabaseList()
      const data = Array.isArray(res) ? res : (res?.data || res || [])
      vectorDatabases.value = data
      
      // 计算启用的向量库类型
      const enabledTypes = new Set()
      vectorDatabases.value.forEach(db => {
        if (db.enabled && db.type) {
          enabledTypes.add(db.type.toLowerCase())
        }
      })
      enabledVectorStoreTypes.value = Array.from(enabledTypes)
      
      // 如果加载失败或没有配置，默认允许所有类型（向后兼容）
      if (enabledVectorStoreTypes.value.length === 0) {
        enabledVectorStoreTypes.value = ['qdrant', 'faiss', 'milvus', 'chroma', 'weaviate', 'elasticsearch']
      }
    } catch (error) {
      logger.error('加载向量库配置列表失败', error)
      // 如果加载失败，默认允许所有类型（向后兼容）
      enabledVectorStoreTypes.value = ['qdrant', 'faiss', 'milvus', 'chroma', 'weaviate', 'elasticsearch']
    }
  }

  // 检查向量库类型是否启用
  const isVectorStoreTypeEnabled = (type) => {
    if (!type) return true // 如果没有指定类型，默认允许（向后兼容）
    return enabledVectorStoreTypes.value.includes(type.toLowerCase())
  }

  // 获取向量库类型名称
  const getVectorStoreTypeName = (type) => {
    if (!type) return 'Qdrant'
    const vdb = vectorDatabases.value.find(v => v.type === type || v.type?.toLowerCase() === type?.toLowerCase())
    if (vdb) return vdb.name
    // 如果没有找到配置，返回默认名称
    if (type === 'faiss') return 'FAISS'
    if (type === 'milvus') return 'Milvus'
    if (type === 'chroma') return 'Chroma'
    if (type === 'weaviate') return 'Weaviate'
    return 'Qdrant'
  }

  // 检查知识库是否启用
  const isKnowledgeBaseActive = (status) => {
    return status === 'active' || status === 1
  }

  // 获取知识库禁用提示
  const getKnowledgeBaseDisabledTip = (kb) => {
    if (!kb) return null
    
    const reasons = []
    
    if (!isKnowledgeBaseActive(kb.status)) {
      reasons.push('知识库已禁用')
    }
    
    if (!isEmbeddingModelEnabled(kb.embeddingModelId)) {
      const modelName = getEmbeddingModelName(kb.embeddingModelId) || '向量化模型'
      reasons.push(`${modelName}已禁用`)
    }
    
    if (!isVectorStoreTypeEnabled(kb.vectorStoreType)) {
      const vectorStoreName = getVectorStoreTypeName(kb.vectorStoreType)
      reasons.push(`${vectorStoreName}已禁用`)
    }
    
    return reasons.length > 0 ? reasons.join('，') : null
  }

  // 获取知识库工具提示内容（包含智能摘要和禁用原因）
  const getKnowledgeBaseTooltipContent = (kb) => {
    if (!kb) return ''
    
    const parts = []
    
    // 如果有智能摘要，优先显示
    if (kb.summary && kb.summary.trim()) {
      const escapedSummary = kb.summary
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
      parts.push(`<div style="margin-bottom: 8px; max-width: 300px; line-height: 1.6; padding: 8px 12px; background: linear-gradient(135deg, #f5f7fa 0%, #e8f4f8 100%); border-left: 3px solid #409eff; border-radius: 4px;"><div style="color: #409eff; font-weight: 600; font-size: 13px; margin-bottom: 6px;">📋 智能摘要</div><div style="color: #606266; font-size: 13px;">${escapedSummary}</div></div>`)
    } else if (kb.description) {
      const escapedDesc = kb.description
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
      parts.push(`<div style="margin-bottom: 8px;">${escapedDesc}</div>`)
    }
    
    // 如果有禁用原因，也显示
    const disabledTip = getKnowledgeBaseDisabledTip(kb)
    if (disabledTip) {
      const escapedTip = disabledTip
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
      parts.push(`<div style="color: #f56c6c; margin-top: 8px; padding: 6px 10px; background-color: #fef0f0; border-left: 3px solid #f56c6c; border-radius: 4px; font-size: 12px;">⚠️ 无法使用：${escapedTip}。请联系管理员处理。</div>`)
    }
    
    return parts.length > 0 ? parts.join('') : ''
  }

  // 加载问答模型列表
  const loadQAModels = async () => {
    try {
      const response = await getAvailableQAModelsForRAG()
      // request拦截器已经提取了response.data，所以response就是数据本身
      const data = Array.isArray(response) ? response : (response?.data || [])
      availableQAModels.value = data || []
      
      // 如果没有选中模型，默认选择第一个或默认模型
      if (!selectedModelId.value && availableQAModels.value.length > 0) {
        const defaultModel = availableQAModels.value.find(m => m.isDefault)
        selectedModelId.value = defaultModel ? defaultModel.id : availableQAModels.value[0].id
      } else if (availableQAModels.value.length === 0) {
        logger.warn('没有可用的RAG问答模型，请先在"大模型管理"页面配置useFor为"rag"或"both"的模型')
        ElMessage.warning('当前没有可用的RAG问答模型，请先在"大模型管理"页面配置模型')
      }
           } catch (error) {
             handleError(error, '加载问答模型列表失败')
             availableQAModels.value = []
           }
  }

  // 发送消息
  const handleSend = async () => {
    if (!question.value.trim() || !selectedKB.value || sending.value) {
      return
    }

    // 检查向量化模型是否启用
    if (!isEmbeddingModelEnabled(selectedKB.value.embeddingModelId)) {
      const modelName = getEmbeddingModelName(selectedKB.value.embeddingModelId) || '默认向量化模型'
      const message = isAdmin 
        ? `该知识库使用的向量化模型"${modelName}"已被禁用，无法进行问答。请在管理端大模型管理页面启用该向量化模型。`
        : `该知识库使用的向量化模型"${modelName}"已被禁用，无法进行问答。请联系管理员启用该向量化模型。`
      ElMessage.warning(message)
      return
    }

    // 检查向量库类型是否启用
    if (!isVectorStoreTypeEnabled(selectedKB.value.vectorStoreType)) {
      const vectorStoreName = getVectorStoreTypeName(selectedKB.value.vectorStoreType)
      const message = isAdmin
        ? `该知识库使用的向量库类型"${vectorStoreName}"已被禁用，无法进行问答。请在向量库管理中启用该类型的向量库配置。`
        : `该知识库使用的向量库类型"${vectorStoreName}"已被禁用，无法进行问答。请联系管理员在向量库管理中启用该类型的向量库配置。`
      ElMessage.warning(message)
      return
    }

    // 检查知识库是否启用
    if (!isKnowledgeBaseActive(selectedKB.value.status)) {
      const message = isAdmin
        ? '该知识库已被禁用，无法进行问答。请在知识库管理中启用该知识库。'
        : '该知识库已被禁用，无法进行问答。请联系管理员在知识库管理中启用该知识库。'
      ElMessage.warning(message)
      return
    }

    const userQuestion = question.value.trim()
    
    chatHistory.value.push({
      type: 'user',
      content: userQuestion,
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    })

    question.value = ''
    sending.value = true

    await nextTick()
    scrollToBottom(true)

    try {
      const maxHistoryRounds = 10
      const historyMessages = chatHistory.value.slice(0, -1)
      const limitedHistory = historyMessages.slice(-maxHistoryRounds * 2).map(msg => ({
        role: msg.type === 'user' ? 'user' : 'assistant',
        content: msg.content
      }))
      
      const history = []
      for (let i = 0; i < limitedHistory.length; i++) {
        const msg = limitedHistory[i]
        if (msg.role && msg.content) {
          history.push(msg)
        }
      }

      if (useStream.value) {
        await handleStreamResponse(userQuestion, history)
      } else {
        await handleNormalResponse(userQuestion, history)
      }
           } catch (error) {
             handleError(error, '问答失败')
      
      // 提取更详细的错误信息
      let errorMessage = '抱歉，问答服务暂时不可用，请稍后重试。'
      if (error?.response?.data?.error) {
        errorMessage = `错误: ${error.response.data.error}`
      } else if (error?.response?.data?.message) {
        errorMessage = `错误: ${error.response.data.message}`
      } else if (error?.message) {
        // 网络错误或连接错误
        if (error.message.includes('Network') || error.message.includes('Failed to fetch') || error.message.includes('ECONNREFUSED')) {
          errorMessage = '无法连接到服务器，请检查网络连接和后端服务是否正常运行。'
        } else {
          errorMessage = `错误: ${error.message}`
        }
      }
      
      chatHistory.value.push({
        type: 'assistant',
        content: errorMessage,
        time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
      })
    } finally {
      sending.value = false
      currentStreamingMessage.value = null
      scrollToBottom(false)
    }
  }

  // 处理非流式响应
  const handleNormalResponse = async (userQuestion, history) => {
    const historyToSend = history && history.length > 0 ? history : null
    
    const res = await knowledgeBaseQA(
      selectedKB.value.id,
      userQuestion,
      conversationId.value,
      null,
      historyToSend,
      selectedModelId.value
    )

    if (res && res.data) {
      if (res.data.conversationId) {
        conversationId.value = res.data.conversationId
      }

      chatHistory.value.push({
        type: 'assistant',
        content: res.data.answer || '抱歉，无法生成答案。',
        time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
        sources: res.data.sources || []
      })
    } else {
      throw new Error('API返回数据格式错误')
    }
  }

  // 处理流式响应
  const handleStreamResponse = async (userQuestion, history) => {
    const aiMessageIndex = chatHistory.value.length
    chatHistory.value.push({
      type: 'assistant',
      content: '',
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      sources: [],
      isLoading: true
    })
    currentStreamingMessage.value = aiMessageIndex
    
    await nextTick()
    scrollToBottom(true)

    let fullAnswer = ''
    let sources = []
    let finalConversationId = conversationId.value

    try {
      const historyToSend = history && history.length > 0 ? history : null
      
      const response = await knowledgeBaseQAStream(
        selectedKB.value.id,
        userQuestion,
        conversationId.value,
        null,
        historyToSend,
        selectedModelId.value
      )

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      // 使用统一的 SSE 流式处理工具
      const { processSSEStream } = await import('@/composables/useSSEStream')
      
      await processSSEStream(response, {
        cumulative: false,
        contentFields: ['answer'],
        onData: (json) => {
          if (!chatHistory.value[aiMessageIndex]) return
          
          // 更新答案内容
          if (json.answer !== undefined && json.answer !== null) {
            fullAnswer = json.answer
            chatHistory.value[aiMessageIndex].isLoading = false
            chatHistory.value[aiMessageIndex].content = fullAnswer
            nextTick(() => scrollToBottom(false))
          }

          // 更新来源
          if (json.sources?.length > 0) {
            sources = json.sources
            chatHistory.value[aiMessageIndex].sources = sources
          }

          // 更新对话ID
          if (json.conversationId) {
            conversationId.value = json.conversationId
            finalConversationId = json.conversationId
          }

          // 处理完成标记
          if (json.finished) {
            chatHistory.value[aiMessageIndex].content = fullAnswer || json.answer || ''
            chatHistory.value[aiMessageIndex].sources = sources
            if (json.conversationId) {
              conversationId.value = json.conversationId
            }
          }
        }
      })
    } catch (error) {
      logger.error('流式响应失败', error)
      if (chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = fullAnswer || '抱歉，生成答案时发生错误。'
      }
      throw error
    }
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

  // 开启新对话
  const handleNewConversation = () => {
    chatHistory.value = []
    conversationId.value = null
    question.value = ''
    sending.value = false
    currentStreamingMessage.value = null
    ElMessage.success('已开启新对话')
    
    nextTick(() => {
      scrollToBottom(true)
    })
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

    // 检查知识库、向量化模型和向量库状态
    if (!selectedKB.value) {
      ElMessage.warning('请先选择知识库')
      return
    }

    if (!isEmbeddingModelEnabled(selectedKB.value.embeddingModelId)) {
      const modelName = getEmbeddingModelName(selectedKB.value.embeddingModelId) || '默认向量化模型'
      const message = isAdmin
        ? `该知识库使用的向量化模型"${modelName}"已被禁用，无法进行问答。请在管理端大模型管理页面启用该向量化模型。`
        : `该知识库使用的向量化模型"${modelName}"已被禁用，无法进行问答。请联系管理员启用该向量化模型。`
      ElMessage.warning(message)
      return
    }

    if (!isVectorStoreTypeEnabled(selectedKB.value.vectorStoreType)) {
      const vectorStoreName = getVectorStoreTypeName(selectedKB.value.vectorStoreType)
      const message = isAdmin
        ? `该知识库使用的向量库类型"${vectorStoreName}"已被禁用，无法进行问答。请在管理端向量库管理中启用该类型的向量库配置。`
        : `该知识库使用的向量库类型"${vectorStoreName}"已被禁用，无法进行问答。请联系管理员在向量库管理中启用该类型的向量库配置。`
      ElMessage.warning(message)
      return
    }

    if (!isKnowledgeBaseActive(selectedKB.value.status)) {
      const message = isAdmin
        ? '该知识库已被禁用，无法进行问答。请在管理端知识库管理中启用该知识库。'
        : '该知识库已被禁用，无法进行问答。请联系管理员在知识库管理中启用该知识库。'
      ElMessage.warning(message)
      return
    }

    // 移除当前的助手消息
    chatHistory.value.splice(messageIndex, 1)

    // 滚动到底部
    await nextTick()
    scrollToBottom(true)

    sending.value = true

    try {
      // 构建历史对话（排除刚移除的助手消息）
      const maxHistoryRounds = 10
      const historyMessages = chatHistory.value.slice()
      const limitedHistory = historyMessages.slice(-maxHistoryRounds * 2).map(msg => ({
        role: msg.type === 'user' ? 'user' : 'assistant',
        content: msg.content
      }))

      const history = []
      for (let i = 0; i < limitedHistory.length; i++) {
        const msg = limitedHistory[i]
        if (msg.role && msg.content) {
          history.push(msg)
        }
      }

      if (useStream.value) {
        await handleStreamResponse(userQuestion, history)
      } else {
        await handleNormalResponse(userQuestion, history)
      }
    } catch (error) {
      handleError(error, '重新生成响应失败', true, () => {
        chatHistory.value.push({
          type: 'assistant',
          content: '抱歉，重新生成答案时发生错误，请重试。',
          time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
        })
      })
    } finally {
      sending.value = false
      currentStreamingMessage.value = null
      scrollToBottom(false)
    }
  }

  // 滚动到底部
  const scrollToBottom = (force = false) => {
    nextTick(() => {
      if (chatHistoryRef.value) {
        if (!force) {
          const element = chatHistoryRef.value
          const threshold = 100
          const isNearBottom = element.scrollHeight - element.scrollTop - element.clientHeight < threshold
          if (!isNearBottom) {
            return
          }
        }
        chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
      }
    })
  }

  // 加载对话历史（仅user版本）
  const loadConversationHistory = async (convId) => {
    if (!enableConversationHistory) {
      logger.debug('对话历史功能未启用')
      return
    }

    try {
      const messages = await getConversationMessages(convId)
      if (messages && messages.length > 0) {
        let kbId = null
        try {
          const conversation = await getConversation(convId)
          if (conversation && conversation.knowledgeBaseId) {
            kbId = conversation.knowledgeBaseId
            if (knowledgeBases.value.length === 0) {
              await loadKnowledgeBases()
            }
            if (kbId && knowledgeBases.value.length > 0) {
              const kb = knowledgeBases.value.find(k => k.id === kbId)
              if (kb) {
                selectedKB.value = kb
              }
            }
          }
        } catch (e) {
          logger.debug('获取会话信息失败', e)
        }
        
        chatHistory.value = messages.map(msg => ({
          type: msg.role === 'user' ? 'user' : 'assistant',
          content: msg.content || '',
          sources: msg.sources || [],
          time: msg.createTime ? new Date(msg.createTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
          isLoading: false
        }))
        
        conversationId.value = convId.toString()
        
        await nextTick()
        scrollToBottom(true)
        
        ElMessage.success('已加载历史对话记录')
      }
           } catch (error) {
             handleError(error, '加载历史对话失败', { showMessage: true, logError: true })
           }
  }

  return {
    // 状态
    knowledgeBases,
    selectedKB,
    embeddingModels,
    question,
    sending,
    chatHistory,
    chatHistoryRef,
    conversationId,
    useStream,
    currentStreamingMessage,
    kbSearchKeyword,
    displayedCount,
    loadingMore,
    availableQAModels,
    selectedModelId,
    vectorDatabases,
    enabledVectorStoreTypes,
    
    // 计算属性
    filteredKnowledgeBases,
    displayedKnowledgeBases,
    hasMoreToLoad,
    remainingCount,
    
    // 方法
    handleKbSearch,
    loadMore,
    selectKB,
    loadKnowledgeBases,
    loadEmbeddingModels,
    getEmbeddingModelName,
    isEmbeddingModelEnabled,
    loadVectorDatabases,
    isVectorStoreTypeEnabled,
    getVectorStoreTypeName,
    isKnowledgeBaseActive,
    getKnowledgeBaseDisabledTip,
    getKnowledgeBaseTooltipContent,
    loadQAModels,
    handleSend,
    handleClearHistory,
    handleNewConversation,
    handleRegenerate,
    scrollToBottom,
    loadConversationHistory,
    renderMarkdown, // 暴露 renderMarkdown
    cleanup: cleanupMarkdown // 暴露清理函数
  }
}

