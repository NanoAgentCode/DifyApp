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
              <pre v-else>{{ formatResult(result) }}</pre>
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
import { getAppDetail, workflowApp, workflowAppStream } from '@/api/aiApp'

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
      ElMessage.warning('应用未配置 inputs 参数，已使用默认配置。如果工作流需要特定参数，请在应用配置中设置。')
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

    const requestData = {
      userId: 'user_' + Date.now(),
      inputs: filteredInputs,
      stream: appInfo.value?.streamEnabled || false
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

const handleClear = () => {
  result.value = null
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

onMounted(() => {
  fetchAppInfo()
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
</style>

