<template>
  <div class="app-icon">
    <!-- 内置图标 -->
    <el-icon v-if="iconType === 'builtin'" :size="size" :style="{ color: color }">
      <component :is="iconComponent" />
    </el-icon>
    <!-- 自定义图标URL -->
    <img 
      v-else-if="iconType === 'url'" 
      :src="iconUrl" 
      :alt="alt || '应用图标'"
      :style="{ width: size + 'px', height: size + 'px', objectFit: 'contain' }"
      @error="handleError"
    />
    <!-- 无图标 -->
    <el-icon v-else :size="size" :style="{ color: color || '#909399' }">
      <Document />
    </el-icon>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Document } from '@element-plus/icons-vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { parseIconValue, getIconById } from '@/utils/icons'

const props = defineProps({
  icon: {
    type: String,
    default: ''
  },
  size: {
    type: [Number, String],
    default: 24
  },
  color: {
    type: String,
    default: '#409eff'
  },
  alt: {
    type: String,
    default: '应用图标'
  }
})

// 解析图标值
const iconInfo = computed(() => {
  return parseIconValue(props.icon)
})

// 图标类型
const iconType = computed(() => {
  return iconInfo.value.type
})

// 图标URL
const iconUrl = computed(() => {
  return iconInfo.value.icon
})

// 图标组件
const iconComponent = computed(() => {
  if (iconType.value === 'builtin' && iconInfo.value.icon) {
    return ElementPlusIconsVue[iconInfo.value.icon] || Document
  }
  return Document
})

// 图标加载错误处理
const handleError = () => {
  // 可以在这里添加错误处理逻辑
  console.warn('图标加载失败:', iconUrl.value)
}
</script>

<style scoped>
.app-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.app-icon img {
  display: block;
}
</style>

