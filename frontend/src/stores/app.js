/**
 * 应用状态管理 Store
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  // 状态
  const loading = ref(false)
  const theme = ref(localStorage.getItem('theme') || 'light')
  const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')

  // Actions
  function setLoading(value) {
    loading.value = value
  }

  function setTheme(newTheme) {
    theme.value = newTheme
    localStorage.setItem('theme', newTheme)
    // 应用主题
    document.documentElement.setAttribute('data-theme', newTheme)
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('sidebarCollapsed', sidebarCollapsed.value)
  }

  function setSidebarCollapsed(value) {
    sidebarCollapsed.value = value
    localStorage.setItem('sidebarCollapsed', value)
  }

  // 初始化主题
  function initTheme() {
    document.documentElement.setAttribute('data-theme', theme.value)
  }

  return {
    // 状态
    loading,
    theme,
    sidebarCollapsed,
    // Actions
    setLoading,
    setTheme,
    toggleSidebar,
    setSidebarCollapsed,
    initTheme
  }
})

