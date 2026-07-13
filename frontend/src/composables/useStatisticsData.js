import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { logger } from '@/utils/logger'
import { getAllStatistics, getChatHistoryStatistics } from '@/api/statistics'

export function useStatisticsData(timeRange, afterLoad) {
  const loading = ref(false)
  const overview = ref(null)
  const users = ref(null)
  const apps = ref(null)
  const knowledgeBases = ref(null)
  const modelTokens = ref(null)
  const chatHistory = ref(null)

  const loadAllStatistics = async () => {
    loading.value = true
    try {
      const rangeDays = parseInt(timeRange.value) || 30
      const statistics = await getAllStatistics(rangeDays)
      overview.value = statistics.overview
      users.value = statistics.users
      apps.value = statistics.apps
      knowledgeBases.value = statistics.knowledgeBases
      modelTokens.value = statistics.modelTokens
      chatHistory.value = await getChatHistoryStatistics(rangeDays)
      await afterLoad?.()
    } catch (error) {
      logger.error('加载统计数据失败:', error)
      ElMessage.error('加载统计数据失败：' + (error.message || '未知错误'))
    } finally {
      loading.value = false
    }
  }

  return { loading, overview, users, apps, knowledgeBases, modelTokens, chatHistory, loadAllStatistics }
}
