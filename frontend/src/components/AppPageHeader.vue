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
/* ========== 页面头部容器 ========== */
.app-page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) 0;
  min-height: 56px;
}

/* ========== 左侧区域 ========== */
.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex: 1;
  min-width: 0;
}

.header-left .app-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xs);
  transition: all var(--transition-base);
}

.header-left .app-icon:hover {
  background: var(--color-bg-hover);
  transform: scale(1.05);
}

.header-left h3 {
  margin: 0;
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-subtitle {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-normal);
  color: var(--color-text-secondary);
  margin-left: var(--spacing-sm);
  white-space: nowrap;
  padding: var(--spacing-xs) var(--spacing-sm);
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-sm);
  line-height: 1.4;
}

/* ========== 右侧区域 ========== */
.header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-shrink: 0;
}

/* ========== 响应式设计 ========== */
@media (max-width: 1024px) {
  .app-page-header {
    padding: var(--spacing-sm) 0;
    min-height: 48px;
  }

  .header-left {
    gap: var(--spacing-sm);
  }

  .header-left h3 {
    font-size: var(--font-size-lg);
  }

  .header-left .app-icon {
    width: 36px;
    height: 36px;
  }

  .header-subtitle {
    font-size: 11px;
    padding: 2px var(--spacing-xs);
  }
}

@media (max-width: 768px) {
  .app-page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-sm);
    padding: var(--spacing-sm) 0;
  }

  .header-left {
    width: 100%;
  }

  .header-left h3 {
    font-size: var(--font-size-md);
  }

  .header-subtitle {
    display: block;
    margin-left: 0;
    margin-top: var(--spacing-xs);
  }

  .header-right {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
