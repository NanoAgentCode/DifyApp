import request from '@/utils/request'

/**
 * 获取所有向量数据库配置
 */
export function getVectorDatabaseList() {
  return request({
    url: '/api/vector-databases',
    method: 'get'
  })
}

/**
 * 根据类型获取向量数据库配置
 */
export function getVectorDatabaseListByType(type) {
  return request({
    url: `/api/vector-databases/type/${type}`,
    method: 'get'
  })
}

/**
 * 更新向量数据库配置
 */
export function updateVectorDatabaseConfig(data) {
  return request({
    url: '/api/vector-databases',
    method: 'put',
    data
  })
}

/**
 * 测试向量数据库连接
 */
export function testVectorDatabaseConnection(data) {
  return request({
    url: '/api/vector-databases/test',
    method: 'post',
    data
  })
}

