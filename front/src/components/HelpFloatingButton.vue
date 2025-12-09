<template>
  <div class="help-floating-button" @click="handleClick">
    <el-icon :size="24"><QuestionFilled /></el-icon>
  </div>
</template>

<script setup>
import { QuestionFilled } from '@element-plus/icons-vue'

const emit = defineEmits(['click'])

const handleClick = () => {
  emit('click')
}
</script>

<style scoped>
.help-floating-button {
  position: fixed;
  bottom: 30px;
  left: 30px;
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
  transition: all 0.3s ease;
  z-index: 1000;
  color: white;
}

.help-floating-button:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.6);
}

.help-floating-button:active {
  transform: scale(0.95);
}
</style>

