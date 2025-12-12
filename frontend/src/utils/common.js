/**
 * 通用工具函数
 */

/**
 * 格式化日期时间
 * @param {string|Date} date 日期
 * @param {string} format 格式（默认：YYYY-MM-DD HH:mm:ss）
 * @returns {string}
 */
export function formatDateTime(date, format = 'YYYY-MM-DD HH:mm:ss') {
  if (!date) return ''
  
  const d = new Date(date)
  if (isNaN(d.getTime())) return ''
  
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  
  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 格式化文件大小
 * @param {number} bytes 字节数
 * @returns {string}
 */
export function formatFileSize(bytes) {
  if (!bytes || bytes === 0) return '0 B'
  
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

/**
 * 截断文本
 * @param {string} text 文本
 * @param {number} length 最大长度
 * @param {string} suffix 后缀
 * @returns {string}
 */
export function truncateText(text, length = 50, suffix = '...') {
  if (!text) return ''
  if (text.length <= length) return text
  return text.substring(0, length) + suffix
}

/**
 * 深拷贝
 * @param {any} obj 要拷贝的对象
 * @returns {any}
 */
export function deepClone(obj) {
  if (obj === null || typeof obj !== 'object') return obj
  if (obj instanceof Date) return new Date(obj.getTime())
  if (obj instanceof Array) return obj.map(item => deepClone(item))
  if (obj instanceof Object) {
    const cloned = {}
    Object.keys(obj).forEach(key => {
      cloned[key] = deepClone(obj[key])
    })
    return cloned
  }
}

/**
 * 防抖搜索
 * @param {Function} searchFn 搜索函数
 * @param {number} delay 延迟时间（毫秒）
 * @returns {Function}
 */
export function createDebouncedSearch(searchFn, delay = 300) {
  let timeout
  return function debouncedSearch(...args) {
    clearTimeout(timeout)
    timeout = setTimeout(() => {
      searchFn(...args)
    }, delay)
  }
}

/**
 * 生成唯一 ID
 * @returns {string}
 */
export function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).substr(2)
}

/**
 * 检查是否为空值
 * @param {any} value 值
 * @returns {boolean}
 */
export function isEmpty(value) {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  if (typeof value === 'object') return Object.keys(value).length === 0
  return false
}

/**
 * 获取对象属性值（支持嵌套路径）
 * @param {Object} obj 对象
 * @param {string} path 路径，如 'user.name' 或 'user[0].name'
 * @param {any} defaultValue 默认值
 * @returns {any}
 */
export function get(obj, path, defaultValue = undefined) {
  const keys = path.replace(/\[(\d+)\]/g, '.$1').split('.')
  let result = obj
  for (const key of keys) {
    if (result === null || result === undefined) return defaultValue
    result = result[key]
  }
  return result === undefined ? defaultValue : result
}

/**
 * 设置对象属性值（支持嵌套路径）
 * @param {Object} obj 对象
 * @param {string} path 路径
 * @param {any} value 值
 */
export function set(obj, path, value) {
  const keys = path.replace(/\[(\d+)\]/g, '.$1').split('.')
  const lastKey = keys.pop()
  let current = obj
  
  for (const key of keys) {
    if (!(key in current) || typeof current[key] !== 'object') {
      current[key] = {}
    }
    current = current[key]
  }
  
  current[lastKey] = value
}

