/**
 * 请求重试工具
 * 为axios请求添加自动重试机制
 */

/**
 * 重试配置选项
 * @typedef {Object} RetryConfig
 * @property {number} retries - 重试次数，默认3次
 * @property {number} retryDelay - 重试延迟（毫秒），默认1000ms
 * @property {Function} shouldRetry - 判断是否应该重试的函数
 * @property {Function} onRetry - 重试回调函数
 */

/**
 * 创建带重试功能的axios拦截器
 * @param {RetryConfig} config - 重试配置
 * @returns {Object} axios拦截器配置
 */
export function createRetryInterceptor(config = {}) {
  const {
    retries = 3,
    retryDelay = 1000,
    shouldRetry = (error) => {
      // 默认重试条件：网络错误或5xx错误
      if (!error.response) return true
      const status = error.response.status
      return status >= 500 || status === 429
    },
    onRetry = (retryCount, error) => {
      console.log(`请求重试 (${retryCount}/${retries}):`, error.config?.url)
    }
  } = config

  return {
    requestInterceptor: (requestConfig) => {
      // 为每个请求添加重试计数器
      requestConfig._retryCount = 0
      requestConfig._maxRetries = retries
      requestConfig._retryDelay = retryDelay
      requestConfig._shouldRetry = shouldRetry
      requestConfig._onRetry = onRetry
      return requestConfig
    },

    responseInterceptorError: async (error) => {
      const config = error.config

      // 检查是否应该重试
      if (!config || config._retryCount >= config._maxRetries) {
        return Promise.reject(error)
      }

      if (!config._shouldRetry(error)) {
        return Promise.reject(error)
      }

      // 增加重试计数
      config._retryCount++

      // 触发重试回调
      config._onRetry?.(config._retryCount, error)

      // 等待延迟后重试
      await new Promise(resolve => setTimeout(resolve, config._retryDelay))

      // 重新发送请求
      return new Promise((resolve, reject) => {
        axios(config).then(resolve).catch(reject)
      })
    }
  }
}

/**
 * 创建指数退避重试配置
 * @param {number} baseDelay - 基础延迟时间（毫秒）
 * @param {number} maxDelay - 最大延迟时间（毫秒）
 * @returns {Function} 计算退避时间的函数
 */
export function createExponentialBackoff(baseDelay = 1000, maxDelay = 10000) {
  return (retryCount) => {
    const delay = Math.min(baseDelay * Math.pow(2, retryCount), maxDelay)
    // 添加随机抖动，避免多个请求同时重试
    return delay + Math.random() * baseDelay
  }
}

export default {
  createRetryInterceptor,
  createExponentialBackoff
}
