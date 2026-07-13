/**
 * 用户状态管理 Store
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getStoredUserInfo, isAdminUser, setStoredUserInfo } from '@/utils/userSession'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(getStoredUserInfo())

  // Getters
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => isAdminUser(userInfo.value))
  const username = computed(() => userInfo.value?.username || '')
  const userId = computed(() => userInfo.value?.userId ?? userInfo.value?.id ?? null)

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
    setStoredUserInfo(info)
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
      setStoredUserInfo(userInfo.value)
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
