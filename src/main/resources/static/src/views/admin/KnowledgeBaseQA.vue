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
              <div
                v-for="kb in displayedKnowledgeBases"
                :key="kb.id"
                :class="['kb-item', { active: selectedKB?.id === kb.id }]"
                @click="selectKB(kb)"
              >
                <el-icon class="kb-icon"><Document /></el-icon>
                <div class="kb-info">
                  <div class="kb-name">{{ kb.name }}</div>
                  <div class="kb-docs">{{ kb.documentCount }} 个文档</div>
                </div>
                <el-tag
                  v-if="kb.status === 'active'"
                  type="success"
                  size="small"
                  class="kb-status"
                >
                  启用
                </el-tag>
              </div>
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
            </div>

            <!-- 对话历史 -->
            <div class="chat-history" ref="chatHistoryRef">
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

            <!-- 输入区域 -->
            <div class="input-area">
              <el-input
                v-model="question"
                type="textarea"
                :rows="3"
                placeholder="请输入您的问题..."
                @keydown.ctrl.enter="handleSend"
                @keydown.enter.exact.prevent="handleSend"
                :disabled="!selectedKB || sending"
              />
              <div class="input-actions">
                <div class="input-tips">
                  <el-checkbox v-model="useStream" size="small">流式响应</el-checkbox>
                  <span class="tips-text">按 Ctrl + Enter 或 Enter 发送</span>
                </div>
                <el-button
                  type="primary"
                  :disabled="!question.trim() || !selectedKB || sending"
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
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
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
  Search
} from '@element-plus/icons-vue'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { knowledgeBaseQA, knowledgeBaseQAStream } from '@/api/knowledgeBaseQA'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import katex from 'katex'
import 'katex/dist/katex.min.css'

// 配置 marked（在组件初始化时配置一次即可）
marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (err) {
        console.warn('代码高亮失败', err)
      }
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true, // 支持换行
  gfm: true, // 启用 GitHub Flavored Markdown
  headerIds: false, // 禁用自动生成 header IDs
  mangle: false // 禁用邮箱地址混淆
})

// 知识库数据
const knowledgeBases = ref([])
const selectedKB = ref(null)
const question = ref('')
const sending = ref(false)
const chatHistory = ref([])
const chatHistoryRef = ref(null)
const conversationId = ref(null)
const useStream = ref(true) // 默认使用流式响应
const currentStreamingMessage = ref(null) // 当前正在流式接收的消息索引
const kbSearchKeyword = ref('') // 知识库搜索关键词
const displayedCount = ref(50) // 初始显示数量
const loadingMore = ref(false) // 加载更多状态
const searchDebounceTimer = ref(null) // 防抖定时器

// 过滤知识库列表
const filteredKnowledgeBases = computed(() => {
  if (!kbSearchKeyword.value) {
    return knowledgeBases.value
  }
  
  const keyword = kbSearchKeyword.value.toLowerCase()
  return knowledgeBases.value.filter(kb => 
    (kb.name && kb.name.toLowerCase().includes(keyword)) ||
    (kb.description && kb.description.toLowerCase().includes(keyword))
  )
})

// 显示的知识库列表（限制数量）
const displayedKnowledgeBases = computed(() => {
  return filteredKnowledgeBases.value.slice(0, displayedCount.value)
})

// 是否还有更多可加载
const hasMoreToLoad = computed(() => {
  return displayedCount.value < filteredKnowledgeBases.value.length
})

// 剩余数量
const remainingCount = computed(() => {
  return filteredKnowledgeBases.value.length - displayedCount.value
})

// 防抖搜索
const handleKbSearch = () => {
  // 重置显示数量
  displayedCount.value = 50
  
  // 清除之前的定时器
  if (searchDebounceTimer.value) {
    clearTimeout(searchDebounceTimer.value)
  }
  
  // 设置新的定时器（300ms 防抖）
  searchDebounceTimer.value = setTimeout(() => {
    // 搜索时自动展开所有结果
    if (kbSearchKeyword.value) {
      displayedCount.value = filteredKnowledgeBases.value.length
    } else {
      displayedCount.value = 50
    }
  }, 300)
}

// 加载更多
const loadMore = () => {
  loadingMore.value = true
  // 模拟加载延迟，实际可以异步加载
  setTimeout(() => {
    displayedCount.value = Math.min(
      displayedCount.value + 50,
      filteredKnowledgeBases.value.length
    )
    loadingMore.value = false
  }, 300)
}

const selectKB = (kb) => {
  selectedKB.value = kb
  // 切换知识库时，清空对话历史
  chatHistory.value = []
  conversationId.value = null
  scrollToBottom()
}

// 加载知识库列表
const loadKnowledgeBases = async () => {
  try {
    // 从localStorage获取用户信息
    const userInfoStr = localStorage.getItem('userInfo')
    let userId = null
    let userRole = null
    if (userInfoStr) {
      try {
        const userInfo = JSON.parse(userInfoStr)
        userId = userInfo.userId
        userRole = userInfo.role
      } catch (e) {
        console.warn('解析用户信息失败', e)
      }
    }
    
    // 管理员可以看到所有知识库（包括禁用的），普通用户只能看到启用的
    const params = {}
    if (userRole !== 1) {
      // 普通用户只获取启用的知识库
      params.status = 1
    }
    // 管理员不传status参数，可以获取所有状态的知识库
    
    if (userId) {
      params.userId = userId
    }
    
    const res = await getKnowledgeBaseList(params)
    // 后端直接返回数组，request拦截器已经提取了response.data
    const data = Array.isArray(res) ? res : (res?.data || [])
    
    if (data && data.length > 0) {
      knowledgeBases.value = data.map(kb => ({
        id: kb.id,
        name: kb.name,
        description: kb.description || '',
        documentCount: kb.documentCount || 0,
        status: kb.status === 1 ? 'active' : 'inactive'
      }))
      
      // 重置显示数量
      displayedCount.value = 50
      
      // 默认选择第一个知识库
      if (knowledgeBases.value.length > 0) {
        selectKB(knowledgeBases.value[0])
      }
    } else {
      ElMessage.info('暂无可用知识库，请先创建知识库')
    }
  } catch (error) {
    console.error('加载知识库列表失败', error)
    ElMessage.error('加载知识库列表失败：' + (error.message || '未知错误'))
  }
}

const handleSend = async () => {
  if (!question.value.trim() || !selectedKB.value || sending.value) {
    return
  }

  const userQuestion = question.value.trim()
  
  // 添加用户消息
  chatHistory.value.push({
    type: 'user',
    content: userQuestion,
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  })

  // 清空输入框
  question.value = ''
  sending.value = true

  // 滚动到底部
  await nextTick()
  scrollToBottom()

  try {
    // 构建对话历史
    const history = chatHistory.value.slice(0, -1).map(msg => ({
      role: msg.type === 'user' ? 'user' : 'assistant',
      content: msg.content
    }))

    if (useStream.value) {
      // 流式响应
      await handleStreamResponse(userQuestion, history)
    } else {
      // 非流式响应
      await handleNormalResponse(userQuestion, history)
    }
  } catch (error) {
    console.error('问答失败', error)
    ElMessage.error('问答失败：' + (error.message || '未知错误'))
    
    // 添加错误消息
    chatHistory.value.push({
      type: 'assistant',
      content: '抱歉，问答服务暂时不可用，请稍后重试。',
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    })
  } finally {
    sending.value = false
    currentStreamingMessage.value = null
    scrollToBottom()
  }
}

// 处理非流式响应
const handleNormalResponse = async (userQuestion, history) => {
  const res = await knowledgeBaseQA(
    selectedKB.value.id,
    userQuestion,
    conversationId.value,
    null,
    history
  )

  if (res && res.data) {
    // 更新对话ID
    if (res.data.conversationId) {
      conversationId.value = res.data.conversationId
    }

    // 添加AI回复
    chatHistory.value.push({
      type: 'assistant',
      content: res.data.answer || '抱歉，无法生成答案。',
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      sources: res.data.sources || []
    })
  } else {
    throw new Error('API返回数据格式错误')
  }
}

// 处理流式响应
const handleStreamResponse = async (userQuestion, history) => {
  // 创建占位的AI消息（显示加载状态）
  const aiMessageIndex = chatHistory.value.length
  chatHistory.value.push({
    type: 'assistant',
    content: '',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    sources: [],
    isLoading: true // 标记为加载中
  })
  currentStreamingMessage.value = aiMessageIndex
  
  // 滚动到底部显示加载提示
  await nextTick()
  scrollToBottom()

  let fullAnswer = ''
  let sources = []
  let finalConversationId = conversationId.value

  try {
    const response = await knowledgeBaseQAStream(
      selectedKB.value.id,
      userQuestion,
      conversationId.value,
      null,
      history
    )

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      
      // 处理SSE格式：每行以data:开头，空行分隔
      // 保留最后一行（可能不完整）到buffer中
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // 保留最后一行（可能不完整）

      for (const line of lines) {
        const trimmedLine = line.trim()
        
        // 跳过空行
        if (!trimmedLine) continue
        
        // 处理SSE数据行：data: {...} 或 data:{...}
        if (trimmedLine.startsWith('data:')) {
          const data = trimmedLine.slice(5).trim()
          
          // 跳过结束标记和空数据
          if (data === '[DONE]' || data === '') continue

          try {
            const json = JSON.parse(data)
            
            // 更新答案内容（后端已经累积，直接使用）
            if (json.answer !== undefined && json.answer !== null) {
              // 后端使用scan操作符累积答案，所以每次返回的都是完整的累积答案
              fullAnswer = json.answer
              // 清除加载状态，显示实际内容
              if (chatHistory.value[aiMessageIndex].isLoading) {
                chatHistory.value[aiMessageIndex].isLoading = false
              }
              chatHistory.value[aiMessageIndex].content = fullAnswer
              
              // 实时滚动
              await nextTick()
              scrollToBottom()
            }

            // 更新来源文档（通常在最后一条消息中）
            if (json.sources && json.sources.length > 0) {
              sources = json.sources
              chatHistory.value[aiMessageIndex].sources = sources
            }

            // 更新对话ID
            if (json.conversationId) {
              finalConversationId = json.conversationId
            }

            // 流式响应完成
            if (json.finished) {
              chatHistory.value[aiMessageIndex].content = fullAnswer || json.answer || ''
              chatHistory.value[aiMessageIndex].sources = sources
              if (finalConversationId) {
                conversationId.value = finalConversationId
              }
              break
            }
          } catch (e) {
            console.warn('解析流式数据失败', e, data)
          }
        }
        // 其他SSE行（如event:、id:等）可以忽略
      }
    }
  } catch (error) {
    console.error('流式响应失败', error)
    chatHistory.value[aiMessageIndex].content = fullAnswer || '抱歉，生成答案时发生错误。'
    throw error
  }
}

// 渲染数学公式（支持行内公式 $...$ 和块级公式 $$...$$）
const renderMath = (html) => {
  if (!html) return ''
  
  try {
    // 先标记块级公式 $$...$$，避免被行内公式匹配
    const blockPlaceholder = '___KATEX_BLOCK_PLACEHOLDER___'
    const blockMatches = []
    html = html.replace(/\$\$([\s\S]*?)\$\$/g, (match, formula) => {
      blockMatches.push({ original: match, formula: formula.trim() })
      return blockPlaceholder + (blockMatches.length - 1) + blockPlaceholder
    })
    
    // 处理行内公式 $...$（不包含换行符，支持方括号等特殊字符）
    html = html.replace(/\$([^$\n]+?)\$/g, (match, formula) => {
      try {
        const trimmed = formula.trim()
        if (!trimmed) return match
        return katex.renderToString(trimmed, {
          displayMode: false,
          throwOnError: false
        })
      } catch (e) {
        console.warn('KaTeX 渲染失败（行内）:', e, '公式:', formula)
        return match
      }
    })
    
    // 恢复并渲染块级公式
    html = html.replace(new RegExp(blockPlaceholder + '(\\d+)' + blockPlaceholder, 'g'), (match, index) => {
      const blockMatch = blockMatches[parseInt(index)]
      if (!blockMatch) return match
      
      try {
        if (!blockMatch.formula) return blockMatch.original
        return katex.renderToString(blockMatch.formula, {
          displayMode: true,
          throwOnError: false
        })
      } catch (e) {
        console.warn('KaTeX 渲染失败（块级）:', e, '公式:', blockMatch.formula)
        return blockMatch.original
      }
    })
    
    return html
  } catch (error) {
    console.error('数学公式渲染失败', error)
    return html
  }
}

// 渲染Markdown
const renderMarkdown = (content) => {
  if (!content) return ''
  
  try {
    // 先渲染 Markdown
    let html = marked.parse(content)
    // 再渲染数学公式
    html = renderMath(html)
    return html
  } catch (error) {
    console.error('Markdown渲染失败', error)
    return content
  }
}

const handleClearHistory = () => {
  if (chatHistory.value.length === 0) {
    ElMessage.info('当前没有对话历史')
    return
  }
  
  chatHistory.value = []
  ElMessage.success('已清空对话历史')
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatHistoryRef.value) {
      chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
    }
  })
}

onMounted(() => {
  // 加载知识库列表
  loadKnowledgeBases()
})

// 组件卸载时清理定时器
onUnmounted(() => {
  if (searchDebounceTimer.value) {
    clearTimeout(searchDebounceTimer.value)
  }
})
</script>

<style>
/* 防止浏览器滚动 - 全局样式 */
body {
  overflow: hidden !important;
  height: 100vh !important;
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
  margin-bottom: 16px;
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
  padding: 12px;
  background: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid transparent;
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
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-docs {
  font-size: 12px;
  color: #909399;
}

.kb-status {
  flex-shrink: 0;
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
  padding: 20px;
  padding-bottom: 200px; /* 为输入框留出空间，确保最后一条消息不被遮挡 */
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 0;
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
  animation: fadeIn 0.3s;
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
  color: #606266;
}

.message-content {
  width: 70%;
  max-width: 70%;
  min-width: 70%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message-item.user .message-content {
  align-items: flex-end;
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
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
}

.message-item.assistant .message-text :deep(code) {
  background: rgba(0, 0, 0, 0.1);
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

.message-time {
  font-size: 12px;
  color: #909399;
  padding: 0 4px;
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
</style>

