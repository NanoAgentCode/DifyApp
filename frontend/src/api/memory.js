import request from '@/utils/request'

export function getMyMemoryItems(params) {
  return request({
    url: '/api/memory/items',
    method: 'get',
    params
  })
}

export function clearMyMemory(params) {
  return request({
    url: '/api/memory/clear',
    method: 'delete',
    params
  })
}

