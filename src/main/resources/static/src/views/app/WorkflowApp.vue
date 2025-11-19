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
          <el-form :model="inputs" label-width="120px">
            <el-form-item
              v-for="(value, key) in inputs"
              :key="key"
              :label="key"
            >
              <el-input
                v-model="inputs[key]"
                :placeholder="`请输入${key}`"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleRun" :loading="loading">
                运行工作流
              </el-button>
              <el-button @click="handleClear">清空</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="output-section">
          <h4>输出结果</h4>
          <el-card v-loading="loading">
            <div v-if="result" class="result-content">
              <pre>{{ formatResult(result) }}</pre>
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
const result = ref(null)
const loading = ref(false)

const fetchAppInfo = async () => {
  try {
    const res = await getAppDetail(route.params.id)
    appInfo.value = res
    
    // 解析inputs配置，初始化输入表单
    if (res.inputs) {
      try {
        const inputsConfig = JSON.parse(res.inputs)
        Object.keys(inputsConfig).forEach(key => {
          inputs[key] = inputsConfig[key] || ''
        })
      } catch (e) {
        // 如果解析失败，使用默认输入
        inputs['input'] = ''
      }
    } else {
      inputs['input'] = ''
    }
  } catch (error) {
    ElMessage.error('获取应用信息失败')
  }
}

const handleRun = async () => {
  loading.value = true
  result.value = null

  try {
    const requestData = {
      userId: 'user_' + Date.now(),
      inputs: inputs,
      stream: appInfo.value?.streamEnabled || false
    }

    if (appInfo.value?.streamEnabled) {
      await handleStreamWorkflow(requestData)
    } else {
      await handleNormalWorkflow(requestData)
    }
  } catch (error) {
    ElMessage.error('运行工作流失败')
    result.value = { error: '运行失败，请稍后重试' }
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
    inputs[key] = ''
  })
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
</style>

