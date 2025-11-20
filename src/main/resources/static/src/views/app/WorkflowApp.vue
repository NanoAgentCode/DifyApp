<template>
  <div class="workflow-app">
    <el-card class="workflow-container">
      <template #header>
        <div class="workflow-header">
          <h3>{{ appInfo?.name || '工作流应用' }}</h3>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <div class="workflow-content">
        <div class="input-section">
          <h4>输入参数</h4>
          <el-form :model="inputs" label-width="140px">
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
              >
                <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                <div class="el-upload__text">
                  将文件拖到此处，或<em>点击上传</em>
                </div>
                <template #tip>
                  <div class="el-upload__tip">
                    支持上传多个文件，单个文件不超过10MB。选择文件后将立即上传到Dify。
                  </div>
                </template>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleRun" :loading="loading">
                运行工作流
              </el-button>
              <el-button @click="handleClear">清空</el-button>
              <el-button @click="showInputsJson = !showInputsJson" type="info" size="small">
                {{ showInputsJson ? '隐藏' : '显示' }}完整 JSON
              </el-button>
            </el-form-item>
            <!-- 完整 JSON 编辑器 -->
            <el-form-item v-if="showInputsJson" label="完整 JSON">
              <el-input
                v-model="fullInputsJson"
                type="textarea"
                :rows="10"
                :placeholder="fullJsonPlaceholder"
                @blur="validateAndUpdateFullJson"
              />
            </el-form-item>
          </el-form>
        </div>

        <div class="output-section">
          <h4>输出结果</h4>
          <el-card v-loading="loading">
            <div v-if="result" class="result-content">
              <div v-if="result.error" class="error-message">
                <el-alert
                  :title="result.error"
                  type="error"
                  :closable="false"
                  show-icon
                />
                <div v-if="result.tip" class="error-tip">
                  {{ result.tip }}
                </div>
              </div>
              <div v-else>
                <!-- 文件预览区域 -->
                <div v-if="hasPreviewableFiles(result)" class="file-preview-section">
                  <div v-for="(file, index) in extractFiles(result)" :key="index" class="file-preview-item">
                    <div class="file-info">
                      <span class="file-name">{{ file.filename || `文件 ${index + 1}` }}</span>
                      <span class="file-type">{{ file.type || file.mime_type || '未知' }}</span>
                      <el-button size="small" @click="openFile(file.fullUrl)" type="primary" link>
                        在新窗口打开
                      </el-button>
                    </div>
                    <!-- 图片预览 -->
                    <div v-if="isImage(file)" class="image-preview">
                      <img :src="file.fullUrl" :alt="file.filename" @error="handleImageError" />
                    </div>
                    <!-- HTML预览 -->
                    <div v-else-if="isHtml(file)" class="html-preview">
                      <iframe :src="file.fullUrl" frameborder="0" class="preview-iframe"></iframe>
                    </div>
                    <!-- PDF预览 -->
                    <div v-else-if="isPdf(file)" class="pdf-preview">
                      <iframe :src="file.fullUrl" frameborder="0" class="preview-iframe"></iframe>
                    </div>
                    <!-- 其他文件类型显示链接 -->
                    <div v-else class="file-link">
                      <el-link :href="file.fullUrl" target="_blank" type="primary">
                        {{ file.filename || file.url }}
                      </el-link>
                    </div>
                  </div>
                </div>
                <!-- JSON数据展示（仅在没有文件时显示） -->
                <pre v-if="!hasPreviewableFiles(result)">{{ formatResult(result) }}</pre>
              </div>
            </div>
            <div v-else class="result-placeholder">
              运行工作流后，结果将显示在这里
            </div>
          </el-card>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getAppDetail, workflowApp, workflowAppStream, uploadFile } from '@/api/aiApp'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const appInfo = ref(null)
const inputs = reactive({})
const inputsJson = reactive({}) // 用于存储复杂输入的 JSON 字符串
const result = ref(null)
const loading = ref(false)
const showInputsJson = ref(false) // 是否显示完整 JSON 编辑器
const fullInputsJson = ref('') // 完整 JSON 字符串
const fullJsonPlaceholder = '{"variable_name": [{"transfer_method": "local_file", "upload_file_id": "file_id", "type": "document"}]}'
const fileUrlPrefix = ref('http://localhost:80') // 文件URL前缀
const fileList = ref([]) // 文件列表
const uploadRef = ref(null) // 上传组件引用

// 获取复杂输入的占位符
const getComplexInputPlaceholder = (key) => {
  return `请输入 ${key} 的 JSON 格式，例如：\n[\n  {\n    "transfer_method": "local_file",\n    "upload_file_id": "file_id",\n    "type": "document"\n  }\n]`
}

// 判断是否为简单值（字符串、数字、布尔值）
const isSimpleValue = (value) => {
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean' || value === null || value === undefined
}

// 初始化输入值
const initializeInput = (key, value) => {
  if (isSimpleValue(value)) {
    inputs[key] = value !== null && value !== undefined ? String(value) : ''
    inputsJson[key] = ''
  } else {
    // 复杂结构，存储为 JSON 字符串
    inputs[key] = value
    try {
      inputsJson[key] = JSON.stringify(value, null, 2)
    } catch (e) {
      inputsJson[key] = ''
    }
  }
}

const fetchAppInfo = async () => {
  try {
    const res = await getAppDetail(route.params.id)
    appInfo.value = res
    
    // 清空之前的输入
    Object.keys(inputs).forEach(key => {
      delete inputs[key]
    })
    Object.keys(inputsJson).forEach(key => {
      delete inputsJson[key]
    })
    fullInputsJson.value = ''
    
    // 解析inputs配置，初始化输入表单
    if (res.inputs) {
      try {
        const inputsConfig = JSON.parse(res.inputs)
        if (typeof inputsConfig === 'object' && inputsConfig !== null) {
          Object.keys(inputsConfig).forEach(key => {
            initializeInput(key, inputsConfig[key])
          })
          // 更新完整 JSON
          fullInputsJson.value = JSON.stringify(inputs, null, 2)
        } else {
          // 如果解析的不是对象，使用默认输入
          console.warn('inputs 配置格式不正确，使用默认配置')
          inputs['word'] = ''
          inputsJson['word'] = ''
        }
      } catch (e) {
        console.error('解析 inputs 配置失败:', e)
        // 如果解析失败，使用默认输入
        inputs['word'] = ''
        inputsJson['word'] = ''
        ElMessage.warning('应用配置中的 inputs 格式不正确，已使用默认配置。请检查应用配置。')
      }
    } else {
      // 如果没有配置，使用默认输入
      inputs['word'] = ''
      inputsJson['word'] = ''
    }
  } catch (error) {
    ElMessage.error('获取应用信息失败')
  }
}

// 验证并更新单个 JSON 输入
const validateAndUpdateJson = (key) => {
  const jsonStr = inputsJson[key]
  if (!jsonStr || !jsonStr.trim()) {
    // 如果为空，保持原值
    return
  }
  try {
    const parsed = JSON.parse(jsonStr)
    inputs[key] = parsed
    ElMessage.success(`${key} 格式正确`)
  } catch (e) {
    ElMessage.error(`${key} JSON 格式错误: ${e.message}`)
    // 恢复为原始值
    try {
      inputsJson[key] = JSON.stringify(inputs[key], null, 2)
    } catch (e2) {
      inputsJson[key] = ''
    }
  }
}

// 验证并更新完整 JSON
const validateAndUpdateFullJson = () => {
  if (!fullInputsJson.value || !fullInputsJson.value.trim()) {
    return
  }
  try {
    const parsed = JSON.parse(fullInputsJson.value)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      throw new Error('必须是对象格式')
    }
    // 清空并重新初始化
    Object.keys(inputs).forEach(key => {
      delete inputs[key]
    })
    Object.keys(inputsJson).forEach(key => {
      delete inputsJson[key]
    })
    Object.keys(parsed).forEach(key => {
      initializeInput(key, parsed[key])
    })
    ElMessage.success('JSON 格式正确，已更新所有输入')
  } catch (e) {
    ElMessage.error(`JSON 格式错误: ${e.message}`)
    // 恢复为原始值
    fullInputsJson.value = JSON.stringify(inputs, null, 2)
  }
}

const handleRun = async () => {
  loading.value = true
  result.value = null

  try {
    // 先验证所有 JSON 输入
    Object.keys(inputsJson).forEach(key => {
      if (inputsJson[key] && inputsJson[key].trim()) {
        validateAndUpdateJson(key)
      }
    })

    // 构建输入对象，保留所有非空值
    const filteredInputs = {}
    Object.keys(inputs).forEach(key => {
      const value = inputs[key]
      // 对于简单值，过滤空字符串
      if (isSimpleValue(value)) {
        if (value !== null && value !== undefined && value !== '') {
          filteredInputs[key] = value
        }
      } else {
        // 对于复杂结构，直接保留（可能是空数组或空对象）
        filteredInputs[key] = value
      }
    })

    // 生成用户ID（用于工作流请求）
    const userId = 'user_' + Date.now()
    
    // 获取已上传的文件列表（文件在选择时已上传）
    let uploadedFiles = []
    if (appInfo.value?.fileUploadEnabled && fileList.value.length > 0) {
      uploadedFiles = getUploadedFiles()
      
      // 检查是否有未上传成功的文件
      const failedFiles = fileList.value.filter(f => f.status === 'fail' || (f.status !== 'success' && f.raw))
      if (failedFiles.length > 0) {
        ElMessage.warning('部分文件上传失败，请重新上传或移除失败的文件')
        throw new Error('存在上传失败的文件')
      }
      
      // 检查是否有正在上传的文件
      const uploadingFiles = fileList.value.filter(f => f.status === 'uploading')
      if (uploadingFiles.length > 0) {
        ElMessage.warning('文件正在上传中，请稍候...')
        throw new Error('文件正在上传中')
      }
    }

    const requestData = {
      userId: userId,
      inputs: filteredInputs,
      stream: appInfo.value?.streamEnabled || false,
      files: uploadedFiles.length > 0 ? uploadedFiles : undefined
    }

    console.log('发送请求数据:', JSON.stringify(requestData, null, 2))

    if (appInfo.value?.streamEnabled) {
      await handleStreamWorkflow(requestData)
    } else {
      await handleNormalWorkflow(requestData)
    }
  } catch (error) {
    // 检查是否是参数缺失错误
    const errorMessage = error.message || error.toString()
    if (errorMessage.includes('缺少必需的输入参数')) {
      ElMessage.error(errorMessage)
      result.value = { 
        error: errorMessage,
        tip: '请在应用配置中正确设置 inputs 字段，包含所有必需的参数。例如：{"word": ""} 或 {"variable_name": [{"transfer_method": "local_file", "upload_file_id": "file_id", "type": "document"}]}'
      }
    } else if (errorMessage.includes('timeout') || errorMessage.includes('超时') || error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，Workflow任务可能需要更长时间处理。请稍后重试或使用流式接口。')
      result.value = { 
        error: '请求超时',
        tip: 'Workflow任务可能需要较长时间处理。如果任务仍在运行，请稍后查看结果，或考虑使用流式接口以获得实时反馈。'
      }
    } else {
      ElMessage.error('运行工作流失败: ' + (errorMessage || '未知错误'))
      result.value = { error: '运行失败，请稍后重试' }
    }
  } finally {
    loading.value = false
  }
}

const handleNormalWorkflow = async (requestData) => {
  const res = await workflowApp(route.params.id, requestData)
  result.value = res
}

const handleStreamWorkflow = async (requestData) => {
  // 流式响应处理
  let streamResult = ''

  try {
    const response = await fetch(`/api/ai-apps/${route.params.id}/workflow/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestData)
    })

    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value)
      const lines = chunk.split('\n')

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.substring(6)
          if (data === '[DONE]') continue

          try {
            const json = JSON.parse(data)
            if (json.answer) {
              streamResult += json.answer
            }
            if (json.metadata) {
              result.value = { answer: streamResult, metadata: json.metadata }
            } else {
              result.value = { answer: streamResult }
            }
          } catch (e) {
            // 忽略解析错误
          }
        }
      }
    }
  } catch (error) {
    throw error
  }
}

// 处理文件变化（选择文件后立即上传）
const handleFileChange = async (file, files) => {
  // 检查文件大小（10MB限制）
  if (file.raw && file.raw.size > 10 * 1024 * 1024) {
    ElMessage.error(`文件 ${file.name} 超过10MB限制`)
    const index = fileList.value.findIndex(f => f.uid === file.uid)
    if (index > -1) {
      fileList.value.splice(index, 1)
    }
    return
  }
  
  fileList.value = files
  
  // 如果文件已上传成功，跳过
  if (file.status === 'success' && file.uploadFileId) {
    return
  }
  
  // 立即上传新选择的文件（包括重新上传失败的文件）
  if (file.raw && appInfo.value?.fileUploadEnabled) {
    await uploadSingleFile(file)
  }
}

// 处理文件移除
const handleFileRemove = (file, files) => {
  fileList.value = files
}

// 上传单个文件到Dify（选择文件后立即上传，通过后端转发）
const uploadSingleFile = async (fileItem) => {
  if (!fileItem.raw) {
    return
  }
  
  // 设置上传状态
  fileItem.status = 'uploading'
  fileItem.percentage = 0
  
  try {
    const formData = new FormData()
    
    // 直接使用文件对象，FormData 会自动处理文件编码
    formData.append('file', fileItem.raw)
    
    // 生成用户ID（用于文件上传）
    const userId = 'user_' + Date.now()
    formData.append('user', userId)
    
    // 通过后端接口上传文件
    const result = await uploadFile(route.params.id, formData, (percentage) => {
      fileItem.percentage = percentage
    })
    
    if (result && result.id) {
      // 保存上传后的文件信息
      fileItem.status = 'success'
      fileItem.percentage = 100
      fileItem.uploadFileId = result.id
      fileItem.uploadFileType = getFileType(fileItem.raw)
      fileItem.uploadResult = result
      ElMessage.success(`文件 ${fileItem.name} 上传成功`)
      return result
    } else {
      throw new Error('上传响应中缺少文件ID')
    }
  } catch (error) {
    fileItem.status = 'fail'
    fileItem.percentage = 0
    ElMessage.error(`文件 ${fileItem.name} 上传失败: ${error.message || '未知错误'}`)
    console.error('文件上传失败:', error)
    throw error
  }
}

// 获取已上传的文件列表（用于工作流请求）
const getUploadedFiles = () => {
  const uploadedFiles = []
  
  if (!fileList.value || fileList.value.length === 0) {
    return uploadedFiles
  }
  
  for (const fileItem of fileList.value) {
    // 只包含上传成功的文件
    if (fileItem.status === 'success' && fileItem.uploadFileId) {
      uploadedFiles.push({
        transfer_method: 'local_file',
        upload_file_id: fileItem.uploadFileId,
        type: fileItem.uploadFileType || getFileType(fileItem.raw)
      })
    }
  }
  
  return uploadedFiles
}

// 获取文件类型
const getFileType = (file) => {
  const type = file.type || ''
  if (type.startsWith('image/')) {
    return 'image'
  } else if (type.includes('pdf')) {
    return 'document'
  } else if (type.includes('text') || type.includes('html')) {
    return 'document'
  } else {
    return 'document' // 默认为文档类型
  }
}

const handleClear = () => {
  result.value = null
  fileList.value = []
  Object.keys(inputs).forEach(key => {
    if (isSimpleValue(inputs[key])) {
      inputs[key] = ''
    } else {
      // 对于复杂结构，重置为默认值
      inputs[key] = Array.isArray(inputs[key]) ? [] : {}
    }
    if (inputsJson[key]) {
      inputsJson[key] = ''
    }
  })
  fullInputsJson.value = JSON.stringify(inputs, null, 2)
}

const handleBack = () => {
  router.push('/admin/apps')
}

const formatResult = (result) => {
  if (typeof result === 'string') {
    return result
  }
  return JSON.stringify(result, null, 2)
}

// 提取文件列表
const extractFiles = (result) => {
  const files = []
  if (!result || typeof result !== 'object') return files
  
  // 检查 data.outputs.files
  if (result.data && result.data.outputs && result.data.outputs.files) {
    result.data.outputs.files.forEach(file => {
      if (file.url) {
        const fullUrl = file.url.startsWith('http') ? file.url : `${fileUrlPrefix.value}${file.url}`
        files.push({
          ...file,
          fullUrl
        })
      }
    })
  }
  
  return files
}

// 检查是否有可预览的文件
const hasPreviewableFiles = (result) => {
  return extractFiles(result).length > 0
}

// 判断是否为图片
const isImage = (file) => {
  const type = (file.type || file.mime_type || '').toLowerCase()
  const extension = (file.extension || '').toLowerCase()
  return type.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'].includes(extension.replace('.', ''))
}

// 判断是否为HTML
const isHtml = (file) => {
  const type = (file.type || file.mime_type || '').toLowerCase()
  const extension = (file.extension || '').toLowerCase()
  return type.includes('html') || extension === '.html' || extension === '.htm'
}

// 判断是否为PDF
const isPdf = (file) => {
  const type = (file.type || file.mime_type || '').toLowerCase()
  const extension = (file.extension || '').toLowerCase()
  return type.includes('pdf') || extension === '.pdf'
}

// 打开文件
const openFile = (url) => {
  window.open(url, '_blank')
}

// 处理图片加载错误
const handleImageError = (event) => {
  event.target.style.display = 'none'
  ElMessage.warning('图片加载失败')
}

// 获取配置
const fetchConfig = async () => {
  try {
    const config = await request({
      url: '/api/ai-apps/config',
      method: 'get'
    })
    if (config.fileUrlPrefix) {
      fileUrlPrefix.value = config.fileUrlPrefix
    }
  } catch (error) {
    console.warn('获取配置失败，使用默认值:', error)
  }
}

onMounted(() => {
  fetchAppInfo()
  fetchConfig()
})
</script>

<style scoped>
.workflow-app {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.workflow-container {
  width: 100%;
  max-width: 1400px;
  height: 90vh;
}

.workflow-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.workflow-header h3 {
  margin: 0;
}

.workflow-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  height: calc(90vh - 120px);
}

.input-section,
.output-section {
  display: flex;
  flex-direction: column;
}

.input-section h4,
.output-section h4 {
  margin: 0 0 16px 0;
}

.result-content {
  max-height: calc(90vh - 200px);
  overflow-y: auto;
}

.result-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.result-placeholder {
  text-align: center;
  color: #909399;
  padding: 40px;
}

.error-message {
  margin-bottom: 16px;
}

.error-tip {
  margin-top: 12px;
  padding: 12px;
  background-color: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  color: #f56c6c;
  font-size: 14px;
  line-height: 1.6;
}

.complex-input {
  width: 100%;
}

.input-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.file-preview-section {
  margin-bottom: 20px;
}

.file-preview-item {
  margin-bottom: 20px;
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background-color: #fafafa;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.file-name {
  font-weight: 500;
  color: #303133;
}

.file-type {
  padding: 2px 8px;
  background-color: #ecf5ff;
  color: #409eff;
  border-radius: 3px;
  font-size: 12px;
}

.image-preview {
  text-align: center;
}

.image-preview img {
  max-width: 100%;
  max-height: 500px;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.html-preview,
.pdf-preview {
  width: 100%;
  height: 600px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.file-link {
  padding: 12px;
  text-align: center;
}
</style>

