import request from '@/utils/request'

/**
 * 获取知识库列表
 */
export function getKnowledgeBaseList(params) {
  return request({
    url: '/api/knowledge-bases',
    method: 'get',
    params
  })
}

/**
 * 获取知识库详情
 */
export function getKnowledgeBaseDetail(id) {
  return request({
    url: `/api/knowledge-bases/${id}`,
    method: 'get'
  })
}

/**
 * 创建知识库
 * @param {Object} data 知识库数据
 * @param {Boolean} force 是否强制创建（忽略重复名称检查）
 */
export function createKnowledgeBase(data, force = false) {
  return request({
    url: '/api/knowledge-bases',
    method: 'post',
    data,
    params: {
      force
    }
  })
}

/**
 * 更新知识库
 */
export function updateKnowledgeBase(id, data) {
  return request({
    url: `/api/knowledge-bases/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除知识库
 */
export function deleteKnowledgeBase(id) {
  return request({
    url: `/api/knowledge-bases/${id}`,
    method: 'delete'
  })
}

/**
 * 生成知识库智能摘要
 * @param {Number} id 知识库ID
 * @param {Number} modelId 模型ID（可选）
 */
export function generateKnowledgeBaseSummary(id, modelId) {
  return request({
    url: `/api/knowledge-bases/${id}/generate-summary`,
    method: 'post',
    params: modelId ? { modelId } : {}
  })
}

/**
 * 导出知识库
 * @param {Number} id 知识库ID
 */
export function exportKnowledgeBase(id) {
  return request({
    url: `/api/knowledge-bases/${id}/export`,
    method: 'get',
    responseType: 'blob'
  })
}

/**
 * 预览ZIP文件内容
 * @param {FormData} formData 包含ZIP文件的FormData
 */
export function previewZipFile(formData) {
  return request({
    url: '/api/knowledge-bases/import/preview',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 导入知识库
 * @param {FormData} formData 包含ZIP文件和知识库信息的FormData
 */
export function importKnowledgeBase(formData) {
  return request({
    url: '/api/knowledge-bases/import',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

