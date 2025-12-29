/**
 * 统一日志工具
 * 支持根据环境变量控制日志输出级别
 * 
 * 使用方式：
 * import { logger } from '@/utils/logger'
 * logger.debug('调试信息')
 * logger.info('普通信息')
 * logger.warn('警告信息')
 * logger.error('错误信息')
 */

// 日志级别枚举
const LogLevel = {
  DEBUG: 0,
  INFO: 1,
  WARN: 2,
  ERROR: 3,
  NONE: 4
}

// 从环境变量获取日志级别，默认为 WARN（生产环境只显示警告和错误）
const getLogLevel = () => {
  const envLevel = import.meta.env.VITE_LOG_LEVEL?.toUpperCase()
  
  // 使用 Map 优化查找性能
  const levelMap = {
    'DEBUG': LogLevel.DEBUG,
    'INFO': LogLevel.INFO,
    'WARN': LogLevel.WARN,
    'ERROR': LogLevel.ERROR,
    'NONE': LogLevel.NONE
  }
  
  if (envLevel && levelMap[envLevel] !== undefined) {
    return levelMap[envLevel]
  }
  
  // 默认：开发环境为 DEBUG，生产环境为 WARN
  return import.meta.env.DEV ? LogLevel.DEBUG : LogLevel.WARN
}

const currentLogLevel = getLogLevel()

// 检查是否应该输出日志
const shouldLog = (level) => {
  return level >= currentLogLevel
}

// 创建日志对象
export const logger = {
  debug: (...args) => {
    if (shouldLog(LogLevel.DEBUG)) {
      console.debug('[DEBUG]', ...args)
    }
  },
  
  info: (...args) => {
    if (shouldLog(LogLevel.INFO)) {
      console.info('[INFO]', ...args)
    }
  },
  
  warn: (...args) => {
    if (shouldLog(LogLevel.WARN)) {
      console.warn('[WARN]', ...args)
    }
  },
  
  error: (...args) => {
    if (shouldLog(LogLevel.ERROR)) {
      console.error('[ERROR]', ...args)
    }
  },
  
  // 兼容原有 console.log 的使用
  log: (...args) => {
    if (shouldLog(LogLevel.DEBUG)) {
      console.log('[LOG]', ...args)
    }
  }
}

// 导出日志级别常量（供外部使用）
export { LogLevel }

