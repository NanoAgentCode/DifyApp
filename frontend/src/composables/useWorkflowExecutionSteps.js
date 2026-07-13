import { useThrottleFn } from '@/utils/debounce'

export const useWorkflowExecutionSteps = (executionSteps) => {
const processWorkflowEventThrottled = useThrottleFn((json) => {
  processWorkflowEvent(json)
}, 120)

// 处理工作流事件，提取执行步骤
const processWorkflowEvent = (json) => {
  if (!json || !json.event) return

  const event = json.event
  const data = json.data || {}

  // workflow_started - 工作流开始
  if (event === 'workflow_started') {
    executionSteps.value = []
    addExecutionStep({
      id: data.workflow_run_id || `step-${Date.now()}`,
      nodeId: 'workflow',
      title: '工作流启动',
      status: 'running',
      startTime: Date.now(),
      message: '工作流开始执行...'
    })
  }

  // node_started - 节点开始
  if (event === 'node_started') {
    const nodeId = data.node_id || data.id
    const nodeName = data.node_name || data.title || nodeId
    const inputs = data.inputs

    // 在展示层模拟“上一步完成再执行下一步”：
    // 收到新节点开始事件时，将当前最后一个运行中的步骤标记为已完成
    for (let i = executionSteps.value.length - 1; i >= 0; i--) {
      const step = executionSteps.value[i]
      if (step.status === 'running') {
        const now = Date.now()
        const autoDuration = step.startTime ? now - step.startTime : step.duration
        const title = step.title || step.nodeId || step.id
        executionSteps.value[i] = {
          ...step,
          status: 'completed',
          duration: autoDuration,
          message: step.message || `节点 "${title}" 执行完成`
        }
        break
      }
    }

    addExecutionStep({
      id: `${nodeId}-${Date.now()}`,
      nodeId: nodeId,
      title: nodeName,
      status: 'running',
      startTime: Date.now(),
      inputs: inputs,
      message: `节点 "${nodeName}" 开始执行...`
    })
  }

  // node_finished - 节点完成
  if (event === 'node_finished') {
    const nodeId = data.node_id || data.id
    const nodeName = data.node_name || data.title || nodeId
    const outputs = data.outputs
    const duration = data.duration || data.elapsed_time

    updateExecutionStep(nodeId, {
      status: 'completed',
      outputs: outputs,
      duration: duration,
      message: `节点 "${nodeName}" 执行完成`
    })
  }

  // node_failed - 节点失败
  if (event === 'node_failed' || event === 'workflow_failed') {
    const nodeId = data.node_id || data.id
    const nodeName = data.node_name || data.title || nodeId || '工作流'
    const error = data.error || data.message || '执行失败'

    updateExecutionStep(nodeId || 'workflow', {
      status: 'failed',
      error: error,
      message: `节点 "${nodeName}" 执行失败`
    })
  }

  // workflow_finished - 工作流完成
  if (event === 'workflow_finished') {
    // 收尾：把所有仍在“执行中”的步骤统一标为已完成，避免最后一步一直停在 running
    const now = Date.now()
    for (let i = 0; i < executionSteps.value.length; i++) {
      const step = executionSteps.value[i]
      if (step.status === 'running') {
        const duration = step.startTime ? now - step.startTime : step.duration
        const title = step.title || step.nodeId || step.id
        executionSteps.value[i] = {
          ...step,
          status: 'completed',
          duration: duration,
          message: step.message || `节点 "${title}" 执行完成`
        }
      }
    }
    updateExecutionStep('workflow', {
      status: 'completed',
      message: '工作流执行完成'
    })
  }
}

// 添加执行步骤
const addExecutionStep = (step) => {
  const existingIndex = executionSteps.value.findIndex(s => s.id === step.id)
  if (existingIndex >= 0) {
    // 如果已存在，更新
    executionSteps.value[existingIndex] = { ...executionSteps.value[existingIndex], ...step }
  } else {
    // 添加新步骤
    executionSteps.value.push(step)
  }
}

// 更新执行步骤
const updateExecutionStep = (nodeId, updates) => {
  // 找到最后一个匹配的步骤（可能同一个节点执行多次）
  for (let i = executionSteps.value.length - 1; i >= 0; i--) {
    if (executionSteps.value[i].nodeId === nodeId || executionSteps.value[i].id === nodeId) {
      executionSteps.value[i] = {
        ...executionSteps.value[i],
        ...updates,
        duration: updates.duration ? Date.now() - (executionSteps.value[i].startTime || Date.now()) : executionSteps.value[i].duration
      }
      return
    }
  }

  // 如果没找到，添加新步骤
  addExecutionStep({
    id: `${nodeId}-${Date.now()}`,
    nodeId: nodeId,
    title: nodeId,
    status: updates.status || 'pending',
    startTime: Date.now(),
    ...updates
  })
}

// 获取步骤状态类型
const getStepStatusType = (status) => {
  switch (status) {
    case 'running':
      return 'warning'
    case 'completed':
      return 'success'
    case 'failed':
      return 'danger'
    default:
      return 'info'
  }
}

// 获取步骤状态文本
const getStepStatusText = (status) => {
  switch (status) {
    case 'running':
      return '执行中'
    case 'completed':
      return '已完成'
    case 'failed':
      return '失败'
    default:
      return '等待中'
  }
}

// 格式化执行时间
const formatExecutionTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    fractionalSecondDigits: 3
  })
}

// 格式化持续时间
const formatDuration = (ms) => {
  if (!ms) return ''
  if (ms < 1000) {
    return `${ms}ms`
  } else if (ms < 60000) {
    return `${(ms / 1000).toFixed(2)}s`
  } else {
    const minutes = Math.floor(ms / 60000)
    const seconds = ((ms % 60000) / 1000).toFixed(2)
    return `${minutes}m ${seconds}s`
  }
}

// 格式化JSON
const formatJson = (obj) => {
  if (!obj) return ''
  try {
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return String(obj)
  }
}


return { processWorkflowEvent, processWorkflowEventThrottled, addExecutionStep, updateExecutionStep }
}
