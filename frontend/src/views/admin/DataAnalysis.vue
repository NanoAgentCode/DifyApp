<template>
  <div class="data-analysis">
    <el-card class="main-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon"><DataAnalysis /></el-icon>
            <span class="header-title">数据分析</span>
          </div>
          <div class="header-actions">
            <el-button @click="openSettingsDialog" :icon="Setting">
              同步设置
            </el-button>
            <el-button type="primary" @click="runNow" :loading="runningNow" :disabled="!settingsForm.neo4jDataSourceId" :icon="VideoPlay">
              立即同步
            </el-button>
            <el-tag v-if="saving" type="info" effect="plain" size="small">
              <el-icon class="is-loading"><Loading /></el-icon>
              保存中
            </el-tag>
          </div>
        </div>
      </template>

      <div class="graph-content">
        <div class="graph-workbench">
          <div v-loading="graphLoading" class="graph-container">
            <v-chart
              v-if="graphOption && graphData?.nodes?.length"
              :option="graphOption"
              autoresize
              class="graph-chart"
              @click="handleGraphClick"
            />
            <el-empty v-else description="暂无图数据，请先配置并运行数据同步" :image-size="120">
              <el-button type="primary" @click="openSettingsDialog" :icon="Setting">
                前往配置
              </el-button>
            </el-empty>
          </div>

          <aside class="graph-side-panel">
            <section class="qa-panel">
              <div class="side-title">
                <el-icon><ChatLineRound /></el-icon>
                <span>图谱问答</span>
              </div>
              <div class="question-suggestions">
                <el-button
                  v-for="item in questionSuggestions"
                  :key="item"
                  size="small"
                  text
                  @click="useSuggestion(item)"
                >
                  {{ item }}
                </el-button>
              </div>
              <div class="graph-toolbar">
                <el-input
                  v-model="graphFilters.keyword"
                  placeholder="搜索用户、知识库、文档、会话"
                  clearable
                  :prefix-icon="Search"
                  size="small"
                />
                <div class="toolbar-selects">
                  <el-select v-model="graphFilters.nodeLabel" placeholder="节点类型" clearable size="small">
                    <el-option v-for="item in nodeLabelOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                  <el-select v-model="graphFilters.relationshipType" placeholder="关系类型" clearable size="small">
                    <el-option v-for="item in relationshipTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </div>
                <div class="depth-control">
                  <span>关系深度</span>
                  <el-radio-group v-model="graphFilters.depth" size="small">
                    <el-radio-button v-for="item in depthOptions" :key="item.value" :label="item.value">
                      {{ item.label }}
                    </el-radio-button>
                  </el-radio-group>
                </div>
                <el-button @click="loadGraph" :loading="graphLoading" :icon="Refresh" size="small">
                  应用筛选
                </el-button>
              </div>
              <el-input
                v-model="graphQuestion"
                type="textarea"
                :rows="3"
                maxlength="200"
                show-word-limit
                placeholder="例如：有哪些知识库？哪个模型使用最多？"
                @keydown.enter.exact.prevent="askGraph"
              />
              <div class="qa-actions">
                <el-input-number v-model="qaLimit" :min="1" :max="50" controls-position="right" size="small" />
                <div class="qa-action-buttons">
                  <el-button :loading="qaLoading" :disabled="!graphQuestion.trim()" @click="askGraph" :icon="ChatLineRound" size="small">
                    快速问答
                  </el-button>
                  <el-button type="primary" :loading="ragLoading" :disabled="!graphQuestion.trim()" @click="askGraphRAG" :icon="ChatLineRound" size="small">
                    知识问答
                  </el-button>
                </div>
              </div>
              <div v-if="graphAnswer" class="answer-box">
                <div class="answer-meta">
                  <el-tag size="small" effect="plain">{{ graphAnswer.intent || 'graph' }}</el-tag>
                  <el-tag v-if="graphAnswer.llmGenerated != null" size="small" :type="graphAnswer.llmGenerated ? 'success' : 'warning'" effect="plain">
                    {{ graphAnswer.llmGenerated ? '模型生成' : '结构化降级' }}
                  </el-tag>
                  <el-tag v-if="graphAnswer.citationValid != null" size="small" :type="graphAnswer.citationValid ? 'success' : 'danger'" effect="plain">
                    {{ graphAnswer.citationValid ? '引用已校验' : '引用异常' }}
                  </el-tag>
                  <span>{{ graphAnswer.count ?? graphAnswer.graphHitCount ?? 0 }} 条命中</span>
                  <span v-if="graphAnswer.modelName">{{ graphAnswer.modelName }}</span>
                </div>
                <div class="answer-text">
                  <template v-for="(part, index) in answerParts" :key="index">
                    <button
                      v-if="part.citation"
                      type="button"
                      class="citation-link"
                      @click="focusCitation(part.text)"
                    >
                      [{{ part.text }}]
                    </button>
                    <span v-else>{{ part.text }}</span>
                  </template>
                </div>
                <div v-if="graphAnswer.errorCode || graphAnswer.fallbackReason" class="fallback-note">
                  <el-tag v-if="graphAnswer.errorCode" size="small" type="warning" effect="plain">{{ graphAnswer.errorCode }}</el-tag>
                  <span>{{ graphAnswer.fallbackReason || graphAnswer.message }}</span>
                </div>
                <div v-if="recognizedEntityTags.length" class="recognized-entities">
                  <span>识别实体</span>
                  <el-tag v-for="entity in recognizedEntityTags" :key="entity.key" size="small" effect="plain">
                    {{ entity.label }}: {{ entity.name }}
                  </el-tag>
                </div>
                <div v-if="graphRagMetricItems.length" class="graph-rag-metrics">
                  <div v-for="item in graphRagMetricItems" :key="item.label" class="graph-rag-metric">
                    <span>{{ item.label }}</span>
                    <strong>{{ item.value }}</strong>
                  </div>
                </div>
                <div v-if="qaResultRows.length" class="qa-result-list">
                  <div class="qa-result-header">
                    <span>结果明细</span>
                    <el-button size="small" text @click="copyAnswerSummary">复制回答</el-button>
                  </div>
                  <div
                    v-for="(row, index) in qaResultRows"
                    :key="index"
                    :ref="el => setCitationRef(row.citationId, el)"
                    class="qa-result-item"
                    :class="{ active: activeCitationId === row.citationId }"
                  >
                    <div class="qa-result-title">
                      <span>{{ index + 1 }}. {{ getResultTitle(row) }}</span>
                      <el-button size="small" text @click="searchResultInGraph(row)">查图谱</el-button>
                    </div>
                    <div class="qa-result-fields">
                      <div v-for="field in getResultFields(row)" :key="field.key" class="qa-result-field">
                        <span>{{ field.key }}</span>
                        <strong>{{ field.value }}</strong>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </section>
          </aside>
        </div>

        <!-- 底部控制栏 -->
        <div class="graph-footer">
          <div class="footer-left">
            <span class="toolbar-label">节点数量:</span>
            <el-input-number v-model="graphLimit" :min="10" :max="2000" controls-position="right" size="small" />
          </div>
          <div class="footer-right">
            <el-button @click="openStatsDialog" :icon="DataLine" size="small">
              统计信息
            </el-button>
            <el-button @click="loadGraph" :loading="graphLoading" :icon="Refresh" size="small">
              刷新
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog
      v-model="nodeDetailDialogVisible"
      title="节点详情"
      width="640px"
      :close-on-click-modal="true"
      class="node-detail-dialog"
    >
      <template v-if="selectedNode">
        <div class="selected-node-title dialog-node-title">
          <el-tag effect="plain" size="small">{{ selectedNode.label }}</el-tag>
          <span>{{ selectedNode.name }}</span>
        </div>
        <div class="property-list dialog-property-list">
          <div v-for="(v, k) in selectedNode.properties" :key="k" class="property-row">
            <span class="property-key">{{ k }}</span>
            <span class="property-value">{{ formatPropertyValue(v) }}</span>
          </div>
        </div>
      </template>
      <el-empty v-else description="暂无节点详情" :image-size="96" />
    </el-dialog>

    <el-dialog
      v-model="statsDialogVisible"
      title="统计信息"
      width="800px"
      :close-on-click-modal="true"
      class="stats-dialog"
    >
      <div class="stats-dialog-content">
        <div v-if="graphData?.nodeCounts" class="stats-section">
          <div class="stats-title">
            <el-icon><CircleCheck /></el-icon>
            <span>节点统计</span>
          </div>
          <div class="stats-grid">
            <div v-for="(v, k) in graphData.nodeCounts" :key="k" class="stat-item">
              <div class="stat-label">{{ k }}</div>
              <div class="stat-value">{{ v }}</div>
            </div>
          </div>
        </div>
        <div v-if="graphData?.relationshipCounts" class="stats-section">
          <div class="stats-title">
            <el-icon><Link /></el-icon>
            <span>关系统计</span>
          </div>
          <div class="stats-grid">
            <div v-for="(v, k) in graphData.relationshipCounts" :key="k" class="stat-item">
              <div class="stat-label">{{ k }}</div>
              <div class="stat-value">{{ v }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="settingsDialogVisible"
      title="同步设置"
      width="600px"
      :close-on-click-modal="true"
      class="settings-dialog"
    >
      <div class="settings-content">
        <div class="settings-section">
          <div class="section-title">
            <el-icon><Setting /></el-icon>
            <span>基本配置</span>
          </div>
          <el-form label-width="120px" class="settings-form">
            <el-form-item label="Neo4j 数据源">
              <el-select
                v-model="settingsForm.neo4jDataSourceId"
                placeholder="请选择数据源（type=neo4j）"
                filterable
                clearable
                style="width: 85%"
              >
                <el-option
                  v-for="ds in neo4jDataSources"
                  :key="ds.id"
                  :label="ds.name"
                  :value="ds.id"
                />
              </el-select>
            </el-form-item>
            <div class="form-row" style="margin-top: 18px;">
              <el-form-item label="定时同步" class="form-item-half">
                <div class="form-control-wrapper">
                  <el-tooltip content="将按照设定的间隔自动同步数据" placement="top">
                    <el-switch v-model="settingsForm.enabled" />
                  </el-tooltip>
                </div>
              </el-form-item>

              <el-form-item label="同步间隔" class="form-item-half">
                <div class="form-control-wrapper">
                  <el-input-number v-model="settingsForm.intervalMinutes" :min="1" :max="1440" controls-position="left" style="width: 120px;" />
                  <span class="form-unit">分钟</span>
                </div>
              </el-form-item>
            </div>
          </el-form>
        </div>

        <el-divider class="section-divider" />

        <div class="settings-section">
          <div class="section-title">
            <el-icon><Monitor /></el-icon>
            <span>同步状态</span>
          </div>
          <div v-loading="loadingStatus" class="status-panel">
            <el-descriptions :column="2" border class="status-descriptions">
              <el-descriptions-item label="运行状态">
                <el-tag :type="status?.running ? 'warning' : 'info'" effect="plain">
                  <el-icon v-if="status?.running"><VideoPlay /></el-icon>
                  <el-icon v-else><VideoPause /></el-icon>
                  {{ status?.running ? '运行中' : '未运行' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="最近状态">
                <el-tag :type="statusTagType(status?.lastStatus)" effect="plain">
                  {{ status?.lastStatus || '从未运行' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="最近开始时间">
                {{ formatTime(status?.lastRunAtMs) }}
              </el-descriptions-item>
              <el-descriptions-item label="最近成功时间">
                {{ formatTime(status?.lastSuccessAtMs) }}
              </el-descriptions-item>
              <el-descriptions-item label="最近耗时">
                {{ formatDuration(status?.lastDurationMs) }}
              </el-descriptions-item>
              <el-descriptions-item label="执行消息">
                <span class="message-text">{{ status?.lastMessage || '-' }}</span>
              </el-descriptions-item>
            </el-descriptions>

            <el-descriptions v-if="status?.metrics" :column="3" border class="metrics-descriptions">
              <template #title>
                <div class="metrics-title">
                  <el-icon><DataLine /></el-icon>
                  <span>同步指标</span>
                </div>
              </template>
              <el-descriptions-item v-for="(v, k) in status.metrics" :key="k" :label="k">
                <span class="metric-value">{{ v }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="handleSettingsSave" :loading="saving">
            保存并运行
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatLineRound, CircleCheck, DataAnalysis, DataLine, Link, Loading, Monitor, Refresh, Search, Setting, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { getDataSourceList } from '@/api/dataSource'
import { askDataAnalysisGraph, askDataAnalysisGraphRAG, getDataAnalysisGraph, getDataAnalysisSettings, getDataAnalysisStatus, runDataAnalysis, updateDataAnalysisSettings } from '@/api/dataAnalysis'
import { logger } from '@/utils/logger'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { GraphChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, GraphChart, LegendComponent, TooltipComponent])

const loadingStatus = ref(false)
const saving = ref(false)
const runningNow = ref(false)
const settingsDialogVisible = ref(false)
const statsDialogVisible = ref(false)

const dataSources = ref([])
const status = ref(null)

const settingsForm = reactive({
  enabled: false,
  neo4jDataSourceId: null,
  intervalMinutes: 60
})

const isLoadingSettings = ref(true)

const neo4jDataSources = computed(() => {
  return (dataSources.value || []).filter(ds => (ds.type || '').toLowerCase() === 'neo4j')
})

const loadDataSources = async () => {
  try {
    const res = await getDataSourceList({ type: 'neo4j', status: 1 })
    if (Array.isArray(res)) {
      dataSources.value = res
    } else if (res && Array.isArray(res.records)) {
      dataSources.value = res.records
    } else {
      dataSources.value = []
    }
  } catch (e) {
    logger.error('加载数据源失败:', e)
    dataSources.value = []
  }
}

const loadSettings = async () => {
  try {
    isLoadingSettings.value = true
    const s = await getDataAnalysisSettings()
    settingsForm.enabled = !!s.enabled
    settingsForm.neo4jDataSourceId = s.neo4jDataSourceId ?? null
    settingsForm.intervalMinutes = s.intervalMinutes ?? 60
  } catch (e) {
    logger.error('加载配置失败:', e)
  } finally {
    isLoadingSettings.value = false
  }
}

const loadStatus = async () => {
  loadingStatus.value = true
  try {
    status.value = await getDataAnalysisStatus()
  } catch (e) {
    logger.error('加载状态失败:', e)
  } finally {
    loadingStatus.value = false
  }
}

const runNow = async () => {
  if (!settingsForm.neo4jDataSourceId) {
    ElMessage.warning('请先选择 Neo4j 数据源')
    return
  }
  runningNow.value = true
  try {
    await updateDataAnalysisSettings({
      enabled: settingsForm.enabled,
      neo4jDataSourceId: settingsForm.neo4jDataSourceId,
      intervalMinutes: settingsForm.intervalMinutes
    })

    status.value = {
      ...(status.value || {}),
      running: true,
      lastStatus: 'running',
      lastMessage: '同步任务已提交'
    }
    startPollingIfRunning()

    await runDataAnalysis()
    ElMessage.success('已提交同步任务')
    await new Promise(resolve => setTimeout(resolve, 600))
    await loadStatus()
  } catch (e) {
    logger.error('提交同步任务失败:', e)
    ElMessage.error('提交失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    runningNow.value = false
  }
}

const openSettingsDialog = () => {
  settingsDialogVisible.value = true
  loadStatus()
}

const handleSettingsSave = async () => {
  if (!settingsForm.neo4jDataSourceId) {
    ElMessage.warning('请先选择 Neo4j 数据源')
    return
  }
  saving.value = true
  try {
    await updateDataAnalysisSettings({
      enabled: settingsForm.enabled,
      neo4jDataSourceId: settingsForm.neo4jDataSourceId,
      intervalMinutes: settingsForm.intervalMinutes
    })
    ElMessage.success('保存成功')
    await loadStatus()
    await runNow()
    settingsDialogVisible.value = false
  } catch (e) {
    logger.error('保存配置失败:', e)
    ElMessage.error('保存失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

const statusTagType = (s) => {
  if (s === 'success') return 'success'
  if (s === 'failed') return 'danger'
  if (s === 'running') return 'warning'
  return 'info'
}

const formatTime = (ms) => {
  if (!ms) return '-'
  try {
    return new Date(ms).toLocaleString()
  } catch {
    return String(ms)
  }
}

const formatDuration = (ms) => {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms} ms`
  const sec = Math.round(ms / 1000)
  return `${sec} s`
}

let pollTimer = null
let graphLoadTimer = null

const graphLoading = ref(false)
const graphLimit = ref(200)
const graphData = ref(null)
const selectedNode = ref(null)
const nodeDetailDialogVisible = ref(false)
const graphQuestion = ref('')
const graphAnswer = ref(null)
const qaLoading = ref(false)
const ragLoading = ref(false)
const qaLimit = ref(10)
const activeCitationId = ref('')
const citationRefs = new Map()
const graphFilters = reactive({
  keyword: '',
  nodeLabel: '',
  relationshipType: '',
  depth: 1
})

const nodeLabelOptions = [
  { label: '用户', value: 'User' },
  { label: 'AI 应用', value: 'AiApp' },
  { label: '知识库', value: 'KnowledgeBase' },
  { label: '知识文档', value: 'KnowledgeDocument' },
  { label: '会话', value: 'Conversation' },
  { label: '消息', value: 'Message' },
  { label: '问答模型', value: 'QAModel' }
]

const relationshipTypeOptions = [
  { label: '拥有应用', value: 'HAS_APP' },
  { label: '创建知识库', value: 'CREATED_KB' },
  { label: '包含文档', value: 'HAS_DOCUMENT' },
  { label: '拥有会话', value: 'HAS_CONVERSATION' },
  { label: '使用应用', value: 'USING_APP' },
  { label: '使用知识库', value: 'USING_KB' },
  { label: '包含消息', value: 'HAS_MESSAGE' },
  { label: '使用模型', value: 'USING_MODEL' }
]

const depthOptions = [
  { label: '1跳', value: 1 },
  { label: '2跳', value: 2 },
  { label: '3跳', value: 3 }
]

const questionSuggestions = [
  '有哪些知识库？',
  '图谱节点统计',
  '图谱关系统计',
  '哪个模型使用最多？',
  '知识库有哪些文档？',
  '文档向量化状态',
  '用户有哪些应用？',
  '最近有哪些会话？'
]

const qaResultRows = computed(() => {
  return Array.isArray(graphAnswer.value?.results) ? graphAnswer.value.results : []
})

const answerParts = computed(() => {
  const answer = graphAnswer.value?.answer || ''
  if (!answer) return []
  const validCitationIds = new Set(qaResultRows.value.map(row => row.citationId).filter(Boolean))
  return answer
    .split(/(\[G\d+])/g)
    .filter(part => part !== '')
    .map(part => {
      const match = part.match(/^\[(G\d+)]$/)
      if (!match || !validCitationIds.has(match[1])) {
        return { text: part, citation: false }
      }
      return { text: match[1], citation: true }
    })
})

const recognizedEntityTags = computed(() => {
  const entities = graphAnswer.value?.recognizedEntities
  if (!Array.isArray(entities)) return []
  return entities.map(entity => ({
    key: `${entity.label}:${entity.id}`,
    label: entity.label,
    name: entity.name
  }))
})

const graphRagMetricItems = computed(() => {
  const metrics = graphAnswer.value?.metrics
  if (!metrics) return []
  return [
    { label: '实体', value: metrics.entityCount ?? '-' },
    { label: '召回', value: metrics.graphHitCount ?? '-' },
    { label: '召回耗时', value: formatMetricMs(metrics.graphRetrievalMs) },
    { label: '模型耗时', value: formatMetricMs(metrics.llmGenerationMs) },
    { label: '总耗时', value: formatMetricMs(metrics.totalMs) }
  ]
})

watch(graphAnswer, () => {
  activeCitationId.value = ''
  citationRefs.clear()
})

const openStatsDialog = () => {
  statsDialogVisible.value = true
}

const graphOption = computed(() => {
  const nodes = graphData.value?.nodes || []
  const links = graphData.value?.links || []
  if (!nodes.length) return null

  const labelSet = new Set(nodes.map(n => n.label || 'Unknown'))
  const categories = Array.from(labelSet).map(name => ({ name }))
  const categoryIndex = new Map(categories.map((c, idx) => [c.name, idx]))

  const seriesNodes = nodes.map(n => ({
    id: n.id,
    name: n.name || n.id,
    label: n.label,
    category: categoryIndex.get(n.label || 'Unknown') ?? 0,
    symbolSize: selectedNode.value?.id === n.id ? 28 : 18
  }))

  const seriesLinks = links.map(l => ({
    source: l.source,
    target: l.target,
    value: l.type,
    lineStyle: { opacity: 0.6 }
  }))

  return {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.dataType === 'edge') return params.data.value || ''
        return params.data?.name || ''
      }
    },
    legend: [{ data: categories.map(c => c.name) }],
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        emphasis: { focus: 'adjacency' },
        categories,
        data: seriesNodes,
        links: seriesLinks,
        label: { show: true, position: 'right' },
        force: { repulsion: 180, edgeLength: 80 }
      }
    ]
  }
})

const loadGraph = async () => {
  if (!settingsForm.neo4jDataSourceId) {
    graphData.value = null
    selectedNode.value = null
    return
  }
  graphLoading.value = true
  try {
    graphData.value = await getDataAnalysisGraph({
      limit: graphLimit.value,
      keyword: graphFilters.keyword || undefined,
      nodeLabel: graphFilters.nodeLabel || undefined,
      relationshipType: graphFilters.relationshipType || undefined,
      depth: graphFilters.depth
    })
    if (selectedNode.value && !(graphData.value?.nodes || []).some(node => node.id === selectedNode.value.id)) {
      selectedNode.value = null
    }
  } catch (e) {
    logger.error('加载图数据失败:', e)
    ElMessage.error('加载图数据失败：' + (e.response?.data?.message || e.message || '未知错误'))
    graphData.value = null
    selectedNode.value = null
  } finally {
    graphLoading.value = false
  }
}

const handleGraphClick = (params) => {
  if (params?.dataType !== 'node') return
  const node = (graphData.value?.nodes || []).find(item => item.id === params.data.id)
  selectedNode.value = node || null
  nodeDetailDialogVisible.value = !!node
}

const askGraph = async () => {
  const question = graphQuestion.value.trim()
  if (!question) return
  activeCitationId.value = ''
  qaLoading.value = true
  try {
    graphAnswer.value = await askDataAnalysisGraph({
      question,
      limit: qaLimit.value
    })
  } catch (e) {
    logger.error('图谱问答失败:', e)
    ElMessage.error('图谱问答失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    qaLoading.value = false
  }
}

const askGraphRAG = async () => {
  const question = graphQuestion.value.trim()
  if (!question) return
  activeCitationId.value = ''
  ragLoading.value = true
  try {
    const response = await askDataAnalysisGraphRAG({
      question,
      limit: qaLimit.value,
      depth: graphFilters.depth
    })
    graphAnswer.value = {
      ...response,
      intent: 'graph_rag',
      count: response.graphHitCount,
      results: response.graphSources || []
    }
  } catch (e) {
    logger.error('GraphRAG问答失败:', e)
    ElMessage.error('GraphRAG问答失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    ragLoading.value = false
  }
}

const useSuggestion = (question) => {
  graphQuestion.value = question
  askGraphRAG()
}

const getResultTitle = (row) => {
  if (row.citationId && row.sourceName && row.targetName) {
    return `${row.citationId} ${row.sourceName} -> ${row.targetName}`
  }
  return row.knowledgeBaseName ||
    row.documentName ||
    row.username ||
    row.modelName ||
    row.title ||
    row.sourceName ||
    row.targetName ||
    '图谱结果'
}

const getResultFields = (row) => {
  return Object.entries(row || {})
    .filter(([, value]) => value != null && value !== '')
    .slice(0, 8)
    .map(([key, value]) => ({
      key,
      value: formatPropertyValue(value)
    }))
}

const searchResultInGraph = (row) => {
  const keyword = row.knowledgeBaseName ||
    row.documentName ||
    row.username ||
    row.modelName ||
    row.title ||
    row.sourceName ||
    row.targetName ||
    getResultTitle(row)
  if (!keyword || keyword === '图谱结果') return
  graphFilters.keyword = keyword
  scheduleGraphLoad(0)
}

const setCitationRef = (citationId, el) => {
  if (!citationId) return
  if (el) {
    citationRefs.set(citationId, el)
  } else {
    citationRefs.delete(citationId)
  }
}

const focusCitation = async (citationId) => {
  if (!citationId) return
  activeCitationId.value = citationId
  await nextTick()
  citationRefs.get(citationId)?.scrollIntoView({
    behavior: 'smooth',
    block: 'nearest'
  })
}

const copyAnswerSummary = async () => {
  const text = graphAnswer.value?.answer
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制回答')
  } catch (e) {
    logger.warn('复制图谱问答结果失败:', e)
    ElMessage.warning('复制失败，请手动选择文本')
  }
}

const formatPropertyValue = (value) => {
  if (value == null || value === '') return '-'
  if (typeof value === 'number' && String(value).length >= 12) {
    return formatTime(value)
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

const formatMetricMs = (value) => {
  if (value == null) return '-'
  const ms = Number(value)
  if (Number.isNaN(ms)) return String(value)
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const startPollingIfRunning = () => {
  stopPolling()
  if (!status.value?.running) return
  pollTimer = setInterval(() => {
    loadStatus()
  }, 5000)
}

watch(() => status.value?.running, () => {
  startPollingIfRunning()
})

const scheduleGraphLoad = (delayMs = 300) => {
  if (graphLoadTimer) clearTimeout(graphLoadTimer)
  graphLoadTimer = setTimeout(() => {
    loadGraph()
  }, delayMs)
}

watch(graphLimit, () => {
  scheduleGraphLoad(300)
})

watch(() => [graphFilters.keyword, graphFilters.nodeLabel, graphFilters.relationshipType, graphFilters.depth], () => {
  scheduleGraphLoad(300)
})

watch(() => settingsForm.neo4jDataSourceId, () => {
  scheduleGraphLoad(0)
})

onMounted(async () => {
  await loadDataSources()
  await loadSettings()
  await loadStatus()
  await loadGraph()
  startPollingIfRunning()
})

onUnmounted(() => {
  stopPolling()
  if (graphLoadTimer) clearTimeout(graphLoadTimer)
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.data-analysis {
  width: 100%;
  padding: var(--spacing-lg);
  background: var(--color-bg-secondary);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ========== 主卡片 ========== */
.main-card {
  border-radius: var(--card-border-radius);
  border: none;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--card-shadow);
  transition: box-shadow var(--transition-base);
}

.main-card:hover {
  box-shadow: var(--card-shadow-hover);
}

.main-card :deep(.el-card__header) {
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-lighter);
  padding: var(--spacing-md) var(--card-padding);
}

.main-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: var(--card-padding);
  background: var(--color-bg-primary);
}

/* ========== 卡片头部 ========== */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.header-icon {
  font-size: var(--font-size-lg);
  color: var(--color-primary);
  transition: all var(--transition-base);
}

.header-left:hover .header-icon {
  transform: scale(1.1);
}

.header-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.header-actions .el-button {
  transition: all var(--transition-base);
}

.header-actions .el-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.graph-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.graph-toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--color-border-lighter);
}

.toolbar-selects {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-sm);
}

.depth-control {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.depth-control :deep(.el-radio-group) {
  flex-shrink: 0;
}

.graph-workbench {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(420px, 32%);
  gap: var(--spacing-md);
  overflow: hidden;
}

.graph-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.graph-title .el-icon {
  font-size: 20px;
  color: #409eff;
}

.graph-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md) var(--spacing-lg);
  margin-top: var(--spacing-md);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border-lighter);
  flex-shrink: 0;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.graph-container {
  flex: 1;
  background: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border-lighter);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-base);
  min-height: 400px;
}

.graph-container:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-border-base);
}

.graph-chart {
  height: 100%;
  width: 100%;
}

.graph-side-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.qa-panel {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: var(--spacing-md);
}

.qa-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  overflow: hidden;
}

.node-detail-dialog :deep(.el-dialog__body) {
  max-height: 62vh;
  overflow-y: auto;
  padding: var(--spacing-lg);
}

.side-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.side-title .el-icon {
  color: var(--color-primary);
}

.question-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--color-border-lighter);
}

.question-suggestions :deep(.el-button) {
  margin-left: 0;
  padding: 4px 6px;
  color: var(--color-text-secondary);
}

.selected-node-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.dialog-node-title {
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--color-border-lighter);
}

.dialog-property-list {
  gap: var(--spacing-sm);
}

.property-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.property-row {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: var(--spacing-sm);
  font-size: var(--font-size-xs);
  line-height: 1.45;
}

.property-key {
  color: var(--color-text-secondary);
  overflow-wrap: anywhere;
}

.property-value {
  color: var(--color-text-primary);
  overflow-wrap: anywhere;
}

.qa-actions {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-sm);
  align-items: center;
}

.qa-action-buttons {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.qa-action-buttons :deep(.el-button) {
  margin-left: 0;
}

.answer-box {
  flex: 1;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  background: var(--color-bg-tertiary);
  padding: var(--spacing-md);
}

.answer-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.answer-text {
  white-space: pre-wrap;
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.citation-link {
  display: inline-flex;
  align-items: center;
  border: 0;
  border-radius: var(--radius-sm);
  padding: 0 4px;
  margin: 0 2px;
  background: var(--color-primary-light-9);
  color: var(--color-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  line-height: 1.5;
  cursor: pointer;
}

.citation-link:hover {
  background: var(--color-primary-light-8);
}

.fallback-note {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: var(--spacing-sm);
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.recognized-entities {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--color-border-lighter);
}

.recognized-entities span:first-child {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.graph-rag-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
  margin-top: var(--spacing-sm);
}

.graph-rag-metric {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-sm);
  padding: 6px;
  min-width: 0;
}

.graph-rag-metric span {
  display: block;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  margin-bottom: 2px;
}

.graph-rag-metric strong {
  color: var(--color-text-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.qa-result-list {
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border-lighter);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.qa-result-header,
.qa-result-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.qa-result-header {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.qa-result-item {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: var(--spacing-sm);
  transition: border-color var(--transition-base), box-shadow var(--transition-base), background-color var(--transition-base);
}

.qa-result-item.active {
  border-color: var(--color-primary);
  background: var(--color-primary-light-9);
  box-shadow: 0 0 0 2px var(--color-primary-light-8);
}

.qa-result-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: 6px;
}

.qa-result-title span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qa-result-fields {
  display: grid;
  grid-template-columns: 1fr;
  gap: 4px;
}

.qa-result-field {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: var(--spacing-sm);
  font-size: var(--font-size-xs);
  line-height: 1.45;
}

.qa-result-field span {
  color: var(--color-text-secondary);
  overflow-wrap: anywhere;
}

.qa-result-field strong {
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
  overflow-wrap: anywhere;
}

.stats-dialog :deep(.el-dialog__body) {
  max-height: 60vh;
  overflow-y: auto;
  padding: 20px;
}

.stats-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stats-section {
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
  border: 1px solid var(--color-border-lighter);
  transition: all var(--transition-base);
}

.stats-section:hover {
  border-color: var(--color-border-base);
  box-shadow: var(--shadow-sm);
}

.stats-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 2px solid var(--color-border-base);
}

.stats-title .el-icon {
  font-size: var(--font-size-lg);
  color: var(--color-primary);
  transition: all var(--transition-base);
}

.stats-title:hover .el-icon {
  transform: scale(1.1);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: var(--spacing-md);
}

.stat-item {
  background: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
  text-align: center;
  transition: all var(--transition-base);
  box-shadow: var(--shadow-xs);
  border: 1px solid var(--color-border-lighter);
}

.stat-item:hover {
  background: var(--color-bg-active);
  transform: translateY(-2px);
  box-shadow: var(--shadow-primary);
  border-color: var(--color-primary);
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-sm);
  word-break: break-all;
  font-weight: var(--font-weight-medium);
}

.stat-value {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.settings-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
  padding: 20px;
}

.settings-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settings-section {
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
  border: 1px solid var(--color-border-lighter);
  transition: all var(--transition-base);
}

.settings-section:hover {
  border-color: var(--color-border-base);
  box-shadow: var(--shadow-xs);
}

.section-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

.section-title .el-icon {
  color: var(--color-primary);
}

.form-row {
  display: flex;
  gap: 12px;
  flex-wrap: nowrap;
  align-items: center;
}

.form-row :deep(.el-form-item) {
  margin-bottom: 0;
}

.form-row :deep(.el-form-item__label) {
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.form-item-half {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.form-control-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-control-wrapper .el-switch {
  margin-right: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid #e5e7eb;
}

.section-title .el-icon {
  font-size: 16px;
  color: #409eff;
}

.settings-form {
  max-width: 100%;
}

.settings-form :deep(.el-form-item) {
  margin-bottom: 8px;
}

.settings-form :deep(.el-form-item__label) {
  padding-bottom: 0;
}



.settings-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
}



.form-unit {
  font-size: 14px;
  color: #606266;
  margin: 0;
}

.section-divider {
  margin: 8px 0;
  border-color: #e5e7eb;
}

.status-panel {
  width: 100%;
}

.status-descriptions {
  background: #fff;
}

.status-descriptions :deep(.el-descriptions__label) {
  font-weight: 500;
  color: #606266;
  background: #f5f7fa;
}

.status-descriptions :deep(.el-descriptions__body) {
  background: #fff;
}

.message-text {
  color: #606266;
  word-break: break-all;
}

.metrics-descriptions {
  margin-top: 16px;
  background: #fff;
}

.metrics-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.metrics-title .el-icon {
  font-size: 16px;
  color: #67c23a;
}

.metric-value {
  font-weight: 600;
  color: #409eff;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 768px) {
  .data-analysis {
    padding: 0;
  }

  .main-card {
    height: 100%;
    min-height: calc(100vh - 84px);
  }

  .graph-footer {
    flex-direction: column;
    gap: 16px;
    align-items: stretch;
  }

  .graph-toolbar {
    padding-bottom: var(--spacing-md);
  }

  .toolbar-selects {
    grid-template-columns: 1fr;
  }

  .graph-workbench {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .graph-side-panel {
    overflow: visible;
  }

  .footer-right {
    justify-content: space-between;
  }

  .graph-chart {
    min-height: 400px;
  }

  .stats-dialog :deep(.el-dialog) {
    width: 95% !important;
    margin: 0 auto;
  }

  .settings-dialog :deep(.el-dialog) {
    width: 95% !important;
    margin: 0 auto;
  }
}
</style>
