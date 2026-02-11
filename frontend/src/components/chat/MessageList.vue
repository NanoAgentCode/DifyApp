<template>
  <div class="chat-history" ref="chatHistoryRef">
    <div class="chat-history-content">
      <div
        v-for="(message, index) in messages"
        :key="getMessageKey(message, index)"
        :class="['message-item', message.type, { 'is-streaming': sending && index === messages.length - 1 && message.type === 'assistant' && message.content }]"
      >
        <div class="message-avatar">
          <el-icon v-if="message.type === 'user'"><User /></el-icon>
          <el-icon v-else><Service /></el-icon>
        </div>
        <div class="message-content">
          <div 
            v-if="message.isLoading && !message.content"
            class="message-text loading"
          >
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>AI正在思考中...</span>
          </div>
          <div 
            v-else
            class="message-text" 
            v-html="getRenderedContent(message, index)"
          ></div>
          
          <!-- 重新生成按钮（仅助手消息且已完成时显示） -->
          <div 
            v-if="message.type === 'assistant' && !message.isLoading && message.content && onRegenerate"
            class="message-actions"
          >
            <el-button
              size="small"
              type="primary"
              text
              :disabled="sending"
              @click="handleRegenerate(index)"
            >
              <el-icon><Refresh /></el-icon>
              重新生成
            </el-button>
          </div>
          
          <div class="message-time">{{ message.time }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onUnmounted } from 'vue'
import { User, Service, Loading, Refresh } from '@element-plus/icons-vue'
import { renderMarkdown } from '@/composables/useMarkdown'
import { useTypewriter } from '@/composables/useTypewriter'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  sending: {
    type: Boolean,
    default: false
  },
  onRegenerate: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['regenerate'])

const handleRegenerate = (index) => {
  if (props.onRegenerate) {
    props.onRegenerate(index)
  } else {
    emit('regenerate', index)
  }
}

const chatHistoryRef = ref(null)
// 缓存渲染结果，避免频繁重新渲染
const renderedCache = ref(new Map())
// 记录上次滚动位置，用于判断是否需要滚动
let lastScrollHeight = 0

// ---- 打字机效果 ----
const typewriter = useTypewriter()
// 记录当前正在进行打字机效果的消息索引
let streamingMsgIndex = -1

onUnmounted(() => typewriter.destroy())

// 监听最后一条消息的流式状态，驱动打字机
// 使用 props.sending 作为流式指示器（全程为 true），而非 message.isLoading
// 因为多个组件（Chat.vue、Portal.vue）在首个数据到达时就将 isLoading 设为 false
watch(() => {
  const msgs = props.messages
  if (!msgs.length) return null
  const last = msgs[msgs.length - 1]
  return {
    content: last.content,
    type: last.type,
    sending: props.sending,
    index: msgs.length - 1
  }
}, (curr, prev) => {
  if (!curr) {
    // 消息列表被清空
    typewriter.reset()
    streamingMsgIndex = -1
    return
  }

  const isStreaming = curr.sending && curr.type === 'assistant'
  const wasStreaming = prev?.sending && prev?.type === 'assistant'

  // 消息索引发生变化（新对话/重新生成），重置打字机
  if (prev && curr.index !== prev.index && wasStreaming) {
    typewriter.reset()
  }

  if (isStreaming && curr.content) {
    // 流式数据到达，喂给打字机
    streamingMsgIndex = curr.index
    typewriter.feed(curr.content)
  } else if (wasStreaming && !isStreaming) {
    // sending 从 true 变为 false → 流结束，立即显示全部内容
    typewriter.finish()
    streamingMsgIndex = -1
    // 延迟 reset，为下次流做准备（避免 finish 后立刻被 reset 清除）
    nextTick(() => typewriter.reset())
  }
}, { deep: true })

// 打字机输出变化时触发滚动
watch(() => typewriter.displayedContent.value, () => {
  if (typewriter.isTyping.value) {
    scrollToBottom(false)
  }
})

/**
 * 在 HTML 末尾最后一个块级闭合标签前插入光标 span
 */
function appendCursor(html) {
  const cursor = '<span class="typing-cursor"></span>'
  const lastClose = Math.max(
    html.lastIndexOf('</p>'),
    html.lastIndexOf('</li>'),
    html.lastIndexOf('</pre>'),
    html.lastIndexOf('</blockquote>'),
    html.lastIndexOf('</td>')
  )
  if (lastClose > 0) {
    return html.substring(0, lastClose) + cursor + html.substring(lastClose)
  }
  return html + cursor
}

// 生成稳定的消息 key（基于索引，不依赖内容）
const getMessageKey = (message, index) => {
  // 使用索引作为主要标识，加上类型和时间戳（如果有）作为辅助
  return `msg-${index}-${message.type}-${message.time || ''}`
}

// 获取渲染后的内容（带缓存 + 打字机双模式）
const getRenderedContent = (message, index) => {
  if (!message.content) return ''

  // ---- 流式消息：使用打字机 + 完整 Markdown ----
  // 通过 streamingMsgIndex 判断（由 watch 根据 sending 状态维护）
  if (streamingMsgIndex === index && message.content) {
    const twContent = typewriter.safeDisplayedContent.value
    if (!twContent) return ''
    // 使用完整渲染（含 KaTeX），保证公式在流式期间也能正确显示
    // 打字机已控制更新频率（10-30ms/tick），KaTeX 开销可接受
    let html = renderMarkdown(twContent)
    // 正在打字时追加闪烁光标
    if (typewriter.isTyping.value) {
      html = appendCursor(html)
    }
    return html
  }

  // ---- 非流式消息：完整渲染 + 缓存 ----
  const cacheKey = `msg-${index}`
  const cached = renderedCache.value.get(cacheKey)
  
  // 如果缓存存在且内容相同，直接返回（避免频繁重新渲染）
  if (cached && cached.content === message.content) {
    return cached.html
  }
  
  // 内容变化或首次渲染，重新渲染（完整版含 KaTeX）
  const html = renderMarkdown(message.content)
  renderedCache.value.set(cacheKey, { content: message.content, html })
  
  // 限制缓存大小，避免内存泄漏（保留最近50条消息的缓存）
  if (renderedCache.value.size > 50) {
    const keys = Array.from(renderedCache.value.keys())
    const sortedKeys = keys.sort((a, b) => {
      const indexA = parseInt(a.split('-')[1]) || 0
      const indexB = parseInt(b.split('-')[1]) || 0
      return indexA - indexB
    })
    sortedKeys.slice(0, 10).forEach(key => renderedCache.value.delete(key))
  }
  
  return html
}

// 检查是否在底部（允许一定的误差范围）
const isNearBottom = () => {
  if (!chatHistoryRef.value) return true
  const element = chatHistoryRef.value
  const threshold = 100 // 距离底部100px内认为是在底部
  return element.scrollHeight - element.scrollTop - element.clientHeight < threshold
}

// 自动滚动到底部（使用 requestAnimationFrame 优化）
let scrollRafId = null
const scrollToBottom = (force = false) => {
  // 取消之前的滚动请求
  if (scrollRafId !== null) {
    cancelAnimationFrame(scrollRafId)
  }
  
  // 使用 requestAnimationFrame 优化滚动性能
  scrollRafId = requestAnimationFrame(() => {
    nextTick(() => {
      if (chatHistoryRef.value) {
        const element = chatHistoryRef.value
        const currentScrollHeight = element.scrollHeight
        
        // 如果用户手动向上滚动了，且不是强制滚动，则不自动滚动
        if (!force && !isNearBottom()) {
          lastScrollHeight = currentScrollHeight
          scrollRafId = null
          return
        }
        
        // 只有在滚动高度变化时才滚动
        if (force || currentScrollHeight !== lastScrollHeight) {
          // 流式输出时使用即时滚动，其他情况使用平滑滚动
          const isStreaming = props.sending && streamingMsgIndex >= 0
          
          if (isStreaming || force) {
            // 流式输出或强制滚动：使用即时滚动，更流畅
            element.scrollTop = element.scrollHeight
          } else {
            // 其他情况：使用平滑滚动
            element.scrollTo({
              top: element.scrollHeight,
              behavior: 'smooth'
            })
          }
          lastScrollHeight = currentScrollHeight
        }
      }
      scrollRafId = null
    })
  })
}

// 监听消息变化，智能滚动
watch(() => props.messages, (newMessages, oldMessages) => {
  // 只在消息数量变化或最后一条消息内容变化时滚动
  if (newMessages.length !== (oldMessages?.length || 0)) {
    // 新消息添加，强制滚动
    scrollToBottom(true)
  } else if (newMessages.length > 0) {
    // 检查最后一条消息是否在更新（流式输出）
    const lastMessage = newMessages[newMessages.length - 1]
    const oldLastMessage = oldMessages?.[oldMessages.length - 1]
    
    if (lastMessage && oldLastMessage) {
      // 如果最后一条消息内容变化，就滚动（降低阈值，提高流畅度）
      const contentDiff = Math.abs((lastMessage.content?.length || 0) - (oldLastMessage.content?.length || 0))
      // 降低阈值到20字符，让更新更及时
      if (contentDiff > 20 || props.sending) {
        scrollToBottom(false)
      }
    }
  }
}, { deep: true })

defineExpose({
  scrollToBottom,
  $el: chatHistoryRef
})
</script>

<style scoped>
.chat-history {
  flex: 1;
  overflow: visible; /* 移除滚动条，由父容器统一处理 */
  padding: 0;
  min-height: 0;
}

.chat-history-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px;
  min-height: 100%;
  justify-content: flex-end;
}

.chat-history::-webkit-scrollbar {
  width: 6px;
}

.chat-history::-webkit-scrollbar-track {
  background: transparent;
}

.chat-history::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
}

.chat-history::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
}

.message-item {
  display: grid;
  grid-template-columns: var(--portal-avatar-size, 36px) minmax(0, var(--portal-message-content-width, 1fr)) var(--portal-avatar-size, 36px);
  column-gap: var(--portal-avatar-gap, 12px);
  align-items: start;
  width: 100%;
  justify-content: center;
  /* 只在首次添加时应用动画，更新时不应用 */
}

/* 用户消息：右对齐，从右侧滑入动画 */
.message-item.user {
  animation: slideInFromRight 0.4s ease-out;
}

/* 助手消息：左对齐，从左向右动画 */
.message-item.assistant {
  animation: slideInFromLeft 0.4s ease-out;
}

/* 流式输出中的消息不应用动画，避免闪烁 */
.message-item.is-streaming {
  animation: none;
}

@keyframes slideInFromLeft {
  from {
    opacity: 0;
    transform: translateX(-30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes slideInFromRight {
  from {
    opacity: 0;
    transform: translateX(30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}

.message-item.assistant .message-avatar {
  grid-column: 1;
  justify-self: start;
}

.message-item.user .message-avatar {
  grid-column: 3;
  justify-self: end;
}

.message-item.user .message-avatar {
  background: #409eff;
  color: white;
}

.message-item.assistant .message-avatar {
  background: #f0f2f5;
  color: #409eff;
}

.message-content {
  grid-column: 2;
  min-width: 0;
  max-width: 100%;
  display: flex;
  flex-direction: column;
}

/* 用户消息内容右对齐 */
.message-item.user .message-content {
  align-items: flex-end;
}

/* 助手消息内容左对齐 */
.message-item.assistant .message-content {
  align-items: flex-start;
}

.message-text {
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 8px;
  line-height: 1.6;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

/* 响应内容（助手消息）白色背景 */
.message-item.assistant .message-text {
  background: #fff;
}

.message-item.user .message-text {
  background: #409eff;
  color: white;
}

.message-text.loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
}

.message-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}

.message-item.user .message-actions {
  justify-content: flex-end;
}

.message-time {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  text-align: right;
}

.message-item.user .message-time {
  text-align: right;
}

.message-item.assistant .message-time {
  text-align: left;
}

/* Markdown样式 */
:deep(.message-text) {
  h1, h2, h3, h4, h5, h6 {
    margin-top: 16px;
    margin-bottom: 8px;
    font-weight: 600;
  }
  
  h1 { font-size: 24px; }
  h2 { font-size: 20px; }
  h3 { font-size: 18px; }
  
  p {
    margin: 8px 0;
  }
  
  ul, ol {
    margin: 8px 0;
    padding-left: 24px;
  }
  
  code {
    background: rgba(0, 0, 0, 0.1);
    padding: 2px 6px;
    border-radius: 3px;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 0.9em;
    color: #e83e8c;
  }
  
  pre {
    background: #1e1e1e !important; /* VS Code Dark+ 背景色 */
    color: #d4d4d4 !important; /* VS Code Dark+ 前景色 */
    padding: 16px;
    border-radius: 6px;
    overflow-x: auto;
    margin: 12px 0;
    position: relative;
    border: 1px solid rgba(255, 255, 255, 0.1);
  }
  
  pre code {
    background: transparent !important;
    padding: 0;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 14px;
    line-height: 1.6;
    display: block;
    width: 100%;
    /* 不设置 color，让 highlight.js 的语法元素使用自己的颜色 */
  }
  
  pre code.hljs {
    display: block;
    overflow-x: auto;
    padding: 0;
    background: transparent !important;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 14px;
    line-height: 1.6;
    /* 不设置 color，让 vscode-dark.css 处理所有语法高亮颜色 */
  }
  
  /* 不要覆盖 highlight.js 的语法高亮颜色，让 vscode-dark.css 处理 */
  
  blockquote {
    border-left: 4px solid #409eff;
    padding-left: 16px;
    margin: 12px 0;
    color: #606266;
  }
  
  table {
    border-collapse: collapse;
    width: 100%;
    margin: 12px 0;
  }
  
  table th,
  table td {
    border: 1px solid #e4e7ed;
    padding: 8px 12px;
    text-align: left;
  }
  
  table th {
    background: #f5f7fa;
    font-weight: 600;
  }
}

.message-item.user :deep(.message-text) {
  h1, h2, h3, h4, h5, h6,
  p, ul, ol, blockquote {
    color: white;
  }
  
  code {
    background: rgba(255, 255, 255, 0.2);
    color: white;
  }
  
  pre {
    background: #1e1e1e !important; /* VS Code Dark+ 背景色 */
    color: #d4d4d4 !important; /* VS Code Dark+ 前景色 */
    border: 1px solid rgba(255, 255, 255, 0.1);
  }
  
  pre code {
    /* 不设置 color，让 highlight.js 的语法元素使用自己的颜色 */
    background: transparent !important;
  }
  
  pre code.hljs {
    background: transparent !important;
    /* 不设置 color，让 vscode-dark.css 处理所有语法高亮颜色 */
  }
  
  table th,
  table td {
    border-color: rgba(255, 255, 255, 0.3);
  }
  
  table th {
    background: rgba(255, 255, 255, 0.1);
  }
}

/* 打字机闪烁光标 */
:deep(.typing-cursor)::after {
  content: '▊';
  display: inline;
  animation: blink-cursor 0.8s step-end infinite;
  color: #409eff;
  font-weight: normal;
  margin-left: 1px;
  font-size: 0.9em;
  vertical-align: baseline;
}

@keyframes blink-cursor {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* KaTeX 数学公式样式 */
:deep(.katex-formula-block) {
  margin: 1em 0;
  text-align: center;
}

:deep(.katex) {
  font-size: 1.1em;
}

:deep(.katex-display) {
  margin: 1em 0;
}

:deep(.katex-display > .katex) {
  text-align: center;
}
</style>
