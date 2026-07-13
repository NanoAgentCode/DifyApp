<template>
      <!-- 主画布区域 -->
      <el-main class="canvas-panel">
        <div class="canvas-header">
          <div class="canvas-title">
            <el-icon><DataAnalysis /></el-icon>
            <span>图表编辑器</span>
          </div>
          <div class="canvas-actions">
            <el-button-group>
              <el-button size="small" @click="zoomOut" :disabled="zoomLevel <= 0.5" title="缩小">
                <el-icon><ZoomOut /></el-icon>
              </el-button>
              <el-button size="small" @click="resetZoom" title="重置缩放">
                <span style="min-width: 50px;">{{ Math.round(zoomLevel * 100) }}%</span>
              </el-button>
              <el-button size="small" @click="zoomIn" :disabled="zoomLevel >= 2" title="放大">
                <el-icon><ZoomIn /></el-icon>
              </el-button>
              <el-button size="small" @click="fitToWindow" title="适应窗口">
                <el-icon><FullScreen /></el-icon>
              </el-button>
            </el-button-group>
            <el-button v-if="!userMode" size="small" @click="handleExport">
              <el-icon><Download /></el-icon>
              导出
            </el-button>
            <el-button size="small" @click="handleImport">
              <el-icon><Upload /></el-icon>
              导入
            </el-button>
          </div>
        </div>

        <!-- AntV Infographic 图表容器 -->
        <div
          class="infographic-wrapper"
          :class="{ dragging: isDragging }"
          ref="infographicWrapper"
          @wheel="handleWheel"
          @mousedown="handleMouseDown"
          @mousemove="handleMouseMove"
          @mouseup="handleMouseUp"
          @mouseleave="handleMouseUp"
        >
          <div
            class="infographic-container"
            ref="infographicContainer"
            :style="{
              transform: `translate(${panOffset.x}px, ${panOffset.y}px) scale(${zoomLevel})`,
              transformOrigin: 'top left',
              cursor: isDragging ? 'grabbing' : 'grab'
            }"
          ></div>
        </div>
      </el-main>

</template>
<script setup>
import { DataAnalysis, Download, FullScreen, Upload, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
defineProps({ userMode:Boolean, zoomLevel:Number, zoomIn:Function, zoomOut:Function, resetZoom:Function, fitToWindow:Function, handleExport:Function, handleImport:Function, isDragging:Boolean, infographicWrapper:Object, infographicContainer:Object, handleWheel:Function, handleMouseDown:Function, handleMouseMove:Function, handleMouseUp:Function, panOffset:Object })
</script>
