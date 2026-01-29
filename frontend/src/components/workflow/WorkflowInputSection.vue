<template>
  <div class="input-section">
    <h4>输入参数</h4>
    <div class="input-section-body">
      <el-form :model="inputs" :label-width="formLabelWidth">
        <!-- 根据配置动态渲染输入字段 -->
        <el-form-item
          v-for="field in inputFields"
          :key="field.key"
          :label="field.label || field.key"
          :required="field.required"
          :style="field.style || {}"
        >
          <!-- 文本输入 -->
          <el-input
            v-if="field.type === 'text'"
            v-model="inputs[field.key]"
            :placeholder="field.placeholder || `请输入${field.label || field.key}`"
            :style="{ width: (field.style && field.style.width) || '100%' }"
          />
          
          <!-- 多行文本 -->
          <el-input
            v-else-if="field.type === 'textarea'"
            v-model="inputs[field.key]"
            type="textarea"
            :rows="field.rows || 2"
            :placeholder="field.placeholder || `请输入${field.label || field.key}`"
            :style="{ width: (field.style && field.style.width) || '100%' }"
          />
          
          <!-- 数字输入 -->
          <el-input-number
            v-else-if="field.type === 'number'"
            v-model="inputs[field.key]"
            :placeholder="field.placeholder || `请输入${field.label || field.key}`"
            :style="{ width: (field.style && field.style.width) || '100%' }"
          />
          
          <!-- 下拉选择 -->
          <el-select
            v-else-if="field.type === 'select'"
            v-model="inputs[field.key]"
            :placeholder="field.placeholder || `请选择${field.label || field.key}`"
            :style="{ width: (field.style && field.style.width) || '100%' }"
          >
            <el-option
              v-for="option in field.options || []"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          
          <!-- JSON编辑器 -->
          <div v-else-if="field.type === 'json'" class="complex-input">
            <el-input
              v-model="inputsJson[field.key]"
              type="textarea"
              :rows="field.rows || 6"
              :placeholder="field.placeholder || getComplexInputPlaceholder(field.key)"
              @blur="validateAndUpdateJson(field.key)"
              :style="{ width: (field.style && field.style.width) || '100%' }"
            />
            <div class="input-tip">
              <el-text type="info" size="small">
                {{ field.helpText || '支持 JSON 格式，可以是字符串、数组或对象' }}
              </el-text>
            </div>
          </div>
          
          <!-- 日期选择 -->
          <el-date-picker
            v-else-if="field.type === 'date'"
            v-model="inputs[field.key]"
            type="date"
            :placeholder="field.placeholder || `请选择${field.label || field.key}`"
            :style="{ width: (field.style && field.style.width) || '100%' }"
          />
          
          <!-- 开关 -->
          <el-switch
            v-else-if="field.type === 'switch'"
            v-model="inputs[field.key]"
          />
          
          <!-- 默认：文本输入 -->
          <el-input
            v-else
            v-model="inputs[field.key]"
            :placeholder="field.placeholder || `请输入${field.label || field.key}`"
            :style="{ width: (field.style && field.style.width) || '100%' }"
          />
          
          <!-- 帮助文本 -->
          <div v-if="field.helpText" class="input-tip">
            <el-text type="info" size="small">
              {{ field.helpText }}
            </el-text>
          </div>
        </el-form-item>
        
        <!-- 兼容旧格式：如果没有配置字段，使用旧的渲染方式 -->
        <template v-if="inputFields.length === 0">
          <el-form-item
            v-for="(value, key) in inputs"
            :key="key"
            :label="key"
          >
            <!-- 简单字符串输入 -->
            <el-input
              v-if="isSimpleValue(value)"
              v-model="inputs[key]"
              :placeholder="`请输入${key}`"
              type="textarea"
              :rows="2"
            />
            <!-- 复杂结构（数组或对象）使用 JSON 编辑器 -->
            <div v-else class="complex-input">
              <el-input
                v-model="inputsJson[key]"
                type="textarea"
                :rows="6"
                :placeholder="getComplexInputPlaceholder(key)"
                @blur="validateAndUpdateJson(key)"
              />
              <div class="input-tip">
                <el-text type="info" size="small">
                  支持 JSON 格式，可以是字符串、数组或对象
                </el-text>
              </div>
            </div>
          </el-form-item>
        </template>
        
        <!-- 文件上传区域 -->
        <el-form-item v-if="appInfo?.fileUploadEnabled" label="文件上传">
          <el-upload
            ref="uploadRef"
            v-model:file-list="fileList"
            :auto-upload="false"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :limit="10"
            multiple
            drag
            list-type="text"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              将文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                单个文件不超过10MB,选择文件后将立即上传到Dify。
              </div>
            </template>
            <template #file="{ file }">
              <div class="upload-file-item">
                <div class="file-info">
                  <el-icon class="file-icon">
                    <Document v-if="!isImageFile(file)" />
                    <Picture v-else />
                  </el-icon>
                  <span class="file-name">{{ file.name }}</span>
                  <span class="file-size">{{ formatFileSize(file.size) }}</span>
                  <el-tag 
                    :type="getFileStatusType(file.status)" 
                    size="small"
                    class="file-status"
                  >
                    {{ getFileStatusText(file.status) }}
                  </el-tag>
                </div>
                <!-- 上传成功/失败信息 -->
                <div v-if="file.status === 'success'" class="file-success">
                  <el-icon class="success-icon"><Check /></el-icon>
                  <span>上传成功</span>
                </div>
                <div v-if="file.status === 'fail'" class="file-error">
                  <el-icon class="error-icon"><Close /></el-icon>
                  <span>上传失败</span>
                </div>
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
    </div>
    <div class="input-actions-bar">
      <el-button type="primary" @click="$emit('run')" :loading="loading">运行</el-button>
      <el-button @click="$emit('clear')">清空</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { UploadFilled, Document, Picture, Check, Close } from '@element-plus/icons-vue'

const props = defineProps({
  appInfo: {
    type: Object,
    default: null
  },
  inputs: {
    type: Object,
    required: true
  },
  inputsJson: {
    type: Object,
    required: true
  },
  inputFields: {
    type: Array,
    default: () => []
  },
  formLabelWidth: {
    type: String,
    default: '140px'
  },
  fileList: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['run', 'clear', 'file-change', 'file-remove', 'update:inputs', 'update:inputsJson'])

const uploadRef = ref(null)

const isSimpleValue = (value) => {
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean' || value === null
}

const getComplexInputPlaceholder = (key) => {
  return `请输入 ${key} 的 JSON 格式，例如：\n字符串: "value"\n数组: ["item1", "item2"]\n对象: {"key": "value"}`
}

const validateAndUpdateJson = (key) => {
  const jsonStr = props.inputsJson[key]
  if (!jsonStr || jsonStr.trim() === '') {
    emit('update:inputs', { ...props.inputs, [key]: null })
    return
  }
  
  try {
    const parsed = JSON.parse(jsonStr)
    emit('update:inputs', { ...props.inputs, [key]: parsed })
  } catch (e) {
    console.error(`JSON解析失败 - ${key}:`, e)
  }
}

const isImageFile = (file) => {
  if (!file || !file.name) return false
  const imageExtensions = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp']
  const fileName = file.name.toLowerCase()
  return imageExtensions.some(ext => fileName.endsWith(ext))
}

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const getFileStatusType = (status) => {
  const statusMap = {
    'ready': 'info',
    'uploading': 'warning',
    'success': 'success',
    'fail': 'danger'
  }
  return statusMap[status] || 'info'
}

const getFileStatusText = (status) => {
  const statusMap = {
    'ready': '待上传',
    'uploading': '上传中',
    'success': '已上传',
    'fail': '上传失败'
  }
  return statusMap[status] || '未知'
}

const handleFileChange = (file, fileList) => {
  emit('file-change', file, fileList)
}

const handleFileRemove = (file, fileList) => {
  emit('file-remove', file, fileList)
}
</script>

<style scoped>
/* 从 WorkflowApp.vue 复制相关样式 */
.input-section {
  margin-bottom: var(--spacing-lg, 20px);
  padding: var(--spacing-lg, 20px);
  background: var(--el-bg-color, #ffffff);
  border-radius: var(--el-border-radius-base, 4px);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

.input-section h4 {
  margin: 0 0 var(--spacing-md, 16px) 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.input-section-body {
  margin-bottom: var(--spacing-md, 16px);
}

.input-actions-bar {
  display: flex;
  gap: var(--spacing-sm, 8px);
  justify-content: flex-end;
  padding-top: var(--spacing-md, 16px);
  border-top: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

.complex-input {
  width: 100%;
}

.input-tip {
  margin-top: var(--spacing-xs, 4px);
}

.upload-file-item {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs, 4px);
  padding: var(--spacing-sm, 8px);
}

.file-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm, 8px);
}

.file-icon {
  font-size: 18px;
  color: var(--el-color-primary, #409eff);
}

.file-name {
  flex: 1;
  font-size: 14px;
  color: var(--el-text-color-primary, #303133);
}

.file-size {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.file-success,
.file-error {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs, 4px);
  font-size: 12px;
}

.file-success {
  color: var(--el-color-success, #67c23a);
}

.file-error {
  color: var(--el-color-danger, #f56c6c);
}
</style>
