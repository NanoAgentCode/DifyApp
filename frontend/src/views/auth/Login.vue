<template>
  <div class="login-container">
    <div class="login-box">
      <h2 class="title">NanoAgent Workbench</h2>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名或邮箱"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <div class="password-actions">
          <el-link type="primary" :underline="false" @click="showForgotPassword = true">
            忘记密码？
          </el-link>
        </div>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-button"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
        <el-form-item>
          <div class="register-link">
            <span>还没有账号？</span>
            <el-link type="primary" @click="goToRegister">立即注册</el-link>
          </div>
        </el-form-item>
      </el-form>
    </div>
    <ForgotPasswordDialog v-model="showForgotPassword" />
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'
import { getFirstPermittedPath } from '@/utils/permission'
import ForgotPasswordDialog from '@/components/ForgotPasswordDialog.vue'

const router = useRouter()

const loginFormRef = ref(null)
const loading = ref(false)
const showForgotPassword = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名或邮箱', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const response = await login({
          username: loginForm.username,
          password: loginForm.password
        })
        
        // 清除旧的 token 验证缓存
        if (window.clearTokenCache) {
          window.clearTokenCache()
        }
        
        // 保存token和用户信息
        localStorage.setItem('token', response.token)
        localStorage.setItem('userInfo', JSON.stringify({
          userId: response.userId,
          username: response.username,
          email: response.email,
          role: response.role,
          status: response.status,
          roles: response.roles || [],
          permissions: response.permissions || []
        }))
        
        ElMessage.success('登录成功')
        
        const preferred = response.role === 1 ? '/admin/chat' : '/user/chat'
        router.push(getFirstPermittedPath(preferred, response.role === 1 ? '/admin' : '/user'))
      } catch (error) {
        ElMessage.error(error.response?.data?.message || error.message || '登录失败')
      } finally {
        loading.value = false
      }
    }
  })
}

const goToRegister = () => {
  router.push('/register')
}
</script>

<style scoped>
/* ========== 登录容器 ========== */
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 50%, var(--color-primary-dark-2) 100%);
  position: relative;
  overflow: hidden;
}

.login-container::before {
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

/* ========== 登录框 ========== */
.login-box {
  width: 420px;
  padding: var(--spacing-3xl) var(--spacing-2xl);
  background: rgba(255, 255, 255, 0.98);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-2xl), 0 15px 50px rgba(64, 158, 255, 0.25);
  position: relative;
  z-index: var(--z-base);
  backdrop-filter: blur(10px);
  transition: all var(--transition-base);
  animation: scaleIn var(--transition-base) ease-out;
}

.login-box:hover {
  box-shadow: var(--shadow-2xl), 0 20px 60px rgba(64, 158, 255, 0.3);
}

/* ========== 标题 ========== */
.title {
  text-align: center;
  margin-bottom: var(--spacing-2xl);
  color: var(--color-primary);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 1px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* ========== 表单 ========== */
.login-form {
  margin-top: var(--spacing-lg);
}

.login-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-lg);
}

.password-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: calc(var(--spacing-md) * -1);
  margin-bottom: var(--spacing-lg);
  font-size: var(--font-size-sm);
}

.login-form :deep(.el-input__wrapper) {
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px var(--color-border-base) inset;
  transition: all var(--transition-base);
  background: var(--color-bg-primary);
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--color-primary-light-2) inset, var(--shadow-xs);
}

.login-form :deep(.el-input.is-focus .el-input__wrapper) {
  box-shadow: var(--shadow-primary);
  border-color: var(--color-primary);
}

.login-form :deep(.el-input__inner) {
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
}

.login-form :deep(.el-input__prefix) {
  color: var(--color-primary);
}

/* ========== 登录按钮 ========== */
.login-button {
  width: 100%;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-medium);
  height: 44px;
  transition: all var(--transition-base);
  box-shadow: var(--shadow-primary);
  color: #ffffff;
}

.login-button:hover {
  background: linear-gradient(135deg, var(--color-primary-light-1) 0%, var(--color-primary) 100%);
  box-shadow: var(--shadow-primary-lg);
  transform: translateY(-2px);
}

.login-button:active {
  transform: translateY(0);
  box-shadow: var(--shadow-primary);
}

.login-button:focus {
  outline: 2px solid var(--color-primary-light-3);
  outline-offset: 2px;
}

/* ========== 注册链接 ========== */
.register-link {
  text-align: center;
  width: 100%;
  color: var(--color-text-regular);
  font-size: var(--font-size-sm);
}

.register-link span {
  margin-right: var(--spacing-sm);
}

.register-link :deep(.el-link) {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
  transition: color var(--transition-base);
}

.register-link :deep(.el-link:hover) {
  color: var(--color-primary-light-1);
}

/* ========== 响应式设计 ========== */
@media (max-width: 768px) {
  .login-box {
    width: 90%;
    max-width: 400px;
    padding: var(--spacing-2xl) var(--spacing-lg);
  }

  .title {
    font-size: var(--font-size-2xl);
    margin-bottom: var(--spacing-xl);
  }
}

@media (max-width: 480px) {
  .login-box {
    width: 95%;
    padding: var(--spacing-xl) var(--spacing-md);
  }

  .title {
    font-size: var(--font-size-xl);
  }
}
</style>

