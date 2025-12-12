/**
 * API 缓存 Composables
 * 提供请求缓存功能，减少重复请求
 */
import { ref } from 'vue'

// 全局缓存
const cache = new Map()
const defaultTTL = 5 * 60 * 1000 // 默认 5 分钟

/**
 * 生成缓存键
 */
function generateCacheKey(url, params = {}) {
  const sortedParams = Object.keys(params)
    .sort()
    .map(key => `${key}=${JSON.stringify(params[key])}`)
    .join('&')
  return `${url}?${sortedParams}`
}

/**
 * 使用 API 缓存
 * @param {Object} options 配置选项
 * @param {number} options.ttl 缓存时间（毫秒）
 * @param {Function} options.keyGenerator 自定义缓存键生成器
 */
export function useApiCache(options = {}) {
  const { ttl = defaultTTL, keyGenerator = generateCacheKey } = options

  /**
   * 获取缓存
   */
  const getCache = (url, params = {}) => {
    const key = keyGenerator(url, params)
    const cached = cache.get(key)
    
    if (cached && Date.now() - cached.timestamp < ttl) {
      return cached.data
    }
    
    // 缓存过期，删除
    if (cached) {
      cache.delete(key)
    }
    
    return null
  }

  /**
   * 设置缓存
   */
  const setCache = (url, params = {}, data) => {
    const key = keyGenerator(url, params)
    cache.set(key, {
      data,
      timestamp: Date.now()
    })
  }

  /**
   * 清除缓存
   */
  const clearCache = (url, params = {}) => {
    if (url) {
      const key = keyGenerator(url, params)
      cache.delete(key)
    } else {
      // 清除所有缓存
      cache.clear()
    }
  }

  /**
   * 包装 API 调用，自动处理缓存
   */
  const cachedApiCall = async (apiCall, url, params = {}) => {
    // 先尝试从缓存获取
    const cached = getCache(url, params)
    if (cached !== null) {
      return cached
    }

    // 执行 API 调用
    const data = await apiCall()
    
    // 设置缓存
    setCache(url, params, data)
    
    return data
  }

  return {
    getCache,
    setCache,
    clearCache,
    cachedApiCall
  }
}

/**
 * 清除所有缓存（全局函数）
 */
export function clearAllCache() {
  cache.clear()
}

