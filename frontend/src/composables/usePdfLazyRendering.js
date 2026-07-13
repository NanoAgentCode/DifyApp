import { computed, nextTick, ref } from 'vue'

const INITIAL_PDF_PAGES = 3
const PDF_PAGES_PER_BATCH = 5

export function usePdfLazyRendering({ onTotalPages, onFirstPageRendered }) {
  const pdfTotalPages = ref(1)
  const visiblePageCount = ref(INITIAL_PDF_PAGES)
  const loadingMorePages = ref(false)
  const pdfEmbedRef = ref(null)
  const pdfViewerWrapperRef = ref(null)
  const pdfPlaceholderRef = ref(null)
  let pdfLazyObserver = null

  const visiblePdfPageNumbers = computed(() => {
    const count = Math.min(visiblePageCount.value, pdfTotalPages.value)
    return Array.from({ length: count }, (_, index) => index + 1)
  })

  const placeholderHeight = computed(() => {
    const unrenderedPages = pdfTotalPages.value - visiblePageCount.value
    return unrenderedPages > 0 ? `${unrenderedPages * 1100}px` : '0px'
  })

  const cleanupPdfLazyLoading = () => {
    if (pdfLazyObserver) {
      pdfLazyObserver.disconnect()
      pdfLazyObserver = null
    }
  }

  const setPdfTotalPages = (totalPages) => {
    if (!Number.isInteger(totalPages) || totalPages <= 0) return

    pdfTotalPages.value = totalPages
    onTotalPages(totalPages)
  }

  const resetPdfLazyRendering = () => {
    cleanupPdfLazyLoading()
    pdfTotalPages.value = 1
    visiblePageCount.value = INITIAL_PDF_PAGES
    loadingMorePages.value = false
  }

  const setPdfEmbedRef = (element) => {
    if (element) {
      pdfEmbedRef.value = element
    }
  }

  const initPdfLazyLoading = () => {
    cleanupPdfLazyLoading()

    if (!pdfPlaceholderRef.value || pdfTotalPages.value <= visiblePageCount.value) return

    pdfLazyObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting && !loadingMorePages.value) {
            loadMorePdfPages()
          }
        })
      },
      {
        root: pdfViewerWrapperRef.value,
        rootMargin: '400px 0px',
        threshold: 0
      }
    )

    pdfLazyObserver.observe(pdfPlaceholderRef.value)
  }

  const loadMorePdfPages = () => {
    if (loadingMorePages.value) return
    if (visiblePageCount.value >= pdfTotalPages.value) {
      cleanupPdfLazyLoading()
      return
    }

    loadingMorePages.value = true
    visiblePageCount.value = Math.min(
      visiblePageCount.value + PDF_PAGES_PER_BATCH,
      pdfTotalPages.value
    )

    nextTick(() => {
      loadingMorePages.value = false
      if (visiblePageCount.value < pdfTotalPages.value && pdfPlaceholderRef.value && pdfLazyObserver) {
        pdfLazyObserver.observe(pdfPlaceholderRef.value)
      } else {
        cleanupPdfLazyLoading()
      }
    })
  }

  const handlePdfPageRendered = (pageNum, info) => {
    if (pageNum !== 1) return

    onFirstPageRendered()
    try {
      const totalPages = pdfEmbedRef.value?.pdf?.numPages || info?.numPages
      if (totalPages) {
        setPdfTotalPages(totalPages)
        if (visiblePageCount.value >= totalPages) {
          visiblePageCount.value = INITIAL_PDF_PAGES
        }
        nextTick(initPdfLazyLoading)
        return
      }

      if (pdfEmbedRef.value) {
        setTimeout(() => {
          const delayedTotalPages = pdfEmbedRef.value?.pdf?.numPages
          if (delayedTotalPages) {
            setPdfTotalPages(delayedTotalPages)
            nextTick(initPdfLazyLoading)
          }
        }, 100)
      }
    } catch (error) {
      console.warn('获取PDF总页数失败:', error)
    }
  }

  return {
    pdfTotalPages,
    visiblePageCount,
    loadingMorePages,
    pdfEmbedRef,
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
  }
}
