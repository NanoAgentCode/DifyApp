<template>
  <div class="system-config">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>系统配置管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            添加配置
          </el-button>
        </div>
      </template>

      <!-- 搜索 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索配置项"
          clearable
          style="width: 300px"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- 按功能分组展示配置 -->
      <div v-loading="loading" class="config-groups">
        <div v-if="Object.keys(groupedConfigs).length === 0" class="empty-state">
          <el-empty description="暂无配置项" />
        </div>

        <div
          v-for="(configs, groupKey) in groupedConfigs"
          :key="groupKey"
          class="config-group-section"
        >
          <!-- 分组标题 -->
          <div class="group-header">
            <div class="group-title">
              <el-icon class="group-icon">
                <component :is="getGroupIcon(groupKey)" />
              </el-icon>
              <span>{{ getGroupLabel(groupKey) }}</span>
              <el-tag size="small" type="info" style="margin-left: 10px">
                {{ configs.length }} 项
              </el-tag>
            </div>
            <el-button
              size="small"
              @click="toggleGroupExpand(groupKey)"
              :icon="isGroupExpanded(groupKey) ? 'ArrowUp' : 'ArrowDown'"
            >
              {{ isGroupExpanded(groupKey) ? '收起' : '展开' }}
            </el-button>
          </div>

          <!-- 配置列表 -->
          <el-collapse-transition>
            <div v-show="isGroupExpanded(groupKey)" class="group-content">
              <el-table
                :data="configs"
                stripe
                border
                style="width: 100%"
              >
                <el-table-column prop="configKey" label="配置键" min-width="200" show-overflow-tooltip align="center"/>
                <el-table-column prop="configValue" label="配置值" min-width="120" show-overflow-tooltip align="center">
                  <template #default="{ row }">
                    <span v-if="isGlobalThemeConfigKey(row.configKey)" class="theme-value-display">
                      {{ getThemeDisplayValue(row.configValue) }}
                    </span>
                    <span v-else-if="row.configValue && row.configValue.length > 50">
                      {{ row.configValue.substring(0, 50) }}...
                    </span>
                    <span v-else>{{ row.configValue || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="configType" label="类型" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.configType" size="small" type="info">{{ row.configType }}</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
                <el-table-column label="操作" width="180" fixed="right" align="center">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
                    <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-collapse-transition>
        </div>
      </div>
    </el-card>

    <!-- 添加/编辑配置对话框 -->
    <el-dialog
      v-model="showDialog"
      :title="editingConfig ? '编辑配置' : '添加配置'"
      width="600px"
    >
      <el-form :model="form" label-width="120px" ref="formRef">
        <el-form-item label="配置键" prop="configKey" :rules="[{ required: true, message: '请选择配置键' }]">
          <el-select
            v-model="form.configKey"
            :placeholder="editingConfig ? '当前配置键' : availableConfigKeys.length === 0 ? '所有配置键已配置' : '请选择配置键'"
            style="width: 100%"
            :disabled="!!editingConfig || availableConfigKeys.length === 0"
            filterable
            @change="handleConfigKeyChange"
          >
            <el-option
              v-for="item in availableConfigKeys"
              :key="item.key"
              :label="item.label"
              :value="item.key"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>{{ item.label }}</span>
                <span style="color: #909399; font-size: 12px; margin-left: 10px;">{{ item.description }}</span>
              </div>
            </el-option>
            <el-option v-if="!editingConfig && availableConfigKeys.length === 0" disabled value="">
              <span style="color: #909399;">所有预定义配置键已配置</span>
            </el-option>
          </el-select>
        </el-form-item>
        <!-- 全局主题色配置特殊处理 -->
        <el-form-item 
          v-if="isGlobalThemeConfig" 
          label="主题" 
          prop="configValue"
        >
          <div class="theme-selector">
            <el-radio-group v-model="selectedTheme" @change="handleThemeChange" class="theme-grid">
              <el-radio 
                v-for="theme in industrialThemes" 
                :key="theme.id" 
                :label="theme.id"
                class="theme-card"
              >
                <div class="theme-preview-card">
                  <div class="theme-header">
                    <div class="theme-name">{{ theme.name }}</div>
                    <div class="theme-check-icon" v-if="selectedTheme === theme.id">
                      <el-icon><Check /></el-icon>
                    </div>
                  </div>
                  <div class="theme-colors-preview">
                    <div 
                      class="color-dot primary" 
                      :style="{ backgroundColor: theme.colors.primary }"
                      :title="`主色: ${theme.colors.primary}`"
                    ></div>
                    <div 
                      class="color-dot secondary" 
                      :style="{ backgroundColor: theme.colors.secondary }"
                      :title="`次色: ${theme.colors.secondary}`"
                    ></div>
                    <div 
                      class="color-dot accent" 
                      :style="{ backgroundColor: theme.colors.accent }"
                      :title="`强调色: ${theme.colors.accent}`"
                    ></div>
                  </div>
                  <div class="theme-info">
                    <div class="theme-desc">{{ theme.description }}</div>
                  </div>
                </div>
              </el-radio>
            </el-radio-group>
          </div>
        </el-form-item>
        <!-- 文档解读模型ID配置特殊处理 -->
        <el-form-item 
          v-else-if="isDocumentReaderModelConfig"
          label="模型" 
          prop="configValue"
        >
          <el-select
            v-model="form.configValue"
            placeholder="请选择模型"
            style="width: 100%"
            filterable
            :loading="loadingModels"
            @focus="loadQAModels"
          >
            <el-option
              v-for="model in qaModels"
              :key="model.id"
              :label="`${model.name} (ID: ${model.id})`"
              :value="String(model.id)"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>{{ model.name }}</span>
                <span style="color: #909399; font-size: 12px;">ID: {{ model.id }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <!-- 其他模型ID配置（help.modelId、drawio.defaultModelId） -->
        <el-form-item 
          v-else-if="isModelIdConfig"
          label="模型" 
          prop="configValue"
        >
          <el-select
            v-model="form.configValue"
            placeholder="请选择模型"
            style="width: 100%"
            filterable
            :loading="loadingModels"
            @focus="loadQAModels"
          >
            <el-option
              v-for="model in qaModels"
              :key="model.id"
              :label="`${model.name} (ID: ${model.id})`"
              :value="String(model.id)"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>{{ model.name }}</span>
                <span style="color: #909399; font-size: 12px;">ID: {{ model.id }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <!-- 知识库ID配置 -->
        <el-form-item 
          v-else-if="isKnowledgeBaseIdConfig"
          label="知识库" 
          prop="configValue"
        >
          <el-select
            v-model="form.configValue"
            placeholder="请选择知识库"
            style="width: 100%"
            filterable
            :loading="loadingKnowledgeBases"
            @focus="loadKnowledgeBases"
          >
            <el-option
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :label="`${kb.name} (ID: ${kb.id})`"
              :value="String(kb.id)"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>{{ kb.name }}</span>
                <span style="color: #909399; font-size: 12px;">ID: {{ kb.id }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <!-- 文档解读向量库实例ID配置特殊处理 -->
        <el-form-item 
          v-else-if="isDocumentReaderVectorDatabaseIdConfig"
          label="向量库实例" 
          prop="configValue"
        >
          <el-select
            v-model="form.configValue"
            placeholder="请选择向量库实例"
            style="width: 100%"
            filterable
            :loading="loadingVectorDatabases"
            @focus="loadVectorDatabases"
          >
            <el-option
              v-for="db in vectorDatabases"
              :key="db.id"
              :label="`${db.name} (ID: ${db.id}, 类型: ${db.type})`"
              :value="String(db.id)"
              :disabled="!db.enabled"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>{{ db.name }}</span>
                <span style="color: #909399; font-size: 12px;">
                  ID: {{ db.id }} | {{ db.type }}
                  <el-tag v-if="!db.enabled" size="small" type="info" style="margin-left: 5px;">已禁用</el-tag>
                </span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <!-- 用户行为日志Elasticsearch数据源ID配置特殊处理 -->
        <el-form-item
          v-else-if="isUserLogElasticsearchDataSourceIdConfig"
          label="Elasticsearch数据源"
          prop="configValue"
        >
          <el-select
            v-model="form.configValue"
            placeholder="请选择Elasticsearch数据源"
            style="width: 100%"
            filterable
            :loading="loadingElasticsearchDataSources"
            @focus="loadElasticsearchDataSources"
          >
            <el-option
              v-for="ds in elasticsearchDataSources"
              :key="ds.id"
              :label="`${ds.name} (ID: ${ds.id})`"
              :value="String(ds.id)"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>{{ ds.name }}</span>
                <span style="color: #909399; font-size: 12px;">ID: {{ ds.id }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <!-- 数据分析Neo4j数据源ID配置特殊处理 -->
        <el-form-item
          v-else-if="isDataAnalysisNeo4jDataSourceIdConfig"
          label="Neo4j数据源"
          prop="configValue"
        >
          <el-select
            v-model="form.configValue"
            placeholder="请选择Neo4j数据源"
            style="width: 100%"
            filterable
            :loading="loadingNeo4jDataSources"
            @focus="loadNeo4jDataSources"
          >
            <el-option
              v-for="ds in neo4jDataSources"
              :key="ds.id"
              :label="`${ds.name} (ID: ${ds.id})`"
              :value="String(ds.id)"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>{{ ds.name }}</span>
                <span style="color: #909399; font-size: 12px;">ID: {{ ds.id }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <!-- 文档解读思维导图服务URL配置特殊处理 -->
        <el-form-item 
          v-else-if="isDocumentReaderMindMapServiceUrlConfig"
          label="思维导图服务URL" 
          prop="configValue"
        >
          <el-input
            v-model="form.configValue"
            placeholder="请输入思维导图服务URL，例如: http://localhost:6066"
            clearable
          />
          <div style="margin-top: 8px; color: #909399; font-size: 12px;">
            <p>配置思维导图外部服务的URL地址，用于生成文档思维导图。</p>
            <p>默认值: http://localhost:6066</p>
          </div>
        </el-form-item>
        <!-- 文档解读向量库类型配置特殊处理 -->
        <el-form-item 
          v-else-if="isDocumentReaderVectorStoreTypeConfig"
          label="向量库类型" 
          prop="configValue"
        >
          <el-select
            v-model="form.configValue"
            placeholder="请选择向量库类型"
            style="width: 100%"
          >
            <el-option label="Qdrant" value="qdrant" />
            <el-option label="FAISS" value="faiss" />
            <el-option label="Milvus" value="milvus" />
            <el-option label="Chroma" value="chroma" />
            <el-option label="Weaviate" value="weaviate" />
            <el-option label="Elasticsearch" value="elasticsearch" />
            <el-option label="PgVector" value="pgvector" />
          </el-select>
        </el-form-item>
        <!-- 布尔类型配置特殊处理 -->
        <el-form-item 
          v-else-if="form.configType === 'boolean'"
          label="配置值" 
          prop="configValue"
        >
          <el-select
            v-model="form.configValue"
            placeholder="请选择布尔值"
            style="width: 100%"
          >
            <el-option label="true" value="true" />
            <el-option label="false" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item 
          v-else
          label="配置值" 
          prop="configValue"
        >
          <el-input
            v-model="form.configValue"
            type="textarea"
            :rows="4"
            placeholder="请输入配置值（可以是JSON格式）"
          />
        </el-form-item>
        <el-form-item label="配置分组" prop="configGroup">
          <el-select
            v-model="form.configGroup"
            placeholder="请选择配置分组"
            style="width: 100%"
            filterable
            allow-create
            default-first-option
          >
            <el-option
              v-for="group in availableGroups"
              :key="group.value"
              :label="group.label"
              :value="group.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="配置类型" prop="configType">
          <el-select v-model="form.configType" placeholder="请选择配置类型" style="width: 100%">
            <el-option label="字符串" value="string" />
            <el-option label="数字" value="number" />
            <el-option label="布尔值" value="boolean" />
            <el-option label="JSON" value="json" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="请输入配置描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Check, Document, Setting, Grid, Picture, Monitor, Files, DataAnalysis } from '@element-plus/icons-vue'
import { getAllConfigs, setOrUpdateConfig, deleteConfig } from '@/api/systemConfig'
import { industrialThemes, getThemeById } from '@/utils/themes'
import { GLOBAL_THEME_CONFIG_KEY } from '@/utils/globalTheme'
import { applyGlobalTheme } from '@/utils/globalTheme'
import { getAvailableQAModels } from '@/api/model'
import { getVectorDatabaseList } from '@/api/vectorDatabase'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { getDataSourceList } from '@/api/dataSource'

// 预定义配置键列表
const predefinedConfigKeys = [
  {
    key: 'help.knowledgeBaseId',
    label: 'help.knowledgeBaseId',
    description: '用户手册知识库ID',
    group: 'help',
    type: 'number'
  },
  {
    key: 'help.modelId',
    label: 'help.modelId',
    description: '用户手册模型ID',
    group: 'help',
    type: 'number'
  },
  {
    key: 'drawio.defaultModelId',
    label: 'drawio.defaultModelId',
    description: '智能框图模型ID',
    group: 'system',
    type: 'number'
  },
  {
    key: 'system.globalTheme',
    label: 'system.globalTheme',
    description: '全局主题色',
    group: 'system',
    type: 'string'
  },
  {
    key: 'userlog.elasticsearchDataSourceId',
    label: 'userlog.elasticsearchDataSourceId',
    description: '用户行为日志Elasticsearch数据源ID',
    group: 'userActionLog',
    type: 'number'
  },
  {
    key: 'analysis.neo4j.dataSourceId',
    label: 'analysis.neo4j.dataSourceId',
    description: '数据分析Neo4j数据源ID',
    group: 'dataAnalysis',
    type: 'number'
  },
  {
    key: 'analysis.etl.enabled',
    label: 'analysis.etl.enabled',
    description: '数据分析ETL是否启用',
    group: 'dataAnalysis',
    type: 'boolean'
  },
  {
    key: 'analysis.etl.intervalMinutes',
    label: 'analysis.etl.intervalMinutes',
    description: '数据分析ETL同步间隔（分钟）',
    group: 'dataAnalysis',
    type: 'number'
  },
  {
    key: 'dify.api.defaultBaseUrl',
    label: 'dify.api.defaultBaseUrl',
    description: 'Dify API Base URL',
    group: 'dify',
    type: 'string'
  },
  {
    key: 'ocr.service.url',
    label: 'ocr.service.url',
    description: 'EasyOCR服务地址',
    group: 'ocr',
    type: 'string'
  },
  {
    key: 'dify.api.fileUrlPrefix',
    label: 'dify.api.fileUrlPrefix',
    description: 'Dify API 文件URL前缀',
    group: 'dify',
    type: 'string'
  },
  {
    key: 'documentReader.defaultQAModelId',
    label: 'documentReader.defaultQAModelId',
    description: '文档解读问答模型ID',
    group: 'documentReader',
    type: 'number'
  },
  {
    key: 'documentReader.defaultEmbeddingModelId',
    label: 'documentReader.defaultEmbeddingModelId',
    description: '文档解读向量化模型ID',
    group: 'documentReader',
    type: 'number'
  },
  {
    key: 'documentReader.vectorDatabaseId',
    label: 'documentReader.vectorDatabaseId',
    description: '文档解读向量库实例ID',
    group: 'documentReader',
    type: 'number'
  },
  {
    key: 'documentReader.vectorStoreType',
    label: 'documentReader.vectorStoreType',
    description: '文档解读向量库类型',
    group: 'documentReader',
    type: 'string'
  },
  {
    key: 'documentReader.topK',
    label: 'documentReader.topK',
    description: '文档解读Top-K数量',
    group: 'documentReader',
    type: 'number'
  },
  {
    key: 'documentReader.mindMapServiceUrl',
    label: 'documentReader.mindMapServiceUrl',
    description: '文档解读思维导图外部服务URL（默认: http://localhost:6066）',
    group: 'documentReader',
    type: 'string'
  }
]

const loading = ref(false)
const saving = ref(false)
const configList = ref([])
const searchKeyword = ref('')
const showDialog = ref(false)
const editingConfig = ref(null)
const formRef = ref(null)
const expandedGroups = ref(new Set()) // 默认收起所有分组

const form = ref({
  configKey: '',
  configValue: '',
  configGroup: '',
  configType: '',
  description: ''
})

// 主题相关
const selectedTheme = ref('element') // 默认使用饿了么蓝

// 模型相关
const qaModels = ref([])
const loadingModels = ref(false)

// 向量库相关
const vectorDatabases = ref([])
const loadingVectorDatabases = ref(false)

// Elasticsearch数据源相关
const elasticsearchDataSources = ref([])
const loadingElasticsearchDataSources = ref(false)

// Neo4j数据源相关
const neo4jDataSources = ref([])
const loadingNeo4jDataSources = ref(false)

// 知识库相关
const knowledgeBases = ref([])
const loadingKnowledgeBases = ref(false)

// 获取已配置的配置键集合
const configuredKeys = computed(() => {
  return new Set(configList.value.map(config => config.configKey))
})

// 获取可用的配置键列表（过滤掉已配置的）
const availableConfigKeys = computed(() => {
  if (editingConfig.value) {
    // 编辑模式下，显示当前配置键（即使不是预定义的也显示）
    const currentKey = editingConfig.value.configKey
    const predefined = predefinedConfigKeys.find(item => item.key === currentKey)
    if (predefined) {
      return [predefined]
    }
    // 如果不是预定义的配置键，创建一个临时项用于显示
    return [{
      key: currentKey,
      label: currentKey,
      description: '自定义配置键',
      group: editingConfig.value.configGroup || '',
      type: editingConfig.value.configType || 'string'
    }]
  } else {
    // 添加模式下，过滤掉已配置的键
    return predefinedConfigKeys.filter(item => !configuredKeys.value.has(item.key))
  }
})

// 判断是否是全局主题色配置
const isGlobalThemeConfig = computed(() => {
  return form.value.configKey === GLOBAL_THEME_CONFIG_KEY
})

// 判断是否是文档解读模型配置
const isDocumentReaderModelConfig = computed(() => {
  return form.value.configKey === 'documentReader.defaultQAModelId' || 
         form.value.configKey === 'documentReader.defaultEmbeddingModelId'
})

// 判断是否是模型ID配置（包括help.modelId和drawio.defaultModelId）
const isModelIdConfig = computed(() => {
  return form.value.configKey === 'help.modelId' || 
         form.value.configKey === 'drawio.defaultModelId'
})

// 判断是否是知识库ID配置
const isKnowledgeBaseIdConfig = computed(() => {
  return form.value.configKey === 'help.knowledgeBaseId'
})

// 判断是否是文档解读向量库类型配置
const isDocumentReaderVectorStoreTypeConfig = computed(() => {
  return form.value.configKey === 'documentReader.vectorStoreType'
})

// 判断是否是文档解读向量库实例ID配置
const isDocumentReaderVectorDatabaseIdConfig = computed(() => {
  return form.value.configKey === 'documentReader.vectorDatabaseId'
})

// 判断是否是用户行为日志Elasticsearch数据源ID配置
const isUserLogElasticsearchDataSourceIdConfig = computed(() => {
  return form.value.configKey === 'userlog.elasticsearchDataSourceId'
})

// 判断是否是数据分析Neo4j数据源ID配置
const isDataAnalysisNeo4jDataSourceIdConfig = computed(() => {
  return form.value.configKey === 'analysis.neo4j.dataSourceId'
})

// 判断是否是文档解读思维导图服务URL配置
const isDocumentReaderMindMapServiceUrlConfig = computed(() => {
  return form.value.configKey === 'documentReader.mindMapServiceUrl'
})

// 加载问答模型列表
const loadQAModels = async () => {
  if (qaModels.value.length > 0 || loadingModels.value) {
    return // 已经加载过或正在加载
  }
  
  loadingModels.value = true
  try {
    const response = await getAvailableQAModels()
    qaModels.value = response || []
  } catch (error) {
    console.error('加载模型列表失败:', error)
    ElMessage.warning('加载模型列表失败，请手动输入模型ID')
  } finally {
    loadingModels.value = false
  }
}

// 加载向量库列表
const loadVectorDatabases = async () => {
  if (vectorDatabases.value.length > 0 || loadingVectorDatabases.value) {
    return // 已经加载过或正在加载
  }

  loadingVectorDatabases.value = true
  try {
    const response = await getVectorDatabaseList()
    vectorDatabases.value = Array.isArray(response) ? response : (response?.data || response || [])
  } catch (error) {
    console.error('加载向量库列表失败:', error)
    ElMessage.warning('加载向量库列表失败，请手动输入向量库ID')
  } finally {
    loadingVectorDatabases.value = false
  }
}

// 加载Elasticsearch数据源列表
const loadElasticsearchDataSources = async () => {
  if (elasticsearchDataSources.value.length > 0 || loadingElasticsearchDataSources.value) {
    return // 已经加载过或正在加载
  }

  loadingElasticsearchDataSources.value = true
  try {
    const response = await getDataSourceList({ type: 'elasticsearch', status: 1 })
    elasticsearchDataSources.value = Array.isArray(response) ? response : (response?.records || response?.data || response || [])
  } catch (error) {
    console.error('加载Elasticsearch数据源列表失败:', error)
    ElMessage.warning('加载Elasticsearch数据源列表失败，请手动输入数据源ID')
  } finally {
    loadingElasticsearchDataSources.value = false
  }
}

// 加载Neo4j数据源列表
const loadNeo4jDataSources = async () => {
  if (neo4jDataSources.value.length > 0 || loadingNeo4jDataSources.value) {
    return // 已经加载过或正在加载
  }

  loadingNeo4jDataSources.value = true
  try {
    const response = await getDataSourceList({ type: 'neo4j', status: 1 })
    neo4jDataSources.value = Array.isArray(response) ? response : (response?.records || response?.data || response || [])
  } catch (error) {
    console.error('加载Neo4j数据源列表失败:', error)
    ElMessage.warning('加载Neo4j数据源列表失败，请手动输入数据源ID')
  } finally {
    loadingNeo4jDataSources.value = false
  }
}

// 加载知识库列表
const loadKnowledgeBases = async () => {
  if (knowledgeBases.value.length > 0 || loadingKnowledgeBases.value) {
    return // 已经加载过或正在加载
  }
  
  loadingKnowledgeBases.value = true
  try {
    const response = await getKnowledgeBaseList({ status: 1 }) // 只加载启用的知识库
    knowledgeBases.value = Array.isArray(response) ? response : (response?.content || response?.data || response || [])
  } catch (error) {
    console.error('加载知识库列表失败:', error)
    ElMessage.warning('加载知识库列表失败，请手动输入知识库ID')
  } finally {
    loadingKnowledgeBases.value = false
  }
}

// 配置键变化处理
const handleConfigKeyChange = (configKey) => {
  const predefined = predefinedConfigKeys.find(item => item.key === configKey)
  if (predefined) {
    // 对于预定义配置键，强制使用正确的分组、类型和描述，确保匹配
    form.value.configGroup = predefined.group
    form.value.configType = predefined.type
    form.value.description = predefined.description
  }
}

// 监听配置键变化，初始化主题选择
watch(() => form.value.configKey, (newKey) => {
  if (newKey === GLOBAL_THEME_CONFIG_KEY) {
    // 自动设置配置分组为system
    if (!form.value.configGroup) {
      form.value.configGroup = 'system'
    }
    // 自动设置描述
    if (!form.value.description) {
      form.value.description = '全局主题色配置，从预设主题中选择'
    }
    
    // 解析当前配置值
    const currentValue = form.value.configValue
    if (currentValue) {
      if (currentValue.includes(':')) {
        const [themeId] = currentValue.split(':')
        selectedTheme.value = themeId
      } else {
        // 尝试查找主题
        const theme = getThemeById(currentValue)
        if (theme) {
          selectedTheme.value = currentValue
        } else {
          // 如果不是有效的主题ID，默认使用饿了么蓝
          selectedTheme.value = 'element'
        }
      }
    } else {
      // 如果没有配置，默认使用饿了么蓝
      selectedTheme.value = 'element'
    }
  }
}, { immediate: true })

// 功能分组配置映射
const groupConfigMap = {
  'help': {
    label: '帮助中心配置',
    icon: Document,
    order: 1,
    description: '用户手册和帮助系统相关配置'
  },
  'system': {
    label: '系统全局配置',
    icon: Setting,
    order: 2,
    description: '系统全局设置和主题配置'
  },
  'userActionLog': {
    label: '行为日志配置',
    icon: Monitor,
    order: 3,
    description: '用户行为日志和Elasticsearch相关配置'
  },
  'userlog': {
    label: '行为日志配置',
    icon: Monitor,
    order: 3,
    description: '用户行为日志和Elasticsearch相关配置'
  },
  'dataAnalysis': {
    label: '数据分析配置',
    icon: DataAnalysis,
    order: 4,
    description: '数据分析和Neo4j相关配置'
  },
  'analysis': {
    label: '数据分析配置',
    icon: DataAnalysis,
    order: 4,
    description: '数据分析和Neo4j相关配置'
  },
  'dify': {
    label: 'Dify集成配置',
    icon: Grid,
    order: 5,
    description: 'Dify平台集成相关配置'
  },
  'ocr': {
    label: 'OCR服务配置',
    icon: Picture,
    order: 6,
    description: 'OCR识别服务配置'
  },
  'documentReader': {
    label: '文档解读配置',
    icon: Files,
    order: 7,
    description: '智能文档解读功能配置'
  },
  'mindmap': {
    label: '思维导图配置',
    icon: Monitor,
    order: 8,
    description: '思维导图生成服务配置'
  }
}

// 获取分组显示名称
const getGroupLabel = (groupKey) => {
  return groupConfigMap[groupKey]?.label || groupKey
}

// 获取分组图标
const getGroupIcon = (groupKey) => {
  return groupConfigMap[groupKey]?.icon || Setting
}

// 判断分组是否展开
const isGroupExpanded = (groupKey) => {
  return expandedGroups.value.has(groupKey)
}

// 切换分组展开状态
const toggleGroupExpand = (groupKey) => {
  if (expandedGroups.value.has(groupKey)) {
    expandedGroups.value.delete(groupKey)
  } else {
    expandedGroups.value.add(groupKey)
  }
}

// 按功能分组配置
const groupedConfigs = computed(() => {
  const grouped = {}

  // 过滤配置列表
  let filteredList = configList.value
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    filteredList = filteredList.filter(config =>
      config.configKey.toLowerCase().includes(keyword) ||
      (config.description && config.description.toLowerCase().includes(keyword)) ||
      (config.configGroup && config.configGroup.toLowerCase().includes(keyword))
    )
  }

  // 按分组聚合
  filteredList.forEach(config => {
    const groupKey = config.configGroup || 'other'
    if (!grouped[groupKey]) {
      grouped[groupKey] = []
    }
    grouped[groupKey].push(config)
  })

  // 按分组排序
  const sortedGroups = {}
  Object.keys(grouped).sort((a, b) => {
    const orderA = groupConfigMap[a]?.order || 999
    const orderB = groupConfigMap[b]?.order || 999
    return orderA - orderB
  }).forEach(key => {
    sortedGroups[key] = grouped[key]
  })

  return sortedGroups
})

// 加载配置列表
const loadConfigs = async () => {
  loading.value = true
  try {
    const response = await getAllConfigs()
    configList.value = response || []
  } catch (error) {
    console.error('加载配置列表失败:', error)
    ElMessage.error('加载配置列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 判断是否是全局主题配置键
const isGlobalThemeConfigKey = (configKey) => {
  return configKey === GLOBAL_THEME_CONFIG_KEY
}

// 获取主题显示值
const getThemeDisplayValue = (configValue) => {
  if (!configValue) return '-'
  const [themeId] = configValue.split(':')
  const theme = getThemeById(themeId)
  return theme ? `${theme.name} (${themeId})` : configValue
}

// 添加配置
const handleAdd = () => {
  editingConfig.value = null
  form.value = {
    configKey: '',
    configValue: '',
    configGroup: '',
    configType: '',
    description: ''
  }
  selectedTheme.value = 'element' // 默认使用饿了么蓝
  showDialog.value = true
}

// 编辑配置
const handleEdit = (row) => {
  editingConfig.value = row
  form.value = {
    configKey: row.configKey,
    configValue: row.configValue || '',
    configGroup: row.configGroup || '',
    configType: row.configType || '',
    description: row.description || ''
  }
  
  // 如果是预定义配置键，强制使用正确的分组、类型和描述，确保匹配
  const predefined = predefinedConfigKeys.find(item => item.key === row.configKey)
  if (predefined) {
    form.value.configGroup = predefined.group
    form.value.configType = predefined.type
    form.value.description = predefined.description
  }
  
  // 如果是全局主题配置，初始化主题选择
  if (row.configKey === GLOBAL_THEME_CONFIG_KEY) {
    const currentValue = row.configValue || ''
    if (currentValue.includes(':')) {
      const [themeId] = currentValue.split(':')
      selectedTheme.value = themeId
    } else {
      const theme = getThemeById(currentValue)
      if (theme) {
        selectedTheme.value = currentValue
      } else {
        // 如果不是有效的主题ID，默认使用饿了么蓝
        selectedTheme.value = 'element'
      }
    }
  }
  
  showDialog.value = true
}

// 主题变化处理
const handleThemeChange = (themeId) => {
  const theme = getThemeById(themeId)
  if (theme) {
    // 保存主题ID和主色到configValue字段（格式：themeId:primaryColor）
    form.value.configValue = `${themeId}:${theme.colors.primary}`
  }
}

// 保存配置
const handleSave = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    saving.value = true
    
    // 如果是全局主题配置，确保使用正确的格式
    if (form.value.configKey === GLOBAL_THEME_CONFIG_KEY) {
      if (selectedTheme.value) {
        const theme = getThemeById(selectedTheme.value)
        if (theme) {
          form.value.configValue = `${selectedTheme.value}:${theme.colors.primary}`
        } else {
          // 如果主题不存在，默认使用饿了么蓝
          const defaultTheme = getThemeById('element')
          if (defaultTheme) {
            form.value.configValue = `element:${defaultTheme.colors.primary}`
          }
        }
      } else {
        // 如果没有选择主题，默认使用饿了么蓝
        const defaultTheme = getThemeById('element')
        if (defaultTheme) {
          form.value.configValue = `element:${defaultTheme.colors.primary}`
        }
      }
    }
    
    await setOrUpdateConfig({
      configKey: form.value.configKey,
      configValue: form.value.configValue,
      configGroup: form.value.configGroup || null,
      configType: form.value.configType || null,
      description: form.value.description || null
    })
    
    ElMessage.success(editingConfig.value ? '配置更新成功' : '配置添加成功')
    
    // 如果是全局主题配置，立即应用新主题
    if (form.value.configKey === GLOBAL_THEME_CONFIG_KEY) {
      applyGlobalTheme(form.value.configValue)
    }
    
    showDialog.value = false
    await loadConfigs()
  } catch (error) {
    if (error !== false) { // 表单验证失败会返回 false
      console.error('保存配置失败:', error)
      ElMessage.error('保存配置失败：' + (error.message || '未知错误'))
    }
  } finally {
    saving.value = false
  }
}

// 删除配置
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除配置 "${row.configKey}" 吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await deleteConfig(row.configKey)
      ElMessage.success('配置删除成功')
      await loadConfigs()
    } catch (error) {
      console.error('删除配置失败:', error)
      ElMessage.error('删除配置失败：' + (error.message || '未知错误'))
    }
  }).catch(() => {
    // 取消删除
  })
}

// 搜索处理
const handleSearch = () => {
  // 搜索功能通过groupedConfigs计算属性自动实现
  // 这里不需要额外处理
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped>
.system-config {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

/* 配置分组样式 */
.config-groups {
  margin-top: 20px;
}

.empty-state {
  padding: 40px 0;
  text-align: center;
}

.config-group-section {
  margin-bottom: 24px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  background: #fff;
}

.group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(to right, #f5f7fa, #ffffff);
  border-bottom: 1px solid #e4e7ed;
  transition: background 0.3s;
}

.group-header:hover {
  background: linear-gradient(to right, #ecf5ff, #ffffff);
}

.group-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.group-icon {
  margin-right: 8px;
  font-size: 18px;
  color: #409eff;
}

.group-content {
  padding: 0;
}

.theme-value-display {
  color: #409eff;
  font-weight: 500;
}

/* 主题选择器样式 */
.theme-selector {
  width: 100%;
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 8px;
  width: 100%;
}

.theme-card {
  margin: 0;
  height: auto;
}

.theme-preview-card {
  border: 2px solid #e4e7ed;
  border-radius: 4px;
  padding: 6px;
  cursor: pointer;
  transition: all 0.3s;
  background: #fff;
}

.theme-preview-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.theme-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.theme-name {
  font-weight: 600;
  font-size: 12px;
  color: #303133;
  line-height: 1.2;
}

.theme-check-icon {
  color: #409eff;
  font-size: 14px;
}

.theme-colors-preview {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
}

.color-dot {
  width: 24px;
  height: 24px;
  border-radius: 3px;
  border: 1px solid #e4e7ed;
  cursor: pointer;
  flex-shrink: 0;
}

.theme-info {
  margin-top: 2px;
}

.theme-desc {
  font-size: 10px;
  color: #909399;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.custom-color-section {
  display: flex;
  align-items: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}

/* 选中状态 */
:deep(.el-radio.is-checked .theme-preview-card) {
  border-color: #409eff;
  background: #ecf5ff;
}
</style>
