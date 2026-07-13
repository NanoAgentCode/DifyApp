<template>
  <div class="loading-container">
    <el-icon class="loading-icon"><Loading /></el-icon>
    <p>{{ message }}</p>
  </div>
</template>

<script setup>
import { Loading } from '@element-plus/icons-vue'
defineProps({ message: { type: String, default: '加载中...' } })
</script>
