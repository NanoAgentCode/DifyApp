import request from '@/utils/request'

/**
 * 获取所有用户列表
 */
export function getUserList() {
  return request({
    url: '/api/auth/users',
    method: 'get'
  })
}

/**
 * 管理员审核用户（激活用户）
 */
export function approveUser(userId) {
  return request({
    url: `/api/auth/approve/${userId}`,
    method: 'post'
  })
}

/**
 * 管理员禁用用户
 */
export function disableUser(userId) {
  return request({
    url: `/api/auth/disable/${userId}`,
    method: 'post'
  })
}

/**
 * 管理员重置用户密码
 */
export function resetPassword(userId, newPassword) {
  return request({
    url: `/api/auth/reset-password/${userId}`,
    method: 'post',
    data: {
      newPassword
    }
  })
}

/**
 * 获取用户的应用可见性列表
 */
export function getUserAppVisibilities(userId) {
  return request({
    url: `/api/auth/users/${userId}/app-visibilities`,
    method: 'get'
  })
}

/**
 * 更新用户对应用的可见性
 */
export function updateUserAppVisibility(userId, appId, visible) {
  return request({
    url: `/api/auth/users/${userId}/app-visibilities/${appId}`,
    method: 'put',
    params: {
      visible
    }
  })
}

