/**
 * 性能监控工具
 * 用于监控和优化前端性能
 */

/**
 * 性能监控类
 */
class PerformanceMonitor {
  constructor() {
    this.metrics = new Map()
    this.observers = []
    this.isSupported = 'performance' in window
  }

  /**
   * 开始计时
   * @param {string} name - 计时器名称
   */
  start(name) {
    if (!this.isSupported) return
    this.metrics.set(name, {
      start: performance.now(),
      end: null,
      duration: null
    })
  }

  /**
   * 结束计时并记录
   * @param {string} name - 计时器名称
   * @returns {number|null} 持续时间（毫秒）
   */
  end(name) {
    if (!this.isSupported) return null
    const metric = this.metrics.get(name)
    if (!metric) return null

    metric.end = performance.now()
    metric.duration = metric.end - metric.start

    // 记录到性能日志
    if (metric.duration > 100) {
      console.warn(`性能警告: ${name} 耗时 ${metric.duration.toFixed(2)}ms`)
    }

    return metric.duration
  }

  /**
   * 获取性能指标
   * @param {string} name - 计时器名称
   * @returns {Object|null} 性能指标对象
   */
  getMetric(name) {
    return this.metrics.get(name) || null
  }

  /**
   * 获取所有性能指标
   * @returns {Array} 所有性能指标
   */
  getAllMetrics() {
    return Array.from(this.metrics.entries()).map(([name, metric]) => ({
      name,
      ...metric
    }))
  }

  /**
   * 清除性能指标
   * @param {string} name - 计时器名称（可选，不传则清除所有）
   */
  clear(name) {
    if (name) {
      this.metrics.delete(name)
    } else {
      this.metrics.clear()
    }
  }

  /**
   * 监控页面加载性能
   */
  observePageLoad() {
    if (!this.isSupported) return

    window.addEventListener('load', () => {
      // 等待一段时间确保所有资源加载完成
      setTimeout(() => {
        const perfData = performance.getEntriesByType('navigation')[0]
        if (perfData) {
          const metrics = {
            dns: perfData.domainLookupEnd - perfData.domainLookupStart,
            tcp: perfData.connectEnd - perfData.connectStart,
            ttfb: perfData.responseStart - perfData.requestStart,
            download: perfData.responseEnd - perfData.responseStart,
            domParse: perfData.domContentLoadedEventEnd - perfData.responseEnd,
            domReady: perfData.domContentLoadedEventEnd - perfData.fetchStart,
            load: perfData.loadEventEnd - perfData.fetchStart
          }

          console.log('页面加载性能指标:', metrics)

          // 检查关键性能指标
          if (metrics.ttfb > 600) {
            console.warn('首字节时间过长:', metrics.ttfb.toFixed(2) + 'ms')
          }
          if (metrics.domReady > 2000) {
            console.warn('DOM准备时间过长:', metrics.domReady.toFixed(2) + 'ms')
          }
          if (metrics.load > 3000) {
            console.warn('页面加载时间过长:', metrics.load.toFixed(2) + 'ms')
          }
        }
      }, 0)
    })
  }

  /**
   * 监控资源加载性能
   */
  observeResources() {
    if (!this.isSupported || !PerformanceObserver) return

    const observer = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        if (entry.duration > 1000) {
          console.warn('资源加载缓慢:', {
            name: entry.name,
            duration: entry.duration.toFixed(2) + 'ms',
            size: (entry.transferSize / 1024).toFixed(2) + 'KB'
          })
        }
      }
    })

    observer.observe({ entryTypes: ['resource'] })
    this.observers.push(observer)
  }

  /**
   * 监控长任务
   */
  observeLongTasks() {
    if (!this.isSupported || !PerformanceObserver) return

    const observer = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        console.warn('检测到长任务:', {
          duration: entry.duration.toFixed(2) + 'ms',
          startTime: entry.startTime.toFixed(2) + 'ms'
        })
      }
    })

    try {
      observer.observe({ entryTypes: ['longtask'] })
      this.observers.push(observer)
    } catch (e) {
      // 某些浏览器可能不支持 longtask
      console.debug('Long task monitoring not supported')
    }
  }

  /**
   * 监控布局偏移（CLS）
   */
  observeLayoutShift() {
    if (!this.isSupported || !PerformanceObserver) return

    let clsValue = 0
    let clsEntries = []

    const observer = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        if (!entry.hadRecentInput) {
          clsValue += entry.value
          clsEntries.push(entry)
        }
      }

      if (clsValue > 0.1) {
        console.warn('累积布局偏移过大 (CLS):', clsValue.toFixed(3))
      }
    })

    try {
      observer.observe({ entryTypes: ['layout-shift'] })
      this.observers.push(observer)
    } catch (e) {
      console.debug('Layout shift monitoring not supported')
    }
  }

  /**
   * 测量内存使用（Chrome专用）
   */
  measureMemory() {
    if (!this.isSupported || !performance.memory) {
      console.debug('Memory API not supported')
      return null
    }

    const memory = performance.memory
    return {
      used: (memory.usedJSHeapSize / 1048576).toFixed(2) + 'MB',
      total: (memory.totalJSHeapSize / 1048576).toFixed(2) + 'MB',
      limit: (memory.jsHeapSizeLimit / 1048576).toFixed(2) + 'MB',
      usage: ((memory.usedJSHeapSize / memory.jsHeapSizeLimit) * 100).toFixed(2) + '%'
    }
  }

  /**
   * 清理所有观察者
   */
  disconnect() {
    this.observers.forEach(observer => observer.disconnect())
    this.observers = []
  }

  /**
   * 生成性能报告
   * @returns {Object} 性能报告
   */
  generateReport() {
    const report = {
      metrics: this.getAllMetrics(),
      memory: this.measureMemory(),
      timestamp: new Date().toISOString()
    }

    console.log('性能报告:', report)
    return report
  }
}

// 创建全局实例
const performanceMonitor = new PerformanceMonitor()

// 自动启动监控
if (typeof window !== 'undefined') {
  // 在开发环境下启用所有监控
  if (process.env.NODE_ENV === 'development') {
    performanceMonitor.observePageLoad()
    performanceMonitor.observeResources()
    performanceMonitor.observeLongTasks()
    performanceMonitor.observeLayoutShift()
  }
}

// 导出工具函数
export const perf = {
  start: (name) => performanceMonitor.start(name),
  end: (name) => performanceMonitor.end(name),
  getMetric: (name) => performanceMonitor.getMetric(name),
  getAllMetrics: () => performanceMonitor.getAllMetrics(),
  clear: (name) => performanceMonitor.clear(name),
  measureMemory: () => performanceMonitor.measureMemory(),
  generateReport: () => performanceMonitor.generateReport()
}

/**
 * 防抖函数（性能优化）
 * @param {Function} func - 要防抖的函数
 * @param {number} wait - 等待时间（毫秒）
 * @returns {Function} 防抖后的函数
 */
export function debounce(func, wait = 300) {
  let timeout
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout)
      func(...args)
    }
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  }
}

/**
 * 节流函数（性能优化）
 * @param {Function} func - 要节流的函数
 * @param {number} limit - 时间限制（毫秒）
 * @returns {Function} 节流后的函数
 */
export function throttle(func, limit = 300) {
  let inThrottle
  return function executedFunction(...args) {
    if (!inThrottle) {
      func(...args)
      inThrottle = true
      setTimeout(() => (inThrottle = false), limit)
    }
  }
}

/**
 * 批量处理函数（性能优化）
 * 将多个操作合并为一次执行
 * @param {Function} func - 要批量处理的函数
 * @param {number} delay - 延迟时间（毫秒）
 * @returns {Object} 批量处理器
 */
export function createBatchProcessor(func, delay = 100) {
  let batch = []
  let timeout = null

  return {
    add: (item) => {
      batch.push(item)
      if (!timeout) {
        timeout = setTimeout(() => {
          if (batch.length > 0) {
            func(batch)
            batch = []
          }
          timeout = null
        }, delay)
      }
    },
    flush: () => {
      if (batch.length > 0) {
        func(batch)
        batch = []
        if (timeout) {
          clearTimeout(timeout)
          timeout = null
        }
      }
    }
  }
}

export default performanceMonitor
