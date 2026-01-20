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

        <!-- 饼状图统计 -->
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <span>饼状图统计</span>
          </template>
          <div class="chart-container chart-container-pie">
            <div class="chart-item">
              <h3>用户角色分布</h3>
              <v-chart v-if="users?.roleDistribution && Object.keys(users.roleDistribution).length > 0" :option="userRoleChartOption" style="height: 220px" />
              <el-empty v-else description="暂无数据" :image-size="80" />
            </div>
            <div class="chart-item">
              <h3>用户状态分布</h3>
              <v-chart v-if="users?.statusDistribution && Object.keys(users.statusDistribution).length > 0" :option="userStatusChartOption" style="height: 220px" />
              <el-empty v-else description="暂无数据" :image-size="80" />
            </div>
            <div class="chart-item">
              <h3>应用类型分布</h3>
              <v-chart v-if="apps?.typeDistribution && Object.keys(apps.typeDistribution).length > 0" :option="appTypeChartOption" style="height: 220px" />
              <el-empty v-else description="暂无数据" :image-size="80" />
            </div>
            <div class="chart-item">
              <h3>知识库状态分布</h3>
              <v-chart v-if="knowledgeBases?.statusDistribution && Object.keys(knowledgeBases.statusDistribution).length > 0" :option="kbStatusChartOption" style="height: 220px" />
              <el-empty v-else description="暂无数据" :image-size="80" />
            </div>
            <div class="chart-item">
              <h3>对话类型分布</h3>
              <v-chart v-if="chatHistory?.typeDistribution && Object.keys(chatHistory.typeDistribution).length > 0" :option="chatTypeChartOption" style="height: 220px" />
              <el-empty v-else description="暂无数据" :image-size="80" />
            </div>
            <div v-if="modelTokens?.modelDistribution && Object.keys(modelTokens.modelDistribution).length > 0" class="chart-item">
              <h3>模型使用占比（按使用次数）</h3>
              <v-chart :option="modelDistributionChartOption" style="height: 220px" />
            </div>
          </div>
        </el-card>

        <!-- 柱状图统计 -->
        <el-card class="chart-card bar-chart-card" shadow="hover">
          <template #header>
            <span>柱状图统计</span>
          </template>
          <el-tabs v-model="activeBarTab" type="card" class="trend-tabs" @tab-change="handleBarTabChange">
            <el-tab-pane label="应用统计" name="apps" :lazy="true">
              <div class="chart-item full-width bar-chart-item">
                <div class="chart-title-bar">
                  <h3>应用使用情况（Top 10）</h3>
                </div>
                <v-chart 
                  v-if="activeBarTab === 'apps' && apps?.appUsage && apps.appUsage.length > 0" 
                  :key="`app-usage-${chartUpdateKey}`"
                  :option="appUsageChartOption" 
                  class="bar-chart"
                  autoresize
                />
                <el-empty v-else description="暂无数据" :image-size="80" />
              </div>
            </el-tab-pane>
            
            <el-tab-pane label="知识库统计" name="knowledgeBases" :lazy="true">
              <div class="chart-item full-width bar-chart-item">
                <div class="chart-title-bar">
                  <h3>知识库使用情况（Top 10）</h3>
                </div>
                <v-chart 
                  v-if="activeBarTab === 'knowledgeBases' && knowledgeBases?.kbUsage && knowledgeBases.kbUsage.length > 0" 
                  :key="`kb-usage-${chartUpdateKey}`"
                  :option="kbUsageChartOption" 
                  class="bar-chart"
                  autoresize
                />
                <el-empty v-else description="暂无数据" :image-size="80" />
              </div>
            </el-tab-pane>
            
            <el-tab-pane label="用户排行" name="users" :lazy="true">
              <div class="chart-item full-width bar-chart-item">
                <div class="chart-title-bar">
                  <h3>用户对话排行（Top 10）</h3>
                </div>
                <v-chart 
                  v-if="activeBarTab === 'users' && chatHistory?.userConversationRanks && chatHistory.userConversationRanks.length > 0" 
                  :key="`user-rank-${chartUpdateKey}`"
                  :option="userConversationRankChartOption" 
                  class="bar-chart"
                  autoresize
                />
                <el-empty v-else-if="activeBarTab === 'users'" description="暂无数据" :image-size="80" />
              </div>
            </el-tab-pane>
            
            <el-tab-pane label="模型Token统计" name="modelTokens" :lazy="true">
              <div class="chart-item full-width bar-chart-item">
                <div class="chart-title-bar">
                  <h3>各模型Token使用量</h3>
                </div>
                <v-chart 
                  v-if="activeBarTab === 'modelTokens' && modelTokens?.modelTokenUsage && modelTokens.modelTokenUsage.length > 0" 
                  :key="`model-token-${chartUpdateKey}`"
                  :option="modelTokenUsageChartOption" 
                  class="bar-chart"
                  autoresize
                />
                <el-empty v-else-if="activeBarTab === 'modelTokens'" description="暂无数据" :image-size="80" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <!-- 时间曲线统计 -->
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <div class="time-trend-header">
              <span>时间曲线统计</span>
              <div class="header-controls">
                <el-radio-group v-model="timeRange" size="small" @change="handleTimeRangeChange">
                  <el-radio-button label="7">最近7天</el-radio-button>
                  <el-radio-button label="30">最近30天</el-radio-button>
                  <el-radio-button label="90">最近90天</el-radio-button>
                </el-radio-group>
              </div>
            </div>
          </template>
          
          <!-- 统计摘要卡片 -->
          <div class="trend-summary-cards">
            <el-card class="summary-card" shadow="hover">
              <div class="summary-item">
                <div class="summary-label">新增用户</div>
                <div class="summary-value">{{ getTrendSummary('users') }}</div>
                <div class="summary-trend" :class="getTrendClass('users')">
                  <el-icon><ArrowUp v-if="getTrendDirection('users') === 'up'" /><ArrowDown v-else /></el-icon>
                  <span>{{ getTrendPercent('users') }}</span>
                </div>
              </div>
            </el-card>
            <el-card class="summary-card" shadow="hover">
              <div class="summary-item">
                <div class="summary-label">新增会话</div>
                <div class="summary-value">{{ getTrendSummary('conversations') }}</div>
                <div class="summary-trend" :class="getTrendClass('conversations')">
                  <el-icon><ArrowUp v-if="getTrendDirection('conversations') === 'up'" /><ArrowDown v-else /></el-icon>
                  <span>{{ getTrendPercent('conversations') }}</span>
                </div>
              </div>
            </el-card>
            <el-card class="summary-card" shadow="hover">
              <div class="summary-item">
                <div class="summary-label">新增消息</div>
                <div class="summary-value">{{ getTrendSummary('messages') }}</div>
                <div class="summary-trend" :class="getTrendClass('messages')">
                  <el-icon><ArrowUp v-if="getTrendDirection('messages') === 'up'" /><ArrowDown v-else /></el-icon>
                  <span>{{ getTrendPercent('messages') }}</span>
                </div>
              </div>
            </el-card>
            <el-card class="summary-card" shadow="hover">
              <div class="summary-item">
                <div class="summary-label">Token使用</div>
                <div class="summary-value">{{ formatTokenCount(getTrendSummary('tokens')) }}</div>
                <div class="summary-trend" :class="getTrendClass('tokens')">
                  <el-icon><ArrowUp v-if="getTrendDirection('tokens') === 'up'" /><ArrowDown v-else /></el-icon>
                  <span>{{ getTrendPercent('tokens') }}</span>
                </div>
              </div>
            </el-card>
          </div>

          <!-- 趋势图表 -->
          <el-tabs v-model="activeTrendTab" type="card" class="trend-tabs" @tab-change="handleTabChange">
            <el-tab-pane label="用户趋势" name="users" :lazy="true">
              <div class="chart-item full-width">
                <div class="chart-title-bar">
                  <h3>用户注册趋势</h3>
                  <el-tag type="info" size="small">{{ timeRange }}天数据</el-tag>
                </div>
                <v-chart 
                  v-if="activeTrendTab === 'users' && users?.registrationTrend && users.registrationTrend.length > 0" 
                  :key="`user-trend-${timeRange}-${chartUpdateKey}`"
                  :option="userRegistrationTrendOption" 
                  style="height: 350px" 
                  autoresize
                />
                <el-empty v-else description="暂无数据" :image-size="80" />
              </div>
            </el-tab-pane>
            
            <el-tab-pane label="会话趋势" name="chat" :lazy="true">
              <div class="chart-item full-width">
                <div class="chart-title-bar">
                  <h3>会话与消息趋势</h3>
                  <el-tag type="info" size="small">{{ timeRange }}天数据</el-tag>
                </div>
                <v-chart 
                  v-if="activeTrendTab === 'chat' && chatHistory?.dailyStatistics && chatHistory.dailyStatistics.length > 0" 
                  :key="`chat-trend-${timeRange}-${chartUpdateKey}`"
                  :option="chatTrendChartOption" 
                  style="height: 350px" 
                  autoresize
                />
                <el-empty v-else description="暂无数据" :image-size="80" />
              </div>
            </el-tab-pane>
            
            <el-tab-pane label="Token趋势" name="tokens" :lazy="true">
              <div class="chart-item full-width">
                <div class="chart-title-bar">
                  <h3>Token使用趋势</h3>
                  <el-tag type="info" size="small">{{ timeRange }}天数据</el-tag>
                </div>
                <v-chart 
                  v-if="activeTrendTab === 'tokens' && modelTokens?.tokenTrend !== null && modelTokens?.tokenTrend !== undefined && modelTokens.tokenTrend.length > 0" 
                  :key="`token-trend-${timeRange}-${chartUpdateKey}`"
                  :option="tokenTrendChartOption" 
                  style="height: 350px" 
                  autoresize
                />
                <el-empty v-else description="暂无数据" :image-size="80" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { logger } from '@/utils/logger'
import { ElMessage } from 'element-plus'
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

// 时间曲线统计相关
const timeRange = ref('30')
const activeTrendTab = ref('users')
// 柱状图统计相关
const activeBarTab = ref('apps')
// 图表强制更新key，用于在数据加载完成后强制重新渲染图表
const chartUpdateKey = ref(0)

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

// 加载所有统计数据
const loadAllStatistics = async () => {
  loading.value = true
  try {
    // 根据timeRange确定天数
    const rangeDays = parseInt(timeRange.value) || 30
    logger.debug('加载统计数据，时间范围:', rangeDays, '天')
    
    // 加载主要统计数据（传递时间范围参数）
    const statsResponse = await getAllStatistics(rangeDays)
    overview.value = statsResponse.overview
    users.value = statsResponse.users
    apps.value = statsResponse.apps
    knowledgeBases.value = statsResponse.knowledgeBases
    modelTokens.value = statsResponse.modelTokens
    
    // 调试：输出统计数据
    logger.debug('用户趋势数据长度:', statsResponse.users?.registrationTrend?.length || 0)
    logger.debug('Token趋势数据长度:', statsResponse.modelTokens?.tokenTrend?.length || 0)

    // 加载会话历史统计（传递时间范围参数）
    const chatHistoryResponse = await getChatHistoryStatistics(rangeDays)
    chatHistory.value = chatHistoryResponse
    logger.debug('会话趋势数据长度:', chatHistoryResponse?.dailyStatistics?.length || 0)
    
    // 数据加载完成后，等待DOM更新，然后强制更新图表
    await nextTick()
    chartUpdateKey.value++
  } catch (error) {
    logger.error('加载统计数据失败:', error)
    ElMessage.error('加载统计数据失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

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

<style scoped>
/* ========== 页面容器 ========== */
.statistics {
  padding: var(--spacing-lg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-secondary);
}

/* ========== 卡片样式 ========== */
:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  transition: box-shadow var(--transition-base);
}

:deep(.el-card:hover) {
  box-shadow: var(--card-shadow-hover);
}

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: var(--spacing-md) var(--card-padding);
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-lighter);
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: var(--spacing-md);
  min-height: 0;
  background: var(--color-bg-primary);
}

/* ========== 卡片头部 ========== */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header span {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

/* ========== 内容区域 ========== */
.statistics-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding-bottom: var(--spacing-lg);
  box-sizing: border-box;
}

/* ========== 概览统计卡片 ========== */
.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.stat-card {
  text-align: center;
  transition: all var(--transition-base);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.stat-item {
  padding: var(--spacing-md);
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-sm);
  font-weight: var(--font-weight-medium);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
  line-height: var(--line-height-tight);
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.chart-card {
  margin-bottom: var(--spacing-lg);
  transition: all var(--transition-base);
}

.chart-card:hover {
  box-shadow: var(--card-shadow-hover);
}

/* 确保最后一个卡片有足够的底部空间 */
.chart-card:last-child {
  margin-bottom: 0;
}

/* 柱状图统计卡片特殊处理，减少底部空白 */
.bar-chart-card :deep(.el-card__body) {
  padding: 12px;
  padding-bottom: 12px;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.bar-chart-card .trend-tabs {
  margin-bottom: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.bar-chart-card .trend-tabs :deep(.el-tabs__content) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding-top: 16px;
  padding-bottom: 0;
}

.bar-chart-card .trend-tabs :deep(.el-tab-pane) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding-bottom: 0;
}

/* 选项卡切换过渡动画 */
.bar-chart-card .trend-tabs :deep(.el-tabs__content) {
  position: relative;
}

.bar-chart-card .trend-tabs :deep(.el-tab-pane) {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.bar-chart-card .trend-tabs :deep(.el-tab-pane.is-active) {
  animation: tabFadeIn 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: opacity, transform; /* 硬件加速 */
}

/* 柱状图项样式 */
.bar-chart-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px;
  padding-top: 8px;
  padding-bottom: 12px;
}

.bar-chart {
  flex: 1;
  min-height: 0;
  width: 100%;
}

.chart-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--spacing-lg);
}

/* 饼状图容器 - 使用3列布局，更整齐 */
.chart-container-pie {
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-lg);
}

.chart-item {
  background: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  border: 1px solid var(--color-border-lighter);
  transition: all var(--transition-base);
}

.chart-item:hover {
  box-shadow: var(--shadow-sm);
  border-color: var(--color-border-base);
}

.chart-item h3 {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

/* 柱状图和时间曲线容器 - 单列布局 */
.chart-container .chart-item.full-width {
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  border: 1px solid var(--color-border-light);
  transition: all var(--transition-base);
}

.chart-container .chart-item.full-width:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-border-base);
  transform: translateY(-2px);
}

.chart-container .chart-item h3 {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

@media (max-width: 1400px) {
  .chart-container-pie {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 900px) {
  .chart-container-pie {
    grid-template-columns: 1fr;
  }
}

.chart-item {
  /* 移除固定最小高度，让内容自适应图表高度 */
  display: flex;
  flex-direction: column;
  background: #fafafa;
  border-radius: 6px;
  padding: 16px;
  border: 1px solid #e4e7ed;
  transition: all 0.3s;
}

.chart-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-color: #c0c4cc;
}

.chart-item.full-width {
  grid-column: 1 / -1;
  padding-bottom: 12px;
  padding-top: 8px;
}

.chart-item h3 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  text-align: center;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.empty-tip {
  grid-column: 1 / -1;
  padding: 40px;
  text-align: center;
}

/* 时间曲线统计样式 */
.time-trend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.trend-summary-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.summary-card {
  text-align: center;
}

.summary-item {
  padding: 12px;
}

.summary-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.summary-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.summary-trend {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
}

.summary-trend.trend-up {
  color: #67c23a;
}

.summary-trend.trend-down {
  color: #f56c6c;
}

.summary-trend.trend-stable {
  color: #909399;
}

.trend-tabs {
  margin-top: 16px;
}

.trend-tabs :deep(.el-tabs__content) {
  padding-top: 16px;
  padding-bottom: 0;
}

.trend-tabs :deep(.el-tab-pane) {
  padding-bottom: 0;
}

/* 选项卡切换过渡动画 */
.trend-tabs :deep(.el-tabs__content) {
  position: relative;
}

.trend-tabs :deep(.el-tab-pane) {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.trend-tabs :deep(.el-tab-pane.is-active) {
  animation: tabFadeIn 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: opacity, transform; /* 硬件加速 */
}

@keyframes tabFadeIn {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.chart-title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #e4e7ed;
}

.chart-title-bar h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

@media (max-width: 1200px) {
  .trend-summary-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .trend-summary-cards {
    grid-template-columns: 1fr;
  }
  
  .time-trend-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>

