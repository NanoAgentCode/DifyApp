/**
 * 通用响应处理工具
 * 统一处理 API 响应的数据提取和更新逻辑
 */

/**
 * 从响应中提取内容
 * @param {Object} response API 响应对象
 * @param {Array<string>} contentFields 内容字段优先级列表
 * @param {string} defaultValue 默认值
 * @returns {string} 提取的内容
 */
export function extractContent(response, contentFields = ['content', 'answer', 'text', 'message'], defaultValue = '') {
  if (!response) return defaultValue
  
  // 按优先级查找内容字段
  for (const field of contentFields) {
    if (response[field] !== undefined && response[field] !== null) {
      return response[field]
    }
  }
  
  return defaultValue
}

/**
 * 更新消息内容的通用方法
 * @param {Object} options 配置选项
 * @param {Array} options.messages 消息数组
 * @param {number} options.messageIndex 消息索引
 * @param {string} options.content 消息内容
 * @param {boolean} options.isLoading 是否加载中
 * @param {Object} options.metadata 额外元数据
 */
export function updateMessage(options) {
  const {
    messages,
    messageIndex,
    content,
    isLoading = false,
    metadata = {}
  } = options
  
  if (!messages || messageIndex < 0 || !messages[messageIndex]) return
  
  messages[messageIndex] = {
    ...messages[messageIndex],
    content: content || messages[messageIndex].content || '',
    isLoading,
    ...metadata
  }
}

/**
 * 更新对话 ID
 * @param {Object} response 响应对象
 * @param {Object} conversationIdRef Vue ref 对象
 * @param {Array<string>} idFields ID 字段名数组
 */
export function updateConversationId(response, conversationIdRef, idFields = ['conversationId', 'conversation_id']) {
  if (!response || !conversationIdRef) return
  
  for (const field of idFields) {
    if (response[field] !== undefined && response[field] !== null) {
      conversationIdRef.value = response[field]
      return
    }
  }
}

/**
 * 处理聊天响应的通用方法
 * @param {Object} options 配置选项
 * @returns {Object} 处理结果
 */
export function processChatResponse(options) {
  const {
    response,
    messages,
    messageIndex,
    conversationIdRef,
    contentFields = ['content', 'answer'],
    defaultContent = '抱歉，未能生成回复',
    extractMetadata = (res) => ({})
  } = options
  
  // 提取内容
  const content = extractContent(response, contentFields, defaultContent)
  
  // 提取元数据
  const metadata = extractMetadata(response)
  
  // 更新消息
  updateMessage({
    messages,
    messageIndex,
    content,
    isLoading: false,
    metadata
  })
  
  // 更新对话ID
  updateConversationId(response, conversationIdRef)
  
  return {
    content,
    conversationId: conversationIdRef.value,
    metadata
  }
}

/**
 * 创建消息处理器
 * @param {Object} messagesRef Vue ref 对象
 * @param {Object} conversationIdRef Vue ref 对象
 * @param {Object} config 配置
 * @returns {Object} 消息处理器
 */
export function createMessageHandler(messagesRef, conversationIdRef, config = {}) {
  const {
    contentFields = ['content', 'answer'],
    defaultContent = '抱歉，未能生成回复',
    metadataExtractor = null
  } = config
  
  /**
   * 添加用户消息
   * @param {string} content 消息内容
   * @param {Object} metadata 额外元数据
   * @returns {number} 消息索引
   */
  const addUserMessage = (content, metadata = {}) => {
    messagesRef.value.push({
      role: 'user',
      content,
      time: new Date(),
      ...metadata
    })
    return messagesRef.value.length - 1
  }
  
  /**
   * 添加AI消息占位
   * @param {Object} metadata 额外元数据
   * @returns {number} 消息索引
   */
  const addAIMessagePlaceholder = (metadata = {}) => {
    messagesRef.value.push({
      role: 'assistant',
      content: '',
      isLoading: true,
      time: new Date(),
      ...metadata
    })
    return messagesRef.value.length - 1
  }
  
  /**
   * 更新AI消息
   * @param {number} messageIndex 消息索引
   * @param {string} content 消息内容
   * @param {boolean} isLoading 是否加载中
   * @param {Object} metadata 额外元数据
   */
  const updateAIMessage = (messageIndex, content, isLoading = false, metadata = {}) => {
    updateMessage({
      messages: messagesRef.value,
      messageIndex,
      content,
      isLoading,
      metadata
    })
  }
  
  /**
   * 处理响应并更新消息
   * @param {number} messageIndex 消息索引
   * @param {Object} response API 响应
   * @returns {Object} 处理结果
   */
  const handleResponse = (messageIndex, response) => {
    const metadata = metadataExtractor ? metadataExtractor(response) : {}
    
    return processChatResponse({
      response,
      messages: messagesRef.value,
      messageIndex,
      conversationIdRef,
      contentFields,
      defaultContent,
      extractMetadata: () => metadata
    })
  }
  
  return {
    addUserMessage,
    addAIMessagePlaceholder,
    updateAIMessage,
    handleResponse
  }
}

export default {
  extractContent,
  updateMessage,
  updateConversationId,
  processChatResponse,
  createMessageHandler
}

