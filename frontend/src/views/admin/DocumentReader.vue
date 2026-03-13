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
            <TranslateTab :doc-id="docId" :document-info="documentInfo" />
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
          <component
            :is="DocumentQAComponent"
            v-if="DocumentQAComponent"
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
import { ref, computed, onMounted, nextTick, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
const DocumentViewer = defineAsyncComponent(() => import('@/components/documentReader/DocumentViewer.vue'))
const GuideTab = defineAsyncComponent(() => import('@/components/documentReader/GuideTab.vue'))
import TranslateTab from '@/components/documentReader/TranslateTab.vue'
import MindMapTab from '@/components/documentReader/MindMapTab.vue'
import NotesTab from '@/components/documentReader/NotesTab.vue'

// 动态导入 DocumentQA 组件，创建单独的代码块
const DocumentQAComponent = defineAsyncComponent(() => 
  import(/* webpackChunkName: "document-qa" */ '@/components/documentReader/DocumentQA.vue')
)
import { getDocumentDetail, downloadDocument } from '@/api/documentReader'
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


// 处理标签页点击
const handleTabClick = (tab) => {
  // 不再需要特殊处理
}

// 加载文档详情
const loadDocumentDetail = async () => {
  if (!docId.value) {
    ElMessage.error('文档ID不存在')
    router.push('/admin/document-reader')
    return
  }
  
  try {
    const detail = await getDocumentDetail(docId.value)
    documentInfo.value = detail
    // 根据文档类型设置总页数（这里假设后端返回了总页数，如果没有则默认为1）
    totalPages.value = detail.totalPages || 1
  } catch (error) {
    ElMessage.error('加载文档详情失败：' + (error.message || '未知错误'))
    router.push('/admin/document-reader')
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
  router.push('/admin/document-reader')
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
const handleExport = async () => {
  if (!docId.value) return
  
  try {
    const blob = await downloadDocument(docId.value)
    const url = window.URL.createObjectURL(new Blob([blob]))
    const link = document.createElement('a')
    link.href = url
    // 使用原始文件名，如果不存在则使用 fileName
    const fileName = documentInfo.value?.originalFileName || documentInfo.value?.fileName || 'document'
    link.setAttribute('download', fileName)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('开始下载...')
  } catch (error) {
    ElMessage.error('下载失败：' + (error.message || '未知错误'))
  }
}

// 处理文本选择
const handleTextSelected = (text) => {
  selectedText.value = text
  // 自动聚焦到问答区域
  qaFocused.value = true
}

// 处理文本解读（将文本插入到输入框，等待用户发送）
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

onMounted(() => {
  if (docId.value) {
    loadDocumentDetail()
    loadAvailableModels()
  } else {
    router.push('/admin/document-reader')
  }
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.document-reader {
  height: 100%;
  width: 100%;
  overflow: hidden;
  background: var(--color-bg-secondary);
  position: relative;
}

/* ========== 阅读器容器 ========== */
.reader-container {
  display: flex;
  height: 100%;
  width: 100%;
  gap: 0;
}

.reader-container.qa-focused .left-panel {
  opacity: 0.3;
  pointer-events: none;
  transition: opacity var(--transition-base);
}

.reader-container.qa-focused .right-panel .function-tabs {
  opacity: 0.3;
  pointer-events: none;
  transition: opacity var(--transition-base);
}

/* ========== 左侧面板 ========== */
.left-panel {
  width: 50%;
  flex-shrink: 0;
  border-right: 1px solid var(--color-border-lighter);
  overflow: hidden;
  background: var(--color-bg-secondary);
}

/* ========== 右侧面板 ========== */
.right-panel {
  width: 50%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  background: var(--color-bg-primary);
}

/* ========== 功能标签页 ========== */
.function-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  background: var(--color-bg-primary);
}

.function-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 var(--spacing-lg);
  border-bottom: 1px solid var(--color-border-lighter);
  flex-shrink: 0;
  background: var(--color-bg-tertiary);
  box-shadow: var(--shadow-xs);
}

.function-tabs :deep(.el-tabs__nav-wrap) {
  padding: 0;
}

.function-tabs :deep(.el-tabs__item) {
  padding: 0 var(--spacing-lg);
  height: 48px;
  line-height: 48px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-regular);
  transition: all var(--transition-base);
}

.function-tabs :deep(.el-tabs__item:hover) {
  color: var(--color-primary);
}

.function-tabs :deep(.el-tabs__item.is-active) {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}

.function-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  background: var(--color-primary);
  border-radius: var(--radius-sm);
}

.function-tabs :deep(.el-tabs__item.is-disabled) {
  color: var(--color-text-disabled);
  cursor: not-allowed;
}

.function-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  padding: 0;
  min-height: 0;
  position: relative;
}

.function-tabs :deep(.el-tab-pane) {
  height: 100%;
  overflow: hidden;
  transition: opacity var(--transition-base), transform var(--transition-base);
}

.function-tabs :deep(.el-tab-pane.is-active) {
  animation: tabFadeIn var(--transition-fast) forwards;
  will-change: opacity, transform;
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

/* ========== 问答区域 ========== */
.qa-section {
  flex: 0 0 auto;
  flex-shrink: 0;
  border-top: 1px solid var(--color-border-lighter);
  overflow: visible;
  background: var(--color-bg-primary);
  position: relative;
  z-index: var(--z-base);
  transition: all var(--transition-base);
  transform-origin: bottom center;
  opacity: 1;
  transform: scaleY(1) translateY(0);
  height: auto;
  box-shadow: var(--shadow-sm);
}

.qa-section.qa-focused {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  height: 100%;
  z-index: var(--z-modal);
  box-shadow: var(--shadow-xl);
  background: var(--color-bg-primary);
  animation: qaExpand var(--transition-base) forwards;
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
