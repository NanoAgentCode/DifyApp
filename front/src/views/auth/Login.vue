<template>
  <div class="login-container">
    <div class="login-box">
      <h2 class="title">Dify应用管理平台</h2>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
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
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'

const router = useRouter()

const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
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
        
        // 保存token和用户信息
        localStorage.setItem('token', response.token)
        localStorage.setItem('userInfo', JSON.stringify({
          userId: response.userId,
          username: response.username,
          role: response.role,
          status: response.status
        }))
        
        ElMessage.success('登录成功')
        
        // 根据角色跳转
        if (response.role === 1) {
          // 管理员跳转到管理端
          router.push('/admin/apps')
        } else {
          // 普通用户跳转到用户端应用列表
          router.push('/user/apps')
        }
      } catch (error) {
        ElMessage.error(error.response?.data?.error || error.message || '登录失败')
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
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, var(--el-color-primary, #409EFF) 0%, var(--el-color-primary-dark-2, #337ECC) 50%, var(--el-color-primary-dark-2, #2B6CB0) 100%);
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

.login-box {
  width: 420px;
  padding: 50px 40px;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 12px;
  box-shadow: 0 15px 50px var(--el-color-primary-rgba-03, rgba(64, 158, 255, 0.25));
  position: relative;
  z-index: 1;
  backdrop-filter: blur(10px);
}

.title {
  text-align: center;
  margin-bottom: 40px;
  color: var(--el-color-primary, #409EFF);
  font-size: 28px;
  font-weight: 600;
  letter-spacing: 1px;
}

.login-form {
  margin-top: 20px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 6px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  transition: all 0.3s;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-color-primary, #409EFF) inset;
}

.login-form :deep(.el-input.is-focus .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-primary, #409EFF) inset;
}

.login-form :deep(.el-input__inner) {
  color: #333;
}

.login-form :deep(.el-input__prefix) {
  color: var(--el-color-primary, #409EFF);
}

.login-button {
  width: 100%;
  background: linear-gradient(135deg, var(--el-color-primary, #409EFF) 0%, var(--el-color-primary-dark-2, #337ECC) 100%);
  border: none;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 500;
  height: 44px;
  transition: all 0.3s;
  box-shadow: 0 4px 12px var(--el-color-primary-rgba-04, rgba(64, 158, 255, 0.3));
}

.login-button:hover {
  background: linear-gradient(135deg, var(--el-color-primary-light-3, #66B1FF) 0%, var(--el-color-primary, #409EFF) 100%);
  box-shadow: 0 6px 16px var(--el-color-primary-rgba-05, rgba(64, 158, 255, 0.4));
  transform: translateY(-2px);
}

.login-button:active {
  transform: translateY(0);
}

.register-link {
  text-align: center;
  width: 100%;
  color: #606266;
  font-size: 14px;
}

.register-link span {
  margin-right: 8px;
}

.register-link :deep(.el-link) {
  color: var(--el-color-primary, #409EFF);
  font-weight: 500;
}

.register-link :deep(.el-link:hover) {
  color: var(--el-color-primary-light-3, #66B1FF);
}
</style>

