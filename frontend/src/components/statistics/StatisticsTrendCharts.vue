<template>
        <!-- 时间曲线统计 -->
        <el-card class="chart-card" shadow="hover">
          <template #header>
            <div class="time-trend-header">
              <span>时间曲线统计</span>
              <div class="header-controls">
                <el-radio-group v-model="timeRangeModel" size="small" @change="handleTimeRangeChange">
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
          <el-tabs v-model="activeTrendTabModel" type="card" class="trend-tabs" @tab-change="handleTabChange">
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
</template>
<script setup>
import { computed } from 'vue'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
const props=defineProps({ timeRange:String, activeTrendTab:String, users:Object, chatHistory:Object, modelTokens:Object, chartUpdateKey:Number, userRegistrationTrendOption:Object, chatTrendChartOption:Object, tokenTrendChartOption:Object, getTrendSummary:Function, getTrendClass:Function, getTrendDirection:Function, getTrendPercent:Function, formatTokenCount:Function })
const emit=defineEmits(['update:timeRange','update:activeTrendTab','time-range-change','tab-change'])
const timeRangeModel=computed({get:()=>props.timeRange,set:value=>emit('update:timeRange',value)})
const activeTrendTabModel=computed({get:()=>props.activeTrendTab,set:value=>emit('update:activeTrendTab',value)})
const handleTimeRangeChange=value=>emit('time-range-change',value)
const handleTabChange=tab=>emit('tab-change',tab)
</script>
