<template>
  <div class="admin-chat-history">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button type="text" @click="handleBack" style="margin-right: 10px">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <span>会话历史管理</span>
          </div>
        </div>
      </template>

      <!-- 搜索和筛选 -->
      <div class="filter-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索会话标题"
          clearable
          style="width: 250px"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="selectedType" placeholder="会话类型" clearable style="width: 150px; margin-left: 10px">
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

      <!-- 批量操作 -->
      <div class="batch-actions" v-if="selectedIds.length > 0">
        <el-button type="danger" @click="handleBatchDelete">
          批量删除 ({{ selectedIds.length }})
        </el-button>
      </div>

      <!-- 对话列表 -->
      <div class="table-wrapper">
        <el-table
          v-loading="loading"
          :data="conversations"
          @selection-change="handleSelectionChange"
          style="width: 100%"
          :default-sort="{ prop: 'updateTime', order: 'descending' }"
          height="100%"
        >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户" width="120" align="center" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'primary' : (row.type === 2 ? 'success' : 'warning')" size="small">
              {{ row.type === 1 ? '普通聊天' : (row.type === 2 ? '知识检索' : '文档问答') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip align="left" />
        <el-table-column prop="messageCount" label="对话轮数" width="100" align="center">
          <template #default="{ row }">
            {{ row.messageCount }} 轮
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="handleView(row)">
              查看
            </el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 15, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

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
              {{ conversationDetail.conversation.type === 1 ? '普通聊天' : (conversationDetail.conversation.type === 2 ? '知识库问答' : '文档问答') }}
            </el-tag>
            <span class="detail-meta">
              用户：{{ conversationDetail.conversation.username }} · 
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
import { useRouter } from 'vue-router'
import { Search, User, Service, ArrowLeft } from '@element-plus/icons-vue'
import { useChatHistory } from '@/composables/useChatHistory'

const router = useRouter()

// 返回主页
const handleBack = () => {
  router.push('/admin/chat')
}

const {
  loading,
  conversations,
  searchKeyword,
  selectedType,
  selectedIds,
  currentPage,
  pageSize,
  total,
  showConversationDetail,
  conversationDetail,
  handleSearch,
  handleReset,
  handleSelectionChange,
  handleView,
  handleDelete,
  handleBatchDelete,
  handleSizeChange,
  handlePageChange,
  formatDateTime,
  formatMessageContent
} = useChatHistory({
  isAdmin: true,
  enableCreate: false,
  enableEdit: false,
  enableContinue: false,
  enableBatchDelete: true,
  defaultPageSize: 15
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.admin-chat-history {
  padding: var(--spacing-lg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-secondary);
}

/* ========== 卡片样式 ========== */
:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  transition: box-shadow var(--transition-base);
}

:deep(.el-card:hover) {
  box-shadow: var(--card-shadow-hover);
}

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: var(--spacing-md) var(--card-padding);
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-lighter);
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: var(--card-padding);
  min-height: 0;
  background: var(--color-bg-primary);
}

/* ========== 卡片头部 ========== */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.header-left span {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

/* ========== 过滤栏 ========== */
.filter-bar {
  margin-bottom: var(--spacing-lg);
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-lighter);
  flex-wrap: wrap;
}

.filter-bar :deep(.el-input__wrapper),
.filter-bar :deep(.el-select .el-input__wrapper) {
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.filter-bar :deep(.el-input__wrapper:hover) {
  box-shadow: var(--shadow-xs);
}

.filter-bar :deep(.el-input__wrapper.is-focus) {
  box-shadow: var(--shadow-primary);
}

.filter-bar .el-button {
  transition: all var(--transition-base);
}

.filter-bar .el-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

/* ========== 批量操作 ========== */
.batch-actions {
  margin-bottom: var(--spacing-lg);
  flex-shrink: 0;
  padding: var(--spacing-md);
  background: var(--color-warning-light);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-warning);
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

:deep(.table-wrapper .el-table) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

:deep(.table-wrapper .el-table .el-table__body-wrapper) {
  flex: 1;
  overflow-y: auto;
}

/* ========== 分页容器 ========== */
.pagination-container {
  margin-top: var(--spacing-lg);
  display: flex;
  justify-content: flex-end;
  padding: var(--spacing-md);
  flex-shrink: 0;
  background: var(--color-bg-tertiary);
  border-top: 1px solid var(--color-border-lighter);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}

:deep(.el-pagination .el-pager li) {
  border-radius: var(--radius-sm);
  transition: all var(--transition-base);
}

:deep(.el-pagination .el-pager li:hover) {
  background-color: var(--color-bg-hover);
}

:deep(.el-pagination .el-pager li.is-active) {
  background-color: var(--color-primary);
  color: #ffffff;
}

/* ========== 表格样式 ========== */
:deep(.el-table) {
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--color-bg-primary);
}

:deep(.el-table__header) {
  background: var(--table-header-bg);
}

:deep(.el-table th) {
  background: var(--table-header-bg);
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
  border-bottom: 2px solid var(--color-border-base);
  padding: var(--spacing-sm) 0;
}

:deep(.el-table td) {
  border-bottom: 1px solid var(--table-border-color);
  padding: var(--spacing-sm) 0;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background-color: var(--color-bg-tertiary);
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--table-row-hover-bg);
  transition: background-color var(--transition-fast);
}

:deep(.el-table .el-table__cell) {
  padding: var(--spacing-sm) 0;
}

:deep(.el-table th.el-table__cell) {
  text-align: center;
}

:deep(.el-table th.el-table__cell:first-child) {
  text-align: center;
}

:deep(.el-table td.el-table__cell) {
  text-align: center;
}

:deep(.el-table td.el-table__cell[aria-colindex="5"]) {
  text-align: left;
}

/* ========== 对话详情 ========== */
.conversation-detail {
  max-height: 70vh;
  display: flex;
  flex-direction: column;
}

.detail-header {
  margin-bottom: var(--spacing-lg);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--color-border-lighter);
}

.detail-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.detail-meta {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
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
  padding: var(--spacing-md);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-light);
  transition: all var(--transition-base);
}

.message-item:hover {
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.user-message {
  background: linear-gradient(135deg, var(--color-primary-light-5) 0%, var(--color-bg-primary) 100%);
  border-color: var(--color-primary-light-3);
}

.assistant-message {
  background-color: var(--color-bg-tertiary);
  border-color: var(--color-border-base);
}

.message-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.message-role {
  font-weight: var(--font-weight-medium);
  color: var(--color-text-regular);
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

