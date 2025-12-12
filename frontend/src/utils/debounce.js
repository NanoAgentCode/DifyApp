/**
 * 防抖函数
 * @param {Function} func 要防抖的函数
 * @param {number} wait 等待时间（毫秒）
 * @param {boolean} immediate 是否立即执行
 * @returns {Function}
 */
export function debounce(func, wait = 300, immediate = false) {
  let timeout
  return function executedFunction(...args) {
    const later = () => {
      timeout = null
      if (!immediate) func(...args)
    }
    const callNow = immediate && !timeout
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
    if (callNow) func(...args)
  }
}

/**
 * 节流函数
 * @param {Function} func 要节流的函数
 * @param {number} limit 时间限制（毫秒）
 * @returns {Function}
 */
export function throttle(func, limit = 300) {
  let inThrottle
  return function executedFunction(...args) {
    if (!inThrottle) {
      func.apply(this, args)
      inThrottle = true
      setTimeout(() => (inThrottle = false), limit)
    }
  }
}

/**
 * Vue 3 Composition API 防抖 Hook
 * @param {Function} fn 要防抖的函数
 * @param {number} delay 延迟时间（毫秒）
 * @returns {Function}
 */
export function useDebounceFn(fn, delay = 300) {
  let timeoutId
  return function debouncedFn(...args) {
    clearTimeout(timeoutId)
    timeoutId = setTimeout(() => {
      fn(...args)
    }, delay)
  }
}

/**
 * Vue 3 Composition API 节流 Hook
 * @param {Function} fn 要节流的函数
 * @param {number} limit 时间限制（毫秒）
 * @returns {Function}
 */
export function useThrottleFn(fn, limit = 300) {
  let lastRun = 0
  return function throttledFn(...args) {
    const now = Date.now()
    if (now - lastRun >= limit) {
      lastRun = now
      fn(...args)
    }
  }
}

