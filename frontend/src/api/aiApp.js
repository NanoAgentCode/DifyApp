import request from '@/utils/request'
import { getFullAPIUrl } from '@/config/api'

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

/**
 * 上传文件到Dify
 */
export function uploadFile(id, formData, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    
    xhr.open('POST', getFullAPIUrl(`/api/ai-apps/${id}/files/upload`), true)

    const token = localStorage.getItem('token')
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }
    
    // 监听上传进度
    if (onProgress) {
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          onProgress(Math.round((e.loaded / e.total) * 100))
        }
      }
    }
    
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const result = JSON.parse(xhr.responseText)
          resolve(result)
        } catch (e) {
          reject(new Error('解析响应失败: ' + e.message))
        }
      } else {
        reject(new Error(`上传失败: ${xhr.status} ${xhr.statusText}`))
      }
    }
    
    xhr.onerror = () => {
      reject(new Error('网络错误'))
    }
    
    xhr.send(formData)
  })
}
