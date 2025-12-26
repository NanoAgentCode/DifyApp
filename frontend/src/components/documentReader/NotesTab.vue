<template>
  <div class="notes-tab">
    <div class="tab-header">
      <div class="header-info">
        <el-icon><EditPen /></el-icon>
        <span>笔记</span>
      </div>
      <div v-if="!isEditing" class="header-actions">
        <el-button
          type="primary"
          size="small"
          @click="handleEdit"
        >
          <el-icon><Edit /></el-icon>
          编辑笔记
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
      <!-- 预览模式 -->
      <div v-if="!isEditing" class="preview-mode">
        <div v-if="notesContent" class="notes-preview" v-html="renderedContent"></div>
        <div v-else class="empty-state">
          <el-icon class="empty-icon"><Document /></el-icon>
          <p>暂无笔记内容</p>
          <el-button type="primary" size="small" @click="handleEdit">
            开始编辑
          </el-button>
        </div>
      </div>
      
      <!-- 编辑模式 -->
      <div v-else class="edit-mode">
        <div class="editor-toolbar">
          <el-button-group>
            <el-button size="small" @click="formatText('bold')" title="粗体">
              <strong>B</strong>
            </el-button>
            <el-button size="small" @click="formatText('italic')" title="斜体">
              <em>I</em>
            </el-button>
            <el-button size="small" @click="formatText('underline')" title="下划线">
              <u>U</u>
            </el-button>
          </el-button-group>
          <el-button-group style="margin-left: 8px">
            <el-button size="small" @click="formatText('h1')" title="标题1">H1</el-button>
            <el-button size="small" @click="formatText('h2')" title="标题2">H2</el-button>
            <el-button size="small" @click="formatText('h3')" title="标题3">H3</el-button>
          </el-button-group>
          <el-button-group style="margin-left: 8px">
            <el-button size="small" @click="formatText('ul')" title="无序列表">
              <el-icon><List /></el-icon>
            </el-button>
            <el-button size="small" @click="formatText('ol')" title="有序列表">
              <el-icon><List /></el-icon>
            </el-button>
          </el-button-group>
          <el-button-group style="margin-left: 8px">
            <el-button size="small" @click="formatText('link')" title="链接">
              <el-icon><Link /></el-icon>
            </el-button>
            <el-button size="small" @click="formatText('image')" title="图片">
              <el-icon><Picture /></el-icon>
            </el-button>
          </el-button-group>
        </div>
        
        <el-input
          v-model="editContent"
          type="textarea"
          :rows="20"
          placeholder="开始记录你的灵感和思考..."
          class="notes-editor"
        />
        
        <div class="editor-footer">
          <span class="save-status" v-if="lastSavedTime">
            <el-icon><Check /></el-icon>
            已保存 {{ lastSavedTime }}
          </span>
          <span v-else class="save-status">
            <el-icon><Clock /></el-icon>
            未保存
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, Check, List, Link, Picture, Clock, Edit, Document } from '@element-plus/icons-vue'
import { getDocumentNotes, saveDocumentNotes } from '@/api/documentReader'
import { renderMarkdown } from '@/composables/useMarkdown'

const props = defineProps({
  docId: {
    type: Number,
    required: true
  }
})

const notesContent = ref('')
const editContent = ref('')
const isEditing = ref(false)
const saving = ref(false)
const lastSavedTime = ref('')
let autoSaveTimer = null

const renderedContent = computed(() => {
  if (!notesContent.value) return ''
  return renderMarkdown(notesContent.value)
})

// 格式化文本
const formatText = (command) => {
  const textarea = document.querySelector('.notes-editor textarea')
  if (!textarea) return
  
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selectedText = editContent.value.substring(start, end)
  let replacement = ''
  
  switch (command) {
    case 'bold':
      replacement = `**${selectedText || '粗体文本'}**`
      break
    case 'italic':
      replacement = `*${selectedText || '斜体文本'}*`
      break
    case 'underline':
      replacement = `<u>${selectedText || '下划线文本'}</u>`
      break
    case 'h1':
      replacement = `# ${selectedText || '标题1'}`
      break
    case 'h2':
      replacement = `## ${selectedText || '标题2'}`
      break
    case 'h3':
      replacement = `### ${selectedText || '标题3'}`
      break
    case 'ul':
      replacement = `- ${selectedText || '列表项'}`
      break
    case 'ol':
      replacement = `1. ${selectedText || '列表项'}`
      break
    case 'link':
      replacement = `[${selectedText || '链接文本'}](url)`
      break
    case 'image':
      replacement = `![${selectedText || '图片描述'}](url)`
      break
  }
  
  const newContent = 
    editContent.value.substring(0, start) + 
    replacement + 
    editContent.value.substring(end)
  
  editContent.value = newContent
  
  // 恢复焦点和光标位置
  nextTick(() => {
    textarea.focus()
    const newPos = start + replacement.length
    textarea.setSelectionRange(newPos, newPos)
  })
}

// 编辑
const handleEdit = () => {
  editContent.value = notesContent.value
  isEditing.value = true
}

// 取消编辑
const handleCancel = () => {
  isEditing.value = false
  editContent.value = ''
}

// 保存笔记
const handleSave = async () => {
  saving.value = true
  try {
    await saveDocumentNotes(props.docId, editContent.value)
    notesContent.value = editContent.value
    isEditing.value = false
    lastSavedTime.value = new Date().toLocaleTimeString('zh-CN')
    ElMessage.success('保存成功')
  } catch (error) {
    ElMessage.error('保存失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 加载笔记
const loadNotes = async () => {
  try {
    const response = await getDocumentNotes(props.docId)
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
    notesContent.value = content || ''
  } catch (error) {
    console.error('加载笔记失败:', error)
    notesContent.value = ''
  }
}

// 监听docId变化
watch(() => props.docId, () => {
  if (props.docId) {
    loadNotes()
  }
}, { immediate: true })

onMounted(() => {
  if (props.docId) {
    loadNotes()
  }
})
</script>

<style scoped>
.notes-tab {
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

.tab-content {
  flex: 1;
  overflow: hidden;
  padding: 8px;
  display: flex;
  flex-direction: column;
}

.preview-mode {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.notes-preview {
  flex: 1;
  background: #ffffff;
  padding: 24px;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow-y: auto;
  overflow-x: hidden;
  line-height: 1.8;
  color: #303133;
}

.empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: #909399;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  margin: 8px 0 16px;
}

.edit-actions {
  display: flex;
  gap: 8px;
}

.edit-mode {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor-toolbar {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
  flex-shrink: 0;
}

.notes-editor {
  flex: 1;
  overflow: auto;
}

.notes-editor :deep(.el-textarea__inner) {
  border: none;
  resize: none;
  font-family: 'Courier New', monospace;
  line-height: 1.6;
  padding: 16px;
}

.editor-footer {
  padding: 8px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
}

.save-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}
</style>

