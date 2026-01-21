<template>
  <div class="portal-container">
    <!-- 顶部导航栏 -->
    <AppHeader v-model="isHeaderCollapsed" @command="handleCommand" />

    <!-- 主要内容区域 -->
    <div class="portal-content" :class="{ 'content-header-collapsed': isHeaderCollapsed, 'no-scroll': isContentOverflow }">
      <!-- 初始欢迎界面（无对话时显示） -->
      <div v-if="chatHistory.length === 0" class="welcome-section">
        <!-- 页面切换标签 -->
        <div class="view-tabs">
          <div 
            class="tab-item" 
            :class="{ active: currentView === 'welcome', disabled: isViewSwitching }"
            @click="switchView('welcome')"
            role="button"
            tabindex="0"
            @keydown.enter="switchView('welcome')"
            @keydown.space.prevent="switchView('welcome')"
          >
            <el-icon><ChatLineRound /></el-icon>
            <span>智能对话</span>
          </div>
          <div 
            class="tab-item" 
            :class="{ active: currentView === 'features', disabled: isViewSwitching }"
            @click="switchView('features')"
            role="button"
            tabindex="0"
            @keydown.enter="switchView('features')"
            @keydown.space.prevent="switchView('features')"
          >
            <el-icon><Grid /></el-icon>
            <span>快捷入口</span>
          </div>
        </div>

        <!-- 欢迎视图 -->
        <transition name="fade-slide" mode="out-in">
          <div v-if="currentView === 'welcome'" key="welcome" class="view-content welcome-view">
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
                      v-memo="[conversation.id, conversation.title, conversation.updateTime, selectedConversationId === conversation.id]"
                      :class="['conversation-card', { 'conversation-selected': selectedConversationId === conversation.id }]"
                      @click="handleConversationClick(conversation)"
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
                      v-memo="[question.text, question.icon, index]"
                      class="question-card"
                      @click="handlePromptClick(question.text)"
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

          <!-- 系统功能视图 -->
          <div v-else-if="currentView === 'features'" key="features" class="view-content">
            <div class="feature-entries">
              <div class="feature-header">
                <div class="feature-title">
                  <el-icon class="feature-title-icon"><Service /></el-icon>
                  <span class="feature-title-text">快捷入口</span>
                </div>
                <p class="feature-subtitle">选择功能模块，快速开始您的工作</p>
              </div>
              
              <div class="feature-categories">
                <!-- AI 应用类 -->
                <div class="feature-category">
                  <h3 class="category-title">
                    <el-icon class="category-icon"><Grid /></el-icon>
                    <span>AI 应用</span>
                  </h3>
                  <div class="feature-cards">
                    <div 
                      class="feature-card" 
                      :class="{ 'navigating': isNavigatingToFeature }"
                      @click="handleFeatureClick('apps')"
                      role="button"
                      tabindex="0"
                      @keydown.enter="handleFeatureClick('apps')"
                    >
                      <div class="feature-card-icon">
                        <el-icon class="feature-icon"><Grid /></el-icon>
                      </div>
                      <div class="feature-card-content">
                        <h4 class="feature-card-title">智能应用</h4>
                        <p class="feature-card-desc">创建和管理AI应用，构建智能工作流</p>
                      </div>
                      <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
                    </div>
                    
                    <div 
                      class="feature-card" 
                      :class="{ 'navigating': isNavigatingToFeature }"
                      @click="handleFeatureClick('ai-drawio')"
                      role="button"
                      tabindex="0"
                      @keydown.enter="handleFeatureClick('ai-drawio')"
                    >
                      <div class="feature-card-icon">
                        <el-icon class="feature-icon"><Picture /></el-icon>
                      </div>
                      <div class="feature-card-content">
                        <h4 class="feature-card-title">智能框图</h4>
                        <p class="feature-card-desc">AI驱动的思维导图和流程图生成</p>
                      </div>
                      <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
                    </div>
                  </div>
                </div>

                <!-- 知识管理类 -->
                <div class="feature-category">
                  <h3 class="category-title">
                    <el-icon class="category-icon"><Folder /></el-icon>
                    <span>知识管理</span>
                  </h3>
                  <div class="feature-cards">
                    <div 
                      class="feature-card" 
                      :class="{ 'navigating': isNavigatingToFeature }"
                      @click="handleFeatureClick('kb-qa')"
                      role="button"
                      tabindex="0"
                      @keydown.enter="handleFeatureClick('kb-qa')"
                    >
                      <div class="feature-card-icon">
                        <el-icon class="feature-icon"><Search /></el-icon>
                      </div>
                      <div class="feature-card-content">
                        <h4 class="feature-card-title">知识检索</h4>
                        <p class="feature-card-desc">基于知识库的智能问答和检索</p>
                      </div>
                      <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
                    </div>
                    
                    <div 
                      class="feature-card" 
                      :class="{ 'navigating': isNavigatingToFeature }"
                      @click="handleFeatureClick('knowledge-base')"
                      role="button"
                      tabindex="0"
                      @keydown.enter="handleFeatureClick('knowledge-base')"
                    >
                      <div class="feature-card-icon">
                        <el-icon class="feature-icon"><Folder /></el-icon>
                      </div>
                      <div class="feature-card-content">
                        <h4 class="feature-card-title">知识管理</h4>
                        <p class="feature-card-desc">管理知识库和文档，构建知识体系</p>
                      </div>
                      <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
                    </div>
                    
                    <div 
                      class="feature-card" 
                      :class="{ 'navigating': isNavigatingToFeature }"
                      @click="handleFeatureClick('document')"
                      role="button"
                      tabindex="0"
                      @keydown.enter="handleFeatureClick('document')"
                    >
                      <div class="feature-card-icon">
                        <el-icon class="feature-icon"><Document /></el-icon>
                      </div>
                      <div class="feature-card-content">
                        <h4 class="feature-card-title">文档解读</h4>
                        <p class="feature-card-desc">上传文档，AI智能解读和分析</p>
                      </div>
                      <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
                    </div>
                  </div>
                </div>

                <!-- 会话管理类 -->
                <div class="feature-category">
                  <h3 class="category-title">
                    <el-icon class="category-icon"><Clock /></el-icon>
                    <span>会话管理</span>
                  </h3>
                  <div class="feature-cards">
                    <div 
                      class="feature-card" 
                      :class="{ 'navigating': isNavigatingToFeature }"
                      @click="handleFeatureClick('chat-history')"
                      role="button"
                      tabindex="0"
                      @keydown.enter="handleFeatureClick('chat-history')"
                    >
                      <div class="feature-card-icon">
                        <el-icon class="feature-icon"><Clock /></el-icon>
                      </div>
                      <div class="feature-card-content">
                        <h4 class="feature-card-title">会话历史</h4>
                        <p class="feature-card-desc">查看和管理历史对话记录</p>
                      </div>
                      <el-icon class="feature-card-arrow"><ArrowRight /></el-icon>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </transition>
      </div>

      <!-- 对话历史区域（有对话时显示） -->
      <div 
        v-if="chatHistory.length > 0" 
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
    <div 
      v-if="currentView !== 'features'"
      class="input-section" 
      :class="{ 'transparent': isContentOverflow }" 
      ref="inputSectionRef"
    >
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
              v-memo="[kb.id, kb.name, kb.documentCount, selectedKbIndex === index]"
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
              v-memo="[doc.id, doc.originalFileName, doc.fileName, doc.name, doc.fileType, selectedDocIndex === index]"
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

          <!-- 联网搜索开关（仅在普通对话模式下显示，即没有选择知识库和文档时） -->
          <div v-if="!selectedKnowledgeBase && !selectedDocument" class="control-item" style="display: flex; align-items: center; gap: 8px; padding: 0 12px;">
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
            :disabled="!canSend"
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
          :key="`${file.name}-${file.size}-${index}`"
          v-memo="[file.name, file.size, index, sending]"
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
      <div class="footer-content">
        <el-icon class="footer-icon"><InfoFilled /></el-icon>
        <span class="footer-text">内容由AI生成，仅供参考</span>
      </div>
    </div>

    <!-- 修改密码对话框 -->
    <ChangePasswordDialog
      v-model="showChangePasswordDialog"
      @success="handlePasswordChangeSuccess"
    />

    <UserMemoryDialog v-model="showUserMemoryDialog" />
  </div>
</template>

<script setup>
import { ref, shallowRef, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
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
  Folder,
  InfoFilled,
  Star,
  QuestionFilled,
  ChatDotRound
} from '@element-plus/icons-vue'
import { chat, chatStream, getMyConversations, getConversationMessages } from '@/api/chat'
import { getAvailableQAModels, getAvailableQAModelsForRAG } from '@/api/model'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { knowledgeBaseQAStream } from '@/api/knowledgeBaseQA'
import { getDocumentList } from '@/api/documentReader'
import { documentQAStream } from '@/api/documentReader'
import MessageList from '@/components/chat/MessageList.vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import UserMemoryDialog from '@/components/UserMemoryDialog.vue'
import AppHeader from '@/components/AppHeader.vue'

// Utility: Simple debounce function
const debounce = (fn, delay) => {
  let timeoutId
  return function(...args) {
    clearTimeout(timeoutId)
    timeoutId = setTimeout(() => fn.apply(this, args), delay)
  }
}

const router = useRouter()

// 基础状态
const question = ref('')
const sending = ref(false)
// 使用 shallowRef 优化大型数组性能（避免深度响应式监听）
const chatHistory = shallowRef([])
const messageListRef = ref(null)
const conversationId = ref(null)
const availableModels = ref([])
const selectedModelId = ref(null)
const selectedFiles = ref([])
const isInputFocused = ref(false)
const uploadRef = ref(null)
const currentDate = ref('')
const currentTime = ref('')
// 使用 shallowRef 优化大型数组性能
const availableKnowledgeBases = shallowRef([])
const selectedKnowledgeBaseId = ref(null)
const selectedKnowledgeBase = ref(null)
// conversationMode 现在是计算属性，根据选择自动判断（见下方定义）
const selectedKnowledgeBaseName = ref('')
const showChangePasswordDialog = ref(false)
const showUserMemoryDialog = ref(false)
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
// 使用 shallowRef 优化大型数组性能
const availableDocuments = shallowRef([])
const selectedDocumentId = ref(null) // 选中的文档ID
const selectedDocument = ref(null) // 选中的文档对象
const loadingDocuments = ref(false) // 是否正在加载文档列表
const mentionWidth = ref(0) // 标签的宽度
const mentionContainerRef = ref(null) // 标签容器的引用
// 使用 shallowRef 优化大型数组性能
const recentConversations = shallowRef([]) // 最近会话历史（展开状态3条，收起状态4条）
// ResizeObserver 实例，用于清理
const chatHistoryResizeObserver = ref(null)
const mentionResizeObserver = ref(null)
// 定时器 ID，用于清理
const dateTimeIntervalId = ref(null)
const selectedConversationId = ref(null) // 选中的会话ID
const loadingConversations = ref(false) // 是否正在加载会话列表
const retryCount = ref(0) // 重试次数
const maxRetries = 3 // 最大重试次数
const isViewSwitching = ref(false) // 视图切换动画状态
const isNavigatingToFeature = ref(false) // 正在导航到功能页面

// 建议问题列表
const suggestedQuestions = ref([
  { text: '最近有什么有趣的事情吗', icon: 'QuestionFilled' },
  { text: '帮我写一份工作总结', icon: 'Document' },
  { text: '解释一下人工智能的基本概念', icon: 'ChatDotRound' },
  { text: '推荐一些提高效率的方法', icon: 'Star' }
])

// 根据顶部状态显示不同数量的建议问题
const displayedQuestions = computed(() => {
  // 展开状态显示3条，收起状态显示4条
  return isHeaderCollapsed.value 
    ? suggestedQuestions.value.slice(0, 4) 
    : suggestedQuestions.value.slice(0, 3)
})

// Memoized computed properties for better performance
const hasActiveSelection = computed(() => selectedKnowledgeBase.value || selectedDocument.value)

const selectedModelName = computed(() => {
  if (!selectedModelId.value) return 'DS V3.2'
  const model = availableModels.value.find(m => m.id === selectedModelId.value)
  return model ? model.name : 'DS V3.2'
})

const hasMeaningfulQuestion = computed(() => {
  const trimmed = question.value.trim()
  if (!trimmed) return false
  return !/^[@/]+$/.test(trimmed)
})

const canSend = computed(() => {
  if (sending.value) return false
  if (selectedKnowledgeBaseId.value || selectedDocumentId.value) {
    return hasMeaningfulQuestion.value
  }
  return hasMeaningfulQuestion.value || selectedFiles.value.length > 0
})

// Utility function to parse response data with various formats
const parseResponseData = (response) => {
  if (!response) return []
  
  // Try different possible data structures
  if (Array.isArray(response)) return response
  if (response.content && Array.isArray(response.content)) return response.content
  if (response.list && Array.isArray(response.list)) return response.list
  
  // Check nested data property
  if (response.data) {
    const data = response.data
    if (Array.isArray(data)) return data
    if (data.content && Array.isArray(data.content)) return data.content
    if (data.list && Array.isArray(data.list)) return data.list
  }
  
  return []
}

// 统一的错误处理函数 - 优化：提取公共错误处理逻辑
const handleError = (error, context = '操作', showMessage = true) => {
  const errorMessage = error?.message || error?.toString() || '未知错误'
  const fullMessage = `${context}失败：${errorMessage}`
  
  // 只在开发环境输出详细错误
  if (process.env.NODE_ENV === 'development') {
    console.error(`[${context}]`, error)
  }
  
  if (showMessage) {
    ElMessage.error(fullMessage)
  }
  
  return fullMessage
}

// 优化：统一的流式响应错误处理
const handleStreamError = (error, aiMessageIndex, hasContent = false) => {
  if (!chatHistory.value[aiMessageIndex]) return
  
  const message = chatHistory.value[aiMessageIndex]
  message.isLoading = false
  
  if (hasContent && message.content) {
    message.content += '\n\n⚠️ 连接中断，部分内容可能不完整。'
  } else {
    message.content = '抱歉，连接已断开，请重试。'
  }
  
  // 只在开发环境输出详细错误
  if (process.env.NODE_ENV === 'development') {
    console.error('流式响应处理失败', error)
  }
}

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
  } else if (command === 'memory') {
    showUserMemoryDialog.value = true
  }
  // logout 命令由 AppHeader 组件内部处理
}

// 处理密码修改成功
const handlePasswordChangeSuccess = () => {
  ElMessage.success('密码修改成功')
  showChangePasswordDialog.value = false
}

// Smooth view switching with animation
const switchView = async (view) => {
  if (currentView.value === view || isViewSwitching.value) return
  
  isViewSwitching.value = true
  currentView.value = view
  
  // Reset switching state after animation completes
  await nextTick()
  setTimeout(() => {
    isViewSwitching.value = false
  }, 300) // Match CSS transition duration
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
const handleInputChange = debounce(() => {
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
}, 100)

// 获取光标位置
const getCursorPosition = () => {
  if (!inputRef.value) return question.value.length
  const textarea = inputRef.value.$el?.querySelector('textarea')
  if (!textarea) return question.value.length
  return textarea.selectionStart || question.value.length
}

// 提取公共的列表位置更新逻辑（减少代码重复）
const updateMentionListPosition = (listStyleRef, isDocList = false) => {
  nextTick(() => {
    if (!inputWrapperRef.value || !inputRef.value) return
    
    const textarea = inputRef.value.$el?.querySelector('textarea')
    if (!textarea) return

    const wrapperRect = inputWrapperRef.value.getBoundingClientRect()
    const textareaRect = textarea.getBoundingClientRect()
    
    // 计算位置，将列表显示在输入框上方，使用固定宽度
    listStyleRef.value = {
      bottom: `${wrapperRect.bottom - textareaRect.top + 8}px`,
      left: '16px',
      width: '280px',
      top: 'auto'
    }
  })
}

// 更新知识库列表位置 - 优化：使用公共函数
const updateKbListPosition = () => updateMentionListPosition(kbListStyle, false)

// 更新文档列表位置 - 优化：使用公共函数
const updateDocListPosition = () => updateMentionListPosition(docListStyle, true)

// 选择文档
const selectDocument = (doc) => {
  if (!doc) return
  
  const text = question.value
  const beforeSlash = text.substring(0, slashSymbolIndex.value)
  const afterCursor = text.substring(getCursorPosition())
  
  // 设置选中的文档
  selectedDocumentId.value = doc.id
  selectedDocument.value = doc
  
  // 清除知识库选择（文档和知识库不能同时选择）
  if (selectedKnowledgeBaseId.value) {
    selectedKnowledgeBaseId.value = null
    selectedKnowledgeBase.value = null
    selectedKnowledgeBaseName.value = ''
  }
  
  // 重新加载模型列表
  loadAvailableModels()
  
  const docName = doc.originalFileName || doc.fileName || doc.name || '未命名文档'
  ElMessage.success(`已选择文档：${docName}`)
  
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
  
  // 重新加载模型列表
  loadAvailableModels()
  
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
    
    availableDocuments.value = parseResponseData(response)
  } catch (error) {
    handleError(error, '加载文档列表', false) // 静默失败，不显示错误消息
    availableDocuments.value = []
  } finally {
    loadingDocuments.value = false
  }
}

// 处理键盘事件 - Optimized with early returns
const handleKeydown = (e) => {
  // Handle knowledge base list
  if (showKbList.value) {
    e.preventDefault()
    
    switch(e.key) {
      case 'ArrowDown':
        selectedKbIndex.value = Math.min(selectedKbIndex.value + 1, filteredKnowledgeBases.value.length - 1)
        break
      case 'ArrowUp':
        selectedKbIndex.value = Math.max(selectedKbIndex.value - 1, 0)
        break
      case 'Enter':
        if (!e.ctrlKey && !e.shiftKey && filteredKnowledgeBases.value[selectedKbIndex.value]) {
          selectKnowledgeBase(filteredKnowledgeBases.value[selectedKbIndex.value])
        }
        break
      case 'Escape':
        showKbList.value = false
        break
    }
    return
  }
  
  // Handle document list
  if (showDocList.value) {
    e.preventDefault()
    
    switch(e.key) {
      case 'ArrowDown':
        selectedDocIndex.value = Math.min(selectedDocIndex.value + 1, filteredDocuments.value.length - 1)
        break
      case 'ArrowUp':
        selectedDocIndex.value = Math.max(selectedDocIndex.value - 1, 0)
        break
      case 'Enter':
        if (!e.ctrlKey && !e.shiftKey && filteredDocuments.value[selectedDocIndex.value]) {
          selectDocument(filteredDocuments.value[selectedDocIndex.value])
        }
        break
      case 'Escape':
        showDocList.value = false
        break
    }
  }
}

// 获取光标位置的缓存函数（用于计算属性，避免重复计算）
const getCursorPositionCached = () => {
  if (!inputRef.value) return question.value.length
  const textarea = inputRef.value.$el?.querySelector('textarea')
  if (!textarea) return question.value.length
  return textarea.selectionStart || question.value.length
}

// 过滤后的知识库列表（最多5个）- 优化：使用缓存和早期返回
const filteredKnowledgeBases = computed(() => {
  if (!showKbList.value || atSymbolIndex.value < 0 || availableKnowledgeBases.value.length === 0) {
    return []
  }
  
  const cursorPos = getCursorPositionCached()
  const searchText = question.value.substring(atSymbolIndex.value + 1, cursorPos).toLowerCase().trim()
  
  if (!searchText) {
    return availableKnowledgeBases.value.slice(0, 5)
  }
  
  // 使用更高效的过滤方式
  const filtered = []
  for (const kb of availableKnowledgeBases.value) {
    if (kb.name?.toLowerCase().includes(searchText)) {
      filtered.push(kb)
      if (filtered.length >= 5) break
    }
  }
  
  return filtered
})

// 过滤后的文档列表（最多5个）- 优化：使用缓存和早期返回
const filteredDocuments = computed(() => {
  if (!showDocList.value || slashSymbolIndex.value < 0 || availableDocuments.value.length === 0) {
    return []
  }
  
  const cursorPos = getCursorPositionCached()
  const searchText = question.value.substring(slashSymbolIndex.value + 1, cursorPos).toLowerCase().trim()
  
  if (!searchText) {
    return availableDocuments.value.slice(0, 5)
  }
  
  // 使用更高效的过滤方式
  const filtered = []
  for (const doc of availableDocuments.value) {
    const fileName = doc.originalFileName || doc.fileName || doc.name || ''
    if (fileName.toLowerCase().includes(searchText)) {
      filtered.push(doc)
      if (filtered.length >= 5) break
    }
  }
  
  return filtered
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
  
  // 清除文档选择（知识库和文档不能同时选择）
  if (selectedDocumentId.value) {
    selectedDocumentId.value = null
    selectedDocument.value = null
  }
  
  // 清空输入框中的@知识库名称，只保留其他内容
  question.value = beforeAt + afterCursor.trim()
  
  // 重新加载模型列表
  loadAvailableModels()
  
  ElMessage.success(`已选择知识库：${kb.name}`)
  
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

// 自动判断对话模式（根据选择的知识库和文档）
const conversationMode = computed(() => {
  if (selectedDocumentId.value && selectedDocument.value) {
    return 'document'
  } else if (selectedKnowledgeBaseId.value && selectedKnowledgeBase.value) {
    return 'rag'
  } else {
    return 'chat'
  }
})

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

// 处理功能点击 - Enhanced with loading state and visual feedback
const handleFeatureClick = async (feature) => {
  if (isNavigatingToFeature.value) return
  
  const featureNames = {
    'kb-qa': '知识检索',
    'document': '文档解读',
    'apps': '智能应用',
    'ai-drawio': '智能框图',
    'chat-history': '会话历史',
    'knowledge-base': '知识管理'
  }
  
  try {
    isNavigatingToFeature.value = true
    
    const userInfo = getUserInfo()
    const basePath = userInfo?.role === 1 ? '/admin' : '/user'
    
    let targetPath = ''
    switch (feature) {
      case 'kb-qa':
        targetPath = `${basePath}/kb-qa`
        break
      case 'document':
        targetPath = `${basePath}/document-reader`
        break
      case 'apps':
        targetPath = `${basePath}/apps`
        break
      case 'ai-drawio':
        targetPath = `${basePath}/ai-drawio`
        break
      case 'chat-history':
        targetPath = `${basePath}/chat-history`
        break
      case 'knowledge-base':
        targetPath = `${basePath}/knowledge-base`
        break
      default:
        throw new Error('Unknown feature')
    }
    
    // Show loading message
    const featureName = featureNames[feature] || '功能'
    ElMessage({
      message: `正在跳转到${featureName}...`,
      type: 'info',
      duration: 1000
    })
    
    // Navigate with slight delay for better UX
    await new Promise(resolve => setTimeout(resolve, 200))
    await router.push(targetPath)
    
  } catch (error) {
    console.error('Navigation failed:', error)
    ElMessage.error('页面跳转失败，请重试')
  } finally {
    isNavigatingToFeature.value = false
  }
}

// 发送消息
const handleSend = async () => {
  if (showKbList.value || showDocList.value) {
    return
  }
  if (!canSend.value) {
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
    handleError(error, '发送消息', true)
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
    const hasContent = chatHistory.value[aiMessageIndex]?.content?.length > 0
    handleStreamError(error, aiMessageIndex, hasContent)
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
    const hasContent = chatHistory.value[aiMessageIndex]?.content?.length > 0
    handleStreamError(error, aiMessageIndex, hasContent)
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
  
  const handleStreamErrorWithRetry = async (error, attempt = 0) => {
    // Check if we should retry
    if (attempt < maxRetries && (!error.message || !error.message.includes('disconnected'))) {
      if (process.env.NODE_ENV === 'development') {
        console.log(`Retrying... Attempt ${attempt + 1}/${maxRetries}`)
      }
      retryCount.value = attempt + 1
      
      // Wait before retrying (exponential backoff)
      await new Promise(resolve => setTimeout(resolve, Math.min(1000 * Math.pow(2, attempt), 5000)))
      
      try {
        return await handleStreamResponse(question, requestConversationId, userId, history, aiMessageIndex, modelId, enableBrowserSearch, files)
      } catch (retryError) {
        return handleStreamErrorWithRetry(retryError, attempt + 1)
      }
    }
    
    // Max retries reached or non-retryable error
    retryCount.value = 0
    const hasContent = chatHistory.value[aiMessageIndex]?.content?.length > 0
    handleStreamError(error, aiMessageIndex, hasContent)
    throw error
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
    
    // Reset retry count on success
    retryCount.value = 0
    
    } catch (error) {
      return handleStreamErrorWithRetry(error, retryCount.value)
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
    handleError(error, '重新生成响应', true)
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

// 滚动到底部 - Optimized with requestAnimationFrame
const scrollToBottom = (force = false) => {
  if (!messageListRef.value?.$el) return
  
  requestAnimationFrame(() => {
    if (!messageListRef.value?.$el) return
    
    if (!force && !isNearBottom()) {
      return
    }
    
    const element = messageListRef.value.$el
    element.scrollTo({
      top: element.scrollHeight,
      behavior: force ? 'auto' : 'smooth'
    })
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
const handleRefresh = (showMessage = true) => {
  chatHistory.value = []
  conversationId.value = null
  question.value = ''
  selectedFiles.value = []
  isInputFocused.value = false
  
  // 清除知识库和文档选择
  selectedKnowledgeBaseId.value = null
  selectedKnowledgeBase.value = null
  selectedKnowledgeBaseName.value = ''
  selectedDocumentId.value = null
  selectedDocument.value = null
  
  // 关闭知识库和文档列表
  showKbList.value = false
  showDocList.value = false
  
  // 重新加载模型列表（conversationMode会自动根据选择判断）
  loadAvailableModels()
  
  // 根据参数决定是否显示消息
  if (showMessage) {
    ElMessage.success('会话已重置')
  }
}


// 格式化会话时间
const formatConversationTime = (timeStr) => {
  if (!timeStr) return ''
  
  try {
    const time = new Date(timeStr)
    const now = new Date()
    const diff = now - time
    const diffDays = Math.floor(diff / (1000 * 60 * 60 * 24))
    const diffHours = Math.floor(diff / (1000 * 60 * 60))
    const diffMinutes = Math.floor(diff / (1000 * 60))
    
    if (diffDays === 0) {
      if (diffHours === 0) {
        if (diffMinutes < 1) return '刚刚'
        return `${diffMinutes}分钟前`
      }
      return `${diffHours}小时前`
    } else if (diffDays === 1) {
      return '昨天'
    } else if (diffDays < 7) {
      return `${diffDays}天前`
    } else {
      return time.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
    }
  } catch (e) {
    return ''
  }
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

// 加载最近会话历史（根据状态加载3条或4条）
const loadRecentConversations = async () => {
  try {
    loadingConversations.value = true
    const userInfo = getUserInfo()
    if (!userInfo) {
      recentConversations.value = []
      return
    }

    // 根据收起状态决定加载数量：展开状态3条，收起状态4条
    const limit = isHeaderCollapsed.value ? 4 : 3
    const response = await getMyConversations(1, limit) // 获取第一页，根据状态加载3或4条
    const conversations = parseResponseData(response)
    
    // 取最近N条，不限类型
    recentConversations.value = conversations.slice(0, limit)
  } catch (error) {
    handleError(error, '加载最近会话', false) // 静默失败
    recentConversations.value = []
  } finally {
    loadingConversations.value = false
  }
}

// 处理会话点击 - Enhanced with better UX feedback
const handleConversationClick = async (conversation) => {
  if (!conversation || !conversation.id || loadingConversations.value) return
  
  // Prevent rapid clicks
  if (selectedConversationId.value === conversation.id && chatHistory.value.length > 0) {
    ElMessage.info('已在该会话中')
    return
  }
  
  try {
    selectedConversationId.value = conversation.id
    
    // Show loading feedback
    const loadingMsg = ElMessage({
      message: '正在加载会话...',
      type: 'info',
      duration: 0,
      customClass: 'conversation-loading-message'
    })
    
    await loadConversationMessages(conversation.id)
    
    // Close loading message
    loadingMsg.close()
    
    // Show success message with conversation title
    const title = conversation.title || '未命名会话'
    ElMessage.success(`已加载会话: ${title}`)
    
  } catch (error) {
    console.error('加载会话失败', error)
    selectedConversationId.value = null
  }
}

// 加载会话消息
const loadConversationMessages = async (convId) => {
  try {
    const response = await getConversationMessages(convId)
    const messages = parseResponseData(response)
    
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
    handleError(error, '加载会话消息', true)
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
    const knowledgeBases = parseResponseData(response)
    
    // 过滤出启用的知识库（status === 'active' 或 status === 1）
    availableKnowledgeBases.value = knowledgeBases.filter(kb => {
      const status = kb.status
      return status === 'active' || status === 1 || status === '1'
    })
    
    if (process.env.NODE_ENV === 'development') {
      console.log('加载知识库列表成功，数量:', availableKnowledgeBases.value.length)
    }
  } catch (error) {
    handleError(error, '加载知识库列表', true)
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
  loadAvailableModels()
  updateMentionWidth()
})

// 监听文档选择变化，重新加载模型
watch(selectedDocumentId, () => {
  loadAvailableModels()
  updateMentionWidth()
})


// 检测内容是否溢出
const checkContentOverflow = () => {
  nextTick(() => {
    if (!chatHistorySectionRef.value || !inputSectionRef.value) {
      isContentOverflow.value = false
      return
    }

    // 如果没有消息，不显示溢出状态
    if (chatHistory.value.length === 0) {
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

// 监听顶部收起状态变化，重新加载会话以匹配显示数量
watch(() => isHeaderCollapsed.value, () => {
  loadRecentConversations()
})

// 监听窗口大小变化
const handleResize = debounce(() => {
  checkContentOverflow()
  if (showKbList.value) {
    updateKbListPosition()
  }
}, 150)

// Global keyboard shortcuts for navigation
const handleGlobalKeydown = (e) => {
  // 如果正在发送消息，不处理快捷键
  if (sending.value) return
  
  // 检查是否在输入框中（textarea 或 contenteditable）
  const activeElement = document.activeElement
  const isInInput = activeElement && (
    activeElement.tagName === 'TEXTAREA' ||
    activeElement.tagName === 'INPUT' ||
    activeElement.isContentEditable ||
    activeElement.closest('.el-input__inner') ||
    activeElement.closest('.el-textarea__inner')
  )
  
  // Ctrl/Cmd + 1: Switch to intelligent Q&A view (智能对话)
  if ((e.ctrlKey || e.metaKey) && e.key === '1') {
    // 如果不在输入框中，或者输入框为空，才允许切换
    if (!isInInput || !question.value.trim()) {
      e.preventDefault()
      e.stopPropagation()
      
      // 如果当前不在欢迎视图，切换到欢迎视图
      if (currentView.value !== 'welcome') {
        switchView('welcome')
        ElMessage({
          message: '已切换到智能对话',
          type: 'info',
          duration: 1500
        })
      }
      
      // 如果输入框有内容，清空并聚焦
      if (question.value.trim()) {
        question.value = ''
      }
      
      // 聚焦输入框
      nextTick(() => {
        const inputElement = inputRef.value?.$el?.querySelector('textarea')
        if (inputElement) {
          inputElement.focus()
        }
      })
      return
    }
  }
  
  // Ctrl/Cmd + 2: Switch to quick entry view (快捷入口)
  if ((e.ctrlKey || e.metaKey) && e.key === '2') {
    // 如果不在输入框中，或者输入框为空，才允许切换
    if (!isInInput || !question.value.trim()) {
      e.preventDefault()
      e.stopPropagation()
      
      // 如果当前不在功能视图，切换到功能视图
      if (currentView.value !== 'features') {
        switchView('features')
        ElMessage({
          message: '已切换到快捷入口',
          type: 'info',
          duration: 1500
        })
      }
      return
    }
  }
  
  // Ctrl/Cmd + N: Start new conversation (新对话)
  if ((e.ctrlKey || e.metaKey) && (e.key === 'n' || e.key === 'N')) {
    e.preventDefault()
    e.stopPropagation()
    
    // 重置会话（不显示默认消息，使用自定义消息）
    handleRefresh(false)
    
    // 切换到智能对话视图
    if (currentView.value !== 'welcome') {
      switchView('welcome')
    }
    
    // 聚焦输入框
    nextTick(() => {
      const inputElement = inputRef.value?.$el?.querySelector('textarea')
      if (inputElement) {
        inputElement.focus()
      }
    })
    
    ElMessage({
      message: '已开启新对话',
      type: 'success',
      duration: 1500
    })
    return
  }
  
  // ESC: Clear input or return to welcome view
  if (e.key === 'Escape') {
    // 如果输入框有内容，清空输入
    if (question.value && isInInput) {
      e.preventDefault()
      question.value = ''
      ElMessage.info('已清空输入')
      return
    }
    
    // 如果知识库或文档列表显示，关闭它们
    if (showKbList.value || showDocList.value) {
      e.preventDefault()
      showKbList.value = false
      showDocList.value = false
      return
    }
    
    // 如果有对话历史，返回欢迎视图
    if (chatHistory.value.length > 0 && currentView.value !== 'welcome') {
      e.preventDefault()
      switchView('welcome')
      return
    }
    
    // 如果没有对话且不在欢迎视图，切换到欢迎视图
    if (chatHistory.value.length === 0 && currentView.value !== 'welcome') {
      e.preventDefault()
      switchView('welcome')
      return
    }
  }
}

onMounted(() => {
  updateDateTime()
  // 每秒更新时间
  dateTimeIntervalId.value = setInterval(updateDateTime, 1000)
  loadKnowledgeBases()
  loadAvailableModels()
  loadRecentConversations()
  loadDocuments()
  checkContentOverflow()
  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
  // Add global keyboard shortcut listener
  window.addEventListener('keydown', handleGlobalKeydown)
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
      // 存储 ResizeObserver 实例以便清理
      chatHistoryResizeObserver.value = resizeObserver
    }
    
    // 初始化时计算标签宽度
    updateMentionWidth()
    
    // 使用 ResizeObserver 监听标签宽度变化
    if (mentionContainerRef.value) {
      const observer = new ResizeObserver(() => {
        updateMentionWidth()
      })
      observer.observe(mentionContainerRef.value)
      // 存储 ResizeObserver 实例以便清理
      mentionResizeObserver.value = observer
    }
  })
})

// 优化清理逻辑：统一清理所有资源
onUnmounted(() => {
  // 清理事件监听器
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('keydown', handleGlobalKeydown)
  
  // 清理定时器
  if (dateTimeIntervalId.value) {
    clearInterval(dateTimeIntervalId.value)
    dateTimeIntervalId.value = null
  }
  
  // 清理 ResizeObserver
  if (chatHistoryResizeObserver.value) {
    chatHistoryResizeObserver.value.disconnect()
    chatHistoryResizeObserver.value = null
  }
  if (mentionResizeObserver.value) {
    mentionResizeObserver.value.disconnect()
    mentionResizeObserver.value = null
  }
  
  // 清理防抖函数（如果有待执行的）
  // debounce 函数内部会自动清理，这里不需要额外处理
})
</script>

<style scoped>
/* CSS Variables for consistent theming */
:root {
  --portal-primary-color: var(--el-color-primary, #409eff);
  --portal-bg-color: var(--el-bg-color-page, #f5f7fa);
  --portal-card-bg: var(--el-bg-color, #ffffff);
  --portal-text-primary: var(--el-text-color-primary, #303133);
  --portal-text-regular: var(--el-text-color-regular, #606266);
  --portal-text-secondary: var(--el-text-color-secondary, #909399);
  --portal-border-color: var(--el-border-color-lighter, #e4e7ed);
  --portal-shadow-light: 0 2px 8px rgba(0, 0, 0, 0.08);
  --portal-shadow-base: 0 4px 16px rgba(64, 158, 255, 0.2);
  --portal-transition: all 0.3s ease;
  --portal-border-radius: 12px;
  --portal-spacing-xs: 4px;
  --portal-spacing-sm: 8px;
  --portal-spacing-md: 12px;
  --portal-spacing-lg: 16px;
  --portal-spacing-xl: 20px;
}

.portal-container {
  height: 100vh; /* 固定高度，确保只有一个滚动条 */
  background: var(--color-bg-secondary);
  display: flex;
  flex-direction: column;
  padding: 0;
  margin: 0;
  position: relative;
  width: 100%;
  overflow: hidden; /* 防止容器本身滚动，只让portal-content滚动 */
  z-index: var(--z-base); /* 确保在导航栏下方 */
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
  padding: 20px 0 10px 0; /* 减少底部内边距 */
  padding-top: calc(20px + 60px) !important; /* 为固定导航栏留出空间，确保不被覆盖 */
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
  max-width: 1036px; /* 内容区与输入区（含左右边距）对齐，头像在两侧 */
  min-width: 636px; /* 内容区与输入区（含左右边距）对齐，头像在两侧 */
  margin: 0 auto;
  padding-left: 20px;
  padding-right: 20px;
  padding-bottom: 200px; /* 底部增加padding，避免被输入区域遮挡 */
  box-sizing: border-box;
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .portal-content > .welcome-section {
    min-width: 500px;
    max-width: 900px;
  }
  
  .portal-content > .chat-history-section {
    min-width: 536px;
    max-width: 836px;
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
  padding-top: 20px !important; /* 顶部收起时不需要留出导航栏空间 */
}

/* 顶部收起时，调整欢迎区域高度 */
.portal-content.content-header-collapsed .welcome-section {
  height: calc(100vh - 40px) !important; /* 视口高度 - 上下padding（20px + 20px） */
  max-height: calc(100vh - 40px) !important;
}

/* 顶部系统图标（在 padding-top 位置） */
/* 欢迎界面 */
.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  height: calc(100vh - 100px); /* 视口高度 - header高度(60px) - 上下padding(20px + 20px) */
  max-height: calc(100vh - 100px);
  padding: 12px 20px !important; /* 进一步减少内边距 */
  width: 100%;
  max-width: 1200px;
  min-width: 600px; /* 最小宽度，确保在小屏幕上也有良好显示 */
  margin: 0 auto;
  box-sizing: border-box;
  overflow: hidden; /* 防止溢出 */
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

.tab-item.disabled {
  opacity: 0.6;
  cursor: not-allowed;
  pointer-events: none;
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
  justify-content: flex-start;
  height: 100%;
  max-height: 100%;
  overflow: hidden;
}

.welcome-view {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  overflow: hidden;
}

/* 助手身份卡片 */
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
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  margin: 0;
}

.assistant-status {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin: 0;
}

/* 欢迎消息卡片 */
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

/* 快速操作提示 */
.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-sm);
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

/* 快捷键描述样式 */
.shortcuts-desc {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  flex-wrap: wrap;
  font-size: 11px;
  line-height: 1.4;
  text-align: center;
}

.shortcuts-desc kbd {
  display: inline-block;
  padding: 2px 5px;
  margin: 0 1px;
  font-size: 10px;
  font-family: 'Courier New', monospace;
  color: var(--el-text-color-primary, #303133);
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 3px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.shortcuts-desc .hint-separator {
  margin: 0 3px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  font-size: 11px;
}

/* 建议区域 - 两列布局 */
.suggestions-section {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 0;
  flex: 1;
  min-height: 0;
  max-height: 100%;
  overflow: hidden;
  align-items: start; /* 确保两列从顶部对齐 */
}

/* 建议区域列 */
.suggestions-column {
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  overflow: hidden;
  align-items: stretch; /* 确保两列高度一致 */
}

/* 确保两列内容区域对齐 */
.suggestions-column > * {
  display: flex;
  flex-direction: column;
  height: 100%;
  box-sizing: border-box;
}

/* 空列占位 */
.empty-column {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.empty-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  height: 100%;
}

.empty-icon {
  font-size: 32px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  opacity: 0.5;
}

.empty-text {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

/* 区域标题 */
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  margin-top: 0;
  padding: 0;
  flex-shrink: 0;
  height: 32px; /* 固定高度，确保两列标题对齐 */
  min-height: 32px;
  max-height: 32px;
  box-sizing: border-box;
}

.section-icon {
  font-size: 16px;
  color: var(--el-color-primary, #409eff);
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

/* 最近会话 */
.recent-conversations {
  width: 100%;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  align-items: stretch; /* 确保与右侧列对齐 */
  padding: 0; /* 确保没有额外的padding影响对齐 */
  margin: 0; /* 确保没有额外的margin影响对齐 */
}

.conversations-list {
  display: grid;
  grid-template-rows: repeat(3, 44px); /* 默认3行，每行44px高度，确保与右侧对齐 */
  gap: 4px;
  overflow: hidden;
  flex: 1;
  min-height: 0;
  align-content: start; /* 从顶部开始对齐 */
}

/* 收起状态下显示4行 */
.conversations-list.collapsed-mode {
  grid-template-rows: repeat(4, 44px); /* 收起状态4行，每行44px高度 */
}

/* 确保grid容器不会显示不完整的条目 */
.conversations-list {
  align-content: start; /* 从顶部开始对齐 */
  overflow: hidden; /* 隐藏溢出部分 */
}

/* 对建议问题列表也应用相同的逻辑 */
.questions-grid {
  align-content: start; /* 从顶部开始对齐 */
  overflow: hidden; /* 隐藏溢出部分 */
}

/* 收起状态下显示4行 */
.questions-grid.collapsed-mode {
  grid-template-rows: repeat(4, 44px); /* 收起状态4行，每行44px高度 */
}

.conversation-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  height: 44px; /* 固定高度，确保与右侧卡片对齐 */
  box-sizing: border-box;
  overflow: hidden; /* 防止内容溢出 */
}

.conversation-card:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
  border-color: var(--el-color-primary, #409eff);
  transform: translateX(4px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.conversation-content {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.conversation-icon {
  font-size: 16px;
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}

.conversation-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.conversation-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-time {
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
}

.conversation-arrow {
  font-size: 16px;
  color: var(--el-text-color-placeholder, #c0c4cc);
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.conversation-card:hover .conversation-arrow {
  color: var(--el-color-primary, #409eff);
  transform: translateX(4px);
}

/* 建议问题 */
.suggested-questions {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  align-items: stretch; /* 确保与左侧列对齐 */
  padding: 0; /* 确保没有额外的padding影响对齐 */
  margin: 0; /* 确保没有额外的margin影响对齐 */
}

.questions-grid {
  display: grid;
  grid-template-columns: 1fr;
  grid-template-rows: repeat(3, 44px); /* 默认3行，每行44px高度，确保与左侧对齐 */
  gap: 4px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  align-content: start; /* 从顶部开始对齐 */
}

/* 收起状态下显示4行 */
.questions-grid.collapsed-mode {
  grid-template-rows: repeat(4, 44px); /* 收起状态4行，每行44px高度 */
}

.question-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  height: 44px; /* 固定高度，确保与左侧卡片对齐 */
  box-sizing: border-box;
  overflow: hidden; /* 防止内容溢出 */
}

.question-card:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
  border-color: var(--el-color-primary, #409eff);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.question-icon {
  font-size: 16px;
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}

.question-text {
  font-size: 13px;
  color: var(--el-text-color-primary, #303133);
  font-weight: 400;
  flex: 1;
  line-height: 1.4;
}

.question-card:hover .question-text {
  color: var(--el-color-primary, #409eff);
}

/* 快捷键提示 */
.shortcuts-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-radius: 10px;
  width: 100%;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  flex-shrink: 0;
  margin-top: auto;
}

.hint-icon {
  font-size: 16px;
  color: var(--el-color-info, #909399);
  flex-shrink: 0;
}

.hint-content {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.hint-label {
  font-weight: 500;
}

.hint-separator {
  margin: 0 4px;
  color: var(--el-text-color-placeholder, #c0c4cc);
}

.hint-content kbd {
  display: inline-block;
  padding: 3px 7px;
  margin: 0 2px;
  font-size: 11px;
  font-family: 'Courier New', monospace;
  color: var(--el-text-color-primary, #303133);
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .welcome-view {
    padding: 0 16px;
  }
  
  .assistant-card {
    padding: 12px 14px;
    gap: 12px;
  }
  
  .assistant-avatar {
    width: 40px;
    height: 40px;
  }
  
  .assistant-icon {
    font-size: 20px;
  }
  
  .assistant-name {
    font-size: 18px;
  }
  
  .assistant-status {
    font-size: 11px;
  }
  
  .welcome-card {
    padding: 12px 14px;
  }
  
  .greeting-text {
    font-size: 15px;
  }
  
  .greeting-subtext {
    font-size: 12px;
  }
  
  .quick-actions {
    grid-template-columns: 1fr;
    gap: 6px;
    padding-top: 8px;
  }
  
  .action-item {
    padding: 8px 6px;
    gap: 6px;
  }
  
  .action-icon {
    width: 28px;
    height: 28px;
  }
  
  .action-icon .el-icon {
    font-size: 14px;
  }
  
  .action-title {
    font-size: 11px;
  }
  
  .action-desc {
    font-size: 10px;
  }
  
  .shortcuts-desc {
    gap: 2px;
    font-size: 10px;
  }
  
  .suggestions-section {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  
  .suggestions-section {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  
  .questions-grid {
    grid-template-columns: 1fr;
  }
  
  .shortcuts-hint {
    padding: 10px 12px;
  }
  
  .hint-content {
    font-size: 11px;
  }
  
  .empty-placeholder {
    padding: 30px 16px;
  }
  
  .empty-icon {
    font-size: 40px;
  }
}

/* 中等屏幕响应式 */
@media (max-width: 1024px) and (min-width: 769px) {
  .suggestions-section {
    grid-template-columns: 1fr;
    gap: 20px;
  }
}

/* 系统功能入口区域 */
.feature-entries {
  width: 100%;
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
  height: 100%;
  max-height: 100%;
  padding: 16px 20px;
  box-sizing: border-box;
  overflow-y: auto;
  overflow-x: hidden;
}

.feature-header {
  text-align: center;
  margin-bottom: 24px;
  flex-shrink: 0;
}

.feature-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 8px;
  height: 32px;
}

.feature-title-icon {
  font-size: 24px;
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}

.feature-title-text {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.feature-subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin: 0;
  line-height: var(--line-height-normal);
}

/* 功能分类容器 */
.feature-categories {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  width: 100%;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.feature-category {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  flex-shrink: 0;
}

.category-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0;
  padding-bottom: var(--spacing-md);
  border-bottom: 2px solid var(--color-border-base);
}

.category-icon {
  font-size: var(--font-size-lg);
  color: var(--color-primary);
  transition: all var(--transition-base);
}

.category-title:hover .category-icon {
  transform: scale(1.1);
}

/* 功能卡片容器 */
.feature-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  width: 100%;
}

/* 功能卡片 */
.feature-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-xl);
  cursor: pointer;
  transition: all var(--transition-base);
  box-shadow: var(--shadow-sm);
  position: relative;
  overflow: hidden;
}

.feature-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: var(--color-primary);
  transform: scaleY(0);
  transform-origin: top;
  transition: transform var(--transition-base);
}

.feature-card:hover {
  transform: translateY(-4px);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-primary-lg);
}

.feature-card:hover::before {
  transform: scaleY(1);
}

.feature-card.navigating {
  opacity: 0.6;
  pointer-events: none;
  transform: scale(0.98);
}

/* 功能卡片图标 */
.feature-card-icon {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--color-primary-light-5) 0%, var(--color-primary-light-4) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-base);
  box-shadow: var(--shadow-xs);
}

.feature-card:hover .feature-card-icon {
  background: linear-gradient(135deg, var(--color-primary-light-4) 0%, var(--color-primary-light-3) 100%);
  transform: scale(1.05);
  box-shadow: var(--shadow-sm);
}

.feature-icon {
  font-size: 28px;
  color: var(--color-primary);
  transition: transform var(--transition-base);
}

.feature-card:hover .feature-icon {
  transform: scale(1.1) rotate(5deg);
}

/* 功能卡片内容 */
.feature-card-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.feature-card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin: 0;
  transition: color var(--transition-base);
}

.feature-card:hover .feature-card-title {
  color: var(--color-primary);
}

.feature-card-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin: 0;
  line-height: var(--line-height-normal);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 功能卡片箭头 */
.feature-card-arrow {
  flex-shrink: 0;
  font-size: var(--font-size-lg);
  color: var(--color-text-placeholder);
  transition: all var(--transition-base);
  transform: translateX(0);
}

.feature-card:hover .feature-card-arrow {
  color: var(--color-primary);
  transform: translateX(4px);
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .feature-entries {
    max-width: 900px;
    padding: 16px;
  }
  
  .feature-cards {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 14px;
  }
  
  .feature-card {
    padding: 18px;
  }
  
  .feature-card-icon {
    width: 50px;
    height: 50px;
  }
  
  .feature-icon {
    font-size: 24px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .feature-entries {
    padding: 16px;
    margin-top: 0;
  }
  
  .feature-header {
    margin-bottom: 32px;
  }
  
  .feature-title-text {
    font-size: 20px;
  }
  
  .feature-subtitle {
    font-size: 13px;
  }
  
  .feature-categories {
    gap: 32px;
  }
  
  .category-title {
    font-size: 16px;
  }
  
  .feature-cards {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  
  .feature-card {
    padding: 16px;
  }
  
  .feature-card-icon {
    width: 48px;
    height: 48px;
  }
  
  .feature-icon {
    font-size: 22px;
  }
  
  .feature-card-title {
    font-size: 15px;
  }
  
  .feature-card-desc {
    font-size: 12px;
  }
}

.input-section {
  width: 100%;
  max-width: 900px; /* 输入区域宽度，比问答区域窄一些 */
  min-width: 500px; /* 最小宽度，确保在小屏幕上也有良好显示 */
  margin: 0 auto 10px;
  position: sticky;
  bottom: 0;
  background: transparent; /* 与大背景颜色保持一致 */
  padding: 8px 20px 16px 20px; /* 减少顶部内边距，左右内边距与问答区域保持一致 */
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
    padding: 8px 16px 16px 16px; /* 减少顶部内边距 */
  }
}

.input-wrapper {
  position: relative;
  background: var(--el-bg-color, #ffffff);
  border-radius: 20px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03), 0 1px 1px 0 rgba(0, 0, 0, 0.02);
  padding: 20px 24px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1.5px solid var(--el-border-color-lighter, #e4e7ed);
  margin-bottom: 12px;
  width: 100%;
  max-width: 100%;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

/* 当内容溢出时，输入框背景也变为半透明 */
.input-section.transparent .input-wrapper {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px) saturate(180%);
  -webkit-backdrop-filter: blur(12px) saturate(180%);
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03), 0 1px 1px 0 rgba(0, 0, 0, 0.02);
  border-color: var(--el-border-color-lighter, #e4e7ed);
}

.input-wrapper:focus-within {
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.04), 0 1px 2px 0 rgba(0, 0, 0, 0.03);
  border-color: var(--el-color-primary, #409eff);
  transform: translateY(-1px);
  background: var(--el-bg-color, #ffffff);
}

.input-controls-float {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: var(--el-bg-color, #ffffff);
  border-radius: 12px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03), 0 1px 1px 0 rgba(0, 0, 0, 0.02);
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
/* .doc-mention-list 使用与 .kb-mention-list 相同的样式 */

.portal-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  line-height: 1.6;
  resize: none;
  min-width: 0;
  transition: all 0.2s ease;
}

.portal-input :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 0;
  font-size: 16px;
  line-height: 1.6;
  resize: none;
  position: relative;
  z-index: 1;
  background: transparent;
  color: var(--el-text-color-primary, #303133);
  transition: color 0.2s ease;
  font-weight: 400;
}

.portal-input :deep(.el-textarea__inner)::placeholder {
  color: var(--el-text-color-placeholder, #c0c4cc);
  font-weight: 400;
  opacity: 0.8;
}

.portal-input :deep(.el-textarea__inner):focus {
  border: none;
  box-shadow: none;
  color: var(--el-text-color-primary, #303133);
}

.portal-input :deep(.el-textarea__inner):hover {
  color: var(--el-text-color-primary, #303133);
}

/* 输入框容器（标签作为输入内容的一部分，不单独占行或列） */
.input-container {
  position: relative;
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
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
  padding-left: 0 !important;
  padding-right: 0 !important;
  text-indent: var(--mention-width, 0px);
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
  max-width: 1036px; /* 内容区与输入区（含左右边距）对齐，头像在两侧 */
  min-width: 636px; /* 内容区与输入区（含左右边距）对齐，头像在两侧 */
  margin: 0 auto;
  flex: 1;
  overflow: visible; /* 移除滚动条，由父容器portal-content统一处理 */
  padding: 20px 20px 200px 20px !important; /* 底部增加padding，避免被输入区域遮挡 */
  box-sizing: border-box;
  transition: height 0.3s ease;
  --portal-avatar-size: 36px;
  --portal-avatar-gap: 12px;
  --portal-message-content-width: 900px;
}

/* 当内容溢出时，固定高度到输入框底部 */
.chat-history-section.content-overflow {
  flex: 0 0 auto;
  overflow-y: auto;
  overflow-x: hidden;
  max-height: calc(100vh - 280px); /* 减去输入框、header和padding的高度 */
  padding-bottom: 200px !important; /* 确保滚动时内容不被输入区域遮挡 */
}

/* 中等屏幕自适应 */
@media (max-width: 1024px) and (min-width: 769px) {
  .chat-history-section {
    min-width: 536px;
    max-width: 836px;
    --portal-message-content-width: 700px;
  }
}

/* 小屏幕自适应 */
@media (max-width: 768px) {
  .chat-history-section {
    min-width: auto; /* 小屏幕下取消最小宽度限制 */
    padding: 20px 16px 200px 16px !important; /* 底部增加padding，避免被输入区域遮挡 */
    --portal-avatar-size: 32px;
    --portal-avatar-gap: 8px;
    --portal-message-content-width: 1fr;
  }
}

:deep(.chat-history-section .chat-history-content) {
  padding-left: 0;
  padding-right: 0;
}

/* 底部免责声明 */
.portal-footer {
  text-align: center;
  padding: 16px 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.95) 0%, rgba(255, 255, 255, 0.98) 100%);
  backdrop-filter: blur(10px);
  border-top: 1px solid var(--el-border-color-lighter, #e4e7ed);
  position: sticky;
  bottom: 0;
  z-index: 10;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
}

.portal-footer:hover {
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.06);
}

.footer-content {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 20px;
  background: rgba(64, 158, 255, 0.06);
  transition: all 0.3s ease;
}

.portal-footer:hover .footer-content {
  background: rgba(64, 158, 255, 0.1);
  transform: translateY(-1px);
}

.footer-icon {
  font-size: 14px;
  color: var(--el-color-primary, #409eff);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.05);
  }
}

.footer-text {
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
  font-weight: 400;
  letter-spacing: 0.3px;
  line-height: 1.5;
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
    padding: 8px 16px 16px 16px; /* 减少顶部内边距 */
  }

  .input-wrapper {
    padding: 14px 16px;
    border-radius: 16px;
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

  .portal-footer {
    padding: 12px 16px;
  }

  .footer-content {
    padding: 6px 12px;
    gap: 6px;
  }

  .footer-icon {
    font-size: 12px;
  }

  .footer-text {
    font-size: 12px;
    letter-spacing: 0.2px;
  }
}
</style>
