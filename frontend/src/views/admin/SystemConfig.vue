<template>
  <div class="system-config">
    <div class="page-header">
      <div>
        <h3>系统配置</h3>
        <p>维护跨模块共享配置，业务模块内的细项优先在对应功能页管理。</p>
      </div>
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>
        新增配置
      </el-button>
    </div>

    <div class="filter-panel">
      <el-input
        v-model="keyword"
        placeholder="搜索配置键、分组或描述"
        clearable
        class="filter-input"
        @input="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-segmented v-model="activeGroup" :options="groupOptions" class="group-segmented" @change="handleGroupChange" />
    </div>

    <div class="table-panel">
      <div class="table-header">
        <div>
          <span class="table-title">{{ activeGroup === 'all' ? '全部配置' : groupLabel(activeGroup) }}</span>
          <span class="table-subtitle">{{ total }} 项</span>
        </div>
      </div>
      <el-table :data="configList" v-loading="loading" stripe height="100%">
        <el-table-column prop="configKey" label="配置键" min-width="230" show-overflow-tooltip />
        <el-table-column prop="configGroup" label="分组" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="light" class="group-tag">{{ groupLabel(row.configGroup) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="配置值" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="value-text">{{ displayValue(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <span class="type-pill">{{ row.configType || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingConfig ? '编辑配置' : '新增配置'" width="620px" class="config-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="config-form">
        <el-form-item label="配置键" prop="configKey">
          <el-select
            v-if="!editingConfig"
            v-model="form.configKey"
            filterable
            allow-create
            default-first-option
            placeholder="选择预设配置或输入自定义配置键"
            style="width: 100%"
            @change="applyPreset"
          >
            <el-option
              v-for="item in availablePresets"
              :key="item.key"
              :label="`${item.key} - ${item.description}`"
              :value="item.key"
            />
          </el-select>
          <el-input v-else v-model="form.configKey" disabled />
        </el-form-item>

        <el-form-item label="配置值" prop="configValue">
          <el-select
            v-if="isThemeConfig"
            v-model="selectedTheme"
            placeholder="选择主题"
            style="width: 100%"
            @change="handleThemeChange"
          >
            <el-option
              v-for="theme in industrialThemes"
              :key="theme.id"
              :label="theme.name"
              :value="theme.id"
            >
              <div class="theme-option">
                <span>{{ theme.name }}</span>
                <div class="theme-swatches">
                  <span :style="{ background: theme.colors.primary }" />
                  <span :style="{ background: theme.colors.secondary }" />
                  <span :style="{ background: theme.colors.accent }" />
                </div>
              </div>
            </el-option>
          </el-select>

          <el-select
            v-else-if="isModelConfig"
            v-model="form.configValue"
            filterable
            clearable
            :loading="loadingModels"
            placeholder="选择模型，也可以直接输入 ID"
            style="width: 100%"
            @focus="loadModels"
          >
            <el-option
              v-for="model in qaModels"
              :key="model.id"
              :label="`${model.name} (ID: ${model.id})`"
              :value="String(model.id)"
            />
          </el-select>

          <el-select
            v-else-if="isKnowledgeBaseConfig"
            v-model="form.configValue"
            filterable
            clearable
            :loading="loadingKnowledgeBases"
            placeholder="选择知识库，也可以直接输入 ID"
            style="width: 100%"
            @focus="loadKnowledgeBases"
          >
            <el-option
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :label="`${kb.name} (ID: ${kb.id})`"
              :value="String(kb.id)"
            />
          </el-select>

          <el-select v-else-if="form.configType === 'boolean'" v-model="form.configValue" style="width: 100%">
            <el-option label="true" value="true" />
            <el-option label="false" value="false" />
          </el-select>

          <el-input
            v-else
            v-model="form.configValue"
            type="textarea"
            :rows="4"
            placeholder="请输入配置值"
          />
        </el-form-item>

        <el-form-item label="分组" prop="configGroup">
          <el-select
            v-model="form.configGroup"
            filterable
            allow-create
            default-first-option
            placeholder="请选择或输入分组"
            style="width: 100%"
          >
            <el-option v-for="group in editableGroups" :key="group.value" :label="group.label" :value="group.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="类型" prop="configType">
          <el-select v-model="form.configType" style="width: 100%">
            <el-option label="字符串" value="string" />
            <el-option label="数字" value="number" />
            <el-option label="布尔值" value="boolean" />
            <el-option label="JSON" value="json" />
          </el-select>
        </el-form-item>

        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入配置说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { deleteConfig, getAllConfigs, getConfigPage, setOrUpdateConfig } from '@/api/systemConfig'
import { getAvailableQAModels } from '@/api/model'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'
import { GLOBAL_THEME_CONFIG_KEY, applyGlobalTheme } from '@/utils/globalTheme'
import { getThemeById, industrialThemes } from '@/utils/themes'

const presets = [
  { key: GLOBAL_THEME_CONFIG_KEY, group: 'system', type: 'string', description: '全局主题' },
  { key: 'help.knowledgeBaseId', group: 'help', type: 'number', description: '帮助中心知识库' },
  { key: 'help.modelId', group: 'help', type: 'number', description: '帮助中心模型' },
  { key: 'ocr.service.url', group: 'ocr', type: 'string', description: 'OCR 服务地址' },
  { key: 'ocr.service.timeout', group: 'ocr', type: 'number', description: 'OCR 请求超时时间（毫秒）' },
  { key: 'documentReader.defaultQAModelId', group: 'documentReader', type: 'number', description: '文档解读默认问答模型' },
  { key: 'documentReader.defaultEmbeddingModelId', group: 'documentReader', type: 'number', description: '文档解读默认向量化模型' },
  { key: 'documentReader.mindMapServiceUrl', group: 'documentReader', type: 'string', description: '思维导图服务地址' },
  { key: 'drawio.defaultModelId', group: 'drawio', type: 'number', description: '智能框图默认模型' },
  { key: 'userlog.elasticsearchDataSourceId', group: 'userlog', type: 'number', description: '行为日志 Elasticsearch 数据源' },
  { key: 'observability.elasticsearchDataSourceId', group: 'observability', type: 'number', description: 'LLM 观测 Elasticsearch 数据源' },
  { key: 'analysis.neo4j.dataSourceId', group: 'analysis', type: 'number', description: '数据分析 Neo4j 数据源' }
]

const groupLabels = {
  all: '全部',
  system: '系统',
  help: '帮助',
  ocr: 'OCR',
  documentReader: '文档解读',
  drawio: '智能框图',
  userlog: '行为日志',
  observability: 'LLM观测',
  analysis: '数据分析',
  other: '其他'
}

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingConfig = ref(null)
const keyword = ref('')
const activeGroup = ref('all')
const configList = ref([])
const allConfigList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const formRef = ref(null)
const selectedTheme = ref('element')
const qaModels = ref([])
const knowledgeBases = ref([])
const loadingModels = ref(false)
const loadingKnowledgeBases = ref(false)

const form = ref({
  configKey: '',
  configValue: '',
  configGroup: 'system',
  configType: 'string',
  description: ''
})

const rules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }],
  configGroup: [{ required: true, message: '请输入配置分组', trigger: 'blur' }],
  configType: [{ required: true, message: '请选择配置类型', trigger: 'change' }]
}

let searchTimer = null

const configuredKeys = computed(() => new Set(allConfigList.value.map(item => item.configKey)))

const availablePresets = computed(() => presets.filter(item => !configuredKeys.value.has(item.key)))

const groupOptions = computed(() => {
  const groups = new Set(allConfigList.value.map(item => item.configGroup || 'other'))
  return [
    { label: '全部', value: 'all' },
    ...Array.from(groups).sort().map(group => ({ label: groupLabel(group), value: group }))
  ]
})

const editableGroups = computed(() => {
  const groups = new Set([...presets.map(item => item.group), ...allConfigList.value.map(item => item.configGroup || 'other')])
  return Array.from(groups).sort().map(group => ({ label: groupLabel(group), value: group }))
})

const isThemeConfig = computed(() => form.value.configKey === GLOBAL_THEME_CONFIG_KEY)
const isModelConfig = computed(() => ['help.modelId', 'drawio.defaultModelId', 'documentReader.defaultQAModelId', 'documentReader.defaultEmbeddingModelId'].includes(form.value.configKey))
const isKnowledgeBaseConfig = computed(() => form.value.configKey === 'help.knowledgeBaseId')

const groupLabel = group => groupLabels[group || 'other'] || group || '其他'

const displayValue = row => {
  if (!row.configValue) return '-'
  if (row.configKey === GLOBAL_THEME_CONFIG_KEY) {
    const [themeId] = row.configValue.split(':')
    const theme = getThemeById(themeId)
    return theme ? theme.name : row.configValue
  }
  return row.configValue.length > 80 ? `${row.configValue.slice(0, 80)}...` : row.configValue
}

const loadConfigs = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }
    if (activeGroup.value !== 'all') {
      params.configGroup = activeGroup.value
    }
    const response = await getConfigPage(params)
    configList.value = response?.content || []
    total.value = response?.total || 0

    const totalPages = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (currentPage.value > totalPages) {
      currentPage.value = totalPages
      await loadConfigs()
    }
  } catch (error) {
    ElMessage.error(error.message || '加载配置失败')
  } finally {
    loading.value = false
  }
}

const loadConfigMeta = async () => {
  try {
    allConfigList.value = await getAllConfigs()
  } catch (error) {
    allConfigList.value = []
  }
}

const refreshConfigs = async () => {
  await Promise.all([loadConfigs(), loadConfigMeta()])
}

const handleSearch = () => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => {
    currentPage.value = 1
    loadConfigs()
  }, 300)
}

const handleGroupChange = () => {
  currentPage.value = 1
  loadConfigs()
}

const handleSizeChange = size => {
  pageSize.value = size
  currentPage.value = 1
  loadConfigs()
}

const handlePageChange = page => {
  currentPage.value = page
  loadConfigs()
}

const loadModels = async () => {
  if (qaModels.value.length || loadingModels.value) return
  loadingModels.value = true
  try {
    qaModels.value = await getAvailableQAModels()
  } catch (error) {
    ElMessage.warning('模型列表加载失败，可手动输入 ID')
  } finally {
    loadingModels.value = false
  }
}

const loadKnowledgeBases = async () => {
  if (knowledgeBases.value.length || loadingKnowledgeBases.value) return
  loadingKnowledgeBases.value = true
  try {
    const response = await getKnowledgeBaseList({ page: 1, pageSize: 100 })
    knowledgeBases.value = Array.isArray(response) ? response : (response?.content || response?.data || [])
  } catch (error) {
    ElMessage.warning('知识库列表加载失败，可手动输入 ID')
  } finally {
    loadingKnowledgeBases.value = false
  }
}

const applyPreset = key => {
  const preset = presets.find(item => item.key === key)
  if (!preset) return
  form.value.configGroup = preset.group
  form.value.configType = preset.type
  form.value.description = preset.description
  if (key === GLOBAL_THEME_CONFIG_KEY && !form.value.configValue) {
    handleThemeChange(selectedTheme.value)
  }
}

const openDialog = row => {
  editingConfig.value = row || null
  if (row) {
    form.value = {
      configKey: row.configKey,
      configValue: row.configValue || '',
      configGroup: row.configGroup || 'system',
      configType: row.configType || 'string',
      description: row.description || ''
    }
    if (row.configKey === GLOBAL_THEME_CONFIG_KEY) {
      selectedTheme.value = (row.configValue || 'element').split(':')[0]
    }
  } else {
    form.value = {
      configKey: '',
      configValue: '',
      configGroup: 'system',
      configType: 'string',
      description: ''
    }
    selectedTheme.value = 'element'
  }
  dialogVisible.value = true
}

const handleThemeChange = themeId => {
  const theme = getThemeById(themeId)
  if (theme) {
    form.value.configValue = `${themeId}:${theme.colors.primary}`
  }
}

const handleSave = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (isThemeConfig.value) {
    handleThemeChange(selectedTheme.value)
  }

  saving.value = true
  try {
    await setOrUpdateConfig({
      configKey: form.value.configKey,
      configValue: form.value.configValue,
      configGroup: form.value.configGroup,
      configType: form.value.configType,
      description: form.value.description
    })
    if (form.value.configKey === GLOBAL_THEME_CONFIG_KEY) {
      applyGlobalTheme(form.value.configValue)
    }
    ElMessage.success('配置已保存')
    dialogVisible.value = false
    await refreshConfigs()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async row => {
  try {
    await ElMessageBox.confirm(`确定删除配置 "${row.configKey}" 吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteConfig(row.configKey)
    ElMessage.success('配置已删除')
    await refreshConfigs()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

onMounted(refreshConfigs)
</script>

<style scoped>
.system-config {
  height: 100%;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--color-bg-secondary);
  overflow: hidden;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 54px;
  flex-shrink: 0;
}

.page-header h2 {
  margin: 0;
  font-size: var(--font-size-lg);
  color: var(--color-text-primary);
  font-weight: var(--font-weight-semibold);
}

.page-header p {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.35;
}

.filter-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  flex-shrink: 0;
  padding: 10px 14px;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

.filter-input {
  width: 340px;
}

.group-segmented {
  max-width: 100%;
  overflow-x: auto;
}

.table-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 0;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-xs);
}

.table-header {
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  border-bottom: 1px solid var(--color-border-lighter);
  background: var(--color-bg-primary);
}

.table-title {
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.table-subtitle {
  margin-left: 8px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.group-tag {
  min-width: 56px;
}

.value-text {
  color: var(--color-text-regular);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.type-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  height: 24px;
  padding: 0 8px;
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  background: var(--color-bg-secondary);
  font-size: var(--font-size-xs);
}

.table-panel :deep(.el-table) {
  flex: 1;
}

.table-panel :deep(.el-table th.el-table__cell) {
  background: var(--color-bg-tertiary);
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
}

.table-panel :deep(.el-table .el-table__cell) {
  padding: 10px 0;
}

.pagination-wrapper {
  height: 56px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
  border-top: 1px solid var(--color-border-lighter);
  background: var(--color-bg-primary);
}

.config-form {
  padding-right: 10px;
}

.theme-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
}

.theme-swatches {
  display: flex;
  gap: 4px;
}

.theme-swatches span {
  width: 18px;
  height: 18px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-light);
}

@media (max-width: 900px) {
  .system-config {
    padding: 12px;
    gap: 8px;
  }

  .page-header,
  .filter-panel {
    align-items: stretch;
  }

  .page-header {
    flex-direction: column;
    gap: 12px;
  }

  .filter-input {
    width: 100%;
  }
}
</style>
