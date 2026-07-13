<template>
  <div class="viewer-controls">
    <div class="controls-left">
      <el-button :disabled="currentPage <= 1" size="small" @click="emit('previous')"><el-icon><ArrowLeft /></el-icon>上一页</el-button>
      <div class="page-jump-wrapper">
        <el-input-number v-model="pageInput" :min="1" :max="totalPages" :precision="0" size="small" controls-position="right" style="width: 80px" @change="emit('page-jump')" @keyup.enter="emit('page-jump')" />
        <span class="page-separator">/</span><span class="total-pages">{{ totalPages }}</span>
      </div>
      <el-button :disabled="currentPage >= totalPages" size="small" @click="emit('next')">下一页<el-icon><ArrowRight /></el-icon></el-button>
    </div>
    <div class="controls-right">
      <el-button :disabled="zoomLevel <= 50" size="small" title="缩小 (Ctrl + -)" @click="emit('zoom-out')"><el-icon><Minus /></el-icon></el-button>
      <el-button size="small" title="重置缩放 (Ctrl + 0)" @click="emit('zoom-reset')"><span class="zoom-reset-text">{{ zoomLevel }}%</span></el-button>
      <el-button :disabled="zoomLevel >= 200" size="small" title="放大 (Ctrl + +)" @click="emit('zoom-in')"><el-icon><Plus /></el-icon></el-button>
      <el-button size="small" title="全屏 (F11)" @click="emit('fullscreen')"><el-icon><FullScreen /></el-icon></el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowLeft, ArrowRight, Minus, Plus, FullScreen } from '@element-plus/icons-vue'

const props = defineProps({ currentPage: Number, totalPages: Number, zoomLevel: Number, pageInput: Number })
const emit = defineEmits(['update:pageInput', 'previous', 'next', 'page-jump', 'zoom-in', 'zoom-out', 'zoom-reset', 'fullscreen'])
const pageInput = computed({ get: () => props.pageInput, set: value => emit('update:pageInput', value) })
</script>
