import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getBaseURL } from '@/config/api'

const request = axios.create({
  baseURL: getBaseURL(),
  // 设置超时时间为10分钟（600000毫秒），以支持长时间运行的Workflow任务
  timeout: 600000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 确保Content-Type正确设置
    if (config.method === 'post' || config.method === 'put') {
      if (!config.headers['Content-Type']) {
        config.headers['Content-Type'] = 'application/json'
      }
    }
    // 添加JWT Token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 清理认证信息并跳转登录
const clearAuthAndRedirect = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  if (window.clearTokenCache) {
    window.clearTokenCache()
  }
  const currentPath = router.currentRoute.value.path
  if (currentPath !== '/login' && currentPath !== '/register') {
    router.push('/login')
  }
}

// 响应拦截器
request.interceptors.response.use(
  response => response.data,
  error => {
    const status = error.response?.status
    const errorMessage = error.response?.data?.error
    
    // 处理401未授权错误
    if (status === 401) {
      clearAuthAndRedirect()
      ElMessage.error(errorMessage || '登录已过期，请重新登录')
      return Promise.reject(error)
    }
    
    // 处理403禁止访问错误
    if (status === 403) {
      const message = errorMessage || '访问被拒绝'
      if (message.includes('禁用') || message.includes('待审核')) {
        clearAuthAndRedirect()
      }
      ElMessage.error(message)
      return Promise.reject(error)
    }
    
    // 处理超时错误
    if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      error.message = '请求超时，请稍后重试。如果Workflow任务需要较长时间，请使用流式接口。'
    }
    
    return Promise.reject(error)
  }
)

export default request

