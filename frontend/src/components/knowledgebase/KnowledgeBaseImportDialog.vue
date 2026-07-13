<template>
    <!-- 导入知识库对话框 -->
    <el-dialog
      v-model="visibleModel"
      title="导入知识库"
      width="700px"
      :close-on-click-modal="false"
      :lock-scroll="true"
      class="import-dialog"
    >
      <div class="import-dialog-content">
        <!-- 文件上传区域 -->
        <div class="upload-section">
          <template v-if="importFileList.length === 0">
            <el-upload
              :auto-upload="false"
              :on-change="(file, fileList) => $emit('file-change', file, fileList)"
              :file-list="importFileList"
              accept=".zip"
              drag
              :limit="1"
              class="import-upload"
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">拖拽ZIP文件到此处或<em>点击上传</em></div>
            </el-upload>
          </template>
          <div v-else class="file-selected-card">
            <!-- 文件信息 -->
            <div class="file-info-row">
              <el-icon class="file-icon"><Document /></el-icon>
              <span class="file-name">{{ importFileList[0].name }}</span>
              <el-button
                type="primary"
                link
                size="small"
                @click="$emit('reselect-file')"
                :disabled="importing"
                class="re-select-btn"
              >
                重新选择
              </el-button>
            </div>

            <!-- 文件预览 -->
            <div v-if="previewFiles.length > 0" class="preview-section">
              <div class="preview-header">
                <span class="preview-count">共 {{ previewFiles.length }} 个文件</span>
                <el-tooltip content="导入后将自动进行向量化处理" placement="top">
                  <el-icon class="preview-tip-icon"><QuestionFilled /></el-icon>
                </el-tooltip>
              </div>
              <div class="preview-table-wrapper">
                <el-table :data="previewFiles" size="small" max-height="140">
                  <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
                  <el-table-column prop="fileType" label="类型" width="80" align="center">
                    <template #default="{ row }">
                      <span class="file-type-tag">{{ row.fileType }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="fileSize" label="大小" width="100" align="right">
                    <template #default="{ row }">
                      {{ formatFileSize(row.fileSize) }}
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </div>
        </div>

        <!-- 知识库信息（文件上传后显示） -->
        <div v-if="importFileList.length > 0" class="form-section">
          <el-form
            :model="importForm"
            :rules="importRules"
            ref="innerFormRef"
            label-width="90px"
            label-position="right"
          >
            <div class="form-section-header">
              <span class="form-section-title">知识库信息</span>
              <el-tooltip content="请确认知识库的基本信息，默认使用ZIP文件名作为知识库名称" placement="top">
                <el-icon class="form-section-icon"><InfoFilled /></el-icon>
              </el-tooltip>
            </div>

            <el-form-item label="名称" prop="name" required>
              <el-input
                v-model="importForm.name"
                placeholder="请输入知识库名称"
                :disabled="importing"
                clearable
              />
            </el-form-item>

            <el-form-item label="描述">
              <el-input
                v-model="importForm.description"
                type="textarea"
                :rows="2"
                placeholder="请输入知识库描述（可选）"
                :disabled="importing"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>

            <!-- 高级配置（折叠） -->
            <el-collapse class="advanced-config-collapse" :border="false">
              <el-collapse-item title="高级配置" name="advanced">
                <el-form-item label="向量存储类型">
                  <el-select
                    v-model="importForm.vectorStoreType"
                    placeholder="使用系统默认值"
                    :disabled="importing"
                    clearable
                    style="width: 100%"
                  >
                    <el-option label="使用默认值" value="" />
                    <el-option label="Qdrant" value="qdrant" />
                    <el-option label="FAISS" value="faiss" />
                    <el-option label="Milvus" value="milvus" />
                    <el-option label="Chroma" value="chroma" />
                    <el-option label="Weaviate" value="weaviate" />
                    <el-option label="PgVector" value="pgvector" />
                  </el-select>
                </el-form-item>
                <el-form-item label="是否公开" v-if="isAdmin">
                  <div class="switch-with-tooltip">
                    <el-switch
                      v-model="importForm.isPublic"
                      :disabled="importing"
                      active-text="公开"
                      inactive-text="私有"
                    />
                    <el-tooltip content="公开：所有用户都可以访问；私有：只有创建者可以访问" placement="top">
                      <el-icon class="switch-tooltip-icon"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </div>
                </el-form-item>
              </el-collapse-item>
            </el-collapse>
          </el-form>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="importDialogVisible = false" :disabled="importing">取消</el-button>
          <el-button
            type="primary"
            @click="$emit('confirm-import')"
            :loading="importing"
            :disabled="importFileList.length === 0 || !importForm.name"
          >
            <el-icon v-if="!importing"><UploadFilled /></el-icon>
            开始导入
          </el-button>
        </div>
      </template>
    </el-dialog>
</template>
<script setup>
import { computed, ref } from 'vue'
import { UploadFilled, Document, QuestionFilled, InfoFilled } from '@element-plus/icons-vue'
const props = defineProps({ visible: Boolean, importFileList: Array, importing: Boolean, previewFiles: Array, importForm: Object, importRules: Object, isAdmin: Boolean, formatFileSize: Function })
const emit = defineEmits(['update:visible', 'file-change', 'reselect-file', 'confirm-import'])
const innerFormRef = ref(null)
const visibleModel = computed({ get: () => props.visible, set: value => emit('update:visible', value) })
defineExpose({ validate: callback => innerFormRef.value?.validate(callback), resetFields: () => innerFormRef.value?.resetFields() })
</script>
