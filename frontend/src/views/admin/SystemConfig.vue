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

      <!-- 搜索和筛选 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索配置键或描述"
          clearable
          style="width: 300px"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="filterGroup"
          placeholder="筛选配置分组"
          clearable
          style="width: 200px; margin-left: 10px"
          @change="handleFilter"
        >
          <el-option label="全部" value="" />
          <el-option label="帮助配置" value="help" />
          <el-option label="系统配置" value="system" />
        </el-select>
      </div>

      <!-- 配置列表 -->
      <el-table
        :data="filteredConfigList"
        v-loading="loading"
        stripe
        border
        style="width: 100%; margin-top: 20px"
      >
        <el-table-column prop="configKey" label="配置键" min-width="200" show-overflow-tooltip />
        <el-table-column prop="configValue" label="配置值" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.configValue && row.configValue.length > 50">
              {{ row.configValue.substring(0, 50) }}...
            </span>
            <span v-else>{{ row.configValue || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configGroup" label="配置分组" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.configGroup" size="small">{{ row.configGroup }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="configType" label="配置类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.configType" size="small" type="info">{{ row.configType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑配置对话框 -->
    <el-dialog
      v-model="showDialog"
      :title="editingConfig ? '编辑配置' : '添加配置'"
      width="600px"
    >
      <el-form :model="form" label-width="120px" ref="formRef">
        <el-form-item label="配置键" prop="configKey" :rules="[{ required: true, message: '请输入配置键' }]">
          <el-input
            v-model="form.configKey"
            placeholder="例如：help.knowledgeBaseId"
            :disabled="!!editingConfig"
          />
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
          <el-input
            v-model="form.configGroup"
            placeholder="例如：help, system"
          />
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
import { Plus, Search, Check } from '@element-plus/icons-vue'
import { getAllConfigs, setOrUpdateConfig, deleteConfig } from '@/api/systemConfig'
import { industrialThemes, getThemeById } from '@/utils/themes'
import { GLOBAL_THEME_CONFIG_KEY } from '@/utils/globalTheme'
import { applyGlobalTheme } from '@/utils/globalTheme'

const loading = ref(false)
const saving = ref(false)
const configList = ref([])
const searchKeyword = ref('')
const filterGroup = ref('')
const showDialog = ref(false)
const editingConfig = ref(null)
const formRef = ref(null)

const form = ref({
  configKey: '',
  configValue: '',
  configGroup: '',
  configType: '',
  description: ''
})

// 主题相关
const selectedTheme = ref('element') // 默认使用饿了么蓝

// 判断是否是全局主题色配置
const isGlobalThemeConfig = computed(() => {
  return form.value.configKey === GLOBAL_THEME_CONFIG_KEY
})

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

// 过滤后的配置列表
const filteredConfigList = computed(() => {
  let result = configList.value

  // 按关键词搜索
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(config => 
      config.configKey.toLowerCase().includes(keyword) ||
      (config.description && config.description.toLowerCase().includes(keyword))
    )
  }

  // 按分组筛选
  if (filterGroup.value) {
    result = result.filter(config => config.configGroup === filterGroup.value)
  }

  return result
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

// 搜索
const handleSearch = () => {
  // 搜索逻辑在 computed 中处理
}

// 筛选
const handleFilter = () => {
  // 筛选逻辑在 computed 中处理
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

