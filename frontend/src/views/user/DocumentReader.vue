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
          @text-translate="handleTextTranslate"
        />
      </div>

      <!-- 右侧：功能区域 -->
      <div class="right-panel">
        <!-- 标签页 -->
        <el-tabs v-model="activeTab" class="function-tabs" @tab-click="handleTabClick">
          <el-tab-pane label="导读" name="guide">
            <GuideTab :doc-id="docId" />
          </el-tab-pane>
          <el-tab-pane label="翻译" name="translate">
            <TranslateTab 
              :doc-id="docId" 
              :document-info="documentInfo"
            />
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
            ref="documentQARef"
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
const documentQARef = ref(null)


// 检测文档的主要语言
const detectDocumentLanguage = async () => {
  if (!docId.value) return null
  
  try {
    // 获取文档文本内容的前2000个字符进行检测
    const textResponse = await getDocumentText(docId.value)
    let textContent = ''
    
    if (typeof textResponse === 'string') {
      textContent = textResponse
    } else if (textResponse && typeof textResponse === 'object') {
      textContent = textResponse.content || textResponse.data?.content || textResponse.text || ''
    }
    
    if (!textContent || textContent.length === 0) {
      return null
    }
    
    // 取前2000个字符进行检测
    const sampleText = textContent.substring(0, 2000)
    
    if (sampleText.length < 10) {
      return null
    }
    
    let totalChars = 0
    let chineseChars = 0
    let japaneseChars = 0
    let koreanChars = 0
    let englishChars = 0
    
    for (const c of sampleText) {
      // 跳过空白字符和标点符号
      if (/\s/.test(c)) {
        continue
      }
      
      totalChars++
      
      const code = c.charCodeAt(0)
      
      // 检测中文（简体中文范围：\u4e00-\u9fa5）
      if (code >= 0x4e00 && code <= 0x9fa5) {
        chineseChars++
      }
      // 检测日文（平假名：\u3040-\u309F，片假名：\u30A0-\u30FF）
      else if ((code >= 0x3040 && code <= 0x309F) || (code >= 0x30A0 && code <= 0x30FF)) {
        japaneseChars++
      }
      // 检测韩文（\uAC00-\uD7AF）
      else if (code >= 0xAC00 && code <= 0xD7AF) {
        koreanChars++
      }
      // 检测英文（ASCII字母）
      else if ((code >= 65 && code <= 90) || (code >= 97 && code <= 122)) {
        englishChars++
      }
    }
    
    if (totalChars === 0) {
      return null
    }
    
    // 计算各语言占比
    const chineseRatio = chineseChars / totalChars
    const japaneseRatio = japaneseChars / totalChars
    const koreanRatio = koreanChars / totalChars
    const englishRatio = englishChars / totalChars
    
    // 如果某种语言占比超过30%，认为是该语言
    if (chineseRatio >= 0.3) {
      return 'zh'
    } else if (japaneseRatio >= 0.3) {
      return 'ja'
    } else if (koreanRatio >= 0.3) {
      return 'ko'
    } else if (englishRatio >= 0.3) {
      return 'en'
    }
    
    return null
  } catch (error) {
    console.error('检测文档语言失败:', error)
    return null
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
    
    // 不再自动检测语言，翻译时再检测
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
      // 优化：使用for循环替代find
      const models = availableModels.value
      let defaultModel = null
      for (let i = 0; i < models.length; i++) {
        if (models[i].isDefault) {
          defaultModel = models[i]
          break
        }
      }
      if (!defaultModel && models.length > 0) {
        defaultModel = models[0]
      }
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

// 处理文本操作的通用方法
const handleTextOperation = (text, insertMethod) => {
  if (!text?.trim()) return
  
  selectedText.value = text.trim()
  qaFocused.value = true
  
  // 等待DOM更新后插入文本
  nextTick(() => {
    const tryInsert = (attempts = 0) => {
      if (documentQARef.value?.[insertMethod]) {
        documentQARef.value[insertMethod](text.trim())
      } else if (attempts < 10) {
        setTimeout(() => tryInsert(attempts + 1), 100)
      }
    }
    tryInsert()
  })
}

// 处理文本解读
const handleTextInterpret = (text) => {
  handleTextOperation(text, 'insertSelectedText')
}

// 处理文本翻译
const handleTextTranslate = (text) => {
  handleTextOperation(text, 'insertTranslateText')
}

// 处理标签页点击
const handleTabClick = (tab) => {
  // 不再需要特殊处理
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
  position: relative;
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
  width: 50%;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter, #e4e7ed);
  overflow: hidden;
  background: var(--el-bg-color-page, #f5f7fa);
}

.right-panel {
  width: 50%;
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

/* 选项卡切换过渡动画 */
.function-tabs :deep(.el-tabs__content) {
  position: relative;
}

.function-tabs :deep(.el-tab-pane) {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.function-tabs :deep(.el-tab-pane.is-active) {
  animation: tabFadeIn 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: opacity, transform; /* 硬件加速 */
}

@keyframes tabFadeIn {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
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

.disabled-translate-message .loading-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: var(--el-color-primary, #409eff);
}

.disabled-translate-message .loading-icon.is-loading {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.disabled-translate-message .message-title {
  margin: 8px 0 4px 0;
  font-size: 18px;
  font-weight: 600;
  text-align: center;
  color: var(--el-text-color-primary, #303133);
}

.disabled-translate-message .message-desc {
  margin: 8px 0;
  font-size: 14px;
  text-align: center;
  color: var(--el-text-color-regular, #606266);
  line-height: 1.6;
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

