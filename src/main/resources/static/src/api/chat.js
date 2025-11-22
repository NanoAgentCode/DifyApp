import request from '@/utils/request'

/**
 * 智能问答（非流式）
 */
export function chat(question, conversationId, userId, history) {
  return request({
    url: '/api/chat',
    method: 'post',
    data: {
      question,
      conversationId,
      userId,
      history,
      stream: false
    }
  })
}

/**
 * 智能问答（流式）
 */
export function chatStream(question, conversationId, userId, history) {
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
      stream: true
    })
  })
}

