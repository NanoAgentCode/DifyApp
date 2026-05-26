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
            v-if="message.taskEvents && message.taskEvents.length"
            class="task-log"
          >
            <button
              type="button"
              class="task-log-header"
              @click="toggleTaskLog(message, index)"
            >
              <span class="task-log-title-wrap">
                <el-icon class="task-log-toggle-icon">
                  <ArrowDown v-if="isTaskExpanded(message, index)" />
                  <ArrowRight v-else />
                </el-icon>
                <span :class="['task-live-dot', { active: message.isLoading }]"></span>
                <span class="task-log-title">{{ getTaskHeaderTitle(message) }}</span>
                <el-tag size="small" :type="getTaskStatusType(message)">
                  {{ getTaskStatusText(message) }}
                </el-tag>
              </span>
              <span class="task-log-summary">
                {{ getTaskSummary(message) }}
              </span>
            </button>

            <transition name="task-collapse">
              <div v-show="isTaskExpanded(message, index)" class="task-log-body">
                <div
                  v-for="group in getTaskTimelineGroups(message)"
                  :key="group.key"
                  :class="['task-step-group', `task-step-group-${group.status}`]"
                >
                  <button
                    type="button"
                    class="task-step-header"
                    @click="toggleTaskStep(group.key)"
                  >
                    <span class="task-step-left">
                      <span :class="['task-step-marker', `task-step-marker-${group.status}`]">{{ group.marker }}</span>
                      <el-icon class="task-step-toggle-icon">
                        <ArrowDown v-if="isTaskStepExpanded(group.key)" />
                        <ArrowRight v-else />
                      </el-icon>
                      <span class="task-step-title">{{ group.title }}</span>
                      <span class="task-step-state">{{ group.statusText }}</span>
                    </span>
                    <span class="task-step-meta">
                      <span class="task-step-summary">{{ group.summary }}</span>
                      <span class="task-step-count">{{ group.eventCount }} 项活动</span>
                    </span>
                  </button>

                  <transition name="task-collapse">
                    <div v-show="isTaskStepExpanded(group.key)" class="task-step-body">
                      <div
                        v-for="event in group.events"
                        :key="`${event.runId || 'task'}-${event.stepId || event.eventType}-${event.confirmationId || ''}`"
                        :class="['task-event', `task-event-${event.eventType}`, { 'task-event-tool': event.toolName }]"
                      >
                        <div class="task-event-header">
                          <span class="task-event-dot"></span>
                          <span class="task-event-title">{{ getTaskEventTitle(event) }}</span>
                          <span class="task-event-state">{{ getTaskEventState(event) }}</span>
                          <el-tag v-if="event.riskLevel" size="small" :type="event.riskLevel === 'HIGH' ? 'danger' : 'warning'">
                            {{ event.riskLevel }}
                          </el-tag>
                        </div>
                        <div v-if="event.content" class="task-event-content">{{ event.content }}</div>
                        <div v-if="event.toolName" class="task-tool-name">{{ event.toolName }}</div>
                        <div v-if="event.toolInputSummary" class="task-tool-summary">
                          <span>输入</span>
                          <p>{{ event.toolInputSummary }}</p>
                        </div>
                        <div v-if="event.toolOutputSummary" class="task-tool-summary">
                          <span>观察</span>
                          <p>{{ event.toolOutputSummary }}</p>
                        </div>
                        <div
                          v-if="event.eventType === 'confirmation_required' && !event.resolved"
                          class="task-confirm-actions"
                        >
                          <el-button
                            type="primary"
                            size="small"
                            :disabled="sending"
                            @click.stop="handleTaskConfirm(event, true)"
                          >
                            确认执行
                          </el-button>
                          <el-button
                            size="small"
                            :disabled="sending"
                            @click.stop="handleTaskConfirm(event, false)"
                          >
                            拒绝
                          </el-button>
                        </div>
                      </div>
                    </div>
                  </transition>
                </div>
              </div>
            </transition>

            <div 
              v-if="message.content"
              class="message-text task-answer"
              v-html="getRenderedContent(message, index)"
            ></div>
            <div
              v-else-if="message.isLoading"
              class="task-live-hint"
            >
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>任务执行中...</span>
            </div>
          </div>
          <div 
            v-else-if="message.isLoading && !message.content"
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
import { User, Service, Loading, Refresh, ArrowDown, ArrowRight } from '@element-plus/icons-vue'
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
  },
  onTaskConfirm: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['regenerate'])
const collapsedTaskLogs = ref(new Set())
const collapsedTaskSteps = ref(new Set())

const handleRegenerate = (index) => {
  if (props.onRegenerate) {
    props.onRegenerate(index)
  } else {
    emit('regenerate', index)
  }
}

const handleTaskConfirm = (event, approved) => {
  if (props.onTaskConfirm) {
    props.onTaskConfirm(event, approved)
  }
}

const getTaskEventTitle = (event) => {
  const titles = {
    plan_created: '计划',
    step_started: '步骤',
    tool_call: '工具调用',
    tool_result: '观察结果',
    confirmation_required: '需要确认',
    confirmation_resolved: '确认结果',
    log: '日志',
    answer_delta: '最终答案',
    finished: '完成',
    error: '错误'
  }
  return titles[event.eventType] || event.eventType || '任务事件'
}

const getTaskHeaderTitle = (message) => {
  if (hasPendingConfirmation(message)) return '等待确认'
  const latest = getLatestTaskEvent(message)
  if (latest?.eventType === 'finished') return '执行完成'
  if (latest?.eventType === 'error') return '执行失败'
  if (message.isLoading) return 'Agent 正在执行'
  return '执行过程'
}

const getTaskEventState = (event) => {
  const states = {
    plan_created: '已生成',
    step_started: '进行中',
    tool_call: '调用中',
    tool_result: '已完成',
    confirmation_required: '等待',
    confirmation_resolved: '已处理',
    log: '记录',
    finished: '完成',
    error: '失败'
  }
  return states[event.eventType] || event.status || ''
}

const getTaskPanelKey = (message, index) => {
  return `${index}-${message.taskRunId || message.taskEvents?.[0]?.runId || message.time || 'task'}`
}

const isTaskExpanded = (message, index) => {
  return !collapsedTaskLogs.value.has(getTaskPanelKey(message, index))
}

const toggleTaskLog = (message, index) => {
  const key = getTaskPanelKey(message, index)
  const next = new Set(collapsedTaskLogs.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  collapsedTaskLogs.value = next
}

const isTaskStepExpanded = (groupKey) => {
  return !collapsedTaskSteps.value.has(groupKey)
}

const toggleTaskStep = (groupKey) => {
  const next = new Set(collapsedTaskSteps.value)
  if (next.has(groupKey)) {
    next.delete(groupKey)
  } else {
    next.add(groupKey)
  }
  collapsedTaskSteps.value = next
}

const getVisibleTaskEvents = (message) => {
  return (message.taskEvents || []).filter(event => event.eventType !== 'answer_delta')
}

const getTaskTimelineGroups = (message) => {
  const events = getVisibleTaskEvents(message)
  const runId = message.taskRunId || events[0]?.runId || message.time || 'task'
  const groups = []
  let currentGroup = null
  let stepNumber = 0

  const ensureGroup = (fallbackTitle = '准备任务') => {
    if (!currentGroup) {
      currentGroup = {
        key: `${runId}-prep`,
        title: fallbackTitle,
        events: []
      }
      groups.push(currentGroup)
    }
    return currentGroup
  }

  events.forEach((event) => {
    if (event.eventType === 'plan_created') {
      const group = {
        key: `${runId}-plan-${event.stepId || groups.length}`,
        title: '执行计划',
        events: [event]
      }
      groups.push(group)
      currentGroup = null
      return
    }

    if (event.eventType === 'step_started') {
      stepNumber += 1
      currentGroup = {
        key: `${runId}-step-${stepNumber}-${event.stepId || groups.length}`,
        title: normalizeStepTitle(event.content, stepNumber),
        events: []
      }
      groups.push(currentGroup)
      return
    }

    ensureGroup().events.push(event)
  })

  return groups.map((group, index) => decorateTaskGroup(group, index, groups.length, message))
}

const normalizeStepTitle = (content, stepNumber) => {
  const text = String(content || '').trim()
  if (!text) return `步骤 ${stepNumber}`
  return text.replace(/^步骤\s*\d+[：:]\s*/, `步骤 ${stepNumber}：`)
}

const decorateTaskGroup = (group, index, total, message) => {
  const lastEvent = group.events[group.events.length - 1]
  const pending = group.events.some(event => event.eventType === 'confirmation_required' && !event.resolved)
  const failed = group.events.some(event => event.eventType === 'error')
  const finished = group.events.some(event => event.eventType === 'finished')
  const isLast = index === total - 1
  let status = 'done'
  let statusText = '已完成'

  if (failed) {
    status = 'error'
    statusText = '失败'
  } else if (pending) {
    status = 'waiting'
    statusText = '待确认'
  } else if (finished) {
    status = 'done'
    statusText = '已完成'
  } else if (isLast && message.isLoading) {
    status = 'running'
    statusText = '进行中'
  }

  const summarySource = lastEvent?.toolOutputSummary || lastEvent?.content || lastEvent?.toolInputSummary || lastEvent?.toolName
  const summary = summarySource
    ? String(summarySource).replace(/\s+/g, ' ').trim()
    : (group.events.length ? `${group.events.length} 个事件` : '等待下一步事件')

  return {
    ...group,
    marker: group.title === '执行计划' ? 'P' : String(index),
    status,
    statusText,
    eventCount: group.events.length,
    summary: summary.length > 48 ? `${summary.slice(0, 48)}...` : summary
  }
}

const getLatestTaskEvent = (message) => {
  const visible = getVisibleTaskEvents(message)
  return visible[visible.length - 1] || null
}

const hasPendingConfirmation = (message) => {
  return (message.taskEvents || []).some(event => event.eventType === 'confirmation_required' && !event.resolved)
}

const getTaskStatusText = (message) => {
  const latest = getLatestTaskEvent(message)
  if (hasPendingConfirmation(message)) return '待确认'
  if (latest?.eventType === 'finished') return '已完成'
  if (latest?.eventType === 'error') return '失败'
  if (message.isLoading) return '执行中'
  return latest?.status || '已暂停'
}

const getTaskStatusType = (message) => {
  const status = getTaskStatusText(message)
  if (status === '已完成') return 'success'
  if (status === '失败') return 'danger'
  if (status === '待确认') return 'warning'
  return 'primary'
}

const getTaskSummary = (message) => {
  const latest = getLatestTaskEvent(message)
  if (!latest) return '准备执行'
  const label = getTaskEventTitle(latest)
  const text = latest.content || latest.toolOutputSummary || latest.toolInputSummary || latest.toolName || ''
  const compact = String(text).replace(/\s+/g, ' ').trim()
  return compact ? `${label}: ${compact.slice(0, 42)}${compact.length > 42 ? '...' : ''}` : label
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
// 因为多个聊天入口会在首个数据到达时就将 isLoading 设为 false
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

.task-log {
  width: 100%;
  max-width: 760px;
  background: linear-gradient(180deg, #fbfcff 0%, #fff 100%);
  border: 1px solid #dfe7f3;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 10px 28px rgba(30, 64, 115, 0.08);
}

.task-log-header {
  width: 100%;
  border: 0;
  background: rgba(247, 250, 255, 0.92);
  padding: 12px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  text-align: left;
}

.task-log-header:hover {
  background: #f3f7ff;
}

.task-log-title-wrap {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-shrink: 0;
}

.task-log-toggle-icon {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.task-live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--el-color-success);
  box-shadow: 0 0 0 3px var(--el-color-success-light-9);
}

.task-live-dot.active {
  background: var(--el-color-primary);
  animation: taskPulse 1.4s ease-in-out infinite;
}

.task-log-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.task-log-summary {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.task-log-body {
  padding: 10px 12px 12px;
  background: #fff;
}

.task-step-group {
  position: relative;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
  box-shadow: 0 3px 10px rgba(31, 45, 61, 0.04);
}

.task-step-group + .task-step-group {
  margin-top: 10px;
}

.task-step-group-running {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 6px 18px rgba(64, 158, 255, 0.13);
}

.task-step-group-waiting {
  border-color: var(--el-color-warning-light-5);
}

.task-step-group-error {
  border-color: var(--el-color-danger-light-5);
}

.task-step-header {
  width: 100%;
  border: 0;
  background: transparent;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  text-align: left;
}

.task-step-header:hover .task-step-title {
  color: var(--el-color-primary);
}

.task-step-left {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-shrink: 0;
}

.task-step-marker {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--el-color-success);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 0 0 3px var(--el-color-success-light-9);
  flex-shrink: 0;
}

.task-step-marker-running {
  background: var(--el-color-primary);
  animation: taskPulse 1.4s ease-in-out infinite;
}

.task-step-marker-waiting {
  background: var(--el-color-warning);
  box-shadow: 0 0 0 3px var(--el-color-warning-light-9);
}

.task-step-marker-error {
  background: var(--el-color-danger);
  box-shadow: 0 0 0 3px var(--el-color-danger-light-9);
}

.task-step-toggle-icon {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.task-step-title {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.task-step-state {
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}

.task-step-summary {
  display: block;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-step-meta {
  min-width: 0;
  flex: 1;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: right;
}

.task-step-count {
  display: block;
  margin-top: 2px;
  color: var(--el-text-color-placeholder);
}

.task-step-body {
  margin: 0 12px 12px 24px;
  padding-left: 18px;
  border-left: 2px solid var(--el-border-color-extra-light);
}

.task-collapse-enter-active,
.task-collapse-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
  transform-origin: top;
}

.task-collapse-enter-from,
.task-collapse-leave-to {
  opacity: 0;
  transform: scaleY(0.98);
}

.task-event {
  position: relative;
  padding: 10px 0 10px 18px;
  color: var(--el-text-color-primary);
}

.task-event:last-child {
  padding-bottom: 2px;
}

.task-event-dot {
  position: absolute;
  left: -25px;
  top: 16px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--el-color-primary);
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}

.task-event-tool_call .task-event-dot,
.task-event-step_started .task-event-dot {
  background: var(--el-color-primary);
  animation: taskPulse 1.4s ease-in-out infinite;
}

.task-event-tool_result .task-event-dot,
.task-event-plan_created .task-event-dot,
.task-event-confirmation_resolved .task-event-dot {
  background: var(--el-color-success);
  box-shadow: 0 0 0 2px var(--el-color-success-light-8);
}

.task-event-confirmation_required .task-event-dot,
.task-event-error .task-event-dot {
  background: var(--el-color-danger);
}

.task-event-finished .task-event-dot {
  background: var(--el-color-success);
}

.task-event-header {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
}

.task-event-title {
  font-size: 13px;
  font-weight: 600;
}

.task-event-state {
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}

.task-event-content,
.task-tool-summary p {
  margin: 6px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.task-tool-name {
  display: inline-flex;
  margin-top: 6px;
  padding: 2px 8px;
  border-radius: 6px;
  background: #edf4ff;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
}

.task-tool-summary {
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid var(--el-border-color-extra-light);
}

.task-tool-summary span {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.task-confirm-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.task-answer {
  margin: 10px 12px 12px;
  border: 1px solid var(--el-border-color-lighter);
}

.task-live-hint {
  margin: 0 12px 12px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@keyframes taskPulse {
  0% {
    box-shadow: 0 0 0 0 var(--el-color-primary-light-5);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(64, 158, 255, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0);
  }
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
