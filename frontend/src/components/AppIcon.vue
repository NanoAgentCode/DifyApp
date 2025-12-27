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

const iconInfo = computed(() => parseIconValue(props.icon))
const iconType = computed(() => iconInfo.value.type)
const iconUrl = computed(() => iconInfo.value.icon)
const iconComponent = computed(() => 
  (iconType.value === 'builtin' && iconInfo.value.icon) 
    ? ElementPlusIconsVue[iconInfo.value.icon] || Document 
    : Document
)

const handleError = () => {}
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

