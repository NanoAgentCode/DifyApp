import { ElMessage, ElMessageBox } from 'element-plus'
import { logger } from '@/utils/logger'

/**
 * 统一错误处理 Composable
 * 提供统一的错误消息提取、显示和处理逻辑
 */
export function useErrorHandler() {
  /**
   * 从错误对象中提取错误消息
   * @param {Error} error 错误对象
   * @param {string} defaultMessage 默认错误消息
   * @returns {string} 错误消息
   */
  const extractErrorMessage = (error, defaultMessage = '操作失败') => {
    if (!error) return defaultMessage

    // 优先从 response.data 中获取错误信息（后端统一格式）
    if (error.response?.data) {
      const data = error.response.data
      
      // 处理验证错误（字段错误）- 后端返回的errors对象
      if (data.errors && typeof data.errors === 'object') {
        const errorFields = Object.keys(data.errors)
        if (errorFields.length > 0) {
          // 如果有多个错误，合并显示
          if (errorFields.length === 1) {
            const firstField = errorFields[0]
            const firstError = data.errors[firstField]
            // 尝试将字段名转换为中文
            const fieldName = translateFieldName(firstField)
            return `${fieldName}: ${firstError}`
          } else {
            // 多个错误，显示第一个并提示还有更多
            const firstField = errorFields[0]
            const firstError = data.errors[firstField]
            const fieldName = translateFieldName(firstField)
            return `${fieldName}: ${firstError}（还有 ${errorFields.length - 1} 个错误）`
          }
        }
      }
      
      // 处理标准错误消息字段（按优先级）
      if (data.message) return data.message  // ApiResponse.message 字段
      if (data.error) return data.error      // 兼容旧格式
      if (data.msg) return data.msg           // 兼容其他格式
      
      // 处理 HTTP 状态码错误
      const status = error.response.status
      if (status) {
        return getHttpStatusMessage(status, data.message || data.error)
      }
    }
    
    // 处理网络错误
    if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      return '请求超时，请稍后重试。如果任务需要较长时间，请使用流式接口。'
    }
    
    // 处理网络连接错误
    if (error.message) {
      if (error.message.includes('Network Error') || 
          error.message.includes('Failed to fetch') || 
          error.message.includes('ECONNREFUSED') ||
          error.message.includes('ERR_CONNECTION_REFUSED')) {
        return '无法连接到服务器，请检查网络连接和后端服务是否正常运行。'
      }
      if (error.message.includes('timeout')) {
        return '请求超时，请稍后重试'
      }
      // 过滤技术性错误信息
      if (error.message.includes('at ') || error.message.includes('Error: ')) {
        return defaultMessage
      }
      return error.message
    }
    
    // 处理字符串错误
    if (typeof error === 'string') return error
    
    return defaultMessage
  }

  /**
   * 将字段名转换为中文（常见字段）
   * @param {string} fieldName 字段名
   * @returns {string} 中文字段名
   */
  const translateFieldName = (fieldName) => {
    const fieldMap = {
      'username': '用户名',
      'password': '密码',
      'email': '邮箱',
      'name': '名称',
      'title': '标题',
      'description': '描述',
      'url': 'URL',
      'apiKey': 'API密钥',
      'host': '主机',
      'port': '端口',
      'database': '数据库',
      'type': '类型',
      'status': '状态',
      'role': '角色',
      'modelId': '模型ID',
      'question': '问题',
      'content': '内容'
    }
    return fieldMap[fieldName] || fieldName
  }

  /**
   * 根据HTTP状态码获取友好的错误消息
   * @param {number} status HTTP状态码
   * @param {string} customMessage 自定义消息
   * @returns {string} 错误消息
   */
  const getHttpStatusMessage = (status, customMessage) => {
    if (customMessage) return customMessage
    
    const statusMessages = {
      400: '请求参数错误',
      401: '登录已过期，请重新登录',
      403: '没有权限执行此操作',
      404: '请求的资源不存在',
      408: '请求超时，请稍后重试',
      409: '数据冲突，请检查输入',
      422: '数据验证失败',
      429: '请求过于频繁，请稍后重试',
      500: '服务器内部错误，请稍后重试',
      502: '网关错误，请稍后重试',
      503: '服务暂时不可用，请稍后重试',
      504: '网关超时，请稍后重试'
    }
    
    return statusMessages[status] || `请求失败 (${status})`
  }

  /**
   * 显示错误消息
   * @param {Error|string} error 错误对象或错误消息
   * @param {string} defaultMessage 默认错误消息
   * @param {Object} options 配置选项
   * @param {boolean} options.showMessage 是否显示消息（默认 true）
   * @param {boolean} options.logError 是否记录错误到控制台（默认 true）
   */
  const handleError = (error, defaultMessage = '操作失败', options = {}) => {
    const {
      showMessage = true,
      logError = false
    } = options

    const errorMessage = extractErrorMessage(error, defaultMessage)

    if (showMessage) {
      ElMessage.error(errorMessage)
    }

    if (logError) {
      logger.error('操作失败:', errorMessage, error)
    }

    return errorMessage
  }

  /**
   * 处理 API 调用，统一错误处理
   * @param {Promise} apiCall API 调用 Promise
   * @param {Object} options 配置选项
   * @param {string} options.successMessage 成功消息
   * @param {string} options.errorMessage 错误消息
   * @param {boolean} options.showSuccess 是否显示成功消息（默认 true）
   * @param {boolean} options.showError 是否显示错误消息（默认 true）
   * @param {boolean} options.logError 是否记录错误（默认 true）
   * @param {Function} options.onSuccess 成功回调
   * @param {Function} options.onError 错误回调
   * @returns {Promise<{success: boolean, data?: any, error?: Error, message?: string}>}
   */
  const handleApiCall = async (apiCall, options = {}) => {
    const {
      successMessage = '',
      errorMessage = '操作失败',
      showSuccess = true,
      showError = true,
      logError = true,
      onSuccess,
      onError
    } = options

    try {
      const data = await apiCall
      
      if (showSuccess && successMessage) {
        ElMessage.success(successMessage)
      }
      
      if (onSuccess) {
        onSuccess(data)
      }
      
      return { success: true, data }
    } catch (error) {
      const message = handleError(error, errorMessage, {
        showMessage: showError,
        logError
      })
      
      if (onError) {
        onError(error, message)
      }
      
      return { success: false, error, message }
    }
  }

  /**
   * 确认删除操作
   * @param {string} message 确认消息
   * @param {string} title 标题（默认 '提示'）
   * @returns {Promise<boolean>} 用户是否确认
   */
  const confirmDelete = async (message, title = '提示') => {
    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      return true
    } catch (error) {
      // 用户取消操作
      return false
    }
  }

  /**
   * 确认批量删除操作
   * @param {number} count 删除数量
   * @param {string} itemName 项目名称（如 '会话'、'知识库'）
   * @returns {Promise<boolean>} 用户是否确认
   */
  const confirmBatchDelete = async (count, itemName = '项目') => {
    return await confirmDelete(
      `确定要删除选中的 ${count} 个${itemName}吗？删除后将无法恢复。`,
      '批量删除确认'
    )
  }

  /**
   * 处理流式响应错误
   * @param {Error} error 错误对象
   * @param {string} defaultMessage 默认错误消息
   * @returns {string} 错误消息
   */
  const handleStreamError = (error, defaultMessage = '流式响应失败') => {
    const errorMessage = error.response 
      ? extractErrorMessage(error, defaultMessage)
      : error.message || defaultMessage

    ElMessage.error(errorMessage)
    
    logger.error('流式响应失败:', errorMessage, error)

    return errorMessage
  }

  return {
    extractErrorMessage,
    handleError,
    handleApiCall,
    confirmDelete,
    confirmBatchDelete,
    handleStreamError,
    translateFieldName,
    getHttpStatusMessage
  }
}

