<template>
  <div class="guide-tab">
    <div class="tab-header">
      <div class="header-info">
        <el-icon><Reading /></el-icon>
        <span>导读</span>
      </div>
      <div v-if="!isEditing" class="header-actions">
        <el-tooltip content="重新生成" placement="bottom">
          <el-button
            type="success"
            size="small"
            @click="handleGenerate"
            :loading="generating"
            :disabled="generating"
            circle
          >
            <el-icon><MagicStick /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="编辑导读" placement="bottom">
          <el-button
            type="primary"
            size="small"
            @click="handleEdit"
            :disabled="generating || !guideContent"
            circle
          >
            <el-icon><Edit /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
      <div v-else class="edit-actions">
        <el-tooltip content="取消" placement="bottom">
          <el-button size="small" @click="handleCancel" circle>
            <el-icon><Close /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="保存" placement="bottom">
          <el-button type="primary" size="small" @click="handleSave" :loading="saving" circle>
            <el-icon><Check /></el-icon>
          </el-button>
        </el-tooltip>
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
            <el-tooltip content="生成导读" placement="top">
              <el-button type="success" size="small" @click="handleGenerate" :loading="generating" circle>
                <el-icon><MagicStick /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Reading, Edit, Document, MagicStick, Loading, Close, Check } from '@element-plus/icons-vue'
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
  return guideContent.value ? renderMarkdown(guideContent.value) : ''
})

// 解析响应内容的通用方法
const parseResponseContent = (response) => {
  if (typeof response === 'string') return response
  
  if (response && typeof response === 'object') {
    const content = response.content || response.data?.content || ''
    return typeof content === 'string' ? content : String(content)
  }
  
  return ''
}

// 加载导读内容
const loadGuide = async () => {
  try {
    const response = await getDocumentGuide(props.docId)
    guideContent.value = parseResponseContent(response)
    
    // 如果没有导读内容，自动生成
    if (!guideContent.value.trim()) {
      await autoGenerateGuide()
    }
  } catch (error) {
    // 接口异常时自动生成导读
    guideContent.value = ''
    await autoGenerateGuide()
  }
}

// 生成导读的通用方法
const generateGuide = async (showMessage = true) => {
  if (generating.value) return
  
  generating.value = true
  try {
    const response = await generateDocumentGuide(props.docId)
    guideContent.value = parseResponseContent(response)
    
    if (showMessage) {
      if (guideContent.value) {
        ElMessage.success('导读生成成功')
      } else {
        ElMessage.warning('导读生成完成，但内容为空')
      }
    }
  } catch (error) {
    if (showMessage) {
      ElMessage.error('生成导读失败：' + (error.message || '未知错误'))
    }
  } finally {
    generating.value = false
  }
}

// 自动生成导读（不显示消息提示）
const autoGenerateGuide = async () => {
  await generateGuide(false)
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
    
    await generateGuide(true)
  } catch (error) {
    // 用户取消操作，静默处理
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

// 监听docId变化，immediate: true 会在组件挂载时自动执行一次
watch(() => props.docId, () => {
  if (props.docId) {
    loadGuide()
  }
}, { immediate: true })
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
  font-size: 15px;
  letter-spacing: 0.3px;
}

/* Markdown 标题样式 */
.guide-content :deep(h1) {
  font-size: 1.75em;
  font-weight: 600;
  color: #303133;
  margin: 24px 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #e4e7ed;
  line-height: 1.5;
}

.guide-content :deep(h1:first-child) {
  margin-top: 0;
}

.guide-content :deep(h2) {
  font-size: 1.5em;
  font-weight: 600;
  color: #409eff;
  margin: 20px 0 12px 0;
  padding-bottom: 6px;
  border-bottom: 1px solid #ecf5ff;
  line-height: 1.5;
}

.guide-content :deep(h3) {
  font-size: 1.25em;
  font-weight: 600;
  color: #606266;
  margin: 16px 0 10px 0;
  line-height: 1.5;
}

.guide-content :deep(h4) {
  font-size: 1.1em;
  font-weight: 600;
  color: #606266;
  margin: 14px 0 8px 0;
  line-height: 1.5;
}

/* 段落样式 */
.guide-content :deep(p) {
  margin: 12px 0;
  line-height: 1.8;
  color: #303133;
}

/* 列表样式 */
.guide-content :deep(ul),
.guide-content :deep(ol) {
  margin: 12px 0;
  padding-left: 24px;
  line-height: 1.8;
}

.guide-content :deep(li) {
  margin: 6px 0;
  line-height: 1.8;
}

.guide-content :deep(ul li) {
  list-style-type: disc;
}

.guide-content :deep(ol li) {
  list-style-type: decimal;
}

.guide-content :deep(ul ul),
.guide-content :deep(ol ol),
.guide-content :deep(ul ol),
.guide-content :deep(ol ul) {
  margin: 4px 0;
  padding-left: 20px;
}

/* 强调样式 */
.guide-content :deep(strong),
.guide-content :deep(b) {
  font-weight: 600;
  color: #303133;
}

.guide-content :deep(em),
.guide-content :deep(i) {
  font-style: italic;
  color: #606266;
}

/* 引用样式 */
.guide-content :deep(blockquote) {
  margin: 12px 0;
  padding: 12px 16px;
  border-left: 4px solid #409eff;
  background-color: #f5f7fa;
  color: #606266;
  border-radius: 4px;
  font-style: italic;
}

.guide-content :deep(blockquote p) {
  margin: 0;
}

/* 代码样式 */
.guide-content :deep(code) {
  background-color: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.9em;
  color: #e6a23c;
}

.guide-content :deep(pre) {
  background-color: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 12px 0;
  border: 1px solid #e4e7ed;
}

.guide-content :deep(pre code) {
  background-color: transparent;
  padding: 0;
  color: #303133;
  font-size: 0.9em;
}

/* 分隔线样式 */
.guide-content :deep(hr) {
  margin: 20px 0;
  border: none;
  border-top: 1px solid #e4e7ed;
}

/* 链接样式 */
.guide-content :deep(a) {
  color: #409eff;
  text-decoration: none;
  transition: color 0.3s;
}

.guide-content :deep(a:hover) {
  color: #66b1ff;
  text-decoration: underline;
}

/* 表格样式 */
.guide-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 0.95em;
}

.guide-content :deep(th),
.guide-content :deep(td) {
  padding: 8px 12px;
  border: 1px solid #e4e7ed;
  text-align: left;
}

.guide-content :deep(th) {
  background-color: #f5f7fa;
  font-weight: 600;
  color: #303133;
}

.guide-content :deep(tr:nth-child(even)) {
  background-color: #fafafa;
}

/* 滚动条样式 */
.guide-content::-webkit-scrollbar {
  width: 8px;
}

.guide-content::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

.guide-content::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.guide-content::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 截断提示样式优化 */
.guide-content :deep(hr + blockquote) {
  margin-top: 16px;
  border-left-color: #e6a23c;
  background-color: #fdf6ec;
  padding: 12px 16px;
}

.guide-content :deep(hr + blockquote strong) {
  color: #e6a23c;
  font-weight: 600;
}

.guide-content :deep(hr) {
  margin: 20px 0;
  border: none;
  border-top: 1px solid #e4e7ed;
  opacity: 0.6;
}

.loading-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
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

