import request from '@/utils/request'

/**
 * 获取所有统计数据
 */
export function getAllStatistics() {
  return request({
    url: '/api/admin/statistics',
    method: 'get'
  })
}

/**
 * 获取概览统计
 */
export function getOverviewStatistics() {
  return request({
    url: '/api/admin/statistics/overview',
    method: 'get'
  })
}

/**
 * 获取用户统计
 */
export function getUserStatistics() {
  return request({
    url: '/api/admin/statistics/users',
    method: 'get'
  })
}

/**
 * 获取应用统计
 */
export function getAppStatistics() {
  return request({
    url: '/api/admin/statistics/apps',
    method: 'get'
  })
}

/**
 * 获取知识库统计
 */
export function getKnowledgeBaseStatistics() {
  return request({
    url: '/api/admin/statistics/knowledge-bases',
    method: 'get'
  })
}

/**
 * 获取模型Token统计
 */
export function getModelTokenStatistics() {
  return request({
    url: '/api/admin/statistics/model-tokens',
    method: 'get'
  })
}

/**
 * 获取会话历史统计（复用现有接口）
 */
export function getChatHistoryStatistics() {
  return request({
    url: '/api/admin/chat/history/statistics',
    method: 'get'
  })
}

