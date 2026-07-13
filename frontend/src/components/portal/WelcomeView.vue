<template>
  <div class="welcome-view">
    <!-- 助手身份卡片 -->
    <div class="assistant-card">
      <div class="assistant-avatar">
        <el-icon class="assistant-icon"><Service /></el-icon>
      </div>
      <div class="assistant-info">
        <h2 class="assistant-name">NanoAgent</h2>
        <p class="assistant-status">智能助手 · 随时为您服务</p>
      </div>
    </div>

    <!-- 欢迎消息卡片 -->
    <div class="welcome-card">
      <div class="welcome-message">
        <p class="greeting-text">你好！我是NanoAgent，很高兴为你提供帮助。</p>
        <p class="greeting-subtext">有什么问题或需要协助的地方吗？</p>
      </div>
      
      <!-- 快速操作提示 -->
      <div class="quick-actions">
        <div class="action-item">
          <div class="action-icon">
            <el-icon><Search /></el-icon>
          </div>
          <div class="action-content">
            <span class="action-title">知识库问答</span>
            <span class="action-desc">输入 <kbd>@</kbd> 选择知识库</span>
          </div>
        </div>
        <div class="action-item">
          <div class="action-icon">
            <el-icon><Document /></el-icon>
          </div>
          <div class="action-content">
            <span class="action-title">文档对话</span>
            <span class="action-desc">输入 <kbd>/</kbd> 选择文档</span>
          </div>
        </div>
        <div class="action-item">
          <div class="action-icon">
            <el-icon><InfoFilled /></el-icon>
          </div>
          <div class="action-content">
            <span class="action-title">快捷键</span>
            <div class="action-desc shortcuts-desc">
              <kbd>Ctrl+1</kbd> 智能对话
              <span class="hint-separator">|</span>
              <kbd>Ctrl+2</kbd> 快捷入口
              <span class="hint-separator">|</span>
              <kbd>Ctrl+N</kbd> 新对话
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 最近会话或建议问题 - 两列布局 -->
    <div class="suggestions-section">
      <!-- 左侧：最近会话 -->
      <div class="suggestions-column">
        <div v-if="recentConversations.length > 0" class="recent-conversations">
          <div class="section-header">
            <el-icon class="section-icon"><Clock /></el-icon>
            <span class="section-title">最近会话</span>
          </div>
          <div class="conversations-list" :class="{ 'collapsed-mode': isHeaderCollapsed }">
            <div 
              v-for="(conversation, index) in recentConversations" 
              :key="conversation.id"
              v-memo="[conversation.id, conversation.title, conversation.updateTime, conversation.type, selectedConversationId === conversation.id]"
              :class="['conversation-card', { 'conversation-selected': selectedConversationId === conversation.id }]"
              @click="$emit('conversation-click', conversation)"
            >
              <div class="conversation-content">
                <el-icon class="conversation-icon"><ChatLineRound /></el-icon>
                <div class="conversation-info">
                  <div class="conversation-title-row">
                    <span class="conversation-title">{{ conversation.title || '未命名会话' }}</span>
                    <el-tag v-if="conversation.type === 4" size="small" type="warning" class="conversation-task-tag">任务</el-tag>
                  </div>
                  <span class="conversation-time" v-if="conversation.updateTime">
                    {{ formatConversationTime(conversation.updateTime) }}
                  </span>
                </div>
              </div>
              <el-icon class="conversation-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
        <div v-else class="empty-column">
          <div class="empty-placeholder">
            <el-icon class="empty-icon"><ChatLineRound /></el-icon>
            <span class="empty-text">暂无最近会话</span>
          </div>
        </div>
      </div>

      <!-- 右侧：建议问题 -->
      <div class="suggestions-column">
        <div class="suggested-questions">
          <div class="section-header">
            <el-icon class="section-icon"><Star /></el-icon>
            <span class="section-title">快速开始</span>
          </div>
          <div class="questions-grid" :class="{ 'collapsed-mode': isHeaderCollapsed }">
            <div 
              v-for="(question, index) in displayedQuestions"
              :key="index"
              class="question-card"
              @click="$emit('prompt-click', question.text)"
            >
              <el-icon class="question-icon">
                <QuestionFilled v-if="question.icon === 'QuestionFilled'" />
                <Document v-else-if="question.icon === 'Document'" />
                <ChatDotRound v-else-if="question.icon === 'ChatDotRound'" />
                <Star v-else />
              </el-icon>
              <span class="question-text">{{ question.text }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Service, Search, Document, InfoFilled, Clock, ChatLineRound, ArrowRight, Star, QuestionFilled, ChatDotRound } from '@element-plus/icons-vue'

const props = defineProps({
  recentConversations: {
    type: Array,
    default: () => []
  },
  suggestedQuestions: {
    type: Array,
    default: () => []
  },
  isHeaderCollapsed: {
    type: Boolean,
    default: false
  },
  selectedConversationId: {
    type: [String, Number],
    default: null
  }
})

const emit = defineEmits(['conversation-click', 'prompt-click'])

const displayedQuestions = computed(() => {
  return props.suggestedQuestions.slice(0, props.isHeaderCollapsed ? 4 : 3)
})

const formatConversationTime = (timeStr) => {
  if (!timeStr) return ''
  try {
    const time = new Date(timeStr)
    const now = new Date()
    const diff = now - time
    const minutes = Math.floor(diff / 60000)
    const hours = Math.floor(diff / 3600000)
    const days = Math.floor(diff / 86400000)
    
    if (minutes < 1) return '刚刚'
    if (minutes < 60) return `${minutes}分钟前`
    if (hours < 24) return `${hours}小时前`
    if (days < 7) return `${days}天前`
    
    return time.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  } catch (e) {
    return ''
  }
}
</script>

<style scoped>
/* 从 Portal.vue 复制相关样式 */
.welcome-view {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.assistant-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--color-bg-primary);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  margin-bottom: var(--spacing-md);
  width: 100%;
  border: 1px solid var(--color-border-lighter);
  transition: all var(--transition-base);
  flex-shrink: 0;
}

.assistant-card:hover {
  box-shadow: var(--shadow-primary-lg);
  transform: translateY(-2px);
  border-color: var(--color-primary);
}

.assistant-avatar {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--color-primary-light-4) 0%, var(--color-primary-light-5) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all var(--transition-base);
  box-shadow: var(--shadow-xs);
}

.assistant-card:hover .assistant-avatar {
  transform: scale(1.1);
  box-shadow: var(--shadow-sm);
}

.assistant-icon {
  font-size: 24px;
  color: var(--color-primary);
  transition: all var(--transition-base);
}

.assistant-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.assistant-name {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.assistant-status {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.welcome-card {
  background: var(--color-bg-primary);
  border-radius: var(--radius-xl);
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  width: 100%;
  border: 1px solid var(--color-border-lighter);
  box-shadow: var(--shadow-sm);
  flex-shrink: 0;
  transition: all var(--transition-base);
}

.welcome-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-border-base);
  transform: translateY(-1px);
}

.welcome-message {
  margin-bottom: var(--spacing-sm);
}

.greeting-text {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  margin: 0 0 var(--spacing-xs) 0;
  line-height: var(--line-height-normal);
}

.greeting-subtext {
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  margin: 0;
  line-height: var(--line-height-normal);
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md, 16px);
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--color-border-lighter);
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-xs);
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
  cursor: default;
  text-align: center;
  border: 1px solid transparent;
}

.action-item:hover {
  background: var(--color-bg-active);
  border-color: var(--color-primary-light-3);
  transform: translateY(-1px);
  box-shadow: var(--shadow-xs);
}

.action-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  background: var(--color-primary-light-5);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all var(--transition-base);
}

.action-item:hover .action-icon {
  background: var(--color-primary-light-4);
  transform: scale(1.1);
}

.action-icon .el-icon {
  font-size: var(--font-size-md);
  color: var(--color-primary);
  transition: all var(--transition-base);
}

.action-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 100%;
  text-align: center;
}

.action-title {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
}

.action-desc {
  font-size: 11px;
  color: var(--color-text-secondary);
  line-height: var(--line-height-tight);
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
}

.action-desc kbd {
  display: inline-block;
  padding: var(--spacing-xs) var(--spacing-xs);
  margin: 0 var(--spacing-xs);
  font-size: 10px;
  font-family: var(--font-family-mono);
  color: var(--color-primary);
  background: var(--color-primary-light-5);
  border: 1px solid var(--color-primary-light-3);
  border-radius: var(--radius-sm);
  font-weight: var(--font-weight-medium);
}

.shortcuts-desc {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs, 4px);
  flex-wrap: wrap;
}

.hint-separator {
  color: var(--el-text-color-placeholder, #c0c4cc);
  margin: 0 var(--spacing-xs, 4px);
}

.suggestions-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-lg, 20px);
  width: 100%;
  max-width: 1000px;
}

.suggestions-column {
  display: flex;
  flex-direction: column;
}

.section-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm, 8px);
  margin-bottom: var(--spacing-md, 16px);
  font-size: 16px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
}

.section-icon {
  font-size: 18px;
  color: var(--el-color-primary, #409eff);
}

.recent-conversations,
.suggested-questions {
  flex: 1;
}

.conversations-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm, 8px);
}

.conversation-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md, 16px);
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: var(--el-border-radius-base, 4px);
  cursor: pointer;
  transition: all 0.2s;
}

.conversation-card:hover {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
}

.conversation-content {
  display: flex;
  align-items: center;
  gap: var(--spacing-md, 16px);
  flex: 1;
  min-width: 0;
}

.conversation-icon {
  font-size: 20px;
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}

.conversation-info {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs, 4px);
  flex: 1;
  min-width: 0;
}

.conversation-title-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs, 4px);
  min-width: 0;
}

.conversation-selected {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
}

.conversation-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-time {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.conversation-arrow {
  font-size: 16px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  flex-shrink: 0;
}

.empty-column {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.empty-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md, 16px);
  color: var(--el-text-color-placeholder, #c0c4cc);
}

.empty-icon {
  font-size: 48px;
}

.empty-text {
  font-size: 14px;
}

.questions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--spacing-md, 16px);
}

.question-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm, 8px);
  padding: var(--spacing-lg, 20px);
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: var(--el-border-radius-base, 4px);
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}

.question-card:hover {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
  transform: translateY(-2px);
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
}

.question-icon {
  font-size: 32px;
  color: var(--el-color-primary, #409eff);
}

.question-text {
  font-size: 13px;
  color: var(--el-text-color-primary, #303133);
  line-height: 1.5;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .quick-actions {
    grid-template-columns: 1fr;
  }

  .suggestions-section {
    grid-template-columns: 1fr;
  }
  
  .questions-grid {
    grid-template-columns: 1fr;
  }
}

/* 最近会话和快速开始按基线保持等高、紧凑的双列布局。 */
.suggestions-section { gap: 12px; margin-bottom: 0; flex: 1; min-height: 0; max-height: 100%; overflow: hidden; align-items: start; }
.suggestions-column { min-width: 0; height: auto; overflow: visible; align-items: stretch; }
.suggestions-column > * { display: flex; flex-direction: column; height: auto; box-sizing: border-box; }
.section-header { gap: 8px; margin: 0 0 8px; padding: 0; flex-shrink: 0; height: 32px; min-height: 32px; max-height: 32px; font-size: inherit; font-weight: inherit; }
.section-icon { font-size: 16px; }
.section-title { font-size: 14px; font-weight: 600; color: var(--el-text-color-primary, #303133); }
.recent-conversations, .suggested-questions { width: 100%; height: auto; overflow: visible; align-items: stretch; padding: 0; margin: 0; min-height: calc(32px + 8px + 3 * 44px + 2 * 4px); box-sizing: border-box; }
.conversations-list { display: grid; grid-template-rows: repeat(3, 44px); gap: 4px; overflow: visible; flex: 1; min-height: calc(3 * 44px + 2 * 4px); height: calc(3 * 44px + 2 * 4px); align-content: start; box-sizing: border-box; }
.questions-grid { grid-template-columns: 1fr; gap: 4px; align-content: start; overflow: visible; min-height: calc(3 * 44px + 2 * 4px); height: calc(3 * 44px + 2 * 4px); box-sizing: border-box; }
.conversations-list.collapsed-mode, .questions-grid.collapsed-mode { grid-template-rows: repeat(4, 44px); min-height: calc(4 * 44px + 3 * 4px); height: calc(4 * 44px + 3 * 4px); }
.conversation-card { padding: 6px 10px; border-radius: 8px; height: 44px; box-sizing: border-box; overflow: hidden; box-shadow: 0 1px 3px rgba(0, 0, 0, .04); transition: all .3s ease; }
.conversation-card:hover { transform: translateX(4px); box-shadow: 0 4px 12px rgba(64, 158, 255, .15); }
.conversation-content { gap: 12px; }
.conversation-icon { font-size: 16px; }
.conversation-info { gap: 2px; }
.conversation-title { font-size: 13px; }
.conversation-time { font-size: 11px; }
.conversation-arrow { transition: all .3s ease; }
.conversation-card:hover .conversation-arrow { color: var(--el-color-primary, #409eff); transform: translateX(4px); }
.question-card { min-height: 44px; flex-direction: row; justify-content: flex-start; gap: 12px; padding: 6px 10px; border-radius: 8px; text-align: left; }
.question-icon { font-size: 16px; flex-shrink: 0; }
.question-text { font-size: 13px; line-height: 1.4; }

@media (max-width: 768px) { .suggestions-section { gap: 16px; max-height: none; overflow: visible; } .recent-conversations, .suggested-questions { min-height: auto; } .conversations-list, .questions-grid { height: auto; min-height: 0; } }
</style>
