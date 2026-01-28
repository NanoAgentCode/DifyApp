<template>
  <el-drawer
    v-model="visible"
    title="调用详情"
    direction="rtl"
    size="50%"
    :destroy-on-close="true"
  >
    <div v-if="!trace || Object.keys(trace).length === 0" class="empty-state">
      <el-empty description="暂无数据" />
    </div>
    <template v-else>
      <el-descriptions title="基础信息" :column="2" border>
        <el-descriptions-item label="链路ID (TraceId)">
          <el-tooltip :content="trace.traceId" placement="top">
            <code class="id-code">{{ trace.traceId || '-' }}</code>
          </el-tooltip>
        </el-descriptions-item>
        <el-descriptions-item label="调用ID (SpanId)">
          <el-tooltip :content="trace.spanId || trace.esDocId" placement="top">
            <code class="id-code">{{ trace.spanId || trace.esDocId || '-' }}</code>
          </el-tooltip>
        </el-descriptions-item>
        <el-descriptions-item label="来源">{{ trace.traceSource || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="trace.status === 1 ? 'success' : 'danger'">
            {{ trace.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="模型">{{ trace.model || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ trace.provider || '-' }}</el-descriptions-item>
        <el-descriptions-item label="延迟">{{ trace.latency || 0 }} ms</el-descriptions-item>
        <el-descriptions-item label="总 Tokens">{{ trace.totalTokens || 0 }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(trace.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="会话 ID">{{ trace.conversationId || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="content-section">
        <h3>请求内容</h3>
        <pre class="code-box">{{ trace.requestContent ? formatJson(trace.requestContent) : '暂无请求内容' }}</pre>
      </div>

      <div class="content-section">
        <h3>{{ trace.status === 1 ? '响应内容' : '错误内容' }}</h3>
        <pre class="code-box">
          <template v-if="trace.status === 1">
            {{ trace.responseContent ? formatJson(trace.responseContent) : '暂无响应内容' }}
          </template>
          <template v-else>
            {{ trace.errorContent || '暂无错误信息' }}
          </template>
        </pre>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import { getTrace } from '@/api/observability'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'

const visible = ref(false)
const trace = ref({})

const open = async (id) => {
  visible.value = true
  trace.value = {}
  try {
    const res = await getTrace(id)
    if (res.success && res.data) {
      trace.value = res.data
    } else {
      ElMessage.warning('获取追踪详情失败: ' + (res.message || '未知错误'))
    }
  } catch (e) {
    ElMessage.error('获取追踪详情失败: ' + (e.message || '未知错误'))
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
.empty-state {
  padding: 40px;
  text-align: center;
}

.code-box {
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
  min-height: 100px;
}

.id-code {
  font-family: 'Fira Code', 'Courier New', Courier, monospace;
  font-size: 12px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
}
</style>
