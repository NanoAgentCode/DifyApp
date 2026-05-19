import router from '@/router'
import { collectRegisteredAssistantContext } from '@/composables/useAssistantContext'

const MAX_SELECTION_LENGTH = 2000
const MAX_SECTION_LENGTH = 4000
const MAX_TOTAL_LENGTH = 10000

const MAIN_SELECTORS = [
  '.main-content:not(.portal-content)',
  '.portal-content',
  '.app-main',
  'main',
  '#app'
]

const NOISE_SELECTORS = [
  '.el-header',
  '.app-header',
  '.home-floating-button',
  '.global-assistant',
  '.assistant-drawer',
  '.el-overlay',
  '.el-drawer',
  '.el-dialog',
  '.el-message',
  '.el-notification',
  'script',
  'style',
  'noscript'
]

function compactText(text = '') {
  return String(text)
    .replace(/\u00a0/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function truncate(text, maxLength) {
  if (!text || text.length <= maxLength) return text
  return `${text.slice(0, Math.max(0, maxLength - 12))}...[已截断]`
}

function getCurrentPageInfo() {
  const route = router.currentRoute.value
  return {
    route: route?.fullPath || window.location.pathname,
    title: document.title || route?.meta?.title || '',
    type: route?.name ? String(route.name) : route?.path || ''
  }
}

function getSelectionText() {
  const selection = window.getSelection?.()
  return truncate(compactText(selection?.toString() || ''), MAX_SELECTION_LENGTH)
}

function findMainElement() {
  for (const selector of MAIN_SELECTORS) {
    const element = document.querySelector(selector)
    if (element) return element
  }
  return document.body
}

function cloneVisibleMainContent() {
  const main = findMainElement()
  if (!main) return null

  const clone = main.cloneNode(true)
  NOISE_SELECTORS.forEach(selector => {
    clone.querySelectorAll?.(selector).forEach(node => node.remove())
  })

  clone.querySelectorAll?.('[aria-hidden="true"], [hidden]').forEach(node => node.remove())
  clone.querySelectorAll?.('button, .el-button, .el-menu, .el-tabs__nav, .el-pagination').forEach(node => {
    const text = compactText(node.innerText || node.textContent || '')
    if (text.length <= 20) node.remove()
  })

  return clone
}

function collectDomSections() {
  const clone = cloneVisibleMainContent()
  if (!clone) return []

  const sections = []
  let totalLength = 0
  const candidates = Array.from(clone.querySelectorAll('.el-card, section, article, .page-container, .content-card'))

  if (candidates.length > 0) {
    candidates.slice(0, 8).forEach((node, index) => {
      if (totalLength >= MAX_TOTAL_LENGTH) return
      const content = truncate(compactText(node.innerText || node.textContent || ''), MAX_SECTION_LENGTH)
      if (!content || content.length < 20) return
      const heading = node.querySelector('h1,h2,h3,.card-header,.el-card__header')?.innerText
      sections.push({
        type: 'text',
        title: compactText(heading) || `页面区块 ${index + 1}`,
        content
      })
      totalLength += content.length
    })
  }

  if (sections.length === 0) {
    const content = truncate(compactText(clone.innerText || clone.textContent || ''), MAX_TOTAL_LENGTH)
    if (content) {
      sections.push({
        type: 'text',
        title: '当前页面可见内容',
        content
      })
    }
  }

  return sections
}

export async function collectAssistantPageContext() {
  const registeredContext = await collectRegisteredAssistantContext()
  const selectionText = getSelectionText()
  const page = {
    ...getCurrentPageInfo(),
    ...(registeredContext?.page || {})
  }

  const sections = registeredContext?.sections?.length
    ? registeredContext.sections
    : collectDomSections()

  return {
    source: registeredContext?.source || 'dom-fallback',
    page,
    selection: selectionText ? { text: selectionText } : undefined,
    sections,
    meta: {
      generatedAt: new Date().toISOString(),
      collector: registeredContext?.sections?.length ? 'provider' : 'dom',
      ...(registeredContext?.meta || {})
    }
  }
}

export default {
  collectAssistantPageContext
}
