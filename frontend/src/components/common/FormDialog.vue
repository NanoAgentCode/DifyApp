<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-width="labelWidth"
    >
      <slot :form="formData"></slot>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: '表单'
  },
  width: {
    type: String,
    default: '600px'
  },
  labelWidth: {
    type: String,
    default: '100px'
  },
  rules: {
    type: Object,
    default: () => ({})
  },
  initialData: {
    type: Object,
    default: () => ({})
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'submit', 'close'])

const visible = ref(false)
const formRef = ref(null)
const formData = ref({ ...props.initialData })

// 监听 visible 变化
watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    // 打开对话框时重置表单
    nextTick(() => {
      resetForm()
    })
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 重置表单
function resetForm() {
  formData.value = { ...props.initialData }
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

// 关闭
function handleClose() {
  visible.value = false
  emit('close')
}

// 提交
async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    emit('submit', { ...formData.value })
  } catch (error) {
    ElMessage.warning('请检查表单输入')
  }
}

// 暴露方法供父组件调用
defineExpose({
  resetForm,
  validate: () => formRef.value?.validate(),
  clearValidate: () => formRef.value?.clearValidate()
})
</script>

