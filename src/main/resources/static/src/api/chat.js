import request from '@/utils/request'

/**
 * 智能问答（非流式）
 */
export function chat(question, conversationId, userId, history, modelId) {
  return request({
    url: '/api/chat',
    method: 'post',
    data: {
      question,
      conversationId,
      userId,
      history,
      stream: false,
      modelId
    }
  })
}

/**
 * 智能问答（流式）
 */
export function chatStream(question, conversationId, userId, history, modelId) {
  // 获取JWT token
  const token = localStorage.getItem('token')
  
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'text/event-stream'
  }
  
  // 添加认证token
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  
  return fetch('/api/chat/stream', {
    method: 'POST',
    headers: headers,
    credentials: 'include', // 包含cookies
    body: JSON.stringify({
      question,
      conversationId,
      userId,
      history,
      stream: true,
      modelId
    })
  })
}

/**
 * 创建新会话
 */
export function createConversation(title, appId, knowledgeBaseId, type) {
  return request({
    url: '/api/chat/history/conversations',
    method: 'post',
    data: {
      title,
      appId,
      knowledgeBaseId,
      type
    }
  })
}

/**
 * 获取我的会话列表
 */
export function getMyConversations(page = 1, size = 20, keyword = '', type = null) {
  const params = { page, size }
  if (keyword) params.keyword = keyword
  if (type) params.type = type
  
  return request({
    url: '/api/chat/history/conversations',
    method: 'get',
    params
  })
}

/**
 * 获取会话详情
 */
export function getConversation(id) {
  return request({
    url: `/api/chat/history/conversations/${id}`,
    method: 'get'
  })
}

/**
 * 获取会话消息列表（该会话中的所有对话消息）
 */
export function getConversationMessages(id) {
  return request({
    url: `/api/chat/history/conversations/${id}/messages`,
    method: 'get'
  })
}

/**
 * 更新会话标题
 */
export function updateConversationTitle(id, title) {
  return request({
    url: `/api/chat/history/conversations/${id}/title`,
    method: 'put',
    data: { title }
  })
}

/**
 * 删除会话
 */
export function deleteConversation(id) {
  return request({
    url: `/api/chat/history/conversations/${id}`,
    method: 'delete'
  })
}

/**
 * 导出会话（包含该会话中的所有消息）
 */
export function exportConversation(id) {
  return request({
    url: `/api/chat/history/conversations/${id}/export`,
    method: 'get'
  })
}

/**
 * 管理员：获取所有对话列表
 */
export function getAllConversations(page = 1, size = 20, keyword = '', type = null, userId = null, startTime = null, endTime = null) {
  const params = { page, size }
  if (keyword) params.keyword = keyword
  if (type) params.type = type
  if (userId) params.userId = userId
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime
  
  return request({
    url: '/api/admin/chat/history/conversations',
    method: 'get',
    params
  })
}

/**
 * 管理员：获取统计信息
 */
export function getStatistics() {
  return request({
    url: '/api/admin/chat/history/statistics',
    method: 'get'
  })
}

/**
 * 管理员：批量删除对话
 */
export function batchDeleteConversations(ids) {
  return request({
    url: '/api/admin/chat/history/conversations/batch',
    method: 'delete',
    data: { ids }
  })
}
