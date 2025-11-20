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

