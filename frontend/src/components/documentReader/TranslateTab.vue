<template>
  <div class="translate-tab">
    <div class="tab-header">
      <div class="header-info">
        <el-icon><Switch /></el-icon>
        <span>翻译</span>
      </div>
      <div v-if="!isEditing" class="header-actions">
        <el-select
          v-model="targetLanguage"
          placeholder="选择目标语言"
          size="small"
          style="width: 150px; margin-right: 8px"
          @change="handleLanguageChange"
        >
          <el-option label="中文" value="zh" />
          <el-option label="英文" value="en" />
          <el-option label="日文" value="ja" />
          <el-option label="韩文" value="ko" />
        </el-select>
        <el-button
          type="success"
          size="small"
          @click="handleTranslate"
          :loading="translating"
          :disabled="!targetLanguage || translating"
        >
          <el-icon><Refresh /></el-icon>
          {{ translationContent ? '重新翻译' : '翻译' }}
        </el-button>
        <el-button
          v-if="translationContent"
          type="primary"
          size="small"
          @click="handleEdit"
          :disabled="translating"
        >
          <el-icon><Edit /></el-icon>
          编辑翻译
        </el-button>
      </div>
      <div v-else class="edit-actions">
        <el-button size="small" @click="handleCancel">取消</el-button>
        <el-button type="primary" size="small" @click="handleSave" :loading="saving">
          保存
        </el-button>
      </div>
    </div>
    
    <div class="tab-content">
      <el-input
        v-if="isEditing"
        v-model="editContent"
        type="textarea"
        :rows="15"
        placeholder="请输入翻译内容..."
        class="edit-input"
      />
      <div v-else class="content-wrapper">
        <div v-if="translating" class="loading-state">
          <el-icon class="loading-icon is-loading"><Loading /></el-icon>
          <p>翻译中，请稍候...</p>
        </div>
        <div v-else-if="translationContent && originalText" class="comparison-view">
          <div class="comparison-container">
            <div class="text-panel original-panel">
              <div class="panel-header">原文</div>
              <div 
                ref="originalContentRef"
                class="text-content original-content" 
                v-html="renderedOriginal"
                @scroll="handleOriginalScroll"
              ></div>
            </div>
            <div class="text-panel translation-panel">
              <div class="panel-header">译文</div>
              <div 
                ref="translationContentRef"
                class="text-content translation-content-display" 
                v-html="renderedTranslation"
                @scroll="handleTranslationScroll"
              ></div>
            </div>
          </div>
        </div>
        <div v-else-if="translationContent" class="translation-content">
          <div class="translation-display" v-html="renderedTranslation"></div>
        </div>
        <div v-else class="empty-state">
          <el-icon class="empty-icon"><Document /></el-icon>
          <p>请选择目标语言并点击翻译按钮</p>
          <p class="tip">仅支持非中文文档的翻译</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Switch, Refresh, Loading, Document, Edit } from '@element-plus/icons-vue'
import { translateDocument, getDocumentTranslation, saveDocumentTranslation, getDocumentText } from '@/api/documentReader'
import { renderMarkdown } from '@/composables/useMarkdown'

const props = defineProps({
  docId: {
    type: Number,
    required: true
  },
  documentInfo: {
    type: Object,
    default: () => ({})
  }
})

const targetLanguage = ref('zh')
const translationContent = ref('')
const originalText = ref('')
const translating = ref(false)
const isEditing = ref(false)
const editContent = ref('')
const saving = ref(false)
const hasAutoTranslated = ref(false) // 标记是否已自动翻译
const originalContentRef = ref(null)
const translationContentRef = ref(null)
const isScrolling = ref(false) // 防止滚动循环

const renderedTranslation = computed(() => {
  if (!translationContent.value) return ''
  // 保持原始格式，不转换为markdown，只转义HTML
  return escapeHtml(translationContent.value).replace(/\n/g, '<br>')
})

const renderedOriginal = computed(() => {
  if (!originalText.value) return ''
  // 保持原始格式，不转换为markdown，只转义HTML
  return escapeHtml(originalText.value).replace(/\n/g, '<br>')
})

// 转义HTML，但保留换行
function escapeHtml(text) {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

// 同步滚动处理
const handleOriginalScroll = () => {
  if (isScrolling.value) return
  if (!originalContentRef.value || !translationContentRef.value) return
  
  isScrolling.value = true
  const originalEl = originalContentRef.value
  const translationEl = translationContentRef.value
  
  // 计算滚动比例
  const scrollRatio = originalEl.scrollTop / (originalEl.scrollHeight - originalEl.clientHeight)
  const targetScrollTop = scrollRatio * (translationEl.scrollHeight - translationEl.clientHeight)
  
  translationEl.scrollTop = targetScrollTop
  
  setTimeout(() => {
    isScrolling.value = false
  }, 50)
}

const handleTranslationScroll = () => {
  if (isScrolling.value) return
  if (!originalContentRef.value || !translationContentRef.value) return
  
  isScrolling.value = true
  const originalEl = originalContentRef.value
  const translationEl = translationContentRef.value
  
  // 计算滚动比例
  const scrollRatio = translationEl.scrollTop / (translationEl.scrollHeight - translationEl.clientHeight)
  const targetScrollTop = scrollRatio * (originalEl.scrollHeight - originalEl.clientHeight)
  
  originalEl.scrollTop = targetScrollTop
  
  setTimeout(() => {
    isScrolling.value = false
  }, 50)
}

// 翻译文档
const handleTranslate = async () => {
  if (!targetLanguage.value) {
    ElMessage.warning('请先选择目标语言')
    return
  }
  
  translating.value = true
  try {
    // 先调用翻译接口
    await translateDocument(props.docId, targetLanguage.value)
    
    // 然后获取翻译内容
    const response = await getDocumentTranslation(props.docId, targetLanguage.value)
    // 后端返回格式: { content: "..." } 或直接是字符串
    let content = ''
    if (typeof response === 'string') {
      content = response
    } else if (response && typeof response === 'object') {
      content = response.content || response.data?.content || ''
      if (content && typeof content !== 'string') {
        try {
          content = JSON.stringify(content)
        } catch (e) {
          content = String(content)
        }
      }
    }
    translationContent.value = content || ''
    
    // 加载原文用于对比
    if (translationContent.value && !originalText.value) {
      await loadOriginalText()
    }
    
    if (translationContent.value) {
      ElMessage.success('翻译完成')
    } else {
      ElMessage.warning('翻译结果为空')
    }
  } catch (error) {
    ElMessage.error('翻译失败：' + (error.message || '未知错误'))
  } finally {
    translating.value = false
  }
}

// 语言切换
const handleLanguageChange = () => {
  translationContent.value = ''
  isEditing.value = false
  editContent.value = ''
}

// 编辑
const handleEdit = () => {
  editContent.value = translationContent.value
  isEditing.value = true
}

// 取消编辑
const handleCancel = () => {
  isEditing.value = false
  editContent.value = ''
}

// 保存编辑
const handleSave = async () => {
  if (!targetLanguage.value) {
    ElMessage.warning('请先选择目标语言')
    return
  }
  
  saving.value = true
  try {
    await saveDocumentTranslation(props.docId, targetLanguage.value, editContent.value)
    translationContent.value = editContent.value
    isEditing.value = false
    ElMessage.success('保存成功')
  } catch (error) {
    ElMessage.error('保存失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 加载原文
const loadOriginalText = async () => {
  if (!props.docId) return
  
  try {
    const response = await getDocumentText(props.docId)
    let content = ''
    if (typeof response === 'string') {
      content = response
    } else if (response && typeof response === 'object') {
      content = response.content || response.data?.content || ''
      if (content && typeof content !== 'string') {
        try {
          content = JSON.stringify(content)
        } catch (e) {
          content = String(content)
        }
      }
    }
    originalText.value = content || ''
  } catch (error) {
    console.error('加载原文失败:', error)
    originalText.value = ''
  }
}

// 加载翻译内容
const loadTranslation = async () => {
  if (!props.docId || !targetLanguage.value) return
  
  try {
    const response = await getDocumentTranslation(props.docId, targetLanguage.value)
    let content = ''
    if (typeof response === 'string') {
      content = response
    } else if (response && typeof response === 'object') {
      content = response.content || response.data?.content || ''
      if (content && typeof content !== 'string') {
        try {
          content = JSON.stringify(content)
        } catch (e) {
          content = String(content)
        }
      }
    }
    translationContent.value = content || ''
    return !!translationContent.value
  } catch (error) {
    // 如果获取失败（可能是404），返回false表示没有翻译内容
    return false
  }
}

// 自动翻译
const autoTranslate = async () => {
  if (!props.docId || !targetLanguage.value || translating.value || hasAutoTranslated.value) {
    return
  }
  
  // 先尝试加载已有翻译
  const hasTranslation = await loadTranslation()
  if (hasTranslation) {
    // 已有翻译，不需要自动翻译
    return
  }
  
  // 没有翻译内容，自动翻译
  hasAutoTranslated.value = true
  await handleTranslate()
}

// 监听docId变化，重新加载翻译
watch(() => props.docId, () => {
  if (props.docId) {
    translationContent.value = ''
    originalText.value = ''
    hasAutoTranslated.value = false
    loadOriginalText()
    autoTranslate()
  }
}, { immediate: false })

// 监听目标语言变化，重新加载翻译
watch(() => targetLanguage.value, () => {
  if (props.docId && targetLanguage.value) {
    translationContent.value = ''
    hasAutoTranslated.value = false
    autoTranslate()
  }
})

// 组件挂载时自动翻译
onMounted(() => {
  if (props.docId) {
    loadOriginalText()
    if (targetLanguage.value) {
      autoTranslate()
    }
  }
})
</script>

<style scoped>
.translate-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
}

.header-actions {
  display: flex;
  align-items: center;
}

.edit-actions {
  display: flex;
  gap: 8px;
}

.tab-content {
  flex: 1;
  overflow: hidden;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.edit-input {
  width: 100%;
}

.content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.loading-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: #909399;
}

.loading-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.loading-icon.is-loading {
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

.translation-content {
  flex: 1;
  overflow: auto;
}

.translation-display {
  line-height: 1.8;
  color: #303133;
}

.comparison-view {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.comparison-container {
  display: flex;
  flex: 1;
  overflow: hidden;
  gap: 1px;
  background: #e4e7ed;
}

.text-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color, #ffffff);
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  background: var(--el-bg-color-page, #f5f7fa);
  border-bottom: 1px solid #e4e7ed;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
  flex-shrink: 0;
}

.original-panel .panel-header {
  border-right: 1px solid #e4e7ed;
}

.text-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px;
  line-height: 1.8;
  color: var(--el-text-color-primary, #303133);
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.original-content {
  background: var(--el-bg-color, #ffffff);
}

.translation-content-display {
  background: var(--el-bg-color, #ffffff);
}

/* 同步滚动效果 */
.text-content {
  scrollbar-width: thin;
  scrollbar-color: #c1c1c1 #f1f1f1;
}

.text-content::-webkit-scrollbar {
  width: 8px;
}

.text-content::-webkit-scrollbar-track {
  background: #f1f1f1;
}

.text-content::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.text-content::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

.empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  color: #909399;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  margin: 8px 0;
  text-align: center;
}

.tip {
  font-size: 14px;
  color: #909399;
  text-align: center;
}
</style>

