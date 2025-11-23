<template>
  <div class="chat-history">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>会话历史</span>
          <el-button type="primary" @click="handleCreateConversation">
            <el-icon><Plus /></el-icon>
            新建会话
          </el-button>
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
          <el-option label="知识库问答" :value="2" />
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
                  <el-tag size="small" :type="conv.type === 1 ? 'primary' : 'success'">
                    {{ conv.type === 1 ? '普通聊天' : '知识库问答' }}
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
    <el-dialog v-model="editTitleDialogVisible" title="编辑会话标题" width="400px">
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
    >
      <div v-if="conversationDetail.conversation" class="conversation-detail">
        <div class="detail-header">
          <div class="detail-info">
            <el-tag :type="conversationDetail.conversation.type === 1 ? 'primary' : 'success'" size="small">
              {{ conversationDetail.conversation.type === 1 ? '普通聊天' : '知识库问答' }}
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

<script>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, ChatLineRound, Edit, Delete, User, Service, Right, View, Clock } from '@element-plus/icons-vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import katex from 'katex'
import 'katex/dist/katex.min.css'
import {
  getMyConversations,
  createConversation,
  updateConversationTitle,
  deleteConversation,
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
  name: 'ChatHistory',
  components: {
    Plus,
    Search,
    ChatLineRound,
    Edit,
    Delete,
    User,
    Service,
    Right,
    View,
    Clock
  },
  setup() {
    const router = useRouter()
    const loading = ref(false)
    const conversations = ref([])
    const searchKeyword = ref('')
    const selectedType = ref(null)
    const selectedConversationId = ref(null)
    const currentPage = ref(1)
    const pageSize = ref(20)
    const total = ref(0)
    const editTitleDialogVisible = ref(false)
    const editTitle = ref('')
    const editingConversation = ref(null)
    const showConversationDetail = ref(false)
    const conversationDetail = ref({
      conversation: null,
      messages: []
    })

    // 加载会话列表
    const loadConversations = async () => {
      loading.value = true
      try {
        const response = await getMyConversations(
          currentPage.value,
          pageSize.value,
          searchKeyword.value,
          selectedType.value
        )
        conversations.value = response.content || []
        total.value = response.total || 0
      } catch (error) {
        ElMessage.error('加载会话列表失败')
        console.error(error)
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

    // 选择会话，查看会话详情（显示该会话中的所有消息）
    const selectConversation = async (conv) => {
      selectedConversationId.value = conv.id
      // 加载该会话的消息列表
      try {
        const messagesResponse = await getConversationMessages(conv.id)
        showConversationDetail.value = true
        conversationDetail.value = {
          conversation: conv,
          messages: messagesResponse || []
        }
      } catch (error) {
        ElMessage.error('加载会话消息失败')
        console.error(error)
      }
    }

    // 创建新会话
    const handleCreateConversation = async () => {
      try {
        const response = await createConversation('新会话', null, null, 1)
        ElMessage.success('创建成功')
        loadConversations()
        // 可以选择跳转到新会话
        if (response && response.id) {
          selectConversation(response)
        }
      } catch (error) {
        ElMessage.error('创建会话失败')
        console.error(error)
      }
    }

    // 编辑标题
    const handleEditTitle = (conv) => {
      editingConversation.value = conv
      editTitle.value = conv.title
      editTitleDialogVisible.value = true
    }

    // 保存标题
    const handleSaveTitle = async () => {
      if (!editTitle.value.trim()) {
        ElMessage.warning('标题不能为空')
        return
      }
      try {
        await updateConversationTitle(editingConversation.value.id, editTitle.value)
        ElMessage.success('更新成功')
        editTitleDialogVisible.value = false
        loadConversations()
      } catch (error) {
        ElMessage.error('更新标题失败')
        console.error(error)
      }
    }

    // 删除会话
    const handleDelete = async (conv) => {
      try {
        await ElMessageBox.confirm('确定要删除这个会话吗？删除后将无法恢复该会话中的所有对话记录。', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        await deleteConversation(conv.id)
        ElMessage.success('删除成功')
        loadConversations()
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('删除失败')
          console.error(error)
        }
      }
    }

    // 继续对话
    const handleContinueConversation = (conv) => {
      // 将 conversationId 存储到 localStorage，聊天页面会读取
      localStorage.setItem('continueConversationId', conv.id.toString())
      // 根据会话类型跳转到对应的页面
      if (conv.type === 1) {
        router.push('/user/chat')
      } else {
        router.push('/user/kb-qa')
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
    const formatTime = (time) => {
      if (!time) return ''
      const date = new Date(time)
      const now = new Date()
      const diff = now - date
      const minutes = Math.floor(diff / 60000)
      const hours = Math.floor(diff / 3600000)
      const days = Math.floor(diff / 86400000)

      if (minutes < 1) return '刚刚'
      if (minutes < 60) return `${minutes}分钟前`
      if (hours < 24) return `${hours}小时前`
      if (days < 7) return `${days}天前`
      return date.toLocaleDateString()
    }

    // 格式化日期时间
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
      selectedConversationId,
      currentPage,
      pageSize,
      total,
      editTitleDialogVisible,
      editTitle,
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
      formatMessageContent,
      showConversationDetail,
      conversationDetail
    }
  }
}
</script>

<style scoped>
.chat-history {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-history :deep(.el-card) {
  display: flex;
  flex-direction: column;
  height: 100%;
  flex: 1;
}

.chat-history :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
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
  height: 100%;
  min-height: 120px;
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

