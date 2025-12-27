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
              ></div>
            </div>
            <div class="text-panel translation-panel">
              <div class="panel-header">译文</div>
              <div 
                ref="translationContentRef"
                class="text-content translation-content-display" 
                v-html="renderedTranslation"
                @scroll="handleTranslationScroll"
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

// 处理文本，识别并标记标题，过滤多余空行，为标题添加对齐空行
function processTextForDisplay(text) {
  if (!text) return ''
  
  // 转义HTML
  const escapeHtml = (str) => {
    const div = document.createElement('div')
    div.textContent = str
    return div.innerHTML
  }
  
  // 按行分割
  const lines = text.split('\n')
  
  // 第一步：识别标题并标记
  const processedLines = []
  let lastWasEmpty = false
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()
    
    // 检查是否是空行
    if (trimmed.length === 0) {
      // 过滤连续的空行：如果上一个也是空行，跳过
      if (!lastWasEmpty) {
        processedLines.push({ type: 'empty', originalIndex: i })
        lastWasEmpty = true
      }
      continue
    }
    
    lastWasEmpty = false
    
    // 转义当前行的HTML
    const escapedLine = escapeHtml(line)
    
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
    
    if (isTitle) {
      processedLines.push({ type: 'title', content: escapedLine, originalIndex: i })
    } else {
      processedLines.push({ type: 'text', content: escapedLine, originalIndex: i })
    }
  }
  
  // 第二步：为标题添加对齐空行，过滤多余空行
  const finalLines = []
  for (let i = 0; i < processedLines.length; i++) {
    const item = processedLines[i]
    const prevItem = i > 0 ? processedLines[i - 1] : null
    const nextItem = i < processedLines.length - 1 ? processedLines[i + 1] : null
    
    if (item.type === 'title') {
      // 标题前：如果前面不是空行且不是另一个标题，添加一个空行
      if (prevItem && prevItem.type === 'title') {
        // 如果前一个也是标题，添加两个空行以分隔
        finalLines.push('<br><br>')
      } else if (!prevItem || prevItem.type !== 'empty') {
        // 如果前面不是空行，添加一个空行
        finalLines.push('<br>')
      }
      
      // 标题本身
      finalLines.push(`<div class="text-title">${item.content}</div>`)
      
      // 标题后：如果后面不是空行，添加一个空行
      if (!nextItem || (nextItem.type !== 'empty' && nextItem.type !== 'title')) {
        finalLines.push('<br>')
      }
    } else if (item.type === 'empty') {
      // 空行：如果前后都是文本，保留一个空行用于段落分隔
      // 但如果前后有标题，则跳过（标题已经自带空行）
      if (prevItem && nextItem && prevItem.type === 'text' && nextItem.type === 'text') {
        finalLines.push('<br>')
      }
      // 其他情况跳过空行（标题已经处理了空行）
    } else {
      // 普通文本
      finalLines.push(item.content + '<br>')
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

// 同步滚动处理 - 改进版本，基于标题对齐
const handleOriginalScroll = () => {
  if (isScrolling.value) return
  if (!originalContentRef.value || !translationContentRef.value) return
  
  isScrolling.value = true
  const originalEl = originalContentRef.value
  const translationEl = translationContentRef.value
  
  // 获取当前可见区域中的第一个标题元素
  const originalTitles = originalEl.querySelectorAll('.text-title')
  let nearestTitleIndex = -1
  let minDistance = Infinity
  
  originalTitles.forEach((title, index) => {
    const rect = title.getBoundingClientRect()
    const containerRect = originalEl.getBoundingClientRect()
    
    // 如果标题在可见区域内或刚刚离开可见区域顶部
    if (rect.top >= containerRect.top - 50 && rect.top <= containerRect.top + 100) {
      const distance = Math.abs(rect.top - containerRect.top)
      if (distance < minDistance) {
        minDistance = distance
        nearestTitleIndex = index
      }
    }
  })
  
  // 如果找到了对应的标题，尝试在译文中找到对应的标题并滚动到相同位置
  if (nearestTitleIndex >= 0) {
    const translationTitles = translationEl.querySelectorAll('.text-title')
    if (translationTitles[nearestTitleIndex]) {
      const targetTitle = translationTitles[nearestTitleIndex]
      const targetRect = targetTitle.getBoundingClientRect()
      const containerRect = translationEl.getBoundingClientRect()
      
      // 计算需要滚动的距离，使标题对齐
      const scrollOffset = targetRect.top - containerRect.top + translationEl.scrollTop
      translationEl.scrollTop = scrollOffset
    } else {
      // 如果找不到对应标题，使用比例滚动
      const scrollRatio = originalEl.scrollTop / (originalEl.scrollHeight - originalEl.clientHeight)
      const targetScrollTop = scrollRatio * (translationEl.scrollHeight - translationEl.clientHeight)
      translationEl.scrollTop = targetScrollTop
    }
  } else {
    // 没有找到标题，使用比例滚动
    const scrollRatio = originalEl.scrollTop / (originalEl.scrollHeight - originalEl.clientHeight)
    const targetScrollTop = scrollRatio * (translationEl.scrollHeight - translationEl.clientHeight)
    translationEl.scrollTop = targetScrollTop
  }
  
  setTimeout(() => {
    isScrolling.value = false
  }, 50)
}

const handleTranslationScroll = () => {
  if (isScrolling.value) return
  if (!originalContentRef.value || !translationContentRef.value) return
  
  isScrolling.value = true
  const originalEl = originalContentRef.value
  const translationEl = translationContentRef.value
  
  // 获取当前可见区域中的第一个标题元素
  const translationTitles = translationEl.querySelectorAll('.text-title')
  let nearestTitleIndex = -1
  let minDistance = Infinity
  
  translationTitles.forEach((title, index) => {
    const rect = title.getBoundingClientRect()
    const containerRect = translationEl.getBoundingClientRect()
    
    // 如果标题在可见区域内或刚刚离开可见区域顶部
    if (rect.top >= containerRect.top - 50 && rect.top <= containerRect.top + 100) {
      const distance = Math.abs(rect.top - containerRect.top)
      if (distance < minDistance) {
        minDistance = distance
        nearestTitleIndex = index
      }
    }
  })
  
  // 如果找到了对应的标题，尝试在原文中找到对应的标题并滚动到相同位置
  if (nearestTitleIndex >= 0) {
    const originalTitles = originalEl.querySelectorAll('.text-title')
    if (originalTitles[nearestTitleIndex]) {
      const targetTitle = originalTitles[nearestTitleIndex]
      const targetRect = targetTitle.getBoundingClientRect()
      const containerRect = originalEl.getBoundingClientRect()
      
      // 计算需要滚动的距离，使标题对齐
      const scrollOffset = targetRect.top - containerRect.top + originalEl.scrollTop
      originalEl.scrollTop = scrollOffset
    } else {
      // 如果找不到对应标题，使用比例滚动
      const scrollRatio = translationEl.scrollTop / (translationEl.scrollHeight - translationEl.clientHeight)
      const targetScrollTop = scrollRatio * (originalEl.scrollHeight - originalEl.clientHeight)
      originalEl.scrollTop = targetScrollTop
    }
  } else {
    // 没有找到标题，使用比例滚动
    const scrollRatio = translationEl.scrollTop / (translationEl.scrollHeight - translationEl.clientHeight)
    const targetScrollTop = scrollRatio * (originalEl.scrollHeight - originalEl.clientHeight)
    originalEl.scrollTop = targetScrollTop
  }
  
  setTimeout(() => {
    isScrolling.value = false
  }, 50)
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
  if (loadingSegments.value.has(segmentIndex) || loadedSegments.value.has(segmentIndex)) {
    return // 正在加载或已加载
  }
  
  if (!targetLanguage.value) {
    console.warn('未选择目标语言，无法加载翻译分段')
    return
  }
  
  if (!segmentsInfo.value || segmentIndex >= segmentsInfo.value.totalSegments) {
    return // 索引无效
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
      
      // 更新显示的翻译内容
      updateTranslationDisplay()
    }
  } catch (error) {
    console.error(`加载分段 ${segmentIndex} 翻译失败:`, error)
    // 只在用户主动操作时显示错误消息，避免自动加载时的错误提示
    // 错误消息会在用户点击翻译按钮时显示
  } finally {
    loadingSegments.value.delete(segmentIndex)
  }
}

// 更新翻译显示内容
const updateTranslationDisplay = () => {
  const translatedParts = []
  for (let i = 0; i < segmentTranslations.value.length; i++) {
    if (segmentTranslations.value[i]) {
      translatedParts.push(segmentTranslations.value[i])
    } else if (loadedSegments.value.has(i)) {
      // 已加载但为空，可能是加载失败
      translatedParts.push('')
    }
  }
  translationContent.value = translatedParts.join('\n\n')
}

// 处理翻译内容滚动（懒加载）- 使用防抖优化
const handleTranslationScrollForLazyLoad = debounce(() => {
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
  
  // 计算当前滚动位置对应的分段索引
  // 使用更准确的计算方式：基于已加载内容的高度比例
  const scrollRatio = scrollTop / (scrollHeight - clientHeight)
  const currentSegmentIndex = Math.min(
    Math.floor(scrollRatio * segmentsInfo.value.totalSegments),
    segmentsInfo.value.totalSegments - 1
  )
  
  // 预加载当前分段及后续2个分段
  const preloadCount = 3
  for (let i = 0; i < preloadCount; i++) {
    const segmentIndex = currentSegmentIndex + i
    if (segmentIndex >= 0 && segmentIndex < segmentsInfo.value.totalSegments) {
      if (!loadedSegments.value.has(segmentIndex) && !loadingSegments.value.has(segmentIndex)) {
        loadTranslationSegment(segmentIndex)
      }
    }
  }
}, 300) // 300ms防抖

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

/* 增强对比效果 */
.text-content {
  font-size: 15px;
  letter-spacing: 0.3px;
}

.text-content :deep(p) {
  margin: 12px 0;
  line-height: 1.8;
}

/* 段落间距优化 */
.text-content :deep(br) {
  line-height: 1.8;
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
</style>

