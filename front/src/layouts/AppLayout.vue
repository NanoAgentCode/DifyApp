<template>
  <el-container class="app-layout">
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
// 应用端布局
</script>

<style scoped>
.app-layout {
  height: 100vh;
}

.main {
  padding: 0;
  background: #f5f7fa;
}
</style>

