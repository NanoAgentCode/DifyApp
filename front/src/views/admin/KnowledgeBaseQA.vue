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
                  <span>该知识库使用的向量化模型"{{ getEmbeddingModelName(selectedKB.embeddingModelId) || '默认向量化模型' }}"已被禁用，无法进行问答。请在管理端大模型管理页面启用该向量化模型。</span>
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
                  <span>该知识库使用的向量库类型"{{ getVectorStoreTypeName(selectedKB.vectorStoreType) }}"已被禁用，无法进行问答。请在向量库管理中启用该类型的向量库配置。</span>
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
                  <span>该知识库已被禁用，无法进行问答。请在知识库管理中启用该知识库。</span>
                </template>
              </el-alert>
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
                :disabled="!selectedKB || sending || !isKnowledgeBaseActive(selectedKB?.status) || !isEmbeddingModelEnabled(selectedKB?.embeddingModelId) || !isVectorStoreTypeEnabled(selectedKB?.vectorStoreType)"
              />
              <div class="input-actions">
                <div class="input-tips">
                  <el-checkbox v-model="useStream" size="small">流式响应</el-checkbox>
                  <span class="tips-text">按 Ctrl + Enter 或 Enter 发送</span>
                </div>
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
  Search,
  Warning
} from '@element-plus/icons-vue'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { knowledgeBaseQA, knowledgeBaseQAStream } from '@/api/knowledgeBaseQA'
import { getModelConfig, getAvailableQAModelsForRAG } from '@/api/model'
import { getModelStyle } from '@/utils/modelColor'
import { getVectorDatabaseList } from '@/api/vectorDatabase'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import katex from 'katex'
import 'katex/dist/katex.min.css'

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
        return code
      }
    }
  },
  breaks: true, // 支持换行
  gfm: true, // 启用 GitHub Flavored Markdown
  headerIds: false, // 禁用自动生成 header IDs
  mangle: false // 禁用邮箱地址混淆
})

// 知识库数据
const knowledgeBases = ref([])
const selectedKB = ref(null)
const embeddingModels = ref([])
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
const availableQAModels = ref([]) // 可用的问答模型列表
const selectedModelId = ref(null) // 选中的问答模型ID
const vectorDatabases = ref([]) // 向量库配置列表
const enabledVectorStoreTypes = ref([]) // 启用的向量库类型列表

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
  scrollToBottom(true) // 切换知识库后强制滚动到底部
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
        summary: kb.summary || '',
        documentCount: kb.documentCount || 0,
        status: kb.status === 1 ? 'active' : 'inactive',
        embeddingModelId: kb.embeddingModelId || null,
        vectorStoreType: kb.vectorStoreType || 'qdrant'
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

  // 检查向量化模型是否启用
  if (!isEmbeddingModelEnabled(selectedKB.value.embeddingModelId)) {
    const modelName = getEmbeddingModelName(selectedKB.value.embeddingModelId) || '默认向量化模型'
    ElMessage.warning(`该知识库使用的向量化模型"${modelName}"已被禁用，无法进行问答。请在管理端大模型管理页面启用该向量化模型。`)
    return
  }

  // 检查向量库类型是否启用
  if (!isVectorStoreTypeEnabled(selectedKB.value.vectorStoreType)) {
    const vectorStoreName = getVectorStoreTypeName(selectedKB.value.vectorStoreType)
    ElMessage.warning(`该知识库使用的向量库类型"${vectorStoreName}"已被禁用，无法进行问答。请在向量库管理中启用该类型的向量库配置。`)
    return
  }

  // 检查知识库是否启用
  if (!isKnowledgeBaseActive(selectedKB.value.status)) {
    ElMessage.warning('该知识库已被禁用，无法进行问答。请在知识库管理中启用该知识库。')
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

  // 滚动到底部（强制滚动，因为这是新消息）
  await nextTick()
  scrollToBottom(true)

  try {
    // 构建对话历史（限制最近10轮对话，避免token过多）
    const maxHistoryRounds = 10 // 最多保留10轮对话（20条消息）
    const historyMessages = chatHistory.value.slice(0, -1)
    const limitedHistory = historyMessages.slice(-maxHistoryRounds * 2).map(msg => ({
      role: msg.type === 'user' ? 'user' : 'assistant',
      content: msg.content
    }))
    
    // 确保历史记录格式正确（user和assistant交替）
    const history = []
    for (let i = 0; i < limitedHistory.length; i++) {
      const msg = limitedHistory[i]
      if (msg.role && msg.content) {
        history.push(msg)
      }
    }

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
    // 流式响应完成后，只在用户还在底部时滚动
    scrollToBottom(false)
  }
}

// 处理非流式响应
const handleNormalResponse = async (userQuestion, history) => {
  // 确保历史记录不为空时才传递
  const historyToSend = history && history.length > 0 ? history : null
  
  const res = await knowledgeBaseQA(
    selectedKB.value.id,
    userQuestion,
    conversationId.value,
    null,
    historyToSend,
    selectedModelId.value
  )

  if (res && res.data) {
    // 更新对话ID
    if (res.data.conversationId) {
      conversationId.value = res.data.conversationId
      console.log('非流式响应完成，更新 conversationId:', conversationId.value)
    }

    // 添加AI回复
    chatHistory.value.push({
      type: 'assistant',
      content: res.data.answer || '抱歉，无法生成答案。',
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      sources: res.data.sources || []
    })
    
    // 触发代码高亮
    await nextTick()
    highlightCodeBlocks()
    // 非流式响应完成后，只在用户还在底部时滚动
    scrollToBottom(false)
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
  
  // 滚动到底部显示加载提示（强制滚动，因为这是新消息）
  await nextTick()
  scrollToBottom(true)

  let fullAnswer = ''
  let sources = []
  let finalConversationId = conversationId.value

  try {
    // 确保历史记录不为空时才传递
    const historyToSend = history && history.length > 0 ? history : null
    
    const response = await knowledgeBaseQAStream(
      selectedKB.value.id,
      userQuestion,
      conversationId.value,
      null,
      historyToSend,
      selectedModelId.value
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
                // 检查消息对象是否存在
                if (!chatHistory.value[aiMessageIndex]) {
                  console.warn('消息对象不存在，索引:', aiMessageIndex, '数组长度:', chatHistory.value.length)
                  continue
                }
                // 后端使用scan操作符累积答案，所以每次返回的都是完整的累积答案
                fullAnswer = json.answer
                // 清除加载状态，显示实际内容
                if (chatHistory.value[aiMessageIndex].isLoading) {
                  chatHistory.value[aiMessageIndex].isLoading = false
                }
                chatHistory.value[aiMessageIndex].content = fullAnswer
                
                // 实时滚动（只在用户还在底部时滚动）
                await nextTick()
                // 手动触发代码高亮（确保流式响应中的代码块被正确高亮）
                highlightCodeBlocks()
                scrollToBottom(false) // 不强制滚动，如果用户向上滚动了就不滚动
              }

            // 更新来源文档（通常在最后一条消息中）
            if (json.sources && json.sources.length > 0) {
              sources = json.sources
              chatHistory.value[aiMessageIndex].sources = sources
            }

            // 更新对话ID（如果响应中包含 conversationId，立即更新，不等待 finished）
            if (json.conversationId) {
              conversationId.value = json.conversationId
              finalConversationId = json.conversationId
              console.log('流式响应中更新 conversationId:', conversationId.value)
            }

            // 流式响应完成
            if (json.finished) {
              // 检查消息对象是否存在
              if (chatHistory.value[aiMessageIndex]) {
                chatHistory.value[aiMessageIndex].content = fullAnswer || json.answer || ''
                chatHistory.value[aiMessageIndex].sources = sources
              }
              // 确保会话ID已更新（如果之前没有更新）
              if (json.conversationId) {
                conversationId.value = json.conversationId
                console.log('流式响应完成，最终 conversationId:', conversationId.value)
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

// 渲染数学公式（支持行内公式 $...$、块级公式 $$...$$ 和 [ ... ] 格式）
const renderMath = (html) => {
  if (!html) return ''
  
  try {
    // 先标记块级公式，避免被行内公式匹配
    const blockPlaceholder = '___KATEX_BLOCK_PLACEHOLDER___'
    const blockMatches = []
    
    // 处理块级公式 $$...$$
    html = html.replace(/\$\$([\s\S]*?)\$\$/g, (match, formula) => {
      blockMatches.push({ original: match, formula: formula.trim() })
      return blockPlaceholder + (blockMatches.length - 1) + blockPlaceholder
    })
    
    // 处理块级公式 [...]
    // 使用更智能的匹配：找到所有 [ ... ] 对，检查是否包含 LaTeX 命令
    // 由于公式可能包含嵌套括号，我们需要更仔细地匹配
    
    // 先处理独立成行的公式（前后有换行）
    html = html.replace(/(?:^|\n)\s*\[([\s\S]*?)\]\s*(?:\n|$)/g, (match, formula) => {
      // 检查是否包含 LaTeX 命令
      if (/\\[a-zA-Z]+/.test(formula)) {
        blockMatches.push({ original: match.trim(), formula: formula.trim() })
        return '\n' + blockPlaceholder + (blockMatches.length - 1) + blockPlaceholder + '\n'
      }
      return match
    })
    
    // 处理行内的 [ ... ] 格式（如果包含 LaTeX 命令且未被处理）
    html = html.replace(/\[([\s\S]*?)\]/g, (match, formula) => {
      // 如果已经被处理过（包含占位符），跳过
      if (match.includes(blockPlaceholder)) return match
      // 检查是否包含 LaTeX 命令
      if (/\\[a-zA-Z]+/.test(formula)) {
        blockMatches.push({ original: match, formula: formula.trim() })
        return blockPlaceholder + (blockMatches.length - 1) + blockPlaceholder
      }
      return match
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
    // 先预处理公式，在 Markdown 渲染之前标记公式
    // 使用 HTML 注释作为占位符，这样不会被 Markdown 解析器处理
    const formulaMatches = []
    
    // 标记块级公式 $$...$$
    content = content.replace(/\$\$([\s\S]*?)\$\$/g, (match, formula) => {
      const index = formulaMatches.length
      formulaMatches.push({ type: 'block', original: match, formula: formula.trim() })
      return `<!--KATEX_FORMULA_${index}-->`
    })
    
    // 标记块级公式 [...]（独立成行）
    content = content.replace(/(?:^|\n)\s*\[([\s\S]*?)\]\s*(?:\n|$)/g, (match, formula) => {
      // 检查是否包含 LaTeX 命令
      if (/\\[a-zA-Z]+/.test(formula)) {
        const index = formulaMatches.length
        formulaMatches.push({ type: 'block', original: match.trim(), formula: formula.trim() })
        return `\n<!--KATEX_FORMULA_${index}-->\n`
      }
      return match
    })
    
    // 标记行内公式 $...$
    content = content.replace(/\$([^$\n]+?)\$/g, (match, formula) => {
      // 如果已经被处理过，跳过
      if (match.includes('<!--KATEX_FORMULA_')) return match
      const index = formulaMatches.length
      formulaMatches.push({ type: 'inline', original: match, formula: formula.trim() })
      return `<!--KATEX_FORMULA_${index}-->`
    })
    
    // 标记行内的 [ ... ] 格式公式
    content = content.replace(/\[([\s\S]*?)\]/g, (match, formula) => {
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
    let html = marked.parse(content)
    
    // 检查是否有公式占位符被误识别为代码块，如果是则恢复
    // 这种情况可能发生在公式格式被 Markdown 解析器识别为代码块时
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
    
    // 处理可能遗留的占位符（如果公式没有被识别，但占位符已经存在）
    // 这种情况可能发生在流式响应中，公式内容还没有完全接收，或者后端直接返回了占位符
    // 先尝试从占位符中提取索引，看看是否有对应的公式
    html = html.replace(/<!--KATEX_FORMULA_(\d+)-->/g, (match, indexStr) => {
      const index = parseInt(indexStr)
      // 检查是否有对应的公式
      if (formulaMatches[index]) {
        // 如果有对应的公式，尝试渲染它
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
      // 如果没有对应的公式，返回空字符串，避免显示占位符
      console.warn('发现未处理的公式占位符，但没有对应的公式内容:', match)
      return ''
    })
    
    return html
  } catch (error) {
    console.error('Markdown渲染失败', error)
    return content
  }
}

// 手动触发代码高亮（用于流式响应中逐步生成的代码块）
const highlightCodeBlocks = () => {
  nextTick(() => {
    if (chatHistoryRef.value) {
      // 查找所有代码块（包括没有hljs类的）
      const codeBlocks = chatHistoryRef.value.querySelectorAll('pre code')
      
      codeBlocks.forEach((block, index) => {
        try {
          // 检查是否是公式（包含 KaTeX 相关元素或公式占位符）
          const isFormula = block.closest('.katex') || 
                           block.closest('.katex-formula-block') ||
                           block.textContent.includes('KATEX_FORMULA_') ||
                           block.textContent.includes('<!--KATEX_FORMULA_') ||
                           block.parentElement?.classList.contains('katex-formula-block')
          
          if (isFormula) {
            console.debug(`代码块 ${index} 是公式，跳过代码高亮`)
            return
          }
          
          const originalText = block.textContent
          // 如果代码块没有hljs类，或者内容已更新，重新高亮
          const needsHighlight = !block.classList.contains('hljs') || 
                                block.dataset.highlighted !== 'true' ||
                                block.dataset.originalText !== originalText
          
          if (needsHighlight && originalText.trim()) {
            // 获取语言标识符（从class或父元素）
            let lang = block.className.match(/language-(\w+)/)?.[1] || 
                      block.parentElement?.className.match(/language-(\w+)/)?.[1] ||
                      block.getAttribute('data-lang')
            
            // 规范化语言标识符
            const normalizedLang = lang ? normalizeLanguage(lang) : null
            
            let highlightedHtml
            if (normalizedLang && hljs.getLanguage(normalizedLang)) {
              // 使用指定语言高亮
              highlightedHtml = hljs.highlight(originalText, { language: normalizedLang }).value
            } else {
              // 自动检测语言
              const result = hljs.highlightAuto(originalText, ['javascript', 'typescript', 'python', 'java', 'go', 'rust', 'cpp', 'c', 'csharp', 'php', 'ruby', 'swift', 'kotlin', 'sql', 'html', 'css', 'json', 'xml', 'yaml', 'bash', 'shell'])
              highlightedHtml = result.value
            }
            
            // 更新HTML并添加类名
            block.innerHTML = highlightedHtml
            block.classList.add('hljs')
            block.dataset.highlighted = 'true'
            block.dataset.originalText = originalText
          }
        } catch (err) {
          console.warn('手动高亮代码块失败', err, block)
        }
      })
    }
  })
}

const handleClearHistory = () => {
  if (chatHistory.value.length === 0) {
    ElMessage.info('当前没有对话历史')
    return
  }
  
  chatHistory.value = []
  ElMessage.success('已清空对话历史')
}

// 检查是否在底部（允许一定的误差范围）
const isNearBottom = () => {
  if (!chatHistoryRef.value) return true
  const element = chatHistoryRef.value
  const threshold = 100 // 距离底部100px内认为是在底部
  return element.scrollHeight - element.scrollTop - element.clientHeight < threshold
}

const scrollToBottom = (force = false) => {
  nextTick(() => {
    if (chatHistoryRef.value) {
      // 如果用户手动向上滚动了，且不是强制滚动，则不自动滚动
      if (!force && !isNearBottom()) {
        return
      }
      chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
    }
  })
}

onMounted(() => {
  // 加载知识库列表
  loadKnowledgeBases()
  // 加载向量化模型列表
  loadEmbeddingModels()
  // 加载问答模型列表
  loadQAModels()
  // 加载向量库配置列表
  loadVectorDatabases()
})

// 加载向量化模型列表（包括禁用的，用于检查状态）
const loadEmbeddingModels = async () => {
  try {
    const response = await getModelConfig()
    // 加载所有模型（包括禁用的），以便检查状态
    embeddingModels.value = response.embeddingModels || []
  } catch (error) {
    console.error('加载向量化模型列表失败', error)
  }
}

// 辅助函数：根据模型ID获取模型名称
const getEmbeddingModelName = (modelId) => {
  if (modelId) {
    const model = embeddingModels.value.find(m => m.id === modelId)
    return model ? model.name : null
  } else {
    // 如果没有指定模型ID，返回默认模型名称
    const defaultModel = embeddingModels.value.find(m => m.isDefault)
    return defaultModel ? defaultModel.name : null
  }
}

// 检查向量化模型是否启用
const isEmbeddingModelEnabled = (modelId) => {
  if (modelId) {
    const model = embeddingModels.value.find(m => m.id === modelId)
    return model ? model.enabled : false
  } else {
    // 如果没有指定模型ID，检查默认模型是否启用
    const defaultModel = embeddingModels.value.find(m => m.isDefault)
    return defaultModel ? defaultModel.enabled : false
  }
}

// 加载向量库配置列表
const loadVectorDatabases = async () => {
  try {
    const response = await getVectorDatabaseList()
    vectorDatabases.value = response || []
    
    // 计算启用的向量库类型
    const enabledTypes = new Set()
    vectorDatabases.value.forEach(db => {
      if (db.enabled && db.type) {
        enabledTypes.add(db.type.toLowerCase())
      }
    })
    enabledVectorStoreTypes.value = Array.from(enabledTypes)
  } catch (error) {
    console.error('加载向量库配置列表失败', error)
    // 如果加载失败，默认允许所有类型
    enabledVectorStoreTypes.value = ['qdrant', 'faiss', 'milvus', 'chroma', 'weaviate', 'elasticsearch']
  }
}

// 检查向量库类型是否启用
const isVectorStoreTypeEnabled = (type) => {
  if (!type) return true // 如果没有指定类型，默认允许（向后兼容）
  return enabledVectorStoreTypes.value.includes(type.toLowerCase())
}

// 获取向量存储类型名称
const getVectorStoreTypeName = (type) => {
  if (type === 'faiss') return 'FAISS'
  if (type === 'milvus') return 'Milvus'
  if (type === 'chroma') return 'Chroma'
  if (type === 'weaviate') return 'Weaviate'
  return 'Qdrant'
}

// 检查知识库是否启用
const isKnowledgeBaseActive = (status) => {
  if (typeof status === 'number') {
    return status === 1
  }
  return status === 'active'
}

// 获取知识库禁用原因提示
const getKnowledgeBaseDisabledTip = (kb) => {
  if (!kb) return ''
  
  const reasons = []
  
  // 检查知识库状态
  if (!isKnowledgeBaseActive(kb.status)) {
    reasons.push('知识库已被禁用')
  }
  
  // 检查向量化模型
  if (!isEmbeddingModelEnabled(kb.embeddingModelId)) {
    const modelName = getEmbeddingModelName(kb.embeddingModelId) || '默认向量化模型'
    reasons.push(`向量化模型"${modelName}"已被禁用`)
  }
  
  // 检查向量库类型
  if (!isVectorStoreTypeEnabled(kb.vectorStoreType)) {
    const vectorStoreName = getVectorStoreTypeName(kb.vectorStoreType)
    reasons.push(`向量库类型"${vectorStoreName}"已被禁用`)
  }
  
  if (reasons.length > 0) {
    return `无法使用：${reasons.join('、')}。请联系管理员处理。`
  }
  
  return ''
}

// 获取知识库 tooltip 内容（包含智能摘要和禁用原因）
const getKnowledgeBaseTooltipContent = (kb) => {
  if (!kb) return ''
  
  const parts = []
  
  // 如果有智能摘要，优先显示
  if (kb.summary && kb.summary.trim()) {
    // 转义 HTML 特殊字符，防止 XSS
    const escapedSummary = kb.summary
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')
    parts.push(`<div style="margin-bottom: 8px; max-width: 300px; line-height: 1.6; padding: 8px 12px; background: linear-gradient(135deg, #f5f7fa 0%, #e8f4f8 100%); border-left: 3px solid #409eff; border-radius: 4px;"><div style="color: #409eff; font-weight: 600; font-size: 13px; margin-bottom: 6px;">📋 智能摘要</div><div style="color: #606266; font-size: 13px;">${escapedSummary}</div></div>`)
  }
  
  // 如果有禁用原因，也显示
  const disabledTip = getKnowledgeBaseDisabledTip(kb)
  if (disabledTip) {
    // 转义 HTML 特殊字符
    const escapedTip = disabledTip
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')
    parts.push(`<div style="color: #f56c6c; margin-top: 8px; padding: 6px 10px; background-color: #fef0f0; border-left: 3px solid #f56c6c; border-radius: 4px; font-size: 12px;">⚠️ ${escapedTip}</div>`)
  }
  
  // 如果既没有摘要也没有禁用原因，返回空字符串（tooltip 会被禁用）
  if (parts.length === 0) {
    return ''
  }
  
  return parts.join('')
}

// 加载问答模型列表（用于知识库问答）
const loadQAModels = async () => {
  try {
    const response = await getAvailableQAModelsForRAG()
    // request拦截器已经提取了response.data，所以response就是数据本身
    const data = Array.isArray(response) ? response : (response?.data || [])
    availableQAModels.value = data || []
    console.log('加载的问答模型列表:', availableQAModels.value)
    // 如果没有选中模型，默认选择第一个或默认模型
    if (!selectedModelId.value && availableQAModels.value.length > 0) {
      const defaultModel = availableQAModels.value.find(m => m.isDefault)
      selectedModelId.value = defaultModel ? defaultModel.id : availableQAModels.value[0].id
    } else if (availableQAModels.value.length === 0) {
      console.warn('没有可用的RAG问答模型，请先在"大模型管理"页面配置useFor为"rag"或"both"的模型')
      ElMessage.warning('当前没有可用的RAG问答模型，请先在"大模型管理"页面配置模型')
    }
  } catch (error) {
    console.error('加载问答模型列表失败', error)
    const errorMessage = error.response?.data?.error || error.response?.data?.message || error.message || '未知错误'
    ElMessage.error(`加载问答模型列表失败: ${errorMessage}`)
    availableQAModels.value = []
  }
}

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
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 0.5em 0;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.message-item.assistant .message-text :deep(pre) {
  background: #1e1e1e;
  color: #d4d4d4;
}

.message-text :deep(pre code) {
  background: transparent;
  padding: 0;
  color: #d4d4d4;
  /* 重要：对于没有高亮的代码块，设置默认文字颜色以确保可读性 */
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  display: block;
  width: 100%;
}

/* 确保代码高亮正常工作 */
.message-text :deep(pre code.hljs) {
  display: block;
  overflow-x: auto;
  padding: 0;
  background: transparent;
  /* 关键：不设置color，让highlight.js的github-dark主题自己处理所有颜色 */
  /* 如果设置了color，会覆盖highlight.js的关键字颜色 */
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  /* 对于没有高亮的代码块，确保有默认颜色 */
  color: #d4d4d4;
}

/* 对于没有hljs类的代码块，确保文字颜色可见 */
.message-text :deep(pre code:not(.hljs)) {
  color: #d4d4d4;
}

/* 确保highlight.js的样式能够正确应用 */
/* github-dark主题已经包含了所有关键字、字符串、注释等的颜色样式 */
/* 不要在这里覆盖任何颜色相关的样式 */

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

