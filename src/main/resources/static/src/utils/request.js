import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '',
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

