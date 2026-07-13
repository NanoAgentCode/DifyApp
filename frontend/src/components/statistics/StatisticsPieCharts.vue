<template>
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

</template>
<script setup>
defineProps({ users:Object, apps:Object, knowledgeBases:Object, chatHistory:Object, modelTokens:Object, userRoleChartOption:Object, userStatusChartOption:Object, appTypeChartOption:Object, kbStatusChartOption:Object, chatTypeChartOption:Object, modelDistributionChartOption:Object })
</script>
