<template>
  <div class="statistics">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>数据统计</span>
          <el-button type="primary" @click="loadAllStatistics" :loading="loading">
            <el-icon><Refresh /></el-icon>
            刷新数据
          </el-button>
        </div>
      </template>

      <div v-loading="loading" class="statistics-content">
        <!-- 概览统计卡片 -->
        <div class="overview-cards">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-item">
              <div class="stat-label">用户总数</div>
              <div class="stat-value">{{ overview?.totalUsers || 0 }}</div>
            </div>
          </el-card>
          <el-card class="stat-card" shadow="hover">
            <div class="stat-item">
              <div class="stat-label">应用总数</div>
              <div class="stat-value">{{ overview?.totalApps || 0 }}</div>
            </div>
          </el-card>
          <el-card class="stat-card" shadow="hover">
            <div class="stat-item">
              <div class="stat-label">知识库总数</div>
              <div class="stat-value">{{ overview?.totalKnowledgeBases || 0 }}</div>
            </div>
          </el-card>
          <el-card class="stat-card" shadow="hover">
            <div class="stat-item">
              <div class="stat-label">会话总数</div>
              <div class="stat-value">{{ overview?.totalConversations || 0 }}</div>
            </div>
          </el-card>
          <el-card class="stat-card" shadow="hover">
            <div class="stat-item">
              <div class="stat-label">消息总数</div>
              <div class="stat-value">{{ overview?.totalMessages || 0 }}</div>
            </div>
          </el-card>
        </div>

        <!-- 用户统计 -->
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>用户统计</span>
          </template>
          <div class="chart-container">
            <div class="chart-item">
              <h3>角色分布</h3>
              <v-chart :option="userRoleChartOption" style="height: 300px" />
            </div>
            <div class="chart-item">
              <h3>状态分布</h3>
              <v-chart :option="userStatusChartOption" style="height: 300px" />
            </div>
            <div class="chart-item full-width">
              <h3>注册趋势（最近30天）</h3>
              <v-chart :option="userRegistrationTrendOption" style="height: 300px" />
            </div>
          </div>
        </el-card>

        <!-- 应用统计 -->
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>应用统计</span>
          </template>
          <div class="chart-container">
            <div class="chart-item">
              <h3>类型分布</h3>
              <v-chart :option="appTypeChartOption" style="height: 300px" />
            </div>
            <div class="chart-item full-width">
              <h3>应用使用情况（Top 10）</h3>
              <v-chart :option="appUsageChartOption" style="height: 300px" />
            </div>
          </div>
        </el-card>

        <!-- 知识库统计 -->
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>知识库统计</span>
          </template>
          <div class="chart-container">
            <div class="chart-item">
              <h3>状态分布</h3>
              <v-chart :option="kbStatusChartOption" style="height: 300px" />
            </div>
            <div class="chart-item full-width">
              <h3>知识库使用情况（Top 10）</h3>
              <v-chart :option="kbUsageChartOption" style="height: 300px" />
            </div>
          </div>
        </el-card>

        <!-- 会话历史统计 -->
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>会话历史统计</span>
          </template>
          <div class="chart-container">
            <div class="chart-item">
              <h3>对话类型分布</h3>
              <v-chart :option="chatTypeChartOption" style="height: 300px" />
            </div>
            <div class="chart-item full-width">
              <h3>用户对话排行（Top 10）</h3>
              <v-chart :option="userConversationRankChartOption" style="height: 300px" />
            </div>
            <div class="chart-item full-width">
              <h3>时间趋势（最近30天）</h3>
              <v-chart :option="chatTrendChartOption" style="height: 300px" />
            </div>
          </div>
        </el-card>

        <!-- 模型Token统计 -->
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>模型Token统计</span>
          </template>
          <div class="chart-container">
            <div v-if="modelTokens?.totalTokens === 0 || !modelTokens?.modelTokenUsage?.length" class="empty-tip">
              <el-empty description="暂无Token统计数据，需要在消息中记录Token使用信息" />
            </div>
            <template v-else>
              <div class="chart-item full-width">
                <h3>各模型Token使用量</h3>
                <v-chart :option="modelTokenUsageChartOption" style="height: 300px" />
              </div>
              <div class="chart-item">
                <h3>模型使用占比</h3>
                <v-chart :option="modelDistributionChartOption" style="height: 300px" />
              </div>
              <div class="chart-item full-width">
                <h3>Token使用趋势（最近30天）</h3>
                <v-chart :option="tokenTrendChartOption" style="height: 300px" />
              </div>
            </template>
          </div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import {
  getAllStatistics,
  getChatHistoryStatistics
} from '@/api/statistics'

// 注册ECharts组件
use([
  CanvasRenderer,
  PieChart,
  BarChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
])

const loading = ref(false)
const overview = ref(null)
const users = ref(null)
const apps = ref(null)
const knowledgeBases = ref(null)
const modelTokens = ref(null)
const chatHistory = ref(null)

// 用户角色分布图表配置
const userRoleChartOption = computed(() => {
  if (!users.value?.roleDistribution) {
    return { title: { text: '暂无数据' } }
  }
  const data = Object.entries(users.value.roleDistribution).map(([name, value]) => ({
    name,
    value
  }))
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '角色分布',
        type: 'pie',
        radius: '50%',
        data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
})

// 用户状态分布图表配置
const userStatusChartOption = computed(() => {
  if (!users.value?.statusDistribution) {
    return { title: { text: '暂无数据' } }
  }
  const data = Object.entries(users.value.statusDistribution).map(([name, value]) => ({
    name,
    value
  }))
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '状态分布',
        type: 'pie',
        radius: '50%',
        data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
})

// 用户注册趋势图表配置
const userRegistrationTrendOption = computed(() => {
  if (!users.value?.registrationTrend || users.value.registrationTrend.length === 0) {
    return { title: { text: '暂无数据' } }
  }
  const dates = users.value.registrationTrend.map(item => item.date)
  const counts = users.value.registrationTrend.map(item => item.count)
  return {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '注册数',
        type: 'line',
        data: counts,
        smooth: true
      }
    ]
  }
})

// 应用类型分布图表配置
const appTypeChartOption = computed(() => {
  if (!apps.value?.typeDistribution) {
    return { title: { text: '暂无数据' } }
  }
  const data = Object.entries(apps.value.typeDistribution).map(([name, value]) => ({
    name,
    value
  }))
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '类型分布',
        type: 'pie',
        radius: '50%',
        data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
})

// 应用使用情况图表配置
const appUsageChartOption = computed(() => {
  if (!apps.value?.appUsage || apps.value.appUsage.length === 0) {
    return { title: { text: '暂无数据' } }
  }
  const names = apps.value.appUsage.map(item => item.appName)
  const counts = apps.value.appUsage.map(item => item.conversationCount)
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '会话数',
        type: 'bar',
        data: counts
      }
    ]
  }
})

// 知识库状态分布图表配置
const kbStatusChartOption = computed(() => {
  if (!knowledgeBases.value?.statusDistribution) {
    return { title: { text: '暂无数据' } }
  }
  const data = Object.entries(knowledgeBases.value.statusDistribution).map(([name, value]) => ({
    name,
    value
  }))
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '状态分布',
        type: 'pie',
        radius: '50%',
        data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
})

// 知识库使用情况图表配置
const kbUsageChartOption = computed(() => {
  if (!knowledgeBases.value?.kbUsage || knowledgeBases.value.kbUsage.length === 0) {
    return { title: { text: '暂无数据' } }
  }
  const names = knowledgeBases.value.kbUsage.map(item => item.kbName)
  const counts = knowledgeBases.value.kbUsage.map(item => item.conversationCount)
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '会话数',
        type: 'bar',
        data: counts
      }
    ]
  }
})

// 对话类型分布图表配置
const chatTypeChartOption = computed(() => {
  if (!chatHistory.value?.typeDistribution) {
    return { title: { text: '暂无数据' } }
  }
  const data = Object.entries(chatHistory.value.typeDistribution).map(([name, value]) => ({
    name,
    value
  }))
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '对话类型',
        type: 'pie',
        radius: '50%',
        data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
})

// 用户对话排行图表配置
const userConversationRankChartOption = computed(() => {
  if (!chatHistory.value?.userConversationRanks || chatHistory.value.userConversationRanks.length === 0) {
    return { title: { text: '暂无数据' } }
  }
  const names = chatHistory.value.userConversationRanks.map(item => item.username)
  const counts = chatHistory.value.userConversationRanks.map(item => item.conversationCount)
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: names
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '对话数',
        type: 'bar',
        data: counts
      }
    ]
  }
})

// 会话时间趋势图表配置
const chatTrendChartOption = computed(() => {
  if (!chatHistory.value?.dailyStatistics || chatHistory.value.dailyStatistics.length === 0) {
    return { title: { text: '暂无数据' } }
  }
  const dates = chatHistory.value.dailyStatistics.map(item => item.date)
  const conversationCounts = chatHistory.value.dailyStatistics.map(item => item.conversationCount)
  const messageCounts = chatHistory.value.dailyStatistics.map(item => item.messageCount)
  return {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['对话数', '消息数']
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '对话数',
        type: 'line',
        data: conversationCounts,
        smooth: true
      },
      {
        name: '消息数',
        type: 'line',
        data: messageCounts,
        smooth: true
      }
    ]
  }
})

// 模型Token使用量图表配置
const modelTokenUsageChartOption = computed(() => {
  if (!modelTokens.value?.modelTokenUsage || modelTokens.value.modelTokenUsage.length === 0) {
    return { title: { text: '暂无数据' } }
  }
  const names = modelTokens.value.modelTokenUsage.map(item => item.modelName)
  const promptTokens = modelTokens.value.modelTokenUsage.map(item => item.promptTokens || 0)
  const completionTokens = modelTokens.value.modelTokenUsage.map(item => item.completionTokens || 0)
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    legend: {
      data: ['Prompt Tokens', 'Completion Tokens']
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: 'Prompt Tokens',
        type: 'bar',
        data: promptTokens
      },
      {
        name: 'Completion Tokens',
        type: 'bar',
        data: completionTokens
      }
    ]
  }
})

// 模型使用占比图表配置
const modelDistributionChartOption = computed(() => {
  if (!modelTokens.value?.modelDistribution) {
    return { title: { text: '暂无数据' } }
  }
  const data = Object.entries(modelTokens.value.modelDistribution).map(([name, value]) => ({
    name,
    value
  }))
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '模型使用',
        type: 'pie',
        radius: '50%',
        data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }
})

// Token使用趋势图表配置
const tokenTrendChartOption = computed(() => {
  if (!modelTokens.value?.tokenTrend || modelTokens.value.tokenTrend.length === 0) {
    return { title: { text: '暂无数据' } }
  }
  const dates = modelTokens.value.tokenTrend.map(item => item.date)
  const totalTokens = modelTokens.value.tokenTrend.map(item => item.totalTokens || 0)
  const promptTokens = modelTokens.value.tokenTrend.map(item => item.promptTokens || 0)
  const completionTokens = modelTokens.value.tokenTrend.map(item => item.completionTokens || 0)
  return {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['总Token数', 'Prompt Tokens', 'Completion Tokens']
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '总Token数',
        type: 'line',
        data: totalTokens,
        smooth: true
      },
      {
        name: 'Prompt Tokens',
        type: 'line',
        data: promptTokens,
        smooth: true
      },
      {
        name: 'Completion Tokens',
        type: 'line',
        data: completionTokens,
        smooth: true
      }
    ]
  }
})

// 加载所有统计数据
const loadAllStatistics = async () => {
  loading.value = true
  try {
    // 加载主要统计数据
    const statsResponse = await getAllStatistics()
    overview.value = statsResponse.overview
    users.value = statsResponse.users
    apps.value = statsResponse.apps
    knowledgeBases.value = statsResponse.knowledgeBases
    modelTokens.value = statsResponse.modelTokens

    // 加载会话历史统计
    const chatHistoryResponse = await getChatHistoryStatistics()
    chatHistory.value = chatHistoryResponse
  } catch (error) {
    console.error('加载统计数据失败:', error)
    ElMessage.error('加载统计数据失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadAllStatistics()
})
</script>

<style scoped>
.statistics {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
}

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: 18px 20px;
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 20px;
  min-height: 0;
  /* 确保底部有足够的空间，防止内容被遮挡 */
  padding-bottom: 30px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.statistics-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  /* 确保滚动条样式统一应用 */
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.2) transparent;
  /* 添加底部padding，防止内容被遮挡 */
  padding-bottom: 60px;
  /* 确保内容区域有足够的空间 */
  box-sizing: border-box;
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
}

.stat-item {
  padding: 10px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 10px;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
}

.chart-card {
  margin-bottom: 20px;
}

/* 确保最后一个卡片有足够的底部空间 */
.chart-card:last-child {
  margin-bottom: 0;
}

.chart-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 20px;
  /* 确保最后一个图表有足够的底部空间 */
  padding-bottom: 20px;
}

.chart-item {
  min-height: 300px;
}

.chart-item.full-width {
  grid-column: 1 / -1;
}

.chart-item h3 {
  margin: 0 0 10px 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.empty-tip {
  grid-column: 1 / -1;
  padding: 40px;
  text-align: center;
}
</style>

