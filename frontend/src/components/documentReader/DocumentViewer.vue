<template>
  <div class="document-viewer">
    <div class="viewer-toolbar"><div class="toolbar-left"><el-button type="default" class="back-button" @click="$emit('back')"><el-icon><ArrowLeft /></el-icon><span>返回</span></el-button><span class="document-title">{{ documentInfo?.originalFileName || documentInfo?.fileName || '未命名文档' }}</span></div><div class="toolbar-right"><el-button type="text" class="toolbar-action-btn" @click="$emit('favorite')"><el-icon><Star /></el-icon></el-button><el-button type="text" class="toolbar-action-btn" @click="$emit('share')"><el-icon><Share /></el-icon></el-button><el-button type="text" class="toolbar-action-btn" @click="$emit('export')"><el-icon><Download /></el-icon></el-button></div></div>
    <div v-if="selectedText" class="text-selection-popup" :style="selectionPopupStyle"><el-button type="success" size="small" @click="handleInterpretText"><el-icon><ChatLineRound /></el-icon>解读</el-button><el-button type="primary" size="small" @click="handleTranslateText"><el-icon><DocumentAdd /></el-icon>翻译</el-button><el-button type="text" size="small" title="取消选择" @click="clearSelection"><el-icon><Close /></el-icon></el-button></div>
    <div class="viewer-content" :style="{ transform: `scale(${zoomLevel / 100})` }" @mouseup="handleTextSelection">
      <PdfDocumentView :file-type="fileType" :loading="loading" :pdf-source="pdfSource" :doc-id="docId" :visible-pdf-page-numbers="visiblePdfPageNumbers" :pdf-total-pages="pdfTotalPages" :visible-page-count="visiblePageCount" :placeholder-height="placeholderHeight" :loading-more-pages="loadingMorePages" :pdf-viewer-wrapper-ref="pdfViewerWrapperRef" :pdf-placeholder-ref="pdfPlaceholderRef" :set-pdf-embed-ref="setPdfEmbedRef" :handle-pdf-page-rendered="handlePdfPageRendered" :handle-pdf-failed="handlePdfFailed" />
      <DocumentFormatView v-if="fileType !== 'pdf'" :file-type="fileType" :is-image-type="isImageType" :image-url="imageUrl" :markdown-content="markdownContent" :rendered-markdown="renderedMarkdown" :text-content="textContent" :docx-content="docxContent" :loading="loading" />
    </div>
    <DocumentViewerControls v-model:page-input="pageInput" :current-page="currentPage" :total-pages="totalPages" :zoom-level="zoomLevel" @previous="handlePrevPage" @next="handleNextPage" @page-jump="handlePageJump" @zoom-in="handleZoomIn" @zoom-out="handleZoomOut" @zoom-reset="handleZoomReset" @fullscreen="handleFullScreen" />
  </div>
</template>
<script setup>
import { ref, computed, watch, onMounted, nextTick, onBeforeUnmount, defineAsyncComponent } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Star, Share, Download, Loading, Document, ArrowRight, Minus, Plus, DocumentAdd, Close, ChatLineRound, FullScreen, Search } from '@element-plus/icons-vue'
import { getDocumentContent } from '@/api/documentReader'
import { renderMarkdown } from '@/composables/useMarkdown'
import { usePdfLazyRendering } from '@/composables/usePdfLazyRendering'
import { useDocumentRendererAdapter } from '@/composables/useDocumentRendererAdapter'
import PdfDocumentView from './views/PdfDocumentView.vue'
import DocumentFormatView from './views/DocumentFormatView.vue'
import DocumentViewerControls from './DocumentViewerControls.vue'

// 动态导入 PDF 相关库，避免阻塞初始加载
let VuePdfEmbed = null
let pdfjsLib = null
let mammoth = null

// 懒加载 PDF 相关库
const loadPdfLibraries = async () => {
  if (!VuePdfEmbed) {
    const [vuePdfEmbedModule, pdfjsModule, mammothModule] = await Promise.all([
      import('vue-pdf-embed'),
      import('pdfjs-dist'),
      import('mammoth')
    ])
    VuePdfEmbed = vuePdfEmbedModule.default
    pdfjsLib = pdfjsModule
    mammoth = mammothModule.default

    // 配置pdfjs-dist worker
    if (typeof window !== 'undefined' && !pdfjsLib.GlobalWorkerOptions.workerSrc) {
      pdfjsLib.GlobalWorkerOptions.workerSrc = `https://unpkg.com/pdfjs-dist@${pdfjsLib.version}/build/pdf.worker.min.mjs`
    }
  }
  return { VuePdfEmbed, pdfjsLib, mammoth }
}

// 抑制PDF.js的字体警告（这些警告不影响PDF显示）
if (typeof window !== 'undefined') {
  const originalWarn = console.warn
  console.warn = function(...args) {
    // 过滤掉PDF字体相关的警告
    const message = args[0]?.toString() || ''
    if (message.includes('TT: undefined function') ||
        message.includes('Warning:') && message.includes('TT:')) {
      // 忽略这些警告，不影响功能
      return
    }
    // 其他警告正常输出
    originalWarn.apply(console, args)
  }
}

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

// 页面跳转输入
const pageInput = ref(1)

// 全屏状态
const isFullScreen = ref(false)

const fileType = computed(() => {
  const fileName = props.documentInfo?.originalFileName || props.documentInfo?.fileName || ''
  const extension = fileName.split('.').pop()?.toLowerCase() || ''
  return extension
})

const isImageType = computed(() => {
  return ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(fileType.value)
})

const pdfSource = ref(null)
const imageUrl = ref('')
const markdownContent = ref('')
const textContent = ref('')
const docxContent = ref('')
const loading = ref(false)

const {
  pdfTotalPages,
  visiblePageCount,
  loadingMorePages,
  pdfViewerWrapperRef,
  pdfPlaceholderRef,
  visiblePdfPageNumbers,
  placeholderHeight,
  setPdfEmbedRef,
  setPdfTotalPages,
  resetPdfLazyRendering,
  initPdfLazyLoading,
  handlePdfPageRendered,
  cleanupPdfLazyLoading
} = usePdfLazyRendering({
  onTotalPages: (totalPages) => emit('update:totalPages', totalPages),
  onFirstPageRendered: () => {
    loading.value = false
  }
})

const renderedMarkdown = computed(() => {
  if (!markdownContent.value) return ''
  return renderMarkdown(markdownContent.value)
})

// 加载文档内容
const loadDocumentContent = async () => {
  if (!props.docId) return

  // 重置所有内容
  pdfSource.value = null
  resetPdfLazyRendering()
  imageUrl.value = ''
  markdownContent.value = ''
  textContent.value = ''
  docxContent.value = ''

  loading.value = true
  try {
    // 对于 PDF，获取完整文件（不传页码参数）
    // 对于其他类型，可能需要按页加载
    const pageParam = fileType.value === 'pdf' ? null : props.currentPage
    const response = await getDocumentContent(props.docId, pageParam)

    if (fileType.value === 'pdf') {
      // 懒加载 PDF 相关库
      const libs = await loadPdfLibraries()
      VuePdfEmbed = libs.VuePdfEmbed
      pdfjsLib = libs.pdfjsLib
      mammoth = libs.mammoth
      // PDF文件，使用vue-pdf-embed显示
      try {
        // response 是 Blob 对象（因为 responseType: 'blob'）
        // vue-pdf-embed 支持 Blob，但为了更好的兼容性，我们创建 Blob URL
        // 注意：Blob URL 不需要认证，因为数据已经在内存中
        let pdfBlob = response
        if (!(response instanceof Blob)) {
          // 如果不是 Blob，可能是 ArrayBuffer，转换为 Blob
          pdfBlob = new Blob([response], { type: 'application/pdf' })
        }
        pdfSource.value = URL.createObjectURL(pdfBlob)

        // 使用pdfjs-dist获取总页数
        try {
          const arrayBuffer = await pdfBlob.arrayBuffer()
          // 配置worker（如果需要）
          if (!pdfjsLib.GlobalWorkerOptions.workerSrc) {
            pdfjsLib.GlobalWorkerOptions.workerSrc = `https://unpkg.com/pdfjs-dist@${pdfjsLib.version}/build/pdf.worker.min.mjs`
          }

          // 配置PDF加载选项，抑制字体警告
          const loadingTask = pdfjsLib.getDocument({
            data: arrayBuffer,
            verbosity: 0 // 0 = errors only, 1 = warnings (default), 5 = infos
          })
          const pdfDocument = await loadingTask.promise

          if (pdfDocument && pdfDocument.numPages) {
            setPdfTotalPages(pdfDocument.numPages)
            // 初始化懒加载观察器
            nextTick(() => initPdfLazyLoading())
          }
        } catch (pageCountError) {
          console.warn('使用pdfjs-dist获取总页数失败，将等待vue-pdf-embed渲染:', pageCountError)
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
      let text = await response.text()
      // 大文本防护：超过 500k 字符截断
      if (text.length > 500_000) {
        text = text.substring(0, 500_000) + '\n\n---\n\n> ……内容过大，仅显示前 500k 字符'
      }
      markdownContent.value = text
    } else if (fileType.value === 'txt') {
      // 文本文件，读取文本内容
      let text = await response.text()
      // 大文本防护：超过 500k 字符截断，防止 <pre> 渲染崩溃
      if (text.length > 500_000) {
        text = text.substring(0, 500_000) + '\n\n...内容过多，仅显示前 500k 字符'
      }
      textContent.value = text
    } else if (fileType.value === 'docx') {
      // Word文档 (docx)，使用mammoth转换为HTML
      const arrayBuffer = await response.arrayBuffer()
      try {
        // 配置mammoth转换选项，保留更多样式信息
        const result = await mammoth.convertToHtml(
          { arrayBuffer: arrayBuffer },
          {
            styleMap: [
              "p[style-name='Heading 1'] => h1:fresh",
              "p[style-name='Heading 2'] => h2:fresh",
              "p[style-name='Heading 3'] => h3:fresh",
              "p[style-name='Heading 4'] => h4:fresh",
              "p[style-name='Heading 5'] => h5:fresh",
              "p[style-name='Heading 6'] => h6:fresh",
              "r[style-name='Strong'] => strong",
              "p[style-name='Title'] => h1.title:fresh",
              "p[style-name='Subtitle'] => h2.subtitle:fresh"
            ],
            includeDefaultStyleMap: true
          }
        )
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

// 滚动到指定页面
const scrollToPage = (pageNum) => {
  // 使用多次尝试，确保页面已渲染
  let attempts = 0
  const maxAttempts = 10

  const tryScroll = () => {
    attempts++
    const pageElement = document.getElementById(`pdf-page-${pageNum}`)
    const container = document.querySelector('.pdf-viewer-wrapper')

    if (pageElement && container) {
      // 使用scrollIntoView更可靠
      pageElement.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
        inline: 'nearest'
      })

      // 额外调整，确保页面顶部有适当间距
      setTimeout(() => {
        const containerRect = container.getBoundingClientRect()
        const pageRect = pageElement.getBoundingClientRect()
        if (pageRect.top < containerRect.top + 20) {
          const scrollTop = container.scrollTop + (pageRect.top - containerRect.top) - 20
          container.scrollTo({
            top: scrollTop,
            behavior: 'smooth'
          })
        }
      }, 100)
    } else if (attempts < maxAttempts) {
      // 如果元素还没渲染，等待一段时间后重试
      setTimeout(tryScroll, 100)
    } else {
      console.warn(`无法滚动到第${pageNum}页，元素可能还未渲染`)
    }
  }

  tryScroll()
}

// 翻页处理
const handlePrevPage = () => {
  if (props.currentPage > 1) {
    const newPage = props.currentPage - 1
    emit('update:currentPage', newPage)
    // 等待DOM更新后滚动
    setTimeout(() => {
      scrollToPage(newPage)
    }, 50)
  }
}

const handleNextPage = () => {
  if (props.currentPage < props.totalPages) {
    const newPage = props.currentPage + 1
    emit('update:currentPage', newPage)
    // 等待DOM更新后滚动
    setTimeout(() => {
      scrollToPage(newPage)
    }, 50)
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

// 重置缩放
const handleZoomReset = () => {
  emit('update:zoomLevel', 100)
}

// 页面跳转
const handlePageJump = () => {
  if (pageInput.value >= 1 && pageInput.value <= props.totalPages) {
    emit('update:currentPage', pageInput.value)
    if (fileType.value === 'pdf') {
      setTimeout(() => {
        scrollToPage(pageInput.value)
      }, 50)
    }
  } else {
    pageInput.value = props.currentPage
  }
}

// 全屏切换
const handleFullScreen = () => {
  const viewer = document.querySelector('.document-viewer')
  if (!viewer) return

  if (!isFullScreen.value) {
    // 进入全屏
    if (viewer.requestFullscreen) {
      viewer.requestFullscreen()
    } else if (viewer.webkitRequestFullscreen) {
      viewer.webkitRequestFullscreen()
    } else if (viewer.mozRequestFullScreen) {
      viewer.mozRequestFullScreen()
    } else if (viewer.msRequestFullscreen) {
      viewer.msRequestFullscreen()
    }
  } else {
    // 退出全屏
    if (document.exitFullscreen) {
      document.exitFullscreen()
    } else if (document.webkitExitFullscreen) {
      document.webkitExitFullscreen()
    } else if (document.mozCancelFullScreen) {
      document.mozCancelFullScreen()
    } else if (document.msExitFullscreen) {
      document.msExitFullscreen()
    }
  }
}

// 监听全屏状态变化
const handleFullScreenChange = () => {
  isFullScreen.value = !!(
    document.fullscreenElement ||
    document.webkitFullscreenElement ||
    document.mozFullScreenElement ||
    document.msFullscreenElement
  )
}

// 鼠标滚轮缩放
const handleWheel = (event) => {
  // 按住Ctrl键时使用滚轮缩放
  if (event.ctrlKey || event.metaKey) {
    event.preventDefault()
    const delta = event.deltaY > 0 ? -10 : 10
    const newZoom = Math.max(50, Math.min(200, props.zoomLevel + delta))
    emit('update:zoomLevel', newZoom)
  }
}

// 双击缩放
const handleDoubleClick = (event) => {
  // 双击文档内容区域时，如果缩放小于100%则重置为100%，否则放大到150%
  if (props.zoomLevel < 100) {
    emit('update:zoomLevel', 100)
  } else if (props.zoomLevel < 150) {
    emit('update:zoomLevel', 150)
  } else {
    emit('update:zoomLevel', 100)
  }
}

// 键盘快捷键处理
const handleKeyDown = (event) => {
  // 如果正在输入，不处理快捷键
  if (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA') {
    return
  }

  const { key, ctrlKey, metaKey, shiftKey } = event
  const isModifier = ctrlKey || metaKey

  // 翻页快捷键
  if (key === 'ArrowLeft' || key === 'ArrowUp') {
    event.preventDefault()
    handlePrevPage()
  } else if (key === 'ArrowRight' || key === 'ArrowDown') {
    event.preventDefault()
    handleNextPage()
  }
  // 缩放快捷键
  else if (isModifier && (key === '=' || key === '+')) {
    event.preventDefault()
    handleZoomIn()
  } else if (isModifier && key === '-') {
    event.preventDefault()
    handleZoomOut()
  } else if (isModifier && key === '0') {
    event.preventDefault()
    handleZoomReset()
  }
  // 全屏快捷键
  else if (key === 'F11') {
    event.preventDefault()
    handleFullScreen()
  }
  // ESC退出全屏
  else if (key === 'Escape' && isFullScreen.value) {
    handleFullScreen()
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
// 监听文档ID变化（需要重新加载）
watch(() => props.docId, (newDocId, oldDocId) => {
  if (newDocId && newDocId !== oldDocId) {
    // 文档ID改变，强制重新加载
    loadDocumentContent()
  }
}, { immediate: true })

// 监听页码变化
watch(() => props.currentPage, (newPage, oldPage) => {
  // 更新页面跳转输入框
  pageInput.value = newPage

  // 对于PDF文件，已经显示所有页面，滚动到对应页面
  if (fileType.value === 'pdf' && newPage !== oldPage) {
    // 等待DOM更新后滚动
    setTimeout(() => {
      scrollToPage(newPage)
    }, 50)
    return
  }

  // 对于其他类型（如按页返回的文档），需要重新加载
  if (fileType.value !== 'pdf' && newPage !== oldPage && props.docId) {
    loadDocumentContent()
  }
})

// 监听缩放变化，同步到输入框
watch(() => props.zoomLevel, (newZoom) => {
  // 可以在这里添加其他逻辑
})

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
  // 只发送一个统一格式的事件（kebab-case），避免重复触发
  const eventName = eventPrefix.replace(/([A-Z])/g, '-$1').toLowerCase()
  emit(eventName, text)

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
  // 初始化页面输入框
  pageInput.value = props.currentPage

  // 添加全局点击监听，点击非文档区域时清除选择
  document.addEventListener('click', handleGlobalClick)

  // 添加键盘快捷键监听
  document.addEventListener('keydown', handleKeyDown)

  // 添加全屏状态监听
  document.addEventListener('fullscreenchange', handleFullScreenChange)
  document.addEventListener('webkitfullscreenchange', handleFullScreenChange)
  document.addEventListener('mozfullscreenchange', handleFullScreenChange)
  document.addEventListener('MSFullscreenChange', handleFullScreenChange)

  // 添加鼠标滚轮监听（在文档内容区域）
  nextTick(() => {
    const viewerContent = document.querySelector('.viewer-content')
    if (viewerContent) {
      viewerContent.addEventListener('wheel', handleWheel, { passive: false })
      viewerContent.addEventListener('dblclick', handleDoubleClick)
    }
  })
})

// 清理资源
onBeforeUnmount(() => {
  // 移除全局点击监听器
  document.removeEventListener('click', handleGlobalClick)

  // 移除键盘快捷键监听
  document.removeEventListener('keydown', handleKeyDown)

  // 移除全屏状态监听
  document.removeEventListener('fullscreenchange', handleFullScreenChange)
  document.removeEventListener('webkitfullscreenchange', handleFullScreenChange)
  document.removeEventListener('mozfullscreenchange', handleFullScreenChange)
  document.removeEventListener('MSFullscreenChange', handleFullScreenChange)

  // 移除鼠标滚轮监听
  const viewerContent = document.querySelector('.viewer-content')
  if (viewerContent) {
    viewerContent.removeEventListener('wheel', handleWheel)
    viewerContent.removeEventListener('dblclick', handleDoubleClick)
  }

  cleanupPdfLazyLoading()

  // 清理PDF源（如果是Blob URL）
  if (pdfSource.value && typeof pdfSource.value === 'string' && pdfSource.value.startsWith('blob:')) {
    URL.revokeObjectURL(pdfSource.value)
  }
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
  }
})
</script>

<style src="./DocumentViewer.css"></style>
