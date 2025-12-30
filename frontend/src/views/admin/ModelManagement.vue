<template>
  <div class="model-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>LLM管理</span>
        </div>
      </template>

      <el-tabs v-model="activeTab" type="border-card">
        <!-- 问答模型配置（智能问答和知识检索） -->
        <el-tab-pane label="问答模型" name="qa">
          <div class="model-list-section">
            <div class="section-header">
              <el-input
                v-model="qaSearchKeyword"
                placeholder="搜索模型名称、标识、提供商或API地址"
                clearable
                style="width: 300px"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-button type="primary" @click="handleAddModel">
                <el-icon><Plus /></el-icon>
                添加模型
              </el-button>
            </div>

            <el-table
              :data="filteredQAModelList"
              v-loading="loading.qa"
              stripe
              border
              style="width: 100%"
              :row-class-name="getQARowClassName"
            >
              <el-table-column prop="name" label="模型名称" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <span>{{ row.name }}</span>
                    <el-tag v-if="row.supportsMultimodal && row.supportsVision" type="success" size="small">
                      多模态
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="provider" label="提供商" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getProviderTagType(row.provider)">
                    {{ getProviderLabel(row.provider) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="模型标识" min-width="200">
                <template #default="{ row }">
                  <el-tag size="small" :style="getModelStyle(row.id)">
                    {{ row.model }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="apiUrl" label="API 地址" min-width="200" show-overflow-tooltip />
              <el-table-column label="使用场景" width="180" align="center">
                <template #default="{ row }">
                  <el-tag
                    v-if="row.useFor === 'chat'"
                    type="primary"
                    size="small"
                  >
                    仅智能问答
                  </el-tag>
                  <el-tag
                    v-else-if="row.useFor === 'rag'"
                    type="success"
                    size="small"
                  >
                    仅知识检索
                  </el-tag>
                  <el-tag
                    v-else-if="row.useFor === 'both'"
                    type="warning"
                    size="small"
                  >
                    两者都使用
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-switch
                    :model-value="row.enabled"
                    @change="(val) => handleToggleEnabled(row, val)"
                    :disabled="row.isDefault"
                    active-text=""
                    inactive-text=""
                  />
                </template>
              </el-table-column>
              <el-table-column label="默认" width="100" align="center">
                <template #default="{ row }">
                  <el-radio
                    :model-value="getDefaultQAModelId(row.useFor)"
                    :label="row.id"
                    @change="handleSetDefault(row)"
                    :disabled="!row.enabled"
                    style="--el-radio-input-width: 16px; --el-radio-input-height: 16px;"
                  >
                    <template #default></template>
                  </el-radio>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="280" fixed="right" align="center">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-button
                      size="small"
                      type="success"
                      @click="handleTestModel(row)"
                      :loading="row.testing"
                      :icon="Refresh"
                    >
                      测试
                    </el-button>
                    <el-button
                      size="small"
                      type="primary"
                      @click="handleEditModel(row)"
                      :icon="Edit"
                    >
                      编辑
                    </el-button>
                    <el-dropdown @command="(cmd) => handleActionCommand(cmd, row)" trigger="click">
                      <el-button size="small" :icon="More">
                        更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item
                            command="delete"
                            :disabled="row.isDefault"
                            :icon="Delete"
                          >
                            删除
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 向量化模型配置 -->
        <el-tab-pane label="向量化模型" name="embedding">
          <div class="model-list-section">
            <div class="section-header">
              <el-input
                v-model="embeddingSearchKeyword"
                placeholder="搜索模型名称、标识、提供商或API地址"
                clearable
                style="width: 300px"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-button type="primary" @click="handleAddEmbeddingModel">
                <el-icon><Plus /></el-icon>
                添加模型
              </el-button>
            </div>

            <el-table
              :data="filteredEmbeddingModelList"
              v-loading="loading.embedding"
              stripe
              border
              style="width: 100%"
              :row-class-name="getEmbeddingRowClassName"
            >
              <el-table-column prop="name" label="模型名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="provider" label="提供商" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getProviderTagType(row.provider)">
                    {{ getProviderLabel(row.provider) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="模型标识" min-width="200">
                <template #default="{ row }">
                  <el-tag size="small" :style="getModelStyle(row.id)">
                    {{ row.model }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="apiUrl" label="API 地址" min-width="200" show-overflow-tooltip />
              <el-table-column prop="timeout" label="超时时间（ms）" width="120" align="center" />
              <el-table-column prop="batchSize" label="批处理大小" width="120" align="center" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-switch
                    :model-value="row.enabled"
                    @change="(val) => handleToggleEnabledEmbedding(row, val)"
                    :disabled="row.isDefault"
                    active-text=""
                    inactive-text=""
                  />
                </template>
              </el-table-column>
              <el-table-column label="默认" width="100" align="center">
                <template #default="{ row }">
                  <el-radio
                    :model-value="getDefaultEmbeddingModelId()"
                    :label="row.id"
                    @change="handleSetDefaultEmbedding(row)"
                    :disabled="!row.enabled"
                    style="--el-radio-input-width: 16px; --el-radio-input-height: 16px;"
                  >
                    <template #default></template>
                  </el-radio>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="280" fixed="right" align="center">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-button
                      size="small"
                      type="success"
                      @click="handleTestEmbeddingModel(row)"
                      :loading="row.testing"
                      :icon="Refresh"
                    >
                      测试
                    </el-button>
                    <el-button
                      size="small"
                      type="primary"
                      @click="handleEditEmbeddingModel(row)"
                      :icon="Edit"
                    >
                      编辑
                    </el-button>
                    <el-dropdown @command="(cmd) => handleEmbeddingActionCommand(cmd, row)" trigger="click">
                      <el-button size="small" :icon="More">
                        更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item
                            command="delete"
                            :disabled="row.isDefault"
                            :icon="Delete"
                          >
                            删除
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 向量数据库 -->
        <el-tab-pane label="向量数据库" name="vectorDatabase">
          <VectorDatabaseManagement />
        </el-tab-pane>

        <!-- 提示词管理 -->
        <el-tab-pane label="提示词管理" name="prompt">
          <div class="prompt-section">
            <div class="section-header">
              <el-input
                v-model="promptSearchKeyword"
                placeholder="搜索提示词标题或内容"
                clearable
                style="width: 300px"
                @input="handlePromptSearch"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-button type="primary" @click="handleCreatePrompt">
                <el-icon><Plus /></el-icon>
                创建提示词
              </el-button>
            </div>

            <!-- 提示词列表 -->
            <div class="table-container">
              <el-table
                :data="prompts"
                v-loading="loading.prompt"
                stripe
              >
                <el-table-column prop="id" label="ID" width="80" align="center" />
                <el-table-column prop="title" label="标题" min-width="200" />
                <el-table-column prop="content" label="内容" min-width="400" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div class="content-preview">{{ row.content }}</div>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="创建时间" width="180">
                  <template #default="{ row }">
                    {{ formatPromptTime(row.createTime) }}
                  </template>
                </el-table-column>
                <el-table-column prop="updateTime" label="更新时间" width="180">
                  <template #default="{ row }">
                    {{ formatPromptTime(row.updateTime) }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" @click="handleEditPrompt(row)">编辑</el-button>
                    <el-button size="small" type="danger" @click="handleDeletePrompt(row.id)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 问答模型编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
      :lock-scroll="true"
    >
      <el-form
        :model="currentModel"
        :rules="modelFormRules"
        ref="modelFormRef"
        label-width="120px"
      >
        <el-form-item label="模型名称" prop="name">
          <el-input
            v-model="currentModel.name"
            placeholder="请输入模型名称，用于标识"
          />
        </el-form-item>

        <el-form-item label="提供商类型" prop="provider">
          <el-select v-model="currentModel.provider" placeholder="请选择提供商类型" style="width: 100%">
            <el-option label="OpenAI" value="openai" />
            <el-option label="VLLM" value="vllm" />
            <el-option label="Ollama" value="ollama" />
          </el-select>
        </el-form-item>

        <el-form-item label="API 地址" prop="apiUrl">
          <el-input
            v-model="currentModel.apiUrl"
            placeholder="例如: https://api.siliconflow.cn 或 http://localhost:8000"
          />
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey" v-if="currentModel.provider === 'openai'">
          <el-input
            v-model="currentModel.apiKey"
            type="password"
            show-password
            placeholder="请输入 API Key"
          />
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey" v-else-if="currentModel.provider === 'vllm'">
          <el-input
            v-model="currentModel.apiKey"
            type="password"
            show-password
            placeholder="VLLM 通常不需要 API Key（可选）"
          />
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey" v-else>
          <el-input
            v-model="currentModel.apiKey"
            type="password"
            show-password
            placeholder="Ollama 通常不需要 API Key（可选）"
          />
        </el-form-item>

        <el-form-item label="模型标识" prop="model">
          <el-input
            v-model="currentModel.model"
            placeholder="请输入模型标识"
          />
          <div class="form-tip">
            <el-icon><InfoFilled /></el-icon>
            <span v-if="currentModel.provider === 'openai'">
              例如: Qwen/Qwen2.5-72B-Instruct, gpt-4, gpt-3.5-turbo
            </span>
            <span v-else-if="currentModel.provider === 'vllm'">
              例如: Qwen/Qwen2.5-72B-Instruct
            </span>
            <span v-else>
              例如: qwen2.5:72b, llama3:70b, mistral:7b
            </span>
          </div>
        </el-form-item>

        <el-form-item label="使用场景" prop="useFor">
          <el-radio-group v-model="currentModel.useFor">
            <el-radio label="chat">仅用于智能问答</el-radio>
            <el-radio label="rag">仅用于知识检索</el-radio>
            <el-radio label="both">两者都使用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="启用状态">
          <el-switch v-model="currentModel.enabled" />
        </el-form-item>

        <!-- 多模态支持配置（仅问答模型） -->
        <template v-if="dialogType === 'qa'">
          <el-form-item label="支持多模态">
            <el-switch v-model="currentModel.supportsMultimodal" />
            <div class="form-tip">
              <el-icon><InfoFilled /></el-icon>
              <span>开启后，模型可以处理文本和图片等多种输入格式（如 Qwen-VL、GPT-4 Vision 等）</span>
            </div>
          </el-form-item>

          <el-form-item label="支持视觉输入" v-if="currentModel.supportsMultimodal">
            <el-switch 
              v-model="currentModel.supportsVision" 
              :disabled="!currentModel.supportsMultimodal"
            />
            <div class="form-tip">
              <el-icon><InfoFilled /></el-icon>
              <span>开启后，上传的图片将直接发送给模型进行视觉理解，而不是使用OCR识别。需要先开启"支持多模态"。</span>
            </div>
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveModel" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 向量化模型编辑对话框 -->
    <el-dialog
      v-model="embeddingDialogVisible"
      :title="embeddingDialogTitle"
      width="600px"
      :close-on-click-modal="false"
      :lock-scroll="true"
    >
      <el-form
        :model="currentEmbeddingModel"
        :rules="embeddingModelFormRules"
        ref="embeddingModelFormRef"
        label-width="120px"
      >
        <el-form-item label="模型名称" prop="name">
          <el-input
            v-model="currentEmbeddingModel.name"
            placeholder="请输入模型名称，用于标识"
          />
        </el-form-item>

        <el-form-item label="提供商类型" prop="provider">
          <el-select v-model="currentEmbeddingModel.provider" placeholder="请选择提供商类型" style="width: 100%">
            <el-option label="OpenAI" value="openai" />
            <el-option label="VLLM" value="vllm" />
            <el-option label="Ollama" value="ollama" />
          </el-select>
        </el-form-item>

        <el-form-item label="API 地址" prop="apiUrl">
          <el-input
            v-model="currentEmbeddingModel.apiUrl"
            placeholder="例如: https://api.siliconflow.cn 或 http://localhost:8000"
          />
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey" v-if="currentEmbeddingModel.provider === 'openai'">
          <el-input
            v-model="currentEmbeddingModel.apiKey"
            type="password"
            show-password
            placeholder="请输入 API Key"
          />
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey" v-else-if="currentEmbeddingModel.provider === 'vllm'">
          <el-input
            v-model="currentEmbeddingModel.apiKey"
            type="password"
            show-password
            placeholder="VLLM 通常不需要 API Key（可选）"
          />
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey" v-else>
          <el-input
            v-model="currentEmbeddingModel.apiKey"
            type="password"
            show-password
            placeholder="Ollama 通常不需要 API Key（可选）"
          />
        </el-form-item>

        <el-form-item label="模型标识" prop="model">
          <el-input
            v-model="currentEmbeddingModel.model"
            placeholder="请输入模型标识"
          />
          <div class="form-tip">
            <el-icon><InfoFilled /></el-icon>
            <span v-if="currentEmbeddingModel.provider === 'openai'">
              例如: Qwen/Qwen3-Embedding-8B, text-embedding-ada-002
            </span>
            <span v-else>
              例如: nomic-embed-text, mxbai-embed-large
            </span>
          </div>
        </el-form-item>

        <el-form-item label="超时时间（ms）" prop="timeout">
          <el-input-number
            v-model="currentEmbeddingModel.timeout"
            :min="1000"
            :max="600000"
            :step="1000"
            style="width: 100%"
          />
          <div class="form-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>建议设置为 300000（5分钟），支持大文档向量化</span>
          </div>
        </el-form-item>

        <el-form-item label="批处理大小" prop="batchSize">
          <el-input-number
            v-model="currentEmbeddingModel.batchSize"
            :min="1"
            :max="1000"
            :step="10"
            style="width: 100%"
          />
          <div class="form-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>每次向量化的文档块数量，建议设置为 100</span>
          </div>
        </el-form-item>

        <el-form-item label="启用状态">
          <el-switch v-model="currentEmbeddingModel.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="embeddingDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEmbeddingModel" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 提示词创建/编辑对话框 -->
    <el-dialog
      v-model="promptDialogVisible"
      :title="promptIsEdit ? '编辑提示词' : '创建提示词'"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-form :model="promptFormData" :rules="promptFormRules" ref="promptFormRef" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="promptFormData.title" placeholder="请输入提示词标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="promptFormData.content"
            type="textarea"
            :rows="15"
            placeholder="请输入提示词内容（支持Markdown格式）"
          />
          <div class="form-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>提示词内容支持Markdown格式，与智能问答的格式要求相同</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="promptDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitPrompt" :loading="promptSaving">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  InfoFilled,
  Link,
  Key,
  Box,
  Refresh,
  Plus,
  Edit,
  Delete,
  More,
  ArrowDown,
  Search
} from '@element-plus/icons-vue'
import { getModelConfig, updateModelConfig, testModelConnection } from '@/api/model'
import { getModelStyle } from '@/utils/modelColor'
import VectorDatabaseManagement from './VectorDatabaseManagement.vue'
import {
  getPrompts,
  createPrompt,
  updatePrompt,
  deletePrompt
} from '@/api/prompt'

const activeTab = ref('qa')
const saving = ref(false)
const loading = reactive({
  qa: false,
  embedding: false,
  prompt: false
})

const qaModelList = ref([])
const embeddingModelList = ref([])

// 查询关键词
const qaSearchKeyword = ref('')
const embeddingSearchKeyword = ref('')

// 过滤后的列表
const filteredQAModelList = computed(() => {
  if (!qaSearchKeyword.value.trim()) {
    return qaModelList.value
  }
  const keyword = qaSearchKeyword.value.toLowerCase().trim()
  return qaModelList.value.filter(model => {
    return (
      model.name?.toLowerCase().includes(keyword) ||
      model.model?.toLowerCase().includes(keyword) ||
      model.provider?.toLowerCase().includes(keyword) ||
      model.apiUrl?.toLowerCase().includes(keyword)
    )
  })
})

const filteredEmbeddingModelList = computed(() => {
  if (!embeddingSearchKeyword.value.trim()) {
    return embeddingModelList.value
  }
  const keyword = embeddingSearchKeyword.value.toLowerCase().trim()
  return embeddingModelList.value.filter(model => {
    return (
      model.name?.toLowerCase().includes(keyword) ||
      model.model?.toLowerCase().includes(keyword) ||
      model.provider?.toLowerCase().includes(keyword) ||
      model.apiUrl?.toLowerCase().includes(keyword)
    )
  })
})

// 提示词管理相关
const prompts = ref([])
const promptSaving = ref(false)
const promptSearchKeyword = ref('')
const promptDialogVisible = ref(false)
const promptIsEdit = ref(false)
const promptFormRef = ref(null)
const promptCurrentEditId = ref(null)
let promptSearchTimer = null

const promptFormData = ref({
  title: '',
  content: ''
})

const promptFormRules = {
  title: [{ required: true, message: '请输入提示词标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入提示词内容', trigger: 'blur' }]
}

// 问答模型对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('添加模型')
const currentModel = reactive({
  id: null,
  name: '',
  provider: 'openai',
  apiUrl: '',
  apiKey: '',
  model: '',
  useFor: 'both', // 'chat', 'rag', 'both'
  enabled: true,
  supportsMultimodal: false,
  supportsVision: false
})
const modelFormRef = ref(null)

// 向量化模型对话框相关
const embeddingDialogVisible = ref(false)
const embeddingDialogTitle = ref('添加向量化模型')
const currentEmbeddingModel = reactive({
  id: null,
  name: '',
  provider: 'openai',
  apiUrl: '',
  apiKey: '',
  model: '',
  timeout: 300000,
  batchSize: 100,
  enabled: true
})
const embeddingModelFormRef = ref(null)



const modelFormRules = {
  name: [
    { required: true, message: '请输入模型名称', trigger: 'blur' }
  ],
  provider: [
    { required: true, message: '请选择提供商类型', trigger: 'change' }
  ],
  apiUrl: [
    { required: true, message: '请输入 API 地址', trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+$/,
      message: '请输入有效的 URL 地址（以 http:// 或 https:// 开头）',
      trigger: 'blur'
    }
  ],
  model: [
    { required: true, message: '请输入模型标识', trigger: 'blur' }
  ],
  useFor: [
    { required: true, message: '请选择使用场景', trigger: 'change' }
  ]
}

const embeddingModelFormRules = {
  name: [
    { required: true, message: '请输入模型名称', trigger: 'blur' }
  ],
  provider: [
    { required: true, message: '请选择提供商类型', trigger: 'change' }
  ],
  apiUrl: [
    { required: true, message: '请输入 API 地址', trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+$/,
      message: '请输入有效的 URL 地址（以 http:// 或 https:// 开头）',
      trigger: 'blur'
    }
  ],
  model: [
    { required: true, message: '请输入模型标识', trigger: 'blur' }
  ],
  timeout: [
    { required: true, message: '请输入超时时间', trigger: 'blur' },
    { type: 'number', min: 1000, message: '超时时间不能小于 1000 毫秒', trigger: 'blur' }
  ],
  batchSize: [
    { required: true, message: '请输入批处理大小', trigger: 'blur' },
    { type: 'number', min: 1, message: '批处理大小不能小于 1', trigger: 'blur' }
  ]
}

// 获取提供商标签类型
const getProviderTagType = (provider) => {
  const map = {
    openai: 'primary',
    vllm: 'success',
    ollama: 'info'
  }
  return map[provider] || 'info'
}

// 获取提供商标签文本
const getProviderLabel = (provider) => {
  const map = {
    openai: 'OpenAI',
    vllm: 'VLLM',
    ollama: 'Ollama'
  }
  return map[provider] || provider
}

// 获取问答模型表格行类名（用于高亮默认模型）
const getQARowClassName = ({ row }) => {
  return row.isDefault ? 'default-model-row' : ''
}

// 获取向量化模型表格行类名（用于高亮默认模型）
const getEmbeddingRowClassName = ({ row }) => {
  return row.isDefault ? 'default-model-row' : ''
}

// 获取默认问答模型ID（根据使用场景）
const getDefaultQAModelId = (useFor) => {
  const defaultModel = qaModelList.value.find(model => 
    model.isDefault && (model.useFor === useFor || model.useFor === 'both')
  )
  return defaultModel ? defaultModel.id : null
}

// 获取默认向量化模型ID
const getDefaultEmbeddingModelId = () => {
  const defaultModel = embeddingModelList.value.find(model => model.isDefault)
  return defaultModel ? defaultModel.id : null
}

// 加载配置
const loadConfig = async () => {
  try {
    loading.qa = true
    
    const data = await getModelConfig()
    
    // 加载问答模型列表（统一管理）
    if (data.qaModels && Array.isArray(data.qaModels)) {
      qaModelList.value = data.qaModels.map(model => ({
        ...model,
        testing: false
      }))
    } else if (data.models && Array.isArray(data.models)) {
      // 兼容 models 字段
      qaModelList.value = data.models.map(model => ({
        ...model,
        testing: false
      }))
    } else {
      // 兼容旧格式：分别从 chat 和 rag 加载
      const models = []
      
      if (data.chatModels && Array.isArray(data.chatModels)) {
        data.chatModels.forEach(model => {
          models.push({
            ...model,
            useFor: model.useFor || 'chat',
            testing: false
          })
        })
      } else if (data.chat) {
        models.push({
          id: 1,
          name: data.chat.name || '默认模型（智能问答）',
          provider: data.chat.providerType || data.chat.provider || 'openai',
          apiUrl: data.chat.apiUrl || '',
          apiKey: data.chat.apiKey || '',
          model: data.chat.model || '',
          useFor: 'chat',
          enabled: true,
          isDefault: true,
          testing: false
        })
      }
      
      if (data.ragModels && Array.isArray(data.ragModels)) {
        data.ragModels.forEach(model => {
          models.push({
            ...model,
            useFor: model.useFor || 'rag',
            testing: false
          })
        })
      } else if (data.rag) {
        models.push({
          id: models.length + 1,
          name: data.rag.name || '默认模型（知识检索）',
          provider: data.rag.providerType || data.rag.provider || 'openai',
          apiUrl: data.rag.apiUrl || '',
          apiKey: data.rag.apiKey || '',
          model: data.rag.model || '',
          useFor: 'rag',
          enabled: true,
          isDefault: true,
          testing: false
        })
      }
      
      qaModelList.value = models
    }
    
    // 加载向量化模型列表
    if (data.embeddingModels && Array.isArray(data.embeddingModels)) {
      embeddingModelList.value = data.embeddingModels.map(model => ({
        ...model,
        testing: false
      }))
    } else if (data.embedding) {
      // 兼容旧格式：单个向量化模型配置
      embeddingModelList.value = [{
        id: 1,
        name: data.embedding.name || '默认向量化模型',
        provider: data.embedding.providerType || (data.embedding.provider === 'openai' && data.embedding.apiUrl && data.embedding.apiUrl.includes('localhost') ? 'vllm' : data.embedding.provider) || 'openai',
        apiUrl: data.embedding.apiUrl || '',
        apiKey: data.embedding.apiKey || '',
        model: data.embedding.model || '',
        timeout: data.embedding.timeout || 300000,
        batchSize: data.embedding.batchSize || 100,
        enabled: true,
        isDefault: true,
        testing: false
      }]
    } else {
      embeddingModelList.value = []
    }
  } catch (error) {
    console.warn('加载模型配置失败，使用默认值:', error)
  } finally {
    loading.qa = false
    loading.embedding = false
  }
}

// 添加模型
const handleAddModel = () => {
  // 重置表单
  Object.assign(currentModel, {
    id: null,
    name: '',
    provider: 'openai',
    providerType: '',
    apiUrl: '',
    apiKey: '',
    model: '',
    useFor: 'both',
    enabled: true,
    supportsMultimodal: false,
    supportsVision: false
  })
  dialogTitle.value = '添加问答模型'
  currentModel.id = null
  currentModel.name = ''
  currentModel.provider = 'openai'
  currentModel.providerType = ''
  currentModel.apiUrl = ''
  currentModel.apiKey = ''
  currentModel.model = ''
  currentModel.useFor = 'both'
  currentModel.enabled = true
  currentModel.supportsMultimodal = false
  currentModel.supportsVision = false
  dialogVisible.value = true
}

// 编辑模型
const handleEditModel = (row) => {
  dialogTitle.value = '编辑问答模型'
  currentModel.id = row.id
  currentModel.name = row.name
  currentModel.provider = row.provider
  currentModel.providerType = row.providerType || ''
  currentModel.apiUrl = row.apiUrl
  currentModel.apiKey = row.apiKey || ''
  currentModel.model = row.model
  currentModel.useFor = row.useFor || 'both'
  currentModel.enabled = row.enabled !== false
  currentModel.supportsMultimodal = row.supportsMultimodal || false
  currentModel.supportsVision = row.supportsVision || false
  dialogVisible.value = true
}

// 保存模型
const handleSaveModel = async () => {
  if (!modelFormRef.value) return
  
  try {
    await modelFormRef.value.validate()
    
    saving.value = true
    
    const modelData = {
      id: currentModel.id,
      name: currentModel.name,
      provider: currentModel.provider === 'vllm' ? 'openai' : currentModel.provider,
      providerType: currentModel.provider,
      apiUrl: currentModel.apiUrl,
      apiKey: currentModel.apiKey,
      model: currentModel.model,
      useFor: currentModel.useFor,
      enabled: currentModel.enabled,
      supportsMultimodal: currentModel.supportsMultimodal || false,
      supportsVision: currentModel.supportsVision || false
    }
    
    if (currentModel.id) {
      // 更新
      const response = await updateModelConfig({
        action: 'update',
        model: modelData
      })
      
      const index = qaModelList.value.findIndex(m => m.id === currentModel.id)
      if (index !== -1) {
        qaModelList.value[index] = { ...response, testing: false }
      }
    } else {
      // 新增
      const response = await updateModelConfig({
        action: 'add',
        model: modelData
      })
      
      qaModelList.value.push({ ...response, testing: false })
    }
    
    ElMessage.success('保存成功')
    dialogVisible.value = false
  } catch (error) {
    if (error !== false) {
      ElMessage.error(error.response?.data?.error || error.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

// 删除模型
const handleDeleteModel = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除模型 "${row.name}" 吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await updateModelConfig({
      action: 'delete',
      modelId: row.id
    })
    
    const index = qaModelList.value.findIndex(m => m.id === row.id)
    if (index !== -1) {
      qaModelList.value.splice(index, 1)
    }
    
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.error || error.message || '删除失败')
    }
  }
}

// 设置默认模型
const handleSetDefault = async (row) => {
  try {
    await updateModelConfig({
      action: 'setDefault',
      modelId: row.id,
      useFor: row.useFor
    })
    
    // 根据使用场景设置默认模型
    qaModelList.value.forEach(model => {
      if (row.useFor === 'both') {
        // 如果设置为两者都使用，则清除所有默认标记
        if (model.id === row.id) {
          model.isDefault = true
        } else {
          model.isDefault = false
        }
      } else {
        // 如果只用于某个场景，只在该场景下设置默认
        if (model.id === row.id && model.useFor === row.useFor) {
          model.isDefault = true
        } else if (model.useFor === row.useFor) {
          model.isDefault = false
        }
      }
    })
    
    ElMessage.success('设置默认模型成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '设置失败')
  }
}

// 切换启用状态
const handleToggleEnabled = async (row, newEnabled) => {
  try {
    // 如果禁用的是默认模型，给出提示
    if (!newEnabled && row.isDefault) {
      ElMessage.warning('正在禁用默认模型，系统将自动取消其默认状态')
    }
    
    await updateModelConfig({
      action: 'toggleEnabled',
      modelId: row.id,
      enabled: newEnabled
    })
    
    row.enabled = newEnabled
    
    // 如果禁用了默认模型，取消其默认状态并刷新列表
    if (!newEnabled && row.isDefault) {
      row.isDefault = false
      // 重新加载模型列表以获取新的默认模型
      await loadQAModels()
    }
    
    ElMessage.success(newEnabled ? '模型已启用' : '模型已禁用')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '操作失败')
    // 恢复原状态（newEnabled 是新值，所以恢复就是取反）
    row.enabled = !newEnabled
  }
}

// 处理操作命令（问答模型）
const handleActionCommand = (command, row) => {
  if (command === 'delete') {
    handleDeleteModel(row)
  }
}

// 处理操作命令（向量化模型）
const handleEmbeddingActionCommand = (command, row) => {
  if (command === 'delete') {
    handleDeleteEmbeddingModel(row)
  }
}

// 测试模型连接
const handleTestModel = async (row) => {
  row.testing = true
  try {
    const testData = {
      type: 'qa',
      provider: row.provider === 'vllm' ? 'openai' : row.provider,
      providerType: row.provider,
      apiUrl: row.apiUrl,
      apiKey: row.apiKey,
      model: row.model
    }
    
    await testModelConnection(testData)
    ElMessage.success('连接测试成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '连接测试失败')
  } finally {
    row.testing = false
  }
}

// 添加向量化模型
const handleAddEmbeddingModel = () => {
  embeddingDialogTitle.value = '添加向量化模型'
  currentEmbeddingModel.id = null
  currentEmbeddingModel.name = ''
  currentEmbeddingModel.provider = 'openai'
  currentEmbeddingModel.apiUrl = ''
  currentEmbeddingModel.apiKey = ''
  currentEmbeddingModel.model = ''
  currentEmbeddingModel.timeout = 300000
  currentEmbeddingModel.batchSize = 100
  currentEmbeddingModel.enabled = true
  embeddingDialogVisible.value = true
}

// 编辑向量化模型
const handleEditEmbeddingModel = (row) => {
  embeddingDialogTitle.value = '编辑向量化模型'
  currentEmbeddingModel.id = row.id
  currentEmbeddingModel.name = row.name
  currentEmbeddingModel.provider = row.provider
  currentEmbeddingModel.apiUrl = row.apiUrl
  currentEmbeddingModel.apiKey = row.apiKey || ''
  currentEmbeddingModel.model = row.model
  currentEmbeddingModel.timeout = row.timeout || 300000
  currentEmbeddingModel.batchSize = row.batchSize || 100
  currentEmbeddingModel.enabled = row.enabled !== false
  embeddingDialogVisible.value = true
}

// 保存向量化模型
const handleSaveEmbeddingModel = async () => {
  if (!embeddingModelFormRef.value) return
  
  try {
    await embeddingModelFormRef.value.validate()
    
    saving.value = true
    
    const modelData = {
      id: currentEmbeddingModel.id,
      name: currentEmbeddingModel.name,
      provider: currentEmbeddingModel.provider === 'vllm' ? 'openai' : currentEmbeddingModel.provider,
      providerType: currentEmbeddingModel.provider,
      apiUrl: currentEmbeddingModel.apiUrl,
      apiKey: currentEmbeddingModel.apiKey,
      model: currentEmbeddingModel.model,
      timeout: currentEmbeddingModel.timeout,
      batchSize: currentEmbeddingModel.batchSize,
      enabled: currentEmbeddingModel.enabled
    }
    
    if (currentEmbeddingModel.id) {
      // 更新
      const response = await updateModelConfig({
        action: 'update',
        type: 'embedding',
        model: modelData
      })
      
      const index = embeddingModelList.value.findIndex(m => m.id === currentEmbeddingModel.id)
      if (index !== -1) {
        embeddingModelList.value[index] = { ...response, testing: false }
      }
    } else {
      // 新增
      const response = await updateModelConfig({
        action: 'add',
        type: 'embedding',
        model: modelData
      })
      
      embeddingModelList.value.push({ ...response, testing: false })
    }
    
    ElMessage.success('保存成功')
    embeddingDialogVisible.value = false
  } catch (error) {
    if (error !== false) {
      ElMessage.error(error.response?.data?.error || error.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

// 删除向量化模型
const handleDeleteEmbeddingModel = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除向量化模型 "${row.name}" 吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await updateModelConfig({
      action: 'delete',
      type: 'embedding',
      modelId: row.id
    })
    
    const index = embeddingModelList.value.findIndex(m => m.id === row.id)
    if (index !== -1) {
      embeddingModelList.value.splice(index, 1)
    }
    
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.error || error.message || '删除失败')
    }
  }
}

// 设置默认向量化模型
const handleSetDefaultEmbedding = async (row) => {
  try {
    await updateModelConfig({
      action: 'setDefault',
      type: 'embedding',
      modelId: row.id
    })
    
    embeddingModelList.value.forEach(model => {
      model.isDefault = model.id === row.id
    })
    
    ElMessage.success('设置默认模型成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '设置失败')
  }
}

// 切换向量化模型启用状态
const handleToggleEnabledEmbedding = async (row, newEnabled) => {
  try {
    // 如果禁用的是默认模型，给出提示
    if (!newEnabled && row.isDefault) {
      ElMessage.warning('正在禁用默认模型，系统将自动取消其默认状态')
    }
    
    await updateModelConfig({
      action: 'toggleEnabled',
      type: 'embedding',
      modelId: row.id,
      enabled: newEnabled
    })
    
    row.enabled = newEnabled
    
    // 如果禁用了默认模型，取消其默认状态并刷新列表
    if (!newEnabled && row.isDefault) {
      row.isDefault = false
      // 重新加载模型列表以获取新的默认模型
      await loadEmbeddingModels()
    }
    
    ElMessage.success(newEnabled ? '模型已启用' : '模型已禁用')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '操作失败')
    // 恢复原状态（newEnabled 是新值，所以恢复就是取反）
    row.enabled = !newEnabled
  }
}

// 测试向量化模型连接
const handleTestEmbeddingModel = async (row) => {
  row.testing = true
  try {
    const testData = {
      type: 'embedding',
      provider: row.provider === 'vllm' ? 'openai' : row.provider,
      providerType: row.provider,
      apiUrl: row.apiUrl,
      apiKey: row.apiKey,
      model: row.model
    }
    
    await testModelConnection(testData)
    ElMessage.success('连接测试成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '连接测试失败')
  } finally {
    row.testing = false
  }
}

// 监听多模态开关，当关闭时自动关闭视觉输入
watch(() => currentModel.supportsMultimodal, (newVal) => {
  if (!newVal) {
    currentModel.supportsVision = false
  }
})

onMounted(() => {
  loadConfig()
  loadPrompts()
})

// 提示词管理相关函数
const loadPrompts = async () => {
  loading.prompt = true
  try {
    const keyword = promptSearchKeyword.value ? promptSearchKeyword.value.trim() : null
    const data = await getPrompts(keyword)
    prompts.value = data || []
  } catch (error) {
    console.error('加载提示词列表失败', error)
    ElMessage.error('加载提示词列表失败')
    prompts.value = []
  } finally {
    loading.prompt = false
  }
}

const handlePromptSearch = () => {
  if (promptSearchTimer) {
    clearTimeout(promptSearchTimer)
  }
  promptSearchTimer = setTimeout(() => {
    loadPrompts()
  }, 300)
}

const handleCreatePrompt = () => {
  promptIsEdit.value = false
  promptCurrentEditId.value = null
  promptFormData.value = {
    title: '',
    content: ''
  }
  promptDialogVisible.value = true
}

const handleEditPrompt = (row) => {
  promptIsEdit.value = true
  promptCurrentEditId.value = row.id
  promptFormData.value = {
    title: row.title,
    content: row.content
  }
  promptDialogVisible.value = true
}

const handleSubmitPrompt = async () => {
  if (!promptFormRef.value) return
  
  try {
    await promptFormRef.value.validate()
  } catch (error) {
    return
  }

  promptSaving.value = true
  try {
    if (promptIsEdit.value) {
      await updatePrompt(promptCurrentEditId.value, promptFormData.value)
      ElMessage.success('更新提示词成功')
    } else {
      await createPrompt(promptFormData.value)
      ElMessage.success('创建提示词成功')
    }
    promptDialogVisible.value = false
    loadPrompts()
  } catch (error) {
    console.error('保存提示词失败', error)
    const errorMessage = error.response?.data?.error || error.message || '保存提示词失败'
    ElMessage.error(errorMessage)
  } finally {
    promptSaving.value = false
  }
}

const handleDeletePrompt = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该提示词吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await deletePrompt(id)
    ElMessage.success('删除提示词成功')
    loadPrompts()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除提示词失败', error)
      const errorMessage = error.response?.data?.error || error.message || '删除提示词失败'
      ElMessage.error(errorMessage)
    }
  }
}

const formatPromptTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<style scoped>
.model-management {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
}

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: 18px 20px;
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 20px;
  min-height: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

:deep(.el-tabs) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px;
  position: relative;
}

/* 选项卡切换过渡动画 */
:deep(.el-tab-pane) {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

:deep(.el-tab-pane.is-active) {
  animation: tabFadeIn 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: opacity, transform; /* 硬件加速 */
}

@keyframes tabFadeIn {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.model-list-section {
  width: 100%;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  gap: 12px;
}

.config-section {
  max-width: 800px;
}

.form-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

.form-tip .el-icon {
  font-size: 14px;
  color: #409eff;
}

:deep(.el-form-item) {
  margin-bottom: 24px;
}

:deep(.el-input-group__prepend) {
  background-color: #f5f7fa;
  color: #909399;
  border-right: none;
}

:deep(.el-input__wrapper) {
  border-left: none;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.action-buttons .el-button {
  margin: 0;
}

.action-buttons .el-dropdown {
  margin-left: 0;
}

/* 高亮默认模型行 */
:deep(.el-table .default-model-row) {
  background-color: #ecf5ff !important;
}

:deep(.el-table .default-model-row:hover) {
  background-color: #d4e8ff !important;
}

/* 确保条纹表格中默认模型行也能正确高亮 */
:deep(.el-table--striped .default-model-row.el-table__row--striped) {
  background-color: #ecf5ff !important;
}

:deep(.el-table--striped .default-model-row.el-table__row--striped:hover) {
  background-color: #d4e8ff !important;
}

/* 隐藏单选按钮的 label 文本 */
:deep(.el-table .el-radio__label) {
  display: none !important;
  padding: 0 !important;
  width: 0 !important;
}

/* 提示词管理样式 */
.prompt-section {
  padding: 0;
}

.prompt-section .section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  gap: 12px;
}

.prompt-section .table-container {
  margin-top: 20px;
}

.content-preview {
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
