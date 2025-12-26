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
import { translateDocument, getDocumentTranslation, saveDocumentTranslation } from '@/api/documentReader'
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
const translating = ref(false)
const isEditing = ref(false)
const editContent = ref('')
const saving = ref(false)
const hasAutoTranslated = ref(false) // 标记是否已自动翻译

const renderedTranslation = computed(() => {
  if (!translationContent.value) return ''
  return renderMarkdown(translationContent.value)
})

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
    hasAutoTranslated.value = false
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
  if (props.docId && targetLanguage.value) {
    autoTranslate()
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

