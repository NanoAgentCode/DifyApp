<template>
  <div class="knowledge-base-qa">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>知识库问答</span>
          <el-button type="primary" @click="handleClearHistory">
            <el-icon><Delete /></el-icon>
            清空历史
          </el-button>
        </div>
      </template>

      <div class="qa-container">
        <!-- 左侧：知识库选择 -->
        <div class="left-panel">
          <div class="panel-title">
            <el-icon><Folder /></el-icon>
            <span>选择知识库</span>
          </div>
          <!-- 搜索框 -->
          <div class="kb-search">
            <el-input
              v-model="kbSearchKeyword"
              placeholder="搜索知识库"
              clearable
              size="small"
              @input="handleKbSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </div>
          <div class="kb-list">
            <div v-if="displayedKnowledgeBases.length === 0 && !kbSearchKeyword" class="empty-kb-list">
              <el-icon><Document /></el-icon>
              <p>暂无可用知识库</p>
              <p class="empty-tip">请先在知识库管理中创建知识库</p>
            </div>
            <div v-else-if="displayedKnowledgeBases.length === 0 && kbSearchKeyword" class="empty-kb-list">
              <el-icon><Search /></el-icon>
              <p>未找到匹配的知识库</p>
            </div>
            <template v-else>
              <el-tooltip
                v-for="kb in displayedKnowledgeBases"
                :key="kb.id"
                :content="getKnowledgeBaseTooltipContent(kb)"
                placement="right"
                :disabled="!getKnowledgeBaseTooltipContent(kb)"
                :teleported="false"
                raw-content
                popper-class="kb-summary-tooltip"
              >
                <template #default>
                  <div
                    :class="['kb-item', { 
                      active: selectedKB?.id === kb.id, 
                      disabled: !isKnowledgeBaseActive(kb.status) || !isEmbeddingModelEnabled(kb.embeddingModelId) || !isVectorStoreTypeEnabled(kb.vectorStoreType)
                    }]"
                    @click="(!isKnowledgeBaseActive(kb.status) || !isEmbeddingModelEnabled(kb.embeddingModelId) || !isVectorStoreTypeEnabled(kb.vectorStoreType)) ? null : selectKB(kb)"
                  >
                <el-icon class="kb-icon"><Document /></el-icon>
                <div class="kb-info">
                  <div class="kb-name">{{ kb.name }}</div>
                  <div class="kb-meta">
                    <span class="kb-docs">{{ kb.documentCount }} 个文档</span>
                  </div>
                </div>
                <el-tag
                  v-if="kb.status === 'active'"
                  type="success"
                  size="small"
                  class="kb-status"
                >
                  启用
                </el-tag>
                <el-tag
                  v-else-if="kb.status === 'inactive'"
                  type="danger"
                  size="small"
                  class="kb-status"
                >
                  禁用
                </el-tag>
                <!-- 禁用原因提示图标 -->
                <el-icon 
                  v-if="getKnowledgeBaseDisabledTip(kb)"
                  class="kb-warning-icon"
                  :title="getKnowledgeBaseDisabledTip(kb)"
                >
                  <Warning />
                </el-icon>
                <!-- 占位符，确保没有警告图标时也保持相同宽度 -->
                <span v-else class="kb-warning-placeholder"></span>
                  </div>
                </template>
              </el-tooltip>
              <!-- 加载更多按钮 -->
              <div v-if="hasMoreToLoad" class="load-more-container">
                <el-button
                  text
                  type="primary"
                  @click="loadMore"
                  :loading="loadingMore"
                  class="load-more-btn"
                >
                  {{ loadingMore ? '加载中...' : `加载更多 (还有 ${remainingCount} 个)` }}
                </el-button>
              </div>
            </template>
          </div>
        </div>

        <!-- 右侧：问答区域 -->
        <div class="right-panel">
          <div v-if="!selectedKB" class="empty-state">
            <el-icon class="empty-icon"><ChatLineRound /></el-icon>
            <p>请选择一个知识库开始问答</p>
          </div>

          <div v-else class="qa-content">
            <!-- 知识库信息 -->
            <div class="kb-header">
              <div class="kb-header-info">
                <el-icon class="kb-header-icon"><Document /></el-icon>
                <div>
                  <div class="kb-header-name">{{ selectedKB.name }}</div>
                  <div class="kb-header-desc">{{ selectedKB.description }}</div>
                </div>
              </div>
              <!-- 问答模型选择 -->
              <div class="model-selector" style="margin-top: 10px;">
                <el-select
                  v-model="selectedModelId"
                  placeholder="选择问答模型"
                  size="small"
                  style="width: 200px;"
                  :disabled="sending || !isKnowledgeBaseActive(selectedKB.status) || !isEmbeddingModelEnabled(selectedKB.embeddingModelId) || !isVectorStoreTypeEnabled(selectedKB.vectorStoreType)"
                >
                  <el-option
                    v-for="model in availableQAModels"
                    :key="model.id"
                    :label="model.name"
                    :value="model.id"
                  >
                    <span :style="getModelStyle(model.id)">{{ model.name }}</span>
                  </el-option>
                </el-select>
              </div>
              <!-- 向量化模型禁用提示 -->
              <el-alert
                v-if="!isEmbeddingModelEnabled(selectedKB.embeddingModelId)"
                type="warning"
                :closable="false"
                show-icon
                style="margin-top: 10px;"
              >
                <template #title>
                  <span>该知识库使用的向量化模型"{{ getEmbeddingModelName(selectedKB.embeddingModelId) || '默认向量化模型' }}"已被禁用，无法进行问答。请联系管理员启用该向量化模型。</span>
                </template>
              </el-alert>
              <!-- 向量库禁用提示 -->
              <el-alert
                v-if="!isVectorStoreTypeEnabled(selectedKB.vectorStoreType)"
                type="warning"
                :closable="false"
                show-icon
                style="margin-top: 10px;"
              >
                <template #title>
                  <span>该知识库使用的向量库类型"{{ getVectorStoreTypeName(selectedKB.vectorStoreType) }}"已被禁用，无法进行问答。请联系管理员在向量库管理中启用该类型的向量库配置。</span>
                </template>
              </el-alert>
              <!-- 知识库禁用提示 -->
              <el-alert
                v-if="!isKnowledgeBaseActive(selectedKB.status)"
                type="warning"
                :closable="false"
                show-icon
                style="margin-top: 10px;"
              >
                <template #title>
                  <span>该知识库已被禁用，无法进行问答。请联系管理员在知识库管理中启用该知识库。</span>
                </template>
              </el-alert>
            </div>

            <!-- 对话历史 -->
            <div class="chat-history" ref="chatHistoryRef">
              <div class="chat-history-content">
                <div
                  v-for="(message, index) in chatHistory"
                  :key="index"
                  :class="['message-item', message.type]"
                >
                <div class="message-avatar">
                  <el-icon v-if="message.type === 'user'"><User /></el-icon>
                  <el-icon v-else><Service /></el-icon>
                </div>
                <div class="message-content">
                  <div 
                    v-if="message.isLoading"
                    class="message-text loading"
                  >
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <span>AI正在思考中...</span>
                  </div>
                  <div 
                    v-else
                    class="message-text" 
                    v-html="renderMarkdown(message.content)"
                  ></div>
                  
                  <!-- 重新生成按钮（仅助手消息且已完成时显示） -->
                  <div 
                    v-if="message.type === 'assistant' && !message.isLoading && message.content"
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
                  
                  <!-- 来源文档 -->
                  <div v-if="message.sources && message.sources.length > 0" class="message-sources">
                    <el-collapse>
                      <el-collapse-item>
                        <template #title>
                          <span class="sources-title">
                            <el-icon><Document /></el-icon>
                            参考来源 ({{ message.sources.length }})
                          </span>
                        </template>
                        <div class="sources-list">
                          <div 
                            v-for="(source, idx) in message.sources" 
                            :key="idx"
                            class="source-item"
                          >
                            <div class="source-header">
                              <span class="source-index">文档 {{ idx + 1 }}</span>
                              <el-tag 
                                v-if="source.score" 
                                size="small" 
                                type="success"
                                effect="light"
                              >
                                <el-icon style="margin-right: 4px; vertical-align: middle;"><Star /></el-icon>
                                相似度: {{ (source.score * 100).toFixed(1) }}%
                              </el-tag>
                            </div>
                            <div class="source-text">{{ source.text }}</div>
                          </div>
                        </div>
                      </el-collapse-item>
                    </el-collapse>
                  </div>
                  
                  <div class="message-time">{{ message.time }}</div>
                </div>
              </div>
              </div>
            </div>

            <!-- 输入区域 -->
            <div class="input-area">
              <el-input
                v-model="question"
                type="textarea"
                :rows="3"
                placeholder="请输入您的问题..."
                @keydown.ctrl.enter="handleSend"
                @keydown.enter.exact.prevent="handleSend"
                :disabled="!selectedKB || sending || !isKnowledgeBaseActive(selectedKB?.status) || !isEmbeddingModelEnabled(selectedKB?.embeddingModelId) || !isVectorStoreTypeEnabled(selectedKB?.vectorStoreType)"
              />
              <div class="input-actions">
                <div class="input-tips">
                  <el-checkbox v-model="useStream" size="small">流式响应</el-checkbox>
                  <span class="tips-text">按 Ctrl + Enter 或 Enter 发送</span>
                </div>
                <div class="input-buttons">
                  <el-button
                    @click="handleNewConversation"
                    :disabled="sending"
                  >
                    <el-icon><Plus /></el-icon>
                    开启新对话
                  </el-button>
                  <el-button
                    type="primary"
                    :disabled="!question.trim() || !selectedKB || sending || !isKnowledgeBaseActive(selectedKB?.status) || !isEmbeddingModelEnabled(selectedKB?.embeddingModelId) || !isVectorStoreTypeEnabled(selectedKB?.vectorStoreType)"
                    @click="handleSend"
                    :loading="sending"
                  >
                    <el-icon><Promotion /></el-icon>
                    发送
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted } from 'vue'
import {
  Delete,
  Folder,
  Document,
  ChatLineRound,
  User,
  Service,
  Promotion,
  Loading,
  Star,
  Search,
  Plus,
  Warning,
  Refresh
} from '@element-plus/icons-vue'
import { getModelStyle } from '@/utils/modelColor'
import { renderMarkdown } from '@/composables/useMarkdown'
import { useKnowledgeBaseQA } from '@/composables/useKnowledgeBaseQA'

// 使用知识库问答 composable（启用对话历史功能，非管理员）
const {
  knowledgeBases,
  selectedKB,
  embeddingModels,
  question,
  sending,
  chatHistory,
  chatHistoryRef,
  conversationId,
  useStream,
  currentStreamingMessage,
  kbSearchKeyword,
  displayedCount,
  loadingMore,
  availableQAModels,
  selectedModelId,
  vectorDatabases,
  enabledVectorStoreTypes,
  filteredKnowledgeBases,
  displayedKnowledgeBases,
  hasMoreToLoad,
  remainingCount,
  handleKbSearch,
  loadMore,
  selectKB,
  loadKnowledgeBases,
  loadEmbeddingModels,
  getEmbeddingModelName,
  isEmbeddingModelEnabled,
  loadVectorDatabases,
  isVectorStoreTypeEnabled,
  getVectorStoreTypeName,
  isKnowledgeBaseActive,
  getKnowledgeBaseDisabledTip,
  getKnowledgeBaseTooltipContent,
  loadQAModels,
  handleSend,
  handleClearHistory,
  handleNewConversation,
  handleRegenerate,
  scrollToBottom,
  loadConversationHistory,
  cleanup
} = useKnowledgeBaseQA({
  enableConversationHistory: true, // user版本启用对话历史
  isAdmin: false // user版本不是管理员
})


// 手动触发代码高亮（用于流式响应中逐步生成的代码块）
const highlightCodeBlocks = () => {
  nextTick(() => {
    if (chatHistoryRef.value) {
      const codeBlocks = chatHistoryRef.value.querySelectorAll('pre code')
      codeBlocks.forEach((block) => {
        try {
          const isFormula = block.closest('.katex') || 
                           block.closest('.katex-formula-block')
          if (isFormula) return
          
          const originalText = block.textContent
          const needsHighlight = !block.classList.contains('hljs') || 
                                block.dataset.highlighted !== 'true' ||
                                block.dataset.originalText !== originalText
          
          if (needsHighlight && originalText.trim()) {
            // 代码高亮已在 useMarkdown 中处理，这里只需要标记
            block.classList.add('hljs')
            block.dataset.highlighted = 'true'
            block.dataset.originalText = originalText
          }
        } catch (err) {
          console.error('手动高亮代码块失败', err)
        }
      })
    }
  })
}

onMounted(async () => {
  loadKnowledgeBases()
  loadEmbeddingModels()
  loadQAModels()
  loadVectorDatabases()
  
  // 检查是否有继续对话的标记
  const continueConvId = localStorage.getItem('continueConversationId')
  if (continueConvId) {
    localStorage.removeItem('continueConversationId')
    await loadConversationHistory(continueConvId)
    await nextTick()
    highlightCodeBlocks()
  }
})

onUnmounted(() => {
  cleanup()
})
</script>

<style>
/* 防止浏览器滚动 - 全局样式 */
body {
  overflow: hidden !important;
  height: 100vh !important;
}

/* 全局移除知识库摘要tooltip的黑色边框 */
.kb-summary-tooltip {
  border: none !important;
  border-width: 0 !important;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1) !important;
}

.kb-summary-tooltip .el-tooltip__arrow::before {
  border: none !important;
  border-color: transparent !important;
}

.kb-summary-tooltip .el-tooltip__arrow::after {
  border: none !important;
  border-color: transparent !important;
}

.kb-summary-tooltip .el-tooltip__arrow {
  border: none !important;
  border-color: transparent !important;
}

html {
  overflow: hidden !important;
  height: 100vh !important;
}
</style>

<style scoped>
/* 全局隐藏滚动条 */
:deep(.el-card__body) {
  overflow: hidden;
}

.knowledge-base-qa {
  padding: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card__body) {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.qa-container {
  display: flex;
  gap: 20px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.left-panel {
  width: 280px;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 16px;
  flex-shrink: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 隐藏左侧面板滚动条 */
.left-panel::-webkit-scrollbar {
  width: 6px;
}

.left-panel::-webkit-scrollbar-track {
  background: transparent;
}

.left-panel::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
}

.left-panel::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.kb-search {
  margin-bottom: 12px;
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty-kb-list {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
  color: #909399;
}

.empty-kb-list .el-icon {
  font-size: 48px;
  color: #c0c4cc;
  margin-bottom: 12px;
}

.empty-kb-list p {
  margin: 4px 0;
  font-size: 14px;
}

.empty-kb-list .empty-tip {
  font-size: 12px;
  color: #c0c4cc;
}

.kb-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px; /* 减少上下padding，收窄高度 */
  background: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid transparent;
  min-height: 48px; /* 减少最小高度 */
  box-sizing: border-box;
}

.kb-item:hover {
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.kb-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.kb-icon {
  color: #409eff;
  font-size: 20px;
  flex-shrink: 0;
}

.kb-info {
  flex: 1;
  min-width: 0;
}

.kb-name {
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px; /* 减少底部间距 */
  line-height: 1.3; /* 减少行高 */
}

.kb-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.kb-docs {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.kb-model-tag {
  flex-shrink: 0;
}

.kb-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}

.kb-item.disabled .kb-name,
.kb-item.disabled .kb-docs {
  color: #c0c4cc;
}

.kb-status {
  flex-shrink: 0;
}

.kb-warning-icon {
  color: #e6a23c;
  font-size: 18px;
  flex-shrink: 0;
  cursor: help;
  width: 18px; /* 固定宽度 */
  height: 18px; /* 固定高度 */
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.kb-warning-placeholder {
  width: 18px; /* 与警告图标相同的宽度 */
  height: 18px; /* 与警告图标相同的高度 */
  flex-shrink: 0;
  display: inline-block;
}

.load-more-container {
  margin-top: 12px;
  text-align: center;
  padding: 8px 0;
}

.load-more-btn {
  width: 100%;
  font-size: 12px;
}

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 4px;
  overflow: hidden;
  min-height: 0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  color: #c0c4cc;
}

.qa-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  position: relative;
}

.kb-header {
  padding: 16px 20px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
  z-index: 10;
}

.kb-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.kb-header-icon {
  font-size: 24px;
  color: #409eff;
  flex-shrink: 0;
}

.kb-header-name {
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.kb-header-desc {
  font-size: 12px;
  color: #909399;
}

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

/* 隐藏对话历史滚动条 */
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

/* Firefox 隐藏滚动条 */
.chat-history {
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.1) transparent;
}

.message-item {
  display: flex;
  gap: 12px;
}

/* 用户消息：右对齐，从右侧滑入动画 */
.message-item.user {
  flex-direction: row-reverse;
  animation: slideInFromRight 0.4s ease-out;
}

/* 助手消息：左对齐，从左向右动画 */
.message-item.assistant {
  animation: slideInFromLeft 0.4s ease-out;
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

.message-item.user .message-avatar {
  background: #409eff;
  color: white;
}

.message-item.assistant .message-avatar {
  background: #f0f2f5;
  color: #606266;
}

.message-content {
  width: 70%;
  max-width: 70%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
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
  padding: 12px 16px;
  border-radius: 8px;
  line-height: 1.6;
  word-break: break-word;
}

.message-text :deep(p) {
  margin: 0.5em 0;
}

.message-text :deep(p:first-child) {
  margin-top: 0;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(code) {
  background: rgba(64, 158, 255, 0.15) !important; /* 浅蓝色背景，提高对比度 */
  color: #303133 !important; /* 深色文字，确保可读性 */
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
  border: 1px solid rgba(64, 158, 255, 0.2); /* 添加边框增强可见性 */
}

.message-item.assistant .message-text :deep(code) {
  background: rgba(64, 158, 255, 0.2) !important; /* 稍深的蓝色背景 */
  color: #303133 !important;
  border: 1px solid rgba(64, 158, 255, 0.3);
}

.message-text :deep(pre) {
  background: rgba(0, 0, 0, 0.05);
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 0.5em 0;
}

.message-item.assistant .message-text :deep(pre) {
  background: rgba(0, 0, 0, 0.1);
}

.message-text :deep(pre code) {
  background: transparent;
  padding: 0;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.message-text :deep(blockquote) {
  border-left: 3px solid rgba(0, 0, 0, 0.2);
  padding-left: 1em;
  margin: 0.5em 0;
  color: rgba(0, 0, 0, 0.7);
}

.message-text :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.5em 0;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid rgba(0, 0, 0, 0.1);
  padding: 6px 12px;
  text-align: left;
}

.message-text :deep(th) {
  background: rgba(0, 0, 0, 0.05);
  font-weight: 600;
}

.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3),
.message-text :deep(h4),
.message-text :deep(h5),
.message-text :deep(h6) {
  margin: 0.8em 0 0.5em 0;
  font-weight: 600;
  line-height: 1.4;
}

.message-text :deep(h1) {
  font-size: 1.5em;
  border-bottom: 2px solid rgba(0, 0, 0, 0.1);
  padding-bottom: 0.3em;
}

.message-text :deep(h2) {
  font-size: 1.3em;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  padding-bottom: 0.3em;
}

.message-text :deep(h3) {
  font-size: 1.1em;
}

.message-text :deep(h4),
.message-text :deep(h5),
.message-text :deep(h6) {
  font-size: 1em;
}

.message-text :deep(a) {
  color: #409eff;
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: all 0.3s;
}

.message-text :deep(a:hover) {
  color: #66b1ff;
  border-bottom-color: #66b1ff;
}

.message-text :deep(hr) {
  border: none;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  margin: 1em 0;
}

.message-text :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 0.5em 0;
}

.message-text :deep(strong) {
  font-weight: 600;
}

.message-text :deep(em) {
  font-style: italic;
}

/* KaTeX 数学公式样式 */
.message-text :deep(.katex) {
  font-size: 1.1em;
}

.message-text :deep(.katex-display) {
  margin: 1em 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.message-text :deep(.katex-display > .katex) {
  display: inline-block;
  text-align: initial;
}

/* 确保数学公式在深色和浅色背景下都清晰可见 */
.message-item.assistant .message-text :deep(.katex) {
  color: #303133;
}

.message-item.user .message-text :deep(.katex) {
  color: white;
}

.message-text.loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
}

.message-item.user .message-text {
  background: #409eff;
  color: white;
  border-bottom-right-radius: 2px;
}

.message-item.assistant .message-text {
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
  color: #303133;
  border-bottom-left-radius: 2px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
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
  padding: 0 4px;
}

.message-sources {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 2px dashed #e4e7ed;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.sources-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #409eff;
  font-weight: 600;
}

/* 自定义折叠面板样式 */
.message-sources :deep(.el-collapse) {
  border: none;
}

.message-sources :deep(.el-collapse-item__header) {
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  padding: 5px 10px;
  font-weight: 600;
  color: #409eff;
  font-size: 12px;
  transition: all 0.3s;
}

.message-sources :deep(.el-collapse-item__header:hover) {
  background: #e1f3ff;
  border-color: #409eff;
}

.message-sources :deep(.el-collapse-item__wrap) {
  border: none;
}

.message-sources :deep(.el-collapse-item__content) {
  padding: 6px 0;
  background: transparent;
}

.sources-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.source-item {
  padding: 7px 8px;
  background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
  border-radius: 6px;
  border-left: 3px solid #67c23a;
  border: 1px solid #e4e7ed;
  border-left: 3px solid #67c23a;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  transition: all 0.3s;
}

.source-item:hover {
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.source-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.source-index {
  font-weight: 600;
  color: #67c23a;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.source-index::before {
  content: '📄';
  font-size: 14px;
}

.source-text {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  max-height: 75px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  background: white;
  padding: 5px 8px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  margin-top: 4px;
}

/* 隐藏来源文档滚动条 */
.source-text::-webkit-scrollbar {
  width: 4px;
}

.source-text::-webkit-scrollbar-track {
  background: transparent;
}

.source-text::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 2px;
}

.source-text::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
}

.input-area {
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
  flex-shrink: 0;
  position: sticky;
  bottom: 10px;
  z-index: 20;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.input-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
}

.input-tips {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.tips-text {
  margin-left: 8px;
}


/* 知识库摘要tooltip样式 - 移除黑色边框 */
:deep(.kb-summary-tooltip) {
  border: none !important;
  border-width: 0 !important;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1) !important;
  background-color: transparent !important;
}

:deep(.kb-summary-tooltip .el-tooltip__arrow::before) {
  border: none !important;
  border-color: transparent !important;
}

:deep(.kb-summary-tooltip .el-tooltip__arrow::after) {
  border: none !important;
  border-color: transparent !important;
}

:deep(.kb-summary-tooltip .el-tooltip__arrow) {
  border: none !important;
  border-color: transparent !important;
}
</style>

