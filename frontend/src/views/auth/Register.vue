<template>
  <div class="register-container">
    <div class="register-box">
      <h2 class="title">用户注册</h2>
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        class="register-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model.trim="registerForm.username"
            placeholder="请输入用户名"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="email">
          <el-input
            v-model.trim="registerForm.email"
            placeholder="请输入邮箱"
            size="large"
            prefix-icon="Message"
          />
        </el-form-item>
        <el-form-item prop="verificationCode">
          <div class="code-row">
            <el-input
              v-model.trim="registerForm.verificationCode"
              placeholder="请输入6位邮箱验证码"
              maxlength="6"
              inputmode="numeric"
              size="large"
            />
            <el-button
              size="large"
              :loading="codeLoading"
              :disabled="countdown > 0"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="8-64位，需包含字母和数字"
            size="large"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            size="large"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleRegister"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="register-button"
            :loading="loading"
            @click="handleRegister"
          >
            注册
          </el-button>
        </el-form-item>
        <el-form-item>
          <div class="login-link">
            <span>已有账号？</span>
            <el-link type="primary" @click="goToLogin">立即登录</el-link>
          </div>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register, sendVerificationCode } from '@/api/auth'

const router = useRouter()

const registerFormRef = ref(null)
const loading = ref(false)
const codeLoading = ref(false)
const countdown = ref(0)
let countdownTimer = null

const registerForm = reactive({
  username: '',
  email: '',
  verificationCode: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validatePassword = (rule, value, callback) => {
  if (!/^(?=.*[A-Za-z])(?=.*\d).+$/.test(value || '')) {
    callback(new Error('密码必须同时包含字母和数字'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名长度在3到64个字符', trigger: 'blur' },
    { pattern: /^[\p{L}\p{N}._-]+$/u, message: '用户名只能包含字母、数字、点、下划线和连字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  verificationCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码必须为6位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度必须在8-64之间', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const startCountdown = () => {
  clearInterval(countdownTimer)
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(countdownTimer)
  }, 1000)
}

const handleSendCode = async () => {
  if (!registerFormRef.value) return
  try {
    await registerFormRef.value.validateField('email')
    codeLoading.value = true
    await sendVerificationCode(registerForm.email, 'REGISTER')
    ElMessage.success('验证码已发送，请查收邮件')
    startCountdown()
  } catch (error) {
    if (error?.response) {
      ElMessage.error(error.response.data?.message || '验证码发送失败')
    }
  } finally {
    codeLoading.value = false
  }
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  
  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const response = await register({
          username: registerForm.username,
          email: registerForm.email,
          verificationCode: registerForm.verificationCode,
          password: registerForm.password
        })
        
        ElMessage.success(response.message || '注册成功，请等待管理员审核')
        
        // 延迟跳转到登录页
        setTimeout(() => {
          router.push('/login')
        }, 1500)
      } catch (error) {
        ElMessage.error(error.response?.data?.message || error.message || '注册失败')
      } finally {
        loading.value = false
      }
    }
  })
}

const goToLogin = () => {
  router.push('/login')
}

onBeforeUnmount(() => clearInterval(countdownTimer))
</script>

<style scoped>
/* ========== 页面容器 ========== */
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 50%, var(--color-primary-dark-2) 100%);
  position: relative;
  overflow: hidden;
}

.register-container::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 1px, transparent 1px);
  background-size: 50px 50px;
  animation: move 20s linear infinite;
}

@keyframes move {
  0% {
    transform: translate(0, 0);
  }
  100% {
    transform: translate(50px, 50px);
  }
}

/* ========== 注册框 ========== */
.register-box {
  width: 420px;
  padding: var(--spacing-3xl) var(--spacing-xl);
  background: rgba(255, 255, 255, 0.98);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-primary-lg);
  position: relative;
  z-index: var(--z-base);
  backdrop-filter: blur(10px);
  animation: scaleIn var(--transition-base);
}

@keyframes scaleIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.title {
  text-align: center;
  margin-bottom: var(--spacing-2xl);
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 1px;
}

/* ========== 表单样式 ========== */
.register-form {
  margin-top: var(--spacing-lg);
}

.register-form :deep(.el-input__wrapper) {
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px var(--color-border-base) inset;
  transition: all var(--transition-base);
}

.register-form :deep(.el-input__wrapper:hover) {
  box-shadow: var(--shadow-xs);
  border-color: var(--color-primary);
}

.register-form :deep(.el-input.is-focus .el-input__wrapper) {
  box-shadow: var(--shadow-primary);
  border-color: var(--color-primary);
}

.register-form :deep(.el-input__inner) {
  color: var(--color-text-primary);
  font-size: var(--font-size-md);
}

.register-form :deep(.el-input__prefix) {
  color: var(--color-primary);
}

.code-row {
  display: flex;
  width: 100%;
  gap: var(--spacing-sm);
}

.code-row .el-button {
  min-width: 118px;
}

/* ========== 注册按钮 ========== */
.register-button {
  width: 100%;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-medium);
  height: var(--button-height-lg);
  transition: all var(--transition-base);
  box-shadow: var(--shadow-primary);
}

.register-button:hover {
  background: linear-gradient(135deg, var(--color-primary-light-1) 0%, var(--color-primary) 100%);
  box-shadow: var(--shadow-primary-lg);
  transform: translateY(-2px);
}

.register-button:active {
  transform: translateY(0);
  box-shadow: var(--shadow-primary);
}

/* ========== 登录链接 ========== */
.login-link {
  text-align: center;
  width: 100%;
  color: var(--color-text-regular);
  font-size: var(--font-size-sm);
}

.login-link span {
  margin-right: var(--spacing-sm);
}

.login-link :deep(.el-link) {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
  transition: all var(--transition-base);
}

.login-link :deep(.el-link:hover) {
  color: var(--color-primary-light-1);
  text-decoration: underline;
}

@media (max-width: 520px) {
  .register-container {
    align-items: flex-start;
    overflow-y: auto;
    padding: var(--spacing-lg) 0;
  }

  .register-box {
    width: 94%;
    padding: var(--spacing-xl) var(--spacing-md);
  }

  .code-row {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>

