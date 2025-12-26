<template>
  <div class="guide-tab">
    <div class="tab-header">
      <div class="header-info">
        <el-icon><Reading /></el-icon>
        <span>导读</span>
      </div>
      <div v-if="!isEditing" class="header-actions">
        <el-button
          type="success"
          size="small"
          @click="handleGenerate"
          :loading="generating"
          :disabled="generating"
        >
          <el-icon><MagicStick /></el-icon>
          重新生成
        </el-button>
        <el-button
          type="primary"
          size="small"
          @click="handleEdit"
          :disabled="generating || !guideContent"
        >
          <el-icon><Edit /></el-icon>
          编辑导读
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
        placeholder="请输入导读内容..."
        class="edit-input"
      />
      <div v-else class="content-display">
        <div v-if="generating" class="loading-state">
          <el-icon class="loading-icon is-loading"><Loading /></el-icon>
          <p>正在生成导读，请稍候...</p>
        </div>
        <div v-else-if="guideContent" class="guide-content" v-html="renderedContent"></div>
        <div v-else class="empty-state">
          <el-icon class="empty-icon"><Document /></el-icon>
          <p>暂无导读内容</p>
          <div class="empty-actions">
            <el-button type="success" size="small" @click="handleGenerate" :loading="generating">
              <el-icon><MagicStick /></el-icon>
              生成导读
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Reading, Edit, Document, MagicStick, Loading } from '@element-plus/icons-vue'
import { getDocumentGuide, saveDocumentGuide, generateDocumentGuide } from '@/api/documentReader'
import { renderMarkdown } from '@/composables/useMarkdown'

const props = defineProps({
  docId: {
    type: Number,
    required: true
  }
})

const guideContent = ref('')
const isEditing = ref(false)
const editContent = ref('')
const saving = ref(false)
const generating = ref(false)

const renderedContent = computed(() => {
  if (!guideContent.value) return ''
  return renderMarkdown(guideContent.value)
})

// 加载导读内容
const loadGuide = async () => {
  try {
    const response = await getDocumentGuide(props.docId)
    // 后端返回格式: { content: "..." } 或直接是字符串
    let content = ''
    if (typeof response === 'string') {
      content = response
    } else if (response && typeof response === 'object') {
      // 如果是对象，尝试获取 content 字段
      content = response.content || response.data?.content || ''
      // 如果 content 仍然是对象，转换为 JSON 字符串（不应该发生，但做保护）
      if (content && typeof content !== 'string') {
        try {
          content = JSON.stringify(content)
        } catch (e) {
          content = String(content)
        }
      }
    }
    guideContent.value = content || ''
    
    // 如果没有导读内容，自动生成
    if (!guideContent.value || guideContent.value.trim() === '') {
      await autoGenerateGuide()
    }
  } catch (error) {
    console.error('加载导读失败:', error)
    // 如果接口返回404或空数据，说明还没有导读内容，自动生成
    guideContent.value = ''
    await autoGenerateGuide()
  }
}

// 自动生成导读（不显示确认对话框）
const autoGenerateGuide = async () => {
  if (generating.value) return // 防止重复生成
  
  generating.value = true
  try {
    const response = await generateDocumentGuide(props.docId)
    // 后端返回格式: { content: "..." }
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
    
    guideContent.value = content || ''
    if (guideContent.value) {
      ElMessage.success('导读生成成功')
    } else {
      ElMessage.warning('导读生成完成，但内容为空')
    }
  } catch (error) {
    console.error('自动生成导读失败:', error)
    ElMessage.error('自动生成导读失败：' + (error.message || '未知错误'))
  } finally {
    generating.value = false
  }
}

// 编辑
const handleEdit = () => {
  editContent.value = guideContent.value
  isEditing.value = true
}

// 取消编辑
const handleCancel = () => {
  isEditing.value = false
  editContent.value = ''
}

// 手动生成导读（显示确认对话框）
const handleGenerate = async () => {
  try {
    await ElMessageBox.confirm(
      '将使用大模型重新生成文档导读，可能需要一些时间，是否继续？',
      '重新生成导读',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    generating.value = true
    try {
      const response = await generateDocumentGuide(props.docId)
      // 后端返回格式: { content: "..." }
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
      
      guideContent.value = content || ''
      ElMessage.success('导读生成成功')
    } catch (error) {
      ElMessage.error('生成导读失败：' + (error.message || '未知错误'))
    } finally {
      generating.value = false
    }
  } catch (error) {
    // 用户取消
    if (error !== 'cancel') {
      console.error('生成导读失败:', error)
    }
  }
}

// 保存
const handleSave = async () => {
  saving.value = true
  try {
    await saveDocumentGuide(props.docId, editContent.value)
    guideContent.value = editContent.value
    isEditing.value = false
    ElMessage.success('保存成功')
  } catch (error) {
    ElMessage.error('保存失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 监听docId变化
watch(() => props.docId, () => {
  if (props.docId) {
    loadGuide()
  }
}, { immediate: true })

onMounted(() => {
  if (props.docId) {
    loadGuide()
  }
})
</script>

<style scoped>
.guide-tab {
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
  gap: 8px;
}

.edit-actions {
  display: flex;
  gap: 8px;
}

.tab-content {
  flex: 1;
  overflow: hidden;
  padding: 8px;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.edit-input {
  width: 100%;
  flex: 1;
}

.content-display {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.guide-content {
  line-height: 1.8;
  color: #303133;
  background: #ffffff;
  padding: 24px;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow-y: auto;
  overflow-x: hidden;
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
}

.loading-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  min-height: 100%;
  color: #909399;
}

.loading-icon {
  font-size: 48px;
  margin-bottom: 16px;
  animation: rotating 2s linear infinite;
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.loading-state p {
  margin: 8px 0;
  font-size: 14px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  min-height: 100%;
  color: #909399;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  margin: 8px 0 16px;
}

.empty-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
</style>

