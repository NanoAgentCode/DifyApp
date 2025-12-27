<template>
  <div class="document-viewer">
    <!-- 工具栏 -->
    <div class="viewer-toolbar">
      <div class="toolbar-left">
        <el-button type="text" @click="$emit('back')">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <span class="document-title">{{ documentInfo?.originalFileName || documentInfo?.fileName || '未命名文档' }}</span>
      </div>
      <div class="toolbar-right">
        <el-button type="text" @click="$emit('favorite')">
          <el-icon><Star /></el-icon>
        </el-button>
        <el-button type="text" @click="$emit('share')">
          <el-icon><Share /></el-icon>
        </el-button>
        <el-button type="text" @click="$emit('export')">
          <el-icon><Download /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 文本选择操作按钮 -->
    <div v-if="selectedText" class="text-selection-popup" :style="selectionPopupStyle">
      <el-button type="success" size="small" @click="handleInterpretText">
        <el-icon><ChatLineRound /></el-icon>
        解读
      </el-button>
      <el-button type="primary" size="small" @click="handleTranslateText">
        <el-icon><DocumentAdd /></el-icon>
        翻译
      </el-button>
      <el-button type="text" size="small" @click="clearSelection" title="取消选择">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>

    <!-- 文档内容区域 -->
    <div class="viewer-content" :style="{ transform: `scale(${zoomLevel / 100})` }" @mouseup="handleTextSelection">
      <!-- PDF文档 -->
      <div v-if="fileType === 'pdf'" class="pdf-container">
        <div v-if="loading" class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>正在加载PDF...</p>
        </div>
        <div v-else-if="pdfSource" class="pdf-viewer-wrapper">
          <vue-pdf-embed
            ref="pdfEmbedRef"
            :source="pdfSource"
            :page="currentPage"
            :textLayer="true"
            class="pdf-embed"
            @rendered="handlePdfRendered"
            @failed="handlePdfFailed"
          />
        </div>
        <div v-else class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
      </div>

      <!-- 图片文档 -->
      <div v-else-if="isImageType" class="image-container">
        <el-image
          v-if="imageUrl"
          :src="imageUrl"
          fit="contain"
          class="document-image"
          :preview-src-list="[imageUrl]"
        />
        <div v-else class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
      </div>

      <!-- Markdown文档 -->
      <div v-else-if="fileType === 'md' || fileType === 'markdown'" class="markdown-container">
        <div v-if="markdownContent" class="markdown-content" v-html="renderedMarkdown"></div>
        <div v-else class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
      </div>

      <!-- 文本文档 -->
      <div v-else-if="fileType === 'txt'" class="text-container">
        <pre v-if="textContent" class="text-content">{{ textContent }}</pre>
        <div v-else class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
      </div>

      <!-- Word文档 (docx) -->
      <div v-else-if="fileType === 'docx' || fileType === 'doc'" class="docx-container">
        <div v-if="docxContent" class="docx-content" v-html="docxContent"></div>
        <div v-else-if="loading" class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>正在转换文档...</p>
        </div>
        <div v-else class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
      </div>

      <!-- 其他类型文档（Excel等） -->
      <div v-else class="other-document-container">
        <div class="unsupported-message">
          <el-icon class="message-icon"><Document /></el-icon>
          <p>该文档类型暂不支持在线预览</p>
          <p class="message-tip">请下载后使用相应软件打开</p>
        </div>
      </div>
    </div>

    <!-- 底部控制栏 -->
    <div class="viewer-controls">
      <div class="controls-left">
        <el-button
          :disabled="currentPage <= 1"
          @click="handlePrevPage"
          size="small"
        >
          <el-icon><ArrowLeft /></el-icon>
          上一页
        </el-button>
        <span class="page-info">
          {{ currentPage }} / {{ totalPages }}
        </span>
        <el-button
          :disabled="currentPage >= totalPages"
          @click="handleNextPage"
          size="small"
        >
          下一页
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
      <div class="controls-right">
        <el-button
          :disabled="zoomLevel <= 50"
          @click="handleZoomOut"
          size="small"
        >
          <el-icon><Minus /></el-icon>
        </el-button>
        <span class="zoom-info">{{ zoomLevel }}%</span>
        <el-button
          :disabled="zoomLevel >= 200"
          @click="handleZoomIn"
          size="small"
        >
          <el-icon><Plus /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Star, Share, Download, Loading, Document, ArrowRight, Minus, Plus, DocumentAdd, Close, ChatLineRound } from '@element-plus/icons-vue'
import { getDocumentContent } from '@/api/documentReader'
import { renderMarkdown } from '@/composables/useMarkdown'
import mammoth from 'mammoth'
import VuePdfEmbed from 'vue-pdf-embed'

const props = defineProps({
  docId: {
    type: Number,
    required: true
  },
  documentInfo: {
    type: Object,
    default: () => ({})
  },
  currentPage: {
    type: Number,
    default: 1
  },
  totalPages: {
    type: Number,
    default: 1
  },
  zoomLevel: {
    type: Number,
    default: 100
  }
})

const emit = defineEmits(['update:currentPage', 'update:zoomLevel', 'update:totalPages', 'back', 'favorite', 'share', 'export', 'textSelected', 'textInterpret'])

// 文本选择相关
const selectedText = ref('')
const selectionPopupStyle = ref({})

const fileType = computed(() => {
  const fileName = props.documentInfo?.originalFileName || props.documentInfo?.fileName || ''
  const extension = fileName.split('.').pop()?.toLowerCase() || ''
  return extension
})

const isImageType = computed(() => {
  return ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(fileType.value)
})

const pdfSource = ref(null)
const pdfEmbedRef = ref(null)
const imageUrl = ref('')
const markdownContent = ref('')
const textContent = ref('')
const docxContent = ref('')
const loading = ref(false)

const renderedMarkdown = computed(() => {
  if (!markdownContent.value) return ''
  return renderMarkdown(markdownContent.value)
})

// 加载文档内容
const loadDocumentContent = async () => {
  if (!props.docId) return
  
  // 重置所有内容
  pdfSource.value = null
  imageUrl.value = ''
  markdownContent.value = ''
  textContent.value = ''
  docxContent.value = ''
  
  loading.value = true
  try {
    const response = await getDocumentContent(props.docId, props.currentPage)
    
    if (fileType.value === 'pdf') {
      // PDF文件，使用vue-pdf-embed显示
      try {
        // response 是 Blob 对象（因为 responseType: 'blob'）
        // vue-pdf-embed 支持 Blob，但为了更好的兼容性，我们创建 Blob URL
        // 注意：Blob URL 不需要认证，因为数据已经在内存中
        if (response instanceof Blob) {
          pdfSource.value = URL.createObjectURL(response)
        } else {
          // 如果不是 Blob，可能是 ArrayBuffer，转换为 Blob
          const blob = new Blob([response], { type: 'application/pdf' })
          pdfSource.value = URL.createObjectURL(blob)
        }
        loading.value = false
      } catch (error) {
        console.error('PDF加载失败:', error)
        loading.value = false
        ElMessage.error('PDF加载失败：' + (error.message || '未知错误'))
      }
    } else if (isImageType.value) {
      // 图片文件，创建blob URL
      const blob = new Blob([response], { type: `image/${fileType.value}` })
      imageUrl.value = URL.createObjectURL(blob)
    } else if (fileType.value === 'md' || fileType.value === 'markdown') {
      // Markdown文件，读取文本内容
      const text = await response.text()
      markdownContent.value = text
    } else if (fileType.value === 'txt') {
      // 文本文件，读取文本内容
      const text = await response.text()
      textContent.value = text
    } else if (fileType.value === 'docx') {
      // Word文档 (docx)，使用mammoth转换为HTML
      const arrayBuffer = await response.arrayBuffer()
      try {
        const result = await mammoth.convertToHtml({ arrayBuffer: arrayBuffer })
        docxContent.value = result.value
        // 如果有警告信息，记录到调试日志（mammoth的警告通常是格式兼容性问题，不影响基本内容显示）
        if (result.messages && result.messages.length > 0) {
          // 过滤掉一些常见的、不影响显示的警告
          const importantMessages = result.messages.filter(msg => {
            const message = msg.message || ''
            // 过滤掉一些常见的、不影响显示的警告类型
            return !message.includes('unimplemented') && 
                   !message.includes('not implemented') &&
                   !message.includes('unsupported')
          })
          // 只在有重要警告时显示，或者使用debug级别
          if (importantMessages.length > 0) {
            console.debug('Docx转换警告:', importantMessages)
          } else {
            console.debug('Docx转换完成，有一些格式兼容性提示（不影响显示）')
          }
        }
      } catch (error) {
        console.error('Docx转换失败:', error)
        docxContent.value = '<p class="docx-error-message">文档转换失败，请下载后使用Word打开</p>'
      }
    } else if (fileType.value === 'doc') {
      // 旧版Word文档 (.doc) 不支持直接预览
      docxContent.value = '<p class="docx-tip-message">.doc格式文档暂不支持在线预览，请下载后使用Word打开</p>'
    }
  } catch (error) {
    console.error('加载文档内容失败:', error)
    if (fileType.value === 'docx' || fileType.value === 'doc') {
      docxContent.value = '<p class="docx-error-message">加载文档失败，请稍后重试</p>'
    }
  } finally {
    loading.value = false
  }
}

// 翻页处理
const handlePrevPage = () => {
  if (props.currentPage > 1) {
    emit('update:currentPage', props.currentPage - 1)
  }
}

const handleNextPage = () => {
  if (props.currentPage < props.totalPages) {
    emit('update:currentPage', props.currentPage + 1)
  }
}

// 缩放处理
const handleZoomIn = () => {
  if (props.zoomLevel < 200) {
    emit('update:zoomLevel', Math.min(props.zoomLevel + 10, 200))
  }
}

const handleZoomOut = () => {
  if (props.zoomLevel > 50) {
    emit('update:zoomLevel', Math.max(props.zoomLevel - 10, 50))
  }
}

// PDF渲染完成回调
const handlePdfRendered = async (info) => {
  console.log('PDF渲染完成:', info)
  loading.value = false
  
  // 尝试从vue-pdf-embed获取总页数
  try {
    if (pdfEmbedRef.value && pdfEmbedRef.value.pdf) {
      const pdf = pdfEmbedRef.value.pdf
      if (pdf && pdf.numPages) {
        emit('update:totalPages', pdf.numPages)
        console.log('PDF总页数:', pdf.numPages)
      }
    } else if (info && info.numPages) {
      emit('update:totalPages', info.numPages)
    }
  } catch (error) {
    console.warn('获取PDF总页数失败:', error)
  }
}

// PDF渲染失败回调
const handlePdfFailed = (error) => {
  console.error('PDF渲染失败:', error)
  loading.value = false
  ElMessage.error('PDF加载失败：' + (error.message || '未知错误'))
}

// 已废弃：将PDF转换为HTML（不再使用）
const convertPdfToHtml_DEPRECATED = async () => {
  if (!pdfDocument.value) {
    console.error('PDF文档对象不存在')
    return
  }
  
  try {
    const numPages = pdfDocument.value.numPages
    console.log(`开始转换PDF，共${numPages}页`)
    const htmlParts = []
    
    // 计算缩放比例（提高缩放比例以获得更好的清晰度）
    const scale = 2.5 // 使用2.5倍缩放，提高清晰度
    
    // 渲染所有页面为图片
    for (let pageNum = 1; pageNum <= numPages; pageNum++) {
      try {
        console.log(`正在转换第${pageNum}页...`)
        // 使用 toRaw 获取原始 PDF 文档对象，避免 Vue 响应式代理
        const rawPdfDocument = toRaw(pdfDocument.value)
        const page = await rawPdfDocument.getPage(pageNum)
        
        if (!page) {
          throw new Error(`无法获取第${pageNum}页`)
        }
        
        // 使用 toRaw 获取原始页面对象
        const rawPage = toRaw(page)
        
        // 获取viewport（不要修改它）
        let viewport = rawPage.getViewport({ scale })
        console.log(`第${pageNum}页原始尺寸: ${viewport.width}x${viewport.height}`)
        
        // 检查Canvas尺寸限制（某些浏览器有最大尺寸限制）
        const maxCanvasSize = 16384 // 大多数浏览器的最大Canvas尺寸
        let finalScale = scale
        if (viewport.width > maxCanvasSize || viewport.height > maxCanvasSize) {
          console.warn(`第${pageNum}页尺寸过大，调整缩放比例`)
          // 重新计算缩放比例，不要修改viewport对象
          const originalViewport = rawPage.getViewport({ scale: 1.0 })
          finalScale = Math.min(
            maxCanvasSize / originalViewport.width,
            maxCanvasSize / originalViewport.height
          )
          viewport = rawPage.getViewport({ scale: finalScale })
          console.log(`调整后尺寸: ${viewport.width}x${viewport.height}, 缩放: ${finalScale}`)
        }
        
        // 创建临时canvas
        const canvas = document.createElement('canvas')
        if (!canvas) {
          throw new Error('无法创建Canvas元素')
        }
        
        const context = canvas.getContext('2d')
        if (!context) {
          throw new Error('无法获取Canvas上下文')
        }
        
        // 设置canvas尺寸（必须在获取context之后）
        canvas.height = viewport.height
        canvas.width = viewport.width
        
        console.log(`Canvas尺寸: ${canvas.width}x${canvas.height}`)
        
        // 渲染页面到canvas
        // 使用正确的渲染上下文配置，不要添加额外的属性
        // 确保 viewport 也是原始对象
        const rawViewport = toRaw(viewport)
        const renderContext = {
          canvasContext: context,
          viewport: rawViewport
        }
        
        // 使用 try-catch 包装渲染任务
        const renderTask = rawPage.render(renderContext)
        
        // 等待渲染完成，并处理可能的错误
        try {
          await renderTask.promise
          console.log(`第${pageNum}页渲染完成`)
        } catch (renderError) {
          console.error(`第${pageNum}页渲染任务失败:`, renderError)
          throw renderError
        }
        
        // 将canvas转换为图片
        let imageData
        try {
          imageData = canvas.toDataURL('image/png', 0.8) // 使用最高质量（1.0）以获得最佳清晰度
          if (!imageData || imageData === 'data:,') {
            throw new Error('Canvas转图片失败，数据为空')
          }
          console.log(`第${pageNum}页图片数据长度: ${imageData.length}`)
        } catch (toDataError) {
          console.error(`第${pageNum}页Canvas转图片失败:`, toDataError)
          throw toDataError
        }
        
        // 创建HTML片段
        htmlParts.push(`
          <div class="pdf-page-wrapper">
            <div class="pdf-page-number">第 ${pageNum} 页 / 共 ${numPages} 页</div>
            <img src="${imageData}" alt="PDF第${pageNum}页" class="pdf-page-image" />
          </div>
        `)
        
        console.log(`第${pageNum}页转换成功`)
      } catch (error) {
        console.error(`转换PDF第${pageNum}页失败:`, error)
        console.error('错误详情:', {
          message: error.message,
          stack: error.stack,
          name: error.name
        })
        htmlParts.push(`
          <div class="pdf-page-wrapper">
            <div class="pdf-page-error">第 ${pageNum} 页加载失败: ${error.message || '未知错误'}</div>
          </div>
        `)
      }
    }
    
    // 组合所有页面的HTML
    pdfHtmlContent.value = htmlParts.join('')
    console.log('PDF转HTML完成，共生成', htmlParts.length, '页')
    
  } catch (error) {
    console.error('PDF转HTML失败:', error)
    console.error('错误详情:', {
      message: error.message,
      stack: error.stack,
      name: error.name
    })
    // 如果转换失败，回退到iframe方式
    try {
      const response = await getDocumentContent(props.docId, props.currentPage)
      // response 是 Blob 对象，直接使用
      pdfUrl.value = URL.createObjectURL(response)
      console.log('已回退到iframe方式')
    } catch (e) {
      console.error('回退到iframe方式也失败:', e)
      pdfHtmlContent.value = '<p class="pdf-error-message">PDF加载失败，请下载后使用PDF阅读器打开</p>'
    }
  }
}

// 监听文档ID和页码变化
watch([() => props.docId, () => props.currentPage], () => {
  loadDocumentContent()
}, { immediate: true })

// 处理文本选择
const handleTextSelection = (event) => {
  // 延迟执行，确保选择已完成
  setTimeout(() => {
    const selection = window.getSelection()
    if (!selection || selection.rangeCount === 0) {
      return
    }
    
    const text = selection.toString().trim()
    if (!text || text.length === 0) {
      clearSelection()
      return
    }
    
    // 检查选择是否在文档内容区域内
    const range = selection.getRangeAt(0)
    if (!range || !range.commonAncestorContainer) {
      return
    }
    
    const viewerContent = event?.currentTarget || document.querySelector('.viewer-content')
    if (!viewerContent) {
      return
    }
    
    if (!viewerContent.contains(range.commonAncestorContainer)) {
      clearSelection()
      return
    }
    
    selectedText.value = text
    
    // 计算弹出框位置
    const rect = range.getBoundingClientRect()
    const viewerRect = viewerContent.getBoundingClientRect()
    
    selectionPopupStyle.value = {
      position: 'fixed',
      top: `${rect.top + window.scrollY - 50}px`,
      left: `${rect.left + window.scrollX + rect.width / 2 - 100}px`,
      zIndex: 1000
    }
  }, 10)
}

// 解读选中的文本（插入到输入框等待用户发送）
// 处理文本操作的通用函数
const handleTextAction = (eventPrefix) => {
  if (!selectedText.value) return
  
  const text = selectedText.value
  // 发送多种格式的事件，确保兼容性
  emit(eventPrefix, text)
  emit(`${eventPrefix.replace(/([A-Z])/g, '-$1').toLowerCase()}`, text)
  emit(`${eventPrefix.charAt(0).toLowerCase()}${eventPrefix.slice(1)}Text`, text)
  
  // 延迟清除选择，确保事件已被处理
  setTimeout(() => {
    clearSelection()
  }, 100)
}

// 解读选中的文本
const handleInterpretText = () => {
  handleTextAction('textInterpret')
}

// 翻译选中的文本
const handleTranslateText = () => {
  handleTextAction('textTranslate')
}

// 使用选中的文本（添加到输入器）
const handleUseSelectedText = () => {
  if (selectedText.value) {
    emit('textSelected', selectedText.value)
    // 不清除选择，让用户可以看到已选中的内容
    // clearSelection()
  }
}

// 清除选择
const clearSelection = () => {
  selectedText.value = ''
  selectionPopupStyle.value = {}
  // 清除浏览器选择
  const selection = window.getSelection()
  if (selection) {
    selection.removeAllRanges()
  }
}

// 全局点击处理函数
const handleGlobalClick = (e) => {
  const viewerContent = document.querySelector('.viewer-content')
  const popup = document.querySelector('.text-selection-popup')
  if (viewerContent && !viewerContent.contains(e.target) && 
      popup && !popup.contains(e.target)) {
    clearSelection()
  }
}

// 监听点击事件，点击其他地方时清除选择
onMounted(() => {
  loadDocumentContent()
  // 添加全局点击监听，点击非文档区域时清除选择
  document.addEventListener('click', handleGlobalClick)
})

// 清理资源
onBeforeUnmount(() => {
  // 移除全局点击监听器
  document.removeEventListener('click', handleGlobalClick)
  
  // 清理PDF源（如果是Blob URL）
  if (pdfSource.value && typeof pdfSource.value === 'string' && pdfSource.value.startsWith('blob:')) {
    URL.revokeObjectURL(pdfSource.value)
  }
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
  }
})
</script>

<style scoped>
.document-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color-page, #f5f7fa);
}

.viewer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: var(--el-bg-color, #ffffff);
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  flex-shrink: 0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.document-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.viewer-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  padding: 0;
  transform-origin: top center;
  transition: transform 0.3s ease;
  background: var(--el-bg-color-page, #f5f7fa);
}

.pdf-container {
  width: 100%;
  min-height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background: var(--el-bg-color-page, #f5f7fa);
}

.pdf-viewer-wrapper {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
}

.pdf-embed {
  width: 100%;
  max-width: 100%;
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
  border-radius: var(--el-border-radius-base, 4px);
  background: var(--el-bg-color, #ffffff);
}

.pdf-embed :deep(.pdf-page) {
  position: relative;
}

.pdf-embed :deep(canvas) {
  width: 100% !important;
  height: auto !important;
  display: block;
  margin: 0 auto;
  position: relative;
  z-index: 1;
}

.pdf-embed :deep(.textLayer) {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  opacity: 1;
  line-height: 1.0;
  pointer-events: auto;
  z-index: 2;
}

.pdf-embed :deep(.textLayer > span) {
  color: transparent;
  position: absolute;
  white-space: pre;
  cursor: text;
  transform-origin: 0% 0%;
  user-select: text;
  -webkit-user-select: text;
  -moz-user-select: text;
  -ms-user-select: text;
}

.pdf-embed :deep(.textLayer .highlight) {
  margin: -1px;
  padding: 1px;
  background-color: rgba(180, 0, 170, 0.2);
  border-radius: 4px;
}

.pdf-embed :deep(.textLayer .highlight.selected) {
  background-color: rgba(0, 100, 0, 0.2);
}

/* 保留旧的样式以防万一（已废弃） */
.pdf-html-content {
  width: 100%;
  max-width: 100%;
  margin: 0;
  background: var(--el-bg-color, #ffffff);
  padding: 20px;
  border-radius: var(--el-border-radius-base, 4px);
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
  overflow-x: hidden;
  box-sizing: border-box;
}

.pdf-html-content :deep(.pdf-page-wrapper) {
  width: 100%;
  margin-bottom: 30px;
  page-break-after: always;
  box-sizing: border-box;
}

.pdf-html-content :deep(.pdf-page-wrapper:last-child) {
  margin-bottom: 0;
}

.pdf-html-content :deep(.pdf-page-number) {
  text-align: center;
  color: var(--el-text-color-secondary, #909399);
  font-size: 14px;
  margin-bottom: 10px;
  padding: 8px;
  background: var(--el-bg-color-page, #f5f7fa);
  border-radius: var(--el-border-radius-base, 4px);
}

.pdf-html-content :deep(.pdf-page-image) {
  width: 100% !important;
  max-width: 100% !important;
  height: auto !important;
  display: block;
  margin: 0 auto;
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
  border-radius: var(--el-border-radius-base, 4px);
  object-fit: contain;
  box-sizing: border-box;
}

.pdf-page-error {
  text-align: center;
  color: var(--el-color-danger, #f56c6c);
  padding: 20px;
  background: var(--el-bg-color-page, #f5f7fa);
  border-radius: var(--el-border-radius-base, 4px);
}

.pdf-error-message {
  text-align: center;
  color: var(--el-color-danger, #f56c6c);
  padding: 40px;
  background: var(--el-bg-color-page, #f5f7fa);
  border-radius: var(--el-border-radius-base, 4px);
}

.pdf-iframe {
  width: 100%;
  height: 100%;
  min-height: 600px;
  border: none;
  background: var(--el-bg-color, #ffffff);
}

.image-container {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background: var(--el-bg-color-page, #f5f7fa);
}

.document-image {
  max-width: 100%;
  max-height: 100%;
}

.markdown-container {
  width: 100%;
  max-width: 900px;
  min-height: 100%;
  padding: 20px;
  background: var(--el-bg-color, #ffffff);
  border-radius: var(--el-border-radius-base, 4px);
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.markdown-content {
  width: 100%;
  line-height: 1.6;
  color: var(--el-text-color-primary, #303133);
}

.text-container {
  width: 100%;
  max-width: 900px;
  min-height: 100%;
  padding: 20px;
  background: var(--el-bg-color, #ffffff);
  border-radius: var(--el-border-radius-base, 4px);
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.text-content {
  width: 100%;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Courier New', monospace;
  line-height: 1.6;
  color: var(--el-text-color-primary, #303133);
  margin: 0;
}

.docx-container {
  width: 100%;
  max-width: 900px;
  min-height: 100%;
  padding: 20px;
  background: var(--el-bg-color, #ffffff);
  border-radius: var(--el-border-radius-base, 4px);
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.docx-content {
  width: 100%;
  line-height: 1.8;
  color: var(--el-text-color-primary, #303133);
  font-family: 'Microsoft YaHei', 'SimSun', Arial, sans-serif;
}

.docx-content :deep(p) {
  margin: 0 0 12px 0;
}

.docx-content :deep(p:last-child) {
  margin-bottom: 0;
}

.docx-content :deep(h1),
.docx-content :deep(h2),
.docx-content :deep(h3),
.docx-content :deep(h4),
.docx-content :deep(h5),
.docx-content :deep(h6) {
  margin: 16px 0 12px 0;
  font-weight: bold;
}

.docx-content :deep(ul),
.docx-content :deep(ol) {
  margin: 12px 0;
  padding-left: 30px;
}

.docx-content :deep(li) {
  margin: 4px 0;
}

.docx-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.docx-content :deep(table td),
.docx-content :deep(table th) {
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  padding: 8px;
  text-align: left;
}

.docx-content :deep(table th) {
  background-color: var(--el-bg-color-page, #f5f7fa);
  font-weight: bold;
}

.docx-content :deep(img) {
  max-width: 100%;
  height: auto;
  margin: 12px 0;
}

.docx-content :deep(.docx-error-message) {
  color: var(--el-color-error, #f56c6c);
  padding: 20px;
  text-align: center;
  background: var(--el-color-error-light-9, #fef0f0);
  border-radius: var(--el-border-radius-base, 4px);
  border: 1px solid var(--el-color-error-light-7, #fde2e2);
}

.docx-content :deep(.docx-tip-message) {
  color: var(--el-text-color-placeholder, #909399);
  padding: 20px;
  text-align: center;
  background: var(--el-bg-color-page, #f5f7fa);
  border-radius: var(--el-border-radius-base, 4px);
}

.other-document-container {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.unsupported-message {
  text-align: center;
  padding: 40px;
  background: var(--el-bg-color, #ffffff);
  border-radius: var(--el-border-radius-base, 4px);
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
}

.message-icon {
  font-size: 48px;
  color: var(--el-text-color-placeholder, #909399);
  margin-bottom: 16px;
}

.unsupported-message p {
  margin: 8px 0;
  color: var(--el-text-color-regular, #606266);
}

.message-tip {
  font-size: 14px;
  color: var(--el-text-color-placeholder, #909399);
}

.loading-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  width: 100%;
  min-height: 100%;
  flex: 1;
  color: var(--el-text-color-placeholder, #909399);
}

.loading-icon {
  font-size: 48px;
  animation: rotate 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.viewer-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--el-bg-color, #ffffff);
  border-top: 1px solid var(--el-border-color-lighter, #e4e7ed);
  flex-shrink: 0;
}

.controls-left,
.controls-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-info,
.zoom-info {
  min-width: 60px;
  text-align: center;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
}

.text-selection-popup {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-color-primary, #409eff);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
  animation: popupFadeIn 0.2s ease-out;
  z-index: 1000;
}

@keyframes popupFadeIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.text-selection-popup .el-button {
  margin: 0;
}
</style>

