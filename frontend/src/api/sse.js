import { getFullAPIUrl } from '@/config/api'

/**
 * Send an authenticated request that expects a Server-Sent Events response.
 * Business APIs keep their own payload and event semantics; this helper owns
 * only the transport concerns shared by all streaming endpoints.
 */
export function requestSSE(path, options = {}) {
  const {
    method = 'POST',
    data,
    body,
    headers: extraHeaders = {},
    signal,
    credentials = 'include'
  } = options
  const requestBody = body ?? (data === undefined ? undefined : JSON.stringify(data))
  const headers = {
    Accept: 'text/event-stream',
    ...extraHeaders
  }

  if (data !== undefined && !(body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }

  const token = localStorage.getItem('token')
  if (token && !headers.Authorization) {
    headers.Authorization = `Bearer ${token}`
  }

  return fetch(getFullAPIUrl(path), {
    method,
    headers,
    credentials,
    signal,
    body: requestBody
  })
}
