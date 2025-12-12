import request from '@/utils/request'

/**
 * 获取知识库文档列表
 * @param {number} kbId - 知识库ID
 * @param {object} params - 查询参数
 * @param {string} params.keyword - 搜索关键词（文件名）
 * @param {number} params.vectorizedStatus - 向量化状态（0-未向量化，1-向量化中，2-向量化成功，3-向量化失败）
 * @param {string} params.fileType - 文件类型（扩展名）
 * @param {number} params.page - 页码（从1开始）
 * @param {number} params.pageSize - 每页大小
 */
export function getDocumentList(kbId, params = {}) {
  return request({
    url: `/api/knowledge-bases/${kbId}/documents`,
    method: 'get',
    params: params
  })
}

/**
 * 获取知识库文档详情
 */
export function getDocumentDetail(kbId, docId) {
  return request({
    url: `/api/knowledge-bases/${kbId}/documents/${docId}`,
    method: 'get'
  })
}

/**
 * 上传文档
 */
export function uploadDocument(kbId, formData) {
  return request({
    url: `/api/knowledge-bases/${kbId}/documents/upload`,
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 删除文档
 */
export function deleteDocument(kbId, docId) {
  return request({
    url: `/api/knowledge-bases/${kbId}/documents/${docId}`,
    method: 'delete'
  })
}

/**
 * 下载文档
 */
export function downloadDocument(kbId, docId) {
  return request({
    url: `/api/knowledge-bases/${kbId}/documents/${docId}/download`,
    method: 'get',
    responseType: 'blob'
  })
}

/**
 * 重新向量化文档
 */
export function reindexDocument(kbId, docId) {
  return request({
    url: `/api/knowledge-bases/${kbId}/documents/${docId}/reindex`,
    method: 'post'
  })
}

