import request from '@/utils/request'

/**
 * 获取数据源列表
 */
export function getDataSourceList(params) {
  return request({
    url: '/api/data-sources',
    method: 'get',
    params
  })
}

/**
 * 获取数据源详情
 */
export function getDataSourceDetail(id) {
  return request({
    url: `/api/data-sources/${id}`,
    method: 'get'
  })
}

/**
 * 创建数据源
 * @param {Object} data 数据源数据
 * @param {Boolean} force 是否强制创建（忽略重复名称检查）
 */
export function createDataSource(data, force = false) {
  return request({
    url: '/api/data-sources',
    method: 'post',
    data,
    params: {
      force
    }
  })
}

/**
 * 更新数据源
 */
export function updateDataSource(id, data) {
  return request({
    url: `/api/data-sources/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除数据源
 */
export function deleteDataSource(id) {
  return request({
    url: `/api/data-sources/${id}`,
    method: 'delete'
  })
}

/**
 * 测试数据源连接
 */
export function testDataSourceConnection(id) {
  return request({
    url: `/api/data-sources/${id}/test`,
    method: 'post'
  })
}

/**
 * 刷新表结构
 */
export function refreshSchema(id, tableName = null) {
  return request({
    url: `/api/data-sources/${id}/refresh-schema`,
    method: 'post',
    params: tableName ? { tableName } : {}
  })
}

/**
 * 获取用户数据源可见性列表
 */
export function getUserDataSourceVisibilities(userId) {
  return request({
    url: `/api/auth/users/${userId}/data-source-visibilities`,
    method: 'get'
  })
}

/**
 * 更新用户数据源可见性
 */
export function updateUserDataSourceVisibility(userId, dataSourceId, visible) {
  return request({
    url: `/api/auth/users/${userId}/data-source-visibilities/${dataSourceId}`,
    method: 'put',
    params: { visible }
  })
}

/**
 * 批量更新用户数据源可见性
 */
export function batchUpdateUserDataSourceVisibility(userId, dataSourceIds, visible) {
  return request({
    url: `/api/auth/users/${userId}/data-source-visibilities/batch`,
    method: 'put',
    data: { dataSourceIds, visible }
  })
}

