import { ref } from 'vue'
import { getDocumentSegments, translateDocumentSegment } from '@/api/documentReader'
import { debounce } from '@/utils/debounce'

export function useDocumentSegmentTranslation({ docId, targetLanguage, translationContent, translationContainerRef, onSegmentError }) {
  const segmentsInfo = ref(null)
  const loadedSegments = ref(new Set())
  const loadingSegments = ref(new Set())
  const segmentTranslations = ref([])
  const isUpdatingContent = ref(false)
  const lastScrollTop = ref(0)
  const lastScrollTime = ref(0)

  const resetSegmentTranslations = () => {
    segmentsInfo.value = null
    loadedSegments.value.clear()
    loadingSegments.value.clear()
    segmentTranslations.value = []
    isUpdatingContent.value = false
  }

  const loadSegmentsInfo = async () => {
    try {
      const response = await getDocumentSegments(docId.value)
      segmentsInfo.value = response
      if (segmentsInfo.value?.totalSegments) {
        segmentTranslations.value = new Array(segmentsInfo.value.totalSegments).fill(null)
      }
    } catch (error) {
      console.error('加载分段信息失败:', error)
    }
  }

  const smartJoinSegments = (segments) => {
    if (segments.length === 0) return ''

    const result = []
    for (let index = 0; index < segments.length; index++) {
      const segment = segments[index]
      if (!segment) continue

      const trimmed = segment.trim()
      if (trimmed === '') {
        if (index > 0 && segments[index - 1] && segments[index - 1].trim() && result.length > 0 && !result[result.length - 1].endsWith('\n\n')) {
          result.push('\n')
        }
        continue
      }

      if (result.length > 0) {
        const previous = segments[index - 1]
        if (previous && previous.trim()) {
          const previousTrimmed = previous.trim()
          const previousEndsWithPunctuation = /[。.！!？?]$/.test(previousTrimmed)
          const previousEndsWithNewline = previous.endsWith('\n') || previous.endsWith('\n\n')
          const currentStartsWithNewline = segment.startsWith('\n')
          result.push(previousEndsWithNewline || currentStartsWithNewline
            ? (previousEndsWithPunctuation && !currentStartsWithNewline ? '\n\n' : '\n')
            : (previousEndsWithPunctuation ? '\n\n' : '\n'))
        } else {
          result.push('\n')
        }
      }
      result.push(trimmed)
    }
    return result.join('')
  }

  const updateTranslationDisplay = () => {
    if (isUpdatingContent.value) return

    isUpdatingContent.value = true
    const container = translationContainerRef.value
    const savedScrollTop = container ? container.scrollTop : 0
    const totalSegments = segmentsInfo.value?.totalSegments || segmentTranslations.value.length

    if (segmentTranslations.value.length < totalSegments) {
      const oldLength = segmentTranslations.value.length
      segmentTranslations.value.length = totalSegments
      for (let index = oldLength; index < totalSegments; index++) {
        if (segmentTranslations.value[index] === undefined) {
          segmentTranslations.value[index] = null
        }
      }
    }

    const translatedParts = segmentTranslations.value
      .slice(0, totalSegments)
      .filter((content) => typeof content === 'string' && content.trim() !== '')

    translationContent.value = smartJoinSegments(translatedParts)
    setTimeout(() => {
      if (container) {
        container.scrollTop = Math.min(savedScrollTop, container.scrollHeight - container.clientHeight)
      }
      isUpdatingContent.value = false
    }, 0)
  }

  const loadTranslationSegment = async (segmentIndex) => {
    const totalSegments = segmentsInfo.value?.totalSegments
    if (!Number.isInteger(segmentIndex) || segmentIndex < 0 || !Number.isInteger(totalSegments) || totalSegments <= 0 || segmentIndex >= totalSegments) {
      return
    }
    if (loadingSegments.value.has(segmentIndex) || !targetLanguage.value) return

    if (loadedSegments.value.has(segmentIndex)) {
      if (segmentTranslations.value[segmentIndex] && !isUpdatingContent.value) {
        requestAnimationFrame(updateTranslationDisplay)
      }
      return
    }
    if (isUpdatingContent.value) {
      setTimeout(() => loadTranslationSegment(segmentIndex), 100)
      return
    }

    loadingSegments.value.add(segmentIndex)
    try {
      const response = await translateDocumentSegment(docId.value, targetLanguage.value, segmentIndex)
      const content = typeof response === 'string' ? response : (response?.content || response?.data?.content || '')
      if (content && content.trim()) {
        if (segmentTranslations.value.length < totalSegments) {
          segmentTranslations.value.length = totalSegments
        }
        segmentTranslations.value[segmentIndex] = content
        loadedSegments.value.add(segmentIndex)
        requestAnimationFrame(updateTranslationDisplay)
      } else {
        loadedSegments.value.add(segmentIndex)
      }
    } catch (error) {
      console.error(`加载分段 ${segmentIndex} 翻译失败:`, error)
      if (error.message?.includes('分段索引无效') || error.message?.includes('索引无效')) {
        loadedSegments.value.add(segmentIndex)
      } else if (error.message?.includes('文档不存在')) {
        onSegmentError?.('文档不存在，请刷新页面重试')
      } else if (error.message?.includes('未配置可用的模型')) {
        onSegmentError?.('未配置翻译模型，请联系管理员')
      }
    } finally {
      loadingSegments.value.delete(segmentIndex)
    }
  }

  const handleTranslationScrollForLazyLoad = debounce(() => {
    if (isUpdatingContent.value || !translationContainerRef.value || !segmentsInfo.value?.totalSegments) return

    const container = translationContainerRef.value
    const { scrollTop, scrollHeight, clientHeight } = container
    if (scrollHeight <= clientHeight) return

    const now = Date.now()
    if (Math.abs(scrollTop - lastScrollTop.value) < 10 && now - lastScrollTime.value < 100) return
    lastScrollTop.value = scrollTop
    lastScrollTime.value = now

    const totalSegments = segmentsInfo.value.totalSegments
    const ratio = (scrollHeight - clientHeight) > 0 ? scrollTop / (scrollHeight - clientHeight) : 0
    const currentSegmentIndex = Math.min(Math.max(0, Math.floor(Math.max(0, Math.min(1, ratio)) * totalSegments)), totalSegments - 1)
    const isNearBottom = scrollHeight - (scrollTop + clientHeight) < 100

    if (isNearBottom) {
      const loadedIndices = Array.from(loadedSegments.value).filter((index) => Number.isInteger(index) && index >= 0 && index < totalSegments)
      const startIndex = loadedIndices.length > 0 ? Math.max(currentSegmentIndex, Math.max(...loadedIndices) + 1) : currentSegmentIndex
      for (let index = startIndex; index < Math.min(startIndex + 5, totalSegments); index++) {
        if (!loadedSegments.value.has(index) && !loadingSegments.value.has(index)) {
          loadTranslationSegment(index)
          break
        }
      }
      return
    }

    for (let offset = 0; offset < 2; offset++) {
      const segmentIndex = currentSegmentIndex + offset
      if (segmentIndex < totalSegments && !loadedSegments.value.has(segmentIndex) && !loadingSegments.value.has(segmentIndex)) {
        loadTranslationSegment(segmentIndex)
      }
    }
  }, 500)

  return {
    segmentsInfo,
    loadedSegments,
    loadingSegments,
    segmentTranslations,
    resetSegmentTranslations,
    loadSegmentsInfo,
    loadTranslationSegment,
    handleTranslationScrollForLazyLoad
  }
}
