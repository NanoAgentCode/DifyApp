<template>
  <div class="app-page-header">
    <div class="header-left">
      <AppIcon v-if="icon" :icon="icon" :size="32" class="app-icon" />
      <h3>{{ title }}</h3>
      <span v-if="subtitle" class="header-subtitle">{{ subtitle }}</span>
    </div>
    <div class="header-right">
      <slot name="actions">
        <el-button @click="handleBack">返回</el-button>
      </slot>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  subtitle: {
    type: String,
    default: ''
  },
  icon: {
    type: String,
    default: ''
  },
  backPath: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['back'])

const router = useRouter()

const handleBack = () => {
  emit('back')
  if (props.backPath) {
    router.push(props.backPath)
  }
}
</script>

<style scoped>
.app-page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left .app-icon {
  flex-shrink: 0;
}

.header-left h3 {
  margin: 0;
}

.header-subtitle {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
  margin-left: 8px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
