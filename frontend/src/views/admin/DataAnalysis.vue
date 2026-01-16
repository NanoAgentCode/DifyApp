<template>
  <div class="data-analysis">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>数据分析</span>
          <div class="header-actions">
            <el-button @click="loadStatus" :loading="loadingStatus">
              刷新状态
            </el-button>
            <el-button type="success" @click="runNow" :loading="runningNow" :disabled="!settingsForm.neo4jDataSourceId">
              立即同步
            </el-button>
            <el-tag v-if="saving" type="info">保存中</el-tag>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="同步设置" name="settings">
          <el-form label-width="140px" class="settings-form">
            <el-form-item label="Neo4j 数据源">
              <el-select
                v-model="settingsForm.neo4jDataSourceId"
                placeholder="请选择数据源（type=neo4j）"
                filterable
                clearable
                style="width: 420px"
              >
                <el-option
                  v-for="ds in neo4jDataSources"
                  :key="ds.id"
                  :label="ds.name"
                  :value="ds.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="启用定时同步">
              <el-switch v-model="settingsForm.enabled" />
            </el-form-item>

            <el-form-item label="同步间隔（分钟）">
              <el-input-number v-model="settingsForm.intervalMinutes" :min="1" :max="1440" />
            </el-form-item>
          </el-form>

          <el-divider />

          <div v-loading="loadingStatus" class="status-panel">
            <el-descriptions title="同步状态" :column="2" border>
              <el-descriptions-item label="是否运行中">
                <el-tag :type="status?.running ? 'warning' : 'info'">
                  {{ status?.running ? '运行中' : '未运行' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="最近状态">
                <el-tag :type="statusTagType(status?.lastStatus)">
                  {{ status?.lastStatus || 'never' }}
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
              <el-descriptions-item label="最近消息">
                {{ status?.lastMessage || '-' }}
              </el-descriptions-item>
            </el-descriptions>

            <el-descriptions v-if="status?.metrics" title="同步指标" :column="2" border class="metrics">
              <el-descriptions-item v-for="(v, k) in status.metrics" :key="k" :label="k">
                {{ v }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-tab-pane>

        <el-tab-pane label="数据视图" name="graph">
          <div class="graph-toolbar">
            <el-input-number v-model="graphLimit" :min="10" :max="2000" />
          </div>

          <div v-loading="graphLoading" class="graph-container">
            <v-chart v-if="graphOption && graphData?.nodes?.length" :option="graphOption" autoresize class="graph-chart" />
            <el-empty v-else description="暂无图数据" :image-size="80" />
          </div>

          <div v-if="graphData?.nodeCounts || graphData?.relationshipCounts" class="graph-stats">
            <el-descriptions v-if="graphData?.nodeCounts" title="节点统计" :column="2" border>
              <el-descriptions-item v-for="(v, k) in graphData.nodeCounts" :key="k" :label="k">
                {{ v }}
              </el-descriptions-item>
            </el-descriptions>
            <el-descriptions v-if="graphData?.relationshipCounts" title="关系统计" :column="2" border class="metrics">
              <el-descriptions-item v-for="(v, k) in graphData.relationshipCounts" :key="k" :label="k">
                {{ v }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
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

const activeTab = ref('settings')

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
let autoSaveTimer = null
watch(
  () => [settingsForm.enabled, settingsForm.neo4jDataSourceId, settingsForm.intervalMinutes],
  () => {
    if (isLoadingSettings.value) return
    if (autoSaveTimer) clearTimeout(autoSaveTimer)
    autoSaveTimer = setTimeout(async () => {
      saving.value = true
      try {
        await updateDataAnalysisSettings({
          enabled: settingsForm.enabled,
          neo4jDataSourceId: settingsForm.neo4jDataSourceId,
          intervalMinutes: settingsForm.intervalMinutes
        })
        await loadStatus()
      } catch (e) {
        logger.error('自动保存配置失败:', e)
        ElMessage.error('自动保存失败：' + (e.response?.data?.message || e.message || '未知错误'))
      } finally {
        saving.value = false
      }
    }, 500)
  }
)

const graphLoading = ref(false)
const graphLimit = ref(200)
const graphData = ref(null)
let graphLoadTimer = null

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
        focusNodeAdjacency: true,
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
    if (activeTab.value !== 'graph') return
    loadGraph()
  }, delayMs)
}

watch(activeTab, (tab) => {
  if (tab === 'graph') {
    scheduleGraphLoad(0)
  }
})

watch(graphLimit, () => {
  if (activeTab.value !== 'graph') return
  scheduleGraphLoad(300)
})

watch(() => settingsForm.neo4jDataSourceId, () => {
  if (activeTab.value !== 'graph') return
  scheduleGraphLoad(0)
})

onMounted(async () => {
  await loadDataSources()
  await loadSettings()
  await loadStatus()
  startPollingIfRunning()
})

onUnmounted(() => {
  stopPolling()
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  if (graphLoadTimer) clearTimeout(graphLoadTimer)
})
</script>

<style scoped>
.data-analysis {
  width: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.settings-form {
  max-width: 720px;
}

.status-panel {
  width: 100%;
}

.metrics {
  margin-top: 16px;
}

.graph-toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.graph-container {
  width: 100%;
}

.graph-chart {
  height: 560px;
  width: 100%;
}

.graph-stats {
  margin-top: 16px;
}
</style>
