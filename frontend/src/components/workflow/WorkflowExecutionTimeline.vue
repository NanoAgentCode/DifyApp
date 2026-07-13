<template>
<!-- 工作流执行过程展示区域（时间轴样式） -->
<div class="execution-section" v-show="isExecuting">
  <h4>工作流执行过程</h4>
  <div class="execution-panel">
    <div class="timeline-container">
      <div
v-for="(step, index) in executionSteps"
:key="step.id || index"
class="timeline-item"
:class="{
  'timeline-active': step.status === 'running',
  'timeline-completed': step.status === 'completed',
  'timeline-failed': step.status === 'failed',
  'timeline-pending': step.status === 'pending'
}"
      >
<!-- 时间轴节点 -->
<div class="timeline-node">
  <div class="timeline-dot" :class="`dot-${step.status}`">
    <el-icon v-if="step.status === 'running'" class="is-loading">
      <Loading />
    </el-icon>
    <el-icon v-else-if="step.status === 'completed'">
      <Check />
    </el-icon>
    <el-icon v-else-if="step.status === 'failed'">
      <Close />
    </el-icon>
    <el-icon v-else>
      <Clock />
    </el-icon>
  </div>
  <!-- 连接线 -->
  <div
    v-if="index < executionSteps.length - 1"
    class="timeline-line"
    :class="{
      'line-completed': step.status === 'completed',
      'line-active': step.status === 'running',
      'line-failed': step.status === 'failed'
    }"
  ></div>
</div>

<!-- 时间轴内容 -->
<div class="timeline-content">
  <div class="timeline-header">
    <div class="timeline-title-row">
      <span class="timeline-title">{{ step.title || step.nodeId || step.nodeName || `步骤 ${index + 1}` }}</span>
      <el-tag
:type="getStepStatusType(step.status)"
size="small"
class="timeline-status-tag"
      >
{{ getStepStatusText(step.status) }}
      </el-tag>
    </div>
    <div class="timeline-meta">
      <span v-if="step.startTime" class="timeline-time">
{{ formatExecutionTime(step.startTime) }}
      </span>
      <span v-if="step.duration" class="timeline-duration">
耗时: {{ formatDuration(step.duration) }}
      </span>
    </div>
  </div>

  <div v-if="step.message" class="timeline-message">
    {{ step.message }}
  </div>

  <div v-if="step.inputs" class="timeline-inputs">
    <el-text type="info" size="small">输入参数:</el-text>
    <pre class="timeline-json">{{ formatJson(step.inputs) }}</pre>
  </div>

  <div v-if="step.outputs" class="timeline-outputs">
    <el-text type="success" size="small">输出结果:</el-text>
    <pre class="timeline-json">{{ formatJson(step.outputs) }}</pre>
  </div>

  <div v-if="step.error" class="timeline-error">
    <el-alert
      :title="step.error"
      type="error"
      :closable="false"
      show-icon
    />
  </div>
</div>
      </div>

      <!-- 空状态 -->
      <div v-if="executionSteps.length === 0" class="timeline-placeholder">
<el-icon class="is-loading" :size="32"><Loading /></el-icon>
<p>正在启动工作流...</p>
      </div>
    </div>
  </div>
</div>

</template>

<script setup>
import { Check, Close, Loading, Clock } from "@element-plus/icons-vue"
defineProps({ executionSteps: { type: Array, default: () => [] } })
const getStepStatusType = (status) => ({ running: "warning", completed: "success", failed: "danger", pending: "info" }[status] || "info")
const getStepStatusText = (status) => ({ running: "执行中", completed: "已完成", failed: "失败", pending: "等待中" }[status] || "等待中")
const formatExecutionTime = (timestamp) => {
  if (!timestamp) return ''

  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 3
  })
}

const formatDuration = (milliseconds) => {
  if (!milliseconds) return ''
  if (milliseconds < 1000) return `${milliseconds}ms`
  if (milliseconds < 60000) return `${(milliseconds / 1000).toFixed(2)}s`

  const minutes = Math.floor(milliseconds / 60000)
  const seconds = ((milliseconds % 60000) / 1000).toFixed(2)
  return `${minutes}m ${seconds}s`
}

const formatJson = (value) => {
  if (!value) return ''

  try {
    return JSON.stringify(value, null, 2)
  } catch (error) {
    return String(value)
  }
}
const formatDuration = (ms) => !ms ? "" : ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(2)}s`
const formatJson = (value) => typeof value === "string" ? value : JSON.stringify(value, null, 2)
</script>
<style scoped>
.execution-section {
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border-lighter);
  min-height: 0;
  transition: all var(--transition-base);
}

.execution-section h4 {
  margin: 0 0 var(--spacing-lg) 0;
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  padding-bottom: var(--spacing-md);
  border-bottom: 2px solid var(--color-primary);
}

.execution-panel {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  max-height: calc(90vh - 200px);
}

.timeline-container {
  position: relative;
  padding: var(--spacing-md) 0;
}

.timeline-item {
  display: flex;
  position: relative;
  margin-bottom: var(--spacing-lg);
  transition: all var(--transition-base);
}

.timeline-item:last-child {
  margin-bottom: 0;
}

.timeline-node {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-right: var(--spacing-lg);
  flex-shrink: 0;
}

.timeline-dot {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid;
  background: var(--color-bg-primary);
  z-index: 2;
  transition: all var(--transition-base);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.timeline-dot .el-icon {
  font-size: 18px;
  color: #fff;
}

.timeline-dot.dot-running {
  background: var(--color-warning);
  border-color: var(--color-warning);
  animation: pulse 2s infinite;
}

.timeline-dot.dot-completed {
  background: var(--color-success);
  border-color: var(--color-success);
}

.timeline-dot.dot-failed {
  background: var(--color-danger);
  border-color: var(--color-danger);
}

.timeline-dot.dot-pending {
  background: var(--color-info);
  border-color: var(--color-info);
}

@keyframes pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(230, 162, 60, 0.7);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(230, 162, 60, 0);
  }
}

.timeline-line {
  width: 2px;
  flex: 1;
  min-height: 40px;
  margin-top: 4px;
  transition: all var(--transition-base);
}

.timeline-line.line-completed {
  background: var(--color-success);
}

.timeline-line.line-active {
  background: linear-gradient(to bottom, var(--color-success) 0%, var(--color-warning) 100%);
}

.timeline-line.line-failed {
  background: var(--color-danger);
}

.timeline-line.line-pending {
  background: var(--color-border-light);
}

.timeline-content {
  flex: 1;
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  border: 1px solid var(--color-border-lighter);
  transition: all var(--transition-base);
  min-width: 0;
}

.timeline-item.timeline-active .timeline-content {
  border-color: var(--color-warning);
  box-shadow: 0 2px 8px rgba(230, 162, 60, 0.2);
}

.timeline-item.timeline-completed .timeline-content {
  border-color: var(--color-success);
}

.timeline-item.timeline-failed .timeline-content {
  border-color: var(--color-danger);
}

.timeline-header {
  margin-bottom: var(--spacing-sm);
}

.timeline-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.timeline-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-status-tag {
  flex-shrink: 0;
}

.timeline-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.timeline-time {
  font-family: 'Courier New', monospace;
}

.timeline-duration {
  color: var(--color-text-tertiary);
}

.timeline-message {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-xs) var(--spacing-sm);
  background: var(--color-bg-secondary);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: var(--line-height-normal);
}

.timeline-inputs,
.timeline-outputs {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-sm);
  background: var(--color-bg-secondary);
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--color-primary);
}

.timeline-outputs {
  border-left-color: var(--color-success);
}

.timeline-json {
  margin: var(--spacing-xs) 0 0 0;
  padding: var(--spacing-xs);
  background: var(--color-bg-primary);
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-family: 'Courier New', 'Fira Code', monospace;
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 200px;
  overflow-y: auto;
  line-height: 1.4;
}

.timeline-error {
  margin-top: var(--spacing-sm);
}

.timeline-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl);
  color: var(--color-text-secondary);
  text-align: center;
}

.timeline-placeholder p {
  margin-top: var(--spacing-md);
  font-size: var(--font-size-base);
}

/* 响应式调整 */

</style>
