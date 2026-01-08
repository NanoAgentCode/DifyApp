import request from '@/utils/request'

/**
 * 分页查询用户行为日志
 */
export function getUserActionLogs(params) {
  return request({
    url: '/api/admin/user-action-logs',
    method: 'get',
    params
  })
}

/**
 * 根据ID查询日志详情
 */
export function getUserActionLogById(id) {
  return request({
    url: `/api/admin/user-action-logs/${id}`,
    method: 'get'
  })
}

/**
 * 删除日志
 */
export function deleteUserActionLog(id) {
  return request({
    url: `/api/admin/user-action-logs/${id}`,
    method: 'delete'
  })
}

/**
 * 批量删除日志
 */
export function batchDeleteUserActionLogs(ids) {
  return request({
    url: '/api/admin/user-action-logs/batch',
    method: 'delete',
    data: ids
  })
}
/**
 * 获取操作类型选项（用于下拉菜单）
 */
export function getUserActionLogActionTypes() {
  return request({
    url: '/api/admin/user-action-logs/action-types',
    method: 'get'
  })
}
