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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Switch, Refresh, Loading, Document, Edit, FullScreen, Close, Check } from '@element-plus/icons-vue'
import { translateDocument, getDocumentTranslation, saveDocumentTranslation, getDocumentText, getDocumentSegments, translateDocumentSegment, getDocumentTranslationRange } from '@/api/documentReader'
import { renderMarkdown } from '@/composables/useMarkdown'
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

// 懒加载相关状态
const segmentsInfo = ref(null) // 分段信息
const loadedSegments = ref(new Set()) // 已加载的分段索引
const loadingSegments = ref(new Set()) // 正在加载的分段索引
const segmentTranslations = ref([]) // 分段翻译内容数组
const translationContainerRef = ref(null) // 翻译内容容器引用
const isUpdatingContent = ref(false) // 标记是否正在更新内容，防止滚动事件触发
const lastScrollTop = ref(0) // 记录上次滚动位置
const lastScrollTime = ref(0) // 记录上次滚动时间
const selectedTextInfo = ref(null) // 选中的文本信息
const highlightTimeout = ref(null) // 高亮清除定时器
const pageHeight = ref(0) // 页面高度（用于页面对齐）

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
function processTextForDisplay(text, preserveStructure = true) {
  if (!text) return ''
  
  // 转义HTML
  const escapeHtml = (str) => {
    const div = document.createElement('div')
    div.textContent = str
    return div.innerHTML
  }
  
  // 分析文本结构
  const { lines, structure } = analyzeTextStructure(text)
  
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

const renderedTranslation = computed(() => {
  if (!translationContent.value) return ''
  return processTextForDisplay(translationContent.value)
})

const renderedOriginal = computed(() => {
  if (!originalText.value) return ''
  return processTextForDisplay(originalText.value)
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
  
  // 如果已有翻译内容，说明用户想要重新翻译，跳过加载已保存翻译的步骤
  // 如果没有翻译内容，先尝试加载已有翻译
  if (!translationContent.value && segmentTranslations.value.length === 0) {
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
  segmentsInfo.value = null
  loadedSegments.value.clear()
  loadingSegments.value.clear()
  segmentTranslations.value = []
  
  translating.value = true
  try {
    // 初始化翻译（只翻译第一段）
    await translateDocument(props.docId, targetLanguage.value)
    
    // 获取分段信息
    await loadSegmentsInfo()
    
    // 加载第一段的翻译
    await loadTranslationSegment(0)
    
    ElMessage.success('翻译已开始，向下滚动可自动加载后续内容')
  } catch (error) {
    ElMessage.error('翻译失败：' + (error.message || '未知错误'))
  } finally {
    translating.value = false
  }
}

// 加载分段信息
const loadSegmentsInfo = async () => {
  try {
    const response = await getDocumentSegments(props.docId)
    segmentsInfo.value = response
    // 初始化分段翻译数组
    if (segmentsInfo.value && segmentsInfo.value.totalSegments) {
      segmentTranslations.value = new Array(segmentsInfo.value.totalSegments).fill(null)
    }
  } catch (error) {
    console.error('加载分段信息失败:', error)
  }
}

// 加载指定分段的翻译
const loadTranslationSegment = async (segmentIndex) => {
  // 严格检查索引有效性
  if (!segmentsInfo.value || 
      segmentIndex < 0 || 
      segmentIndex >= segmentsInfo.value.totalSegments) {
    console.warn(`分段索引无效: ${segmentIndex}, 总段数: ${segmentsInfo.value?.totalSegments || 0}`)
    return // 索引无效
  }
  
  if (loadingSegments.value.has(segmentIndex) || loadedSegments.value.has(segmentIndex)) {
    return // 正在加载或已加载
  }
  
  if (!targetLanguage.value) {
    console.warn('未选择目标语言，无法加载翻译分段')
    return
  }
  
  // 如果正在更新内容，延迟加载
  if (isUpdatingContent.value) {
    setTimeout(() => {
      loadTranslationSegment(segmentIndex)
    }, 100)
    return
  }
  
  loadingSegments.value.add(segmentIndex)
  
  try {
    const response = await translateDocumentSegment(props.docId, targetLanguage.value, segmentIndex)
    let content = ''
    if (typeof response === 'string') {
      content = response
    } else if (response && typeof response === 'object') {
      content = response.content || response.data?.content || ''
    }
    
    if (content) {
      segmentTranslations.value[segmentIndex] = content
      loadedSegments.value.add(segmentIndex)
      
      // 批量更新：延迟更新显示，避免频繁更新导致卡顿
      if (!isUpdatingContent.value) {
        // 使用 requestAnimationFrame 优化更新时机
        requestAnimationFrame(() => {
      updateTranslationDisplay()
        })
      }
    }
  } catch (error) {
    console.error(`加载分段 ${segmentIndex} 翻译失败:`, error)
    // 如果是因为索引超出范围，不再尝试加载
    if (error.message && (error.message.includes('分段索引无效') || error.message.includes('索引无效'))) {
      console.warn(`分段索引 ${segmentIndex} 超出范围，停止加载`)
      // 从加载集合中移除，避免重复尝试
      loadedSegments.value.add(segmentIndex) // 标记为已处理，避免重复请求
    }
    // 只在用户主动操作时显示错误消息，避免自动加载时的错误提示
    // 错误消息会在用户点击翻译按钮时显示
  } finally {
    loadingSegments.value.delete(segmentIndex)
  }
}

// 智能合并分段翻译，保持段落结构
const smartJoinSegments = (segments) => {
  if (segments.length === 0) return ''
  
  const result = []
  for (let i = 0; i < segments.length; i++) {
    const segment = segments[i]
    if (!segment) continue
    
    // 检查分段是否为空（只包含空白字符）
    const trimmed = segment.trim()
    if (trimmed === '') {
      // 空分段，如果前一个分段存在，添加一个换行
      if (i > 0 && segments[i - 1] && segments[i - 1].trim()) {
        // 检查是否已经有换行，避免重复
        if (result.length > 0 && !result[result.length - 1].endsWith('\n\n')) {
          result.push('\n')
        }
      }
      continue
    }
    
    // 检查分段边界，智能添加分隔符
    if (result.length > 0) {
      const prevSegment = segments[i - 1]
      if (prevSegment && prevSegment.trim()) {
        const prevTrimmed = prevSegment.trim()
        const prevEndsWithPunctuation = /[。.！!？?]$/.test(prevTrimmed)
        const prevEndsWithNewline = prevSegment.endsWith('\n') || prevSegment.endsWith('\n\n')
        const currentStartsWithNewline = segment.startsWith('\n')
        
        // 如果前一个分段以换行结尾，或当前分段以换行开头
        if (prevEndsWithNewline || currentStartsWithNewline) {
          // 检查是否需要额外的段落分隔
          if (prevEndsWithPunctuation && !currentStartsWithNewline) {
            result.push('\n\n')
          } else {
            result.push('\n')
          }
        } else {
          // 根据标点符号判断是否需要段落分隔
          if (prevEndsWithPunctuation) {
            // 前一个分段以标点结尾，使用双换行作为段落分隔
            result.push('\n\n')
          } else {
            // 其他情况，使用单换行
            result.push('\n')
          }
        }
      } else {
        // 前一个分段为空，添加单换行
        result.push('\n')
      }
    }
    
    // 添加当前分段（保留原始格式，但移除首尾空白）
    result.push(trimmed)
  }
  
  return result.join('')
}

// 更新翻译显示内容
const updateTranslationDisplay = () => {
  if (isUpdatingContent.value) {
    return // 防止重复更新
  }
  
  isUpdatingContent.value = true
  
  // 保存当前滚动位置
  const container = translationContainerRef.value
  const savedScrollTop = container ? container.scrollTop : 0
  
  const translatedParts = []
  for (let i = 0; i < segmentTranslations.value.length; i++) {
    if (segmentTranslations.value[i]) {
      translatedParts.push(segmentTranslations.value[i])
    } else if (loadedSegments.value.has(i)) {
      // 已加载但为空，可能是加载失败，跳过
      continue
    }
  }
  
  // 使用智能合并，保持段落结构
  translationContent.value = smartJoinSegments(translatedParts)
  
  // 在下一个tick恢复滚动位置
  setTimeout(() => {
    if (container) {
      // 恢复滚动位置，但允许向下滚动（如果内容增加了）
      const newScrollHeight = container.scrollHeight
      const maxScrollTop = newScrollHeight - container.clientHeight
      container.scrollTop = Math.min(savedScrollTop, maxScrollTop)
    }
    isUpdatingContent.value = false
  }, 0)
}

// 处理翻译内容滚动（懒加载）- 使用防抖和节流优化
const handleTranslationScrollForLazyLoad = debounce(() => {
  // 如果正在更新内容，跳过滚动处理
  if (isUpdatingContent.value) {
    return
  }
  
  if (!translationContainerRef.value || !segmentsInfo.value || segmentsInfo.value.totalSegments === 0) {
    return
  }
  
  const container = translationContainerRef.value
  const scrollTop = container.scrollTop
  const scrollHeight = container.scrollHeight
  const clientHeight = container.clientHeight
  
  // 如果内容高度小于容器高度，不需要懒加载
  if (scrollHeight <= clientHeight) {
    return
  }
  
  // 检查滚动是否真的发生了变化（避免重复处理）
  const now = Date.now()
  if (Math.abs(scrollTop - lastScrollTop.value) < 10 && now - lastScrollTime.value < 100) {
    return // 滚动位置变化很小且时间间隔很短，跳过
  }
  
  lastScrollTop.value = scrollTop
  lastScrollTime.value = now
  
  // 计算当前滚动位置对应的分段索引（页面索引）
  // 使用更准确的计算方式：基于已加载内容的高度比例
  const scrollRatio = (scrollHeight - clientHeight) > 0 
    ? scrollTop / (scrollHeight - clientHeight) 
    : 0
  
  // 确保分段索引在有效范围内
  if (!segmentsInfo.value || !segmentsInfo.value.totalSegments || segmentsInfo.value.totalSegments <= 0) {
    return
  }
  
  // 确保 scrollRatio 是有效数字
  if (isNaN(scrollRatio) || !isFinite(scrollRatio)) {
    return
  }
  
  // 限制 scrollRatio 在 [0, 1] 范围内
  const clampedRatio = Math.max(0, Math.min(1, scrollRatio))
  const currentSegmentIndex = Math.min(
    Math.max(0, Math.floor(clampedRatio * segmentsInfo.value.totalSegments)),
    segmentsInfo.value.totalSegments - 1
  )
  
  // 预加载当前分段及后续2个分段（减少预加载数量，避免一次性加载太多）
  const preloadCount = 2
  let loadedCount = 0
  for (let i = 0; i < preloadCount && loadedCount < 2; i++) {
    const segmentIndex = currentSegmentIndex + i
    // 严格检查索引范围
    if (segmentIndex >= 0 && segmentIndex < segmentsInfo.value.totalSegments) {
      if (!loadedSegments.value.has(segmentIndex) && !loadingSegments.value.has(segmentIndex)) {
        loadTranslationSegment(segmentIndex)
        loadedCount++
      }
    }
  }
}, 500) // 增加防抖时间到500ms，减少触发频率

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
    // 重置懒加载状态
    segmentsInfo.value = null
    loadedSegments.value.clear()
    loadingSegments.value.clear()
    segmentTranslations.value = []
    loadOriginalText()
    // 不再自动翻译，用户需要手动点击翻译按钮
  }
}, { immediate: false })

// 监听目标语言变化，重置翻译状态
watch(() => targetLanguage.value, () => {
  if (props.docId && targetLanguage.value) {
    translationContent.value = ''
    hasAutoTranslated.value = false
    // 重置懒加载状态
    segmentsInfo.value = null
    loadedSegments.value.clear()
    loadingSegments.value.clear()
    segmentTranslations.value = []
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
  padding: 20px;
  color: var(--el-text-color-regular, #606266);
  gap: 8px;
}

.loading-segment .loading-icon {
  font-size: 16px;
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

