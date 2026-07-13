<template>
    <!-- 查看详情对话框 -->
    <el-dialog
      v-model="visibleModel"
      title="知识库详情"
      width="700px"
      :lock-scroll="true"
    >
      <el-descriptions :column="2" border v-if="currentKB">
        <el-descriptions-item label="ID">{{ currentKB.id }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ currentKB.name }}</el-descriptions-item>
        <el-descriptions-item label="向量化模型" :span="2">
          <el-tag
            v-if="getEmbeddingModelName(currentKB.embeddingModelId)"
            size="small"
            effect="plain"
            class="kb-embedding-model-tag"
            :style="getModelPlainStyle(currentKB.embeddingModelId)"
          >
            {{ getEmbeddingModelName(currentKB.embeddingModelId) }}
          </el-tag>
          <span v-else style="color: #909399;">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="Top-K检索数量" :span="2">
          <el-tag v-if="currentKB.topK" type="info">{{ currentKB.topK }}</el-tag>
          <span v-else style="color: #909399;">使用全局配置</span>
        </el-descriptions-item>
        <el-descriptions-item label="向量存储类型" :span="2">
          <el-tag
            :type="getVectorStoreTypeTag(currentKB.vectorStoreType)"
          >
            {{ getVectorStoreTypeDisplayName(currentKB.vectorStoreType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="isActive(currentKB.status) ? 'success' : 'info'">
            {{ getStatusText(currentKB.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文档总数">{{ currentKB.documentCount || 0 }} 个</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(currentKB.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="成功文档">
          <el-tag type="success" size="small">{{ currentKB.successDocumentCount || 0 }} 个</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="失败文档">
          <el-tag type="danger" size="small">{{ currentKB.failedDocumentCount || 0 }} 个</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentKB.description || '无' }}</el-descriptions-item>
        <el-descriptions-item label="智能摘要" :span="2">
          <div v-if="currentKB.summary" style="max-width: 600px; word-wrap: break-word; line-height: 1.6;">
            {{ currentKB.summary }}
          </div>
          <div v-else style="color: #909399; font-style: italic;">
            暂无摘要
            <el-button
              type="primary"
              size="small"
              style="margin-left: 10px;"
              @click="$emit('generate-summary')"
            >
              生成摘要
            </el-button>
          </div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
        <el-button
          v-if="currentKB && !currentKB.summary"
          type="primary"
          @click="$emit('generate-summary')"
        >
          生成摘要
        </el-button>
      </template>
    </el-dialog>

</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ visible: Boolean, currentKB: Object, getEmbeddingModelName: Function, getModelPlainStyle: Function, getVectorStoreTypeTag: Function, getVectorStoreTypeDisplayName: Function, isActive: Function, getStatusText: Function, formatDate: Function })
const emit = defineEmits(['update:visible', 'generate-summary'])
const visibleModel = computed({ get: () => props.visible, set: value => emit('update:visible', value) })
</script>
