<template>
  <div class="knowledge-base-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button type="text" @click="handleBack" style="margin-right: 10px">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <span>我的知识库</span>
          </div>
          <div class="header-right">
            <el-button type="success" @click="handleImport">
              <el-icon><UploadFilled /></el-icon>
              导入知识库
            </el-button>
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon>
              创建知识库
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索知识库名称或描述"
          clearable
          style="width: 300px"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="filterVectorStoreType"
          placeholder="筛选向量库"
          clearable
          style="width: 150px; margin-left: 10px"
          @change="handleFilter"
        >
          <el-option label="全部" value="" />
          <el-option label="Qdrant" value="qdrant" />
          <el-option label="FAISS" value="faiss" />
          <el-option label="Milvus" value="milvus" />
          <el-option label="Chroma" value="chroma" />
          <el-option label="Weaviate" value="weaviate" />
          <el-option label="PgVector" value="pgvector" />
        </el-select>
        <el-select
          v-model="filterStatus"
          placeholder="筛选状态"
          clearable
          style="width: 150px; margin-left: 10px"
          @change="handleFilter"
        >
          <el-option label="全部" value="" />
          <el-option label="启用" value="active" />
          <el-option label="禁用" value="inactive" />
        </el-select>
      </div>

      <!-- 知识库列表 -->
      <el-table
        :data="paginatedKnowledgeBases"
        v-loading="loading"
        stripe
        style="margin-top: 20px"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column label="知识库名称" min-width="200">
          <template #default="{ row }">
            <div class="kb-name-cell">
              <el-icon class="kb-icon"><Document /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column label="文档数量" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="info">{{ row.documentCount || 0 }} 个</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tooltip :content="getStatusText(row.status)" placement="top">
              <el-icon :size="20" :color="isActive(row.status) ? '#67c23a' : '#909399'">
                <Check v-if="isActive(row.status)" />
                <Close v-else />
              </el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="向量化模型" width="180" align="center">
          <template #default="{ row }">
            <el-tag 
              v-if="getEmbeddingModelName(row.embeddingModelId)" 
              size="small"
              :style="getModelStyle(row.embeddingModelId)"
            >
              {{ getEmbeddingModelName(row.embeddingModelId) }}
            </el-tag>
            <span v-else style="color: #909399; font-size: 12px;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="向量存储" width="180" align="center">
          <template #default="{ row }">
            <el-tag 
              :type="getVectorStoreTypeTag(row.vectorStoreType)"
              size="small"
            >
              {{ getVectorStoreInstanceName(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
            <el-table-column label="操作" width="280" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-buttons-row">
                  <el-tooltip content="可以通过查看详情看摘要" placement="top">
                    <el-button 
                      size="small" 
                      type="warning" 
                      @click="handleGenerateSummary(row)"
                    >
                      <el-icon><DocumentCopy /></el-icon>
                      摘要
                    </el-button>
                  </el-tooltip>
                  <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
                  <el-dropdown @command="(command) => handleDropdownCommand(command, row)">
                    <el-button size="small" type="primary">
                      更多<el-icon class="el-icon--right"><arrow-down /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="view">
                          <el-icon><View /></el-icon>
                          查看详情
                        </el-dropdown-item>
                        <el-dropdown-item command="edit">
                          <el-icon><Edit /></el-icon>
                          编辑信息
                        </el-dropdown-item>
                        <el-dropdown-item command="documents">
                          <el-icon><Document /></el-icon>
                          文件管理
                        </el-dropdown-item>
                        <el-dropdown-item command="export">
                          <el-icon><Download /></el-icon>
                          导出知识库
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredKnowledgeBases.length"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      :close-on-click-modal="false"
      :lock-scroll="true"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
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
        <el-form-item label="可见性">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 0;"
          >
            <template #title>
              <span>普通用户只能创建私有知识库，只有创建者可以访问</span>
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
              <div style="display: flex; justify-content: space-between; align-items: center; width: 100%">
                <el-tag 
                  size="small"
                  :style="getModelStyle(model.id)"
                  style="flex-shrink: 0"
                >
                  {{ model.name }}
                </el-tag>
                <el-tag v-if="model.isDefault" type="primary" size="small" style="margin-left: 8px; flex-shrink: 0">
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
                v-for="db in vectorDatabases.filter(db => db.enabled && (db.allowCreateKnowledgeBase !== false))"
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
            <span>当前使用：<strong>{{ getVectorStoreTypeName(formData.vectorStoreType) }}</strong>。已有文档，无法修改。</span>
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
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog
      v-model="viewDialogVisible"
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
            :style="getModelStyle(currentKB.embeddingModelId)"
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
              @click="handleGenerateSummaryFromView"
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
          @click="handleGenerateSummaryFromView"
        >
          生成摘要
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入知识库对话框 -->
    <el-dialog 
      v-model="importDialogVisible" 
      title="导入知识库" 
      width="700px" 
      :close-on-click-modal="false"
      :lock-scroll="true"
      class="import-dialog"
    >
      <el-form 
        :model="importForm" 
        :rules="importRules" 
        ref="importFormRef" 
        label-width="120px"
        label-position="right"
      >
        <!-- 文件上传区域 -->
        <el-form-item label="ZIP文件" required>
          <div class="upload-wrapper">
            <!-- 未选择文件时显示上传框 -->
            <el-upload
              v-if="importFileList.length === 0"
              :auto-upload="false"
              :on-change="handleFileChange"
              :file-list="importFileList"
              accept=".zip"
              drag
              :limit="1"
              class="import-upload"
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">将ZIP文件拖到此处，或<em>点击上传</em></div>
            </el-upload>
            <div class="upload-tip-wrapper">
              <el-tooltip content="支持导入包含文档文件的ZIP压缩包" placement="top">
                <el-icon class="upload-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
            <!-- 已选择文件时显示文件信息 -->
            <div v-else class="file-selected-info">
              <el-icon><Document /></el-icon>
              <span class="file-name">{{ importFileList[0].name }}</span>
              <el-button 
                type="primary" 
                link 
                size="small" 
                @click="handleReSelectFile"
                :disabled="importing"
              >
                重新选择
              </el-button>
            </div>
          </div>
        </el-form-item>
        
        <!-- 文件预览 -->
        <el-form-item v-if="previewFiles.length > 0" label="文件预览">
          <div class="preview-table-wrapper">
            <el-table :data="previewFiles" size="small" max-height="140" stripe>
              <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
              <el-table-column prop="fileSize" label="大小" width="100" align="center">
                <template #default="{ row }">
                  {{ formatFileSize(row.fileSize) }}
                </template>
              </el-table-column>
              <el-table-column prop="fileType" label="类型" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.fileType }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div class="preview-tip-wrapper">
              <span class="preview-file-count">共 {{ previewFiles.length }} 个文件</span>
              <el-tooltip content="导入后将自动进行向量化处理" placement="top">
                <el-icon class="preview-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
          </div>
        </el-form-item>
        
        <!-- 知识库信息（文件上传后显示） -->
        <template v-if="importFileList.length > 0">
          <el-divider class="form-divider" />
          
          <div class="form-section-header">
            <span>请确认知识库信息</span>
            <el-tooltip content="请确认知识库的基本信息，默认使用ZIP文件名作为知识库名称" placement="top">
              <el-icon class="form-section-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
          
          <!-- 基本信息 -->
          <el-form-item label="知识库名称" prop="name" required>
            <div class="input-with-tooltip">
              <el-input 
                v-model="importForm.name" 
                placeholder="请输入知识库名称"
                :disabled="importing"
                clearable
              />
              <el-tooltip v-if="defaultName" :content="`默认名称：${defaultName}（可修改）`" placement="top">
                <el-icon class="input-tooltip-icon"><InfoFilled /></el-icon>
              </el-tooltip>
            </div>
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
          <el-collapse class="advanced-config-collapse">
            <el-collapse-item title="高级配置（可选）" name="advanced">
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
            </el-collapse-item>
          </el-collapse>
        </template>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="importDialogVisible = false" :disabled="importing">取消</el-button>
          <el-button 
            type="primary" 
            @click="handleConfirmImport" 
            :loading="importing"
            :disabled="importFileList.length === 0 || !importForm.name"
          >
            <el-icon v-if="!importing"><UploadFilled /></el-icon>
            开始导入
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElTooltip } from 'element-plus'
import { Plus, Search, Document, ArrowDown, UploadFilled, View, Edit, Check, Close, Warning, Link, QuestionFilled, DocumentCopy, ArrowLeft, Download } from '@element-plus/icons-vue'
import { 
  getKnowledgeBaseList, 
  createKnowledgeBase, 
  updateKnowledgeBase, 
  deleteKnowledgeBase,
  getKnowledgeBaseDetail,
  generateKnowledgeBaseSummary,
  exportKnowledgeBase,
  importKnowledgeBase,
  previewZipFile
} from '@/api/knowledgeBase'
import { getModelConfig } from '@/api/model'
import { getModelStyle } from '@/utils/modelColor'
import { getVectorDatabaseList } from '@/api/vectorDatabase'

const knowledgeBases = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterStatus = ref('')
const filterVectorStoreType = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const router = useRouter()

// 返回主页
const handleBack = () => {
  router.push('/user/chat')
}

const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const currentKB = ref(null)
const isEdit = ref(false)
const formRef = ref(null)
const currentEditId = ref(null)
const currentEditDocumentCount = ref(0)
const embeddingModels = ref([])
const vectorDatabases = ref([]) // 向量库配置列表
const enabledVectorStoreTypes = ref([]) // 启用的向量库类型列表

const formData = ref({
  name: '',
  description: '',
  status: 'active',
  isPublic: false, // 普通用户只能创建私有知识库
  embeddingModelId: null,
  topK: null,
  vectorStoreType: 'qdrant'
})

// 计算属性：是否有文档
const hasDocuments = computed(() => {
  return currentEditDocumentCount.value > 0
})

onMounted(() => {
  loadKnowledgeBases()
  loadEmbeddingModels()
  loadVectorDatabases()
})

// 加载向量化模型列表
const loadEmbeddingModels = async () => {
  try {
    const response = await getModelConfig()
    embeddingModels.value = (response.embeddingModels || []).filter(m => m.enabled)
  } catch (error) {
    console.error('加载向量化模型列表失败', error)
  }
}

// 获取默认向量存储类型
const getDefaultVectorStoreType = () => {
  try {
    // 确保 vectorDatabases 已初始化
    if (!vectorDatabases.value || vectorDatabases.value.length === 0) {
      return 'qdrant'
    }
    
    // 查找默认的向量库配置
    const defaultDb = vectorDatabases.value.find(db => db.isDefault && db.enabled && (db.allowCreateKnowledgeBase !== false))
    if (defaultDb && defaultDb.type) {
      return defaultDb.type.toLowerCase()
    }
    // 如果没有默认配置，使用第一个启用的且允许新建知识库的配置
    const firstEnabledDb = vectorDatabases.value.find(db => db.enabled && (db.allowCreateKnowledgeBase !== false))
    if (firstEnabledDb && firstEnabledDb.type) {
      return firstEnabledDb.type.toLowerCase()
    }
    // 如果都没有，返回默认值
    return 'qdrant'
  } catch (error) {
    console.warn('获取默认向量存储类型时出错', error)
    return 'qdrant'
  }
}

// 加载向量库配置列表
const loadVectorDatabases = async () => {
  try {
    const response = await getVectorDatabaseList()
    vectorDatabases.value = response || []
    
    // 计算启用的向量库类型
    const enabledTypes = new Set()
    vectorDatabases.value.forEach(db => {
      if (db.enabled && db.type) {
        enabledTypes.add(db.type.toLowerCase())
      }
    })
    enabledVectorStoreTypes.value = Array.from(enabledTypes)
    
    // 如果表单还没有设置向量存储类型，或者当前是默认值，则更新为默认向量库类型
    if (!formData.value.vectorStoreType || formData.value.vectorStoreType === 'qdrant' || !formData.value.vectorStoreType.includes('_')) {
      const defaultType = getDefaultVectorStoreType()
      // 查找默认实例，转换为 type_id 格式
      const defaultDb = vectorDatabases.value.find(db => 
        db.type === defaultType && db.isDefault && db.enabled
      )
      if (defaultDb) {
        formData.value.vectorStoreType = `${defaultDb.type}_${defaultDb.id}`
      } else {
        const firstDb = vectorDatabases.value.find(db => 
          db.type === defaultType && db.enabled
        )
        if (firstDb) {
          formData.value.vectorStoreType = `${firstDb.type}_${firstDb.id}`
        } else {
          formData.value.vectorStoreType = defaultType
        }
      }
      console.log('设置默认向量存储类型:', formData.value.vectorStoreType)
    }
  } catch (error) {
    console.error('加载向量库配置列表失败', error)
    // 如果加载失败，默认允许所有类型
    enabledVectorStoreTypes.value = ['qdrant', 'faiss', 'milvus', 'chroma', 'weaviate', 'elasticsearch']
  }
}

// 检查向量库类型是否启用
const isVectorStoreTypeEnabled = (type) => {
  if (!type) return true // 如果没有指定类型，默认允许
  return enabledVectorStoreTypes.value.includes(type.toLowerCase())
}

// 获取指定类型的所有启用的向量库实例
const getEnabledVectorDatabasesByType = (type) => {
  if (!vectorDatabases.value || vectorDatabases.value.length === 0) {
    return []
  }
  return vectorDatabases.value.filter(db => 
    db.type && db.type.toLowerCase() === type.toLowerCase() && db.enabled
  )
}

// 从 type_id 格式中提取类型
const extractTypeFromValue = (value) => {
  if (!value) return ''
  if (value.includes('_')) {
    const parts = value.split('_')
    return parts.slice(0, -1).join('_')
  }
  return value
}

// 从 value 获取类型名称（支持 type_id 格式）
const getVectorStoreTypeNameFromValue = (value) => {
  const type = extractTypeFromValue(value)
  return getVectorStoreTypeName(type)
}

// 从 value 获取类型描述（支持 type_id 格式）
const getVectorStoreTypeDescriptionFromValue = (value) => {
  const type = extractTypeFromValue(value)
  return getVectorStoreTypeDescription(type)
}

// 检查是否为默认向量存储类型
const isDefaultVectorStoreType = (type) => {
  const defaultType = getDefaultVectorStoreType()
  return defaultType && defaultType.toLowerCase() === type.toLowerCase()
}

// 辅助函数：根据模型ID获取模型名称
const getEmbeddingModelName = (modelId) => {
  if (modelId) {
    const model = embeddingModels.value.find(m => m.id === modelId)
    return model ? model.name : null
  } else {
    // 如果没有指定模型ID，返回默认模型名称
    const defaultModel = embeddingModels.value.find(m => m.isDefault)
    return defaultModel ? defaultModel.name : null
  }
}

const formRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' }
  ]
}

const dialogTitle = computed(() => {
  return isEdit.value ? '编辑知识库' : '创建知识库'
})

// 状态映射：前端使用active/inactive，后端使用1/0
const statusMap = {
  'active': 1,
  'inactive': 0,
  1: 'active',
  0: 'inactive'
}

// 辅助函数：判断状态是否为启用
const isActive = (status) => {
  if (typeof status === 'number') {
    return status === 1
  }
  return status === 'active'
}

// 辅助函数：获取状态文本
const getStatusText = (status) => {
  return isActive(status) ? '启用' : '禁用'
}


const filteredKnowledgeBases = computed(() => {
  let result = [...knowledgeBases.value]
  
  // 搜索过滤
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(kb => 
      kb.name.toLowerCase().includes(keyword) ||
      (kb.description && kb.description.toLowerCase().includes(keyword))
    )
  }
  
  // 状态过滤
  if (filterStatus.value) {
    result = result.filter(kb => {
      const kbStatus = typeof kb.status === 'number' ? statusMap[kb.status] : kb.status
      return kbStatus === filterStatus.value
    })
  }
  
  // 向量库类型过滤
  if (filterVectorStoreType.value) {
    result = result.filter(kb => {
      return kb.vectorStoreType === filterVectorStoreType.value
    })
  }
  
  return result
})

// 分页后的数据
const paginatedKnowledgeBases = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredKnowledgeBases.value.slice(start, end)
})

// 加载知识库列表（只加载当前用户的知识库）
const loadKnowledgeBases = async () => {
  loading.value = true
  try {
    // 获取当前用户ID
    const userInfoStr = localStorage.getItem('userInfo')
    let userId = null
    if (userInfoStr) {
      try {
        const userInfo = JSON.parse(userInfoStr)
        userId = userInfo.userId
      } catch (e) {
        console.error('解析用户信息失败', e)
      }
    }
    
    const params = {}
    if (userId) {
      params.userId = userId // 只获取当前用户的知识库
    }
    if (filterStatus.value) {
      params.status = statusMap[filterStatus.value]
    }
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }
    
    const response = await getKnowledgeBaseList(params)
    knowledgeBases.value = response.map(kb => ({
      ...kb,
      status: typeof kb.status === 'number' ? statusMap[kb.status] : kb.status
    }))
  } catch (error) {
    ElMessage.error('加载知识库列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadKnowledgeBases()
}

const handleFilter = () => {
  currentPage.value = 1
  loadKnowledgeBases()
}


const handleCreate = () => {
  isEdit.value = false
  currentEditId.value = null
  currentEditDocumentCount.value = 0
  try {
    // 获取默认向量存储实例，优先选择 isDefault=true 的实例
    let defaultVectorStoreValue = 'qdrant'
    if (vectorDatabases.value && vectorDatabases.value.length > 0) {
      // 查找默认实例（isDefault=true 且 enabled=true）
      const defaultDb = vectorDatabases.value.find(db => db.isDefault && db.enabled && (db.allowCreateKnowledgeBase !== false))
      if (defaultDb) {
        defaultVectorStoreValue = `${defaultDb.type}_${defaultDb.id}`
      } else {
        // 如果没有默认实例，使用第一个启用的且允许新建知识库的实例
        const firstDb = vectorDatabases.value.find(db => db.enabled && (db.allowCreateKnowledgeBase !== false))
        if (firstDb) {
          defaultVectorStoreValue = `${firstDb.type}_${firstDb.id}`
        } else {
          // 如果都没有启用的实例，使用默认类型
          try {
            const defaultType = getDefaultVectorStoreType()
            defaultVectorStoreValue = defaultType
          } catch (e) {
            console.warn('获取默认向量存储类型失败，使用默认值 qdrant', e)
          }
        }
      }
    } else {
      // 如果向量库列表还未加载，使用默认类型
      try {
        const defaultType = getDefaultVectorStoreType()
        defaultVectorStoreValue = defaultType
      } catch (e) {
        console.warn('获取默认向量存储类型失败，使用默认值 qdrant', e)
      }
    }
    
    formData.value = {
      name: '',
      description: '',
      status: 'active',
      isPublic: false, // 普通用户只能创建私有知识库
      embeddingModelId: null,
      topK: null,
      vectorStoreType: defaultVectorStoreValue
    }
    dialogVisible.value = true
  } catch (error) {
    console.error('创建知识库对话框打开失败', error)
    ElMessage.error('打开创建对话框失败：' + (error.message || '未知错误'))
  }
}

const handleEdit = (row) => {
  isEdit.value = true
  currentEditId.value = row.id
  currentEditDocumentCount.value = row.documentCount || 0
  // 根据 vectorDatabaseId 或 vectorStoreType 转换为 type_id 格式
  let vectorStoreTypeValue = 'qdrant'
  if (row.vectorDatabaseId && vectorDatabases.value && vectorDatabases.value.length > 0) {
    // 优先使用 vectorDatabaseId 查找对应的实例
    const db = vectorDatabases.value.find(db => db.id === row.vectorDatabaseId && db.enabled)
    if (db) {
      vectorStoreTypeValue = `${db.type}_${db.id}`
    } else {
      // 如果找不到对应的实例，使用类型查找默认实例
      const type = row.vectorStoreType || 'qdrant'
      const defaultDb = vectorDatabases.value.find(db => 
        db.type === type && db.isDefault && db.enabled
      )
      if (defaultDb) {
        vectorStoreTypeValue = `${defaultDb.type}_${defaultDb.id}`
      } else {
        const firstDb = vectorDatabases.value.find(db => 
          db.type === type && db.enabled
        )
        if (firstDb) {
          vectorStoreTypeValue = `${firstDb.type}_${firstDb.id}`
        }
      }
    }
  } else if (row.vectorStoreType && vectorDatabases.value && vectorDatabases.value.length > 0) {
    // 兼容旧数据：如果没有 vectorDatabaseId，使用类型查找
    const type = row.vectorStoreType
    const defaultDb = vectorDatabases.value.find(db => 
      db.type === type && db.isDefault && db.enabled
    )
    if (defaultDb) {
      vectorStoreTypeValue = `${defaultDb.type}_${defaultDb.id}`
    } else {
      const firstDb = vectorDatabases.value.find(db => 
        db.type === type && db.enabled
      )
      if (firstDb) {
        vectorStoreTypeValue = `${firstDb.type}_${firstDb.id}`
      }
    }
  }
  formData.value = {
    name: row.name,
    description: row.description || '',
    status: typeof row.status === 'number' ? statusMap[row.status] : row.status,
    isPublic: false, // 普通用户只能创建私有知识库
    embeddingModelId: row.embeddingModelId || null,
    topK: row.topK || null,
    vectorStoreType: vectorStoreTypeValue
  }
  dialogVisible.value = true
}

const handleView = async (row) => {
  try {
    const response = await getKnowledgeBaseDetail(row.id)
    currentKB.value = {
      ...response,
      status: typeof response.status === 'number' ? statusMap[response.status] : response.status
    }
    viewDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取知识库详情失败：' + (error.message || '未知错误'))
  }
}

const handleGenerateSummaryFromView = async () => {
  if (!currentKB.value) return
  try {
    ElMessageBox.confirm(
      `确定要为知识库"${currentKB.value.name}"生成智能摘要吗？这将基于知识库中的文档内容自动生成摘要。`,
      '生成摘要确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    ).then(async () => {
      try {
        ElMessage.info('正在生成摘要，请稍候...')
        const response = await generateKnowledgeBaseSummary(currentKB.value.id)
        ElMessage.success('摘要生成成功')
        // 刷新知识库详情
        const updatedResponse = await getKnowledgeBaseDetail(currentKB.value.id)
        currentKB.value = {
          ...updatedResponse,
          status: typeof updatedResponse.status === 'number' ? statusMap[updatedResponse.status] : updatedResponse.status
        }
      } catch (error) {
        ElMessage.error('生成摘要失败：' + (error.message || '未知错误'))
      }
    }).catch(() => {
      // 取消操作
    })
  } catch (error) {
    ElMessage.error('操作失败：' + (error.message || '未知错误'))
  }
}

const handleDocuments = (row) => {
  router.push(`/user/knowledge-base/${row.id}/documents`)
}

const handleGenerateSummary = async (row) => {
  try {
    ElMessageBox.confirm(
      `确定要为知识库"${row.name}"生成智能摘要吗？这将基于知识库中的文档内容自动生成摘要。`,
      '生成摘要确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    ).then(async () => {
      try {
        ElMessage.info('正在生成摘要，请稍候...')
        const response = await generateKnowledgeBaseSummary(row.id)
        ElMessage.success('摘要生成成功')
        // 刷新知识库列表
        await loadKnowledgeBases()
      } catch (error) {
        ElMessage.error('生成摘要失败：' + (error.message || '未知错误'))
      }
    }).catch(() => {
      // 取消操作
    })
  } catch (error) {
    ElMessage.error('操作失败：' + (error.message || '未知错误'))
  }
}

const handleDropdownCommand = (command, row) => {
  switch (command) {
    case 'view':
      handleView(row)
      break
    case 'edit':
      handleEdit(row)
      break
    case 'documents':
      handleDocuments(row)
      break
    case 'export':
      handleExport(row)
      break
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除知识库"${row.name}"吗？此操作不可恢复。`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await deleteKnowledgeBase(row.id)
      ElMessage.success('删除成功')
      loadKnowledgeBases()
    } catch (error) {
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }).catch(() => {
    // 取消操作
  })
}

// 导出知识库
const handleExport = async (row) => {
  try {
    ElMessage.info('正在导出知识库，请稍候...')
    const response = await exportKnowledgeBase(row.id)
    
    // 创建下载链接
    const url = window.URL.createObjectURL(response)
    const link = document.createElement('a')
    link.href = url
    link.download = `knowledge-base-${row.id}-${Date.now()}.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败：' + (error.message || '未知错误'))
  }
}

// 导入相关状态
const importDialogVisible = ref(false)
const importFormRef = ref(null)
const importFileList = ref([])
const importing = ref(false)
const previewFiles = ref([])
const defaultName = ref('')

const importForm = ref({
  name: '',
  description: '',
  vectorStoreType: '',
  isPublic: false
})

const importRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 1, max: 100, message: '长度在 1 到 100 个字符', trigger: 'blur' }
  ]
}

// 打开导入对话框
const handleImport = () => {
  importDialogVisible.value = true
  importForm.value = {
    name: '',
    description: '',
    vectorStoreType: '',
    isPublic: false
  }
  importFileList.value = []
  previewFiles.value = []
  defaultName.value = ''
}

// 提取知识库名称的工具函数
const extractKnowledgeBaseName = (fileName) => {
  // 去掉.zip扩展名（不区分大小写）
  let name = fileName.replace(/\.zip$/i, '')
  
  // 去掉路径（只取文件名，支持Windows和Unix路径分隔符）
  name = name.split('/').pop().split('\\').pop()
  
  // 如果提取失败或为空，使用默认值
  return name.trim() || '导入的知识库'
}

// 文件上传处理
const handleFileChange = (file, fileList) => {
  importFileList.value = fileList
  
  if (fileList.length > 0) {
    const zipFile = fileList[0].raw || fileList[0]
    
    // 提取默认知识库名称
    const fileName = zipFile.name
    const extractedName = extractKnowledgeBaseName(fileName)
    
    // 如果知识库名称为空，自动填充默认名称
    if (!importForm.value.name) {
      importForm.value.name = extractedName
    }
    
    // 保存默认名称用于显示
    defaultName.value = extractedName
    
    // 可选：预览ZIP内容
    previewZipContent(zipFile)
  }
}

// 重新选择文件
const handleReSelectFile = () => {
  importFileList.value = []
  previewFiles.value = []
  defaultName.value = ''
  importForm.value.name = ''
}

// 预览ZIP内容（可选）
const previewZipContent = async (file) => {
  try {
    const formData = new FormData()
    formData.append('file', file)
    const result = await previewZipFile(formData)
    previewFiles.value = result.files || []
  } catch (error) {
    console.error('预览ZIP失败:', error)
    // 预览失败不影响导入
  }
}

// 确认导入
const handleConfirmImport = async () => {
  if (!importFormRef.value) return
  
  await importFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    if (importFileList.value.length === 0) {
      ElMessage.warning('请选择ZIP文件')
      return
    }
    
    importing.value = true
    try {
      const formData = new FormData()
      formData.append('file', importFileList.value[0].raw)
      formData.append('knowledgeBaseName', importForm.value.name)
      
      if (importForm.value.description) {
        formData.append('description', importForm.value.description)
      }
      if (importForm.value.vectorStoreType) {
        formData.append('vectorStoreType', importForm.value.vectorStoreType)
      }
      // 普通用户只能创建私有知识库，所以isPublic固定为false
      
      const result = await importKnowledgeBase(formData)
      
      if (result.status === 'SUCCESS') {
        ElMessage.success(`导入成功！共导入 ${result.successCount} 个文档`)
      } else if (result.status === 'PARTIAL_SUCCESS') {
        ElMessage.warning(`部分导入成功！成功: ${result.successCount}，失败: ${result.failedCount}`)
        if (result.errors && result.errors.length > 0) {
          console.error('导入错误:', result.errors)
        }
      } else {
        ElMessage.error('导入失败：' + (result.message || '未知错误'))
      }
      
      // 关闭对话框并刷新列表
      importDialogVisible.value = false
      loadKnowledgeBases()
    } catch (error) {
      ElMessage.error('导入失败：' + (error.message || '未知错误'))
    } finally {
      importing.value = false
    }
  })
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const handleSubmit = () => {
  formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const data = {
          name: formData.value.name,
          description: formData.value.description,
          status: statusMap[formData.value.status],
          isPublic: false // 普通用户只能创建私有知识库
        }
        
        // 添加向量化模型ID（如果选择了）
        if (formData.value.embeddingModelId) {
          data.embeddingModelId = formData.value.embeddingModelId
        }
        
        // 添加topK（如果设置了）
        if (formData.value.topK !== null && formData.value.topK !== undefined) {
          data.topK = formData.value.topK
        }
        
        // 添加vectorStoreType和vectorDatabaseId（如果设置了）
        // 如果value是 type_id 格式，提取type和ID
        if (formData.value.vectorStoreType) {
          const vectorStoreValue = formData.value.vectorStoreType
          // 检查是否是 type_id 格式
          if (vectorStoreValue.includes('_')) {
            const parts = vectorStoreValue.split('_')
            // 提取类型部分（除了最后一个下划线后的ID）
            const type = parts.slice(0, -1).join('_')
            // 提取ID部分（最后一个下划线后的数字）
            const idStr = parts[parts.length - 1]
            const id = parseInt(idStr, 10)
            if (!isNaN(id)) {
              data.vectorDatabaseId = id
              data.vectorStoreType = type
            } else {
              // 如果ID解析失败，只设置类型
              data.vectorStoreType = type
            }
          } else {
            // 兼容旧格式（只有类型）
            data.vectorStoreType = vectorStoreValue
          }
        }
        
        if (isEdit.value) {
          await updateKnowledgeBase(currentEditId.value, data)
          ElMessage.success('编辑成功')
        } else {
          await createKnowledgeBase(data)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        loadKnowledgeBases()
      } catch (error) {
        ElMessage.error((isEdit.value ? '编辑' : '创建') + '失败：' + (error.message || '未知错误'))
      }
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
}

const handlePageChange = (page) => {
  currentPage.value = page
}

const formatDate = (date) => {
  if (!date) return ''
  if (typeof date === 'string') {
    return date
  }
  if (date instanceof Date) {
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  }
  // 如果是时间戳
  const d = new Date(date)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 获取向量存储类型描述
const getVectorStoreTypeDescription = (type) => {
  if (!type) return ''
  const descriptions = {
    'qdrant': '分布式向量数据库，适合生产环境。',
    'faiss': '本地文件存储，无需额外服务，适合开发测试。',
    'milvus': '开源向量数据库，支持大规模向量检索，需要独立服务器，使用 gRPC 协议。',
    'chroma': '开源向量数据库，轻量级，易于部署，支持 HTTP REST API。',
    'weaviate': '开源向量数据库，支持 GraphQL 和 REST API，提供强大的语义搜索能力。',
    'elasticsearch': '企业级分布式搜索和分析引擎，支持向量搜索、全文检索和混合搜索。具备高可用性、水平扩展能力强，适合大规模生产环境。支持多种认证方式（用户名密码、API Key）。重要：建议使用 Elasticsearch 8.x 版本（推荐 8.11.0+），低版本可能存在兼容性问题。'
  }
  return descriptions[type.toLowerCase()] || ''
}

// 获取向量存储类型名称（简短）
const getVectorStoreTypeName = (type) => {
  if (type === 'faiss') return 'FAISS'
  if (type === 'milvus') return 'Milvus'
  if (type === 'chroma') return 'Chroma'
  if (type === 'weaviate') return 'Weaviate'
  if (type === 'elasticsearch') return 'Elasticsearch'
  return 'Qdrant'
}

// 获取向量存储类型显示名称（完整）
const getVectorStoreTypeDisplayName = (type) => {
  if (type === 'faiss') return 'FAISS（本地文件存储）'
  if (type === 'milvus') return 'Milvus（向量数据库）'
  if (type === 'chroma') return 'Chroma（向量数据库）'
  if (type === 'weaviate') return 'Weaviate（向量数据库）'
  if (type === 'elasticsearch') return 'Elasticsearch（向量数据库）'
  return 'Qdrant（向量数据库）'
}

// 根据类型获取向量库实例名称
const getVectorStoreInstanceName = (row) => {
  if (!row) return '-'
  if (!vectorDatabases.value || vectorDatabases.value.length === 0) {
    // 如果还没有加载向量库列表，返回类型名称作为后备
    return getVectorStoreTypeName(row.vectorStoreType)
  }
  
  // 优先使用 vectorDatabaseId 进行精确匹配
  if (row.vectorDatabaseId) {
    const db = vectorDatabases.value.find(db => 
      db.id === row.vectorDatabaseId && db.enabled
    )
    if (db) {
      return db.name
    }
  }
  
  // 兼容旧数据：如果没有 vectorDatabaseId，则按类型查找
  const type = row.vectorStoreType
  if (!type) return '-'
  
  // 查找该类型的默认实例
  const defaultDb = vectorDatabases.value.find(db => 
    db.type === type && db.isDefault && db.enabled
  )
  if (defaultDb) {
    return defaultDb.name
  }
  // 如果没有默认实例，使用第一个启用的实例
  const firstDb = vectorDatabases.value.find(db => 
    db.type === type && db.enabled
  )
  if (firstDb) {
    return firstDb.name
  }
  // 如果找不到实例，返回类型名称作为后备
  return getVectorStoreTypeName(type)
}

// 获取向量存储类型标签类型
const getVectorStoreTypeTag = (type) => {
  if (type === 'faiss') return 'success'
  if (type === 'milvus') return 'warning'
  if (type === 'chroma') return 'info'
  if (type === 'weaviate') return 'success'
  if (type === 'elasticsearch') return 'warning'
  return 'primary'
}

// 获取向量库实例的文档数量（只统计当前用户的文档）
const getVectorDatabaseDocumentCount = (db) => {
  if (!knowledgeBases.value || knowledgeBases.value.length === 0) {
    return 0
  }
  
  // 获取当前用户ID
  const userInfoStr = localStorage.getItem('userInfo')
  let currentUserId = null
  if (userInfoStr) {
    try {
      const userInfo = JSON.parse(userInfoStr)
      currentUserId = userInfo.userId
    } catch (e) {
      console.error('解析用户信息失败', e)
    }
  }
  
  // 根据向量库实例ID精确统计文档数量（只统计当前用户的知识库）
  let totalCount = 0
  knowledgeBases.value.forEach(kb => {
    // 只统计当前用户创建的知识库
    if (currentUserId && kb.creatorId !== currentUserId) {
      return // 跳过其他用户的知识库
    }
    
    // 优先使用 vectorDatabaseId 进行精确匹配
    if (kb.vectorDatabaseId === db.id) {
      totalCount += (kb.documentCount || 0)
    } else if (!kb.vectorDatabaseId && kb.vectorStoreType === db.type) {
      // 兼容旧数据：如果没有 vectorDatabaseId，则按类型匹配
      totalCount += (kb.documentCount || 0)
    }
  })
  return totalCount
}

</script>

<style scoped>
/* 向量存储下拉菜单样式 */
:deep(.vector-store-select-dropdown) {
  min-width: 550px !important;
  max-width: none !important;
}

:deep(.vector-store-select-dropdown .el-select-dropdown__item) {
  padding: 8px 20px 8px 20px !important;
  height: auto;
  min-height: 48px;
  overflow: visible !important;
}

:deep(.vector-store-select-dropdown .el-select-dropdown__item > span) {
  overflow: visible !important;
  width: 100% !important;
  display: block !important;
}

:deep(.vector-store-select-dropdown .el-select-dropdown__item .el-tag) {
  flex-shrink: 0 !important;
  white-space: nowrap !important;
}

.knowledge-base-management {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-bar {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.kb-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kb-icon {
  color: #409eff;
  font-size: 18px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.doc-management {
  padding: 10px 0;
}

.upload-section {
  padding: 20px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  background-color: #fafafa;
}

.upload-actions {
  display: flex;
  gap: 10px;
}

.doc-list-section {
  margin-top: 20px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 10px;
}

/* 紧凑表格样式 */
.compact-table :deep(.el-table__cell) {
  padding: 8px 0;
}

.compact-table :deep(.el-button) {
  padding: 5px 10px;
  font-size: 12px;
}

.compact-table :deep(.el-tag) {
  font-size: 12px;
  padding: 0 6px;
  height: 22px;
  line-height: 22px;
}

.action-buttons-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.action-buttons-row .el-button {
  flex-shrink: 0;
  margin: 0;
}

.action-buttons-row .el-button + .el-button {
  margin-left: 0;
}

.form-item-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  line-height: 1.5;
}

.form-item-hint-warning {
  color: #e6a23c;
  display: flex;
  align-items: flex-start;
  gap: 4px;
}

.form-item-hint-warning .el-icon {
  margin-top: 2px;
  flex-shrink: 0;
}

.form-item-description {
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
  border-left: 3px solid #409eff;
}

.form-item-description .description-label {
  font-weight: 500;
  color: #303133;
}

.form-item-description .description-text {
  color: #606266;
}
/* ========== 导入对话框样式 ========== */
:deep(.import-dialog .el-dialog__header) {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}

:deep(.import-dialog .el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

:deep(.import-dialog .el-dialog__body) {
  padding: 16px;
  background: #ffffff;
}

:deep(.import-dialog .el-form) {
  padding: 0;
}

/* 紧凑的表单项间距 */
:deep(.import-dialog .el-form-item) {
  margin-bottom: 12px;
}

:deep(.import-dialog .el-form-item__label) {
  padding-bottom: 0;
  line-height: 32px;
}

.upload-wrapper {
  width: 100%;
}

.import-upload :deep(.el-upload-dragger) {
  width: 100%;
  height: 120px;
  border: 2px dashed #d9d9d9;
  border-radius: 6px;
  background: #fafafa;
  transition: all 0.3s;
}

.import-upload :deep(.el-upload-dragger:hover) {
  border-color: #409eff;
  background: #f0f7ff;
}

.import-upload :deep(.el-icon--upload) {
  font-size: 36px;
  color: #8c939d;
  margin-bottom: 8px;
}

.import-upload :deep(.el-upload__text) {
  color: #606266;
  font-size: 14px;
}

.import-upload :deep(.el-upload__text em) {
  color: #409eff;
  font-style: normal;
}

.upload-tip-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

.upload-tip-icon {
  color: #909399;
  font-size: 16px;
  cursor: help;
}

.file-selected-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  color: #606266;
  font-size: 13px;
}

.file-selected-info .el-icon {
  color: #409eff;
  font-size: 18px;
  flex-shrink: 0;
}

.file-selected-info .file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.preview-table-wrapper {
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}

.preview-table-wrapper :deep(.el-table) {
  border: none;
}

.preview-table-wrapper :deep(.el-table th) {
  background: #f5f7fa;
}

.preview-tip-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 12px;
  background: #f5f7fa;
  border-top: 1px solid #e4e7ed;
}

.preview-file-count {
  color: #909399;
  font-size: 12px;
}

.preview-tip-icon {
  color: #909399;
  font-size: 14px;
  cursor: help;
}

.form-divider {
  margin: 12px 0;
  border-color: #e4e7ed;
}

.form-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 6px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  color: #606266;
  font-size: 13px;
  font-weight: 500;
}

.form-section-icon {
  color: #409eff;
  font-size: 16px;
  cursor: help;
  flex-shrink: 0;
}

.input-with-tooltip {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.input-with-tooltip :deep(.el-input) {
  flex: 1;
}

.input-tooltip-icon {
  color: #909399;
  font-size: 16px;
  cursor: help;
  flex-shrink: 0;
}

.advanced-config-collapse {
  margin-top: 8px;
}

.advanced-config-collapse :deep(.el-collapse-item__header) {
  padding: 6px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  height: auto;
  line-height: 1.5;
}

.advanced-config-collapse :deep(.el-collapse-item__content) {
  padding: 12px;
}

.advanced-config-collapse :deep(.el-collapse-item__content .el-form-item) {
  margin-bottom: 8px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #e4e7ed;
}

.dialog-footer .el-button {
  min-width: 100px;
}
</style>

