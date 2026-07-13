<template>
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

</template>
<script setup>
defineProps({ overview: Object })
</script>
