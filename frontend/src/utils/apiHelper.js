/**
 * API 辅助工具函数
 * 提供统一的错误处理和消息提示
 */
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 统一处理 API 调用
 * @param {Promise} apiCall API 调用 Promise
 * @param {Object} options 配置选项
 * @param {string} options.successMessage 成功消息
 * @param {string} options.errorMessage 错误消息
 * @param {boolean} options.showSuccess 是否显示成功消息
 * @param {boolean} options.showError 是否显示错误消息
 * @param {Function} options.onSuccess 成功回调
 * @param {Function} options.onError 错误回调
 * @returns {Promise}
 */
export async function handleApiCall(apiCall, options = {}) {
  const {
    successMessage = '',
    errorMessage = '操作失败',
    showSuccess = true,
    showError = true,
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
    const message = error?.response?.data?.error || error?.message || errorMessage
    if (showError) {
      ElMessage.error(message)
    }
    if (onError) {
      onError(error, message)
    }
    return { success: false, error, message }
  }
}

/**
 * 确认删除操作
 * @param {string} message 确认消息
 * @param {string} title 标题
 * @returns {Promise<boolean>}
 */
export function confirmDelete(message = '确定要删除吗？', title = '提示') {
  return ElMessageBox.confirm(message, title, {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
}

/**
 * 批量操作确认
 * @param {number} count 操作数量
 * @param {string} action 操作名称
 * @returns {Promise<boolean>}
 */
export function confirmBatchAction(count, action = '删除') {
  return confirmDelete(`确定要${action}选中的 ${count} 项吗？`, '批量操作确认')
}

/**
 * 处理删除操作
 * @param {Function} deleteApi 删除 API 函数
 * @param {string|number} id 要删除的 ID
 * @param {Object} options 配置选项
 * @returns {Promise}
 */
export async function handleDelete(deleteApi, id, options = {}) {
  const {
    confirmMessage = '确定要删除吗？',
    successMessage = '删除成功',
    onSuccess
  } = options

  try {
    await confirmDelete(confirmMessage)
    return await handleApiCall(deleteApi(id), {
      successMessage,
      onSuccess
    })
  } catch (error) {
    // 用户取消操作
    if (error === 'cancel') {
      return { success: false, cancelled: true }
    }
    throw error
  }
}

/**
 * 处理批量删除操作
 * @param {Function} deleteApi 删除 API 函数
 * @param {Array} ids 要删除的 ID 数组
 * @param {Object} options 配置选项
 * @returns {Promise}
 */
export async function handleBatchDelete(deleteApi, ids, options = {}) {
  const {
    confirmMessage,
    successMessage = '批量删除成功',
    onSuccess
  } = options

  try {
    await confirmBatchAction(ids.length, '删除')
    
    const results = await Promise.allSettled(
      ids.map(id => deleteApi(id))
    )
    
    const successCount = results.filter(r => r.status === 'fulfilled').length
    const failCount = results.filter(r => r.status === 'rejected').length
    
    if (failCount === 0) {
      ElMessage.success(successMessage || `成功删除 ${successCount} 项`)
      if (onSuccess) {
        onSuccess(results)
      }
      return { success: true, results }
    } else {
      ElMessage.warning(`成功删除 ${successCount} 项，失败 ${failCount} 项`)
      return { success: false, results }
    }
  } catch (error) {
    if (error === 'cancel') {
      return { success: false, cancelled: true }
    }
    throw error
  }
}

