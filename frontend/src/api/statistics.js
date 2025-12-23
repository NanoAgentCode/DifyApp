import request from '@/utils/request'

/**
 * 获取所有统计数据
 * @param {number} days 统计天数，默认30天
 */
export function getAllStatistics(days = 30) {
  return request({
    url: '/api/admin/statistics',
    method: 'get',
    params: { days }
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
 * @param {number} days 统计天数，默认30天
 */
export function getChatHistoryStatistics(days = 30) {
  return request({
    url: '/api/admin/chat/history/statistics',
    method: 'get',
    params: { days }
  })
}

