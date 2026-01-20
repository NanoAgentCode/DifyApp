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
              class="conversation-card"
              @click="$emit('conversation-click', conversation)"
            >
              <div class="conversation-content">
                <el-icon class="conversation-icon"><ChatLineRound /></el-icon>
                <div class="conversation-info">
                  <span class="conversation-title">{{ conversation.title || '未命名会话' }}</span>
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
  align-items: center;
  gap: var(--spacing-xl, 24px);
  padding: var(--spacing-xl, 24px);
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.assistant-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md, 16px);
  padding: var(--spacing-lg, 20px);
  background: var(--el-bg-color-page, #f5f7fa);
  border-radius: var(--el-border-radius-base, 4px);
  width: 100%;
  max-width: 600px;
}

.assistant-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--el-color-primary, #409eff) 0%, var(--el-color-primary-light-3, #79bbff) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.assistant-icon {
  font-size: 32px;
  color: white;
}

.assistant-info {
  flex: 1;
}

.assistant-name {
  margin: 0 0 var(--spacing-xs, 4px) 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.assistant-status {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary, #909399);
}

.welcome-card {
  background: var(--el-bg-color, #ffffff);
  border-radius: var(--el-border-radius-base, 4px);
  padding: var(--spacing-xl, 24px);
  width: 100%;
  max-width: 800px;
  box-shadow: var(--el-box-shadow-light, 0 2px 12px 0 rgba(0, 0, 0, 0.1));
}

.welcome-message {
  text-align: center;
  margin-bottom: var(--spacing-lg, 20px);
}

.greeting-text {
  font-size: 18px;
  color: var(--el-text-color-primary, #303133);
  margin: 0 0 var(--spacing-sm, 8px) 0;
}

.greeting-subtext {
  font-size: 14px;
  color: var(--el-text-color-secondary, #909399);
  margin: 0;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md, 16px);
}

.action-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md, 16px);
  padding: var(--spacing-md, 16px);
  background: var(--el-bg-color-page, #f5f7fa);
  border-radius: var(--el-border-radius-base, 4px);
  transition: all 0.2s;
}

.action-item:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
}

.action-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--el-color-primary-light-8, #d9ecff);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--el-color-primary, #409eff);
}

.action-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs, 4px);
}

.action-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
}

.action-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.action-desc kbd {
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 3px;
  padding: 2px 6px;
  font-size: 11px;
  font-family: monospace;
  margin: 0 2px;
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
  .suggestions-section {
    grid-template-columns: 1fr;
  }
  
  .questions-grid {
    grid-template-columns: 1fr;
  }
}
</style>
