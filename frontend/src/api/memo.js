import request from '@/utils/request'

/**
 * 获取备忘录列表
 */
export function getMemos(params) {
  return request({
    url: '/api/memos',
    method: 'get',
    params: { status: params?.status, page: params?.page, size: params?.size }
  })
}

/**
 * 获取已到期的待提醒列表（轮询弹通知）
 */
export function getMemosDue() {
  return request({
    url: '/api/memos/due',
    method: 'get'
  })
}

/**
 * 创建备忘录（自然语言）
 */
export function createMemo(data) {
  return request({
    url: '/api/memos',
    method: 'post',
    data: { rawInput: data.rawInput }
  })
}

/**
 * 标记为已提醒
 */
export function markMemoDone(id) {
  return request({
    url: `/api/memos/${id}/done`,
    method: 'patch'
  })
}

/**
 * 取消备忘录
 */
export function cancelMemo(id) {
  return request({
    url: `/api/memos/${id}/cancel`,
    method: 'patch'
  })
}

/**
 * 删除备忘录
 */
export function deleteMemo(id) {
  return request({
    url: `/api/memos/${id}`,
    method: 'delete'
  })
}
