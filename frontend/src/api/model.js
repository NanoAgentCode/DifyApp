import request from '@/utils/request'

/**
 * 获取模型配置
 */
export function getModelConfig() {
  return request({
    url: '/api/models/config',
    method: 'get'
  })
}

/**
 * 更新模型配置
 * data 格式：
 * - 单个配置更新：{ embedding: {...} }
 * - 模型管理操作：{ type: 'chat'|'rag', action: 'add'|'update'|'delete'|'setDefault'|'toggleEnabled', model: {...}, modelId: ... }
 */
export function updateModelConfig(data) {
  return request({
    url: '/api/models/config',
    method: 'put',
    data
  })
}

/**
 * 测试模型连接
 */
export function testModelConnection(data) {
  return request({
    url: '/api/models/test',
    method: 'post',
    data
  })
}

/**
 * 获取可用的问答模型列表（用于智能问答）
 */
export function getAvailableQAModels() {
  return request({
    url: '/api/models/qa/available',
    method: 'get'
  })
}

/**
 * 获取可用的问答模型列表（用于知识库问答）
 */
export function getAvailableQAModelsForRAG() {
  return request({
    url: '/api/models/qa/available/rag',
    method: 'get'
  })
}

