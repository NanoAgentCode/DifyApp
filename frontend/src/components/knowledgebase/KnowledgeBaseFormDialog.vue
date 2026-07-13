<template>
    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="visibleModel"
      :title="dialogTitle"
      width="700px"
      :close-on-click-modal="false"
      :lock-scroll="true"
      @close="$emit('close')"
    >
      <el-form
        ref="innerFormRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
        label-position="right"
      >
        <el-form-item label="知识库名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            placeholder="请输入知识库描述"
          />
        </el-form-item>
        <el-form-item label="可见性" prop="isPublic" v-if="isAdmin">
          <el-radio-group v-model="formData.isPublic" class="visibility-radio-group">
            <el-radio :label="true" class="visibility-radio-item">
              <div class="radio-content">
                <span class="radio-label">公开</span>
                <span class="radio-description">所有用户都可以访问</span>
              </div>
            </el-radio>
            <el-radio :label="false" class="visibility-radio-item">
              <div class="radio-content">
                <span class="radio-label">私有</span>
                <span class="radio-description">只有创建者可以访问</span>
              </div>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-else>
          <el-alert
            type="info"
            :closable="false"
            show-icon
          >
            <template #title>
              <span>普通用户只能创建私有知识库</span>
            </template>
          </el-alert>
        </el-form-item>
        <el-form-item prop="embeddingModelId">
          <template #label>
            <span>向量化模型</span>
            <el-tooltip
              v-if="!(isEdit && hasDocuments)"
              content="用于文档向量化的模型，如果不选择则使用系统默认向量化模型"
              placement="top"
            >
              <el-icon style="margin-left: 4px; color: #909399; cursor: help;">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </template>
          <el-select
            v-model="formData.embeddingModelId"
            :placeholder="isEdit && hasDocuments ? (getEmbeddingModelName(formData.embeddingModelId) || '默认模型') : '选择向量化模型（不选择则使用默认模型）'"
            clearable
            style="width: 100%"
            :disabled="isEdit && hasDocuments"
          >
            <el-option
              v-for="model in embeddingModels"
              :key="model.id"
              :label="model.name"
              :value="model.id"
            >
              <div class="kb-embedding-option-row">
                <el-tag size="small" effect="plain" class="kb-embedding-option-tag kb-embedding-model-tag" :style="getModelPlainStyle(model.id)">
                  {{ model.name }}
                </el-tag>
                <el-tag v-if="model.isDefault" type="primary" size="small" class="kb-embedding-default-tag">
                  默认
                </el-tag>
              </div>
            </el-option>
          </el-select>
          <div v-if="isEdit && hasDocuments" class="form-item-hint form-item-hint-warning">
            <el-icon><Warning /></el-icon>
            <span>当前使用：<strong>{{ getEmbeddingModelName(formData.embeddingModelId) || '默认模型' }}</strong>。已有文档，无法修改。</span>
          </div>
        </el-form-item>
        <el-form-item prop="topK">
          <template #label>
            <span>Top-K检索数量</span>
            <el-tooltip
              content="检索时返回的最相关文档片段数量（1-50），如果不设置则使用系统全局配置"
              placement="top"
            >
              <el-icon style="margin-left: 4px; color: #909399; cursor: help;">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </template>
          <el-input-number
            v-model="formData.topK"
            :min="1"
            :max="50"
            :step="1"
            placeholder="不设置则使用全局配置"
            style="width: 100%"
            clearable
          />
        </el-form-item>
        <el-form-item prop="vectorStoreType">
          <template #label>
            <span>向量存储</span>
            <el-tooltip
              v-if="!formData.vectorStoreType"
              content="请选择向量存储实例。将显示所有启用的向量库配置。"
              placement="top"
            >
              <el-icon style="margin-left: 4px; color: #909399; cursor: help;">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </template>
          <el-select
            v-model="formData.vectorStoreType"
            placeholder="选择向量存储实例"
            clearable
            style="width: 100%"
            :disabled="isEdit && hasDocuments"
            filterable
            popper-class="vector-store-select-dropdown"
          >
            <el-option
              v-for="db in creatableVectorDatabases"
              :key="db.id"
              :label="db.name"
              :value="`${db.type}_${db.id}`"
            >
                <div style="display: flex; flex-direction: column; gap: 6px; width: 100%; padding: 4px 0;">
                  <!-- 第一行：实例名称和标签 -->
                  <div style="display: flex; justify-content: space-between; align-items: center; gap: 8px;">
                    <div style="display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0;">
                      <span style="font-weight: 600; font-size: 14px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ db.name }}</span>
                      <el-tag v-if="db.isDefault" type="primary" size="small" style="flex-shrink: 0;">默认</el-tag>
                      <span style="font-size: 12px; color: #909399; flex-shrink: 0;">{{ getVectorDatabaseDocumentCount(db) }} 个文档</span>
                    </div>
                    <el-tag :type="getVectorStoreTypeTag(db.type)" size="small" style="flex-shrink: 0;">
                      {{ getVectorStoreTypeName(db.type) }}
                    </el-tag>
                  </div>
                  <!-- 第二行：URL地址 -->
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <el-icon style="font-size: 12px; color: #909399;"><Link /></el-icon>
                    <span style="font-size: 12px; color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1;">{{ db.url || '未配置URL' }}</span>
                  </div>
                </div>
            </el-option>
          </el-select>
          <div v-if="isEdit && hasDocuments" class="form-item-hint form-item-hint-warning">
            <el-icon><Warning /></el-icon>
            <span>当前使用：<strong>{{ getVectorStoreTypeNameFromValue(formData.vectorStoreType) }}</strong>。已有文档，无法修改。</span>
          </div>
          <div v-else-if="formData.vectorStoreType" class="form-item-hint form-item-description">
            <span class="description-label">{{ getVectorStoreTypeNameFromValue(formData.vectorStoreType) }}：</span>
            <span class="description-text">{{ getVectorStoreTypeDescriptionFromValue(formData.vectorStoreType) }}</span>
          </div>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio label="active">启用</el-radio>
            <el-radio label="inactive">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="$emit('submit')">确定</el-button>
      </template>
    </el-dialog>

</template>
<script setup>
import { computed, ref } from 'vue'
import { QuestionFilled, Warning, Link } from '@element-plus/icons-vue'
const props = defineProps({ visible: Boolean, dialogTitle: String, formData: Object, formRules: Object, isAdmin: Boolean, isEdit: Boolean, hasDocuments: Boolean, embeddingModels: Array, creatableVectorDatabases: Array, getEmbeddingModelName: Function, getModelPlainStyle: Function, getVectorDatabaseDocumentCount: Function, getVectorStoreTypeTag: Function, getVectorStoreTypeName: Function, getVectorStoreTypeNameFromValue: Function, getVectorStoreTypeDescriptionFromValue: Function })
const emit = defineEmits(['update:visible', 'close', 'submit'])
const innerFormRef = ref(null)
const visibleModel = computed({ get: () => props.visible, set: value => emit('update:visible', value) })
defineExpose({ validate: callback => innerFormRef.value?.validate(callback), resetFields: () => innerFormRef.value?.resetFields() })
</script>
