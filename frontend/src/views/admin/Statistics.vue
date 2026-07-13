<template>
  <div class="statistics">
    <el-card>
      <template #header><div class="card-header"><span>数据统计</span><el-button type="primary" @click="loadAllStatistics" :loading="loading"><el-icon><Refresh /></el-icon>刷新数据</el-button></div></template>
      <div v-loading="loading" class="statistics-content">
        <StatisticsOverviewCards :overview="overview" />
        <StatisticsPieCharts :users="users" :apps="apps" :knowledge-bases="knowledgeBases" :chat-history="chatHistory" :model-tokens="modelTokens" :user-role-chart-option="userRoleChartOption" :user-status-chart-option="userStatusChartOption" :app-type-chart-option="appTypeChartOption" :kb-status-chart-option="kbStatusChartOption" :chat-type-chart-option="chatTypeChartOption" :model-distribution-chart-option="modelDistributionChartOption" />
        <StatisticsBarCharts v-model:active-bar-tab="activeBarTab" :apps="apps" :knowledge-bases="knowledgeBases" :chat-history="chatHistory" :model-tokens="modelTokens" :chart-update-key="chartUpdateKey" :app-usage-chart-option="appUsageChartOption" :kb-usage-chart-option="kbUsageChartOption" :user-conversation-rank-chart-option="userConversationRankChartOption" :model-token-usage-chart-option="modelTokenUsageChartOption" @tab-change="handleBarTabChange" />
        <StatisticsTrendCharts v-model:time-range="timeRange" v-model:active-trend-tab="activeTrendTab" :users="users" :chat-history="chatHistory" :model-tokens="modelTokens" :chart-update-key="chartUpdateKey" :user-registration-trend-option="userRegistrationTrendOption" :chat-trend-chart-option="chatTrendChartOption" :token-trend-chart-option="tokenTrendChartOption" :get-trend-summary="getTrendSummary" :get-trend-class="getTrendClass" :get-trend-direction="getTrendDirection" :get-trend-percent="getTrendPercent" :format-token-count="formatTokenCount" @time-range-change="handleTimeRangeChange" @tab-change="handleTabChange" />
      </div>
    </el-card>
  </div>
</template>
<script setup>
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { logger } from '@/utils/logger'
import StatisticsOverviewCards from '@/components/statistics/StatisticsOverviewCards.vue'
import StatisticsPieCharts from '@/components/statistics/StatisticsPieCharts.vue'
import StatisticsBarCharts from '@/components/statistics/StatisticsBarCharts.vue'
import StatisticsTrendCharts from '@/components/statistics/StatisticsTrendCharts.vue'
import { Refresh, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
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
import { useStatisticsData } from '@/composables/useStatisticsData'

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

const timeRange = ref('30')
const activeTrendTab = ref('users')
const activeBarTab = ref('apps')
const chartUpdateKey = ref(0)
const { loading, overview, users, apps, knowledgeBases, modelTokens, chatHistory, loadAllStatistics } = useStatisticsData(
  timeRange,
  async () => {
    await nextTick()
    chartUpdateKey.value++
  }
)

// 统一的X轴标签显示间隔计算函数
const getXAxisInterval = (rangeDays) => {
  return (index) => {
    // 根据时间范围动态调整显示间隔
    // 返回0表示显示该标签，返回'auto'表示跳过
    if (rangeDays <= 7) {
      return 0 // 7天显示所有标签
    } else if (rangeDays <= 30) {
      // 30天每隔一个显示（显示偶数索引：0, 2, 4, 6...）
      return index % 2 === 0 ? 0 : 'auto'
    } else {
      // 90天每隔两个显示（显示能被3整除的索引：0, 3, 6, 9...）
      return index % 3 === 0 ? 0 : 'auto'
    }
  }
}

// 生成指定天数的日期列表（最近N天）
const generateDateRange = (days) => {
  const dates = []
  const today = new Date()
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date(today)
    date.setDate(date.getDate() - i)
    const dateStr = date.toISOString().split('T')[0] // 格式：YYYY-MM-DD
    dates.push(dateStr)
  }
  return dates
}

// 保护性数据补充函数：如果后端数据条目不够，补充缺失日期（值为0）
// 正常情况下后端应该返回完整数据，此函数作为安全保护措施
const ensureCompleteDateRange = (dataArray, dateKey, valueKeys, expectedDays) => {
  if (!dataArray || dataArray.length === 0) {
    // 如果完全没有数据，生成完整日期范围，所有值设为0
    const fullDateRange = generateDateRange(expectedDays)
    // 优化：使用for循环替代map
    const result = []
    for (let i = 0; i < fullDateRange.length; i++) {
      const date = fullDateRange[i]
      const item = { [dateKey]: date }
      for (let j = 0; j < valueKeys.length; j++) {
        item[valueKeys[j]] = 0
      }
      result.push(item)
    }
    return result
  }

  // 如果数据条数已经等于期望天数，直接返回（后端已保证完整）
  if (dataArray.length === expectedDays) {
    return dataArray
  }

  // 如果数据条数不够，补充缺失日期
  if (dataArray.length < expectedDays) {
    logger.debug(`后端数据条目不足：期望${expectedDays}天，实际${dataArray.length}天，正在补充缺失日期`)

    const fullDateRange = generateDateRange(expectedDays)
    const dataMap = new Map()

    // 将现有数据转换为Map，以日期为key（优化：使用for循环）
    for (let i = 0; i < dataArray.length; i++) {
      const item = dataArray[i]
      if (item && item[dateKey]) {
        let dateStr = item[dateKey]
        if (typeof dateStr === 'string') {
          dateStr = dateStr.split(' ')[0].split('T')[0]
        } else if (dateStr instanceof Date) {
          dateStr = dateStr.toISOString().split('T')[0]
        }
        dataMap.set(dateStr, item)
      }
    }

    // 为每个日期创建数据对象，缺失的日期值设为0（优化：使用for循环）
    const filledData = []
    for (let i = 0; i < fullDateRange.length; i++) {
      const date = fullDateRange[i]
      const normalizedDate = date.split(' ')[0].split('T')[0]

      if (dataMap.has(normalizedDate)) {
        const existingItem = dataMap.get(normalizedDate)
        filledData.push({ ...existingItem, [dateKey]: normalizedDate })
      } else {
        // 创建缺失日期的数据对象，所有值设为0
        const missingItem = { [dateKey]: normalizedDate }
        for (let j = 0; j < valueKeys.length; j++) {
          missingItem[valueKeys[j]] = 0
        }
        filledData.push(missingItem)
      }
    }

    return filledData
  }

  // 如果数据条数超过期望天数，截取最近N天
  if (dataArray.length > expectedDays) {
    logger.debug(`后端数据条目过多：期望${expectedDays}天，实际${dataArray.length}天，截取最近${expectedDays}天`)
    return dataArray.slice(-expectedDays)
  }

  return dataArray
}

// 用户角色分布图表配置
const userRoleChartOption = computed(() => {
  if (!users.value?.roleDistribution) {
    return { title: { text: '暂无数据' } }
  }
  // 优化：使用for循环替代map
  const entries = Object.entries(users.value.roleDistribution)
  const data = []
  for (let i = 0; i < entries.length; i++) {
    const [name, value] = entries[i]
    data.push({ name, value })
  }
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'horizontal',
      bottom: '0%',
      left: 'center'
    },
    series: [
      {
        name: '角色分布',
        type: 'pie',
        radius: '60%',
        center: ['50%', '45%'],
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
  // 优化：使用for循环替代map
  const entries = Object.entries(users.value.statusDistribution)
  const data = []
  for (let i = 0; i < entries.length; i++) {
    const [name, value] = entries[i]
    data.push({ name, value })
  }
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'horizontal',
      bottom: '0%',
      left: 'center'
    },
    series: [
      {
        name: '状态分布',
        type: 'pie',
        radius: '60%',
        center: ['50%', '45%'],
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
  // 确保依赖timeRange，使其响应式更新
  const currentTimeRange = timeRange.value
  const currentData = users.value?.registrationTrend

  if (!currentData || currentData.length === 0) {
    return {
      title: { text: '暂无数据', left: 'center', top: 'middle' },
      grid: { left: '3%', right: '4%', bottom: '25%', containLabel: true }
    }
  }

  // 根据timeRange确定天数
  const rangeDays = parseInt(currentTimeRange) || 30

  // 使用后端返回的数据，如果数据条目不够则补充缺失日期（保护性措施）
  const completeData = ensureCompleteDateRange(currentData, 'date', ['count'], rangeDays)
  // 优化：使用for循环替代map
  const dates = []
  const counts = []
  for (let i = 0; i < completeData.length; i++) {
    dates.push(completeData[i].date)
    counts.push(completeData[i].count || 0)
  }

  // 确保dates数组有效且长度正确
  if (!dates || dates.length === 0) {
    logger.debug('用户趋势：dates数组为空')
    return {
      title: { text: '数据错误', left: 'center', top: 'middle' },
      grid: { left: '3%', right: '4%', bottom: '25%', containLabel: true }
    }
  }

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      },
      hideDelay: 100, // 失去焦点后延迟100ms隐藏
      enterable: false, // 不允许鼠标进入tooltip
      confine: true // 将tooltip限制在图表区域内
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '40%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLabel: {
        rotate: 45,
        margin: 15,
        interval: getXAxisInterval(rangeDays),
        showMaxLabel: true,
        showMinLabel: true,
        formatter: function(value) {
          // 如果日期是完整格式，只显示月-日
          if (value && typeof value === 'string' && value.length >= 10) {
            return value.substring(5, 10) // 显示 MM-DD
          }
          return value
        }
      },
      axisTick: {
        alignWithLabel: true, // 刻度线与标签对齐
        show: true,
        interval: 0
      },
      scale: false // 确保X轴不自动缩放
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: '{value}'
      }
    },
    series: [
      {
        name: '注册数',
        type: 'line',
        data: counts,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          width: 2,
          color: '#409eff'
        },
        itemStyle: {
          color: '#409eff'
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
              { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
            ]
          }
        }
      }
    ]
  }
})

// 应用类型分布图表配置
const appTypeChartOption = computed(() => {
  if (!apps.value?.typeDistribution) {
    return { title: { text: '暂无数据' } }
  }
  // 优化：使用for循环替代map
  const entries = Object.entries(apps.value.typeDistribution)
  const data = []
  for (let i = 0; i < entries.length; i++) {
    const [name, value] = entries[i]
    data.push({ name, value })
  }
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'horizontal',
      bottom: '0%',
      left: 'center'
    },
    series: [
      {
        name: '类型分布',
        type: 'pie',
        radius: '60%',
        center: ['50%', '45%'],
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
  // 优化：使用for循环替代map
  const usage = apps.value.appUsage
  const names = []
  const counts = []
  for (let i = 0; i < usage.length; i++) {
    names.push(usage[i].appName)
    counts.push(usage[i].conversationCount)
  }
  return {
    grid: {
      left: '3%',
      right: '4%',
      bottom: '12%',
      top: '5%',
      containLabel: true
    },
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
        data: counts,
        barWidth: '5%'
      }
    ]
  }
})

// 知识库状态分布图表配置
const kbStatusChartOption = computed(() => {
  if (!knowledgeBases.value?.statusDistribution) {
    return { title: { text: '暂无数据' } }
  }
  // 优化：使用for循环替代map
  const entries = Object.entries(knowledgeBases.value.statusDistribution)
  const data = []
  for (let i = 0; i < entries.length; i++) {
    const [name, value] = entries[i]
    data.push({ name, value })
  }
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'horizontal',
      bottom: '0%',
      left: 'center'
    },
    series: [
      {
        name: '状态分布',
        type: 'pie',
        radius: '60%',
        center: ['50%', '45%'],
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
  // 优化：使用for循环替代map
  const usage = knowledgeBases.value.kbUsage
  const names = []
  const counts = []
  for (let i = 0; i < usage.length; i++) {
    names.push(usage[i].kbName)
    counts.push(usage[i].conversationCount)
  }
  return {
    grid: {
      left: '3%',
      right: '4%',
      bottom: '12%',
      top: '5%',
      containLabel: true
    },
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
        data: counts,
        barWidth: '5%'
      }
    ]
  }
})

// 对话类型分布图表配置
const chatTypeChartOption = computed(() => {
  if (!chatHistory.value?.typeDistribution) {
    return { title: { text: '暂无数据' } }
  }
  // 优化：使用for循环替代map
  const entries = Object.entries(chatHistory.value.typeDistribution)
  const data = []
  for (let i = 0; i < entries.length; i++) {
    const [name, value] = entries[i]
    data.push({ name, value })
  }
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'horizontal',
      bottom: '0%',
      left: 'center'
    },
    series: [
      {
        name: '对话类型',
        type: 'pie',
        radius: '60%',
        center: ['50%', '45%'],
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
  // 优化：使用for循环替代map
  const ranks = chatHistory.value.userConversationRanks
  const names = []
  const counts = []
  for (let i = 0; i < ranks.length; i++) {
    names.push(ranks[i].username)
    counts.push(ranks[i].conversationCount)
  }
  return {
    grid: {
      left: '3%',
      right: '4%',
      bottom: '8%',
      top: '5%',
      containLabel: true
    },
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
        data: counts,
        barWidth: '5%'
      }
    ]
  }
})

// 会话时间趋势图表配置
const chatTrendChartOption = computed(() => {
  // 确保依赖timeRange，使其响应式更新
  const currentTimeRange = timeRange.value
  const currentData = chatHistory.value?.dailyStatistics

  if (!currentData || currentData.length === 0) {
    return {
      title: { text: '暂无数据', left: 'center', top: 'middle' },
      grid: { left: '3%', right: '4%', bottom: '25%', containLabel: true }
    }
  }

  // 根据timeRange确定天数
  const rangeDays = parseInt(currentTimeRange) || 30

  // 使用后端返回的数据，如果数据条目不够则补充缺失日期（保护性措施）
  const completeData = ensureCompleteDateRange(currentData, 'date', ['conversationCount', 'messageCount'], rangeDays)
  // 优化：使用for循环替代map
  const dates = []
  const conversationCounts = []
  const messageCounts = []
  for (let i = 0; i < completeData.length; i++) {
    dates.push(completeData[i].date)
    conversationCounts.push(completeData[i].conversationCount || 0)
    messageCounts.push(completeData[i].messageCount || 0)
  }

  // 确保dates数组有效且长度正确
  if (!dates || dates.length === 0) {
    logger.debug('会话趋势：dates数组为空')
    return {
      title: { text: '数据错误', left: 'center', top: 'middle' },
      grid: { left: '3%', right: '4%', bottom: '25%', containLabel: true }
    }
  }

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      },
      hideDelay: 100, // 失去焦点后延迟100ms隐藏
      enterable: false, // 不允许鼠标进入tooltip
      confine: true // 将tooltip限制在图表区域内
    },
    legend: {
      data: ['对话数', '消息数'],
      bottom: '8%'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '40%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLabel: {
        rotate: 45,
        margin: 15,
        interval: getXAxisInterval(rangeDays),
        showMaxLabel: true,
        showMinLabel: true,
        formatter: function(value) {
          // 如果日期是完整格式，只显示月-日
          if (value && typeof value === 'string' && value.length >= 10) {
            return value.substring(5, 10) // 显示 MM-DD
          }
          return value
        }
      },
      axisTick: {
        alignWithLabel: true,
        show: true,
        interval: 0
      },
      scale: false // 确保X轴不自动缩放
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: '{value}'
      }
    },
    series: [
      {
        name: '对话数',
        type: 'line',
        data: conversationCounts,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          width: 2,
          color: '#409eff'
        },
        itemStyle: {
          color: '#409eff'
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
              { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
            ]
          }
        }
      },
      {
        name: '消息数',
        type: 'line',
        data: messageCounts,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          width: 2,
          color: '#67c23a'
        },
        itemStyle: {
          color: '#67c23a'
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(103, 194, 58, 0.3)' },
              { offset: 1, color: 'rgba(103, 194, 58, 0.05)' }
            ]
          }
        }
      }
    ]
  }
})

// 模型Token使用量图表配置
const modelTokenUsageChartOption = computed(() => {
  if (!modelTokens.value?.modelTokenUsage || modelTokens.value.modelTokenUsage.length === 0) {
    return { title: { text: '暂无数据' } }
  }
  // 优化：使用for循环替代map
  const usage = modelTokens.value.modelTokenUsage
  const names = []
  const promptTokens = []
  const completionTokens = []
  for (let i = 0; i < usage.length; i++) {
    names.push(usage[i].modelName)
    promptTokens.push(usage[i].promptTokens || 0)
    completionTokens.push(usage[i].completionTokens || 0)
  }
  return {
    grid: {
      left: '3%',
      right: '4%',
      bottom: '12%',
      top: '8%',
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    legend: {
      data: ['Prompt Tokens', 'Completion Tokens'],
      top: '0%'
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
        data: promptTokens,
        barWidth: '5%'
      },
      {
        name: 'Completion Tokens',
        type: 'bar',
        data: completionTokens,
        barWidth: '5%'
      }
    ]
  }
})

// 模型使用占比图表配置
const modelDistributionChartOption = computed(() => {
  if (!modelTokens.value?.modelDistribution) {
    return { title: { text: '暂无数据' } }
  }
    // 优化：使用for循环替代map
    const entries = Object.entries(modelTokens.value.modelDistribution)
    const data = []
    for (let i = 0; i < entries.length; i++) {
      const [name, value] = entries[i]
      data.push({ name, value })
    }
  return {
    tooltip: {
      trigger: 'item',
      formatter: function(params) {
        return params.seriesName + '<br/>' +
               params.name + ': ' +
               params.value.toLocaleString() + ' 次 (' +
               params.percent + '%)'
      }
    },
    legend: {
      orient: 'horizontal',
      bottom: '0%',
      left: 'center'
    },
    series: [
      {
        name: '模型使用',
        type: 'pie',
        radius: '60%',
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}\n{d}%'
        },
        labelLine: {
          show: true
        },
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
  try {
    // 确保依赖timeRange，使其响应式更新
    const currentTimeRange = timeRange.value
    const currentData = modelTokens.value?.tokenTrend

    // 数据验证
    if (!currentData) {
      logger.debug('Token趋势数据不存在')
      return {
        title: { text: '暂无数据', left: 'center', top: 'middle' },
        grid: { left: '3%', right: '4%', bottom: '25%', containLabel: true }
      }
    }

    if (currentData.length === 0) {
      logger.debug('Token趋势数据为空数组')
      return {
        title: { text: '暂无数据', left: 'center', top: 'middle' },
        grid: { left: '3%', right: '4%', bottom: '25%', containLabel: true }
      }
    }

    // 根据timeRange确定天数
    const rangeDays = parseInt(currentTimeRange) || 30

    // 按模型分组数据
    const modelDataMap = new Map()
    const allDates = new Set()

    // 收集所有日期和按模型分组的数据
    currentData.forEach(item => {
      if (!item.modelId || !item.modelName) return

      const modelKey = `${item.modelId}_${item.modelName}`
      if (!modelDataMap.has(modelKey)) {
        modelDataMap.set(modelKey, {
          modelId: item.modelId,
          modelName: item.modelName,
          data: new Map()
        })
      }

      allDates.add(item.date)
      modelDataMap.get(modelKey).data.set(item.date, item)
    })

    // 生成完整的日期范围
    const fullDateRange = []
    const today = new Date()
    for (let i = rangeDays - 1; i >= 0; i--) {
      const date = new Date(today)
      date.setDate(date.getDate() - i)
      const dateStr = date.toISOString().split('T')[0]
      fullDateRange.push(dateStr)
      allDates.add(dateStr)
    }

    const dates = Array.from(allDates).sort()

    // 为每个模型生成数据系列
    const series = []
    const legendData = []
    const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#606266']
    let colorIndex = 0

    // 存储每个数据点的完整信息，用于tooltip
    const dataPointMap = new Map() // key: `${modelKey}_${date}`, value: item数据

    modelDataMap.forEach((modelInfo, modelKey) => {
      const modelData = []
      dates.forEach((date, dateIdx) => {
        const item = modelInfo.data.get(date)
        if (item) {
          modelData.push(item.totalTokens || 0)
          // 存储数据点信息
          dataPointMap.set(`${modelKey}_${dateIdx}`, item)
        } else {
          modelData.push(0)
        }
      })

      series.push({
        name: modelInfo.modelName,
        type: 'line',
        data: modelData,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          width: 2,
          color: colors[colorIndex % colors.length]
        },
        itemStyle: {
          color: colors[colorIndex % colors.length]
        },
        // 存储模型key，用于tooltip查找
        modelKey: modelKey
      })

      legendData.push(modelInfo.modelName)
      colorIndex++
    })

    // 确保日期数据有效
    if (!dates || dates.length === 0) {
      logger.debug('Token趋势数据日期为空')
      return {
        title: { text: '数据错误', left: 'center', top: 'middle' },
        grid: { left: '3%', right: '4%', bottom: '25%', containLabel: true }
      }
    }

    // 调试日志
    logger.debug('Token趋势图表配置（按模型分组）', {
      timeRange: currentTimeRange,
      rangeDays: rangeDays,
      modelCount: modelDataMap.size,
      datesLength: dates.length,
      firstDate: dates[0],
      lastDate: dates[dates.length - 1]
    })

    // 创建tooltip formatter，将数据映射关系存储在闭包中
    const tooltipFormatter = ((modelDataMapRef, datesRef) => {
      return (params) => {
        try {
          if (!params || params.length === 0) return ''

          const dateIndex = params[0].dataIndex
          if (dateIndex === undefined || dateIndex === null || dateIndex < 0 || dateIndex >= datesRef.length) {
            return ''
          }

          const date = datesRef[dateIndex]
          if (!date) return ''

          let result = `<div style="margin-bottom: 8px;"><strong>${date}</strong></div>`

          params.forEach(param => {
            try {
              const modelName = param.seriesName
              // 从series中获取modelKey
              const modelKey = param.series?.modelKey
              if (modelKey && modelDataMapRef.has(modelKey)) {
                const modelInfo = modelDataMapRef.get(modelKey)
                if (modelInfo && modelInfo.data) {
                  const item = modelInfo.data.get(date)
                  if (item) {
                    result += `<div style="margin: 4px 0;">
                      <span style="display:inline-block;width:10px;height:10px;background-color:${param.color || '#409eff'};border-radius:50%;margin-right:8px;"></span>
                      <strong>${modelName || '未知模型'}</strong><br/>
                      &nbsp;&nbsp;总Token数: ${(item.totalTokens || 0).toLocaleString()}<br/>
                      &nbsp;&nbsp;Prompt Tokens: ${(item.promptTokens || 0).toLocaleString()}<br/>
                      &nbsp;&nbsp;Completion Tokens: ${(item.completionTokens || 0).toLocaleString()}
                    </div>`
                  }
                }
              }
            } catch (e) {
              logger.debug('Tooltip formatter error for param:', e)
            }
          })

          return result
        } catch (e) {
          logger.error('Tooltip formatter error:', e)
          return ''
        }
      }
    })(modelDataMap, dates)

    return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        label: {
          backgroundColor: '#6a7985'
        }
      },
      formatter: tooltipFormatter,
      hideDelay: 100, // 失去焦点后延迟100ms隐藏
      enterable: false, // 不允许鼠标进入tooltip
      confine: true // 将tooltip限制在图表区域内
    },
    legend: {
      data: legendData,
      bottom: '8%',
      type: 'scroll'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '40%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLabel: {
        rotate: 45,
        margin: 15,
        interval: getXAxisInterval(rangeDays),
        showMaxLabel: true,
        showMinLabel: true,
        formatter: function(value) {
          // 如果日期是完整格式，只显示月-日
          if (value && typeof value === 'string' && value.length >= 10) {
            return value.substring(5, 10) // 显示 MM-DD
          }
          return value
        }
      },
      axisTick: {
        alignWithLabel: true,
        show: true,
        interval: 0
      },
      scale: false // 确保X轴不自动缩放
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: '{value}'
      }
    },
    series: series
  }
  } catch (error) {
    logger.error('生成Token趋势图表配置失败:', error)
    return {
      title: { text: '图表配置错误', left: 'center', top: 'middle' },
      grid: { left: '3%', right: '4%', bottom: '25%', containLabel: true }
    }
  }
})

// 时间范围变化处理
const handleTimeRangeChange = (value) => {
  // 时间范围改变时，重新加载数据
  logger.debug('时间范围改变为:', value, '天')
  loadAllStatistics()
}

// Tab切换处理
const handleTabChange = (tabName) => {
  logger.debug('切换到Tab:', tabName)
  // Tab切换后，等待DOM更新完成，然后强制更新图表
  nextTick(() => {
    setTimeout(() => {
      chartUpdateKey.value++
    }, 100) // 延迟100ms确保DOM完全渲染
  })
}

// 柱状图Tab切换处理
const handleBarTabChange = (tabName) => {
  logger.debug('切换到柱状图Tab:', tabName)
  // Tab切换后，等待DOM更新完成，然后强制更新图表
  nextTick(() => {
    setTimeout(() => {
      chartUpdateKey.value++
    }, 100) // 延迟100ms确保DOM完全渲染
  })
}

// 获取趋势摘要数据
const getTrendSummary = (type) => {
  const range = parseInt(timeRange.value)
  let data = []

  switch (type) {
    case 'users':
      data = users.value?.registrationTrend || []
      break
    case 'conversations':
      data = chatHistory.value?.dailyStatistics || []
      return data.slice(-range).reduce((sum, item) => sum + (item.conversationCount || 0), 0)
    case 'messages':
      data = chatHistory.value?.dailyStatistics || []
      return data.slice(-range).reduce((sum, item) => sum + (item.messageCount || 0), 0)
    case 'tokens':
      data = modelTokens.value?.tokenTrend || []
      return data.slice(-range).reduce((sum, item) => sum + (item.totalTokens || 0), 0)
    default:
      return 0
  }

  return data.slice(-range).reduce((sum, item) => sum + (item.count || 0), 0)
}

// 获取趋势方向
const getTrendDirection = (type) => {
  const range = parseInt(timeRange.value)
  let data = []

  switch (type) {
    case 'users':
      data = users.value?.registrationTrend || []
      break
    case 'conversations':
      data = chatHistory.value?.dailyStatistics || []
      if (data.length < 2) return 'stable'
      const convData = data.slice(-range)
      const convFirst = convData.slice(0, Math.floor(convData.length / 2)).reduce((sum, item) => sum + (item.conversationCount || 0), 0)
      const convSecond = convData.slice(Math.floor(convData.length / 2)).reduce((sum, item) => sum + (item.conversationCount || 0), 0)
      return convSecond > convFirst ? 'up' : convSecond < convFirst ? 'down' : 'stable'
    case 'messages':
      data = chatHistory.value?.dailyStatistics || []
      if (data.length < 2) return 'stable'
      const msgData = data.slice(-range)
      const msgFirst = msgData.slice(0, Math.floor(msgData.length / 2)).reduce((sum, item) => sum + (item.messageCount || 0), 0)
      const msgSecond = msgData.slice(Math.floor(msgData.length / 2)).reduce((sum, item) => sum + (item.messageCount || 0), 0)
      return msgSecond > msgFirst ? 'up' : msgSecond < msgFirst ? 'down' : 'stable'
    case 'tokens':
      data = modelTokens.value?.tokenTrend || []
      if (data.length < 2) return 'stable'
      const tokenData = data.slice(-range)
      const tokenFirst = tokenData.slice(0, Math.floor(tokenData.length / 2)).reduce((sum, item) => sum + (item.totalTokens || 0), 0)
      const tokenSecond = tokenData.slice(Math.floor(tokenData.length / 2)).reduce((sum, item) => sum + (item.totalTokens || 0), 0)
      return tokenSecond > tokenFirst ? 'up' : tokenSecond < tokenFirst ? 'down' : 'stable'
    default:
      return 'stable'
  }

  if (data.length < 2) return 'stable'
  const sliced = data.slice(-range)
  const first = sliced.slice(0, Math.floor(sliced.length / 2)).reduce((sum, item) => sum + (item.count || 0), 0)
  const second = sliced.slice(Math.floor(sliced.length / 2)).reduce((sum, item) => sum + (item.count || 0), 0)
  return second > first ? 'up' : second < first ? 'down' : 'stable'
}

// 获取趋势百分比
const getTrendPercent = (type) => {
  const range = parseInt(timeRange.value)
  let data = []

  switch (type) {
    case 'users':
      data = users.value?.registrationTrend || []
      break
    case 'conversations':
      data = chatHistory.value?.dailyStatistics || []
      if (data.length < 2) return '0%'
      const convData = data.slice(-range)
      const convFirst = convData.slice(0, Math.floor(convData.length / 2)).reduce((sum, item) => sum + (item.conversationCount || 0), 0)
      const convSecond = convData.slice(Math.floor(convData.length / 2)).reduce((sum, item) => sum + (item.conversationCount || 0), 0)
      if (convFirst === 0) return convSecond > 0 ? '100%' : '0%'
      const convPercent = ((convSecond - convFirst) / convFirst * 100).toFixed(1)
      return `${convPercent > 0 ? '+' : ''}${convPercent}%`
    case 'messages':
      data = chatHistory.value?.dailyStatistics || []
      if (data.length < 2) return '0%'
      const msgData = data.slice(-range)
      const msgFirst = msgData.slice(0, Math.floor(msgData.length / 2)).reduce((sum, item) => sum + (item.messageCount || 0), 0)
      const msgSecond = msgData.slice(Math.floor(msgData.length / 2)).reduce((sum, item) => sum + (item.messageCount || 0), 0)
      if (msgFirst === 0) return msgSecond > 0 ? '100%' : '0%'
      const msgPercent = ((msgSecond - msgFirst) / msgFirst * 100).toFixed(1)
      return `${msgPercent > 0 ? '+' : ''}${msgPercent}%`
    case 'tokens':
      data = modelTokens.value?.tokenTrend || []
      if (data.length < 2) return '0%'
      const tokenData = data.slice(-range)
      const tokenFirst = tokenData.slice(0, Math.floor(tokenData.length / 2)).reduce((sum, item) => sum + (item.totalTokens || 0), 0)
      const tokenSecond = tokenData.slice(Math.floor(tokenData.length / 2)).reduce((sum, item) => sum + (item.totalTokens || 0), 0)
      if (tokenFirst === 0) return tokenSecond > 0 ? '100%' : '0%'
      const tokenPercent = ((tokenSecond - tokenFirst) / tokenFirst * 100).toFixed(1)
      return `${tokenPercent > 0 ? '+' : ''}${tokenPercent}%`
    default:
      return '0%'
  }

  if (data.length < 2) return '0%'
  const sliced = data.slice(-range)
  const first = sliced.slice(0, Math.floor(sliced.length / 2)).reduce((sum, item) => sum + (item.count || 0), 0)
  const second = sliced.slice(Math.floor(sliced.length / 2)).reduce((sum, item) => sum + (item.count || 0), 0)
  if (first === 0) return second > 0 ? '100%' : '0%'
  const percent = ((second - first) / first * 100).toFixed(1)
  return `${percent > 0 ? '+' : ''}${percent}%`
}

// 获取趋势样式类
const getTrendClass = (type) => {
  const direction = getTrendDirection(type)
  return {
    'trend-up': direction === 'up',
    'trend-down': direction === 'down',
    'trend-stable': direction === 'stable'
  }
}

// 格式化Token数量
const formatTokenCount = (count) => {
  if (!count || count === 0) return '0'
  if (count < 1000) return count.toString()
  if (count < 1000000) return (count / 1000).toFixed(1) + 'K'
  if (count < 1000000000) return (count / 1000000).toFixed(1) + 'M'
  return (count / 1000000000).toFixed(1) + 'B'
}

// 监听数据变化，确保图表在数据就绪后更新
watch(
  [() => users.value?.registrationTrend, () => chatHistory.value?.dailyStatistics, () => modelTokens.value?.tokenTrend, timeRange],
  () => {
    // 当数据或时间范围变化时，强制更新图表
    nextTick(() => {
      chartUpdateKey.value++
    })
  },
  { deep: true }
)

onMounted(() => {
  loadAllStatistics()
})
</script>

<style src="./Statistics.css"></style>
