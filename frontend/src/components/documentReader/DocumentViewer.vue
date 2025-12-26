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

    <!-- 文档内容区域 -->
    <div class="viewer-content" :style="{ transform: `scale(${zoomLevel / 100})` }">
      <!-- PDF文档 -->
      <div v-if="fileType === 'pdf'" class="pdf-container">
        <!-- 使用HTML方式显示（类似DOCX） -->
        <div v-if="pdfHtmlContent" class="pdf-html-content" v-html="pdfHtmlContent"></div>
        <!-- 回退到iframe方式 -->
        <iframe
          v-else-if="pdfUrl"
          :src="pdfUrl"
          class="pdf-iframe"
          frameborder="0"
        ></iframe>
        <div v-else-if="loading" class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>正在转换PDF...</p>
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
import { ref, computed, watch, onMounted, nextTick, onBeforeUnmount, toRaw } from 'vue'
import { ArrowLeft, Star, Share, Download, Loading, Document, ArrowRight, Minus, Plus } from '@element-plus/icons-vue'
import { getDocumentContent } from '@/api/documentReader'
import { renderMarkdown } from '@/composables/useMarkdown'
import mammoth from 'mammoth'
import * as pdfjsLib from 'pdfjs-dist'

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

const emit = defineEmits(['update:currentPage', 'update:zoomLevel', 'update:totalPages', 'back', 'favorite', 'share', 'export'])

const fileType = computed(() => {
  const fileName = props.documentInfo?.originalFileName || props.documentInfo?.fileName || ''
  const extension = fileName.split('.').pop()?.toLowerCase() || ''
  return extension
})

const isImageType = computed(() => {
  return ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(fileType.value)
})

// 配置PDF.js worker - 使用多个备用方案
// 方案1: 尝试使用本地 worker（如果 Vite 能正确处理）
// 方案2: 使用 unpkg CDN（比 cdnjs 更可靠）
// 方案3: 使用 jsdelivr CDN（备用）
const workerUrls = [
  `https://unpkg.com/pdfjs-dist@${pdfjsLib.version}/build/pdf.worker.min.mjs`,
  `https://cdn.jsdelivr.net/npm/pdfjs-dist@${pdfjsLib.version}/build/pdf.worker.min.mjs`,
  `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjsLib.version}/pdf.worker.min.js`
]

// 使用第一个可用的 worker URL
pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrls[0]

const pdfUrl = ref('')
const pdfHtmlContent = ref('')
const imageUrl = ref('')
const markdownContent = ref('')
const textContent = ref('')
const docxContent = ref('')
const loading = ref(false)
const pdfDocument = ref(null)

const renderedMarkdown = computed(() => {
  if (!markdownContent.value) return ''
  return renderMarkdown(markdownContent.value)
})

// 加载文档内容
const loadDocumentContent = async () => {
  if (!props.docId) return
  
  // 重置所有内容
  pdfUrl.value = ''
  pdfHtmlContent.value = ''
  imageUrl.value = ''
  markdownContent.value = ''
  textContent.value = ''
  docxContent.value = ''
  
  loading.value = true
  try {
    const response = await getDocumentContent(props.docId, props.currentPage)
    
    if (fileType.value === 'pdf') {
      // PDF文件，转换为HTML预览（类似DOCX）
      try {
        // response 是 Blob 对象（因为 responseType: 'blob'）
        // 将 Blob 转换为 ArrayBuffer
        const arrayBuffer = await response.arrayBuffer()
        
        const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer })
        const rawPdfDocument = await loadingTask.promise
        // 使用 toRaw 获取原始对象，避免 Vue 响应式代理导致的问题
        pdfDocument.value = rawPdfDocument
        
        // 更新总页数
        emit('update:totalPages', rawPdfDocument.numPages)
        
        // 将所有页面转换为HTML
        await convertPdfToHtml()
      } catch (error) {
        console.error('PDF加载失败:', error)
        console.error('错误详情:', {
          message: error.message,
          stack: error.stack,
          name: error.name
        })
        // 如果PDF.js加载失败，回退到iframe方式
        try {
          // 重新获取response（因为之前的已经被读取了）
          const fallbackResponse = await getDocumentContent(props.docId, props.currentPage)
          // fallbackResponse 也是 Blob 对象
          pdfUrl.value = URL.createObjectURL(fallbackResponse)
          console.log('已回退到iframe方式显示PDF')
        } catch (fallbackError) {
          console.error('回退到iframe方式也失败:', fallbackError)
          pdfHtmlContent.value = '<p class="pdf-error-message">PDF加载失败，请下载后使用PDF阅读器打开</p>'
        }
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

// 将PDF转换为HTML（类似DOCX的方式）
const convertPdfToHtml = async () => {
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

// 清理资源
onBeforeUnmount(() => {
  if (pdfUrl.value) {
    URL.revokeObjectURL(pdfUrl.value)
  }
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
  }
  // 清理PDF HTML内容中的图片数据URL（释放内存）
  if (pdfHtmlContent.value) {
    pdfHtmlContent.value = ''
  }
})

onMounted(() => {
  loadDocumentContent()
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
  padding: 12px 16px;
  background: var(--el-bg-color, #ffffff);
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  flex-shrink: 0;
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
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  background: var(--el-bg-color-page, #f5f7fa);
}

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
  padding: 20px;
  background: var(--el-bg-color, #ffffff);
  border-radius: var(--el-border-radius-base, 4px);
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
}

.markdown-content {
  line-height: 1.6;
  color: var(--el-text-color-primary, #303133);
}

.text-container {
  width: 100%;
  max-width: 900px;
  padding: 20px;
  background: var(--el-bg-color, #ffffff);
  border-radius: var(--el-border-radius-base, 4px);
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
}

.text-content {
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
  padding: 20px;
  background: var(--el-bg-color, #ffffff);
  border-radius: var(--el-border-radius-base, 4px);
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
  margin: 0 auto;
}

.docx-content {
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
  height: 400px;
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
</style>

