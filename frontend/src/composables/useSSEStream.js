/**
 * SSE 流式响应处理 Composable
 * 统一处理所有流式响应的通用逻辑
 */

/**
 * 检查 JSON 字符串是否完整
 * @param {string} str JSON 字符串
 * @returns {boolean} 是否完整
 */
const isCompleteJSON = (str) => {
  if (!str?.trim()) return false
  
  const trimmed = str.trim()
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return false
  
  const openBraces = (trimmed.match(/{/g) || []).length
  const closeBraces = (trimmed.match(/}/g) || []).length
  const openBrackets = (trimmed.match(/\[/g) || []).length
  const closeBrackets = (trimmed.match(/\]/g) || []).length
  
  return openBraces === closeBraces && 
         openBrackets === closeBrackets && 
         (trimmed.endsWith('}') || trimmed.endsWith(']'))
}

/**
 * 解析 SSE 数据行，提取 JSON 内容
 * @param {string} line SSE 数据行
 * @returns {string|null} JSON 字符串或 null
 */
const extractSSEData = (line) => {
  if (!line?.trim()) return null
  
  const trimmed = line.trim()
  
  // 处理 data: 前缀
  if (trimmed.startsWith('data: ')) {
    return trimmed.substring(6).trim()
  }
  
  if (trimmed.startsWith('data:')) {
    return trimmed.substring(5).trim()
  }
  
  // 检查原始行是否包含 data: 前缀（处理特殊格式）
  const match = line.match(/^data:\s*(.*)$/)
  if (match) {
    return match[1]
  }
  
  // 忽略其他 SSE 标记行
  if (trimmed.startsWith('event:') || 
      trimmed.startsWith('id:') || 
      trimmed.startsWith(':') ||
      trimmed === '[DONE]') {
    return null
  }
  
  // 可能是直接的 JSON 数据
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    return trimmed
  }
  
  return null
}

/**
 * 处理 SSE 流式响应
 * @param {Response} response Fetch API 响应对象
 * @param {Object} options 配置选项
 * @param {Function} options.onData 数据回调 (json) => void
 * @param {Function} options.onError 错误回调 (error) => void
 * @param {Function} options.onComplete 完成回调 () => void
 * @param {Array<string>} options.contentFields 内容字段名数组（默认 ['content', 'answer']）
 * @param {Array<string>} options.endEvents 结束事件名数组（默认 ['message_end', 'workflow_finished']）
 * @param {boolean} options.cumulative 是否累积内容（默认 false）
 * @returns {Promise<void>}
 */
export async function processSSEStream(response, options = {}) {
  const {
    onData = () => {},
    onError = () => {},
    onComplete = () => {},
    contentFields = ['content', 'answer'],
    endEvents = ['message_end', 'workflow_finished'],
    cumulative = false
  } = options

  if (!response?.ok) {
    const error = new Error(`HTTP ${response?.status || 'Unknown'}`)
    onError(error)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let pendingData = ''
  let cumulativeContent = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      
      if (done) {
        // 处理剩余数据
        if (pendingData) {
          if (buffer.trim()) {
            pendingData += buffer.trim()
          }
          try {
            const json = JSON.parse(pendingData)
            onData(json, cumulative ? cumulativeContent : null)
          } catch (e) {
            // 忽略解析失败
          }
        } else if (buffer.trim()) {
          const dataContent = extractSSEData(buffer)
          if (dataContent) {
            try {
              const json = JSON.parse(dataContent)
              onData(json, cumulative ? cumulativeContent : null)
            } catch (e) {
              // 忽略解析失败
            }
          }
        }
        break
      }

      buffer += decoder.decode(value, { stream: true })
      
      // 按行处理
      let newlineIndex
      while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
        const line = buffer.substring(0, newlineIndex)
        buffer = buffer.substring(newlineIndex + 1)
        
        const trimmed = line.trim()
        
        // 空行表示事件结束，处理 pending 数据
        if (!trimmed) {
          if (pendingData) {
            try {
              const json = JSON.parse(pendingData)
              
              // 累积内容
              if (cumulative) {
                const content = contentFields.find(field => json[field] !== undefined)
                if (content !== undefined) {
                  cumulativeContent += json[content] || ''
                }
              }
              
              onData(json, cumulative ? cumulativeContent : null)
              
              // 检查是否结束
              if (json.finished === true || 
                  (json.event && endEvents.includes(json.event))) {
                onComplete()
                return
              }
            } catch (e) {
              // 忽略解析失败
            }
            pendingData = ''
          }
          continue
        }
        
        // 提取数据内容
        const dataContent = extractSSEData(line)
        if (!dataContent) continue
        
        // 合并 pending 数据
        let fullData = pendingData ? pendingData + dataContent : dataContent
        
        // 检查 JSON 完整性
        if (isCompleteJSON(fullData)) {
          try {
            const json = JSON.parse(fullData)
            
            // 累积内容
            if (cumulative) {
              const contentField = contentFields.find(field => json[field] !== undefined)
              if (contentField) {
                cumulativeContent += json[contentField] || ''
              }
            }
            
            onData(json, cumulative ? cumulativeContent : null)
            
            // 检查是否结束
            if (json.finished === true || 
                (json.event && endEvents.includes(json.event))) {
              onComplete()
              return
            }
            
            pendingData = ''
          } catch (e) {
            // JSON 解析失败，可能是因为数据不完整
            pendingData = fullData
          }
        } else {
          // JSON 不完整，保存到 pending
          pendingData = fullData
        }
      }
    }
    
    onComplete()
  } catch (error) {
    onError(error)
  }
}

/**
 * 创建 SSE 流式响应处理器（带累积内容）
 * 适用于需要累积显示完整内容的场景
 * @param {Function} updateCallback 更新回调 (cumulativeContent) => void
 * @param {Object} streamOptions 流式处理选项
 * @returns {Object} { start: Function, stop: Function }
 */
export function createCumulativeSSEHandler(updateCallback, streamOptions = {}) {
  let abortController = null
  
  const start = async (url, requestData) => {
    abortController = new AbortController()
    
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
        },
        body: JSON.stringify(requestData),
        signal: abortController.signal
      })
      
      await processSSEStream(response, {
        cumulative: true,
        onData: (json, cumulativeContent) => {
          if (cumulativeContent !== null) {
            updateCallback(cumulativeContent)
          }
        },
        ...streamOptions
      })
    } catch (error) {
      if (error.name !== 'AbortError') {
        streamOptions.onError?.(error)
      }
    }
  }
  
  const stop = () => {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
  }
  
  return { start, stop }
}

/**
 * 创建 SSE 流式响应处理器（逐个数据块）
 * 适用于需要逐个处理数据块的场景
 * @param {Function} dataCallback 数据回调 (json) => void
 * @param {Object} streamOptions 流式处理选项
 * @returns {Object} { start: Function, stop: Function }
 */
export function createIncrementalSSEHandler(dataCallback, streamOptions = {}) {
  let abortController = null
  
  const start = async (url, requestData) => {
    abortController = new AbortController()
    
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
        },
        body: JSON.stringify(requestData),
        signal: abortController.signal
      })
      
      await processSSEStream(response, {
        cumulative: false,
        onData: dataCallback,
        ...streamOptions
      })
    } catch (error) {
      if (error.name !== 'AbortError') {
        streamOptions.onError?.(error)
      }
    }
  }
  
  const stop = () => {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
  }
  
  return { start, stop }
}

export default {
  processSSEStream,
  createCumulativeSSEHandler,
  createIncrementalSSEHandler,
  isCompleteJSON,
  extractSSEData
}

