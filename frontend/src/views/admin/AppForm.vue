<template>
  <div class="app-form">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button type="text" @click="handleBack" style="margin-right: 10px">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <span>{{ isEdit ? '编辑应用' : '创建应用' }}</span>
          </div>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="140px"
      >
        <!-- 基本信息 -->
        <el-card shadow="never" class="form-section">
          <template #header>
            <span class="section-title">基本信息</span>
          </template>
          <el-form-item label="应用名称" prop="name">
            <el-input v-model="form.name" placeholder="请输入应用名称" />
          </el-form-item>

          <el-form-item label="应用描述" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              placeholder="请输入应用描述"
            />
          </el-form-item>

          <el-form-item label="应用类型" prop="type">
            <el-radio-group v-model="form.type">
              <el-radio :label="1">Chat Flow</el-radio>
              <el-radio :label="2">Workflow</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-card>

        <!-- API配置 -->
        <el-card shadow="never" class="form-section">
          <template #header>
            <span class="section-title">API配置</span>
          </template>
          <el-form-item label="Dify API Key" prop="appId">
            <el-input v-model="form.appId" placeholder="请输入Dify API Key" />
          </el-form-item>

          <el-form-item label="API Base URL" prop="apiBaseUrl">
            <el-input v-model="form.apiBaseUrl" placeholder="留空则使用默认URL" />
          </el-form-item>
        </el-card>

        <!-- 功能设置 -->
        <el-card shadow="never" class="form-section">
          <template #header>
            <span class="section-title">功能设置</span>
          </template>
          <el-form-item label="是否支持流式响应">
            <el-switch 
              v-model="form.streamEnabled" 
              active-text="开启"
              inactive-text="关闭"
            />
            <div class="form-item-tip">开启后支持实时流式响应</div>
          </el-form-item>

          <el-form-item label="是否需要上传文件">
            <el-switch 
              v-model="form.fileUploadEnabled" 
              active-text="开启"
              inactive-text="关闭"
            />
            <div class="form-item-tip">开启后用户可以在工作流中上传文件</div>
          </el-form-item>

          <el-form-item label="是否显示文本输入框">
            <el-switch 
              v-model="form.inputEnabled" 
              active-text="开启"
              inactive-text="关闭"
            />
            <div class="form-item-tip">开启后显示文本输入框，关闭后隐藏输入框</div>
          </el-form-item>
        </el-card>

        <!-- 输入字段配置 -->
        <el-card shadow="never" class="form-section" v-if="form.type === 2">
          <template #header>
            <span class="section-title">输入字段配置</span>
          </template>
          <div class="input-fields-config">
            <div class="config-header">
              <el-button type="primary" @click="addInputField" size="small">
                <el-icon><Plus /></el-icon>
                添加字段
              </el-button>
              <el-button @click="importFromJson" size="small" type="info">
                <el-icon><DocumentCopy /></el-icon>
                从JSON导入
              </el-button>
              <el-button @click="exportToJson" size="small" type="info">
                <el-icon><Download /></el-icon>
                导出JSON
              </el-button>
            </div>
            
            <el-form :model="inputFieldsConfig" label-width="140px">
              <div 
                v-for="(field, index) in inputFieldsList" 
                :key="field.key || index"
                class="input-field-item"
              >
                <el-card shadow="hover" class="field-card">
                  <template #header>
                    <div class="field-header">
                      <span class="field-title">{{ field.label || field.key || `字段 ${index + 1}` }}</span>
                      <el-button 
                        type="danger" 
                        size="small" 
                        text 
                        @click="removeInputField(index)"
                      >
                        <el-icon><Delete /></el-icon>
                        删除
                      </el-button>
                    </div>
                  </template>
                  
                  <el-form-item label="字段键名" :required="true">
                    <el-input 
                      v-model="field.key" 
                      placeholder="例如: word, variable_name"
                      @blur="validateFieldKey(field, index)"
                    />
                    <div class="form-item-tip">用于API请求的字段名，必须唯一</div>
                  </el-form-item>
                  
                  <el-form-item label="显示标签">
                    <el-input 
                      v-model="field.label" 
                      placeholder="例如: 关键词"
                    />
                    <div class="form-item-tip">表单中显示的标签文本</div>
                  </el-form-item>
                  
                  <el-form-item label="字段类型">
                    <el-select v-model="field.type" placeholder="选择字段类型">
                      <el-option label="文本输入" value="text" />
                      <el-option label="多行文本" value="textarea" />
                      <el-option label="数字" value="number" />
                      <el-option label="下拉选择" value="select" />
                      <el-option label="JSON编辑器" value="json" />
                      <el-option label="日期选择" value="date" />
                      <el-option label="开关" value="switch" />
                    </el-select>
                  </el-form-item>
                  
                  <el-form-item label="占位符">
                    <el-input 
                      v-model="field.placeholder" 
                      placeholder="请输入占位符文本"
                    />
                  </el-form-item>
                  
                  <el-form-item label="默认值">
                    <el-input 
                      v-model="field.defaultValue" 
                      placeholder="字段的默认值"
                    />
                    <div class="form-item-tip">对于JSON类型，请输入有效的JSON字符串</div>
                  </el-form-item>
                  
                  <el-form-item label="帮助文本">
                    <el-input 
                      v-model="field.helpText" 
                      type="textarea"
                      :rows="2"
                      placeholder="显示在输入框下方的提示文本"
                    />
                  </el-form-item>
                  
                  <el-form-item label="是否必填">
                    <el-switch v-model="field.required" />
                  </el-form-item>
                  
                  <el-form-item label="行数" v-if="field.type === 'textarea'">
                    <el-input-number 
                      v-model="field.rows" 
                      :min="1" 
                      :max="20" 
                      :step="1"
                    />
                    <div class="form-item-tip">多行文本输入框的行数</div>
                  </el-form-item>
                  
                  <el-form-item label="选项列表" v-if="field.type === 'select'">
                    <div class="select-options">
                      <div 
                        v-for="(option, optIndex) in field.options || []" 
                        :key="optIndex"
                        class="option-item"
                      >
                        <el-input 
                          v-model="option.label" 
                          placeholder="显示文本"
                          style="width: 45%"
                        />
                        <el-input 
                          v-model="option.value" 
                          placeholder="值"
                          style="width: 45%"
                        />
                        <el-button 
                          type="danger" 
                          size="small" 
                          text 
                          @click="removeOption(field, optIndex)"
                        >
                          <el-icon><Delete /></el-icon>
                        </el-button>
                      </div>
                      <el-button 
                        type="primary" 
                        size="small" 
                        text 
                        @click="addOption(field)"
                      >
                        <el-icon><Plus /></el-icon>
                        添加选项
                      </el-button>
                    </div>
                  </el-form-item>
                  
                  <el-form-item label="样式配置">
                    <div class="style-config">
                      <el-input 
                        v-model="field.style.width" 
                        placeholder="宽度，如: 100%"
                        style="width: 48%"
                      />
                      <el-input 
                        v-model="field.style.labelWidth" 
                        placeholder="标签宽度，如: 140px"
                        style="width: 48%"
                      />
                    </div>
                  </el-form-item>
                  
                  <el-form-item label="验证规则">
                    <div class="validation-config">
                      <el-input-number 
                        v-model="field.validation.minLength" 
                        placeholder="最小长度"
                        :min="0"
                        style="width: 30%"
                      />
                      <el-input-number 
                        v-model="field.validation.maxLength" 
                        placeholder="最大长度"
                        :min="0"
                        style="width: 30%"
                      />
                      <el-input 
                        v-model="field.validation.pattern" 
                        placeholder="正则表达式"
                        style="width: 35%"
                      />
                    </div>
                  </el-form-item>
                </el-card>
              </div>
              
              <div v-if="inputFieldsList.length === 0" class="empty-tip">
                <el-empty description="暂无输入字段，点击上方按钮添加" :image-size="100" />
              </div>
            </el-form>
          </div>
        </el-card>

        <!-- 显示设置 -->
        <el-card shadow="never" class="form-section">
          <template #header>
            <span class="section-title">显示设置</span>
          </template>
          <el-form-item label="应用图标" prop="icon">
            <el-radio-group v-model="selectedIcon" @change="handleIconChange" class="icon-radio-group">
              <el-radio 
                v-for="icon in builtInIcons" 
                :key="icon.id" 
                :label="icon.id"
                class="icon-radio-item"
              >
                <div class="icon-radio-content">
                  <el-icon :size="24" class="icon-radio-icon">
                    <component :is="iconComponents[icon.icon]" />
                  </el-icon>
                  <span class="icon-radio-label">{{ icon.name }}</span>
                </div>
              </el-radio>
              <el-radio label="custom" class="icon-radio-item">
                <div class="icon-radio-content">
                  <el-icon :size="24" class="icon-radio-icon"><Picture /></el-icon>
                  <span class="icon-radio-label">自定义图标URL</span>
                </div>
              </el-radio>
            </el-radio-group>
            <div v-if="selectedIcon === 'custom'" class="custom-icon-input">
              <el-input 
                v-model="customIconUrl" 
                placeholder="请输入图标URL（如：https://example.com/icon.png）"
                @input="handleCustomIconChange"
                clearable
                style="margin-top: 12px;"
              />
              <div class="custom-icon-preview" v-if="customIconUrl">
                <img :src="customIconUrl" alt="自定义图标" @error="handleIconError" />
              </div>
            </div>
          </el-form-item>

          <el-form-item label="排序" prop="sort">
            <el-input-number v-model="form.sort" :min="0" />
            <div class="form-item-tip">数字越小越靠前</div>
          </el-form-item>
        </el-card>

        <!-- 系统设置 -->
        <el-card shadow="never" class="form-section">
          <template #header>
            <span class="section-title">系统设置</span>
          </template>
          <el-form-item label="租户编号" prop="tenantId">
            <el-input-number v-model="form.tenantId" :min="1" />
          </el-form-item>
        </el-card>

        <!-- 操作按钮 -->
        <el-form-item class="form-actions">
          <el-button type="primary" @click="handleSubmit" size="large">保存</el-button>
          <el-button @click="handleCancel" size="large">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, DocumentCopy, Download, Picture, ArrowLeft } from '@element-plus/icons-vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { createApp, updateApp, getAppDetail } from '@/api/aiApp'
import { builtInIcons, getIconById } from '@/utils/icons'
import { useErrorHandler } from '@/composables/useErrorHandler'

const { handleError } = useErrorHandler()

// 创建图标组件映射
const iconComponents = {}
Object.keys(ElementPlusIconsVue).forEach(key => {
  iconComponents[key] = ElementPlusIconsVue[key]
})

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const isEdit = ref(false)
const selectedIcon = ref('')
const customIconUrl = ref('')

const form = reactive({
  name: '',
  description: '',
  type: 1,
  appId: '',
  apiBaseUrl: '',
  streamEnabled: false,
  fileUploadEnabled: false,
  inputEnabled: true,
  icon: '',
  sort: 0,
  tenantId: 1,
  inputs: ''
})

// 输入字段配置
const inputFieldsConfig = reactive({})
const inputFieldsList = computed(() => {
  return Object.keys(inputFieldsConfig).map(key => ({
    key,
    ...inputFieldsConfig[key]
  }))
})

// 创建默认字段配置
const createDefaultField = () => ({
  key: '',
  label: '',
  type: 'text',
  placeholder: '',
  defaultValue: '',
  helpText: '',
  required: false,
  rows: 2,
  options: [],
  style: {
    width: '100%',
    labelWidth: '140px'
  },
  validation: {
    minLength: null,
    maxLength: null,
    pattern: ''
  }
})

// 添加输入字段
const addInputField = () => {
  const field = createDefaultField()
  const key = `field_${Date.now()}`
  field.key = key
  inputFieldsConfig[key] = field
}

// 删除输入字段
const removeInputField = (index) => {
  const field = inputFieldsList.value[index]
  if (field && field.key) {
    delete inputFieldsConfig[field.key]
  }
}

// 验证字段键名
const validateFieldKey = (field, index) => {
  if (!field.key || field.key.trim() === '') {
    ElMessage.warning('字段键名不能为空')
    return
  }
  
  // 检查是否有重复的键名
  const duplicates = inputFieldsList.value.filter((f, i) => 
    i !== index && f.key === field.key
  )
  if (duplicates.length > 0) {
    ElMessage.warning('字段键名已存在，请使用不同的键名')
    field.key = `field_${Date.now()}`
  }
}

// 添加选项（用于select类型）
const addOption = (field) => {
  if (!field.options) {
    field.options = []
  }
  field.options.push({ label: '', value: '' })
}

// 删除选项
const removeOption = (field, index) => {
  if (field.options && field.options.length > index) {
    field.options.splice(index, 1)
  }
}

// 从JSON导入配置
const importFromJson = async () => {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入JSON配置（格式：{"field1": {"label": "字段1", "type": "text", ...}, ...}）',
      '从JSON导入',
      {
        confirmButtonText: '导入',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '请输入JSON配置...',
        inputValue: form.inputs || ''
      }
    )
    
    if (value && value.trim()) {
      try {
        const parsed = JSON.parse(value.trim())
        
        // 如果是旧格式（简单的键值对），转换为新格式
        if (isOldFormat(parsed)) {
          const converted = convertOldFormat(parsed)
          Object.assign(inputFieldsConfig, converted)
        } else {
          // 新格式，直接使用
          Object.assign(inputFieldsConfig, parsed)
        }
        
        ElMessage.success('导入成功')
      } catch (e) {
        ElMessage.error('JSON格式错误: ' + e.message)
      }
    }
  } catch (e) {
    // 用户取消
  }
}

// 导出为JSON
const exportToJson = () => {
  const config = {}
  Object.keys(inputFieldsConfig).forEach(key => {
    const field = inputFieldsConfig[key]
    config[key] = {
      label: field.label || '',
      type: field.type || 'text',
      placeholder: field.placeholder || '',
      defaultValue: field.defaultValue || '',
      helpText: field.helpText || '',
      required: field.required || false,
      rows: field.rows || 2,
      options: field.options || [],
      style: field.style || {},
      validation: field.validation || {}
    }
  })
  
  const jsonStr = JSON.stringify(config, null, 2)
  
  // 复制到剪贴板
  navigator.clipboard.writeText(jsonStr).then(() => {
    ElMessage.success('JSON配置已复制到剪贴板')
  }).catch(() => {
    // 如果复制失败，显示在对话框中
    ElMessageBox.alert(jsonStr, 'JSON配置', {
      confirmButtonText: '确定',
      type: 'info'
    })
  })
}

// 检查是否为旧格式
const isOldFormat = (obj) => {
  if (!obj || typeof obj !== 'object') return false
  
  // 旧格式：简单的键值对，值通常是字符串、数字、布尔值或数组/对象
  const values = Object.values(obj)
  return values.every(v => 
    typeof v === 'string' || 
    typeof v === 'number' || 
    typeof v === 'boolean' || 
    v === null ||
    Array.isArray(v) ||
    (typeof v === 'object' && v !== null && !v.hasOwnProperty('type'))
  )
}

// 转换旧格式为新格式
const convertOldFormat = (oldConfig) => {
  const newConfig = {}
  Object.keys(oldConfig).forEach(key => {
    const value = oldConfig[key]
    newConfig[key] = {
      label: key,
      type: Array.isArray(value) || (typeof value === 'object' && value !== null) ? 'json' : 'text',
      placeholder: `请输入${key}`,
      defaultValue: typeof value === 'string' ? value : JSON.stringify(value),
      helpText: '',
      required: false,
      rows: Array.isArray(value) || (typeof value === 'object' && value !== null) ? 6 : 2,
      options: [],
      style: {
        width: '100%',
        labelWidth: '140px'
      },
      validation: {}
    }
  })
  return newConfig
}

// 将输入字段配置转换为inputs JSON字符串
const convertFieldsToInputs = () => {
  const inputs = {}
  Object.keys(inputFieldsConfig).forEach(key => {
    const field = inputFieldsConfig[key]
    if (field.key) {
      // 根据类型设置默认值
      let defaultValue = field.defaultValue || ''
      
      if (field.type === 'json' && defaultValue) {
        try {
          inputs[field.key] = JSON.parse(defaultValue)
        } catch (e) {
          inputs[field.key] = defaultValue
        }
      } else if (field.type === 'number') {
        inputs[field.key] = defaultValue ? Number(defaultValue) : null
      } else if (field.type === 'switch') {
        inputs[field.key] = defaultValue === 'true' || defaultValue === true
      } else {
        inputs[field.key] = defaultValue
      }
    }
  })
  
  // 同时保存字段配置
  const fieldsConfig = {}
  Object.keys(inputFieldsConfig).forEach(key => {
    const field = inputFieldsConfig[key]
    if (field.key) {
      fieldsConfig[field.key] = {
        label: field.label || '',
        type: field.type || 'text',
        placeholder: field.placeholder || '',
        defaultValue: field.defaultValue || '',
        helpText: field.helpText || '',
        required: field.required || false,
        rows: field.rows || 2,
        options: field.options || [],
        style: field.style || {},
        validation: field.validation || {}
      }
    }
  })
  
  // 返回包含字段配置和默认值的完整配置
  return JSON.stringify({
    fields: fieldsConfig,
    defaults: inputs
  }, null, 2)
}

// 从inputs JSON字符串加载输入字段配置
const loadFieldsFromInputs = (inputsStr) => {
  if (!inputsStr || inputsStr.trim() === '') {
    return
  }
  
  try {
    const parsed = JSON.parse(inputsStr)
    
    // 检查是否是新格式（包含fields和defaults）
    if (parsed.fields && parsed.defaults) {
      // 新格式
      Object.keys(parsed.fields).forEach(key => {
        inputFieldsConfig[key] = {
          key,
          ...parsed.fields[key]
        }
      })
    } else if (isOldFormat(parsed)) {
      // 旧格式，转换为新格式
      const converted = convertOldFormat(parsed)
      Object.assign(inputFieldsConfig, converted)
    } else {
      // 可能是字段配置格式
      Object.keys(parsed).forEach(key => {
        inputFieldsConfig[key] = {
          key,
          ...parsed[key]
        }
      })
    }
  } catch (e) {
    console.error('解析inputs配置失败:', e)
  }
}

// 图标变化处理
const handleIconChange = (value) => {
  if (value === 'custom') {
    // 选择自定义图标
    if (customIconUrl.value && customIconUrl.value.trim()) {
      form.icon = customIconUrl.value.trim()
    } else {
      form.icon = ''
    }
  } else {
    // 选择内置图标
    const icon = getIconById(value)
    if (icon) {
      form.icon = `icon:${value}`
      customIconUrl.value = ''
    }
  }
}

// 自定义图标URL变化处理
const handleCustomIconChange = (url) => {
  if (selectedIcon.value === 'custom') {
    if (url && url.trim()) {
      form.icon = url.trim()
    } else {
      form.icon = ''
    }
  }
}

// 图标加载错误处理
const handleIconError = () => {
  ElMessage.warning('自定义图标URL无法加载，请检查URL是否正确')
}

const rules = {
  name: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择应用类型', trigger: 'change' }],
  appId: [{ required: true, message: '请输入Dify API Key', trigger: 'blur' }],
  tenantId: [{ required: true, message: '请输入租户编号', trigger: 'blur' }]
}

const fetchAppDetail = async () => {
  if (route.params.id) {
    isEdit.value = true
    try {
      const res = await getAppDetail(route.params.id)
      Object.assign(form, res)
      
      // 加载输入字段配置
      if (res.inputs) {
        loadFieldsFromInputs(res.inputs)
      }
      
      // 初始化主题选择
      // 初始化图标选择
      if (res.icon) {
        if (res.icon.startsWith('icon:')) {
          // 内置图标格式：icon:iconId
          const iconId = res.icon.substring(5)
          selectedIcon.value = iconId
          customIconUrl.value = ''
        } else {
          // 自定义图标URL
          selectedIcon.value = 'custom'
          customIconUrl.value = res.icon
        }
      } else {
        selectedIcon.value = ''
        customIconUrl.value = ''
      }
    } catch (error) {
      ElMessage.error('获取应用详情失败')
    }
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    
    // 将输入字段配置转换为inputs JSON字符串
    if (form.type === 2 && inputFieldsList.value.length > 0) {
      form.inputs = convertFieldsToInputs()
    } else if (form.type === 2 && inputFieldsList.value.length === 0) {
      // 如果没有配置字段，设置为空字符串
      form.inputs = ''
    }
    
    // 清理空字符串字段，转换为null或删除
    const submitData = { ...form }
    
    // 将空字符串转换为null或删除
    if (!submitData.description || submitData.description.trim() === '') {
      submitData.description = null
    }
    if (!submitData.apiBaseUrl || submitData.apiBaseUrl.trim() === '') {
      submitData.apiBaseUrl = null
    }
    if (!submitData.icon || submitData.icon.trim() === '') {
      submitData.icon = null
    }
    if (!submitData.inputs || submitData.inputs.trim() === '') {
      submitData.inputs = null
    }

    delete submitData.themeColor
    
    if (isEdit.value) {
      // 更新时排除 appId 和 tenantId 字段（API Key 和租户ID不应该被更新）
      const { appId, tenantId, ...updateData } = submitData
      await updateApp(route.params.id, updateData)
      ElMessage.success('更新成功')
    } else {
      await createApp(submitData)
      ElMessage.success('创建成功')
    }
    router.push('/admin/apps')
  } catch (error) {
    if (error !== false) {
      const { handleError } = useErrorHandler()
      handleError(error, isEdit.value ? '更新失败' : '创建失败', { logError: true })
    }
  }
}

const handleCancel = () => {
  router.back()
}

const handleBack = () => {
  router.push('/admin/apps')
}

onMounted(() => {
  fetchAppDetail()
})
</script>

<style scoped>
.app-form {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
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

.form-section {
  margin-bottom: 20px;
  border: 1px solid #e4e7ed;
}

.form-section :deep(.el-card__header) {
  background-color: #f5f7fa;
  padding: 15px 20px;
  border-bottom: 1px solid #e4e7ed;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.form-section :deep(.el-card__body) {
  padding: 20px;
}

.form-section :deep(.el-form-item) {
  margin-bottom: 22px;
}

.form-item-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

.form-actions {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
  text-align: center;
}

.form-actions :deep(.el-form-item__content) {
  justify-content: center;
}

.form-actions .el-button {
  margin: 0 10px;
}

/* 输入字段配置样式 */
.input-fields-config {
  width: 100%;
}

.config-header {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #e4e7ed;
}

.input-field-item {
  margin-bottom: 20px;
}

.field-card {
  margin-bottom: 15px;
}

.field-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.field-title {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}

.select-options {
  width: 100%;
}

.option-item {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.style-config,
.validation-config {
  display: flex;
  gap: 10px;
  align-items: center;
}

.empty-tip {
  padding: 40px 0;
  text-align: center;
}

/* 图标选择器样式 - 多列单选框形式 */
.icon-radio-group {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.icon-radio-item {
  width: 100%;
  margin: 0;
  height: auto;
  padding: 0;
}

.icon-radio-item :deep(.el-radio__input) {
  margin-right: 12px;
}

.icon-radio-item :deep(.el-radio__label) {
  padding: 0;
  width: 100%;
}

.icon-radio-content {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.icon-radio-icon {
  color: #409eff;
  flex-shrink: 0;
}

.icon-radio-label {
  font-size: 14px;
  color: #303133;
}

.icon-radio-item.is-checked .icon-radio-label {
  color: #409eff;
  font-weight: 500;
}

.custom-icon-input {
  margin-top: 12px;
  padding-left: 32px;
}

.custom-icon-preview {
  width: 64px;
  height: 64px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #f5f7fa;
  margin-top: 8px;
}

.custom-icon-preview img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
</style>
