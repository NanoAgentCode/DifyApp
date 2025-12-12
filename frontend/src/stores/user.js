/**
 * 用户状态管理 Store
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  // Getters
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')
  const username = computed(() => userInfo.value?.username || '')
  const userId = computed(() => userInfo.value?.id)

  // Actions
  function setToken(newToken) {
    token.value = newToken
    if (newToken) {
      localStorage.setItem('token', newToken)
    } else {
      localStorage.removeItem('token')
    }
  }

  function setUserInfo(info) {
    userInfo.value = info
    if (info) {
      localStorage.setItem('userInfo', JSON.stringify(info))
    } else {
      localStorage.removeItem('userInfo')
    }
  }

  function login(tokenValue, userInfoValue) {
    setToken(tokenValue)
    setUserInfo(userInfoValue)
  }

  function logout() {
    setToken('')
    setUserInfo(null)
    // 清除 token 验证缓存
    if (window.clearTokenCache) {
      window.clearTokenCache()
    }
  }

  function updateUserInfo(updates) {
    if (userInfo.value) {
      userInfo.value = { ...userInfo.value, ...updates }
      localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    }
  }

  return {
    // 状态
    token,
    userInfo,
    // Getters
    isLoggedIn,
    isAdmin,
    username,
    userId,
    // Actions
    setToken,
    setUserInfo,
    login,
    logout,
    updateUserInfo
  }
})

