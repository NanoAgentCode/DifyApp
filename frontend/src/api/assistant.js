import request from '@/utils/request'
import { getFullAPIUrl } from '@/config/api'

export function assistantChat(data) {
  return request({
    url: '/api/assistant',
    method: 'post',
    data
  })
}

export function assistantChatStream(data) {
  const token = localStorage.getItem('token')
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  return fetch(getFullAPIUrl('/api/assistant/stream'), {
    method: 'POST',
    headers,
    credentials: 'include',
    body: JSON.stringify(data)
  })
}
