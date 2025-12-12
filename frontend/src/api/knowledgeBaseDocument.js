import request from '@/utils/request'

/**
 * 获取知识库文档列表
 */
export function getDocumentList(kbId) {
  return request({
    url: `/api/knowledge-bases/${kbId}/documents`,
    method: 'get'
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

