import { onUnmounted } from 'vue'

const providers = new Set()

export function registerAssistantContextProvider(provider) {
  if (typeof provider !== 'function') {
    return () => {}
  }
  providers.add(provider)
  return () => providers.delete(provider)
}

export function useAssistantContext(provider) {
  const unregister = registerAssistantContextProvider(provider)
  onUnmounted(unregister)
  return unregister
}

export async function collectRegisteredAssistantContext() {
  const sections = []
  let page = null
  let source = 'page-provider'
  let meta = {}

  for (const provider of providers) {
    try {
      const context = await provider()
      if (!context) continue

      if (context.page) {
        page = { ...(page || {}), ...context.page }
      }
      if (Array.isArray(context.sections)) {
        sections.push(...context.sections)
      }
      if (context.meta) {
        meta = { ...meta, ...context.meta }
      }
      if (context.source) {
        source = context.source
      }
    } catch (error) {
      console.warn('采集页面助手上下文失败:', error)
    }
  }

  if (!page && sections.length === 0) {
    return null
  }

  return {
    source,
    page,
    sections,
    meta
  }
}

export default {
  useAssistantContext,
  registerAssistantContextProvider,
  collectRegisteredAssistantContext
}
