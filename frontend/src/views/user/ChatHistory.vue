<template>
  <div class="chat-history">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>会话历史</span>
          <div class="header-right">
            <el-button type="primary" @click="handleCreateConversation">
              <el-icon><Plus /></el-icon>
              新建会话
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索会话标题"
          clearable
          style="width: 300px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="selectedType" placeholder="会话类型" clearable style="width: 150px; margin-left: 10px" @change="handleSearch">
          <el-option label="普通聊天" :value="1" />
          <el-option label="知识检索" :value="2" />
          <el-option label="文档问答" :value="3" />
        </el-select>
        <el-button type="primary" @click="handleSearch" style="margin-left: 10px">
          搜索
        </el-button>
        <el-button @click="handleReset" style="margin-left: 10px">
          重置
        </el-button>
      </div>

      <!-- 会话列表 -->
      <div class="conversation-list" v-loading="loading">
        <div v-if="conversations.length === 0 && !loading" class="empty-state">
          <el-icon class="empty-icon"><ChatLineRound /></el-icon>
          <p class="empty-text">暂无会话记录</p>
          <p class="empty-hint" v-if="searchKeyword || selectedType">尝试调整搜索条件或筛选条件</p>
          <el-button type="primary" @click="handleCreateConversation" style="margin-top: 20px">
            <el-icon><Plus /></el-icon>
            创建新会话
          </el-button>
        </div>

        <div v-else class="conversation-container">
          <!-- 卡片列表 -->
          <div class="conversation-items">
            <div
              v-for="conv in conversations"
              :key="conv.id"
              :class="['conversation-item', { active: selectedConversationId === conv.id }]"
            >
              <div class="conversation-card-header">
                <div class="conversation-id">ID: {{ conv.id }}</div>
                <div class="conversation-actions-header">
                  <el-button
                    text
                    type="primary"
                    @click.stop="selectConversation(conv)"
                    title="查看详情"
                  >
                    <el-icon><View /></el-icon>
                  </el-button>
                  <el-button
                    text
                    type="primary"
                    @click.stop="handleEditTitle(conv)"
                    title="编辑标题"
                  >
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button
                    text
                    type="danger"
                    @click.stop="handleDelete(conv)"
                    title="删除会话"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              
              <div class="conversation-card-body">
                <div class="conversation-title-wrapper">
                  <el-icon class="conversation-icon"><ChatLineRound /></el-icon>
                  <span class="conversation-title">{{ conv.title || '未命名会话' }}</span>
                </div>
                
                <div class="conversation-info-row">
                  <el-tag size="small" :type="conv.type === 1 ? 'primary' : (conv.type === 2 ? 'success' : 'warning')">
                    {{ conv.type === 1 ? '普通聊天' : (conv.type === 2 ? '知识检索' : '文档问答') }}
                  </el-tag>
                  <el-tag type="info" size="small">{{ conv.messageCount || 0 }} 轮对话</el-tag>
                </div>
                
                <div class="conversation-time-row">
                  <el-icon><Clock /></el-icon>
                  <span>{{ formatDateTime(conv.updateTime) }}</span>
                </div>
              </div>
              
              <div class="conversation-card-footer">
                <el-button
                  type="primary"
                  @click.stop="handleContinueConversation(conv)"
                  style="width: 100%"
                >
                  <el-icon><Right /></el-icon>
                  继续对话
                </el-button>
              </div>
            </div>
          </div>

          <!-- 分页 -->
          <div class="pagination-wrapper">
            <el-pagination
              v-if="total > 0"
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </div>
      </div>
    </el-card>

    <!-- 编辑标题对话框 -->
    <el-dialog v-model="editTitleDialogVisible" title="编辑会话标题" width="400px" :lock-scroll="true">
      <el-input v-model="editTitle" placeholder="请输入会话标题" />
      <template #footer>
        <el-button @click="editTitleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTitle">保存</el-button>
      </template>
    </el-dialog>

    <!-- 会话详情对话框（显示该会话中的所有消息） -->
    <el-dialog 
      v-model="showConversationDetail" 
      :title="conversationDetail.conversation ? conversationDetail.conversation.title : '会话详情'" 
      width="80%"
      :close-on-click-modal="false"
      :lock-scroll="true"
    >
      <div v-if="conversationDetail.conversation" class="conversation-detail">
        <div class="detail-header">
          <div class="detail-info">
            <el-tag :type="conversationDetail.conversation.type === 1 ? 'primary' : (conversationDetail.conversation.type === 2 ? 'success' : 'warning')" size="small">
              {{ conversationDetail.conversation.type === 1 ? '普通聊天' : (conversationDetail.conversation.type === 2 ? '知识检索' : '文档问答') }}
            </el-tag>
            <span class="detail-meta">
              {{ conversationDetail.messages.length }} 条消息 · 
              创建于 {{ formatDateTime(conversationDetail.conversation.createTime) }}
            </span>
          </div>
        </div>
        
        <div class="messages-container">
          <div v-if="conversationDetail.messages.length === 0" class="empty-messages">
            <p>该会话暂无消息</p>
          </div>
          <div v-else class="messages-list">
            <div
              v-for="(msg, index) in conversationDetail.messages"
              :key="msg.id"
              :class="['message-item', msg.role === 'user' ? 'user-message' : 'assistant-message']"
            >
              <div class="message-header">
                <el-icon v-if="msg.role === 'user'"><User /></el-icon>
                <el-icon v-else><Service /></el-icon>
                <span class="message-role">{{ msg.role === 'user' ? '用户' : '助手' }}</span>
                <span class="message-time">{{ formatDateTime(msg.createTime) }}</span>
              </div>
              <div class="message-content" v-html="formatMessageContent(msg.content)"></div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showConversationDetail = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { Plus, Search, ChatLineRound, Edit, Delete, User, Service, Right, View, Clock, HomeFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useChatHistory } from '@/composables/useChatHistory'

const router = useRouter()

const {
  loading,
  conversations,
  searchKeyword,
  selectedType,
  selectedConversationId,
  currentPage,
  pageSize,
  total,
  editTitleDialogVisible,
  editTitle,
  showConversationDetail,
  conversationDetail,
  handleSearch,
  handleReset,
  selectConversation,
  handleCreateConversation,
  handleEditTitle,
  handleSaveTitle,
  handleDelete,
  handleContinueConversation,
  handleSizeChange,
  handlePageChange,
  formatTime,
  formatDateTime,
  formatMessageContent
} = useChatHistory({
  isAdmin: false,
  enableCreate: true,
  enableEdit: true,
  enableContinue: true,
  enableBatchDelete: false,
})

const {
  loading,
  conversations,
  searchKeyword,
  selectedType,
  selectedConversationId,
  currentPage,
  pageSize,
  total,
  editTitleDialogVisible,
  editTitle,
  showConversationDetail,
  conversationDetail,
  handleSearch,
  handleReset,
  selectConversation,
  handleCreateConversation,
  handleEditTitle,
  handleSaveTitle,
  handleDelete,
  handleContinueConversation,
  handleSizeChange,
  handlePageChange,
  formatTime,
  formatDateTime,
  formatMessageContent
} = useChatHistory({
  isAdmin: false,
  enableCreate: true,
  enableEdit: true,
  enableContinue: true,
  enableBatchDelete: false,
  defaultPageSize: 20
})
</script>

<style scoped>
.chat-history {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-history :deep(.el-card) {
  display: flex;
  flex-direction: column;
  height: 100%;
  flex: 1;
  margin: 0;
}

.chat-history :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
}

.search-bar {
  margin-bottom: 24px;
  display: flex;
  align-items: center;
}

.conversation-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #909399;
}

.empty-icon {
  font-size: 80px;
  margin-bottom: 20px;
  color: #c0c4cc;
}

.empty-text {
  font-size: 16px;
  color: #606266;
  margin: 0 0 8px 0;
}

.empty-hint {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

/* 容器 */
.conversation-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

/* 卡片网格列表 */
.conversation-items {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding: 4px;
  align-items: start;
}

/* 响应式调整 */
@media (max-width: 1600px) {
  .conversation-items {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 1200px) {
  .conversation-items {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .conversation-items {
    grid-template-columns: 1fr;
  }
}

/* 卡片样式 */
.conversation-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background-color: #fff;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  height: 175px;
  overflow: hidden;
}

.conversation-item:hover {
  border-color: #409eff;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.2);
  transform: translateY(-2px);
}

.conversation-item.active {
  border-color: #409eff;
  background-color: #ecf5ff;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.25);
  border-width: 2px;
}

/* 卡片头部 */
.conversation-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  border-bottom: 1px solid #f0f0f0;
  background-color: #fafafa;
}

.conversation-id {
  font-size: 11px;
  color: #909399;
  font-weight: 500;
}

.conversation-actions-header {
  display: flex;
  gap: 6px;
}

.conversation-actions-header .el-button {
  padding: 4px 8px;
  font-size: 11px;
}

.conversation-actions-header .el-icon {
  font-size: 14px;
}

/* 卡片主体 */
.conversation-card-body {
  flex: 1;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
}

.conversation-title-wrapper {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 0;
}

.conversation-icon {
  font-size: 16px;
  color: #409eff;
  flex-shrink: 0;
}

.conversation-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.conversation-info-row {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.conversation-time-row {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #909399;
  margin-top: auto;
}

.conversation-time-row .el-icon {
  font-size: 12px;
}

/* 卡片底部 */
.conversation-card-footer {
  padding: 8px 10px;
  border-top: 1px solid #f0f0f0;
  background-color: #fafafa;
  flex-shrink: 0;
  margin-top: auto;
}

.conversation-card-footer .el-button {
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 500;
}

.conversation-card-footer .el-icon {
  font-size: 13px;
  margin-right: 3px;
}

.pagination-wrapper {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
}

.conversation-detail {
  max-height: 70vh;
  display: flex;
  flex-direction: column;
}

.detail-header {
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e4e7ed;
}

.detail-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-meta {
  font-size: 12px;
  color: #909399;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  max-height: 60vh;
}

.empty-messages {
  text-align: center;
  padding: 40px;
  color: #909399;
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-item {
  padding: 15px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.user-message {
  background-color: #f0f9ff;
  border-color: #b3d8ff;
}

.assistant-message {
  background-color: #f5f5f5;
  border-color: #d3d3d3;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #909399;
}

.message-role {
  font-weight: 500;
  color: #606266;
}

.message-time {
  margin-left: auto;
}

.message-content {
  line-height: 1.6;
  color: #303133;
  word-wrap: break-word;
}

.message-content code {
  background-color: rgba(64, 158, 255, 0.15) !important; /* 浅蓝色背景，提高对比度 */
  color: #303133 !important; /* 深色文字，确保可读性 */
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
  border: 1px solid rgba(64, 158, 255, 0.2); /* 添加边框增强可见性 */
}

/* Markdown样式 */
.message-content h1, .message-content h2, .message-content h3, 
.message-content h4, .message-content h5, .message-content h6 {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
}

.message-content h1 { font-size: 24px; }
.message-content h2 { font-size: 20px; }
.message-content h3 { font-size: 18px; }

.message-content p {
  margin: 8px 0;
}

.message-content ul, .message-content ol {
  margin: 8px 0;
  padding-left: 24px;
}

.message-content pre {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 16px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 12px 0;
  position: relative;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.message-content pre code {
  background: transparent;
  padding: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  display: block;
  width: 100%;
  /* 不设置 color，让 highlight.js 的语法元素使用自己的颜色 */
}

.message-content pre code.hljs {
  display: block;
  overflow-x: auto;
  padding: 0;
  background: transparent;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  /* 不设置 color，让 vscode-dark.css 处理所有语法高亮颜色 */
}

.message-content blockquote {
  border-left: 4px solid #409eff;
  padding-left: 16px;
  margin: 12px 0;
  color: #606266;
}

.message-content table {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.message-content table th,
.message-content table td {
  border: 1px solid #e4e7ed;
  padding: 8px 12px;
  text-align: left;
}

.message-content table th {
  background: #f5f7fa;
  font-weight: 600;
}

/* KaTeX 数学公式样式 */
.message-content .katex-formula-block {
  margin: 1em 0;
  text-align: center;
}

.message-content .katex {
  font-size: 1.1em;
}

.message-content .katex-display {
  margin: 1em 0;
}

.message-content .katex-display > .katex {
  text-align: center;
}
</style>

