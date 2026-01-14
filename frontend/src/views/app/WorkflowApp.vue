<template>
  <div class="workflow-app">
    <el-card class="workflow-container">
      <template #header>
        <div class="workflow-header">
          <div class="workflow-header-left">
            <AppIcon :icon="appInfo?.icon" :size="32" class="app-icon" />
            <h3>{{ appInfo?.name || '工作流应用' }}</h3>
          </div>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <div class="workflow-content">
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
                v-if="appInfo?.inputEnabled === true"
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
                  v-if="appInfo?.inputEnabled === true"
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
            <el-button type="primary" @click="handleRun" :loading="loading">运行</el-button>
            <el-button @click="handleClear">清空</el-button>
          </div>
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
                <div v-if="hasPreviewableFilesComputed" class="file-preview-section">
                  <div v-for="(file, index) in extractedFiles" :key="file.fullUrl || file.url || `file-${index}`" class="file-preview-item">
                    <div class="file-info">
                      <span class="file-name">
                        {{ file.filename || file.saved_filename || `文件 ${index + 1}` }}
                        <span class="file-type">{{ getFileTypeLabel(file) }}</span>
                      </span>
                      <span class="file-size-display" v-if="file.file_size">
                        {{ formatFileSize(file.file_size) }}
                      </span>
                      <el-button 
                        class="open-file-btn"
                        size="small" 
                        @click="openPreview(file)" 
                        type="primary" 
                        :icon="FullScreen"
                        circle
                        :title="'全屏预览'"
                      />
                    </div>
                    <!-- 图片预览 -->
                    <div v-if="isImage(file)" class="image-preview">
                      <img :src="file.fullUrl" :alt="file.filename" @error="handleImageError" />
                    </div>
                    <!-- HTML预览 -->
                    <div v-else-if="isHtml(file)" class="html-preview">
                      <div class="html-preview-wrapper">
                        <iframe 
                          :src="file.fullUrl" 
                          frameborder="0" 
                          scrolling="no"
                          class="preview-iframe html-iframe"
                          :style="{ height: (htmlIframeHeights[index] || 600) + 'px' }"
                          @load="handleHtmlIframeLoad($event, index)"
                        ></iframe>
                        <div 
                          class="resize-handle"
                          @mousedown="startResize($event, index)"
                        >
                          <div class="resize-handle-line"></div>
                        </div>
                      </div>
                    </div>
                    <!-- DOCX预览 -->
                    <div v-else-if="isDocx(file)" class="docx-preview">
                      <div v-if="docxErrorMap[file.fullUrl]" class="docx-error">{{ docxErrorMap[file.fullUrl] }}</div>
                      <div v-else-if="docxLoadingMap[file.fullUrl]" class="docx-loading">正在加载文档...</div>
                      <div v-else v-html="docxHtmlMap[file.fullUrl]" class="docx-content"></div>
                    </div>
                    <!-- PDF预览 -->
                    <div v-else-if="isPdf(file)" class="pdf-preview">
                      <div class="pdf-preview-header">
                        <el-button 
                          type="primary" 
                          size="small"
                          @click="downloadFile(file)"
                          :icon="Download"
                        >
                          下载PDF
                        </el-button>
                        <span class="file-size-info" v-if="file.file_size">
                          文件大小: {{ formatFileSize(file.file_size) }}
                        </span>
                      </div>
                      <div class="pdf-preview-content">
                        <!-- 调试信息 -->
                        <div v-if="!file.fullUrl" class="pdf-debug">
                          <el-alert
                            title="PDF URL未找到"
                            type="error"
                            :closable="false"
                            show-icon
                          >
                            <template #default>
                              <p>文件URL: {{ file.url || '未设置' }}</p>
                              <p>完整URL: {{ file.fullUrl || '未设置' }}</p>
                              <p>下载URL: {{ file.download_url || '未设置' }}</p>
                            </template>
                          </el-alert>
                        </div>
                        <!-- PDF预览区域 -->
                        <div v-if="file.fullUrl" class="pdf-viewer-wrapper">
                          <!-- 加载中 -->
                          <div v-if="pdfLoading" class="pdf-loading">
                            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
                            <p>正在加载PDF...</p>
                          </div>
                          <!-- PDF预览embed -->
                          <embed 
                            v-if="pdfBlobUrl && !pdfLoadError"
                            :src="pdfBlobUrl + '#toolbar=0&navpanes=0&scrollbar=0'" 
                            type="application/pdf"
                            class="preview-embed"
                          />
                          <!-- 加载失败提示 -->
                          <div v-if="pdfLoadError && !pdfLoading" class="pdf-fallback">
                            <el-alert
                              title="PDF预览失败"
                              type="warning"
                              :closable="false"
                              show-icon
                            >
                              <template #default>
                                <p>无法在浏览器中预览PDF文件，可能是由于服务器设置或跨域限制。</p>
                                <div class="pdf-fallback-actions">
                                  <el-button 
                                    type="primary" 
                                    @click="openPreview(file)"
                                    :icon="FullScreen"
                                  >
                                    全屏预览
                                  </el-button>
                                  <el-button 
                                    type="success" 
                                    @click="downloadFile(file)"
                                    :icon="Download"
                                  >
                                    下载PDF文件
                                  </el-button>
                                </div>
                              </template>
                            </el-alert>
                          </div>
                        </div>
                      </div>
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
                <pre v-if="!hasPreviewableFilesComputed">{{ formatResult(result) }}</pre>
              </div>
            </div>
            <div v-else class="result-placeholder">
              运行工作流后，结果将显示在这里
            </div>
          </el-card>
        </div>
      </div>
    </el-card>
    <el-dialog v-model="previewDialog.visible" fullscreen class="preview-dialog" :show-close="false">
      <template #header>
        <div class="preview-dialog-header">
          <div class="preview-dialog-title">{{ previewDialogTitle }}</div>
          <el-button class="preview-dialog-close" circle @click="previewDialog.visible = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </template>
      <div class="fullscreen-preview-body">
        <img
          v-if="previewDialogType === 'image'"
          :src="previewDialog.file?.fullUrl"
          class="fullscreen-image"
        />
        <iframe
          v-else-if="previewDialogType === 'html'"
          :src="previewDialog.file?.fullUrl"
          frameborder="0"
          class="fullscreen-iframe"
        ></iframe>
        <embed
          v-else-if="previewDialogType === 'pdf'"
          :src="(pdfBlobUrl || previewDialog.file?.fullUrl || '') + '#toolbar=0&navpanes=0'"
          type="application/pdf"
          class="fullscreen-embed"
        />
        <div v-else-if="previewDialogType === 'docx'" class="fullscreen-docx">
          <div v-if="docxErrorMap[previewDialog.file?.fullUrl]" class="docx-error">{{ docxErrorMap[previewDialog.file?.fullUrl] }}</div>
          <div v-else-if="docxLoadingMap[previewDialog.file?.fullUrl]" class="docx-loading">正在加载文档...</div>
          <div v-else v-html="docxHtmlMap[previewDialog.file?.fullUrl]" class="docx-content"></div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, computed, watch, nextTick, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled, FullScreen, Document, Picture, Check, Close, Download, Loading } from '@element-plus/icons-vue'
import { getAppDetail, workflowApp, workflowAppStream, uploadFile } from '@/api/aiApp'
import { getFullAPIUrl } from '@/config/api'
import request from '@/utils/request'
import AppIcon from '@/components/AppIcon.vue'
import { logger } from '@/utils/logger'
import { useThrottleFn } from '@/utils/debounce'
import mammoth from 'mammoth'
import { buildMappedInputs } from '@/utils/difyInputMapping'

const route = useRoute()
const router = useRouter()
// 使用shallowRef优化性能（appInfo对象较大）
const appInfo = shallowRef(null)
const inputs = reactive({})
const inputsJson = reactive({}) // 用于存储复杂输入的 JSON 字符串
// 使用shallowRef优化大型对象性能（result可能包含大量数据）
const result = shallowRef(null)
const loading = ref(false)

const fileUrlPrefix = ref('http://localhost:80') // 文件URL前缀
const fileList = ref([]) // 文件列表
const uploadRef = ref(null) // 上传组件引用
const showPdfEmbed = ref(false) // 是否显示PDF embed标签
const pdfLoadError = ref(false) // PDF加载错误
const pdfLoading = ref(false) // PDF加载中
const pdfBlobUrl = ref('') // PDF Blob URL
const htmlIframeHeights = ref({}) // HTML iframe高度映射，key为文件索引
const isResizing = ref(false) // 是否正在调整大小
const resizeStartY = ref(0) // 拖拽开始时的Y坐标
const resizeStartHeight = ref(0) // 拖拽开始时的高度
const currentResizeIndex = ref(-1) // 当前正在调整的文件索引
const previewDialog = ref({ visible: false, file: null })
const docxHtmlMap = ref({})
const docxLoadingMap = ref({})
const docxErrorMap = ref({})

// 输入字段配置
const inputFields = ref([])
const formLabelWidth = ref('140px')

// 缓存提取的文件列表（性能优化）
const extractedFiles = computed(() => {
  if (!result.value) return []
  return extractFiles(result.value)
})

// 缓存是否有可预览文件（性能优化）
const hasPreviewableFilesComputed = computed(() => {
  return extractedFiles.value.length > 0
})

const previewDialogType = computed(() => {
  const file = previewDialog.value?.file
  if (!file) return ''
  if (isImage(file)) return 'image'
  if (isHtml(file)) return 'html'
  if (isPdf(file)) return 'pdf'
  if (isDocx(file)) return 'docx'
  return ''
})

const previewDialogTitle = computed(() => {
  const file = previewDialog.value?.file
  return file?.filename || file?.saved_filename || '预览'
})

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

// 解析输入字段配置
const parseInputFieldsConfig = (inputsStr) => {
  if (!inputsStr || inputsStr.trim() === '') {
    return []
  }
  
  try {
    const parsed = JSON.parse(inputsStr)
    
    // 检查是否是新格式（包含fields和defaults）
    if (parsed.fields && parsed.defaults) {
      // 新格式：从fields配置中提取字段信息
      const fields = []
      Object.keys(parsed.fields).forEach(key => {
        const fieldConfig = parsed.fields[key]
        fields.push({
          key,
          mapTo: fieldConfig.mapTo || fieldConfig.targetKey || '',
          label: fieldConfig.label || key,
          type: fieldConfig.type || 'text',
          placeholder: fieldConfig.placeholder || `请输入${fieldConfig.label || key}`,
          defaultValue: fieldConfig.defaultValue || '',
          helpText: fieldConfig.helpText || '',
          required: fieldConfig.required || false,
          rows: fieldConfig.rows || 2,
          options: fieldConfig.options || [],
          style: fieldConfig.style || {},
          validation: fieldConfig.validation || {}
        })
        
        // 设置默认值
        if (parsed.defaults && parsed.defaults.hasOwnProperty(key)) {
          inputs[key] = parsed.defaults[key]
        } else {
          // 根据类型设置默认值
          if (fieldConfig.type === 'json' && fieldConfig.defaultValue) {
            try {
              inputs[key] = JSON.parse(fieldConfig.defaultValue)
            } catch (e) {
              inputs[key] = fieldConfig.defaultValue
            }
          } else if (fieldConfig.type === 'number') {
            inputs[key] = fieldConfig.defaultValue ? Number(fieldConfig.defaultValue) : null
          } else if (fieldConfig.type === 'switch') {
            inputs[key] = fieldConfig.defaultValue === 'true' || fieldConfig.defaultValue === true
          } else {
            inputs[key] = fieldConfig.defaultValue || ''
          }
        }
        
        // 设置表单标签宽度（使用第一个字段的配置）
        if (fields.length === 1 && fieldConfig.style && fieldConfig.style.labelWidth) {
          formLabelWidth.value = fieldConfig.style.labelWidth
        }
      })
      return fields
    } else {
      // 旧格式或字段配置格式
      const fields = []
      Object.keys(parsed).forEach(key => {
        const value = parsed[key]
        
        // 检查是否是字段配置对象
        if (value && typeof value === 'object' && value.hasOwnProperty('type')) {
          // 字段配置格式
          fields.push({
            key,
            mapTo: value.mapTo || value.targetKey || '',
            label: value.label || key,
            type: value.type || 'text',
            placeholder: value.placeholder || `请输入${value.label || key}`,
            defaultValue: value.defaultValue || '',
            helpText: value.helpText || '',
            required: value.required || false,
            rows: value.rows || 2,
            options: value.options || [],
            style: value.style || {},
            validation: value.validation || {}
          })
          
          // 设置默认值
          if (value.type === 'json' && value.defaultValue) {
            try {
              inputs[key] = JSON.parse(value.defaultValue)
            } catch (e) {
              inputs[key] = value.defaultValue
            }
          } else if (value.type === 'number') {
            inputs[key] = value.defaultValue ? Number(value.defaultValue) : null
          } else if (value.type === 'switch') {
            inputs[key] = value.defaultValue === 'true' || value.defaultValue === true
          } else {
            inputs[key] = value.defaultValue || ''
          }
        } else {
          // 旧格式：简单的键值对
          initializeInput(key, value)
        }
      })
      return fields
    }
  } catch (e) {
    logger.error('解析输入字段配置失败:', e)
    return []
  }
}

const fetchAppInfo = async () => {
  try {
    const res = await getAppDetail(route.params.id)
    appInfo.value = res
    logger.debug('应用信息加载完成')
    
    // 清空之前的输入
    for (const key in inputs) {
      if (Object.prototype.hasOwnProperty.call(inputs, key)) {
        delete inputs[key]
      }
    }
    for (const key in inputsJson) {
      if (Object.prototype.hasOwnProperty.call(inputsJson, key)) {
        delete inputsJson[key]
      }
    }
    inputFields.value = []
    
    // 解析inputs配置，初始化输入表单
    if (res.inputs) {
      try {
        // 解析输入字段配置
        const fields = parseInputFieldsConfig(res.inputs)
        
        if (fields.length > 0) {
          // 使用新格式的字段配置
          inputFields.value = fields
        } else {
          // 旧格式：解析为简单的键值对
          const inputsConfig = JSON.parse(res.inputs)
          if (typeof inputsConfig === 'object' && inputsConfig !== null) {
            Object.keys(inputsConfig).forEach(key => {
              initializeInput(key, inputsConfig[key])
            })
          } else {
            // 如果解析的不是对象，使用默认输入
            logger.debug('inputs 配置格式不正确，使用默认配置')
            inputs['word'] = ''
            inputsJson['word'] = ''
          }
        }
      } catch (e) {
        logger.error('解析 inputs 配置失败:', e)
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


const handleRun = async () => {
  loading.value = true
  result.value = null

  try {
    // 先验证所有 JSON 输入（优化：使用for循环替代filter）
    const allKeys = Object.keys(inputsJson)
    const keysToValidate = []
    for (let i = 0; i < allKeys.length; i++) {
      const key = allKeys[i]
      if (inputsJson[key] && inputsJson[key].trim()) {
        keysToValidate.push(key)
      }
    }
    keysToValidate.forEach(key => {
      validateAndUpdateJson(key)
    })

    // 构建输入对象，保留所有非空值
    const filteredInputs = {}
    
    // 如果有配置的字段，使用配置的字段
    if (inputFields.value.length > 0) {
      const mapped = buildMappedInputs(inputFields.value, inputs, inputsJson)
      Object.keys(mapped).forEach(k => {
        filteredInputs[k] = mapped[k]
      })
    } else {
      // 旧格式：使用原有的逻辑（优化：直接遍历对象属性）
      for (const key in inputs) {
        if (Object.prototype.hasOwnProperty.call(inputs, key)) {
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
        }
      }
    }

    // 生成用户ID（用于工作流请求）
    const userId = 'user_' + Date.now()
    
    // 获取已上传的文件列表（文件在选择时已上传）
    let uploadedFiles = []
    if (appInfo.value?.fileUploadEnabled && fileList.value.length > 0) {
      uploadedFiles = getUploadedFiles()
      
      // 检查是否有未上传成功的文件（优化：使用for循环提前退出）
      let hasFailedFiles = false
      const files = fileList.value
      for (let i = 0; i < files.length; i++) {
        const f = files[i]
        if (f.status === 'fail' || (f.status !== 'success' && f.raw)) {
          hasFailedFiles = true
          break
        }
      }
      if (hasFailedFiles) {
        ElMessage.warning('部分文件上传失败，请重新上传或移除失败的文件')
        throw new Error('存在上传失败的文件')
      }
      
      // 检查是否有正在上传的文件（优化：使用for循环提前退出）
      let hasUploadingFiles = false
      for (let i = 0; i < files.length; i++) {
        if (files[i].status === 'uploading') {
          hasUploadingFiles = true
          break
        }
      }
      if (hasUploadingFiles) {
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

    logger.debug('发送工作流请求')

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
    const response = await fetch(getFullAPIUrl(`/api/ai-apps/${route.params.id}/workflow/stream`), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      },
      body: JSON.stringify(requestData)
    })

    const { processSSEStream } = await import('@/composables/useSSEStream')
    
    await processSSEStream(response, {
      cumulative: true,
      contentFields: ['answer'],
      onData: (json, cumulativeContent) => {
        if (cumulativeContent) {
          streamResult = cumulativeContent
        }
        
        result.value = json.metadata 
          ? { answer: streamResult, metadata: json.metadata }
          : { answer: streamResult }
      }
    })
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
  
  try {
    const formData = new FormData()
    
    // 直接使用文件对象，FormData 会自动处理文件编码
    formData.append('file', fileItem.raw)
    
    // 生成用户ID（用于文件上传）
    const userId = 'user_' + Date.now()
    formData.append('user', userId)
    
    // 通过后端接口上传文件
    const result = await uploadFile(route.params.id, formData)
    
    if (result && result.id) {
      // 保存上传后的文件信息
      fileItem.status = 'success'
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
    ElMessage.error(`文件 ${fileItem.name} 上传失败: ${error.message || '未知错误'}`)
    logger.error('文件上传失败:', error)
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

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

// 判断是否为图片文件
const isImageFile = (file) => {
  if (!file || !file.raw) return false
  const type = file.raw.type || ''
  return type.startsWith('image/')
}

// 获取文件状态类型（用于 Tag 颜色）
const getFileStatusType = (status) => {
  switch (status) {
    case 'success':
      return 'success'
    case 'fail':
      return 'danger'
    case 'uploading':
      return 'warning'
    default:
      return 'info'
  }
}

// 获取文件状态文本
const getFileStatusText = (status) => {
  switch (status) {
    case 'success':
      return '已上传'
    case 'fail':
      return '上传失败'
    case 'uploading':
      return '上传中'
    default:
      return '待上传'
  }
}

const handleClear = () => {
  result.value = null
  fileList.value = []
  // 优化：使用for...in循环替代Object.keys().forEach()
  for (const key in inputs) {
    if (Object.prototype.hasOwnProperty.call(inputs, key)) {
      if (isSimpleValue(inputs[key])) {
        inputs[key] = ''
      } else {
        // 对于复杂结构，重置为默认值
        inputs[key] = Array.isArray(inputs[key]) ? [] : {}
      }
      if (inputsJson[key]) {
        inputsJson[key] = ''
      }
    }
  }
}

const handleBack = () => {
  // 根据用户角色返回不同页面
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      const userInfo = JSON.parse(userInfoStr)
      router.push(userInfo.role === 1 ? '/admin/apps' : '/user/apps')
    } catch (e) {
      router.push('/admin/apps')
    }
  } else {
    router.push('/admin/apps')
  }
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
  
  // 检查 data.outputs.body（新格式：数组）
  if (result.data && result.data.outputs && result.data.outputs.body) {
    const bodyContent = result.data.outputs.body
    
    // 如果是数组，遍历每个文件对象（优化：使用for循环）
    if (Array.isArray(bodyContent)) {
      for (let i = 0; i < bodyContent.length; i++) {
        const fileItem = bodyContent[i]
        if (fileItem && fileItem.url) {
          // 提取URL并拼接前缀
          const url = fileItem.url
          const fullUrl = url.startsWith('http') ? url : `${fileUrlPrefix.value}${url}`
          
          // 构建文件信息对象
          const fileInfo = {
            url: url,
            fullUrl: fullUrl,
            filename: fileItem.filename || fileItem.saved_filename || 'download',
            saved_filename: fileItem.saved_filename,
            type: fileItem.mime_type || fileItem.type || 'application/octet-stream',
            mime_type: fileItem.mime_type || fileItem.type || 'application/octet-stream',
            extension: fileItem.extension || '',
            file_size: fileItem.size || fileItem.file_size,
            download_url: url,
            // 保留其他字段
            dify_model_identity: fileItem.dify_model_identity,
            related_id: fileItem.related_id,
            transfer_method: fileItem.transfer_method
          }
          
          logger.debug('提取到文件信息')
          files.push(fileInfo)
        }
      }
    } else if (typeof bodyContent === 'string') {
      // 兼容旧格式：body 可能是字符串
      let bodyStr = bodyContent.replace(/^["']|["']$/g, '').trim()
      
      if (bodyStr) {
        // 尝试解析为JSON（可能是JSON字符串）
        try {
          const parsedBody = JSON.parse(bodyStr)
          
          // 检查是否是文件信息对象（包含 download_url）
          if (parsedBody && parsedBody.download_url) {
            const downloadUrl = parsedBody.download_url
            const fullUrl = downloadUrl.startsWith('http') ? downloadUrl : `${fileUrlPrefix.value}${downloadUrl}`
            
            // 判断文件类型
            const urlLower = downloadUrl.toLowerCase()
            const isPdfFile = urlLower.includes('.pdf') || downloadUrl.endsWith('.pdf')
            const isHtmlFile = urlLower.includes('.html') || urlLower.includes('.htm')
            const isImageFile = /\.(jpg|jpeg|png|gif|webp|svg|bmp)$/i.test(downloadUrl)
            
            let fileType = 'application/octet-stream'
            let extension = ''
            
            if (isPdfFile) {
              fileType = 'application/pdf'
              extension = '.pdf'
            } else if (isHtmlFile) {
              fileType = 'text/html'
              extension = '.html'
            } else if (isImageFile) {
              const match = downloadUrl.match(/\.([^.]+)$/i)
              extension = match ? `.${match[1]}` : ''
              fileType = `image/${extension.replace('.', '')}`
            }
            
            const fileInfo = {
              url: downloadUrl,
              fullUrl: fullUrl,
              filename: parsedBody.original_filename || parsedBody.saved_filename || 'download',
              saved_filename: parsedBody.saved_filename,
              type: fileType,
              mime_type: fileType,
              extension: extension,
              file_size: parsedBody.file_size,
              message: parsedBody.message,
              download_url: downloadUrl
            }
            
            logger.debug('添加文件到列表')
            files.push(fileInfo)
          } else {
            // 如果不是文件信息对象，可能是直接的URL字符串
            const fullUrl = bodyStr.startsWith('http') ? bodyStr : `${fileUrlPrefix.value}${bodyStr}`
            
            // 判断URL类型
            const urlLower = bodyStr.toLowerCase()
            const isPdfFile = urlLower.includes('.pdf')
            const isHtmlFile = urlLower.includes('.html') || urlLower.includes('.htm')
            
            let fileType = 'text/html'
            let extension = '.html'
            let filename = 'output.html'
            
            if (isPdfFile) {
              fileType = 'application/pdf'
              extension = '.pdf'
              filename = 'output.pdf'
            }
            
            files.push({
              url: bodyStr,
              fullUrl: fullUrl,
              filename: filename,
              type: fileType,
              mime_type: fileType,
              extension: extension
            })
          }
        } catch (e) {
          // 如果不是JSON，当作普通URL处理
          const fullUrl = bodyStr.startsWith('http') ? bodyStr : `${fileUrlPrefix.value}${bodyStr}`
          const urlLower = bodyStr.toLowerCase()
          const isPdfFile = urlLower.includes('.pdf')
          
          files.push({
            url: bodyStr,
            fullUrl: fullUrl,
            filename: isPdfFile ? 'output.pdf' : 'output.html',
            type: isPdfFile ? 'application/pdf' : 'text/html',
            mime_type: isPdfFile ? 'application/pdf' : 'text/html',
            extension: isPdfFile ? '.pdf' : '.html'
          })
        }
      }
    }
  }
  
  // 检查 data.outputs.files（兼容旧格式，优化：使用for循环）
  if (result.data && result.data.outputs && result.data.outputs.files) {
    const filesArray = result.data.outputs.files
    for (let i = 0; i < filesArray.length; i++) {
      const file = filesArray[i]
      if (file && file.url) {
        const fullUrl = file.url.startsWith('http') ? file.url : `${fileUrlPrefix.value}${file.url}`
        files.push({
          ...file,
          fullUrl
        })
      }
    }
  }
  
  return files
}

// 检查是否有可预览的文件（已废弃，使用computed替代）
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

// 判断是否为DOCX
const isDocx = (file) => {
  const type = (file.type || file.mime_type || '').toLowerCase()
  const extension = (file.extension || '').toLowerCase()
  return extension === '.docx' || type.includes('officedocument.wordprocessingml') || type.includes('application/vnd.openxmlformats-officedocument.wordprocessingml')
}

const ensureDocxLoaded = async (file) => {
  const url = file?.fullUrl
  if (!url) return
  if (docxHtmlMap.value[url] || docxLoadingMap.value[url]) return

  docxLoadingMap.value = { ...docxLoadingMap.value, [url]: true }
  docxErrorMap.value = { ...docxErrorMap.value, [url]: '' }

  try {
    const token = localStorage.getItem('token') || ''
    const resp = await fetch(url, token ? { headers: { 'Authorization': `Bearer ${token}` } } : undefined)
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const arrayBuffer = await resp.arrayBuffer()
    const result = await mammoth.convertToHtml(
      { arrayBuffer },
      {
        styleMap: [
          "p[style-name='Heading 1'] => h1:fresh",
          "p[style-name='Heading 2'] => h2:fresh",
          "p[style-name='Heading 3'] => h3:fresh",
          "p[style-name='Heading 4'] => h4:fresh",
          "p[style-name='Heading 5'] => h5:fresh",
          "p[style-name='Heading 6'] => h6:fresh",
          "r[style-name='Strong'] => strong",
          "p[style-name='Title'] => h1.title:fresh",
          "p[style-name='Subtitle'] => h2.subtitle:fresh"
        ],
        includeDefaultStyleMap: true
      }
    )
    docxHtmlMap.value = { ...docxHtmlMap.value, [url]: result.value || '' }
  } catch (e) {
    docxErrorMap.value = { ...docxErrorMap.value, [url]: '文档加载失败，请下载后查看' }
  } finally {
    docxLoadingMap.value = { ...docxLoadingMap.value, [url]: false }
  }
}

const openPreview = (file) => {
  previewDialog.value = { visible: true, file }
  if (isPdf(file)) {
    nextTick(() => {
      resetPdfError()
      loadPdfForPreview(file)
    })
  }
  if (isDocx(file)) ensureDocxLoaded(file)
}

// 下载文件
const downloadFile = (file) => {
  const url = file.fullUrl || file.download_url || file.url
  if (!url) {
    ElMessage.error('文件URL不存在')
    return
  }
  
  try {
    // 创建临时链接并触发下载
    const link = document.createElement('a')
    link.href = url
    link.download = file.filename || file.saved_filename || 'download'
    
    // 添加到DOM并触发点击
    document.body.appendChild(link)
    link.click()
    
    // 清理
    document.body.removeChild(link)
    
    ElMessage.success('文件下载已开始')
  } catch (error) {
    logger.error('下载文件失败:', error)
    ElMessage.error('文件下载失败')
  }
}

// 获取文件类型标签
const getFileTypeLabel = (file) => {
  if (file.type || file.mime_type) {
    const type = (file.type || file.mime_type).toLowerCase()
    if (type.includes('pdf')) return 'PDF'
    if (type.includes('html')) return 'HTML'
    if (type.includes('officedocument.wordprocessingml')) return 'DOCX'
    if (type.startsWith('image/')) return '图片'
    return type.split('/')[1]?.toUpperCase() || '文件'
  }
  if (file.extension) {
    const ext = file.extension.replace('.', '').toUpperCase()
    return ext || '文件'
  }
  return '未知'
}

// 加载PDF并创建Blob URL（避免直接下载）
const loadPdfForPreview = async (file) => {
  if (!file.fullUrl) {
    pdfLoadError.value = true
    return
  }
  
  pdfLoading.value = true
  pdfLoadError.value = false
  
  // 清理之前的Blob URL
  if (pdfBlobUrl.value) {
    URL.revokeObjectURL(pdfBlobUrl.value)
    pdfBlobUrl.value = ''
  }
  
  try {
    // 使用fetch获取PDF内容
    const token = localStorage.getItem('token') || ''
    const response = await fetch(file.fullUrl, {
      method: 'GET',
      headers: {
        ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        'Accept': 'application/pdf'
      }
    })
    
    if (!response.ok) {
      throw new Error(`HTTP错误: ${response.status}`)
    }
    
    // 获取PDF的Blob数据
    const blob = await response.blob()
    
    // 检查是否是PDF类型
    if (blob.type && !blob.type.includes('pdf')) {
      logger.debug('返回的内容不是PDF类型:', blob.type)
    }
    
    // 创建Blob URL用于预览
    pdfBlobUrl.value = URL.createObjectURL(blob)
    pdfLoading.value = false
    pdfLoadError.value = false
    
    logger.debug('PDF加载成功')
  } catch (error) {
    logger.error('PDF加载失败:', error)
    pdfLoading.value = false
    pdfLoadError.value = true
    
    ElMessage.warning('PDF预览加载失败，可尝试全屏或下载')
  }
}

// PDF加载成功处理
const handlePdfLoad = (event) => {
  logger.debug('PDF iframe加载完成')
  pdfLoadError.value = false
}

// 重置PDF状态
const resetPdfError = () => {
  pdfLoadError.value = false
  pdfLoading.value = false
  if (pdfBlobUrl.value) {
    URL.revokeObjectURL(pdfBlobUrl.value)
    pdfBlobUrl.value = ''
  }
}

// 处理图片加载错误
const handleImageError = (event) => {
  event.target.style.display = 'none'
  ElMessage.warning('图片加载失败')
}

// 处理HTML iframe加载
const handleHtmlIframeLoad = (event, index) => {
  const iframe = event.target
  try {
    // 尝试获取iframe内容高度（可能受跨域限制）
    const iframeDoc = iframe.contentDocument || iframe.contentWindow?.document
    if (iframeDoc) {
      // 尝试注入CSS来隐藏滚动条
      try {
        const style = iframeDoc.createElement('style')
        style.textContent = `
          * {
            scrollbar-width: none !important;
            -ms-overflow-style: none !important;
          }
          *::-webkit-scrollbar {
            display: none !important;
            width: 0 !important;
            height: 0 !important;
            background: transparent !important;
          }
          html {
            overflow: auto !important;
            scrollbar-width: none !important;
            -ms-overflow-style: none !important;
            width: 100% !important;
            height: 100% !important;
          }
          html::-webkit-scrollbar {
            display: none !important;
            width: 0 !important;
            height: 0 !important;
            background: transparent !important;
          }
          body {
            overflow: auto !important;
            scrollbar-width: none !important;
            -ms-overflow-style: none !important;
            width: 100% !important;
            margin: 0 !important;
            padding: 0 !important;
            box-sizing: border-box !important;
          }
          body::-webkit-scrollbar {
            display: none !important;
            width: 0 !important;
            height: 0 !important;
            background: transparent !important;
          }
        `
        iframeDoc.head.appendChild(style)
      } catch (e) {
        // 如果无法注入样式，忽略
        logger.debug('无法注入样式到iframe:', e)
      }
      
      // 等待内容完全加载
      setTimeout(() => {
        const height = Math.max(
          iframeDoc.body?.scrollHeight || 0,
          iframeDoc.body?.offsetHeight || 0,
          iframeDoc.documentElement?.clientHeight || 0,
          iframeDoc.documentElement?.scrollHeight || 0,
          iframeDoc.documentElement?.offsetHeight || 0
        )
        if (height > 0 && height < 10000) {
          // 设置实际内容高度，但不超过10000px
          if (!htmlIframeHeights.value[index]) {
            htmlIframeHeights.value[index] = height
          }
        } else if (height === 0) {
          // 如果无法获取高度，设置一个合理的默认值
          if (!htmlIframeHeights.value[index]) {
            htmlIframeHeights.value[index] = 600
          }
        }
      }, 100)
    }
  } catch (e) {
    // 跨域限制，无法访问iframe内容
    // 设置默认高度
    if (!htmlIframeHeights.value[index]) {
      htmlIframeHeights.value[index] = 600
    }
  }
}

// 开始调整大小
const startResize = (event, index) => {
  event.preventDefault()
  event.stopPropagation()
  isResizing.value = true
  currentResizeIndex.value = index
  resizeStartY.value = event.clientY
  resizeStartHeight.value = htmlIframeHeights.value[index] || 600
  
  document.addEventListener('mousemove', handleResize)
  document.addEventListener('mouseup', stopResize)
}

// 处理拖拽调整（使用节流优化性能）
const handleResizeInternal = (event) => {
  if (!isResizing.value || currentResizeIndex.value === -1) return
  
  const deltaY = event.clientY - resizeStartY.value
  let newHeight = resizeStartHeight.value + deltaY
  
  // 计算最大高度限制
  const resizeHandle = event.target.closest('.resize-handle')
  if (resizeHandle) {
    const htmlPreviewElement = resizeHandle.closest('.html-preview')
    if (htmlPreviewElement) {
      const resultContent = htmlPreviewElement.closest('.result-content')
      if (resultContent) {
        const htmlPreviewRect = htmlPreviewElement.getBoundingClientRect()
        const resultContentRect = resultContent.getBoundingClientRect()
        const maxHeight = resultContentRect.bottom - htmlPreviewRect.top - 20 // 减去一些边距
        newHeight = Math.min(newHeight, maxHeight)
      }
    }
  }
  
  // 限制最小高度为200px
  newHeight = Math.max(200, newHeight)
  htmlIframeHeights.value[currentResizeIndex.value] = newHeight
}

// 使用节流优化resize事件处理（16ms约60fps）
const handleResize = useThrottleFn(handleResizeInternal, 16)

// 停止调整大小
const stopResize = () => {
  isResizing.value = false
  currentResizeIndex.value = -1
  document.removeEventListener('mousemove', handleResize)
  document.removeEventListener('mouseup', stopResize)
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
    logger.debug('获取配置失败，使用默认值:', error)
  }
}

// 监听result变化，自动加载PDF预览（优化：使用computed避免重复计算）
watch(extractedFiles, (files) => {
  if (files && files.length > 0) {
    // 优化：使用for循环替代find
    let pdfFile = null
    for (let i = 0; i < files.length; i++) {
      if (isPdf(files[i])) {
        pdfFile = files[i]
        break
      }
    }
    for (let i = 0; i < files.length; i++) {
      if (isDocx(files[i])) ensureDocxLoaded(files[i])
    }
    if (pdfFile) {
      // 延迟加载，确保DOM已更新
      nextTick(() => {
        resetPdfError()
        loadPdfForPreview(pdfFile)
      })
    } else {
      // 如果没有PDF文件，重置状态
      resetPdfError()
    }
  } else {
    // 如果没有文件，重置状态
    resetPdfError()
  }
})

onMounted(() => {
  fetchAppInfo()
  fetchConfig()
})

// 清理资源，防止内存泄漏
onBeforeUnmount(() => {
  // 清理resize事件监听器
  if (isResizing.value) {
    document.removeEventListener('mousemove', handleResize)
    document.removeEventListener('mouseup', stopResize)
    isResizing.value = false
    currentResizeIndex.value = -1
  }
  
  // 清理PDF Blob URL
  if (pdfBlobUrl.value) {
    URL.revokeObjectURL(pdfBlobUrl.value)
    pdfBlobUrl.value = ''
  }
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

.workflow-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.workflow-header-left .app-icon {
  flex-shrink: 0;
}

.workflow-header h3 {
  margin: 0;
}

.workflow-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  height: calc(90vh - 120px);
}

@media (max-width: 1200px) {
  .workflow-content {
    grid-template-columns: 1fr;
    height: auto;
  }
}

.input-section,
.output-section {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  min-height: 0;
}

.input-section-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.input-actions-bar {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  background: #fff;
}

.input-section h4,
.output-section h4 {
  margin: 0 0 20px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  padding-bottom: 12px;
  border-bottom: 2px solid var(--el-color-primary, #409eff);
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
  overflow: visible;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
  position: relative;
}

.open-file-btn {
  position: absolute;
  right: 10%;
}

.file-name {
  font-weight: 500;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-type {
  padding: 2px 8px;
  background-color: #ecf5ff;
  color: #409eff;
  border-radius: 3px;
  font-size: 12px;
  font-weight: normal;
}

.file-size-display {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

.image-preview {
  text-align: center;
}

.image-preview img {
  max-width: 100%;
  max-height: 500px;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  display: block;
  margin: 0 auto;
}

.image-actions {
  margin-top: 12px;
  text-align: center;
}

.docx-preview {
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  max-height: 600px;
  overflow: auto;
  background: #fff;
}

.docx-loading,
.docx-error {
  font-size: 13px;
  color: #909399;
}

.docx-error {
  color: #f56c6c;
}

.docx-content :deep(img) {
  max-width: 100%;
  height: auto;
}

.preview-dialog :deep(.el-dialog__body) {
  padding: 0;
  height: 100%;
}

.preview-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.preview-dialog-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: #303133;
}

.preview-dialog-close {
  flex-shrink: 0;
}

.fullscreen-preview-body {
  height: 100%;
  width: 100%;
  background: #fff;
}

.fullscreen-iframe,
.fullscreen-embed {
  width: 100%;
  height: calc(100vh - 54px);
  display: block;
}

.fullscreen-image {
  max-width: 100%;
  max-height: calc(100vh - 54px);
  display: block;
  margin: 0 auto;
}

.fullscreen-docx {
  height: calc(100vh - 54px);
  overflow: auto;
  padding: 16px;
}

.html-preview {
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: visible;
  display: flex;
  flex-direction: column;
}

.html-preview-wrapper {
  position: relative;
  width: 100%;
}

.resize-handle {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 8px;
  cursor: ns-resize;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  transition: background 0.2s;
}

.resize-handle:hover {
  background: rgba(64, 158, 255, 0.1);
}

.resize-handle-line {
  width: 60px;
  height: 4px;
  background: #409eff;
  border-radius: 2px;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.resize-handle:hover .resize-handle-line {
  opacity: 1;
}

.pdf-preview {
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.pdf-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.file-size-info {
  font-size: 12px;
  color: #909399;
}

.pdf-preview-content {
  flex: 1;
  position: relative;
  width: 100%;
  overflow: visible;
}

.pdf-viewer-wrapper {
  width: 100%;
  overflow: visible;
}

.pdf-viewer-wrapper object {
  overflow: visible !important;
}

.pdf-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 500px;
  color: #909399;
}

.pdf-loading p {
  margin-top: 12px;
  font-size: 14px;
}

.preview-object {
  width: 100%;
  min-height: 800px;
  border: none;
  display: block;
}

.preview-iframe {
  width: 100%;
  min-height: 2000px;
  border: none;
  display: block;
}

/* HTML预览的iframe样式 */
.html-preview .preview-iframe,
.html-preview .html-iframe {
  width: 100%;
  min-height: 100px;
  border: none;
  display: block;
  overflow: auto;
  /* 隐藏滚动条但保留滚动功能 */
  scrollbar-width: none !important; /* Firefox */
  -ms-overflow-style: none !important; /* IE and Edge */
  box-sizing: border-box;
}

/* 隐藏HTML预览iframe内部滚动条（Webkit浏览器） */
.html-preview .preview-iframe::-webkit-scrollbar,
.html-preview .html-iframe::-webkit-scrollbar {
  display: none !important;
  width: 0 !important;
  height: 0 !important;
  background: transparent !important;
}

/* 确保iframe内容可以滚动 */
.html-preview-wrapper {
  position: relative;
  width: 100%;
  overflow: hidden; /* 隐藏包装器的滚动条 */
}

/* 如果iframe内部内容需要滚动，通过CSS注入来隐藏滚动条 */
.html-preview-wrapper iframe {
  overflow: auto !important;
  scrollbar-width: none !important;
  -ms-overflow-style: none !important;
  box-sizing: border-box !important;
}

.html-preview-wrapper iframe::-webkit-scrollbar {
  display: none !important;
  width: 0 !important;
  height: 0 !important;
  background: transparent !important;
}

/* 隐藏iframe内部滚动条 */
.pdf-viewer-wrapper iframe {
  overflow: hidden !important;
}

/* 确保PDF查看器不显示滚动条 */
.pdf-viewer-wrapper iframe::-webkit-scrollbar {
  display: none;
}

.preview-embed {
  width: 100%;
  min-height: 5000px;
  display: block;
  border: none;
  overflow: hidden;
}

/* 隐藏embed内部滚动条 */
.pdf-viewer-wrapper embed {
  width: 100%;
  min-height: 5000px;
  overflow: hidden !important;
}

/* 隐藏所有可能的滚动条 */
.pdf-viewer-wrapper embed::-webkit-scrollbar {
  display: none !important;
  width: 0 !important;
  height: 0 !important;
}

.pdf-viewer-wrapper embed {
  scrollbar-width: none !important;
  -ms-overflow-style: none !important;
}

.pdf-error {
  padding: 20px;
  background-color: #fff;
}

.pdf-error-content {
  margin-top: 10px;
}

.pdf-error-content p {
  margin-bottom: 12px;
  color: #606266;
}

.pdf-url {
  font-size: 12px;
  color: #909399;
  word-break: break-all;
}

.pdf-debug {
  padding: 10px;
  background-color: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  margin-bottom: 10px;
}

.pdf-fallback {
  padding: 20px;
  text-align: center;
}

.pdf-fallback-actions {
  margin-top: 12px;
  display: flex;
  gap: 12px;
  justify-content: center;
}

.file-link {
  padding: 12px;
  text-align: center;
}

/* 文件上传进度条样式 */
.upload-file-item {
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  margin-bottom: 8px;
  background-color: #fff;
  transition: all 0.3s;
}

.upload-file-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.file-icon {
  font-size: 20px;
  color: #409eff;
  flex-shrink: 0;
}

.file-name {
  flex: 1;
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.file-status {
  flex-shrink: 0;
}

.file-success {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: #67c23a;
  font-size: 12px;
}

.success-icon {
  font-size: 16px;
}

.file-error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: #f56c6c;
  font-size: 12px;
}

.error-icon {
  font-size: 16px;
}
</style>

