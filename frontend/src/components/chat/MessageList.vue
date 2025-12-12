<template>
  <div class="chat-history" ref="chatHistoryRef">
    <div class="chat-history-content">
      <div
        v-for="(message, index) in messages"
        :key="getMessageKey(message, index)"
        :class="['message-item', message.type, { 'is-streaming': message.isLoading && message.content }]"
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
          
          <div class="message-time">{{ message.time }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, computed } from 'vue'
import { User, Service, Loading } from '@element-plus/icons-vue'
import { renderMarkdown } from '@/composables/useMarkdown'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  }
})

const chatHistoryRef = ref(null)
// 缓存渲染结果，避免频繁重新渲染
const renderedCache = ref(new Map())
// 记录上次滚动位置，用于判断是否需要滚动
let lastScrollHeight = 0

// 生成稳定的消息 key（基于索引，不依赖内容）
const getMessageKey = (message, index) => {
  // 使用索引作为主要标识，加上类型和时间戳（如果有）作为辅助
  return `msg-${index}-${message.type}-${message.time || ''}`
}

// 获取渲染后的内容（带缓存）
const getRenderedContent = (message, index) => {
  if (!message.content) return ''
  
  // 使用消息索引作为缓存键，每个消息只缓存一次
  const cacheKey = `msg-${index}`
  const cached = renderedCache.value.get(cacheKey)
  
  // 如果缓存存在且内容相同，直接返回（避免频繁重新渲染）
  if (cached && cached.content === message.content) {
    return cached.html
  }
  
  // 内容变化或首次渲染，重新渲染
  const html = renderMarkdown(message.content)
  renderedCache.value.set(cacheKey, { content: message.content, html })
  
  // 限制缓存大小，避免内存泄漏（保留最近50条消息的缓存）
  if (renderedCache.value.size > 50) {
    // 删除最旧的缓存（按索引排序）
    const keys = Array.from(renderedCache.value.keys())
    const sortedKeys = keys.sort((a, b) => {
      const indexA = parseInt(a.split('-')[1]) || 0
      const indexB = parseInt(b.split('-')[1]) || 0
      return indexA - indexB
    })
    // 删除最旧的10个缓存
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
          const messages = props.messages
          const isStreaming = messages?.length > 0 && 
                            messages[messages.length - 1]?.isLoading && 
                            messages[messages.length - 1]?.content
          
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
      if (contentDiff > 20 || lastMessage.isLoading) {
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
  overflow-y: auto;
  overflow-x: hidden;
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
  display: flex;
  gap: 12px;
  /* 只在首次添加时应用动画，更新时不应用 */
  animation: fadeIn 0.3s;
}

/* 流式输出中的消息不应用动画，避免闪烁 */
.message-item.is-streaming {
  animation: none;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-item.user {
  flex-direction: row-reverse;
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

.message-item.user .message-avatar {
  background: #409eff;
  color: white;
}

.message-item.assistant .message-avatar {
  background: #f0f2f5;
  color: #409eff;
}

.message-content {
  flex: 1;
  min-width: 0;
  max-width: 70%;
}

.message-text {
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 8px;
  line-height: 1.6;
  word-wrap: break-word;
  overflow-wrap: break-word;
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

.message-time {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  text-align: right;
}

.message-item.user .message-time {
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

