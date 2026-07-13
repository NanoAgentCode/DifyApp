<template>
  <div class="global-assistant">
    <el-tooltip content="页面智能助手" placement="left" :show-after="300">
      <button class="assistant-fab" type="button" @click="openDrawer">
        <el-icon :size="20"><ChatDotRound /></el-icon>
      </button>
    </el-tooltip>

    <el-drawer
      v-model="visible"
      custom-class="assistant-drawer"
      direction="rtl"
      size="460px"
      :with-header="false"
      :modal="false"
      :append-to-body="true"
    >
      <div class="assistant-panel">
        <header class="assistant-header" data-assistant-surface>
          <div>
            <h2>页面助手</h2>
            <p>{{ pageTitle }}</p>
          </div>
          <div class="assistant-header-actions">
            <el-button :icon="Refresh" circle text @click="refreshContext" />
            <el-button :icon="Close" circle text @click="visible = false" />
          </div>
        </header>

        <div ref="messageListRef" class="assistant-messages" data-assistant-surface>
          <div v-if="messages.length === 0" class="assistant-empty">
            <el-icon><ChatDotRound /></el-icon>
            <span>可以问我当前页面里的内容、表格、文档或配置。</span>
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
            </div>
            <span class="assistant-time">{{ message.time }}</span>
          </div>
        </div>

        <footer class="assistant-input" data-assistant-surface>
          <div class="context-status">
            <span>{{ contextSummary }}</span>
            <el-tag size="small" :type="contextReady ? 'success' : 'info'">
              {{ contextReady ? '已读取页面' : '上下文为空' }}
            </el-tag>
          </div>
          <el-input
            v-model="input"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            resize="none"
            placeholder="基于当前页面提问..."
            :disabled="sending"
            @keydown.enter.exact.prevent="sendMessage"
            @keydown.ctrl.enter="insertNewline"
          />
          <div class="assistant-actions">
            <el-button text size="small" :disabled="sending || messages.length === 0" @click="clearMessages">
              清空
            </el-button>
            <el-button type="primary" :loading="sending" :disabled="!input.trim()" @click="sendMessage">
              <el-icon><Promotion /></el-icon>
              发送
            </el-button>
          </div>
        </footer>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Close, Loading, Promotion, Refresh } from '@element-plus/icons-vue'
import { assistantChatStream } from '@/api/assistant'
import { processSSEStream } from '@/composables/useSSEStream'
import { collectAssistantPageContext } from '@/utils/assistantContextCollector'
import { renderMarkdown } from '@/composables/useMarkdown'

const CONTEXT_RESEND_INTERVAL_MS = 25 * 60 * 1000

const visible = ref(false)
const sending = ref(false)
const input = ref('')
const messages = ref([])
const pageContext = ref(null)
const conversationId = ref(null)
const messageListRef = ref(null)
const sentContextHashes = ref(new Map())

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
  if (!visible.value) return
  if (isAssistantEventTarget(event.target)) return
  visible.value = false
}

watch(visible, (nextVisible) => {
  if (nextVisible) {
    document.addEventListener('pointerdown', handleOutsidePointerDown, true)
  } else {
    document.removeEventListener('pointerdown', handleOutsidePointerDown, true)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleOutsidePointerDown, true)
})

const refreshContext = async () => {
  pageContext.value = await collectAssistantPageContext()
}

const openDrawer = async () => {
  visible.value = true
  await refreshContext()
  scrollToBottom()
}

const clearMessages = () => {
  messages.value = []
  conversationId.value = null
  sentContextHashes.value = new Map()
}

const insertNewline = () => {
  input.value += '\n'
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
  scrollToBottom()

  try {
    const { payload, pageContextHash, sentFullContext } = await buildAssistantPayload(message, historyForRequest)
    const response = await assistantChatStream(payload)

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
    console.error('页面助手发送失败:', error)
    ElMessage.error('页面助手暂时不可用，请稍后重试')
    if (messages.value[assistantIndex]) {
      messages.value[assistantIndex].content = '抱歉，页面助手暂时不可用，请稍后重试。'
      messages.value[assistantIndex].loading = false
    }
  } finally {
    sending.value = false
    scrollToBottom()
  }
}
</script>

<style scoped>
.assistant-fab {
  position: fixed;
  right: 0;
  top: calc(50% + 72px);
  z-index: 1000;
  width: 56px;
  height: 56px;
  transform: translateX(50%);
  border: 2px solid var(--el-color-primary);
  border-radius: 50%;
  background: #fff;
  color: var(--el-color-primary);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.25s ease, background-color 0.25s ease, color 0.25s ease, box-shadow 0.25s ease;
}

.assistant-fab:hover {
  transform: translateX(0);
  background: var(--el-color-primary);
  color: #fff;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}

:global(.assistant-drawer) {
  height: 100vh;
}

:global(.assistant-drawer .el-drawer__body) {
  padding: 0;
  overflow: hidden;
}

.assistant-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary, #fff);
}

.assistant-header {
  flex-shrink: 0;
  min-height: 72px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.assistant-header h2 {
  margin: 0;
  font-size: 17px;
  line-height: 24px;
  color: var(--el-text-color-primary);
}

.assistant-header p {
  margin: 2px 0 0;
  max-width: 300px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-header-actions {
  display: flex;
  gap: 4px;
}

.assistant-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  background: #f7f9fc;
}

.assistant-empty {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--el-text-color-secondary);
  text-align: center;
  font-size: 13px;
}

.assistant-empty .el-icon {
  font-size: 34px;
  color: var(--el-color-primary);
}

.assistant-message {
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;
}

.assistant-message.user {
  align-items: flex-end;
}

.assistant-message.assistant {
  align-items: flex-start;
}

.assistant-message-body {
  max-width: 88%;
  border-radius: 8px;
  padding: 10px 12px;
  line-height: 1.6;
  word-break: break-word;
  box-shadow: 0 2px 8px rgba(31, 45, 61, 0.06);
}

.assistant-message.user .assistant-message-body {
  background: var(--el-color-primary);
  color: #fff;
}

.assistant-message.assistant .assistant-message-body {
  background: #fff;
  color: var(--el-text-color-primary);
}

.assistant-loading {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
}

.assistant-time {
  margin-top: 4px;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
}

.assistant-input {
  flex-shrink: 0;
  padding: 12px;
  border-top: 1px solid var(--el-border-color-light);
  background: #fff;
}

.context-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.assistant-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.assistant-message-text :deep(p) {
  margin: 6px 0;
}

.assistant-message-text :deep(ul),
.assistant-message-text :deep(ol) {
  padding-left: 18px;
  margin: 6px 0;
}

.assistant-message-text :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  padding: 10px;
  border-radius: 6px;
  background: #1f2937;
  color: #f9fafb;
}

.assistant-message-text :deep(code) {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

@media (max-width: 768px) {
  :global(.assistant-drawer) {
    width: 100% !important;
  }

  .assistant-fab {
    width: 48px;
    height: 48px;
  }
}
</style>
