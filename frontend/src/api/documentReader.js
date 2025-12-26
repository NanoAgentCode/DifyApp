import request from '@/utils/request'

/**
 * 获取文档列表
 * @param {object} params - 查询参数
 * @param {string} params.keyword - 搜索关键词（文件名）
 * @param {string} params.fileType - 文件类型（扩展名）
 * @param {number} params.page - 页码（从1开始）
 * @param {number} params.pageSize - 每页大小
 */
export function getDocumentList(params = {}) {
  return request({
    url: '/api/document-reader/documents',
    method: 'get',
    params: params
  })
}

/**
 * 获取文档详情
 * @param {number} docId - 文档ID
 */
export function getDocumentDetail(docId) {
  return request({
    url: `/api/document-reader/documents/${docId}`,
    method: 'get'
  })
}

/**
 * 上传文档
 * @param {FormData} formData - 文件表单数据
 */
export function uploadDocument(formData) {
  return request({
    url: '/api/document-reader/documents/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 删除文档
 * @param {number} docId - 文档ID
 */
export function deleteDocument(docId) {
  return request({
    url: `/api/document-reader/documents/${docId}`,
    method: 'delete'
  })
}

/**
 * 重新向量化文档
 * @param {number} docId - 文档ID
 */
export function reindexDocument(docId) {
  return request({
    url: `/api/document-reader/documents/${docId}/reindex`,
    method: 'post'
  })
}

/**
 * 获取文档内容（用于显示）
 * @param {number} docId - 文档ID
 * @param {number} page - 页码（可选）
 */
export function getDocumentContent(docId, page = null) {
  const params = page !== null ? { page } : {}
  return request({
    url: `/api/document-reader/documents/${docId}/content`,
    method: 'get',
    params: params,
    responseType: 'blob'
  })
}

/**
 * 获取文档导读
 * @param {number} docId - 文档ID
 */
export function getDocumentGuide(docId) {
  return request({
    url: `/api/document-reader/documents/${docId}/guide`,
    method: 'get'
  })
}

/**
 * 保存文档导读
 * @param {number} docId - 文档ID
 * @param {string} content - 导读内容
 */
export function saveDocumentGuide(docId, content) {
  return request({
    url: `/api/document-reader/documents/${docId}/guide`,
    method: 'post',
    data: { content }
  })
}

/**
 * 生成文档导读（使用大模型）
 * @param {number} docId - 文档ID
 * @param {number} modelId - 模型ID（可选）
 */
export function generateDocumentGuide(docId, modelId = null) {
  return request({
    url: `/api/document-reader/documents/${docId}/guide/generate`,
    method: 'post',
    data: modelId ? { modelId } : {}
  })
}

/**
 * 获取文档原文文本
 * @param {number} docId - 文档ID
 */
export function getDocumentText(docId) {
  return request({
    url: `/api/document-reader/documents/${docId}/text`,
    method: 'get'
  })
}

/**
 * 翻译文档
 * @param {number} docId - 文档ID
 * @param {string} targetLang - 目标语言（如：zh, en）
 */
export function translateDocument(docId, targetLang) {
  return request({
    url: `/api/document-reader/documents/${docId}/translate`,
    method: 'post',
    data: { targetLang }
  })
}

/**
 * 获取文档翻译内容
 * @param {number} docId - 文档ID
 * @param {string} targetLang - 目标语言
 */
export function getDocumentTranslation(docId, targetLang) {
  return request({
    url: `/api/document-reader/documents/${docId}/translation`,
    method: 'get',
    params: { targetLang }
  })
}

/**
 * 获取文档翻译内容（懒加载模式，指定范围）
 * @param {number} docId - 文档ID
 * @param {string} targetLang - 目标语言
 * @param {number} startSegment - 起始分段索引
 * @param {number} endSegment - 结束分段索引（不包含）
 */
export function getDocumentTranslationRange(docId, targetLang, startSegment = 0, endSegment = 10) {
  return request({
    url: `/api/document-reader/documents/${docId}/translation/range`,
    method: 'get',
    params: { targetLang, startSegment, endSegment }
  })
}

/**
 * 获取文档分段信息
 * @param {number} docId - 文档ID
 */
export function getDocumentSegments(docId) {
  return request({
    url: `/api/document-reader/documents/${docId}/segments`,
    method: 'get'
  })
}

/**
 * 翻译指定分段（懒加载）
 * @param {number} docId - 文档ID
 * @param {string} targetLang - 目标语言
 * @param {number} segmentIndex - 分段索引
 */
export function translateDocumentSegment(docId, targetLang, segmentIndex) {
  return request({
    url: `/api/document-reader/documents/${docId}/translate/segment`,
    method: 'post',
    data: { targetLang, segmentIndex }
  })
}

/**
 * 保存文档翻译内容
 * @param {number} docId - 文档ID
 * @param {string} targetLang - 目标语言
 * @param {string} content - 翻译内容
 */
export function saveDocumentTranslation(docId, targetLang, content) {
  return request({
    url: `/api/document-reader/documents/${docId}/translation`,
    method: 'post',
    data: { targetLang, content }
  })
}

/**
 * 获取文档脑图
 * @param {number} docId - 文档ID
 */
export function getDocumentMindMap(docId) {
  return request({
    url: `/api/document-reader/documents/${docId}/mindmap`,
    method: 'get'
  })
}

/**
 * 保存文档脑图
 * @param {number} docId - 文档ID
 * @param {object} data - 脑图数据（JSON格式）
 */
export function saveDocumentMindMap(docId, data) {
  return request({
    url: `/api/document-reader/documents/${docId}/mindmap`,
    method: 'post',
    data: { mindMapData: data }
  })
}

/**
 * 生成文档脑图（使用大模型）
 * @param {number} docId - 文档ID
 * @param {number} modelId - 模型ID（可选）
 */
export function generateDocumentMindMap(docId, modelId = null) {
  return request({
    url: `/api/document-reader/documents/${docId}/mindmap/generate`,
    method: 'post',
    data: modelId ? { modelId } : {}
  })
}

/**
 * 获取文档笔记
 * @param {number} docId - 文档ID
 */
export function getDocumentNotes(docId) {
  return request({
    url: `/api/document-reader/documents/${docId}/notes`,
    method: 'get'
  })
}

/**
 * 保存文档笔记
 * @param {number} docId - 文档ID
 * @param {string} content - 笔记内容
 */
export function saveDocumentNotes(docId, content) {
  return request({
    url: `/api/document-reader/documents/${docId}/notes`,
    method: 'post',
    data: { content }
  })
}

/**
 * 文档问答（非流式）
 * @param {number} docId - 文档ID
 * @param {string} question - 问题
 * @param {number} conversationId - 会话ID（可选）
 * @param {number} userId - 用户ID
 * @param {array} history - 历史对话（可选）
 * @param {number} modelId - 模型ID
 */
export function documentQA(docId, question, conversationId, userId, history, modelId) {
  return request({
    url: `/api/document-reader/documents/${docId}/qa`,
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
 * 文档问答（流式）
 * @param {number} docId - 文档ID
 * @param {string} question - 问题
 * @param {number} conversationId - 会话ID（可选）
 * @param {number} userId - 用户ID
 * @param {array} history - 历史对话（可选）
 * @param {number} modelId - 模型ID
 */
export function documentQAStream(docId, question, conversationId, userId, history, modelId) {
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
  
  return fetch(`/api/document-reader/documents/${docId}/qa/stream`, {
    method: 'POST',
    headers: headers,
    credentials: 'include',
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

