import { ElMessage, ElMessageBox } from 'element-plus'

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

    // 优先从 response.data 中获取错误信息
    if (error.response?.data) {
      const data = error.response.data
      
      // 处理验证错误（字段错误）
      if (data.errors && typeof data.errors === 'object') {
        const errorFields = Object.keys(data.errors)
        if (errorFields.length > 0) {
          const firstField = errorFields[0]
          const firstError = data.errors[firstField]
          return `${firstField}: ${firstError}`
        }
      }
      
      // 处理标准错误消息字段
      if (data.error) return data.error
      if (data.message) return data.message
      if (data.msg) return data.msg
      
      // 处理 HTTP 状态码错误
      if (error.response.status) {
        return `请求失败 (${error.response.status})`
      }
    }
    
    // 处理网络错误或超时错误
    if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      return '请求超时，请稍后重试。如果任务需要较长时间，请使用流式接口。'
    }
    
    // 处理其他错误消息
    if (error.message) return error.message
    
    // 处理字符串错误
    if (typeof error === 'string') return error
    
    return defaultMessage
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
      logError = true
    } = options

    const errorMessage = extractErrorMessage(error, defaultMessage)

    if (showMessage) {
      ElMessage.error(errorMessage)
    }

    if (logError) {
      console.error('操作失败:', errorMessage, error)
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
    let errorMessage = defaultMessage

    if (error.response) {
      // HTTP 错误响应
      errorMessage = extractErrorMessage(error, defaultMessage)
    } else if (error.message) {
      // 网络错误或其他错误
      errorMessage = error.message
    }

    ElMessage.error(errorMessage)
    console.error('流式响应失败:', errorMessage, error)

    return errorMessage
  }

  return {
    extractErrorMessage,
    handleError,
    handleApiCall,
    confirmDelete,
    confirmBatchDelete,
    handleStreamError
  }
}

