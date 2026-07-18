<template>
  <div class="global-assistant">
    <el-tooltip content="打开页面助手" placement="left" :show-after="500">
      <button
        class="assistant-fab"
        type="button"
        aria-label="打开页面助手"
        :aria-expanded="visible"
        @click="openDrawer"
      >
        <span class="assistant-fab-icon">
          <el-icon :size="20"><ChatDotRound /></el-icon>
        </span>
        <span class="assistant-fab-label">页面助手</span>
      </button>
    </el-tooltip>

    <el-drawer
      v-model="visible"
      custom-class="assistant-drawer"
      direction="rtl"
      size="460px"
      :with-header="false"
      :modal="true"
      :close-on-click-modal="true"
      modal-class="assistant-overlay"
      :append-to-body="true"
    >
      <div class="assistant-panel">
        <header class="assistant-header" data-assistant-surface>
          <div class="assistant-title">
            <span class="assistant-brand">
              <el-icon><ChatDotRound /></el-icon>
            </span>
            <div class="assistant-title-copy">
              <div class="assistant-title-row">
                <h2>页面助手</h2>
                <span class="assistant-online"><i />在线</span>
              </div>
              <p :title="pageTitle">正在阅读：{{ pageTitle }}</p>
            </div>
          </div>
          <div class="assistant-header-actions">
            <el-tooltip content="重新读取当前页面" placement="bottom">
              <el-button
                :icon="Refresh"
                circle
                text
                aria-label="重新读取当前页面"
                :loading="refreshing"
                @click="refreshContext(true)"
              />
            </el-tooltip>
            <el-tooltip content="关闭" placement="bottom">
              <el-button :icon="Close" circle text aria-label="关闭页面助手" @click="closeDrawer" />
            </el-tooltip>
          </div>
        </header>

        <div ref="messageListRef" class="assistant-messages" data-assistant-surface>
          <div v-if="messages.length === 0" class="assistant-empty">
            <div class="assistant-empty-icon">
              <el-icon><MagicStick /></el-icon>
            </div>
            <h3>你好，我已经准备好了</h3>
            <p>我会结合当前页面内容回答，试试这样问：</p>
            <div class="assistant-suggestions">
              <button
                v-for="suggestion in suggestions"
                :key="suggestion"
                type="button"
                @click="useSuggestion(suggestion)"
              >
                <span>{{ suggestion }}</span>
                <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
          </div>

          <div
            v-for="(message, index) in messages"
            :key="`${message.role}-${index}-${message.time}`"
            :class="['assistant-message', message.role]"
          >
            <div class="assistant-message-body">
              <div v-if="message.loading && !message.content" class="assistant-loading">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>正在结合页面内容思考...</span>
              </div>
              <div v-else class="assistant-message-text" v-html="renderContent(message.content)" />
              <div v-if="message.role === 'assistant' && message.content && !message.loading" class="message-tools">
                <button type="button" :aria-label="copiedIndex === index ? '已复制' : '复制回答'" @click="copyMessage(message.content, index)">
                  <el-icon><component :is="copiedIndex === index ? Check : CopyDocument" /></el-icon>
                  <span>{{ copiedIndex === index ? '已复制' : '复制' }}</span>
                </button>
              </div>
            </div>
            <span class="assistant-time">{{ message.time }}</span>
          </div>
        </div>

        <footer class="assistant-input" data-assistant-surface>
          <div class="context-status">
            <span class="context-summary"><el-icon><Document /></el-icon>{{ contextSummary }}</span>
            <span :class="['context-badge', { ready: contextReady }]">
              <i />{{ refreshing ? '正在读取' : contextReady ? '页面已读取' : '暂无上下文' }}
            </span>
          </div>
          <div class="assistant-composer" :class="{ focused: inputFocused }">
            <el-input
              ref="inputRef"
              v-model="input"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 5 }"
              resize="none"
              maxlength="2000"
              placeholder="询问当前页面中的内容..."
              @focus="inputFocused = true"
              @blur="inputFocused = false"
              @keydown.enter.exact.prevent="sendMessage"
            />
            <div class="composer-footer">
              <span class="keyboard-hint">Enter 发送 · Shift + Enter 换行</span>
              <div class="assistant-actions">
                <el-button text size="small" :disabled="sending || messages.length === 0" @click="clearMessages">
                  清空对话
                </el-button>
                <el-button v-if="sending" class="stop-button" @click="stopGeneration">
                  <span class="stop-icon" />
                  停止
                </el-button>
                <el-button v-else type="primary" :disabled="!input.trim()" @click="sendMessage">
                  <el-icon><Promotion /></el-icon>
                  发送
                </el-button>
              </div>
            </div>
          </div>
        </footer>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  ChatDotRound,
  Check,
  Close,
  CopyDocument,
  Document,
  Loading,
  MagicStick,
  Promotion,
  Refresh
} from '@element-plus/icons-vue'
import { assistantChatStream } from '@/api/assistant'
import { processSSEStream } from '@/composables/useSSEStream'
import { collectAssistantPageContext } from '@/utils/assistantContextCollector'
import { renderMarkdown } from '@/composables/useMarkdown'

const CONTEXT_RESEND_INTERVAL_MS = 25 * 60 * 1000
const suggestions = ['总结一下当前页面', '帮我解释页面中的关键信息', '下一步建议做什么？']

const route = useRoute()
const visible = ref(false)
const sending = ref(false)
const refreshing = ref(false)
const input = ref('')
const inputFocused = ref(false)
const messages = ref([])
const pageContext = ref(null)
const conversationId = ref(null)
const messageListRef = ref(null)
const inputRef = ref(null)
const sentContextHashes = ref(new Map())
const copiedIndex = ref(-1)
let abortController = null
let copyResetTimer = null
let refreshPromise = null

const pageTitle = computed(() => pageContext.value?.page?.title || '当前页面')
const contextReady = computed(() => (pageContext.value?.sections || []).some(section => section.content))
const contextSummary = computed(() => {
  const count = pageContext.value?.sections?.length || 0
  const source = pageContext.value?.source === 'page-provider' ? '结构化上下文' : '页面文本'
  return count > 0 ? `${source} · ${count} 个区块` : '尚未采集页面内容'
})

const now = () => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const renderContent = (content) => renderMarkdown(content || '')

const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

const isAssistantEventTarget = (target) => {
  if (!(target instanceof Element)) return false
  return Boolean(target.closest('.assistant-drawer, .assistant-fab, [data-assistant-surface]'))
}

const handleOutsidePointerDown = (event) => {
  if (!visible.value || isAssistantEventTarget(event.target)) return
  closeDrawer()
}

watch(visible, (nextVisible) => {
  if (nextVisible) {
    document.addEventListener('pointerdown', handleOutsidePointerDown, true)
    nextTick(() => inputRef.value?.focus())
  } else {
    document.removeEventListener('pointerdown', handleOutsidePointerDown, true)
  }
})

watch(() => route.fullPath, () => {
  if (visible.value) refreshContext()
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleOutsidePointerDown, true)
  abortController?.abort()
  if (copyResetTimer) clearTimeout(copyResetTimer)
})

const refreshContext = (notify = false) => {
  if (refreshPromise) return refreshPromise
  refreshing.value = true
  refreshPromise = (async () => {
    try {
      pageContext.value = await collectAssistantPageContext()
      if (notify) {
        ElMessage.success(contextReady.value ? '已重新读取当前页面' : '当前页面暂无可读取内容')
      }
    } finally {
      refreshing.value = false
      refreshPromise = null
    }
  })()
  return refreshPromise
}

const openDrawer = async () => {
  visible.value = true
  await refreshContext()
  scrollToBottom()
  nextTick(() => inputRef.value?.focus())
}

const closeDrawer = () => {
  visible.value = false
}

const clearMessages = () => {
  messages.value = []
  conversationId.value = null
  sentContextHashes.value = new Map()
}

const useSuggestion = (suggestion) => {
  input.value = suggestion
  nextTick(() => inputRef.value?.focus())
}

const copyMessage = async (content, index) => {
  try {
    await navigator.clipboard.writeText(content)
    copiedIndex.value = index
    if (copyResetTimer) clearTimeout(copyResetTimer)
    copyResetTimer = setTimeout(() => {
      copiedIndex.value = -1
    }, 1600)
  } catch (error) {
    ElMessage.error('复制失败，请手动选择内容')
  }
}

const stopGeneration = () => {
  if (!sending.value) return
  abortController?.abort()
  const pendingMessage = [...messages.value].reverse().find(message => message.role === 'assistant' && message.loading)
  if (pendingMessage) {
    pendingMessage.loading = false
    if (!pendingMessage.content) pendingMessage.content = '已停止生成。'
  }
}

const buildHistory = () => messages.value
  .filter(message => !message.loading && message.content)
  .slice(-8)
  .map(message => ({
    role: message.role === 'user' ? 'user' : 'assistant',
    content: message.content
  }))

const normalizeContextForHash = (value) => {
  if (Array.isArray(value)) {
    return value.map(normalizeContextForHash)
  }
  if (value && typeof value === 'object') {
    return Object.keys(value)
      .filter(key => key !== 'generatedAt')
      .sort()
      .reduce((result, key) => {
        const item = normalizeContextForHash(value[key])
        if (item !== undefined) {
          result[key] = item
        }
        return result
      }, {})
  }
  return value
}

const stableStringify = (value) => JSON.stringify(normalizeContextForHash(value))

const fallbackHash = (text) => {
  let hash = 2166136261
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index)
    hash += (hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24)
  }
  return `fnv1a-${(hash >>> 0).toString(16)}`
}

const buildContextHash = async (context) => {
  if (!context) return null
  const text = stableStringify(context)
  if (!text) return null
  if (!window.crypto?.subtle) {
    return fallbackHash(text)
  }
  const digest = await window.crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  return Array.from(new Uint8Array(digest))
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('')
}

const buildAssistantPayload = async (message, history) => {
  const pageContextHash = await buildContextHash(pageContext.value)
  const lastSentAt = pageContextHash ? sentContextHashes.value.get(pageContextHash) : null
  const cacheFresh = lastSentAt && Date.now() - lastSentAt < CONTEXT_RESEND_INTERVAL_MS
  const shouldSendFullContext = !pageContextHash || !cacheFresh
  return {
    payload: {
      message,
      conversationId: conversationId.value,
      pageContextHash,
      pageContext: shouldSendFullContext ? pageContext.value : undefined,
      history
    },
    pageContextHash,
    sentFullContext: shouldSendFullContext && Boolean(pageContextHash)
  }
}

const sendMessage = async () => {
  const message = input.value.trim()
  if (!message || sending.value) return

  await refreshContext()
  const historyForRequest = buildHistory()

  messages.value.push({
    role: 'user',
    content: message,
    time: now()
  })
  input.value = ''

  const assistantIndex = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    time: now(),
    loading: true
  })
  sending.value = true
  const requestController = new AbortController()
  abortController = requestController
  scrollToBottom()

  try {
    const { payload, pageContextHash, sentFullContext } = await buildAssistantPayload(message, historyForRequest)
    const response = await assistantChatStream(payload, requestController.signal)

    if (!response.ok || !response.body) {
      const errorText = await response.text().catch(() => '')
      throw new Error(errorText || `HTTP ${response.status}`)
    }
    if (sentFullContext) {
      sentContextHashes.value.set(pageContextHash, Date.now())
    }

    await processSSEStream(response, {
      onData: (json) => {
        if (json.conversationId) {
          conversationId.value = String(json.conversationId)
        }
        if (json.answer !== undefined && messages.value[assistantIndex]) {
          messages.value[assistantIndex].content = json.answer || ''
          messages.value[assistantIndex].loading = false
          scrollToBottom()
        }
        if (json.finished && messages.value[assistantIndex]) {
          messages.value[assistantIndex].loading = false
        }
      }
    })
  } catch (error) {
    if (error.name === 'AbortError') return
    console.error('页面助手发送失败:', error)
    ElMessage.error('页面助手暂时不可用，请稍后重试')
    if (messages.value[assistantIndex]) {
      messages.value[assistantIndex].content = '抱歉，页面助手暂时不可用，请稍后重试。'
      messages.value[assistantIndex].loading = false
    }
  } finally {
    if (abortController === requestController) {
      abortController = null
    }
    sending.value = false
    scrollToBottom()
    nextTick(() => inputRef.value?.focus())
  }
}
</script>

<style scoped>
.assistant-fab {
  position: fixed;
  right: var(--spacing-sm);
  top: calc(50% + 72px);
  z-index: var(--z-dropdown);
  display: flex;
  align-items: center;
  width: 48px;
  height: 48px;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--color-primary-light-4);
  border-radius: var(--radius-full);
  background: var(--color-bg-primary);
  color: var(--color-primary);
  box-shadow: var(--shadow-lg), var(--shadow-primary);
  cursor: pointer;
  white-space: nowrap;
  transition: width var(--transition-base), transform var(--transition-fast), box-shadow var(--transition-base), background-color var(--transition-fast), color var(--transition-fast);
}

.assistant-fab:hover,
.assistant-fab:focus-visible {
  width: 124px;
  transform: translateY(-2px);
  background: var(--color-primary);
  color: var(--color-bg-primary);
  box-shadow: var(--shadow-xl), var(--shadow-primary-lg);
  outline: none;
}

.assistant-fab-icon {
  flex: 0 0 46px;
  height: 46px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.assistant-fab-label {
  padding-right: var(--spacing-md);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  opacity: 0;
  transform: translateX(var(--spacing-xs));
  transition: opacity var(--transition-fast), transform var(--transition-fast);
}

.assistant-fab:hover .assistant-fab-label,
.assistant-fab:focus-visible .assistant-fab-label {
  opacity: 1;
  transform: translateX(0);
}

:global(.assistant-drawer) {
  height: 100vh;
  border-left: 1px solid var(--color-border-lighter);
  box-shadow: var(--shadow-2xl);
}

:global(.assistant-overlay) {
  background: color-mix(in srgb, var(--color-text-primary) 28%, transparent);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
}

:global(.assistant-drawer .el-drawer__body) {
  padding: 0;
  overflow: hidden;
}

.assistant-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary);
}

.assistant-header {
  flex-shrink: 0;
  min-height: 76px;
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid var(--color-border-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  background: var(--color-bg-primary);
}

.assistant-title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.assistant-brand {
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark-2));
  color: var(--color-bg-primary);
  box-shadow: var(--shadow-primary);
  font-size: var(--font-size-lg);
}

.assistant-title-copy {
  min-width: 0;
}

.assistant-title-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.assistant-title-row h2 {
  margin: 0;
  font-size: var(--font-size-md);
  line-height: var(--line-height-tight);
  color: var(--color-text-primary);
  font-weight: var(--font-weight-semibold);
}

.assistant-online {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  color: var(--color-success);
  font-size: var(--font-size-xs);
}

.assistant-online i,
.context-badge i {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
  background: currentColor;
}

.assistant-header p {
  margin: var(--spacing-xs) 0 0;
  max-width: 250px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-header-actions {
  flex-shrink: 0;
  display: flex;
  gap: var(--spacing-xs);
}

.assistant-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--spacing-md);
  scroll-behavior: smooth;
  background: var(--color-bg-secondary);
  scrollbar-width: thin;
  scrollbar-color: var(--color-border-base) transparent;
}

.assistant-empty {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl) var(--spacing-md);
  text-align: center;
  color: var(--color-text-secondary);
}

.assistant-empty-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--spacing-md);
  border-radius: var(--radius-2xl);
  background: var(--color-primary-light-5);
  color: var(--color-primary);
  font-size: var(--font-size-2xl);
}

.assistant-empty h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.assistant-empty p {
  margin: var(--spacing-sm) 0 var(--spacing-md);
  font-size: var(--font-size-sm);
}

.assistant-suggestions {
  width: 100%;
  max-width: 340px;
  display: grid;
  gap: var(--spacing-sm);
}

.assistant-suggestions button {
  width: 100%;
  min-height: 42px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-xl);
  background: var(--color-bg-primary);
  color: var(--color-text-regular);
  font: inherit;
  font-size: var(--font-size-sm);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--transition-fast), color var(--transition-fast), transform var(--transition-fast), box-shadow var(--transition-fast);
}

.assistant-suggestions button:hover,
.assistant-suggestions button:focus-visible {
  border-color: var(--color-primary-light-2);
  color: var(--color-primary);
  transform: translateY(-1px);
  box-shadow: var(--shadow-primary);
  outline: none;
}

.assistant-message {
  display: flex;
  flex-direction: column;
  margin-bottom: var(--spacing-md);
  animation: message-enter var(--transition-base) both;
}

.assistant-message.user {
  align-items: flex-end;
}

.assistant-message.assistant {
  align-items: flex-start;
}

.assistant-message-body {
  position: relative;
  max-width: 90%;
  border-radius: var(--radius-xl);
  padding: 10px var(--spacing-sm);
  line-height: var(--line-height-relaxed);
  font-size: var(--font-size-sm);
  word-break: break-word;
}

.assistant-message.user .assistant-message-body {
  border-bottom-right-radius: var(--radius-sm);
  background: var(--color-primary);
  color: var(--color-bg-primary);
  box-shadow: var(--shadow-primary);
}

.assistant-message.assistant .assistant-message-body {
  border: 1px solid var(--color-border-lighter);
  border-bottom-left-radius: var(--radius-sm);
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-xs);
}

.assistant-loading {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--color-text-secondary);
}

.assistant-time {
  margin-top: var(--spacing-xs);
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.message-tools {
  display: flex;
  margin-top: var(--spacing-xs);
  padding-top: var(--spacing-xs);
  border-top: 1px solid var(--color-border-extra-light);
}

.message-tools button {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: var(--font-size-xs);
  cursor: pointer;
  opacity: 0;
  transition: opacity var(--transition-fast), color var(--transition-fast), background-color var(--transition-fast);
}

.assistant-message-body:hover .message-tools button,
.message-tools button:focus-visible {
  opacity: 1;
}

.message-tools button:hover,
.message-tools button:focus-visible {
  background: var(--color-bg-hover);
  color: var(--color-primary);
  outline: none;
}

.assistant-input {
  flex-shrink: 0;
  padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
  border-top: 1px solid var(--color-border-lighter);
  background: var(--color-bg-primary);
}

.context-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.context-summary {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-badge {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  color: var(--color-text-secondary);
}

.context-badge.ready {
  color: var(--color-success);
}

.assistant-composer {
  overflow: hidden;
  border: 1px solid var(--color-border-base);
  border-radius: var(--radius-xl);
  background: var(--color-bg-primary);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.assistant-composer.focused {
  border-color: var(--color-primary-light-1);
  box-shadow: 0 0 0 3px var(--color-primary-light-5);
}

.assistant-composer :deep(.el-textarea__inner) {
  padding: 12px;
  border: 0;
  box-shadow: none;
  background: transparent;
  color: var(--color-text-primary);
  line-height: var(--line-height-normal);
}

.assistant-composer :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) var(--spacing-sm) var(--spacing-sm);
}

.keyboard-hint {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.assistant-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.assistant-actions :deep(.el-button) {
  margin-left: 0;
  border-radius: var(--radius-lg);
}

.stop-button {
  color: var(--color-text-regular);
}

.stop-icon {
  width: 8px;
  height: 8px;
  margin-right: var(--spacing-xs);
  border-radius: 2px;
  background: currentColor;
}

.assistant-message-text :deep(p) {
  margin: var(--spacing-xs) 0;
}

/* 页面助手采用紧凑问答样式，模型偶尔输出标题时也不放大展示。 */
.assistant-message-text :deep(h1),
.assistant-message-text :deep(h2),
.assistant-message-text :deep(h3),
.assistant-message-text :deep(h4),
.assistant-message-text :deep(h5),
.assistant-message-text :deep(h6) {
  margin: var(--spacing-sm) 0 var(--spacing-xs);
  color: inherit;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-normal);
}

.assistant-message-text :deep(ul),
.assistant-message-text :deep(ol) {
  padding-left: var(--spacing-lg);
  margin: var(--spacing-xs) 0;
}

.assistant-message-text :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  padding: var(--spacing-sm);
  border-radius: var(--radius-md);
}

.assistant-message-text :deep(code) {
  font-family: var(--font-family-mono);
}

@keyframes message-enter {
  from {
    opacity: 0;
    transform: translateY(var(--spacing-sm));
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  :global(.assistant-drawer) {
    width: 100% !important;
  }

  .assistant-fab {
    right: var(--spacing-sm);
    top: auto;
    bottom: 76px;
  }

  .assistant-header p {
    max-width: 180px;
  }

  .keyboard-hint {
    display: none;
  }

  .assistant-message-body {
    max-width: 94%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-fab,
  .assistant-fab-label,
  .assistant-message,
  .assistant-suggestions button,
  .assistant-messages {
    animation: none;
    scroll-behavior: auto;
    transition: none;
  }
}
</style>
