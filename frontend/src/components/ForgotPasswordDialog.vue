<template>
  <el-dialog
    v-model="visible"
    title="找回密码"
    width="480px"
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <div class="dialog-tip">使用注册邮箱接收验证码并设置新密码。</div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="注册邮箱" prop="email">
        <el-input v-model.trim="form.email" placeholder="请输入注册邮箱" prefix-icon="Message" />
      </el-form-item>
      <el-form-item label="邮箱验证码" prop="verificationCode">
        <div class="code-row">
          <el-input
            v-model.trim="form.verificationCode"
            maxlength="6"
            placeholder="6位验证码"
            inputmode="numeric"
          />
          <el-button
            :loading="codeLoading"
            :disabled="countdown > 0"
            @click="handleSendCode"
          >
            {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          placeholder="8-64位，需包含字母和数字"
          show-password
        />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="请再次输入新密码"
          show-password
          @keyup.enter="handleSubmit"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">重置密码</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { forgotPassword, sendVerificationCode } from '@/api/auth'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'success'])
const visible = ref(false)
const loading = ref(false)
const codeLoading = ref(false)
const countdown = ref(0)
const formRef = ref(null)
let countdownTimer = null

const form = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d).+$/
const validatePassword = (rule, value, callback) => {
  if (!passwordPattern.test(value || '')) callback(new Error('密码必须同时包含字母和数字'))
  else callback()
}
const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules = {
  email: [
    { required: true, message: '请输入注册邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  verificationCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码必须为6位数字', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度必须在8-64之间', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

watch(() => props.modelValue, value => { visible.value = value })
watch(visible, value => emit('update:modelValue', value))

const startCountdown = () => {
  clearInterval(countdownTimer)
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(countdownTimer)
  }, 1000)
}

const handleSendCode = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validateField('email')
    codeLoading.value = true
    await sendVerificationCode(form.email, 'RESET_PASSWORD')
    ElMessage.success('如该邮箱已注册，验证码将发送至邮箱')
    startCountdown()
  } catch (error) {
    if (error?.response) {
      ElMessage.error(error.response.data?.message || '验证码发送失败')
    }
  } finally {
    codeLoading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    loading.value = true
    await forgotPassword({
      email: form.email,
      verificationCode: form.verificationCode,
      newPassword: form.newPassword
    })
    ElMessage.success('密码重置成功，请使用新密码登录')
    visible.value = false
    emit('success')
  } catch (error) {
    if (error?.response) {
      ElMessage.error(error.response.data?.message || '密码重置失败')
    }
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  Object.assign(form, { email: '', verificationCode: '', newPassword: '', confirmPassword: '' })
  formRef.value?.clearValidate()
  clearInterval(countdownTimer)
  countdown.value = 0
}

onBeforeUnmount(() => clearInterval(countdownTimer))
</script>

<style scoped>
.dialog-tip {
  margin-bottom: var(--spacing-lg);
  color: var(--color-text-regular);
  font-size: var(--font-size-sm);
}

.code-row {
  display: flex;
  width: 100%;
  gap: var(--spacing-sm);
}

.code-row .el-button {
  min-width: 118px;
}

@media (max-width: 520px) {
  .code-row {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
