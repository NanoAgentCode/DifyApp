<template>
        <!-- 柱状图统计 -->
        <el-card class="chart-card bar-chart-card" shadow="hover">
          <template #header>
            <span>柱状图统计</span>
          </template>
          <el-tabs v-model="activeBarTabModel" type="card" class="trend-tabs" @tab-change="handleBarTabChange">
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

</template>
<script setup>
import { computed } from 'vue'
const props=defineProps({ activeBarTab:String, apps:Object, knowledgeBases:Object, chatHistory:Object, modelTokens:Object, chartUpdateKey:Number, appUsageChartOption:Object, kbUsageChartOption:Object, userConversationRankChartOption:Object, modelTokenUsageChartOption:Object })
const emit=defineEmits(['update:activeBarTab','tab-change'])
const activeBarTabModel=computed({get:()=>props.activeBarTab,set:value=>emit('update:activeBarTab',value)})
const handleBarTabChange=tab=>emit('tab-change',tab)
</script>
