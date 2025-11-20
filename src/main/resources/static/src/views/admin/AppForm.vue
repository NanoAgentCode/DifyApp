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
        label-width="120px"
      >
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

        <el-form-item label="Dify API Key" prop="appId">
          <el-input v-model="form.appId" placeholder="请输入Dify API Key" />
        </el-form-item>

        <el-form-item label="API Base URL" prop="apiBaseUrl">
          <el-input v-model="form.apiBaseUrl" placeholder="留空则使用默认URL" />
        </el-form-item>

        <el-form-item label="是否支持流式响应">
          <el-switch v-model="form.streamEnabled" />
        </el-form-item>

        <el-form-item label="是否需要上传文件">
          <el-switch v-model="form.fileUploadEnabled" />
        </el-form-item>

        <el-form-item label="应用图标" prop="icon">
          <el-input v-model="form.icon" placeholder="请输入图标URL" />
        </el-form-item>

        <el-form-item label="主题色" prop="themeColor">
          <el-color-picker v-model="form.themeColor" />
        </el-form-item>

        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>

        <el-form-item label="租户编号" prop="tenantId">
          <el-input-number v-model="form.tenantId" :min="1" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit">保存</el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createApp, updateApp, getAppDetail } from '@/api/aiApp'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const isEdit = ref(false)

const form = reactive({
  name: '',
  description: '',
  type: 1,
  appId: '',
  apiBaseUrl: '',
  streamEnabled: false,
  fileUploadEnabled: false,
  icon: '',
  themeColor: '',
  sort: 0,
  tenantId: 1
})

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
  max-width: 800px;
}
</style>

