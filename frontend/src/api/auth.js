import request from '@/utils/request'

/**
 * 用户注册
 */
export function register(data) {
  return request({
    url: '/api/auth/register',
    method: 'post',
    data,
    skipRetry: true
  })
}

/**
 * 用户登录
 */
export function login(data) {
  return request({
    url: '/api/auth/login',
    method: 'post',
    data
  })
}

/**
 * 发送注册或重置密码邮箱验证码
 */
export function sendVerificationCode(email, purpose) {
  return request({
    url: '/api/auth/verification-code',
    method: 'post',
    data: { email, purpose },
    skipRetry: true
  })
}

/**
 * 使用邮箱验证码找回密码
 */
export function forgotPassword(data) {
  return request({
    url: '/api/auth/forgot-password',
    method: 'post',
    data,
    skipRetry: true
  })
}

/**
 * 验证 token 是否有效
 * 通过调用需要认证的 API 来验证 token
 */
export async function validateToken() {
  try {
    await request({
      url: '/api/rbac/my-permissions',
      method: 'get'
    })
    return true
  } catch (error) {
    // 如果是 401 错误，说明 token 无效
    if (error.response && error.response.status === 401) {
      return false
    }
    // 其他错误（如网络错误）也认为 token 可能无效
    return false
  }
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
 * 修改密码
 */
export function changePassword(data) {
  return request({
    url: '/api/auth/change-password',
    method: 'post',
    data,
    skipRetry: true
  })
}
