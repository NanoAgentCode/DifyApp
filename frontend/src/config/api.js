/**
 * API 配置
 * 
 * 配置后端 API 地址的方式：
 * 1. 通过环境变量 VITE_API_BASE_URL（推荐）
 * 2. 直接修改此文件中的 defaultBaseURL
 */

// 默认后端地址（当环境变量未设置时使用）
const defaultBaseURL = 'http://106.54.124.170:9090'

// 从环境变量获取 API 地址，如果未设置则使用默认值
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || defaultBaseURL

// 开发环境标识
export const IS_DEV = import.meta.env.DEV

// 检测是否在Tauri环境中运行
export const IS_TAURI = typeof window !== 'undefined' && window.__TAURI__ !== undefined

// 获取完整的 API Base URL
// 开发环境且未配置环境变量时，返回空字符串（使用 Vite 代理）
// 生产环境或配置了环境变量时，返回配置的地址
// Tauri打包后必须使用完整URL
export function getBaseURL() {
  // 如果配置了环境变量，直接使用
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL
  }
  
  // Tauri环境：必须使用完整URL
  if (IS_TAURI) {
    return defaultBaseURL
  }
  
  // 开发环境：使用空字符串走 Vite 代理
  if (IS_DEV) {
    return ''
  }
  
  // 生产环境：使用默认地址
  return defaultBaseURL
}

/**
 * 获取完整的API URL（用于fetch请求，特别是流式响应）
 * Tauri打包后需要完整URL，不能使用相对路径
 * @param {string} path - API路径（如 '/api/chat/stream'）
 * @returns {string} 完整的URL
 */
export function getFullAPIUrl(path) {
  const baseURL = getBaseURL()
  // 如果baseURL为空（开发环境），直接返回path（使用Vite代理）
  if (!baseURL) {
    return path
  }
  // 确保path以/开头
  const normalizedPath = path.startsWith('/') ? path : '/' + path
  // 确保baseURL不以/结尾
  const normalizedBase = baseURL.endsWith('/') ? baseURL.slice(0, -1) : baseURL
  return normalizedBase + normalizedPath
}

