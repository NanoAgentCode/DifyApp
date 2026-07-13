import { logger } from '@/utils/logger'

export const formatWorkflowResult = (result) => {
  if (typeof result === 'string') {
    return result
  }
  return JSON.stringify(result, null, 2)
}

// 提取文件列表
export const extractWorkflowFiles = (result, fileUrlPrefix) => {
  const files = []
  if (!result || typeof result !== 'object') return files

  // 检查 data.outputs.body（新格式：数组）
  if (result.data && result.data.outputs && result.data.outputs.body) {
    const bodyContent = result.data.outputs.body

    // 如果是数组，遍历每个文件对象（优化：使用for循环）
    if (Array.isArray(bodyContent)) {
      for (let i = 0; i < bodyContent.length; i++) {
        const fileItem = bodyContent[i]
        if (fileItem && fileItem.url) {
          // 提取URL并拼接前缀
          const url = fileItem.url
          const fullUrl = url.startsWith('http') ? url : `${fileUrlPrefix}${url}`

          // 构建文件信息对象
          const fileInfo = {
            url: url,
            fullUrl: fullUrl,
            filename: fileItem.filename || fileItem.saved_filename || 'download',
            saved_filename: fileItem.saved_filename,
            type: fileItem.mime_type || fileItem.type || 'application/octet-stream',
            mime_type: fileItem.mime_type || fileItem.type || 'application/octet-stream',
            extension: fileItem.extension || '',
            file_size: fileItem.size || fileItem.file_size,
            download_url: url,
            // 保留其他字段
            dify_model_identity: fileItem.dify_model_identity,
            related_id: fileItem.related_id,
            transfer_method: fileItem.transfer_method
          }

          logger.debug('提取到文件信息')
          files.push(fileInfo)
        }
      }
    } else if (typeof bodyContent === 'string') {
      // 兼容旧格式：body 可能是字符串
      let bodyStr = bodyContent.replace(/^["']|["']$/g, '').trim()

      if (bodyStr) {
        // 尝试解析为JSON（可能是JSON字符串）
        try {
          const parsedBody = JSON.parse(bodyStr)

          // 检查是否是文件信息对象（包含 download_url）
          if (parsedBody && parsedBody.download_url) {
            const downloadUrl = parsedBody.download_url
            const fullUrl = downloadUrl.startsWith('http') ? downloadUrl : `${fileUrlPrefix}${downloadUrl}`

            // 判断文件类型
            const urlLower = downloadUrl.toLowerCase()
            const isPdfFile = urlLower.includes('.pdf') || downloadUrl.endsWith('.pdf')
            const isHtmlFile = urlLower.includes('.html') || urlLower.includes('.htm')
            const isImageFile = /\.(jpg|jpeg|png|gif|webp|svg|bmp)$/i.test(downloadUrl)

            let fileType = 'application/octet-stream'
            let extension = ''

            if (isPdfFile) {
              fileType = 'application/pdf'
              extension = '.pdf'
            } else if (isHtmlFile) {
              fileType = 'text/html'
              extension = '.html'
            } else if (isImageFile) {
              const match = downloadUrl.match(/\.([^.]+)$/i)
              extension = match ? `.${match[1]}` : ''
              fileType = `image/${extension.replace('.', '')}`
            }

            const fileInfo = {
              url: downloadUrl,
              fullUrl: fullUrl,
              filename: parsedBody.original_filename || parsedBody.saved_filename || 'download',
              saved_filename: parsedBody.saved_filename,
              type: fileType,
              mime_type: fileType,
              extension: extension,
              file_size: parsedBody.file_size,
              message: parsedBody.message,
              download_url: downloadUrl
            }

            logger.debug('添加文件到列表')
            files.push(fileInfo)
          } else {
            // 如果不是文件信息对象，可能是直接的URL字符串
            const fullUrl = bodyStr.startsWith('http') ? bodyStr : `${fileUrlPrefix}${bodyStr}`

            // 判断URL类型
            const urlLower = bodyStr.toLowerCase()
            const isPdfFile = urlLower.includes('.pdf')
            const isHtmlFile = urlLower.includes('.html') || urlLower.includes('.htm')

            let fileType = 'text/html'
            let extension = '.html'
            let filename = 'output.html'

            if (isPdfFile) {
              fileType = 'application/pdf'
              extension = '.pdf'
              filename = 'output.pdf'
            }

            files.push({
              url: bodyStr,
              fullUrl: fullUrl,
              filename: filename,
              type: fileType,
              mime_type: fileType,
              extension: extension
            })
          }
        } catch (e) {
          // 如果不是JSON，当作普通URL处理
          const fullUrl = bodyStr.startsWith('http') ? bodyStr : `${fileUrlPrefix}${bodyStr}`
          const urlLower = bodyStr.toLowerCase()
          const isPdfFile = urlLower.includes('.pdf')

          files.push({
            url: bodyStr,
            fullUrl: fullUrl,
            filename: isPdfFile ? 'output.pdf' : 'output.html',
            type: isPdfFile ? 'application/pdf' : 'text/html',
            mime_type: isPdfFile ? 'application/pdf' : 'text/html',
            extension: isPdfFile ? '.pdf' : '.html'
          })
        }
      }
    }
  }

  // 检查 data.outputs.files（兼容旧格式，优化：使用for循环）
  if (result.data && result.data.outputs && result.data.outputs.files) {
    const filesArray = result.data.outputs.files
    for (let i = 0; i < filesArray.length; i++) {
      const file = filesArray[i]
      if (file && file.url) {
        const fullUrl = file.url.startsWith('http') ? file.url : `${fileUrlPrefix}${file.url}`
        files.push({
          ...file,
          fullUrl
        })
      }
    }
  }

  return files
}

// 检查是否有可预览的文件（已废弃，使用computed替代）
export const hasWorkflowPreviewableFiles = (result) => {
  return extractWorkflowFiles(result, fileUrlPrefix).length > 0
}

// 判断是否为图片
export const isWorkflowImage = (file) => {
  const type = (file.type || file.mime_type || '').toLowerCase()
  const extension = (file.extension || '').toLowerCase()
  return type.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(extension.replace('.', ''))
}

// 判断是否为HTML
export const isWorkflowHtml = (file) => {
  const type = (file.type || file.mime_type || '').toLowerCase()
  const extension = (file.extension || '').toLowerCase()
  return type.includes('html') || extension === '.html' || extension === '.htm'
}

// 判断是否为PDF
export const isWorkflowPdf = (file) => {
  const type = (file.type || file.mime_type || '').toLowerCase()
  const extension = (file.extension || '').toLowerCase()
  return type.includes('pdf') || extension === '.pdf'
}

// 判断是否为DOCX
export const isWorkflowDocx = (file) => {
  const type = (file.type || file.mime_type || '').toLowerCase()
  const extension = (file.extension || '').toLowerCase()
  return extension === '.docx' || type.includes('officedocument.wordprocessingml') || type.includes('application/vnd.openxmlformats-officedocument.wordprocessingml')
}
