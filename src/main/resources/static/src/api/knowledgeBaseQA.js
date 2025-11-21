import request from '@/utils/request'

/**
 * 知识库问答（非流式）
 */
export function knowledgeBaseQA(kbId, question, conversationId, userId, history) {
  return request({
    url: `/api/knowledge-bases/${kbId}/qa`,
    method: 'post',
    data: {
      question,
      conversationId,
      userId,
      history
    }
  })
}

/**
 * 知识库问答（流式）
 */
export function knowledgeBaseQAStream(kbId, question, conversationId, userId, history) {
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
  
  return fetch(`/api/knowledge-bases/${kbId}/qa/stream`, {
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

/**
 * 重新索引文档
 */
export function reindexDocument(kbId, docId) {
  return request({
    url: `/api/knowledge-bases/${kbId}/qa/documents/${docId}/reindex`,
    method: 'post'
  })
}

