<template>
  <div v-if="shouldShow" class="home-floating-button">
    <el-tooltip content="用户手册智能问答" placement="left" :show-after="300">
      <div class="help-button" @click="handleHelpClick">
        <el-icon :size="18"><QuestionFilled /></el-icon>
      </div>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { QuestionFilled } from '@element-plus/icons-vue'

const props = defineProps({
  onHelpClick: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['help-click'])

const route = useRoute()

// 判断是否应该显示按钮（主页不显示）
const shouldShow = computed(() => {
  const path = route.path
  return path !== '/user/chat' && path !== '/admin/chat'
})

// 用户手册智能问答
const handleHelpClick = () => {
  if (props.onHelpClick) {
    props.onHelpClick()
  } else {
    emit('help-click')
  }
}
</script>

<style scoped>
.home-floating-button {
  position: fixed;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1000;
}

.help-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  border: 2px solid var(--el-color-primary);
  background-color: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  transform: translateX(50%);
}

.home-floating-button:hover .help-button {
  transform: translateX(0);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  background-color: var(--el-color-primary);
  color: white;
}

@media (max-width: 768px) {
  .help-button {
    width: 48px;
    height: 48px;
    transform: translateX(50%);
  }
}
</style>

