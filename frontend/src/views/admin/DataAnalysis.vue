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

        <div v-loading="graphLoading" class="graph-container">
          <v-chart v-if="graphOption && graphData?.nodes?.length" :option="graphOption" autoresize class="graph-chart" />
          <el-empty v-else description="暂无图数据，请先配置并运行数据同步" :image-size="120">
            <el-button type="primary" @click="openSettingsDialog" :icon="Setting">
              前往配置
            </el-button>
          </el-empty>
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
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, DataAnalysis, DataLine, Link, Loading, Monitor, Refresh, Setting, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { getDataSourceList } from '@/api/dataSource'
import { getDataAnalysisGraph, getDataAnalysisSettings, getDataAnalysisStatus, runDataAnalysis, updateDataAnalysisSettings } from '@/api/dataAnalysis'
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
    category: categoryIndex.get(n.label || 'Unknown') ?? 0,
    symbolSize: 18
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
    return
  }
  graphLoading.value = true
  try {
    graphData.value = await getDataAnalysisGraph({ limit: graphLimit.value })
  } catch (e) {
    logger.error('加载图数据失败:', e)
    ElMessage.error('加载图数据失败：' + (e.response?.data?.message || e.message || '未知错误'))
    graphData.value = null
  } finally {
    graphLoading.value = false
  }
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