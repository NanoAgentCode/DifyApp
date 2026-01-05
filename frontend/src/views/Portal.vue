<template>
  <div class="portal-container">
    <!-- 顶部导航栏 -->
    <AppHeader v-model="isHeaderCollapsed" @command="handleCommand" />

    <!-- 主要内容区域 -->
    <div class="portal-content" :class="{ 'content-header-collapsed': isHeaderCollapsed, 'no-scroll': isContentOverflow }">
      <!-- 初始欢迎界面（无对话时显示） -->
      <div v-if="chatHistory.length === 0 && !isInputFocused" class="welcome-section">
        <!-- 页面切换标签 -->
        <div class="view-tabs">
          <div 
            class="tab-item" 
            :class="{ active: currentView === 'welcome' }"
            @click="currentView = 'welcome'"
          >
            <el-icon><ChatLineRound /></el-icon>
            <span>智能对话</span>
          </div>
          <div 
            class="tab-item" 
            :class="{ active: currentView === 'features' }"
            @click="currentView = 'features'"
          >
            <el-icon><Grid /></el-icon>
            <span>快捷入口</span>
          </div>
        </div>

        <!-- 欢迎视图 -->
        <transition name="fade-slide" mode="out-in">
          <div v-if="currentView === 'welcome'" key="welcome" class="view-content">
            <div class="assistant-identity">
              <el-icon class="assistant-icon"><Service /></el-icon>
              <span class="assistant-name">NanoAgent</span>
            </div>
            <div class="welcome-message">
              你好！我是NanoAgent，很高兴为你提供帮助。有什么问题或需要协助的地方吗？
              <div class="welcome-tips">
                <span class="tip-item">输入 <span class="tip-symbol">@</span> 选择知识库</span>
                <span class="tip-item">输入 <span class="tip-symbol">/</span> 选择文档</span>
              </div>
            </div>
            <div class="suggested-prompts">
              <div 
                v-for="(conversation, index) in recentConversations" 
                :key="conversation.id"
                class="prompt-item"
              >
                <div class="prompt-item-content" @click="handleConversationClick(conversation)">
                  <span class="prompt-bullet">•</span>
                  <span class="prompt-text">{{ conversation.title || '未命名会话' }}</span>
                </div>
                <el-tooltip content="继续对话" placement="top">
                  <el-button
                    class="prompt-continue-btn"
                    type="primary"
                    text
                    size="small"
                    @click.stop="handleConversationClick(conversation)"
                  >
                    <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
              <div 
                v-if="recentConversations.length === 0"
                class="prompt-item"
                @click="handlePromptClick('最近有什么有趣的事情吗')"
              >
                <span class="prompt-bullet">•</span>
                <span class="prompt-text">最近有什么有趣的事情吗</span>
              </div>
            </div>
          </div>

          <!-- 系统功能视图 -->
          <div v-else-if="currentView === 'features'" key="features" class="view-content">
            <div class="feature-entries">
              <div class="feature-title">
                <el-icon class="feature-title-icon"><Service /></el-icon>
                <span class="feature-title-text">NanoAgent</span>
              </div>
              <div class="feature-grid">
                <div class="feature-item" @click="handleFeatureClick('apps')">
                  <div class="feature-circle">
                    <el-icon class="feature-icon"><Grid /></el-icon>
                  </div>
                  <span class="feature-name">智能应用</span>
                </div>
                <div class="feature-item" @click="handleFeatureClick('kb-qa')">
                  <div class="feature-circle">
                    <el-icon class="feature-icon"><Search /></el-icon>
                  </div>
                  <span class="feature-name">知识检索</span>
                </div>
                <div class="feature-item" @click="handleFeatureClick('knowledge-base')">
                  <div class="feature-circle">
                    <el-icon class="feature-icon"><Folder /></el-icon>
                  </div>
                  <span class="feature-name">知识管理</span>
                </div>
                <div class="feature-item" @click="handleFeatureClick('ai-drawio')">
                  <div class="feature-circle">
                    <el-icon class="feature-icon"><Picture /></el-icon>
                  </div>
                  <span class="feature-name">智能框图</span>
                </div>
                <div class="feature-item" @click="handleFeatureClick('document')">
                  <div class="feature-circle">
                    <el-icon class="feature-icon"><Document /></el-icon>
                  </div>
                  <span class="feature-name">文档解读</span>
                </div>
                <div class="feature-item" @click="handleFeatureClick('chat-history')">
                  <div class="feature-circle">
                    <el-icon class="feature-icon"><Clock /></el-icon>
                  </div>
                  <span class="feature-name">会话历史</span>
                </div>
              </div>
            </div>
          </div>
        </transition>
      </div>

      <!-- 对话历史区域（有对话时显示） -->
      <div 
        v-if="chatHistory.length > 0 || isInputFocused" 
        class="chat-history-section"
        :class="{ 'content-overflow': isContentOverflow }"
        ref="chatHistorySectionRef"
      >
        <MessageList
          :messages="chatHistory"
          :sending="sending"
          :on-regenerate="handleRegenerate"
          ref="messageListRef"
        />
      </div>
    </div>

    <!-- 中央输入区域 -->
    <div class="input-section" :class="{ 'transparent': isContentOverflow }" ref="inputSectionRef">
      <div class="input-wrapper" ref="inputWrapperRef">
        <!-- 输入框容器（标签作为输入内容的一部分，不单独占行或列） -->
        <div class="input-container">
          <!-- 输入框 -->
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            :autosize="{ minRows: 3, maxRows: 8 }"
            placeholder="有问题尽管问Nano"
            class="portal-input"
            :class="{ 'has-mentions': selectedKnowledgeBase || selectedDocument }"
            :style="{ '--mention-width': mentionWidth + 'px' }"
            @keydown.enter.exact.prevent="handleSend"
            @keydown.ctrl.enter="handleSend"
            @focus="handleInputFocus"
            @blur="handleInputBlur"
            @input="handleInputChange"
            @keydown="handleKeydown"
            :disabled="sending"
            ref="inputRef"
          />
          <!-- 选中的知识库和文档标签（作为输入内容的一部分，浮动在输入框内） -->
          <div 
            v-if="selectedKnowledgeBase || selectedDocument" 
            class="selected-mentions-inline"
            ref="mentionContainerRef"
          >
            <el-tag
              v-if="selectedKnowledgeBase"
              type="primary"
              closable
              @close="clearKnowledgeBase"
              class="mention-tag-inline"
            >
              <el-icon class="mention-icon"><Search /></el-icon>
              @{{ selectedKnowledgeBase.name }}
            </el-tag>
            <el-tag
              v-if="selectedDocument"
              type="success"
              closable
              @close="clearDocument"
              class="mention-tag-inline"
            >
              <el-icon class="mention-icon"><Document /></el-icon>
              /{{ selectedDocument.originalFileName || selectedDocument.fileName || selectedDocument.name }}
            </el-tag>
          </div>
        </div>
        <!-- 知识库选择列表（@触发） -->
        <div 
          v-if="showKbList"
          class="kb-mention-list"
          :style="kbListStyle"
        >
          <div v-if="filteredKnowledgeBases.length === 0" class="kb-mention-empty">
            <el-icon><Document /></el-icon>
            <div>暂无可用知识库</div>
          </div>
          <div v-else class="kb-mention-items">
            <div
              v-for="(kb, index) in filteredKnowledgeBases"
              :key="kb.id"
              :class="['kb-mention-item', { 'kb-mention-item-active': selectedKbIndex === index }]"
              @click="selectKnowledgeBase(kb)"
              @mouseenter="selectedKbIndex = index"
            >
              <div class="kb-mention-item-name">{{ kb.name }}</div>
              <div class="kb-mention-item-docs">{{ kb.documentCount || 0 }} 个文档</div>
            </div>
          </div>
        </div>
        <!-- 文档选择列表（/触发） -->
        <div 
          v-if="showDocList"
          class="kb-mention-list doc-mention-list"
          :style="docListStyle"
        >
          <div v-if="filteredDocuments.length === 0" class="kb-mention-empty">
            <el-icon><Document /></el-icon>
            <div>暂无可用文档</div>
          </div>
          <div v-else class="kb-mention-items">
            <div
              v-for="(doc, index) in filteredDocuments"
              :key="doc.id"
              :class="['kb-mention-item', { 'kb-mention-item-active': selectedDocIndex === index }]"
              @click="selectDocument(doc)"
              @mouseenter="selectedDocIndex = index"
            >
              <div class="kb-mention-item-name">{{ doc.originalFileName || doc.fileName || doc.name || '未命名文档' }}</div>
              <div class="kb-mention-item-docs">{{ doc.fileType || '' }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 浮动控制选项 -->
      <div class="input-controls-float">
        <!-- 左侧控制按钮 -->
        <div class="input-left-controls">
          <el-dropdown 
            @command="handleModeChange" 
            trigger="click"
            placement="top-start"
          >
            <div class="control-item" :class="{ 'mode-selected': conversationMode === 'rag' || conversationMode === 'document' }">
              <el-icon><ChatLineRound /></el-icon>
              <span>{{ 
                conversationMode === 'rag' ? '知识库问答' : 
                conversationMode === 'document' ? '文档对话' : 
                '对话模式' 
              }}</span>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="chat" :class="{ 'is-selected': conversationMode === 'chat' }">
                  <span>普通对话</span>
                  <el-icon v-if="conversationMode === 'chat'" style="margin-left: 8px;"><Check /></el-icon>
                </el-dropdown-item>
                <el-dropdown-item command="rag" :class="{ 'is-selected': conversationMode === 'rag' }">
                  <span>知识库问答</span>
                  <el-icon v-if="conversationMode === 'rag'" style="margin-left: 8px;"><Check /></el-icon>
                </el-dropdown-item>
                <el-dropdown-item command="document" :class="{ 'is-selected': conversationMode === 'document' }">
                  <span>文档对话</span>
                  <el-icon v-if="conversationMode === 'document'" style="margin-left: 8px;"><Check /></el-icon>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          
          <el-dropdown @command="handleModelChange" trigger="click">
            <div class="control-item">
              <el-icon><Connection /></el-icon>
              <span>{{ selectedModelName || 'DS V3.2' }}</span>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="model in availableModels"
                  :key="model.id"
                  :command="model.id"
                >
                  <div style="display: flex; justify-content: space-between; align-items: center;">
                    <span>{{ model.name }}</span>
                    <el-tag v-if="model.isDefault" type="primary" size="small">默认</el-tag>
                  </div>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- 联网搜索开关（仅在普通对话模式下显示） -->
          <div v-if="conversationMode === 'chat'" class="control-item" style="display: flex; align-items: center; gap: 8px; padding: 0 12px;">
            <span style="font-size: 14px; color: #606266; white-space: nowrap;">联网搜索</span>
            <el-switch
              v-model="enableBrowserSearch"
              :disabled="sending"
              size="small"
            />
          </div>
        </div>

        <!-- 右侧控制按钮 -->
        <div class="input-right-controls">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :on-change="handleFileChange"
            :show-file-list="false"
            accept="image/*"
            multiple
          >
            <div class="control-item">
              <el-icon><Paperclip /></el-icon>
            </div>
          </el-upload>

          <el-tooltip content="重置会话" placement="top">
            <div class="control-item" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
            </div>
          </el-tooltip>

          <el-button
            type="primary"
            :disabled="(!question.trim() && selectedFiles.length === 0) || sending"
            @click="handleSend"
            :loading="sending"
            class="send-button"
          >
            <el-icon><Promotion /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 已选择的附件预览 -->
      <div v-if="selectedFiles.length > 0" class="attachments-preview">
        <div
          v-for="(file, index) in selectedFiles"
          :key="index"
          class="attachment-item"
        >
          <el-image
            v-if="isImageFile(file)"
            :src="getFilePreview(file)"
            :preview-src-list="[getFilePreview(file)]"
            fit="contain"
            class="attachment-preview-image"
            loading="lazy"
            :preview-teleported="true"
            :z-index="3000"
          />
          <div class="attachment-info">
            <span class="attachment-name" :title="file.name">{{ file.name }}</span>
            <span class="attachment-size">{{ formatFileSize(file.size) }}</span>
          </div>
          <el-button
            type="danger"
            :icon="Delete"
            size="small"
            text
            @click="removeFile(index)"
            :disabled="sending"
          />
        </div>
      </div>
    </div>

    <!-- 底部免责声明 -->
    <div class="portal-footer">
      <span class="footer-text">内容由AI生成仅供参考</span>
    </div>

    <!-- 修改密码对话框 -->
    <ChangePasswordDialog
      v-model="showChangePasswordDialog"
      @success="handlePasswordChangeSuccess"
    />
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatLineRound,
  ArrowDown,
  ArrowUp,
  ArrowRight,
  Connection,
  Paperclip,
  Promotion,
  Delete,
  Document,
  DocumentAdd,
  List,
  DataAnalysis,
  Clock,
  Picture,
  Refresh,
  Service,
  Check,
  User,
  Grid,
  Search,
  Folder
} from '@element-plus/icons-vue'
import { chat, chatStream, getMyConversations, getConversationMessages } from '@/api/chat'
import { getAvailableQAModels, getAvailableQAModelsForRAG } from '@/api/model'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { knowledgeBaseQAStream } from '@/api/knowledgeBaseQA'
import { getDocumentList } from '@/api/documentReader'
import { documentQAStream } from '@/api/documentReader'
import MessageList from '@/components/chat/MessageList.vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import AppHeader from '@/components/AppHeader.vue'

const router = useRouter()

const question = ref('')
const sending = ref(false)
const chatHistory = ref([])
const messageListRef = ref(null)
const conversationId = ref(null)
const availableModels = ref([])
const selectedModelId = ref(null)
const selectedFiles = ref([])
const isInputFocused = ref(false)
const uploadRef = ref(null)
const currentDate = ref('')
const currentTime = ref('')
const availableKnowledgeBases = ref([])
const selectedKnowledgeBaseId = ref(null)
const selectedKnowledgeBase = ref(null)
const conversationMode = ref('chat') // 'chat' 普通对话, 'rag' 知识库问答, 'document' 文档对话
const selectedKnowledgeBaseName = ref('')
const showChangePasswordDialog = ref(false)
const isHeaderCollapsed = ref(false)
const enableBrowserSearch = ref(false) // 联网搜索开关状态
const currentView = ref('welcome') // 'welcome' 或 'features'
const isContentOverflow = ref(false) // 内容是否溢出
const chatHistorySectionRef = ref(null)
const inputSectionRef = ref(null)
const inputRef = ref(null)
const inputWrapperRef = ref(null)
const showKbList = ref(false) // 是否显示知识库列表
const atSymbolIndex = ref(-1) // @符号在输入框中的位置
const selectedKbIndex = ref(0) // 当前选中的知识库索引
const kbListStyle = ref({}) // 知识库列表的样式
const showDocList = ref(false) // 是否显示文档列表
const slashSymbolIndex = ref(-1) // /符号在输入框中的位置
const selectedDocIndex = ref(0) // 当前选中的文档索引
const docListStyle = ref({}) // 文档列表的样式
const availableDocuments = ref([]) // 可用文档列表
const selectedDocumentId = ref(null) // 选中的文档ID
const selectedDocument = ref(null) // 选中的文档对象
const loadingDocuments = ref(false) // 是否正在加载文档列表
const mentionWidth = ref(0) // 标签的宽度
const mentionContainerRef = ref(null) // 标签容器的引用
const recentConversations = ref([]) // 最近三条会话历史
const selectedConversationId = ref(null) // 选中的会话ID
const loadingConversations = ref(false) // 是否正在加载会话列表

const selectedModelName = computed(() => {
  if (!selectedModelId.value) return 'DS V3.2'
  const model = availableModels.value.find(m => m.id === selectedModelId.value)
  return model ? model.name : 'DS V3.2'
})

// 获取用户信息
const getUserInfo = () => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      return JSON.parse(userInfoStr)
    } catch (e) {
      console.error('解析用户信息失败', e)
    }
  }
  return null
}

// 处理下拉菜单命令
const handleCommand = (command) => {
  if (command === 'changePassword') {
    showChangePasswordDialog.value = true
  }
  // logout 命令由 AppHeader 组件内部处理
}

// 处理密码修改成功
const handlePasswordChangeSuccess = () => {
  ElMessage.success('密码修改成功')
  showChangePasswordDialog.value = false
}

// 处理输入框焦点
const handleInputFocus = () => {
  isInputFocused.value = true
}

const handleInputBlur = () => {
  // 延迟处理，以便点击按钮时不会立即失焦
  setTimeout(() => {
    if (!sending.value && chatHistory.value.length === 0) {
      isInputFocused.value = false
    }
    // 延迟隐藏知识库和文档列表，以便点击列表项时不会立即隐藏
    setTimeout(() => {
      showKbList.value = false
      showDocList.value = false
    }, 200)
  }, 200)
}

// 处理输入框内容变化
const handleInputChange = () => {
  const text = question.value
  const cursorPos = getCursorPosition()
  
  // 查找@符号（知识库）
  let atIndex = -1
  for (let i = cursorPos - 1; i >= 0; i--) {
    if (text[i] === '@') {
      // 检查@后面是否有空格或其他@符号
      if (i === cursorPos - 1 || text[i + 1] === ' ' || text[i + 1] === '@') {
        atIndex = i
        break
      }
    } else if (text[i] === ' ' || text[i] === '\n') {
      break
    }
  }

  // 查找/符号（文档）
  let slashIndex = -1
  for (let i = cursorPos - 1; i >= 0; i--) {
    if (text[i] === '/') {
      // 检查/后面是否有空格或其他/符号
      if (i === cursorPos - 1 || text[i + 1] === ' ' || text[i + 1] === '/') {
        slashIndex = i
        break
      }
    } else if (text[i] === ' ' || text[i] === '\n' || text[i] === '@') {
      break
    }
  }

  // 优先处理@符号（知识库）
  if (atIndex >= 0) {
    atSymbolIndex.value = atIndex
    updateKbListPosition()
    showKbList.value = true
    selectedKbIndex.value = 0
    // 隐藏文档列表
    showDocList.value = false
    slashSymbolIndex.value = -1
  } else if (slashIndex >= 0) {
    // 处理/符号（文档）
    slashSymbolIndex.value = slashIndex
    updateDocListPosition()
    showDocList.value = true
    selectedDocIndex.value = 0
    // 隐藏知识库列表
    showKbList.value = false
    atSymbolIndex.value = -1
  } else {
    // 都没有，隐藏所有列表
    showKbList.value = false
    atSymbolIndex.value = -1
    showDocList.value = false
    slashSymbolIndex.value = -1
  }
}

// 获取光标位置
const getCursorPosition = () => {
  if (!inputRef.value) return 0
  const textarea = inputRef.value.$el?.querySelector('textarea')
  if (!textarea) return question.value.length
  return textarea.selectionStart || question.value.length
}

// 更新知识库列表位置
const updateKbListPosition = () => {
  nextTick(() => {
    if (!inputWrapperRef.value || !inputRef.value) return
    
    const textarea = inputRef.value.$el?.querySelector('textarea')
    if (!textarea) return

    const wrapperRect = inputWrapperRef.value.getBoundingClientRect()
    const textareaRect = textarea.getBoundingClientRect()
    
    // 计算@符号的位置，将列表显示在输入框上方，使用固定宽度
    kbListStyle.value = {
      bottom: `${wrapperRect.bottom - textareaRect.top + 8}px`,
      left: '16px',
      width: '280px',
      top: 'auto'
    }
  })
}

// 更新文档列表位置
const updateDocListPosition = () => {
  nextTick(() => {
    if (!inputWrapperRef.value || !inputRef.value) return
    
    const textarea = inputRef.value.$el?.querySelector('textarea')
    if (!textarea) return

    const wrapperRect = inputWrapperRef.value.getBoundingClientRect()
    const textareaRect = textarea.getBoundingClientRect()
    
    // 计算/符号的位置，将列表显示在输入框上方
    docListStyle.value = {
      bottom: `${wrapperRect.bottom - textareaRect.top + 8}px`,
      left: '16px',
      width: '280px',
      top: 'auto'
    }
  })
}

// 选择文档
const selectDocument = (doc) => {
  if (!doc) return
  
  const text = question.value
  const beforeSlash = text.substring(0, slashSymbolIndex.value)
  const afterCursor = text.substring(getCursorPosition())
  
  // 设置选中的文档
  selectedDocumentId.value = doc.id
  selectedDocument.value = doc
  
  // 如果在非文档对话模式下选择文档，自动切换到文档对话模式
  if (conversationMode.value !== 'document') {
    conversationMode.value = 'document'
    loadAvailableModels()
    const docName = doc.originalFileName || doc.fileName || doc.name || '未命名文档'
    ElMessage.success(`已切换到文档对话模式，并选择文档：${docName}`)
  } else {
    const docName = doc.originalFileName || doc.fileName || doc.name || '未命名文档'
    ElMessage.success(`已选择文档：${docName}`)
  }
  
  // 隐藏列表
  showDocList.value = false
  slashSymbolIndex.value = -1
  
  // 清空输入框中的/文档名称，只保留其他内容
  question.value = beforeSlash + afterCursor.trim()
  
  // 设置光标位置
  nextTick(() => {
    if (inputRef.value) {
      const textarea = inputRef.value.$el?.querySelector('textarea')
      if (textarea) {
        const newPos = beforeSlash.length
        textarea.setSelectionRange(newPos, newPos)
        textarea.focus()
      }
    }
  })
  
  // 更新标签宽度
  updateMentionWidth()
}

// 清除知识库选择
const clearKnowledgeBase = () => {
  selectedKnowledgeBaseId.value = null
  selectedKnowledgeBase.value = null
  selectedKnowledgeBaseName.value = ''
  
  // 从输入框中移除@知识库名称
  const text = question.value
  const atPattern = /@[^\s@]+\s*/g
  question.value = text.replace(atPattern, '')
  
  ElMessage.success('已清除知识库选择')
}

// 清除文档选择
const clearDocument = () => {
  selectedDocumentId.value = null
  selectedDocument.value = null
  
  // 从输入框中移除/文档名称
  const text = question.value
  const slashPattern = /\/[^\s/]+\s*/g
  question.value = text.replace(slashPattern, '')
  
  // 如果当前是文档对话模式，清除后切换回普通对话模式
  if (conversationMode.value === 'document') {
    conversationMode.value = 'chat'
    loadAvailableModels()
  }
  
  // 更新标签宽度
  updateMentionWidth()
  
  ElMessage.success('已清除文档选择')
}

// 加载文档列表
const loadDocuments = async () => {
  try {
    loadingDocuments.value = true
    const userInfo = getUserInfo()
    if (!userInfo) {
      availableDocuments.value = []
      return
    }

    const response = await getDocumentList({
      page: 1,
      pageSize: 100
    })
    
    let documents = []
    if (response && response.content && Array.isArray(response.content)) {
      documents = response.content
    } else if (response && response.list && Array.isArray(response.list)) {
      documents = response.list
    } else if (Array.isArray(response)) {
      documents = response
    } else if (response && response.data) {
      const data = response.data
      if (data.content && Array.isArray(data.content)) {
        documents = data.content
      } else if (data.list && Array.isArray(data.list)) {
        documents = data.list
      } else if (Array.isArray(data)) {
        documents = data
      }
    }
    
    availableDocuments.value = documents
  } catch (error) {
    console.error('加载文档列表失败', error)
    availableDocuments.value = []
  } finally {
    loadingDocuments.value = false
  }
}

// 处理键盘事件
const handleKeydown = (e) => {
  // 处理知识库列表
  if (showKbList.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      selectedKbIndex.value = Math.min(selectedKbIndex.value + 1, filteredKnowledgeBases.value.length - 1)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      selectedKbIndex.value = Math.max(selectedKbIndex.value - 1, 0)
    } else if (e.key === 'Enter' && !e.ctrlKey && !e.shiftKey) {
      e.preventDefault()
      if (filteredKnowledgeBases.value[selectedKbIndex.value]) {
        selectKnowledgeBase(filteredKnowledgeBases.value[selectedKbIndex.value])
      }
    } else if (e.key === 'Escape') {
      showKbList.value = false
    }
    return
  }
  
  // 处理文档列表
  if (showDocList.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      selectedDocIndex.value = Math.min(selectedDocIndex.value + 1, filteredDocuments.value.length - 1)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      selectedDocIndex.value = Math.max(selectedDocIndex.value - 1, 0)
    } else if (e.key === 'Enter' && !e.ctrlKey && !e.shiftKey) {
      e.preventDefault()
      if (filteredDocuments.value[selectedDocIndex.value]) {
        selectDocument(filteredDocuments.value[selectedDocIndex.value])
      }
    } else if (e.key === 'Escape') {
      showDocList.value = false
    }
  }
}

// 过滤后的知识库列表（最多5个）
const filteredKnowledgeBases = computed(() => {
  if (!showKbList.value || atSymbolIndex.value < 0) return []
  
  const searchText = question.value.substring(atSymbolIndex.value + 1, getCursorPosition()).toLowerCase()
  let filtered = availableKnowledgeBases.value
  
  if (searchText) {
    filtered = availableKnowledgeBases.value.filter(kb => 
      kb.name.toLowerCase().includes(searchText)
    )
  }
  
  return filtered.slice(0, 5) // 最多显示5个
})

// 过滤后的文档列表（最多5个）
const filteredDocuments = computed(() => {
  if (!showDocList.value || slashSymbolIndex.value < 0) return []
  
  const searchText = question.value.substring(slashSymbolIndex.value + 1, getCursorPosition()).toLowerCase()
  let filtered = availableDocuments.value
  
  if (searchText) {
    filtered = availableDocuments.value.filter(doc => {
      const fileName = doc.originalFileName || doc.fileName || doc.name || ''
      return fileName.toLowerCase().includes(searchText)
    })
  }
  
  return filtered.slice(0, 5) // 最多显示5个
})

// 选择知识库
const selectKnowledgeBase = (kb) => {
  if (!kb) return
  
  const text = question.value
  const beforeAt = text.substring(0, atSymbolIndex.value)
  const afterCursor = text.substring(getCursorPosition())
  
  // 设置选中的知识库
  selectedKnowledgeBaseId.value = kb.id
  selectedKnowledgeBase.value = kb
  selectedKnowledgeBaseName.value = kb.name
  
  // 清空输入框中的@知识库名称，只保留其他内容
  question.value = beforeAt + afterCursor.trim()
  
  // 如果在对话模式下选择知识库，自动切换到知识库问答模式
  if (conversationMode.value === 'chat') {
    conversationMode.value = 'rag'
    loadAvailableModels()
    ElMessage.success(`已切换到知识库问答模式，并选择知识库：${kb.name}`)
  } else {
    ElMessage.success(`已选择知识库：${kb.name}`)
  }
  
  // 隐藏列表
  showKbList.value = false
  atSymbolIndex.value = -1
  
  // 设置光标位置
  nextTick(() => {
    if (inputRef.value) {
      const textarea = inputRef.value.$el?.querySelector('textarea')
      if (textarea) {
        const newPos = beforeAt.length
        textarea.setSelectionRange(newPos, newPos)
        textarea.focus()
      }
    }
  })
  
  ElMessage.success(`已选择知识库：${kb.name}`)
}

// 处理模式切换
const handleModeChange = (mode) => {
  conversationMode.value = mode
  if (mode === 'rag') {
    // 切换到知识库问答模式时，如果没有选择知识库，提示用户
    if (!selectedKnowledgeBaseId.value && availableKnowledgeBases.value.length > 0) {
      ElMessage.info('请在输入框中输入@选择知识库')
    }
    // 清除文档选择
    selectedDocumentId.value = null
    selectedDocument.value = null
  } else if (mode === 'document') {
    // 切换到文档对话模式时，如果没有选择文档，提示用户
    if (!selectedDocumentId.value && availableDocuments.value.length > 0) {
      ElMessage.info('请在输入框中输入/选择文档')
    }
    // 清除知识库选择
    selectedKnowledgeBaseId.value = null
    selectedKnowledgeBase.value = null
    selectedKnowledgeBaseName.value = ''
    showKbList.value = false
  } else {
    // 切换到普通对话模式时，清除知识库和文档选择
    selectedKnowledgeBaseId.value = null
    selectedKnowledgeBase.value = null
    selectedKnowledgeBaseName.value = ''
    selectedDocumentId.value = null
    selectedDocument.value = null
    showKbList.value = false
  }
  // 重新加载模型列表（因为不同模式使用的模型可能不同）
  loadAvailableModels()
}

// 处理模型切换
const handleModelChange = (modelId) => {
  selectedModelId.value = modelId
}


// 处理知识库选择（保留用于其他可能的调用）
const handleKnowledgeBaseSelect = (kbId) => {
  if (kbId === 'clear') {
    selectedKnowledgeBaseId.value = null
    selectedKnowledgeBase.value = null
    selectedKnowledgeBaseName.value = ''
    ElMessage.success('已清除知识库选择')
    return
  }
  
  const kb = availableKnowledgeBases.value.find(k => k.id === kbId)
  if (kb) {
    selectedKnowledgeBaseId.value = kbId
    selectedKnowledgeBase.value = kb
    selectedKnowledgeBaseName.value = kb.name
    ElMessage.success(`已选择知识库：${kb.name}`)
  }
}

// 处理文件选择
const handleFileChange = (file) => {
  if (!file.raw.type.startsWith('image/')) {
    ElMessage.warning('只能上传图片文件')
    return
  }
  
  if (file.raw.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过10MB')
    return
  }
  
  selectedFiles.value.push(file.raw)
}

// 移除文件
const removeFile = (index) => {
  selectedFiles.value.splice(index, 1)
}

// 判断是否为图片文件
const isImageFile = (file) => {
  return file.type && file.type.startsWith('image/')
}

// 获取文件预览URL
const getFilePreview = (file) => {
  return URL.createObjectURL(file)
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

// 处理功能点击
const handleFeatureClick = (feature) => {
  const userInfo = getUserInfo()
  const basePath = userInfo?.role === 1 ? '/admin' : '/user'
  
  switch (feature) {
    case 'kb-qa':
      router.push(`${basePath}/kb-qa`)
      break
    case 'document':
      router.push(`${basePath}/document-reader`)
      break
    case 'apps':
      router.push(`${basePath}/apps`)
      break
    case 'ai-drawio':
      router.push(`${basePath}/ai-drawio`)
      break
    case 'chat-history':
      router.push(`${basePath}/chat-history`)
      break
    case 'knowledge-base':
      router.push(`${basePath}/knowledge-base`)
      break
  }
}

// 发送消息
const handleSend = async () => {
  if ((!question.value.trim() && selectedFiles.value.length === 0) || sending.value) {
    return
  }

  const userQuestion = question.value.trim()
  
  // 构建用户消息内容（包含附件信息）
  let userMessageContent = userQuestion
  if (selectedFiles.value.length > 0) {
    const fileNames = selectedFiles.value.map(f => f.name).join('、')
    userMessageContent = `[已上传 ${selectedFiles.value.length} 张图片: ${fileNames}]\n\n${userQuestion}`
  }
  
  // 添加用户消息
  chatHistory.value.push({
    type: 'user',
    content: userMessageContent,
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    files: selectedFiles.value.map(f => ({
      name: f.name,
      size: f.size,
      type: f.type,
      preview: getFilePreview(f)
    }))
  })

  // 保存当前选择的文件（用于发送）
  const filesToSend = [...selectedFiles.value]
  
  // 清空输入框和文件列表
  question.value = ''
  selectedFiles.value = []
  
  // 确保输入框获得焦点
  isInputFocused.value = true
  
  // 滚动到底部
  await nextTick()
  scrollToBottom(true)

  // 添加AI回复占位
  const aiMessageIndex = chatHistory.value.length
  chatHistory.value.push({
    type: 'assistant',
    content: '',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    isLoading: true
  })
  
  sending.value = true

  // 构建历史对话
  const history = chatHistory.value
    .slice(0, -1)
    .filter(msg => !msg.isLoading)
    .map(msg => ({
      role: msg.type === 'user' ? 'user' : 'assistant',
      content: msg.content
    }))

  const userInfo = getUserInfo()
  const userId = userInfo ? userInfo.userId : null

  try {
    const currentConversationId = conversationId.value
    
    // 如果选择了文档，使用文档问答API
    if (conversationMode.value === 'document' && selectedDocumentId.value) {
      await handleDocumentStreamResponse(
        selectedDocumentId.value,
        userQuestion,
        currentConversationId,
        userId,
        history,
        aiMessageIndex,
        selectedModelId.value
      )
    } else if (conversationMode.value === 'document' && !selectedDocumentId.value) {
      ElMessage.warning('请先在输入框中输入/选择文档')
      // 移除AI回复占位
      chatHistory.value.pop()
      sending.value = false
      return
    } else if (conversationMode.value === 'rag' && selectedKnowledgeBaseId.value) {
      // 使用知识库问答流式响应
      await handleKnowledgeBaseStreamResponse(
        selectedKnowledgeBaseId.value,
        userQuestion,
        currentConversationId,
        userId,
        history,
        aiMessageIndex,
        selectedModelId.value
      )
    } else if (conversationMode.value === 'rag' && !selectedKnowledgeBaseId.value) {
      ElMessage.warning('请先在输入框中输入@选择知识库')
      // 移除AI回复占位
      chatHistory.value.pop()
      sending.value = false
      return
    } else {
      // 使用普通聊天流式响应
      await handleStreamResponse(userQuestion, currentConversationId, userId, history, aiMessageIndex, selectedModelId.value, enableBrowserSearch.value, filesToSend)
    }
  } catch (error) {
    console.error('发送消息失败', error)
    ElMessage.error('发送消息失败：' + (error.message || '未知错误'))
    if (chatHistory.value[aiMessageIndex]) {
      chatHistory.value[aiMessageIndex].content = '抱歉，生成答案时发生错误，请重试。'
      chatHistory.value[aiMessageIndex].isLoading = false
    }
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom(false)
  }
}

// 处理知识库问答流式响应
const handleKnowledgeBaseStreamResponse = async (kbId, question, requestConversationId, userId, history, aiMessageIndex, modelId) => {
  let reader = null
  let response = null
  
  let rafId = null
  let pendingContent = null
  
  const scheduleUpdate = (content) => {
    pendingContent = content
    
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
    }
    
    rafId = requestAnimationFrame(() => {
      if (chatHistory.value[aiMessageIndex] && pendingContent !== null) {
        chatHistory.value[aiMessageIndex].content = pendingContent
        if (chatHistory.value[aiMessageIndex].isLoading) {
          chatHistory.value[aiMessageIndex].isLoading = false
        }
        
        if (messageListRef.value?.scrollToBottom) {
          messageListRef.value.scrollToBottom(false)
        }
      }
      rafId = null
    })
  }
  
  try {
    response = await knowledgeBaseQAStream(kbId, question, requestConversationId, userId, history, modelId)
    
    if (!response.ok) {
      const errorText = await response.text().catch(() => '未知错误')
      throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`)
    }

    if (!response.body) {
      throw new Error('响应体不可用，连接可能已断开')
    }

    reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let hasReceivedData = false

    try {
      while (true) {
        let readResult
        try {
          readResult = await reader.read()
        } catch (readError) {
          if (readError.message && readError.message.includes('disconnected')) {
            console.warn('流式连接已断开')
            if (rafId !== null) {
              cancelAnimationFrame(rafId)
              rafId = null
            }
            if (hasReceivedData && chatHistory.value[aiMessageIndex]?.content) {
              if (pendingContent !== null) {
                chatHistory.value[aiMessageIndex].content = pendingContent
                pendingContent = null
              }
              chatHistory.value[aiMessageIndex].isLoading = false
              break
            }
            throw new Error('连接已断开，请重试')
          }
          throw readError
        }
        
        const { done, value } = readResult
        
        if (done) {
          break
        }

        hasReceivedData = true
        buffer += decoder.decode(value, { stream: true })
        
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmedLine = line.trim()
          if (!trimmedLine) continue
          
          if (trimmedLine.startsWith('data:')) {
            const data = trimmedLine.slice(5).trim()
            if (data === '[DONE]' || data === '') continue

            try {
              const json = JSON.parse(data)
              
              if (json.conversationId) {
                conversationId.value = json.conversationId.toString()
              }
              
              if (json.answer !== undefined && json.answer !== null) {
                if (!chatHistory.value[aiMessageIndex]) {
                  continue
                }
                scheduleUpdate(json.answer)
              }

              if (json.finished) {
                if (rafId !== null) {
                  cancelAnimationFrame(rafId)
                  rafId = null
                }
                if (chatHistory.value[aiMessageIndex]) {
                  chatHistory.value[aiMessageIndex].isLoading = false
                  chatHistory.value[aiMessageIndex].content = json.answer || pendingContent || chatHistory.value[aiMessageIndex].content || ''
                  pendingContent = null
                }
                if (json.conversationId) {
                  conversationId.value = json.conversationId.toString()
                }
                break
              }
            } catch (e) {
              console.warn('解析流式数据失败', e, '原始数据:', data)
            }
          }
        }
      }
    } finally {
      if (rafId !== null) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
      if (pendingContent !== null && chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = pendingContent
        pendingContent = null
      }
      
      if (reader) {
        try {
          await reader.cancel()
        } catch (cancelError) {
          console.warn('取消reader失败', cancelError)
        }
      }
    }

    if (buffer.trim()) {
      const lines = buffer.split('\n')
      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine) continue
        
        if (trimmedLine.startsWith('data:')) {
          const data = trimmedLine.slice(5).trim()
          if (data === '[DONE]' || data === '') continue
          
          try {
            const json = JSON.parse(data)
            if (json.answer !== undefined && json.answer !== null && chatHistory.value[aiMessageIndex]) {
              chatHistory.value[aiMessageIndex].content = json.answer
              chatHistory.value[aiMessageIndex].isLoading = false
            }
          } catch (e) {
            console.warn('解析流式数据失败', e, '原始数据:', data)
          }
        }
      }
    }

    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].isLoading) {
      chatHistory.value[aiMessageIndex].isLoading = false
    }
    
  } catch (error) {
    console.error('知识库问答流式响应处理失败', error)
    
    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].content) {
      chatHistory.value[aiMessageIndex].isLoading = false
      chatHistory.value[aiMessageIndex].content += '\n\n⚠️ 连接中断，部分内容可能不完整。'
    } else {
      if (chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = '抱歉，连接已断开，请重试。'
        chatHistory.value[aiMessageIndex].isLoading = false
      }
    }
    
    throw error
  }
}

// 处理文档问答流式响应
const handleDocumentStreamResponse = async (docId, question, requestConversationId, userId, history, aiMessageIndex, modelId) => {
  let response = null
  
  let rafId = null
  let pendingContent = null
  
  const scheduleUpdate = (content) => {
    pendingContent = content
    
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
    }
    
    rafId = requestAnimationFrame(() => {
      if (chatHistory.value[aiMessageIndex] && pendingContent !== null) {
        chatHistory.value[aiMessageIndex].content = pendingContent
        if (chatHistory.value[aiMessageIndex].isLoading) {
          chatHistory.value[aiMessageIndex].isLoading = false
        }
        
        if (messageListRef.value?.scrollToBottom) {
          messageListRef.value.scrollToBottom(false)
        }
      }
      rafId = null
    })
  }
  
  try {
    response = await documentQAStream(docId, question, requestConversationId, userId, history, modelId)
    
    if (!response.ok) {
      const errorText = await response.text().catch(() => '未知错误')
      throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`)
    }

    if (!response.body) {
      throw new Error('响应体不可用，连接可能已断开')
    }

    let reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let hasReceivedData = false

    try {
      while (true) {
        const { done, value } = await reader.read()
        
        if (done) {
          break
        }
        
        hasReceivedData = true
        buffer += decoder.decode(value, { stream: true })
        
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        
        for (const line of lines) {
          const trimmedLine = line.trim()
          if (!trimmedLine) continue
          
          if (trimmedLine.startsWith('data:')) {
            const data = trimmedLine.slice(5).trim()
            if (data === '[DONE]' || data === '') continue
            
            try {
              const json = JSON.parse(data)
              if (json.answer !== undefined && json.answer !== null && chatHistory.value[aiMessageIndex]) {
                scheduleUpdate(json.answer)
              }
            } catch (e) {
              console.warn('解析流式数据失败', e, '原始数据:', data)
            }
          }
        }
      }
    } finally {
      if (reader) {
        try {
          reader.releaseLock()
        } catch (e) {
          console.warn('释放reader失败', e)
        }
      }
    }

    if (buffer.trim()) {
      const lines = buffer.split('\n')
      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine) continue
        
        if (trimmedLine.startsWith('data:')) {
          const data = trimmedLine.slice(5).trim()
          if (data === '[DONE]' || data === '') continue
          
          try {
            const json = JSON.parse(data)
            if (json.answer !== undefined && json.answer !== null && chatHistory.value[aiMessageIndex]) {
              chatHistory.value[aiMessageIndex].content = json.answer
              chatHistory.value[aiMessageIndex].isLoading = false
            }
          } catch (e) {
            console.warn('解析流式数据失败', e, '原始数据:', data)
          }
        }
      }
    }

    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].isLoading) {
      chatHistory.value[aiMessageIndex].isLoading = false
    }
    
  } catch (error) {
    console.error('文档问答流式响应处理失败', error)
    
    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].content) {
      chatHistory.value[aiMessageIndex].isLoading = false
      chatHistory.value[aiMessageIndex].content += '\n\n⚠️ 连接中断，部分内容可能不完整。'
    } else {
      if (chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = '抱歉，连接已断开，请重试。'
        chatHistory.value[aiMessageIndex].isLoading = false
      }
    }
    
    throw error
  }
}

// 处理流式响应
const handleStreamResponse = async (question, requestConversationId, userId, history, aiMessageIndex, modelId, enableBrowserSearch, files = []) => {
  let reader = null
  let response = null
  
  let rafId = null
  let pendingContent = null
  
  const scheduleUpdate = (content) => {
    pendingContent = content
    
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
    }
    
    rafId = requestAnimationFrame(() => {
      if (chatHistory.value[aiMessageIndex] && pendingContent !== null) {
        chatHistory.value[aiMessageIndex].content = pendingContent
        if (chatHistory.value[aiMessageIndex].isLoading) {
          chatHistory.value[aiMessageIndex].isLoading = false
        }
        
        if (messageListRef.value?.scrollToBottom) {
          messageListRef.value.scrollToBottom(false)
        }
      }
      rafId = null
    })
  }
  
  try {
    response = await chatStream(question, requestConversationId, userId, history, modelId, enableBrowserSearch, files)
    
    if (!response.ok) {
      const errorText = await response.text().catch(() => '未知错误')
      throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`)
    }

    if (!response.body) {
      throw new Error('响应体不可用，连接可能已断开')
    }

    reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let hasReceivedData = false

    try {
      while (true) {
        let readResult
        try {
          readResult = await reader.read()
        } catch (readError) {
          if (readError.message && readError.message.includes('disconnected')) {
            console.warn('流式连接已断开')
            if (rafId !== null) {
              cancelAnimationFrame(rafId)
              rafId = null
            }
            if (hasReceivedData && chatHistory.value[aiMessageIndex]?.content) {
              if (pendingContent !== null) {
                chatHistory.value[aiMessageIndex].content = pendingContent
                pendingContent = null
              }
              chatHistory.value[aiMessageIndex].isLoading = false
              break
            }
            throw new Error('连接已断开，请重试')
          }
          throw readError
        }
        
        const { done, value } = readResult
        
        if (done) {
          break
        }

        hasReceivedData = true
        buffer += decoder.decode(value, { stream: true })
        
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmedLine = line.trim()
          if (!trimmedLine) continue
          
          if (trimmedLine.startsWith('data:')) {
            const data = trimmedLine.slice(5).trim()
            if (data === '[DONE]' || data === '') continue

            try {
              const json = JSON.parse(data)
              
              if (json.conversationId) {
                conversationId.value = json.conversationId.toString()
              }
              
              if (json.answer !== undefined && json.answer !== null) {
                if (!chatHistory.value[aiMessageIndex]) {
                  continue
                }
                scheduleUpdate(json.answer)
              }

              if (json.finished) {
                if (rafId !== null) {
                  cancelAnimationFrame(rafId)
                  rafId = null
                }
                if (chatHistory.value[aiMessageIndex]) {
                  chatHistory.value[aiMessageIndex].isLoading = false
                  chatHistory.value[aiMessageIndex].content = json.answer || pendingContent || chatHistory.value[aiMessageIndex].content || ''
                  pendingContent = null
                }
                if (json.conversationId) {
                  conversationId.value = json.conversationId.toString()
                }
                break
              }
            } catch (e) {
              console.warn('解析流式数据失败', e, '原始数据:', data)
            }
          }
        }
      }
    } finally {
      if (rafId !== null) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
      if (pendingContent !== null && chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = pendingContent
        pendingContent = null
      }
      
      if (reader) {
        try {
          await reader.cancel()
        } catch (cancelError) {
          console.warn('取消reader失败', cancelError)
        }
      }
    }

    if (buffer.trim()) {
      const lines = buffer.split('\n')
      for (const line of lines) {
        const trimmedLine = line.trim()
        if (!trimmedLine) continue
        
        if (trimmedLine.startsWith('data:')) {
          const data = trimmedLine.slice(5).trim()
          if (data === '[DONE]' || data === '') continue
          
          try {
            const json = JSON.parse(data)
            if (json.answer !== undefined && json.answer !== null && chatHistory.value[aiMessageIndex]) {
              chatHistory.value[aiMessageIndex].content = json.answer
              chatHistory.value[aiMessageIndex].isLoading = false
            }
          } catch (e) {
            console.warn('解析流式数据失败', e, '原始数据:', data)
          }
        }
      }
    }

    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].isLoading) {
      chatHistory.value[aiMessageIndex].isLoading = false
    }
    
  } catch (error) {
    console.error('流式响应处理失败', error)
    
    if (chatHistory.value[aiMessageIndex] && chatHistory.value[aiMessageIndex].content) {
      chatHistory.value[aiMessageIndex].isLoading = false
      chatHistory.value[aiMessageIndex].content += '\n\n⚠️ 连接中断，部分内容可能不完整。'
    } else {
      if (chatHistory.value[aiMessageIndex]) {
        chatHistory.value[aiMessageIndex].content = '抱歉，连接已断开，请重试。'
        chatHistory.value[aiMessageIndex].isLoading = false
      }
    }
    
    throw error
  }
}

// 重新生成响应
const handleRegenerate = async (messageIndex) => {
  if (sending.value || messageIndex < 0 || messageIndex >= chatHistory.value.length) {
    return
  }

  const assistantMessage = chatHistory.value[messageIndex]
  if (assistantMessage.type !== 'assistant' || assistantMessage.isLoading) {
    return
  }

  if (messageIndex === 0 || chatHistory.value[messageIndex - 1].type !== 'user') {
    ElMessage.warning('无法找到对应的用户消息')
    return
  }

  const userMessage = chatHistory.value[messageIndex - 1]
  const userQuestion = userMessage.content

  chatHistory.value.splice(messageIndex, 1)

  await nextTick()
  scrollToBottom(true)

  const aiMessageIndex = chatHistory.value.length
  chatHistory.value.push({
    type: 'assistant',
    content: '',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    isLoading: true
  })

  sending.value = true

  const history = chatHistory.value
    .slice(0, -1)
    .filter(msg => !msg.isLoading)
    .map(msg => ({
      role: msg.type === 'user' ? 'user' : 'assistant',
      content: msg.content
    }))

  const userInfo = getUserInfo()
  const userId = userInfo ? userInfo.userId : null

  const filesToSend = []

  try {
    const currentConversationId = conversationId.value
    
    // 如果选择了知识库问答模式且选择了知识库，使用知识库问答API；否则使用普通聊天API
    if (conversationMode.value === 'rag' && selectedKnowledgeBaseId.value) {
      await handleKnowledgeBaseStreamResponse(
        selectedKnowledgeBaseId.value,
        userQuestion,
        currentConversationId,
        userId,
        history,
        aiMessageIndex,
        selectedModelId.value
      )
    } else if (conversationMode.value === 'rag' && !selectedKnowledgeBaseId.value) {
      ElMessage.warning('请先在输入框中输入@选择知识库')
      // 移除AI回复占位
      chatHistory.value.pop()
      sending.value = false
      return
    } else {
      await handleStreamResponse(userQuestion, currentConversationId, userId, history, aiMessageIndex, selectedModelId.value, enableBrowserSearch.value, filesToSend)
    }
  } catch (error) {
    console.error('重新生成响应失败', error)
    ElMessage.error('重新生成响应失败：' + (error.message || '未知错误'))
    if (chatHistory.value[aiMessageIndex]) {
      chatHistory.value[aiMessageIndex].content = '抱歉，重新生成答案时发生错误，请重试。'
      chatHistory.value[aiMessageIndex].isLoading = false
    }
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom(false)
  }
}

// 滚动到底部
const scrollToBottom = (force = false) => {
  nextTick(() => {
    if (messageListRef.value?.$el) {
      if (!force && !isNearBottom()) {
        return
      }
      messageListRef.value.$el.scrollTop = messageListRef.value.$el.scrollHeight
    }
  })
}

// 检查是否在底部
const isNearBottom = () => {
  if (!messageListRef.value?.$el) return true
  const element = messageListRef.value.$el
  const threshold = 100
  return element.scrollHeight - element.scrollTop - element.clientHeight < threshold
}

// 更新日期和时间
const updateDateTime = () => {
  const now = new Date()
  currentDate.value = now.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}


// 处理刷新（重置会话）
const handleRefresh = () => {
  chatHistory.value = []
  conversationId.value = null
  question.value = ''
  selectedFiles.value = []
  isInputFocused.value = false
  ElMessage.success('会话已重置')
}


// 处理建议提示点击
const handlePromptClick = (prompt) => {
  question.value = prompt
  // 自动聚焦输入框
  nextTick(() => {
    const inputElement = document.querySelector('.portal-input textarea')
    if (inputElement) {
      inputElement.focus()
    }
  })
}

// 加载最近三条会话历史
const loadRecentConversations = async () => {
  try {
    loadingConversations.value = true
    const userInfo = getUserInfo()
    if (!userInfo) {
      recentConversations.value = []
      return
    }

    const response = await getMyConversations(1, 3) // 获取第一页，每页3条
    
    let conversations = []
    if (response && response.content && Array.isArray(response.content)) {
      conversations = response.content
    } else if (response && response.list && Array.isArray(response.list)) {
      conversations = response.list
    } else if (Array.isArray(response)) {
      conversations = response
    } else if (response && response.data) {
      const data = response.data
      if (data.content && Array.isArray(data.content)) {
        conversations = data.content
      } else if (data.list && Array.isArray(data.list)) {
        conversations = data.list
      } else if (Array.isArray(data)) {
        conversations = data
      }
    }
    
    // 取最近3条，不限类型
    recentConversations.value = conversations.slice(0, 3)
  } catch (error) {
    console.error('加载最近会话失败', error)
    recentConversations.value = []
  } finally {
    loadingConversations.value = false
  }
}

// 处理会话点击
const handleConversationClick = async (conversation) => {
  if (!conversation || !conversation.id) return
  
  selectedConversationId.value = conversation.id
  await loadConversationMessages(conversation.id)
}

// 加载会话消息
const loadConversationMessages = async (convId) => {
  try {
    const response = await getConversationMessages(convId)
    
    let messages = []
    if (response && response.content && Array.isArray(response.content)) {
      messages = response.content
    } else if (response && response.list && Array.isArray(response.list)) {
      messages = response.list
    } else if (Array.isArray(response)) {
      messages = response
    } else if (response && response.data) {
      const data = response.data
      if (data.content && Array.isArray(data.content)) {
        messages = data.content
      } else if (data.list && Array.isArray(data.list)) {
        messages = data.list
      } else if (Array.isArray(data)) {
        messages = data
      }
    }
    
    // 转换消息格式
    chatHistory.value = messages.map(msg => ({
      type: msg.role === 'user' ? 'user' : 'assistant',
      content: msg.content || msg.answer || '',
      time: msg.createTime ? new Date(msg.createTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      isLoading: false
    }))
    
    // 设置会话ID
    conversationId.value = convId.toString()
    
    // 滚动到底部
    await nextTick()
    scrollToBottom(true)
    
    // 聚焦输入框
    isInputFocused.value = true
    nextTick(() => {
      const inputElement = document.querySelector('.portal-input textarea')
      if (inputElement) {
        inputElement.focus()
      }
    })
  } catch (error) {
    console.error('加载会话消息失败', error)
    ElMessage.error('加载会话消息失败：' + (error.message || '未知错误'))
  }
}

// 处理继续对话
const handleContinueConversation = async () => {
  if (!selectedConversationId.value) {
    // 如果没有选中的会话，使用最近一条
    if (recentConversations.value.length > 0) {
      await handleConversationClick(recentConversations.value[0])
    } else {
      ElMessage.warning('没有可继续的会话')
    }
    return
  }
  
  await loadConversationMessages(selectedConversationId.value)
}

// 加载可用模型列表
const loadAvailableModels = async () => {
  try {
    // 根据模式选择不同的模型API
    let api
    if (conversationMode.value === 'rag') {
      api = getAvailableQAModelsForRAG
    } else if (conversationMode.value === 'document') {
      // 文档对话模式可以使用普通问答模型
      api = getAvailableQAModels
    } else {
      api = getAvailableQAModels
    }
    const models = await api()
    availableModels.value = models || []
    
    if (availableModels.value.length > 0) {
      const defaultModel = availableModels.value.find(m => m.isDefault)
      if (defaultModel) {
        selectedModelId.value = defaultModel.id
      } else {
        selectedModelId.value = availableModels.value[0].id
      }
    }
  } catch (error) {
    console.error('加载可用模型列表失败', error)
    availableModels.value = []
  }
}

// 加载知识库列表
const loadKnowledgeBases = async () => {
  try {
    // 获取用户信息
    const userInfo = getUserInfo()
    const userId = userInfo ? userInfo.userId : null
    const userRole = userInfo ? userInfo.role : null
    
    // 构建请求参数
    const params = {
      page: 1,
      pageSize: 100
    }
    
    // 普通用户只获取启用的知识库，管理员可以获取所有
    if (userRole !== 1) {
      params.status = 1 // 1 表示启用状态
    }
    
    // 传递用户ID，后端会根据权限过滤
    if (userId) {
      params.userId = userId
    }
    
    const response = await getKnowledgeBaseList(params)
    
    // 处理不同的响应格式
    let knowledgeBases = []
    if (response && response.content && Array.isArray(response.content)) {
      knowledgeBases = response.content
    } else if (response && response.list && Array.isArray(response.list)) {
      knowledgeBases = response.list
    } else if (Array.isArray(response)) {
      knowledgeBases = response
    } else if (response && response.data) {
      const data = response.data
      if (data.content && Array.isArray(data.content)) {
        knowledgeBases = data.content
      } else if (data.list && Array.isArray(data.list)) {
        knowledgeBases = data.list
      } else if (Array.isArray(data)) {
        knowledgeBases = data
      }
    }
    
    // 过滤出启用的知识库（status === 'active' 或 status === 1）
    availableKnowledgeBases.value = knowledgeBases.filter(kb => {
      const status = kb.status
      return status === 'active' || status === 1 || status === '1'
    })
    
    console.log('加载知识库列表成功，数量:', availableKnowledgeBases.value.length)
  } catch (error) {
    console.error('加载知识库列表失败', error)
    ElMessage.error('加载知识库列表失败：' + (error.message || '未知错误'))
    availableKnowledgeBases.value = []
  }
}

// 计算标签宽度并更新输入框样式
const updateMentionWidth = () => {
  nextTick(() => {
    if (mentionContainerRef.value && (selectedKnowledgeBase.value || selectedDocument.value)) {
      // 等待 DOM 更新后再计算宽度
      setTimeout(() => {
        if (mentionContainerRef.value) {
          const width = mentionContainerRef.value.offsetWidth
          mentionWidth.value = width + 12 // 加上间距和额外空间
        }
      }, 0)
    } else {
      mentionWidth.value = 0
    }
  })
}

// 监听标签变化，更新宽度
watch([selectedKnowledgeBase, selectedDocument], () => {
  updateMentionWidth()
}, { deep: true })

// 监听知识库选择变化，重新加载模型
watch(selectedKnowledgeBaseId, () => {
  if (conversationMode.value === 'rag') {
    loadAvailableModels()
  }
  updateMentionWidth()
})

// 监听对话模式变化，重新加载模型
watch(conversationMode, () => {
  loadAvailableModels()
})


// 检测内容是否溢出
const checkContentOverflow = () => {
  nextTick(() => {
    if (!chatHistorySectionRef.value || !inputSectionRef.value) {
      isContentOverflow.value = false
      return
    }

    // 如果没有消息，不显示溢出状态
    if (chatHistory.value.length === 0 && !isInputFocused.value) {
      isContentOverflow.value = false
      return
    }

    const portalContent = document.querySelector('.portal-content')
    if (!portalContent) {
      isContentOverflow.value = false
      return
    }

    // 获取输入框的位置
    const inputRect = inputSectionRef.value.getBoundingClientRect()
    const portalContentRect = portalContent.getBoundingClientRect()
    
    // 计算聊天历史区域的实际内容高度
    const chatHistoryContent = chatHistorySectionRef.value.querySelector('.chat-history-content')
    if (!chatHistoryContent) {
      isContentOverflow.value = false
      return
    }

    const contentHeight = chatHistoryContent.scrollHeight
    // 可用高度 = 输入框顶部位置 - 内容区域顶部位置 - 上下padding
    const availableHeight = inputRect.top - portalContentRect.top - 40

    // 如果内容高度超过可用高度，则认为内容溢出
    isContentOverflow.value = contentHeight > availableHeight
  })
}

// 监听消息变化，检测内容溢出
watch(() => chatHistory.value, () => {
  checkContentOverflow()
}, { deep: true })

// 监听输入框焦点变化
watch(() => isInputFocused.value, () => {
  checkContentOverflow()
})

// 监听窗口大小变化
const handleResize = () => {
  checkContentOverflow()
  if (showKbList.value) {
    updateKbListPosition()
  }
}

onMounted(() => {
  updateDateTime()
  // 每秒更新时间
  setInterval(updateDateTime, 1000)
  loadKnowledgeBases()
  loadAvailableModels()
  loadRecentConversations()
  loadDocuments()
  checkContentOverflow()
  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
  // 使用 ResizeObserver 监听内容区域变化
  nextTick(() => {
    if (chatHistorySectionRef.value) {
      const resizeObserver = new ResizeObserver(() => {
        checkContentOverflow()
      })
      resizeObserver.observe(chatHistorySectionRef.value)
      // 也监听聊天历史内容的变化
      const chatHistoryContent = chatHistorySectionRef.value.querySelector('.chat-history-content')
      if (chatHistoryContent) {
        resizeObserver.observe(chatHistoryContent)
      }
    }
    
    // 初始化时计算标签宽度
    updateMentionWidth()
    
    // 使用 ResizeObserver 监听标签宽度变化
    if (mentionContainerRef.value) {
      const mentionResizeObserver = new ResizeObserver(() => {
        updateMentionWidth()
      })
      mentionResizeObserver.observe(mentionContainerRef.value)
      
      onUnmounted(() => {
        mentionResizeObserver.disconnect()
      })
    }
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.portal-container {
  height: 100vh; /* 固定高度，确保只有一个滚动条 */
  background: var(--el-bg-color-page, #f5f7fa);
  display: flex;
  flex-direction: column;
  padding: 0;
  margin: 0;
  position: relative;
  width: 100%;
  overflow: hidden; /* 防止容器本身滚动，只让portal-content滚动 */
  z-index: 1; /* 确保在导航栏下方 */
}

/* 确保 Portal 页面的导航栏始终显示在最上层并占据顶部宽度 */
.portal-container :deep(.app-header) {
  z-index: 1000 !important;
  position: fixed !important;
  width: 100vw !important;
  max-width: 100vw !important;
  left: 0 !important;
  right: 0 !important;
  top: 0 !important;
}




/* 主要内容区域 */
.portal-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto; /* 唯一的滚动条 */
  overflow-x: hidden;
  padding: 40px 0; /* 只保留上下内边距，左右为0让滚动条靠右 */
  padding-top: calc(40px + 60px) !important; /* 为固定导航栏留出空间，确保不被覆盖 */
  width: 100%; /* 占据全宽，让滚动条在视口最右侧 */
  margin: 0;
  margin-top: 0 !important; /* 确保没有额外的上边距 */
  transition: padding-top 0.3s ease;
  position: relative;
  box-sizing: border-box;
  min-height: 0; /* 确保flex子元素可以正确收缩 */
  background: transparent; /* 确保透明，让输入框能够透过看到后面的内容 */
}

/* 当内容溢出时，禁用 portal-content 的滚动，只让 chat-history-section 滚动 */
.portal-content.no-scroll {
  overflow-y: hidden;
}

/* 内容容器，用于居中显示内容 */
.portal-content > .welcome-section {
  width: 100%;
  max-width: 1200px;
  min-width: 600px; /* 最小宽度，确保在小屏幕上也有良好显示 */
  margin: 0 auto;
  padding-left: 20px;
  padding-right: 20px;
  box-sizing: border-box;
}

.portal-content > .chat-history-section {
  width: 100%;
  max-width: 900px; /* 与输入区域同宽 */
  min-width: 500px; /* 最小宽度，与输入区域保持一致 */
  margin: 0 auto;
  padding-left: 20px;
  padding-right: 20px;
  box-sizing: border-box;
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .portal-content > .welcome-section {
    min-width: 500px;
    max-width: 900px;
  }
  
  .portal-content > .chat-history-section {
    min-width: 400px;
    max-width: 700px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .portal-content > .welcome-section,
  .portal-content > .chat-history-section {
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    width: 100%;
    padding-left: 16px;
    padding-right: 16px;
  }
}

.portal-content.content-header-collapsed {
  padding-top: 40px !important; /* 顶部收起时不需要留出导航栏空间 */
}

/* 顶部系统图标（在 padding-top 位置） */
/* 欢迎界面 */
.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 40px 20px !important; /* 覆盖父容器的padding设置 */
  width: 100%;
  max-width: 1200px;
  min-width: 600px; /* 最小宽度，确保在小屏幕上也有良好显示 */
  margin: 0 auto;
  box-sizing: border-box;
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .welcome-section {
    min-width: 500px;
    max-width: 900px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .welcome-section {
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    padding: 40px 16px !important;
  }
}

/* 页面切换标签 */
.view-tabs {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-bottom: 32px;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  color: var(--el-text-color-regular, #606266);
  font-size: 14px;
  background: transparent;
  border: 1px solid transparent;
}

.tab-item:hover {
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-color-primary, #409eff);
}

.tab-item.active {
  background: var(--el-color-primary, #409eff);
  color: #ffffff;
  border-color: var(--el-color-primary, #409eff);
}

.tab-item .el-icon {
  font-size: 16px;
}

/* 视图内容区域 */
.view-content {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 500px; /* 固定最小高度，避免切换时跳动 */
  justify-content: flex-start;
}

.assistant-identity {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
  margin-top: 0;
  color: #000000;
  font-size: 20px;
  position: relative;
  height: 32px; /* 固定高度，与 feature-title 保持一致 */
}

.assistant-icon {
  font-size: 24px;
  position: relative;
  flex-shrink: 0;
  color: var(--el-color-primary, #409eff); /* 与快捷入口图标颜色一致 */
}

.assistant-name {
  font-weight: 500;
  font-size: 20px;
}

.welcome-message {
  font-size: 16px;
  line-height: 1.8;
  color: var(--el-text-color-primary, #303133);
  text-align: center;
  margin-bottom: 32px;
  max-width: 600px;
  padding: 0 20px;
  font-weight: 400;
}

.welcome-tips {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
  flex-wrap: wrap;
}

.tip-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: var(--el-text-color-secondary, #909399);
  padding: 6px 12px;
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-radius: 6px;
  transition: all 0.3s ease;
}

.tip-item:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}

.tip-symbol {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: var(--el-color-primary, #409eff);
  color: #ffffff;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  font-family: 'Courier New', monospace;
}

/* 确保智能对话视图内容也保持固定高度 */
.view-content .assistant-identity {
  margin-top: 0;
}

.suggested-prompts {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  max-width: 500px;
}

.prompt-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  background: var(--el-bg-color, #ffffff);
  border-radius: 10px;
  transition: all 0.3s ease;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.prompt-item-content {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  cursor: pointer;
  min-width: 0;
}

.prompt-item:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
  border-color: var(--el-color-primary, #409eff);
  transform: translateX(4px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.prompt-bullet {
  color: var(--el-color-primary, #409eff);
  font-weight: bold;
}

.prompt-text {
  color: var(--el-text-color-regular, #606266);
  font-size: 14px;
  font-weight: 400;
  transition: color 0.3s ease;
}

.prompt-item:hover .prompt-text {
  color: var(--el-color-primary, #409eff);
}

.prompt-continue-btn {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.3s ease, transform 0.3s ease;
  transform: translateX(-4px);
}

.prompt-item:hover .prompt-continue-btn {
  opacity: 1;
  transform: translateX(0);
}

.prompt-continue-btn:hover {
  transform: translateX(2px);
}

/* 系统功能入口区域 */
.feature-entries {
  width: 100%;
  max-width: 800px;
  margin-top: -40px; /* 向上移动 */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  min-height: 500px; /* 与 view-content 保持一致的高度 */
  padding-top: 20px;
}

.feature-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 24px;
  margin-top: 0;
  height: 32px; /* 固定高度，与 assistant-identity 保持一致 */
}

.feature-title-icon {
  font-size: 24px;
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}

.feature-title-text {
  font-size: 20px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 32px 16px;
  width: 100%;
  justify-items: center;
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s ease;
  gap: 12px;
}

.feature-item:hover {
  transform: translateY(-4px);
}

.feature-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--el-bg-color, #ffffff);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--el-border-color-lighter, #e4e7ed);
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px var(--el-box-shadow-light, rgba(0, 0, 0, 0.08));
  position: relative;
}

/* 优化图标显示 - 添加渐变背景效果 */
.feature-circle::before {
  content: '';
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: radial-gradient(circle at center, rgba(64, 158, 255, 0.05) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.feature-item:hover .feature-circle::before {
  opacity: 1;
}

.feature-item:hover .feature-circle {
  background: var(--el-color-primary-light-9, #ecf5ff);
  border-color: var(--el-color-primary, #409eff);
  box-shadow: 0 4px 16px var(--el-box-shadow-base, rgba(64, 158, 255, 0.2));
  transform: scale(1.05);
}

.feature-icon {
  font-size: 36px;
  color: var(--el-color-primary, #409eff);
  transition: transform 0.3s ease;
  position: relative;
  z-index: 1;
  filter: drop-shadow(0 1px 2px rgba(64, 158, 255, 0.2)); /* 图标阴影效果 */
}

.feature-item:hover .feature-icon {
  transform: scale(1.1);
}

.feature-name {
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  font-weight: 500;
  transition: color 0.3s ease;
  text-align: center;
}

.feature-item:hover .feature-name {
  color: var(--el-color-primary, #409eff);
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .feature-grid {
    gap: 28px 12px;
  }
  
  .feature-circle {
    width: 70px;
    height: 70px;
  }
  
  .feature-icon {
    font-size: 32px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .feature-entries {
    margin-top: 32px;
  }
  
  .feature-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 24px 12px;
  }
  
  .feature-circle {
    width: 64px;
    height: 64px;
  }
  
  .feature-icon {
    font-size: 28px;
  }
  
  .feature-name {
    font-size: 13px;
  }
}

.input-section {
  width: 100%;
  max-width: 900px; /* 输入区域宽度，比问答区域窄一些 */
  min-width: 500px; /* 最小宽度，确保在小屏幕上也有良好显示 */
  margin: 0 auto 20px;
  position: sticky;
  bottom: 0;
  background: var(--el-bg-color-page, #f5f7fa);
  padding: 16px 20px; /* 添加左右内边距，与问答区域保持一致 */
  z-index: 10;
  box-sizing: border-box;
  transition: background-color 0.3s ease, backdrop-filter 0.3s ease;
}

/* 当内容溢出时，输入框变为半透明 */
.input-section.transparent {
  background: rgba(245, 247, 250, 0.3);
  backdrop-filter: blur(8px) saturate(180%);
  -webkit-backdrop-filter: blur(8px) saturate(180%);
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .input-section {
    min-width: 400px;
    max-width: 700px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .input-section {
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    width: 100%;
    padding: 16px;
  }
}

.input-wrapper {
  position: relative;
  background: var(--el-bg-color, #ffffff);
  border-radius: 16px;
  box-shadow: 0 2px 12px var(--el-box-shadow-light, rgba(0, 0, 0, 0.08));
  padding: 16px;
  transition: box-shadow 0.3s, background-color 0.3s ease;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  margin-bottom: 8px;
}

/* 当内容溢出时，输入框背景也变为半透明 */
.input-section.transparent .input-wrapper {
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(8px) saturate(180%);
  -webkit-backdrop-filter: blur(8px) saturate(180%);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  border-color: rgba(228, 231, 237, 0.5);
}

.input-wrapper:focus-within {
  box-shadow: 0 4px 20px var(--el-box-shadow-base, rgba(0, 0, 0, 0.12));
  border-color: var(--el-color-primary, #409eff);
}

.input-controls-float {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: var(--el-bg-color, #ffffff);
  border-radius: 12px;
  box-shadow: 0 1px 4px var(--el-box-shadow-light, rgba(0, 0, 0, 0.06));
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  transition: background-color 0.3s ease, backdrop-filter 0.3s ease;
}

/* 当内容溢出时，控制区域背景也变为半透明 */
.input-section.transparent .input-controls-float {
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(8px) saturate(180%);
  -webkit-backdrop-filter: blur(8px) saturate(180%);
  border-color: rgba(228, 231, 237, 0.5);
}

.input-left-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.control-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  user-select: none;
}

.control-item:hover {
  background-color: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-color-primary, #409eff);
}

.control-item .arrow {
  font-size: 12px;
  margin-left: 2px;
}

.control-item.kb-selected,
.control-item.mode-selected {
  background-color: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}

/* 知识库@提及列表样式 */
.kb-mention-list {
  position: absolute;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 8px;
  box-shadow: 0 4px 16px var(--el-box-shadow-base, rgba(0, 0, 0, 0.12));
  z-index: 1000;
  max-height: 240px;
  overflow-y: auto;
  overflow-x: hidden;
  bottom: 0;
  width: 280px;
  min-width: 280px;
}

.kb-mention-empty {
  padding: 24px 20px;
  text-align: center;
  color: var(--el-text-color-placeholder, #909399);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.kb-mention-empty .el-icon {
  font-size: 28px;
  color: var(--el-text-color-placeholder, #c0c4cc);
}

.kb-mention-empty div {
  font-size: 13px;
  line-height: 1.5;
}

.kb-mention-items {
  padding: 4px 0;
}

.kb-mention-item {
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  border-bottom: 1px solid var(--el-border-color-lighter, #f0f0f0);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kb-mention-item:first-child {
  border-top-left-radius: 8px;
  border-top-right-radius: 8px;
}

.kb-mention-item:last-child {
  border-bottom: none;
  border-bottom-left-radius: 8px;
  border-bottom-right-radius: 8px;
}

.kb-mention-item:hover,
.kb-mention-item-active {
  background-color: var(--el-color-primary-light-9, #ecf5ff);
  border-color: var(--el-color-primary-light-7, #b3d8ff);
}

.kb-mention-item-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.2s ease;
}

.kb-mention-item:hover .kb-mention-item-name,
.kb-mention-item-active .kb-mention-item-name {
  color: var(--el-color-primary, #409eff);
}

.kb-mention-item-docs {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  line-height: 1.4;
  display: flex;
  align-items: center;
  gap: 4px;
}

.kb-mention-item-docs::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background-color: var(--el-text-color-placeholder, #c0c4cc);
}

/* 知识库列表滚动条样式 */
.kb-mention-list::-webkit-scrollbar {
  width: 6px;
}

.kb-mention-list::-webkit-scrollbar-track {
  background: transparent;
}

.kb-mention-list::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
}

.kb-mention-list::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
}

/* 文档列表样式（复用知识库列表样式） */
.doc-mention-list {
  /* 使用相同的样式，可以添加特定样式覆盖 */
}

.portal-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  line-height: 1.5;
  resize: none;
  min-width: 0;
}

.portal-input :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 0;
  font-size: 16px;
  line-height: 1.5;
  resize: none;
  position: relative;
  z-index: 1;
}

.portal-input :deep(.el-textarea__inner):focus {
  border: none;
  box-shadow: none;
}

/* 输入框容器（标签作为输入内容的一部分，不单独占行或列） */
.input-container {
  position: relative;
  display: flex;
  flex: 1;
  min-width: 0;
}

/* 选中的知识库和文档标签（作为输入内容的一部分，浮动在输入框内第一行） */
.selected-mentions-inline {
  position: absolute;
  top: 0;
  left: 0;
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 0;
  margin: 0;
  z-index: 1;
  pointer-events: none;
  max-width: calc(100% - 16px);
  height: 1.5em;
  line-height: 1.5em;
}

.selected-mentions-inline .mention-tag-inline {
  pointer-events: auto;
}

/* 当有标签时，调整输入框的样式，让文本从标签后开始，但换行后从最左侧开始 */
.portal-input.has-mentions :deep(.el-textarea__inner) {
  padding-left: var(--mention-width, 0px) !important;
  text-indent: 0;
  padding-top: 0;
  box-sizing: border-box;
  position: relative;
}

.mention-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.mention-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.mention-icon {
  font-size: 14px;
}

/* 内联标签样式（作为输入内容的一部分） */
.mention-tag-inline {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 6px;
  transition: all 0.2s ease;
  margin: 0;
}

.mention-tag-inline:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.input-right-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.send-button {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.attachments-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding: 8px;
  background: var(--el-bg-color, #ffffff);
  border-radius: 8px;
  box-shadow: 0 1px 4px var(--el-box-shadow-light, rgba(0, 0, 0, 0.06));
  transition: background-color 0.3s ease, backdrop-filter 0.3s ease;
}

/* 当内容溢出时，附件预览区域背景也变为半透明 */
.input-section.transparent .attachments-preview {
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(8px) saturate(180%);
  -webkit-backdrop-filter: blur(8px) saturate(180%);
}

.attachment-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 4px;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  max-width: 100%;
}

.attachment-preview-image {
  max-width: 200px !important;
  max-height: 200px !important;
  width: auto !important;
  height: auto !important;
  border-radius: 4px;
  cursor: pointer;
}

.attachment-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.attachment-name {
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.attachment-size {
  font-size: 11px;
  color: var(--el-text-color-placeholder, #909399);
}

.features-section {
  display: flex;
  justify-content: center;
  gap: 40px;
  flex-wrap: wrap;
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: transform 0.2s;
  padding: 12px;
  border-radius: 12px;
}

.feature-item:hover {
  transform: translateY(-4px);
  background-color: var(--el-fill-color-lighter, rgba(255, 255, 255, 0.6));
}

.feature-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--el-bg-color, #ffffff);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px var(--el-box-shadow-light, rgba(0, 0, 0, 0.1));
  font-size: 24px;
  color: var(--el-color-primary, #409eff);
  transition: all 0.3s;
}

.feature-item:hover .feature-icon {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
  box-shadow: 0 4px 12px var(--el-box-shadow-base, rgba(64, 158, 255, 0.2));
}

.feature-label {
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  font-weight: 500;
  transition: color 0.3s;
}

.feature-item:hover .feature-label {
  color: var(--el-color-primary, #409eff);
}

.chat-history-section {
  width: 100%;
  max-width: 900px; /* 与输入区域同宽 */
  min-width: 500px; /* 最小宽度，与输入区域保持一致 */
  margin: 0 auto;
  flex: 1;
  overflow: visible; /* 移除滚动条，由父容器portal-content统一处理 */
  padding: 20px 20px !important; /* 覆盖父容器的padding设置 */
  box-sizing: border-box;
  transition: height 0.3s ease;
}

/* 当内容溢出时，固定高度到输入框底部 */
.chat-history-section.content-overflow {
  flex: 0 0 auto;
  overflow-y: auto;
  overflow-x: hidden;
  max-height: calc(100vh - 280px); /* 减去输入框、header和padding的高度 */
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .chat-history-section {
    min-width: 400px;
    max-width: 700px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .chat-history-section {
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    padding: 20px 16px !important;
  }
}

/* 底部免责声明 */
.portal-footer {
  text-align: center;
  padding: 12px 20px;
  background: var(--el-bg-color, #ffffff);
  border-top: 1px solid var(--el-border-color-lighter, #e4e7ed);
  position: sticky;
  bottom: 0;
  z-index: 10;
}

.footer-text {
  font-size: 12px;
  color: var(--el-text-color-placeholder, #909399);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .logo {
    font-size: 36px;
  }

  .input-section {
    max-width: 100%;
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    margin-bottom: 60px;
    padding: 16px;
  }

  .input-wrapper {
    padding: 10px 12px;
  }

  .control-item {
    padding: 4px 8px;
    font-size: 12px;
  }

  .control-item span {
    display: none;
  }

  .features-section {
    gap: 20px;
  }

  .feature-icon {
    width: 48px;
    height: 48px;
    font-size: 20px;
  }

  .feature-label {
    font-size: 12px;
  }
}
</style>

