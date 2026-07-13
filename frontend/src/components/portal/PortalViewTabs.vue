<template>
  <div class="view-tabs">
    <div
      class="tab-item"
      :class="{ active: modelValue === 'welcome', disabled: disabled }"
      role="button"
      tabindex="0"
      @click="select('welcome')"
      @keydown.enter="select('welcome')"
      @keydown.space.prevent="select('welcome')"
    >
      <el-icon><ChatLineRound /></el-icon>
      <span>智能对话</span>
    </div>
    <div
      class="tab-item"
      :class="{ active: modelValue === 'features', disabled: disabled }"
      role="button"
      tabindex="0"
      @click="select('features')"
      @keydown.enter="select('features')"
      @keydown.space.prevent="select('features')"
    >
      <el-icon><Grid /></el-icon>
      <span>快捷入口</span>
    </div>
  </div>
</template>

<script setup>
import { ChatLineRound, Grid } from '@element-plus/icons-vue'

const props = defineProps({ modelValue: String, disabled: Boolean })
const emit = defineEmits(['select'])
const select = (view) => {
  if (!props.disabled) emit('select', view)
}
</script>

<style scoped>
.view-tabs { display: flex; justify-content: center; gap: 8px; margin-bottom: 32px; }
.tab-item { display: flex; align-items: center; gap: 6px; padding: 8px 20px; border-radius: 20px; cursor: pointer; transition: all .3s ease; color: var(--el-text-color-regular, #606266); font-size: 14px; background: transparent; border: 1px solid transparent; }
.tab-item:hover { background: var(--el-fill-color-light, #f5f7fa); color: var(--el-color-primary, #409eff); }
.tab-item.active { background: var(--el-color-primary, #409eff); color: #ffffff; border-color: var(--el-color-primary, #409eff); }
.tab-item.disabled { opacity: .6; cursor: not-allowed; pointer-events: none; }
.tab-item .el-icon { font-size: 16px; }
</style>
