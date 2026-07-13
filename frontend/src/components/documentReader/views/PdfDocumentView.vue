<template>
      <!-- PDF文档 -->
      <div v-if="fileType === 'pdf'" class="pdf-container">
        <DocumentViewerLoading v-if="loading" message="正在加载PDF..." />
        <div v-else-if="pdfSource" class="pdf-viewer-wrapper" :ref="pdfViewerWrapperRef">
          <!-- 已渲染的页面（懒加载：仅渲染 visiblePageCount 个页面） -->
          <div
            v-for="pageNum in visiblePdfPageNumbers"
            :key="`pdf-${docId}-page-${pageNum}`"
            :id="`pdf-page-${pageNum}`"
            class="pdf-page-container"
          >
            <div class="pdf-page-number">第 {{ pageNum }} 页 / 共 {{ pdfTotalPages }} 页</div>
            <vue-pdf-embed
              :source="pdfSource"
              :page="pageNum"
              :textLayer="true"
              class="pdf-embed"
              :ref="pageNum === 1 ? setPdfEmbedRef : undefined"
              @rendered="(info) => handlePdfPageRendered(pageNum, info)"
              @failed="handlePdfFailed"
            />
          </div>
          <!-- 占位符：未渲染的页（保持滚动高度连续） -->
          <div
            v-if="pdfTotalPages > visiblePageCount"
            class="pdf-pages-placeholder"
            :style="{ height: placeholderHeight }"
            :ref="pdfPlaceholderRef"
          >
            <div class="placeholder-loading" v-if="loadingMorePages">
              <el-icon class="loading-icon"><Loading /></el-icon>
              <span>正在加载更多页面...</span>
            </div>
          </div>
        </div>
        <div v-else class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
      </div>
</template>
<script setup>
import { Loading } from '@element-plus/icons-vue'
import DocumentViewerLoading from '../DocumentViewerLoading.vue'
defineProps({ fileType:String, loading:Boolean, pdfSource:[String,Object], docId:Number, visiblePdfPageNumbers:Array, pdfTotalPages:Number, visiblePageCount:Number, placeholderHeight:String, loadingMorePages:Boolean, pdfViewerWrapperRef:[Function,Object], pdfPlaceholderRef:[Function,Object], setPdfEmbedRef:Function, handlePdfPageRendered:Function, handlePdfFailed:Function })
</script>
