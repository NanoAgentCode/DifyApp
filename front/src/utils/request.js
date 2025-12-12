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

// 响应拦截器
request.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    // 处理401未授权错误
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      // 清除 token 验证缓存
      if (window.clearTokenCache) {
        window.clearTokenCache()
      }
      const errorMessage = error.response.data?.error || '登录已过期，请重新登录'
      ElMessage.error(errorMessage)
      if (router.currentRoute.value.path !== '/login' && router.currentRoute.value.path !== '/register') {
        router.push('/login')
      }
      return Promise.reject(error)
    }
    // 处理403禁止访问错误（用户被禁用或待审核）
    if (error.response && error.response.status === 403) {
      const errorMessage = error.response.data?.error || '访问被拒绝'
      // 检查是否是用户状态相关的错误
      if (errorMessage.includes('禁用') || errorMessage.includes('待审核')) {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        // 清除 token 验证缓存
        if (window.clearTokenCache) {
          window.clearTokenCache()
        }
        ElMessage.error(errorMessage)
        if (router.currentRoute.value.path !== '/login' && router.currentRoute.value.path !== '/register') {
          router.push('/login')
        }
      } else {
        ElMessage.error(errorMessage)
      }
      return Promise.reject(error)
    }
    // 处理超时错误
    if (error.code === 'ECONNABORTED' || error.message.includes('timeout')) {
      error.message = '请求超时，请稍后重试。如果Workflow任务需要较长时间，请使用流式接口。'
    }
    // 不在这里显示错误消息，让调用方处理
    // 这样可以显示更详细的错误信息
    return Promise.reject(error)
  }
)

export default request

