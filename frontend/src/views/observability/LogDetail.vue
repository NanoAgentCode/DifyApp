<template>
  <el-drawer
    v-model="visible"
    title="调用详情"
    direction="rtl"
    size="50%"
    :destroy-on-close="true"
  >
    <el-descriptions title="基础信息" :column="2" border>
      <el-descriptions-item label="Trace ID">{{ trace.traceId }}</el-descriptions-item>
      <el-descriptions-item label="来源">{{ trace.traceSource }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="trace.status === 1 ? 'success' : 'danger'">
          {{ trace.status === 1 ? '成功' : '失败' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="模型">{{ trace.model }}</el-descriptions-item>
      <el-descriptions-item label="供应商">{{ trace.provider }}</el-descriptions-item>
      <el-descriptions-item label="延迟">{{ trace.latency }} ms</el-descriptions-item>
      <el-descriptions-item label="总 Tokens">{{ trace.totalTokens }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatTime(trace.createdAt) }}</el-descriptions-item>
      <el-descriptions-item label="会话 ID">{{ trace.conversationId }}</el-descriptions-item>
    </el-descriptions>

    <div class="content-section">
      <h3>请求内容</h3>
      <pre class="code-box">{{ formatJson(trace.requestContent) }}</pre>
    </div>

    <div class="content-section">
      <h3>{{ trace.status === 1 ? '响应内容' : '错误内容' }}</h3>
      <pre class="code-box">{{ trace.status === 1 ? formatJson(trace.responseContent) : trace.errorContent }}</pre>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import { getTrace } from '@/api/observability' // Assuming this API exists
import dayjs from 'dayjs'

const visible = ref(false)
const trace = ref({})

const open = async (id) => {
  visible.value = true
  try {
    const res = await getTrace(id)
    if (res.success) {
      trace.value = res.data
    }
  } catch (e) {
    console.error(e)
  }
}

const formatTime = (time) => {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

const formatJson = (jsonStr) => {
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch (e) {
    return jsonStr
  }
}

defineExpose({
  open
})
</script>

<style scoped>
.content-section {
  margin-top: 20px;
}
.code-box {
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-wrap: break-word; 
}
</style>
