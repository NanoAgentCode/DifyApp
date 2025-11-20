<template>
  <div class="app-form">
    <el-card>
      <template #header>
        <span>{{ isEdit ? '编辑应用' : '创建应用' }}</span>
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

        <!-- 显示设置 -->
        <el-card shadow="never" class="form-section">
          <template #header>
            <span class="section-title">显示设置</span>
          </template>
          <el-form-item label="应用图标" prop="icon">
            <el-input v-model="form.icon" placeholder="请输入图标URL" />
          </el-form-item>

          <el-form-item label="主题" prop="themeColor">
            <div class="theme-selector">
              <el-radio-group v-model="selectedTheme" @change="handleThemeChange">
                <el-radio 
                  v-for="theme in industrialThemes" 
                  :key="theme.id" 
                  :label="theme.id"
                  class="theme-radio"
                >
                  <div class="theme-preview">
                    <div 
                      class="theme-color-box" 
                      :style="{ backgroundColor: theme.colors.primary }"
                    ></div>
                    <div class="theme-info">
                      <div class="theme-name">{{ theme.name }}</div>
                      <div class="theme-desc">{{ theme.description }}</div>
                    </div>
                  </div>
                </el-radio>
              </el-radio-group>
              <div class="custom-color-section">
                <el-divider>或自定义颜色</el-divider>
                <el-color-picker v-model="form.themeColor" @change="handleCustomColorChange" />
                <span class="custom-color-tip">选择自定义颜色将覆盖预设主题</span>
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
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createApp, updateApp, getAppDetail } from '@/api/aiApp'
import { industrialThemes, getThemeById, findThemeByColor } from '@/utils/themes'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const isEdit = ref(false)
const selectedTheme = ref('')

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
  themeColor: '',
  sort: 0,
  tenantId: 1
})

// 主题变化处理
const handleThemeChange = (themeId) => {
  const theme = getThemeById(themeId)
  if (theme) {
    // 保存主题ID和主色到themeColor字段（格式：themeId:primaryColor）
    form.themeColor = `${themeId}:${theme.colors.primary}`
  }
}

// 自定义颜色变化处理
const handleCustomColorChange = (color) => {
  if (color) {
    // 如果选择了自定义颜色，清除主题选择
    selectedTheme.value = ''
    form.themeColor = color
  }
}

// 监听form.themeColor变化，同步selectedTheme
watch(() => form.themeColor, (newColor) => {
  if (newColor && newColor.includes(':')) {
    // 如果是主题格式 themeId:color
    const [themeId] = newColor.split(':')
    selectedTheme.value = themeId
  } else if (newColor) {
    // 如果是自定义颜色，尝试查找匹配的主题
    const matchedTheme = findThemeByColor(newColor)
    if (matchedTheme) {
      selectedTheme.value = matchedTheme.id
    } else {
      selectedTheme.value = ''
    }
  } else {
    selectedTheme.value = ''
  }
}, { immediate: true })

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
      // 初始化主题选择
      if (res.themeColor) {
        if (res.themeColor.includes(':')) {
          const [themeId] = res.themeColor.split(':')
          selectedTheme.value = themeId
        } else {
          const matchedTheme = findThemeByColor(res.themeColor)
          selectedTheme.value = matchedTheme ? matchedTheme.id : ''
        }
      }
    } catch (error) {
      ElMessage.error('获取应用详情失败')
    }
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    
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
    if (!submitData.themeColor || submitData.themeColor.trim() === '') {
      submitData.themeColor = null
    }
    
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
      // 显示更详细的错误信息
      let errorMsg = isEdit.value ? '更新失败' : '创建失败'
      
      if (error?.response?.data) {
        const errorData = error.response.data
        if (errorData.errors) {
          // 验证错误，显示字段错误
          const errorFields = Object.keys(errorData.errors)
          const firstError = errorFields[0]
          errorMsg = `${firstError}: ${errorData.errors[firstError]}`
        } else if (errorData.error) {
          errorMsg = errorData.error
        }
      } else if (error?.message) {
        errorMsg = error.message
      }
      
      ElMessage.error(errorMsg)
      console.error('提交失败:', error)
    }
  }
}

const handleCancel = () => {
  router.back()
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
</style>

