<template>
  <div class="document-viewer">
    <!-- 工具栏 -->
    <div class="viewer-toolbar">
      <div class="toolbar-left">
        <el-tooltip content="返回文档列表" placement="bottom">
          <el-button type="default" @click="$emit('back')" class="back-button">
            <el-icon><ArrowLeft /></el-icon>
            <span>返回</span>
          </el-button>
        </el-tooltip>
        <span class="document-title">{{ documentInfo?.originalFileName || documentInfo?.fileName || '未命名文档' }}</span>
      </div>
      <div class="toolbar-right">
        <el-tooltip content="收藏" placement="bottom">
          <el-button type="text" @click="$emit('favorite')" class="toolbar-action-btn">
            <el-icon><Star /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="分享" placement="bottom">
          <el-button type="text" @click="$emit('share')" class="toolbar-action-btn">
            <el-icon><Share /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="导出" placement="bottom">
          <el-button type="text" @click="$emit('export')" class="toolbar-action-btn">
            <el-icon><Download /></el-icon>
          </el-button>
        </el-tooltip>
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
        <div v-else-if="pdfSource" class="pdf-viewer-wrapper" ref="pdfViewerWrapperRef">
          <!-- 已渲染的页面（懒加载：仅渲染 visiblePageCount 个页面） -->
          <div
            v-for="pageNum in visiblePdfPageNumbers"
            :key="`pdf-${docId}-page-${pageNum}`"
            :id="`pdf-page-${pageNum}`"
            class="pdf-page-container"
          >
            <div class="pdf-page-number">第 {{ pageNum }} 页 / 共 {{ pdfTotalPages }} 页</div>
            <vue-pdf-embed
              :source="pdfSource"
              :page="pageNum"
              :textLayer="true"
              class="pdf-embed"
              :ref="pageNum === 1 ? setPdfEmbedRef : undefined"
              @rendered="(info) => handlePdfPageRendered(pageNum, info)"
              @failed="handlePdfFailed"
            />
          </div>
          <!-- 占位符：未渲染的页（保持滚动高度连续） -->
          <div
            v-if="pdfTotalPages > visiblePageCount"
            class="pdf-pages-placeholder"
            :style="{ height: placeholderHeight }"
            ref="pdfPlaceholderRef"
          >
            <div class="placeholder-loading" v-if="loadingMorePages">
              <el-icon class="loading-icon"><Loading /></el-icon>
              <span>正在加载更多页面...</span>
            </div>
          </div>
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
        <div class="page-jump-wrapper">
          <el-input-number
            v-model="pageInput"
            :min="1"
            :max="totalPages"
            :precision="0"
            size="small"
            controls-position="right"
            style="width: 80px"
            @change="handlePageJump"
            @keyup.enter="handlePageJump"
          />
          <span class="page-separator">/</span>
          <span class="total-pages">{{ totalPages }}</span>
        </div>
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
          title="缩小 (Ctrl + -)"
        >
          <el-icon><Minus /></el-icon>
        </el-button>
        <el-button
          @click="handleZoomReset"
          size="small"
          title="重置缩放 (Ctrl + 0)"
        >
          <span class="zoom-reset-text">{{ zoomLevel }}%</span>
        </el-button>
        <el-button
          :disabled="zoomLevel >= 200"
          @click="handleZoomIn"
          size="small"
          title="放大 (Ctrl + +)"
        >
          <el-icon><Plus /></el-icon>
        </el-button>
        <el-button
          @click="handleFullScreen"
          size="small"
          title="全屏 (F11)"
        >
          <el-icon><FullScreen /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick, onBeforeUnmount, defineAsyncComponent } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Star, Share, Download, Loading, Document, ArrowRight, Minus, Plus, DocumentAdd, Close, ChatLineRound, FullScreen, Search } from '@element-plus/icons-vue'
import { getDocumentContent } from '@/api/documentReader'
import { renderMarkdown } from '@/composables/useMarkdown'

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
const pdfEmbedRef = ref(null)
const imageUrl = ref('')
const markdownContent = ref('')
const textContent = ref('')
const docxContent = ref('')
const loading = ref(false)

// PDF总页数
const pdfTotalPages = ref(1)

// 懒加载：当前已渲染的页数（初始只渲染 INITIAL_PDF_PAGES 页）
const INITIAL_PDF_PAGES = 3
const PDF_PAGES_PER_BATCH = 5
const visiblePageCount = ref(INITIAL_PDF_PAGES)
const loadingMorePages = ref(false)
const pdfViewerWrapperRef = ref(null)
const pdfPlaceholderRef = ref(null)
let pdfLazyObserver = null

// 已渲染的页码数组（懒加载，只包含已渲染的页）
const visiblePdfPageNumbers = computed(() => {
  const count = Math.min(visiblePageCount.value, pdfTotalPages.value)
  return Array.from({ length: count }, (_, i) => i + 1)
})

// 占位符高度（估算未渲染部分的高度，每页约 1100px）
const placeholderHeight = computed(() => {
  const unrenderedPages = pdfTotalPages.value - visiblePageCount.value
  return unrenderedPages > 0 ? `${unrenderedPages * 1100}px` : '0px'
})

// 设置PDF embed ref（仅用于第一页）
const setPdfEmbedRef = (el) => {
  if (el) {
    pdfEmbedRef.value = el
  }
}

const renderedMarkdown = computed(() => {
  if (!markdownContent.value) return ''
  return renderMarkdown(markdownContent.value)
})

// 加载文档内容
const loadDocumentContent = async () => {
  if (!props.docId) return
  
  // 重置所有内容
  pdfSource.value = null
  pdfTotalPages.value = 1 // 重置总页数，初始显示第一页
  visiblePageCount.value = INITIAL_PDF_PAGES // 重置懒加载状态
  // 清理旧的观察器
  if (pdfLazyObserver) {
    pdfLazyObserver.disconnect()
    pdfLazyObserver = null
  }
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
            pdfTotalPages.value = pdfDocument.numPages
            emit('update:totalPages', pdfDocument.numPages)
            // 重置懒加载状态
            visiblePageCount.value = INITIAL_PDF_PAGES
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

// 初始化 PDF 懒加载观察器
const initPdfLazyLoading = () => {
  // 清理旧的观察器
  if (pdfLazyObserver) {
    pdfLazyObserver.disconnect()
    pdfLazyObserver = null
  }
  
  if (!pdfPlaceholderRef.value || pdfTotalPages.value <= visiblePageCount.value) return
  
  pdfLazyObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting && !loadingMorePages.value) {
          loadMorePdfPages()
        }
      })
    },
    {
      root: pdfViewerWrapperRef.value,
      rootMargin: '400px 0px', // 提前 400px 触发加载
      threshold: 0
    }
  )
  
  if (pdfPlaceholderRef.value) {
    pdfLazyObserver.observe(pdfPlaceholderRef.value)
  }
}

// 加载更多 PDF 页面
const loadMorePdfPages = () => {
  if (loadingMorePages.value) return
  if (visiblePageCount.value >= pdfTotalPages.value) {
    // 所有页面已渲染，断开观察器
    if (pdfLazyObserver) {
      pdfLazyObserver.disconnect()
      pdfLazyObserver = null
    }
    return
  }
  
  loadingMorePages.value = true
  const newCount = Math.min(visiblePageCount.value + PDF_PAGES_PER_BATCH, pdfTotalPages.value)
  visiblePageCount.value = newCount
  
  // 等待 DOM 更新后重新观察占位符
  nextTick(() => {
    loadingMorePages.value = false
    // 如果还有未渲染的页，重新观察新的占位符
    if (visiblePageCount.value < pdfTotalPages.value && pdfPlaceholderRef.value && pdfLazyObserver) {
      pdfLazyObserver.observe(pdfPlaceholderRef.value)
    } else if (pdfLazyObserver) {
      pdfLazyObserver.disconnect()
      pdfLazyObserver = null
    }
  })
}

// PDF页面渲染完成回调
const handlePdfPageRendered = async (pageNum, info) => {
  // 只在第一页渲染时获取总页数并更新状态
  if (pageNum === 1) {
    loading.value = false
    
    // 尝试从vue-pdf-embed获取总页数
    try {
      if (pdfEmbedRef.value && pdfEmbedRef.value.pdf) {
        const pdf = pdfEmbedRef.value.pdf
        if (pdf && pdf.numPages) {
          pdfTotalPages.value = pdf.numPages
          emit('update:totalPages', pdf.numPages)
          // 初始化懒加载（如果 pdfjs 未能提前获取总页数）
          if (visiblePageCount.value >= pdf.numPages) {
            visiblePageCount.value = INITIAL_PDF_PAGES
          }
          nextTick(() => initPdfLazyLoading())
          return
        }
      }
      
      // 如果从 ref 获取失败，尝试从 info 参数获取
      if (info && typeof info === 'object' && info.numPages) {
        pdfTotalPages.value = info.numPages
        emit('update:totalPages', info.numPages)
        nextTick(() => initPdfLazyLoading())
        return
      } else if (pdfEmbedRef.value) {
        // info 为 undefined 或无效值，尝试延迟获取
        setTimeout(() => {
          if (pdfEmbedRef.value?.pdf?.numPages) {
            pdfTotalPages.value = pdfEmbedRef.value.pdf.numPages
            emit('update:totalPages', pdfEmbedRef.value.pdf.numPages)
            nextTick(() => initPdfLazyLoading())
          }
        }, 100)
      }
    } catch (error) {
      console.warn('获取PDF总页数失败:', error)
    }
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
  
  // 清理 PDF 懒加载观察器
  if (pdfLazyObserver) {
    pdfLazyObserver.disconnect()
    pdfLazyObserver = null
  }
  
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
  gap: 16px;
}

.back-button {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 14px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.back-button:hover {
  background-color: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
  transform: translateX(-2px);
}

.document-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
  max-width: 500px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-action-btn {
  padding: 8px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.toolbar-action-btn:hover {
  background-color: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
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
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
  gap: 20px;
}

.pdf-page-container {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 20px;
}

.pdf-page-number {
  text-align: center;
  color: var(--el-text-color-secondary, #909399);
  font-size: 14px;
  margin-bottom: 10px;
  padding: 8px;
  background: var(--el-bg-color-page, #f5f7fa);
  border-radius: var(--el-border-radius-base, 4px);
  width: 100%;
  max-width: 100%;
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

.pdf-pages-placeholder {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding-top: 40px;
  box-sizing: border-box;
}

.placeholder-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary, #909399);
  font-size: 14px;
  padding: 16px 24px;
  background: var(--el-bg-color, #ffffff);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.placeholder-loading .loading-icon {
  font-size: 20px;
  animation: rotate 1s linear infinite;
  margin-bottom: 0;
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
  min-height: 100%;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 40px 20px;
  background: #d0d0d0; /* 模拟Word的灰色背景 */
  background-image: 
    repeating-linear-gradient(0deg, transparent, transparent 1px, rgba(0,0,0,0.03) 1px, rgba(0,0,0,0.03) 2px);
}

.docx-content {
  width: 100%;
  max-width: 816px; /* A4纸张宽度（210mm）在96dpi下的像素值 */
  min-height: 1056px; /* A4纸张高度（297mm）在96dpi下的像素值 */
  padding: 96px 96px 96px 96px; /* 上下左右各2.54cm（1英寸）的页边距 */
  margin: 0 auto;
  background: #ffffff;
  box-shadow: 
    0 0 0 1px rgba(0, 0, 0, 0.1),
    0 2px 8px rgba(0, 0, 0, 0.15),
    0 4px 16px rgba(0, 0, 0, 0.1);
  line-height: 1.15; /* Word默认行距 */
  color: #000000;
  font-family: 'Calibri', 'Microsoft YaHei', 'SimSun', 'Times New Roman', Arial, sans-serif;
  font-size: 11pt; /* Word默认字体大小 */
  text-align: left;
  word-wrap: break-word;
  overflow-wrap: break-word;
  box-sizing: border-box;
}

.docx-content :deep(p) {
  margin: 0;
  padding: 0;
  text-align: left;
  line-height: 1.15;
  font-size: 11pt;
  color: #000000;
  font-family: 'Calibri', 'Microsoft YaHei', 'SimSun', 'Times New Roman', Arial, sans-serif;
}

.docx-content :deep(p + p) {
  margin-top: 0;
}

.docx-content :deep(p:last-child) {
  margin-bottom: 0;
}

/* 标题样式 - 模拟Word标题格式 */
.docx-content :deep(h1) {
  margin: 12pt 0 6pt 0;
  padding: 0;
  font-size: 16pt;
  font-weight: bold;
  color: #000000;
  font-family: 'Calibri', 'Microsoft YaHei', 'SimSun', 'Times New Roman', Arial, sans-serif;
  line-height: 1.15;
  page-break-after: avoid;
}

.docx-content :deep(h2) {
  margin: 12pt 0 6pt 0;
  padding: 0;
  font-size: 14pt;
  font-weight: bold;
  color: #000000;
  font-family: 'Calibri', 'Microsoft YaHei', 'SimSun', 'Times New Roman', Arial, sans-serif;
  line-height: 1.15;
  page-break-after: avoid;
}

.docx-content :deep(h3) {
  margin: 12pt 0 6pt 0;
  padding: 0;
  font-size: 12pt;
  font-weight: bold;
  color: #000000;
  font-family: 'Calibri', 'Microsoft YaHei', 'SimSun', 'Times New Roman', Arial, sans-serif;
  line-height: 1.15;
  page-break-after: avoid;
}

.docx-content :deep(h4),
.docx-content :deep(h5),
.docx-content :deep(h6) {
  margin: 12pt 0 6pt 0;
  padding: 0;
  font-size: 11pt;
  font-weight: bold;
  color: #000000;
  font-family: 'Calibri', 'Microsoft YaHei', 'SimSun', 'Times New Roman', Arial, sans-serif;
  line-height: 1.15;
  page-break-after: avoid;
}

.docx-content :deep(ul),
.docx-content :deep(ol) {
  margin: 0;
  padding-left: 36pt; /* Word默认列表缩进 */
  line-height: 1.15;
}

.docx-content :deep(ul) {
  list-style-type: disc;
}

.docx-content :deep(ol) {
  list-style-type: decimal;
}

.docx-content :deep(li) {
  margin: 0;
  padding: 0;
  line-height: 1.15;
  font-size: 11pt;
  color: #000000;
  font-family: 'Calibri', 'Microsoft YaHei', 'SimSun', 'Times New Roman', Arial, sans-serif;
}

.docx-content :deep(li p) {
  margin: 0;
  padding: 0;
}

.docx-content :deep(li + li) {
  margin-top: 0;
}

.docx-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 6pt 0;
  border: 0.5pt solid #000000;
  font-size: 11pt;
  font-family: 'Calibri', 'Microsoft YaHei', 'SimSun', 'Times New Roman', Arial, sans-serif;
}

.docx-content :deep(table td),
.docx-content :deep(table th) {
  border: 0.5pt solid #000000;
  padding: 4pt 5.4pt; /* Word默认单元格内边距 */
  text-align: left;
  vertical-align: top;
  line-height: 1.15;
  color: #000000;
}

.docx-content :deep(table th) {
  background-color: #f2f2f2; /* Word表头默认背景色 */
  font-weight: bold;
  text-align: center;
}

.docx-content :deep(table tr:nth-child(even)) {
  background-color: #ffffff;
}

.docx-content :deep(table tr:nth-child(odd)) {
  background-color: #ffffff;
}

.docx-content :deep(img) {
  max-width: 100%;
  height: auto;
  margin: 6pt 0;
  display: block;
  border: none;
  box-shadow: none;
}

.docx-content :deep(img[style*="float: left"]) {
  float: left;
  margin-right: 12pt;
  margin-bottom: 6pt;
}

.docx-content :deep(img[style*="float: right"]) {
  float: right;
  margin-left: 12pt;
  margin-bottom: 6pt;
}

/* 文本格式样式 */
.docx-content :deep(strong),
.docx-content :deep(b) {
  font-weight: bold;
  color: #000000;
}

.docx-content :deep(em),
.docx-content :deep(i) {
  font-style: italic;
  color: #000000;
}

.docx-content :deep(u) {
  text-decoration: underline;
  color: #000000;
}

.docx-content :deep(s),
.docx-content :deep(strike) {
  text-decoration: line-through;
  color: #000000;
}

.docx-content :deep(code) {
  font-family: 'Courier New', monospace;
  font-size: 10pt;
  background-color: #f5f5f5;
  padding: 2pt 4pt;
  border-radius: 2pt;
}

.docx-content :deep(blockquote) {
  margin: 12pt 0;
  padding: 0 12pt;
  border-left: 3pt solid #cccccc;
  color: #666666;
  font-style: italic;
}

.docx-content :deep(hr) {
  border: none;
  border-top: 0.5pt solid #000000;
  margin: 12pt 0;
  width: 100%;
}

/* 超链接样式 */
.docx-content :deep(a) {
  color: #0563c1;
  text-decoration: underline;
}

.docx-content :deep(a:hover) {
  color: #0563c1;
  text-decoration: underline;
}

.docx-content :deep(.docx-error-message) {
  color: #c00000;
  padding: 20px;
  text-align: center;
  background: #fff4f4;
  border: 1px solid #ffcccc;
  font-size: 11pt;
  font-family: 'Calibri', 'Microsoft YaHei', 'SimSun', 'Times New Roman', Arial, sans-serif;
}

.docx-content :deep(.docx-tip-message) {
  color: #666666;
  padding: 20px;
  text-align: center;
  background: #f5f5f5;
  font-size: 11pt;
  font-family: 'Calibri', 'Microsoft YaHei', 'SimSun', 'Times New Roman', Arial, sans-serif;
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

.page-jump-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-separator {
  color: var(--el-text-color-placeholder, #909399);
  font-size: 14px;
}

.total-pages {
  color: var(--el-text-color-regular, #606266);
  font-size: 14px;
  min-width: 30px;
}

.zoom-reset-text {
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  min-width: 50px;
  text-align: center;
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
