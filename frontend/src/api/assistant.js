import request from '@/utils/request'
import { requestSSE } from '@/api/sse'

export function assistantChat(data) {
  return request({
    url: '/api/assistant',
    method: 'post',
    data
  })
}

export function assistantChatStream(data, signal) {
  return requestSSE('/api/assistant/stream', { data, signal })
}
