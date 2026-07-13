<template>
      <!-- 图片文档 -->
      <div v-if="isImageType" class="image-container">
        <el-image
          v-if="imageUrl"
          :src="imageUrl"
          fit="contain"
          class="document-image"
          :preview-src-list="[imageUrl]"
        />
        <DocumentViewerLoading v-else />
      </div>

      <!-- Markdown文档 -->
      <div v-else-if="fileType === 'md' || fileType === 'markdown'" class="markdown-container">
        <div v-if="markdownContent" class="markdown-content" v-html="renderedMarkdown"></div>
        <div v-else class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
      </div>

      <!-- 文本文档 -->
      <div v-else-if="fileType === 'txt'" class="text-container">
        <pre v-if="textContent" class="text-content">{{ textContent }}</pre>
        <div v-else class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
      </div>

      <!-- Word文档 (docx) -->
      <div v-else-if="fileType === 'docx' || fileType === 'doc'" class="docx-container">
        <div v-if="docxContent" class="docx-content" v-html="docxContent"></div>
        <div v-else-if="loading" class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>正在转换文档...</p>
        </div>
        <div v-else class="loading-container">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
      </div>

      <!-- 其他类型文档（Excel等） -->
      <div v-else class="other-document-container">
        <div class="unsupported-message">
          <el-icon class="message-icon"><Document /></el-icon>
          <p>该文档类型暂不支持在线预览</p>
          <p class="message-tip">请下载后使用相应软件打开</p>
        </div>
      </div>
</template>
<script setup>
import { Loading, Document } from '@element-plus/icons-vue'
import DocumentViewerLoading from '../DocumentViewerLoading.vue'
defineProps({ fileType:String, isImageType:Boolean, imageUrl:String, markdownContent:String, renderedMarkdown:String, textContent:String, docxContent:String, loading:Boolean })
</script>
