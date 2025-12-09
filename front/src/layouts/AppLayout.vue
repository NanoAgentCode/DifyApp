<template>
  <el-container class="app-layout">
    <el-main class="main">
      <router-view />
    </el-main>
    
    <!-- 帮助悬浮按钮 -->
    <HelpFloatingButton @click="showHelpDialog = true" />
    
    <!-- 帮助对话框 -->
    <HelpDialog 
      v-model="showHelpDialog" 
      :knowledge-base-id="helpKnowledgeBaseId"
      :model-id="helpModelId"
    />
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import HelpFloatingButton from '@/components/HelpFloatingButton.vue'
import HelpDialog from '@/components/HelpDialog.vue'

const showHelpDialog = ref(false)
const helpKnowledgeBaseId = ref(null)
const helpModelId = ref(null)

// 从本地存储读取知识库配置
onMounted(() => {
  const savedKBId = localStorage.getItem('helpKnowledgeBaseId')
  if (savedKBId) {
    helpKnowledgeBaseId.value = parseInt(savedKBId)
  }
  
  // 从本地存储读取模型配置（由管理端配置）
  const savedModelId = localStorage.getItem('helpModelId')
  if (savedModelId) {
    helpModelId.value = parseInt(savedModelId)
  }
})
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

