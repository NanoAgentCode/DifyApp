<template>
  <div class="document-reader">
    <div class="reader-container" :class="{ 'qa-focused': qaFocused }">
      <!-- 左侧：文档查看器 -->
      <div class="left-panel">
        <DocumentViewer
          :doc-id="docId"
          :document-info="documentInfo"
          :current-page="currentPage"
          :total-pages="totalPages"
          :zoom-level="zoomLevel"
          @update:current-page="currentPage = $event"
          @update:zoom-level="zoomLevel = $event"
          @update:total-pages="totalPages = $event"
          @back="handleBack"
          @favorite="handleFavorite"
          @share="handleShare"
          @export="handleExport"
          @text-selected="handleTextSelected"
          @text-interpret="handleTextInterpret"
        />
      </div>

      <!-- 右侧：功能区域 -->
      <div class="right-panel">
        <!-- 标签页 -->
        <el-tabs v-model="activeTab" class="function-tabs">
          <el-tab-pane label="导读" name="guide">
            <GuideTab :doc-id="docId" />
          </el-tab-pane>
          <el-tab-pane label="翻译" name="translate" :disabled="isChineseDocument">
            <TranslateTab v-if="!isChineseDocument" :doc-id="docId" :document-info="documentInfo" />
            <div v-else class="disabled-translate-message">
              <el-icon class="message-icon"><Warning /></el-icon>
              <p>中文文档暂不支持翻译功能</p>
            </div>
          </el-tab-pane>
          <el-tab-pane label="脑图" name="mindmap">
            <MindMapTab :doc-id="docId" />
          </el-tab-pane>
          <el-tab-pane label="笔记" name="notes">
            <NotesTab :doc-id="docId" />
          </el-tab-pane>
        </el-tabs>

        <!-- 问答区域 -->
        <div class="qa-section" :class="{ 'qa-focused': qaFocused }">
          <DocumentQA
            :doc-id="docId"
            :model-id="selectedModelId"
            :use-stream="useStream"
            :selected-text="selectedText"
            @focus="qaFocused = true"
            @blur="qaFocused = false"
            @text-used="selectedText = ''"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Warning } from '@element-plus/icons-vue'
import DocumentViewer from '@/components/documentReader/DocumentViewer.vue'
import GuideTab from '@/components/documentReader/GuideTab.vue'
import TranslateTab from '@/components/documentReader/TranslateTab.vue'
import MindMapTab from '@/components/documentReader/MindMapTab.vue'
import NotesTab from '@/components/documentReader/NotesTab.vue'
import DocumentQA from '@/components/documentReader/DocumentQA.vue'
import { getDocumentDetail, getDocumentText } from '@/api/documentReader'
import { getAvailableQAModels } from '@/api/model'

const route = useRoute()
const router = useRouter()

const docId = computed(() => {
  return route.params.docId ? parseInt(route.params.docId) : null
})

const documentInfo = ref({})
const currentPage = ref(1)
const totalPages = ref(1)
const zoomLevel = ref(100)
const activeTab = ref('guide')
const selectedModelId = ref(null)
const useStream = ref(true)
const availableModels = ref([])
const qaFocused = ref(false)
const selectedText = ref('')
const isChineseDocument = ref(false)
const documentQARef = ref(null)

// 检测文档是否为中文
const detectChineseDocument = async () => {
  if (!docId.value) return
  
  try {
    // 获取文档文本内容的前1000个字符进行检测
    const textResponse = await getDocumentText(docId.value)
    let textContent = ''
    
    if (typeof textResponse === 'string') {
      textContent = textResponse
    } else if (textResponse && typeof textResponse === 'object') {
      textContent = textResponse.content || textResponse.data?.content || ''
    }
    
    if (!textContent || textContent.length === 0) {
      isChineseDocument.value = false
      return
    }
    
    // 取前1000个字符进行检测
    const sampleText = textContent.substring(0, 1000)
    
    // 检测中文字符：使用正则表达式匹配中文字符
    // 中文字符范围：\u4e00-\u9fa5（基本汉字），\u3400-\u4db5（扩展A），\u20000-\u2a6d6（扩展B）
    const chineseCharPattern = /[\u4e00-\u9fa5\u3400-\u4db5]/
    const chineseCharCount = (sampleText.match(chineseCharPattern) || []).length
    
    // 如果中文字符占比超过30%，认为是中文文档
    const chineseRatio = chineseCharCount / Math.min(sampleText.length, 1000)
    isChineseDocument.value = chineseRatio > 0.3
    
    // 如果检测到是中文文档且当前在翻译标签页，切换到其他标签页
    if (isChineseDocument.value && activeTab.value === 'translate') {
      activeTab.value = 'guide'
    }
  } catch (error) {
    console.error('检测文档语言失败:', error)
    // 检测失败时默认不禁用翻译功能
    isChineseDocument.value = false
  }
}

// 加载文档详情
const loadDocumentDetail = async () => {
  if (!docId.value) {
    ElMessage.error('文档ID不存在')
    router.push('/user/document-reader')
    return
  }
  
  try {
    const detail = await getDocumentDetail(docId.value)
    documentInfo.value = detail
    // 根据文档类型设置总页数（这里假设后端返回了总页数，如果没有则默认为1）
    totalPages.value = detail.totalPages || 1
    
    // 加载文档详情后检测语言
    await detectChineseDocument()
  } catch (error) {
    ElMessage.error('加载文档详情失败：' + (error.message || '未知错误'))
    router.push('/user/document-reader')
  }
}

// 加载可用模型
const loadAvailableModels = async () => {
  try {
    const models = await getAvailableQAModels()
    availableModels.value = Array.isArray(models) ? models : (models?.data || [])
    
    // 默认选择第一个模型或默认模型
    if (availableModels.value.length > 0) {
      const defaultModel = availableModels.value.find(m => m.isDefault) || availableModels.value[0]
      selectedModelId.value = defaultModel.id
    }
  } catch (error) {
    console.error('加载模型列表失败:', error)
  }
}

// 返回
const handleBack = () => {
  router.push('/user/document-reader')
}

// 收藏
const handleFavorite = () => {
  ElMessage.info('收藏功能开发中')
}

// 分享
const handleShare = () => {
  ElMessage.info('分享功能开发中')
}

// 导出
const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

// 处理文本选择
const handleTextSelected = (text) => {
  selectedText.value = text
  // 自动聚焦到问答区域
  qaFocused.value = true
}

// 处理文本解读（直接发送到问答）
const handleTextInterpret = (text) => {
  if (!text || !text.trim()) return
  
  // 自动聚焦到问答区域
  qaFocused.value = true
  
  // 等待问答区域展开后，发送问题
  setTimeout(() => {
    if (documentQARef.value) {
      // 调用DocumentQA的方法直接发送问题
      const question = `请解读以下内容：\n\n${text.trim()}`
      documentQARef.value.sendQuestion(question)
    }
  }, 300)
}

onMounted(() => {
  if (docId.value) {
    loadDocumentDetail()
    loadAvailableModels()
  } else {
    router.push('/user/document-reader')
  }
})
</script>

<style scoped>
.document-reader {
  height: 100%;
  width: 100%;
  overflow: hidden;
  background: var(--el-bg-color-page, #f5f7fa);
}

.reader-container {
  display: flex;
  height: 100%;
  width: 100%;
  gap: 0;
}

.reader-container.qa-focused .left-panel {
  opacity: 0.3;
  pointer-events: none;
  transition: opacity 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.reader-container.qa-focused .right-panel .function-tabs {
  opacity: 0.3;
  pointer-events: none;
  transition: opacity 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.left-panel {
  width: 55%;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter, #e4e7ed);
  overflow: hidden;
  background: var(--el-bg-color-page, #f5f7fa);
}

.right-panel {
  width: 45%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  background: var(--el-bg-color, #ffffff);
}

.function-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  background: var(--el-bg-color, #ffffff);
}

.function-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 20px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  flex-shrink: 0;
  background: var(--el-bg-color, #ffffff);
}

.function-tabs :deep(.el-tabs__nav-wrap) {
  padding: 0;
}

.function-tabs :deep(.el-tabs__item) {
  padding: 0 20px;
  height: 48px;
  line-height: 48px;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-regular, #606266);
  transition: all 0.3s;
}

.function-tabs :deep(.el-tabs__item.is-active) {
  color: var(--el-color-primary, #409eff);
  font-weight: 600;
}

.function-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  background: var(--el-color-primary, #409eff);
}

.function-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  padding: 0;
  min-height: 0;
}

.function-tabs :deep(.el-tab-pane) {
  height: 100%;
  overflow: hidden;
}

.function-tabs :deep(.el-tabs__item.is-disabled) {
  color: var(--el-text-color-disabled, #c0c4cc);
  cursor: not-allowed;
}

.disabled-translate-message {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: var(--el-text-color-placeholder, #909399);
  padding: 40px;
}

.disabled-translate-message .message-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: var(--el-color-warning, #e6a23c);
}

.disabled-translate-message p {
  margin: 8px 0;
  font-size: 16px;
  text-align: center;
}

.qa-section {
  flex: 0 0 auto;
  flex-shrink: 0;
  border-top: 1px solid var(--el-border-color-lighter, #e4e7ed);
  overflow: visible;
  background: var(--el-bg-color, #ffffff);
  position: relative;
  z-index: 1;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  transform-origin: bottom center;
  opacity: 1;
  transform: scaleY(1) translateY(0);
  height: auto;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);
}

.qa-section.qa-focused {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  height: 100%;
  z-index: 10;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.15);
  animation: qaExpand 0.4s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}

@keyframes qaExpand {
  from {
    opacity: 0.9;
    transform: scaleY(0.3) translateY(66%);
  }
  to {
    opacity: 1;
    transform: scaleY(1) translateY(0);
  }
}
</style>

