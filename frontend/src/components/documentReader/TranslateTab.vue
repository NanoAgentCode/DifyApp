<template>
  <div class="translate-tab" :class="{ 'fullscreen-mode': isFullscreen }" ref="translateTabRef">
    <div class="tab-header">
      <div class="header-info">
        <el-icon><Switch /></el-icon>
        <span>翻译</span>
      </div>
      <div v-if="!isEditing" class="header-actions">
        <el-select
          v-model="targetLanguage"
          placeholder="选择目标语言"
          size="small"
          style="width: 150px; margin-right: 8px"
          @change="handleLanguageChange"
        >
          <el-option label="中文" value="zh" />
          <el-option label="英文" value="en" />
          <el-option label="日文" value="ja" />
          <el-option label="韩文" value="ko" />
        </el-select>
        <el-tooltip
          :content="translationContent ? '重新翻译' : '翻译'"
          placement="bottom"
        >
        <el-button
          type="success"
          size="small"
          @click="handleTranslate"
          :loading="translating"
          :disabled="!targetLanguage || translating"
            circle
        >
          <el-icon><Refresh /></el-icon>
        </el-button>
        </el-tooltip>
        <el-tooltip
          v-if="translationContent"
          content="编辑翻译"
          placement="bottom"
        >
          <el-button
          type="primary"
          size="small"
          @click="handleEdit"
          :disabled="translating"
            circle
        >
          <el-icon><Edit /></el-icon>
        </el-button>
        </el-tooltip>
        <el-tooltip
          v-if="translationContent"
          :content="isFullscreen ? '退出全屏' : '全屏'"
          placement="bottom"
        >
          <el-button
          type="success"
          size="small"
          @click="toggleFullscreen"
            circle
        >
          <el-icon><FullScreen /></el-icon>
        </el-button>
        </el-tooltip>
      </div>
      <div v-else class="edit-actions">
        <el-tooltip content="取消" placement="bottom">
          <el-button size="small" @click="handleCancel" circle>
            <el-icon><Close /></el-icon>
        </el-button>
        </el-tooltip>
        <el-tooltip content="保存" placement="bottom">
          <el-button type="primary" size="small" @click="handleSave" :loading="saving" circle>
            <el-icon><Check /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>
    
    <div class="tab-content">
      <el-input
        v-if="isEditing"
        v-model="editContent"
        type="textarea"
        :rows="15"
        placeholder="请输入翻译内容..."
        class="edit-input"
      />
      <div v-else class="content-wrapper">
        <div v-if="translating" class="loading-state">
          <el-icon class="loading-icon is-loading"><Loading /></el-icon>
          <p>翻译中，请稍候...</p>
        </div>
        <!-- 全屏模式：显示原文和译文对比 -->
        <div v-else-if="isFullscreen && translationContent && originalText" class="comparison-view">
          <div class="comparison-container">
            <div class="text-panel original-panel">
              <div class="panel-header">原文</div>
              <div 
                ref="originalContentRef"
                class="text-content original-content" 
                v-html="renderedOriginal"
                @scroll="handleOriginalScroll"
                @mouseup="handleTextSelection('original')"
              ></div>
            </div>
            <div class="text-panel translation-panel">
              <div class="panel-header">译文</div>
              <div 
                ref="translationContentRef"
                class="text-content translation-content-display" 
                v-html="renderedTranslation"
                @scroll="handleTranslationScroll"
                @mouseup="handleTextSelection('translation')"
              ></div>
            </div>
          </div>
        </div>
        <!-- 默认模式：只显示译文（懒加载模式） -->
        <div v-else-if="translationContent || segmentTranslations.length > 0" 
             class="translation-content" 
             ref="translationContainerRef"
             @scroll="handleTranslationScrollForLazyLoad">
          <div class="translation-display" v-html="renderedTranslation"></div>
          <!-- 显示加载提示 -->
          <div v-if="loadingSegments.size > 0" class="loading-segment">
            <el-icon class="loading-icon is-loading"><Loading /></el-icon>
            <span>正在翻译后续内容...</span>
            <span v-if="loadingSegments.size > 0" class="loading-info">
              ({{ Array.from(loadingSegments).sort((a, b) => a - b).join(', ') }})
            </span>
          </div>
          <!-- 显示翻译完成提示 -->
          <div v-else-if="isTranslationComplete" class="translation-complete">
            <el-icon class="complete-icon"><Check /></el-icon>
            <span>翻译已完成</span>
          </div>
        </div>
        <div v-else class="empty-state">
          <el-icon class="empty-icon"><Document /></el-icon>
          <p>请选择目标语言并点击翻译按钮</p>
          <p class="tip">仅支持非中文文档的翻译</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Switch, Refresh, Loading, Document, Edit, FullScreen, Close, Check } from '@element-plus/icons-vue'
import { translateDocument, getDocumentTranslation, saveDocumentTranslation, getDocumentText } from '@/api/documentReader'
import { useDocumentSegmentTranslation } from '@/composables/useDocumentSegmentTranslation'
import { debounce } from '@/utils/debounce'

const props = defineProps({
  docId: {
    type: Number,
    required: true
  },
  documentInfo: {
    type: Object,
    default: () => ({})
  }
})

const targetLanguage = ref('zh')
const translationContent = ref('')
const originalText = ref('')
const translating = ref(false)
const isEditing = ref(false)
const editContent = ref('')
const saving = ref(false)
const hasAutoTranslated = ref(false) // 标记是否已自动翻译
const originalContentRef = ref(null)
const translationContentRef = ref(null)
const isScrolling = ref(false) // 防止滚动循环
const isFullscreen = ref(false) // 全屏状态
const translateTabRef = ref(null) // 翻译组件引用

const translationContainerRef = ref(null) // 翻译内容容器引用
const selectedTextInfo = ref(null) // 选中的文本信息
const highlightTimeout = ref(null) // 高亮清除定时器
const pageHeight = ref(0) // 页面高度（用于页面对齐）

const {
  segmentsInfo,
  loadedSegments,
  loadingSegments,
  segmentTranslations,
  resetSegmentTranslations,
  loadSegmentsInfo,
  loadTranslationSegment,
  handleTranslationScrollForLazyLoad
} = useDocumentSegmentTranslation({
  docId: computed(() => props.docId),
  targetLanguage,
  translationContent,
  translationContainerRef,
  onSegmentError: (message) => ElMessage.error(message)
})

// 分析文本结构，提取段落分隔信息
function analyzeTextStructure(text) {
  if (!text) return { lines: [], structure: [] }
  
  const lines = text.split('\n')
  const structure = []
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()
    
    if (trimmed.length === 0) {
      structure.push({ type: 'empty', index: i, originalLine: line })
    } else {
    // 识别标题模式
    const isTitle = 
      // 数字编号模式：1. 1.1 第一章 第一节
      /^(\d+[\.、]\s*|\d+\.\d+[\.、]\s*|第[一二三四五六七八九十\d]+[章节部分])/.test(trimmed) ||
      // 全大写且长度适中（可能是英文标题）
      (trimmed === trimmed.toUpperCase() && trimmed.length < 100 && trimmed.length > 3 && /^[A-Z\s\-]+$/.test(trimmed)) ||
      // Markdown风格标题
      /^#{1,6}\s/.test(trimmed) ||
      // 短行且可能是标题（长度小于80且不以句号结尾）
      (trimmed.length < 80 && !trimmed.endsWith('。') && !trimmed.endsWith('.') && 
       (trimmed.includes('章') || trimmed.includes('节') || trimmed.includes('部分') || 
        trimmed.includes('Chapter') || trimmed.includes('Section') || trimmed.includes('Part')))
    
      structure.push({ 
        type: isTitle ? 'title' : 'text', 
        index: i, 
        originalLine: line,
        trimmed: trimmed
      })
    }
  }
  
  return { lines, structure }
}

// 处理文本，识别并标记标题，保持原始段落结构
function processTextForDisplay(text) {
  if (!text) return ''
  
  // 大文本防射：超过限制则截断，避免一次性渲染过多文字崩溃浏览器
  const safeText = text.length > MAX_RENDER_CHARS 
    ? text.substring(0, MAX_RENDER_CHARS) + '\n\n... (内容过多，仅显示前 ' + (MAX_RENDER_CHARS / 1000).toFixed(0) + 'k 字符)'
    : text
  
  // 分析文本结构
  const { structure } = analyzeTextStructure(safeText)
  
  if (structure.length === 0) return ''
  
  // 处理文本，保持原始结构
  const finalLines = []
  let consecutiveEmptyLines = 0
  
  for (let i = 0; i < structure.length; i++) {
    const item = structure[i]
    const prevItem = i > 0 ? structure[i - 1] : null
    const nextItem = i < structure.length - 1 ? structure[i + 1] : null
    
    if (item.type === 'empty') {
      // 处理空行：保留段落分隔，但限制连续空行数量
      consecutiveEmptyLines++
      if (consecutiveEmptyLines <= 2) {
        // 如果前后都是文本，保留空行用于段落分隔
        if (prevItem && nextItem && prevItem.type === 'text' && nextItem.type === 'text') {
          finalLines.push('<br>')
        } else if (prevItem && prevItem.type === 'title' && nextItem && nextItem.type === 'title') {
          // 标题之间的空行，保留
          finalLines.push('<br>')
        } else if (prevItem && prevItem.type === 'title' && nextItem && nextItem.type === 'text') {
          // 标题和文本之间的空行，保留
          finalLines.push('<br>')
        } else if (prevItem && prevItem.type === 'text' && nextItem && nextItem.type === 'title') {
          // 文本和标题之间的空行，保留
          finalLines.push('<br>')
        }
      }
    } else {
      consecutiveEmptyLines = 0
      const escapedLine = escapeHtml(item.originalLine)
    
    if (item.type === 'title') {
        // 标题前：根据前一个元素类型决定空行
        if (prevItem) {
          if (prevItem.type === 'title') {
            // 连续标题，添加两个空行分隔
        finalLines.push('<br><br>')
          } else if (prevItem.type === 'text') {
            // 文本后接标题，添加两个空行
            finalLines.push('<br><br>')
          } else if (prevItem.type === 'empty' && i > 0 && structure[i - 2] && structure[i - 2].type === 'text') {
            // 空行前是文本，标题前已有空行，不再添加
          } else {
            // 其他情况，添加一个空行
        finalLines.push('<br>')
          }
      }
      
      // 标题本身
        finalLines.push(`<div class="text-title">${escapedLine}</div>`)
      
        // 标题后：如果后面是文本，添加一个空行
        if (nextItem && nextItem.type === 'text') {
        finalLines.push('<br>')
        } else if (nextItem && nextItem.type === 'empty' && i < structure.length - 1 && structure[i + 2] && structure[i + 2].type === 'text') {
          // 空行后是文本，标题后已有空行，不再添加
        }
    } else {
      // 普通文本
        // 如果前一个是标题且当前是文本的第一行，不需要额外空行（标题后已添加）
        if (prevItem && prevItem.type === 'title') {
          // 标题后已有空行，直接添加文本
        } else if (prevItem && prevItem.type === 'empty' && i > 1) {
          const beforeEmpty = structure[i - 2]
          if (beforeEmpty && beforeEmpty.type === 'title') {
            // 标题后的空行，文本前不需要额外空行
          } else if (beforeEmpty && beforeEmpty.type === 'text') {
            // 文本后的空行，这是段落分隔，不需要额外处理
          }
        }
        
        finalLines.push(escapedLine + '<br>')
      }
    }
  }
  
  // 移除末尾多余的空行
  while (finalLines.length > 0 && finalLines[finalLines.length - 1] === '<br>') {
    finalLines.pop()
  }
  
  return finalLines.join('')
}

// 最大渲染字符数限制（超过此限制被截断，防止源天大文本崩溃浏览器）
const MAX_RENDER_CHARS = 500_000

// 快速转义 HTML（避免频繁创建 DOM元素）
const escapeHtml = (str) => {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

// 将 renderedTranslation/renderedOriginal 改为普通 ref，由防抑 watch 驱动更新，
// 避免 computed 在流式更新时频繁重算大文本
const renderedTranslation = ref('')
const renderedOriginal = ref('')

// 防抑更新：等待 200ms 无更新后再执行渲染
let renderTranslationTimer = null
let renderOriginalTimer = null

watch(translationContent, (newVal) => {
  if (renderTranslationTimer) clearTimeout(renderTranslationTimer)
  renderTranslationTimer = setTimeout(() => {
    renderedTranslation.value = newVal ? processTextForDisplay(newVal) : ''
  }, 150)
}, { immediate: true })

watch(originalText, (newVal) => {
  if (renderOriginalTimer) clearTimeout(renderOriginalTimer)
  renderOriginalTimer = setTimeout(() => {
    renderedOriginal.value = newVal ? processTextForDisplay(newVal) : ''
  }, 150)
}, { immediate: true })

// 检测翻译是否完成
const isTranslationComplete = computed(() => {
  // 如果没有分段信息，无法判断是否完成
  if (!segmentsInfo.value || !segmentsInfo.value.totalSegments || segmentsInfo.value.totalSegments === 0) {
    return false
  }
  
  const totalSegments = segmentsInfo.value.totalSegments
  
  // 检查所有分段是否都已加载且有翻译内容
  for (let i = 0; i < totalSegments; i++) {
    // 如果分段没有翻译内容，说明未完成
    if (!segmentTranslations.value[i] || segmentTranslations.value[i].trim() === '') {
      return false
    }
  }
  
  // 所有分段都有翻译内容，且没有正在加载的分段
  return loadingSegments.value.size === 0
})

// 获取容器可见区域中心点在内容中的位置比例（0-1）
const getVisibleCenterRatio = (container) => {
  const scrollTop = container.scrollTop
  const scrollHeight = container.scrollHeight
  const clientHeight = container.clientHeight
  
  if (scrollHeight <= clientHeight) return 0.5 // 内容未超出容器，返回中间位置
  
  // 计算可见区域中心点在总内容中的位置比例
  const visibleCenter = scrollTop + clientHeight / 2
  return Math.max(0, Math.min(1, visibleCenter / scrollHeight))
}

// 根据内容比例计算目标滚动位置
const getScrollTopForRatio = (container, ratio) => {
  const scrollHeight = container.scrollHeight
  const clientHeight = container.clientHeight
  const maxScroll = scrollHeight - clientHeight
  
  if (maxScroll <= 0) return 0
  
  // 将比例转换为滚动位置，使中心点对齐
  const targetCenter = ratio * scrollHeight
  return Math.max(0, Math.min(maxScroll, targetCenter - clientHeight / 2))
}

// 改进的同步滚动处理 - 基于可见区域中心点比例映射（带防抖）
const handleOriginalScrollInternal = () => {
  if (isScrolling.value) return
  if (!originalContentRef.value || !translationContentRef.value) return
  
  isScrolling.value = true
  const originalEl = originalContentRef.value
  const translationEl = translationContentRef.value
  
  // 获取原文可见区域中心点的比例
  const originalRatio = getVisibleCenterRatio(originalEl)
  
  // 计算译文的目标滚动位置（保持相同的比例）
  const targetScrollTop = getScrollTopForRatio(translationEl, originalRatio)
  
  // 平滑滚动到目标位置
  requestAnimationFrame(() => {
    translationEl.scrollTop = targetScrollTop
    setTimeout(() => {
      isScrolling.value = false
    }, 100)
  })
}

// 使用防抖优化滚动性能
const handleOriginalScroll = debounce(handleOriginalScrollInternal, 50)

// 改进的同步滚动处理 - 基于可见区域中心点比例映射（带防抖）
const handleTranslationScrollInternal = () => {
  if (isScrolling.value) return
  if (!originalContentRef.value || !translationContentRef.value) return
  
  isScrolling.value = true
  const originalEl = originalContentRef.value
  const translationEl = translationContentRef.value
  
  // 获取译文可见区域中心点的比例
  const translationRatio = getVisibleCenterRatio(translationEl)
  
  // 计算原文的目标滚动位置（保持相同的比例）
  const targetScrollTop = getScrollTopForRatio(originalEl, translationRatio)
  
  // 平滑滚动到目标位置
  requestAnimationFrame(() => {
    originalEl.scrollTop = targetScrollTop
    setTimeout(() => {
      isScrolling.value = false
    }, 100)
  })
}

// 使用防抖优化滚动性能
const handleTranslationScroll = debounce(handleTranslationScrollInternal, 50)

// 处理文本选择，实现原文和译文的对应高亮
const handleTextSelection = (source) => {
  // 清除之前的高亮
  clearHighlights()
  
  // 清除之前的定时器
  if (highlightTimeout.value) {
    clearTimeout(highlightTimeout.value)
  }
  
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0) {
    return
  }
  
  const selectedText = selection.toString().trim()
  if (!selectedText || selectedText.length === 0) {
    return
  }
  
  const range = selection.getRangeAt(0)
  
  // 只在全屏模式下处理文本选择
  if (!isFullscreen.value) {
    return
  }
  
  const container = source === 'original' ? originalContentRef.value : translationContentRef.value
  const targetContainer = source === 'original' ? translationContentRef.value : originalContentRef.value
  
  if (!container || !targetContainer || !range.intersectsNode(container)) {
    return
  }
  
  // 获取选中文本在容器中的位置信息
  const containerRect = container.getBoundingClientRect()
  const rangeRect = range.getBoundingClientRect()
  
  // 计算选中文本在容器中的相对位置（基于垂直位置）
  const relativeTop = rangeRect.top - containerRect.top + container.scrollTop
  const relativeBottom = rangeRect.bottom - containerRect.top + container.scrollTop
  const containerHeight = container.scrollHeight
  
  // 计算对应的位置比例
  const topRatio = relativeTop / containerHeight
  const bottomRatio = relativeBottom / containerHeight
  
  // 在目标容器中找到对应位置并高亮
  highlightCorrespondingText(targetContainer, topRatio, bottomRatio, selectedText)
  
  // 保存选中信息
  selectedTextInfo.value = {
    source,
    text: selectedText,
    topRatio,
    bottomRatio
  }
  
  // 5秒后自动清除高亮
  highlightTimeout.value = setTimeout(() => {
    clearHighlights()
    selectedTextInfo.value = null
  }, 5000)
}

// 在目标容器中高亮对应位置的文本
const highlightCorrespondingText = (targetContainer, topRatio, bottomRatio, selectedText) => {
  if (!targetContainer) return
  
  // 计算目标位置
  const containerHeight = targetContainer.scrollHeight
  const targetTop = topRatio * containerHeight
  const targetBottom = bottomRatio * containerHeight
  
  // 获取目标容器中的所有元素和文本节点
  const allNodes = []
  
  // 先获取所有元素节点
  const elementWalker = document.createTreeWalker(
    targetContainer,
    NodeFilter.SHOW_ELEMENT,
    {
      acceptNode: (node) => {
        const tagName = node.tagName?.toLowerCase()
        // 跳过script、style、mark等标签
        if (tagName === 'script' || tagName === 'style' || tagName === 'mark') {
          return NodeFilter.FILTER_REJECT
        }
        return NodeFilter.FILTER_ACCEPT
      }
    },
    false
  )
  
  let elementNode
  while (elementNode = elementWalker.nextNode()) {
    const rect = elementNode.getBoundingClientRect()
    if (rect && rect.height > 0) {
      const containerRect = targetContainer.getBoundingClientRect()
      const elTop = rect.top - containerRect.top + targetContainer.scrollTop
      const elBottom = elTop + rect.height
      
      // 如果元素在目标范围内或与目标范围重叠
      if ((elTop >= targetTop && elTop <= targetBottom) ||
          (elBottom >= targetTop && elBottom <= targetBottom) ||
          (elTop <= targetTop && elBottom >= targetBottom)) {
        allNodes.push({ node: elementNode, type: 'element', top: elTop, bottom: elBottom })
      }
    }
  }
  
  // 再获取所有文本节点
  const textWalker = document.createTreeWalker(
    targetContainer,
    NodeFilter.SHOW_TEXT,
    null,
    false
  )
  
  let textNode
  while (textNode = textWalker.nextNode()) {
    // 跳过已经在高亮元素中的文本节点
    if (textNode.parentElement?.classList.contains('text-highlight') ||
        textNode.parentElement?.tagName === 'MARK') {
      continue
    }
    
    const nodeRect = getTextNodeRect(textNode)
    if (nodeRect && nodeRect.height > 0) {
      const containerRect = targetContainer.getBoundingClientRect()
      const nodeTop = nodeRect.top - containerRect.top + targetContainer.scrollTop
      const nodeBottom = nodeTop + nodeRect.height
      
      // 如果节点在目标范围内或与目标范围重叠
      if ((nodeTop >= targetTop && nodeTop <= targetBottom) ||
          (nodeBottom >= targetTop && nodeBottom <= targetBottom) ||
          (nodeTop <= targetTop && nodeBottom >= targetBottom)) {
        allNodes.push({ node: textNode, type: 'text', top: nodeTop, bottom: nodeBottom })
      }
    }
  }
  
  // 高亮找到的节点
  if (allNodes.length > 0) {
    allNodes.forEach(({ node, type }) => {
      try {
        if (type === 'text') {
          const range = document.createRange()
          range.selectNodeContents(node)
          highlightRange(range)
    } else {
          // 对于元素节点，添加高亮类
          if (!node.classList.contains('text-highlight')) {
            node.classList.add('text-highlight')
          }
        }
      } catch (e) {
        console.warn('高亮节点失败:', e)
      }
    })
  } else {
    // 如果找不到精确的节点，尝试基于位置比例高亮
    highlightByPositionRatio(targetContainer, topRatio, bottomRatio)
  }
}

// 获取文本节点的位置信息
const getTextNodeRect = (textNode) => {
  if (!textNode || !textNode.parentElement) return null
  
  const range = document.createRange()
  range.selectNodeContents(textNode)
  return range.getBoundingClientRect()
}

// 基于位置比例高亮文本
const highlightByPositionRatio = (container, topRatio, bottomRatio) => {
  if (!container) return
  
  const containerHeight = container.scrollHeight
  const targetTop = topRatio * containerHeight
  const targetBottom = bottomRatio * containerHeight
  
  // 获取容器中的所有元素
  const allElements = container.querySelectorAll('*')
  const elementsInRange = []
  
  allElements.forEach(el => {
    const rect = el.getBoundingClientRect()
    const containerRect = container.getBoundingClientRect()
    const elTop = rect.top - containerRect.top + container.scrollTop
    const elBottom = elTop + rect.height
    
    // 如果元素在目标范围内
    if ((elTop >= targetTop && elTop <= targetBottom) ||
        (elBottom >= targetTop && elBottom <= targetBottom) ||
        (elTop <= targetTop && elBottom >= targetBottom)) {
      elementsInRange.push(el)
    }
  })
  
  // 高亮找到的元素
  elementsInRange.forEach(el => {
    try {
      const range = document.createRange()
      range.selectNodeContents(el)
      highlightRange(range)
    } catch (e) {
      console.warn('高亮元素失败:', e)
    }
  })
}

// 高亮指定的范围
const highlightRange = (range) => {
  try {
    // 使用mark标签进行高亮（更语义化）
    const mark = document.createElement('mark')
    mark.className = 'text-highlight'
    
    try {
      range.surroundContents(mark)
    } catch (e) {
      // 如果surroundContents失败，尝试使用extractContents
      try {
        const contents = range.extractContents()
        mark.appendChild(contents)
        range.insertNode(mark)
      } catch (e2) {
        // 如果还是失败，尝试高亮整个父元素
        const parent = range.commonAncestorContainer
        if (parent.nodeType === Node.TEXT_NODE && parent.parentElement) {
          const parentEl = parent.parentElement
          if (!parentEl.classList.contains('text-highlight')) {
            parentEl.classList.add('text-highlight')
          }
        } else if (parent.nodeType === Node.ELEMENT_NODE) {
          const el = parent
          if (!el.classList.contains('text-highlight')) {
            el.classList.add('text-highlight')
          }
        }
      }
    }
  } catch (e) {
    console.warn('高亮范围失败:', e)
  }
}

// 清除所有高亮
const clearHighlights = () => {
  if (!originalContentRef.value || !translationContentRef.value) return
  
  // 清除原文中的高亮（移除mark标签或class）
  const originalHighlights = originalContentRef.value.querySelectorAll('mark.text-highlight, .text-highlight')
  originalHighlights.forEach(highlight => {
    if (highlight.tagName === 'MARK') {
      const parent = highlight.parentNode
      if (parent) {
        const textNode = document.createTextNode(highlight.textContent)
        parent.replaceChild(textNode, highlight)
        parent.normalize()
    }
  } else {
      highlight.classList.remove('text-highlight')
    }
  })
  
  // 清除译文中的高亮
  const translationHighlights = translationContentRef.value.querySelectorAll('mark.text-highlight, .text-highlight')
  translationHighlights.forEach(highlight => {
    if (highlight.tagName === 'MARK') {
      const parent = highlight.parentNode
      if (parent) {
        const textNode = document.createTextNode(highlight.textContent)
        parent.replaceChild(textNode, highlight)
        parent.normalize()
      }
    } else {
      highlight.classList.remove('text-highlight')
    }
  })
}

// 检测文档的主要语言
const detectDocumentLanguage = async () => {
  try {
    const textResponse = await getDocumentText(props.docId)
    let textContent = ''
    
    if (typeof textResponse === 'string') {
      textContent = textResponse
    } else if (textResponse && typeof textResponse === 'object') {
      textContent = textResponse.content || textResponse.data?.content || textResponse.text || ''
    }
    
    if (!textContent || textContent.length === 0) {
      return null
    }
    
    const sampleText = textContent.substring(0, 2000)
    if (sampleText.length < 10) {
      return null
    }
    
    let totalChars = 0
    let chineseChars = 0
    let japaneseChars = 0
    let koreanChars = 0
    let englishChars = 0
    
    for (const c of sampleText) {
      if (/\s/.test(c)) continue
      
      totalChars++
      const code = c.charCodeAt(0)
      
      if (code >= 0x4e00 && code <= 0x9fa5) {
        chineseChars++
      } else if ((code >= 0x3040 && code <= 0x309F) || (code >= 0x30A0 && code <= 0x30FF)) {
        japaneseChars++
      } else if (code >= 0xAC00 && code <= 0xD7AF) {
        koreanChars++
      } else if ((code >= 65 && code <= 90) || (code >= 97 && code <= 122)) {
        englishChars++
      }
    }
    
    if (totalChars === 0) return null
    
    const chineseRatio = chineseChars / totalChars
    const japaneseRatio = japaneseChars / totalChars
    const koreanRatio = koreanChars / totalChars
    const englishRatio = englishChars / totalChars
    
    if (chineseRatio >= 0.3) return 'zh'
    if (japaneseRatio >= 0.3) return 'ja'
    if (koreanRatio >= 0.3) return 'ko'
    if (englishRatio >= 0.3) return 'en'
    
    return null
  } catch (error) {
    console.error('检测文档语言失败:', error)
    return null
  }
}

// 翻译文档（懒加载模式：只翻译第一段）
const handleTranslate = async () => {
  if (!targetLanguage.value) {
    ElMessage.warning('请先选择目标语言')
    return
  }
  
  // 检测文档语言，检查是否为同种语言翻译
  const detectedLang = await detectDocumentLanguage()
  if (detectedLang && detectedLang === targetLanguage.value) {
    const langNames = { zh: '中文', en: '英文', ja: '日文', ko: '韩文' }
    ElMessage.warning(`不能将${langNames[detectedLang] || detectedLang}文档翻译为${langNames[targetLanguage.value] || targetLanguage.value}，翻译功能仅支持不同语言之间的翻译`)
    return
  }
  
  // 判断是否为重新翻译（已有翻译内容）
  const isRetranslate = translationContent.value || segmentTranslations.value.length > 0
  
  // 如果已有翻译内容，说明用户想要重新翻译，跳过加载已保存翻译的步骤
  // 如果没有翻译内容，先尝试加载已有翻译
  if (!isRetranslate) {
    const hasTranslation = await loadTranslation()
    if (hasTranslation) {
      // 如果已有翻译，加载分段信息并显示
      await loadSegmentsInfo()
      ElMessage.success('已加载已保存的翻译内容')
      return
    }
  }
  
  // 重新翻译：清空现有翻译内容
  translationContent.value = ''
  resetSegmentTranslations()
  
  translating.value = true
  try {
    // 初始化翻译（只翻译第一段）
    // 如果是重新翻译，传递 forceRetranslate=true 以清除旧的翻译记录
    await translateDocument(props.docId, targetLanguage.value, isRetranslate)
    
    // 获取分段信息（重新翻译后必须重新加载，因为分段可能发生变化）
    await loadSegmentsInfo()
    
    // 确保分段信息加载成功后再加载第一段
    if (!segmentsInfo.value || !segmentsInfo.value.totalSegments || segmentsInfo.value.totalSegments <= 0) {
      throw new Error('无法获取分段信息，请重试')
    }
    
    // 加载第一段的翻译
    await loadTranslationSegment(0)
    
    ElMessage.success('翻译已开始，向下滚动可自动加载后续内容')
  } catch (error) {
    ElMessage.error('翻译失败：' + (error.message || '未知错误'))
  } finally {
    translating.value = false
  }
}

// 语言切换
const handleLanguageChange = () => {
  translationContent.value = ''
  isEditing.value = false
  editContent.value = ''
}

// 编辑
const handleEdit = () => {
  editContent.value = translationContent.value
  isEditing.value = true
}

// 取消编辑
const handleCancel = () => {
  isEditing.value = false
  editContent.value = ''
}

// 保存编辑
const handleSave = async () => {
  if (!targetLanguage.value) {
    ElMessage.warning('请先选择目标语言')
    return
  }
  
  saving.value = true
  try {
    await saveDocumentTranslation(props.docId, targetLanguage.value, editContent.value)
    translationContent.value = editContent.value
    isEditing.value = false
    ElMessage.success('保存成功')
  } catch (error) {
    ElMessage.error('保存失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 加载原文
const loadOriginalText = async () => {
  if (!props.docId) return
  
  try {
    const response = await getDocumentText(props.docId)
    let content = ''
    if (typeof response === 'string') {
      content = response
    } else if (response && typeof response === 'object') {
      content = response.content || response.data?.content || ''
      if (content && typeof content !== 'string') {
        try {
          content = JSON.stringify(content)
        } catch (e) {
          content = String(content)
        }
      }
    }
    originalText.value = content || ''
  } catch (error) {
    console.error('加载原文失败:', error)
    originalText.value = ''
  }
}

// 加载翻译内容（兼容旧格式和懒加载格式）
const loadTranslation = async () => {
  if (!props.docId || !targetLanguage.value) return false
  
  try {
    // 先尝试加载分段信息
    try {
      await loadSegmentsInfo()
    } catch (error) {
      // 如果加载分段信息失败，可能是旧格式，继续尝试加载完整翻译
      console.warn('加载分段信息失败，尝试加载完整翻译:', error)
    }
    
    if (segmentsInfo.value && segmentsInfo.value.totalSegments > 0) {
      // 懒加载模式：尝试加载第一段（如果已翻译）
      // 静默失败，不显示错误消息
      try {
        await loadTranslationSegment(0)
        return loadedSegments.value.has(0)
      } catch (error) {
        // 静默失败，可能是分段还未翻译
        console.debug('加载第一段翻译失败（可能还未翻译）:', error)
        return false
      }
    } else {
      // 旧格式：加载完整翻译
      const response = await getDocumentTranslation(props.docId, targetLanguage.value)
      let content = ''
      if (typeof response === 'string') {
        content = response
      } else if (response && typeof response === 'object') {
        content = response.content || response.data?.content || ''
        if (content && typeof content !== 'string') {
          try {
            content = JSON.stringify(content)
          } catch (e) {
            content = String(content)
          }
        }
      }
      translationContent.value = content || ''
      return !!translationContent.value
    }
  } catch (error) {
    // 如果获取失败（可能是404），返回false表示没有翻译内容
    console.warn('加载翻译内容失败:', error)
    return false
  }
}

// 移除自动翻译功能，用户需要手动点击翻译按钮

// 监听docId变化，重新加载翻译
watch(() => props.docId, () => {
  if (props.docId) {
    translationContent.value = ''
    originalText.value = ''
    hasAutoTranslated.value = false
    resetSegmentTranslations()
    loadOriginalText()
    // 不再自动翻译，用户需要手动点击翻译按钮
  }
}, { immediate: false })

// 监听目标语言变化，重置翻译状态
watch(() => targetLanguage.value, () => {
  if (props.docId && targetLanguage.value) {
    translationContent.value = ''
    hasAutoTranslated.value = false
    resetSegmentTranslations()
    // 不再自动翻译，用户需要手动点击翻译按钮
  }
})

// 切换全屏
const toggleFullscreen = async () => {
  if (!translateTabRef.value) return
  
  try {
    if (!isFullscreen.value) {
      // 进入全屏前，确保已加载原文
      if (!originalText.value && props.docId) {
        await loadOriginalText()
      }
      
      // 进入全屏
      if (translateTabRef.value.requestFullscreen) {
        await translateTabRef.value.requestFullscreen()
      } else if (translateTabRef.value.webkitRequestFullscreen) {
        await translateTabRef.value.webkitRequestFullscreen()
      } else if (translateTabRef.value.mozRequestFullScreen) {
        await translateTabRef.value.mozRequestFullScreen()
      } else if (translateTabRef.value.msRequestFullscreen) {
        await translateTabRef.value.msRequestFullscreen()
      }
    } else {
      // 退出全屏
      if (document.exitFullscreen) {
        await document.exitFullscreen()
      } else if (document.webkitExitFullscreen) {
        await document.webkitExitFullscreen()
      } else if (document.mozCancelFullScreen) {
        await document.mozCancelFullScreen()
      } else if (document.msExitFullscreen) {
        await document.msExitFullscreen()
      }
    }
  } catch (error) {
    console.error('全屏操作失败:', error)
    ElMessage.error('全屏操作失败')
  }
}

// 监听全屏状态变化
const handleFullscreenChange = () => {
  isFullscreen.value = !!(
    document.fullscreenElement ||
    document.webkitFullscreenElement ||
    document.mozFullScreenElement ||
    document.msFullscreenElement
  )
}

// 组件挂载时
onMounted(async () => {
  if (props.docId) {
    // 默认模式下不加载原文，只在全屏时加载
    // loadOriginalText() // 移除自动加载
    
    // 不再自动加载翻译，用户需要手动点击翻译按钮
    // 如果用户之前有翻译，可以在选择目标语言后手动点击翻译按钮加载
  }
  
  // 监听全屏状态变化
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('webkitfullscreenchange', handleFullscreenChange)
  document.addEventListener('mozfullscreenchange', handleFullscreenChange)
  document.addEventListener('msfullscreenchange', handleFullscreenChange)
})

// 组件卸载时移除监听
onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', handleFullscreenChange)
  document.removeEventListener('mozfullscreenchange', handleFullscreenChange)
  document.removeEventListener('msfullscreenchange', handleFullscreenChange)
  
  // 清理渲染防抖定时器
  if (renderTranslationTimer) clearTimeout(renderTranslationTimer)
  if (renderOriginalTimer) clearTimeout(renderOriginalTimer)
  
  // 清除高亮和定时器
  if (highlightTimeout.value) {
    clearTimeout(highlightTimeout.value)
  }
  clearHighlights()
})
</script>

<style scoped>
.translate-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
}

.header-actions {
  display: flex;
  align-items: center;
}

.edit-actions {
  display: flex;
  gap: 8px;
}

.tab-content {
  flex: 1;
  overflow: hidden;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.edit-input {
  width: 100%;
}

.content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.loading-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  color: #909399;
}

.loading-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.loading-icon.is-loading {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.translation-content {
  flex: 1;
  overflow: auto;
  position: relative;
}

.translation-display {
  line-height: 1.8;
  color: #303133;
  max-width: 900px;
  margin: 0 auto;
  font-size: 15px;
  letter-spacing: 0.3px;
}

.translation-display :deep(.text-title) {
  font-weight: 600;
  font-size: 1.1em;
  color: var(--el-color-primary, #409eff);
  margin: 24px 0 12px 0;
  padding: 8px 0;
  border-bottom: 2px solid var(--el-color-primary-light-8, #ecf5ff);
  line-height: 1.6;
}

.translation-display :deep(.text-title:first-child) {
  margin-top: 0;
}

.translation-display :deep(p) {
  margin: 12px 0;
  line-height: 1.8;
}

.comparison-view {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.comparison-container {
  display: flex;
  flex: 1;
  overflow: hidden;
  gap: 1px;
  background: #e4e7ed;
}

.text-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color, #ffffff);
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  background: var(--el-bg-color-page, #f5f7fa);
  border-bottom: 1px solid #e4e7ed;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
  flex-shrink: 0;
}

.original-panel .panel-header {
  border-right: 1px solid #e4e7ed;
}

.text-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px;
  line-height: 1.8;
  color: var(--el-text-color-primary, #303133);
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 15px;
  letter-spacing: 0.3px;
}

.original-content {
  background: var(--el-bg-color, #ffffff);
}

.translation-content-display {
  background: var(--el-bg-color, #ffffff);
}

/* 同步滚动效果 */
.text-content {
  scrollbar-width: thin;
  scrollbar-color: #c1c1c1 #f1f1f1;
}

.text-content::-webkit-scrollbar {
  width: 8px;
}

.text-content::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.text-content::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.text-content::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 标题样式 */
.text-content :deep(.text-title) {
  font-weight: 600;
  font-size: 1.1em;
  color: var(--el-color-primary, #409eff);
  margin: 24px 0 12px 0;
  padding: 8px 0;
  border-bottom: 2px solid var(--el-color-primary-light-8, #ecf5ff);
  line-height: 1.6;
}

.text-content :deep(.text-title:first-child) {
  margin-top: 0;
}

/* 确保原文和译文样式一致 */
.original-content,
.translation-content-display {
  font-size: 15px;
  letter-spacing: 0.3px;
  line-height: 1.8;
}

.text-content :deep(p) {
  margin: 12px 0;
  line-height: 1.8;
}

/* 段落间距优化 - 确保原文和译文一致 */
.text-content :deep(br) {
  line-height: 1.8;
}

/* 确保普通文本段落样式一致 */
.text-content :deep(div:not(.text-title)) {
  margin: 0;
  padding: 0;
  line-height: 1.8;
}

.translation-display {
  line-height: 1.8;
  font-size: 15px;
  letter-spacing: 0.3px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  color: #909399;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  margin: 8px 0;
  text-align: center;
}

.tip {
  font-size: 14px;
  color: #909399;
  text-align: center;
}

.loading-segment {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 20px;
  color: var(--el-text-color-regular, #606266);
  gap: 8px;
  background: rgba(255, 255, 255, 0.95);
  border-top: 1px solid #e4e7ed;
  position: sticky;
  bottom: 0;
  z-index: 10;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
  margin-top: 20px;
}

.loading-segment .loading-icon {
  font-size: 18px;
  color: var(--el-color-primary, #409eff);
}

.loading-segment .loading-info {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin-left: 4px;
}

.translation-complete {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 20px;
  color: var(--el-color-success, #67c23a);
  gap: 8px;
  background: rgba(103, 194, 58, 0.1);
  border-top: 1px solid rgba(103, 194, 58, 0.3);
  position: sticky;
  bottom: 0;
  z-index: 10;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
  margin-top: 20px;
  border-radius: 4px;
}

.translation-complete .complete-icon {
  font-size: 18px;
  color: var(--el-color-success, #67c23a);
}

.translation-complete span {
  font-weight: 500;
}

.translation-complete {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 20px;
  color: var(--el-color-success, #67c23a);
  gap: 8px;
  background: rgba(103, 194, 58, 0.1);
  border-top: 1px solid rgba(103, 194, 58, 0.3);
  position: sticky;
  bottom: 0;
  z-index: 10;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
  margin-top: 20px;
  border-radius: 4px;
}

.translation-complete .complete-icon {
  font-size: 18px;
  color: var(--el-color-success, #67c23a);
}

.translation-complete span {
  font-weight: 500;
}

/* 全屏模式样式 */
.translate-tab.fullscreen-mode {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100vw;
  height: 100vh;
  z-index: 9999;
  background: var(--el-bg-color, #ffffff);
}

.translate-tab.fullscreen-mode .tab-content {
  height: calc(100vh - 60px);
}

/* 文本高亮样式 */
.text-content :deep(mark.text-highlight),
.text-content :deep(.text-highlight) {
  background-color: rgba(255, 235, 59, 0.5) !important;
  padding: 2px 0;
  border-radius: 2px;
  transition: background-color 0.3s ease;
  cursor: pointer;
}

.text-content :deep(mark.text-highlight:hover),
.text-content :deep(.text-highlight:hover) {
  background-color: rgba(255, 235, 59, 0.7) !important;
}

.translation-display :deep(mark.text-highlight),
.translation-display :deep(.text-highlight) {
  background-color: rgba(255, 235, 59, 0.5) !important;
  padding: 2px 0;
  border-radius: 2px;
  transition: background-color 0.3s ease;
  cursor: pointer;
}

.translation-display :deep(mark.text-highlight:hover),
.translation-display :deep(.text-highlight:hover) {
  background-color: rgba(255, 235, 59, 0.7) !important;
}
</style>

