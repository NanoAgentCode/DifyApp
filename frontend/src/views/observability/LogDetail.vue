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
          <el-tag :type="statusTagType">
            {{ statusLabel }}
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
        <h3>步骤追踪</h3>
        <div class="steps-header">
          <el-tag type="info">步骤数：{{ steps.length }}</el-tag>
          <el-tag :type="failedStepCount > 0 ? 'danger' : 'success'">
            失败步骤：{{ failedStepCount }}
          </el-tag>
          <el-tag v-if="asyncStateStatus" :type="asyncStateStatus === 'PARTIAL' ? 'warning' : 'success'">
            异步一致性：{{ asyncStateStatus }}
          </el-tag>
        </div>
        <el-alert
          v-if="asyncStateStatus === 'PARTIAL'"
          title="检测到异步写入部分失败，步骤信息可能不完整。"
          type="warning"
          show-icon
          :closable="false"
          class="partial-alert"
        />
        <el-empty v-if="!steps.length" description="暂无步骤数据" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="(step, idx) in steps"
            :key="`${step.stepCode || 'step'}-${idx}`"
            :type="getStepType(step)"
            :timestamp="formatStepTimestamp(step)"
            placement="top"
          >
            <div class="step-card">
              <div class="step-title">
                <strong>{{ step.stepName || step.stepCode || `步骤${idx + 1}` }}</strong>
                <el-tag size="small" :type="getStepType(step)">{{ step.status || 'UNKNOWN' }}</el-tag>
              </div>
              <div class="step-meta">
                <span>Code: {{ step.stepCode || '-' }}</span>
                <span>耗时: {{ step.durationMs ?? 0 }} ms</span>
              </div>
              <pre v-if="step.inputSummary" class="code-box small">输入: {{ step.inputSummary }}</pre>
              <pre v-if="step.outputSummary" class="code-box small">输出: {{ step.outputSummary }}</pre>
              <pre v-if="step.errorSummary" class="code-box small error">错误: {{ step.errorSummary }}</pre>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>

      <div class="content-section">
        <h3>请求内容</h3>
        <pre class="code-box">{{ trace.requestContent ? formatJson(trace.requestContent) : '暂无请求内容' }}</pre>
      </div>

      <div class="content-section">
        <h3>{{ trace.status === 1 ? '响应内容' : '错误内容' }}</h3>
        <template v-if="trace.status === 1">
          <div v-if="trace.responseContent" class="code-box markdown-content" v-html="renderedResponseContent"></div>
          <pre v-else class="code-box">暂无响应内容</pre>
        </template>
        <pre v-else class="code-box">{{ trace.errorContent || '暂无错误信息' }}</pre>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { getTrace, getTraceSteps } from '@/api/observability'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { renderMarkdown } from '@/composables/useMarkdown'

const visible = ref(false)
const trace = ref({})
const steps = ref([])
const asyncState = ref({})

const statusLabel = computed(() => {
  if (trace.value?.status !== 1) return '失败'
  if (asyncState.value?.status === 'PARTIAL') return '部分成功'
  return '成功'
})

const statusTagType = computed(() => {
  if (trace.value?.status !== 1) return 'danger'
  if (asyncState.value?.status === 'PARTIAL') return 'warning'
  return 'success'
})

const asyncStateStatus = computed(() => asyncState.value?.status || '')

const failedStepCount = computed(() => steps.value.filter((item) => item?.status === 'FAILED').length)

const renderedResponseContent = computed(() => {
  if (trace.value?.status !== 1) return ''
  return renderMarkdown(trace.value?.responseContent || '')
})

const parseMetaData = (metaData) => {
  if (!metaData) return {}
  try {
    return typeof metaData === 'string' ? JSON.parse(metaData) : metaData
  } catch (e) {
    return {}
  }
}

const open = async (id) => {
  visible.value = true
  trace.value = {}
  steps.value = []
  asyncState.value = {}
  try {
    const [traceRes, stepRes] = await Promise.all([getTrace(id), getTraceSteps(id)])
    if (traceRes.success && traceRes.data) {
      trace.value = traceRes.data
      const meta = parseMetaData(traceRes.data.metaData)
      asyncState.value = meta?.asyncState || {}
    } else {
      ElMessage.warning('获取追踪详情失败: ' + (traceRes.message || '未知错误'))
      return
    }

    if (stepRes.success && Array.isArray(stepRes.data)) {
      steps.value = stepRes.data
    } else {
      ElMessage.warning('获取步骤失败: ' + (stepRes.message || '未知错误'))
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

const getStepType = (step) => {
  const status = step?.status
  if (status === 'FAILED') return 'danger'
  if (status === 'SUCCESS') return 'success'
  return 'info'
}

const formatStepTimestamp = (step) => {
  if (step?.startAt) return formatTime(step.startAt)
  if (step?.endAt) return formatTime(step.endAt)
  return ''
}

defineExpose({
  open
})
</script>

<style scoped>
.content-section {
  margin-top: 20px;
}

.steps-header {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.partial-alert {
  margin-bottom: 12px;
}

.step-card {
  border: 1px solid var(--color-border-lighter);
  border-radius: 8px;
  padding: 10px;
  background: var(--color-bg-primary);
}

.step-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.step-meta {
  margin-top: 6px;
  color: var(--color-text-secondary);
  font-size: 12px;
  display: flex;
  gap: 12px;
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

.code-box.small {
  min-height: auto;
  max-height: 180px;
  margin-top: 8px;
}

.code-box.error {
  color: var(--color-danger);
}

.markdown-content :deep(pre) {
  margin: 0 0 10px 0;
}

.markdown-content :deep(code) {
  font-family: 'Fira Code', 'Courier New', Courier, monospace;
}

.markdown-content :deep(p) {
  margin: 0 0 8px 0;
  line-height: 1.65;
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
