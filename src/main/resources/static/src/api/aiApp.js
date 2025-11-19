import request from '@/utils/request'

/**
 * 获取应用列表
 */
export function getAppList(params) {
  return request({
    url: '/api/ai-apps',
    method: 'get',
    params
  })
}

/**
 * 获取应用详情
 */
export function getAppDetail(id) {
  return request({
    url: `/api/ai-apps/${id}`,
    method: 'get'
  })
}

/**
 * 创建应用
 */
export function createApp(data) {
  return request({
    url: '/api/ai-apps',
    method: 'post',
    data
  })
}

/**
 * 更新应用
 */
export function updateApp(id, data) {
  return request({
    url: `/api/ai-apps/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除应用
 */
export function deleteApp(id) {
  return request({
    url: `/api/ai-apps/${id}`,
    method: 'delete'
  })
}

/**
 * 调用Chat Flow（非流式）
 */
export function chatApp(id, data) {
  return request({
    url: `/api/ai-apps/${id}/chat`,
    method: 'post',
    data
  })
}

/**
 * 调用Chat Flow（流式）
 */
export function chatAppStream(id, data) {
  return request({
    url: `/api/ai-apps/${id}/chat/stream`,
    method: 'post',
    data,
    responseType: 'stream'
  })
}

/**
 * 调用Workflow（非流式）
 */
export function workflowApp(id, data) {
  return request({
    url: `/api/ai-apps/${id}/workflow`,
    method: 'post',
    data
  })
}

/**
 * 调用Workflow（流式）
 */
export function workflowAppStream(id, data) {
  return request({
    url: `/api/ai-apps/${id}/workflow/stream`,
    method: 'post',
    data,
    responseType: 'stream'
  })
}

