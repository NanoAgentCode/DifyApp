import request from '@/utils/request'

/**
 * 获取提示词列表
 */
export function getPrompts(keyword) {
  return request({
    url: '/api/prompts',
    method: 'get',
    params: { keyword }
  })
}

/**
 * 根据ID获取提示词
 */
export function getPromptById(id) {
  return request({
    url: `/api/prompts/${id}`,
    method: 'get'
  })
}

/**
 * 创建提示词
 */
export function createPrompt(data) {
  return request({
    url: '/api/prompts',
    method: 'post',
    data
  })
}

/**
 * 更新提示词
 */
export function updatePrompt(id, data) {
  return request({
    url: `/api/prompts/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除提示词
 */
export function deletePrompt(id) {
  return request({
    url: `/api/prompts/${id}`,
    method: 'delete'
  })
}
