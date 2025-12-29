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

// 获取完整的 API Base URL
// 开发环境且未配置环境变量时，返回空字符串（使用 Vite 代理）
// 生产环境或配置了环境变量时，返回配置的地址
export function getBaseURL() {
  // 如果配置了环境变量，直接使用
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL
  }
  
  // 开发环境：使用空字符串走 Vite 代理
  if (IS_DEV) {
    return ''
  }
  
  // 生产环境：使用默认地址
  return defaultBaseURL
}

