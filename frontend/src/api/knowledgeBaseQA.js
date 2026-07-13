import request from '@/utils/request'
import { requestSSE } from '@/api/sse'

/**
 * 知识库问答（非流式）
 */
export function knowledgeBaseQA(kbId, question, conversationId, userId, history, modelId) {
  return request({
    url: `/api/knowledge-bases/${kbId}/qa`,
    method: 'post',
    data: {
      question,
      conversationId,
      userId,
      history,
      modelId
    }
  })
}

/**
 * 知识库问答（流式）
 */
export function knowledgeBaseQAStream(kbId, question, conversationId, userId, history, modelId) {
  return requestSSE(`/api/knowledge-bases/${kbId}/qa/stream`, {
    data: {
      question,
      conversationId,
      userId,
      history,
      stream: true,
      modelId
    }
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

