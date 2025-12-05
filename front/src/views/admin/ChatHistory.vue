<template>
  <div class="admin-chat-history">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>会话历史管理</span>
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
          <el-option label="知识库问答" :value="2" />
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
        >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户" width="120" align="center" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'primary' : 'success'" size="small">
              {{ row.type === 1 ? '普通聊天' : '知识库问答' }}
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
        <el-table-column label="操作" width="150" fixed="right" align="center">
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
            <el-tag :type="conversationDetail.conversation.type === 1 ? 'primary' : 'success'" size="small">
              {{ conversationDetail.conversation.type === 1 ? '普通聊天' : '知识库问答' }}
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

<script>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, User, Service } from '@element-plus/icons-vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import katex from 'katex'
import 'katex/dist/katex.min.css'
import {
  getAllConversations,
  deleteConversation,
  batchDeleteConversations,
  getConversationMessages
} from '@/api/chat'

// 主流开发语言别名映射
const languageAliases = {
  'js': 'javascript',
  'ts': 'typescript',
  'py': 'python',
  'rb': 'ruby',
  'sh': 'bash',
  'yml': 'yaml',
  'md': 'markdown',
  'json': 'json',
  'xml': 'xml',
  'html': 'html',
  'css': 'css',
  'scss': 'scss',
  'less': 'less',
  'vue': 'vue',
  'react': 'jsx',
  'jsx': 'jsx',
  'tsx': 'tsx',
  'go': 'go',
  'java': 'java',
  'c': 'c',
  'cpp': 'cpp',
  'cs': 'csharp',
  'php': 'php',
  'swift': 'swift',
  'kt': 'kotlin',
  'rs': 'rust',
  'sql': 'sql',
  'dockerfile': 'dockerfile',
  'yaml': 'yaml'
}

// 规范化语言标识符
const normalizeLanguage = (lang) => {
  if (!lang) return null
  const normalized = lang.toLowerCase().trim()
  return languageAliases[normalized] || normalized
}

// 配置 marked - 使用更可靠的代码高亮方式
marked.setOptions({
  highlight: function(code, lang) {
    if (!code) return ''
    
    // 规范化语言标识符
    const normalizedLang = normalizeLanguage(lang)
    
    try {
      let result
      
      // 如果指定了语言且支持，使用指定语言高亮
      if (normalizedLang && hljs.getLanguage(normalizedLang)) {
        try {
          const highlighted = hljs.highlight(code, { language: normalizedLang })
          result = highlighted.value
        } catch (err) {
          console.warn('代码高亮失败', err, '语言:', normalizedLang)
          // 如果指定语言失败，尝试自动检测
          result = hljs.highlightAuto(code).value
        }
      } else {
        // 自动检测语言
        const autoResult = hljs.highlightAuto(code, ['javascript', 'typescript', 'python', 'java', 'go', 'rust', 'cpp', 'c', 'csharp', 'php', 'ruby', 'swift', 'kotlin', 'sql', 'html', 'css', 'json', 'xml', 'yaml', 'bash', 'shell'])
        result = autoResult.value
      }
      
      return result
    } catch (err) {
      console.error('代码高亮异常', err)
      try {
        return hljs.highlightAuto(code).value
      } catch (fallbackErr) {
        console.error('代码高亮降级失败', fallbackErr)
        return `<code class="hljs">${code}</code>`
      }
    }
  },
  breaks: true, // 支持换行
  gfm: true, // 启用 GitHub Flavored Markdown
  headerIds: false, // 禁用自动生成 header IDs
  mangle: false // 禁用邮箱地址混淆
})

export default {
  name: 'AdminChatHistory',
  components: {
    Search,
    User,
    Service
  },
  setup() {
    const loading = ref(false)
    const conversations = ref([])
    const searchKeyword = ref('')
    const selectedType = ref(null)
    const selectedIds = ref([])
    const currentPage = ref(1)
    const pageSize = ref(15)
    const total = ref(0)
    const showConversationDetail = ref(false)
    const conversationDetail = ref({
      conversation: null,
      messages: []
    })

    // 加载会话列表
    const loadConversations = async () => {
      loading.value = true
      try {
        const response = await getAllConversations(
          currentPage.value,
          pageSize.value,
          searchKeyword.value,
          selectedType.value
        )
        console.log('API响应数据:', response)
        conversations.value = response.content || []
        total.value = response.total || 0
        console.log('分页信息 - 当前页:', currentPage.value, '每页大小:', pageSize.value, '总数:', total.value, '当前数据量:', conversations.value.length)
      } catch (error) {
        ElMessage.error('加载会话列表失败')
        console.error('加载会话列表错误:', error)
      } finally {
        loading.value = false
      }
    }

    // 搜索
    const handleSearch = () => {
      currentPage.value = 1
      loadConversations()
    }

    // 重置
    const handleReset = () => {
      searchKeyword.value = ''
      selectedType.value = null
      currentPage.value = 1
      loadConversations()
    }

    // 选择变化
    const handleSelectionChange = (selection) => {
      selectedIds.value = selection.map(item => item.id)
    }

    // 查看会话详情（显示该会话中的所有消息）
    const handleView = async (row) => {
      try {
        const messagesResponse = await getConversationMessages(row.id)
        showConversationDetail.value = true
        conversationDetail.value = {
          conversation: row,
          messages: messagesResponse || []
        }
      } catch (error) {
        ElMessage.error('加载会话消息失败')
        console.error(error)
      }
    }

    // 删除会话
    const handleDelete = async (row) => {
      try {
        await ElMessageBox.confirm('确定要删除这个会话吗？删除后将无法恢复该会话中的所有对话记录。', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        await deleteConversation(row.id)
        ElMessage.success('删除成功')
        loadConversations()
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('删除失败')
          console.error(error)
        }
      }
    }

    // 批量删除会话
    const handleBatchDelete = async () => {
      try {
        await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 个会话吗？删除后将无法恢复这些会话中的所有对话记录。`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        await batchDeleteConversations(selectedIds.value)
        ElMessage.success('批量删除成功')
        selectedIds.value = []
        loadConversations()
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('批量删除失败')
          console.error(error)
        }
      }
    }

    // 分页
    const handleSizeChange = (size) => {
      pageSize.value = size
      currentPage.value = 1
      loadConversations()
    }

    const handlePageChange = (page) => {
      currentPage.value = page
      loadConversations()
    }

    // 格式化时间
    const formatDateTime = (time) => {
      if (!time) return ''
      return new Date(time).toLocaleString('zh-CN')
    }

    // 格式化消息内容（支持Markdown）
    const formatMessageContent = (content) => {
      if (!content) return ''
      
      try {
        // 先预处理公式，在 Markdown 渲染之前标记公式
        const formulaMatches = []
        
        // 标记块级公式 $$...$$
        let processedContent = content.replace(/\$\$([\s\S]*?)\$\$/g, (match, formula) => {
          const index = formulaMatches.length
          formulaMatches.push({ type: 'block', original: match, formula: formula.trim() })
          return `<!--KATEX_FORMULA_${index}-->`
        })
        
        // 标记块级公式 [...]（独立成行）
        processedContent = processedContent.replace(/(?:^|\n)\s*\[([\s\S]*?)\]\s*(?:\n|$)/g, (match, formula) => {
          // 检查是否包含 LaTeX 命令
          if (/\\[a-zA-Z]+/.test(formula)) {
            const index = formulaMatches.length
            formulaMatches.push({ type: 'block', original: match.trim(), formula: formula.trim() })
            return `\n<!--KATEX_FORMULA_${index}-->\n`
          }
          return match
        })
        
        // 标记行内公式 $...$
        processedContent = processedContent.replace(/\$([^$\n]+?)\$/g, (match, formula) => {
          // 如果已经被处理过，跳过
          if (match.includes('<!--KATEX_FORMULA_')) return match
          const index = formulaMatches.length
          formulaMatches.push({ type: 'inline', original: match, formula: formula.trim() })
          return `<!--KATEX_FORMULA_${index}-->`
        })
        
        // 标记行内的 [ ... ] 格式公式
        processedContent = processedContent.replace(/\[([\s\S]*?)\]/g, (match, formula) => {
          // 如果已经被处理过，跳过
          if (match.includes('<!--KATEX_FORMULA_')) return match
          // 检查是否包含 LaTeX 命令
          if (/\\[a-zA-Z]+/.test(formula)) {
            const index = formulaMatches.length
            formulaMatches.push({ type: 'block', original: match, formula: formula.trim() })
            return `<!--KATEX_FORMULA_${index}-->`
          }
          return match
        })
        
        // 渲染 Markdown（包含代码高亮）
        let html = marked.parse(processedContent)
        
        // 检查是否有公式占位符被误识别为代码块，如果是则恢复
        html = html.replace(/<pre><code[^>]*>([\s\S]*?)<!--KATEX_FORMULA_(\d+)-->([\s\S]*?)<\/code><\/pre>/g, (match, before, index, after) => {
          // 如果代码块中包含公式占位符，说明公式被误识别为代码块
          // 移除代码块标签，保留占位符
          return (before || '') + `<!--KATEX_FORMULA_${index}-->` + (after || '')
        })
        
        // 确保代码块有正确的类名（hljs）
        html = html.replace(/<pre><code(?!\s+class)/g, '<pre><code class="hljs"')
        html = html.replace(/<pre><code class="language-(\w+)(?!.*hljs)"/g, '<pre><code class="hljs language-$1"')
        html = html.replace(/<pre><code class="([^"]*)"(?!.*hljs)/g, (match, classes) => {
          if (classes && !classes.includes('hljs')) {
            return `<pre><code class="hljs ${classes}"`
          }
          return match
        })
        
        // 恢复并渲染公式（使用 HTML 注释占位符）
        formulaMatches.forEach((formulaMatch, index) => {
          const placeholder = `<!--KATEX_FORMULA_${index}-->`
          // 使用全局替换，确保所有匹配都被替换
          const placeholderRegex = new RegExp(placeholder.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')
          
          try {
            if (formulaMatch.formula) {
              const rendered = katex.renderToString(formulaMatch.formula, {
                displayMode: formulaMatch.type === 'block',
                throwOnError: false
              })
              // 如果是块级公式，包装在 div 中以便正确显示
              const finalRendered = formulaMatch.type === 'block' 
                ? `<div class="katex-formula-block">${rendered}</div>` 
                : rendered
              html = html.replace(placeholderRegex, finalRendered)
            } else {
              html = html.replace(placeholderRegex, formulaMatch.original)
            }
          } catch (e) {
            console.warn('KaTeX 渲染失败:', e, '公式:', formulaMatch.formula)
            html = html.replace(placeholderRegex, formulaMatch.original)
          }
        })
        
        // 处理可能遗留的占位符
        html = html.replace(/<!--KATEX_FORMULA_(\d+)-->/g, (match, indexStr) => {
          const index = parseInt(indexStr)
          if (formulaMatches[index]) {
            const formulaMatch = formulaMatches[index]
            try {
              if (formulaMatch.formula) {
                const rendered = katex.renderToString(formulaMatch.formula, {
                  displayMode: formulaMatch.type === 'block',
                  throwOnError: false
                })
                const finalRendered = formulaMatch.type === 'block' 
                  ? `<div class="katex-formula-block">${rendered}</div>` 
                  : rendered
                return finalRendered
              }
            } catch (e) {
              console.warn('渲染遗留占位符公式失败:', e, '公式:', formulaMatch.formula)
            }
          }
          console.warn('发现未处理的公式占位符，但没有对应的公式内容:', match)
          return ''
        })
        
        return html
      } catch (error) {
        console.error('Markdown渲染失败', error)
        return content
      }
    }

    onMounted(() => {
      loadConversations()
    })

    return {
      loading,
      conversations,
      searchKeyword,
      selectedType,
      selectedIds,
      currentPage,
      pageSize,
      total,
      handleSearch,
      handleReset,
      handleSelectionChange,
      handleView,
      handleDelete,
      handleBatchDelete,
      handleSizeChange,
      handlePageChange,
      formatDateTime,
      formatMessageContent,
      showConversationDetail,
      conversationDetail
    }
  }
}
</script>

<style scoped>
.admin-chat-history {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
}

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: 18px 20px;
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 20px;
  min-height: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.batch-actions {
  margin-bottom: 20px;
  flex-shrink: 0;
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  padding: 0;
  flex-shrink: 0;
}

:deep(.el-table .el-table__cell) {
  padding: 7px 0;
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
  background-color: #f4f4f4;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
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
  color: inherit;
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

