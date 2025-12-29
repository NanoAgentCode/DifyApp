/**
 * 模型管理 Composables
 * 统一管理模型相关的逻辑
 */
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getAvailableQAModels } from '@/api/model'
import { getModelStyle } from '@/utils/modelColor'
import { logger } from '@/utils/logger'

export function useModel() {
  const qaModels = ref([])
  const embeddingModels = ref([])
  const loading = ref(false)

  /**
   * 加载问答模型列表
   */
  const loadQAModels = async () => {
    try {
      loading.value = true
      const models = await getAvailableQAModels()
      qaModels.value = models || []
    } catch (error) {
      logger.error('加载问答模型失败:', error)
      ElMessage.error('加载问答模型失败')
      qaModels.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 加载向量化模型列表
   * 注意：如果 API 不存在，此方法可能无法使用
   */
  const loadEmbeddingModels = async () => {
    try {
      loading.value = true
      // TODO: 如果后端有向量化模型列表 API，请替换此处的调用
      // const models = await getAvailableEmbeddingModels()
      embeddingModels.value = []
      logger.debug('向量化模型列表 API 未实现')
    } catch (error) {
      logger.error('加载向量化模型失败:', error)
      ElMessage.error('加载向量化模型失败')
      embeddingModels.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取默认问答模型
   */
  const defaultQAModel = computed(() => {
    return qaModels.value.find(m => m.isDefault) || qaModels.value[0] || null
  })

  /**
   * 获取默认向量化模型
   */
  const defaultEmbeddingModel = computed(() => {
    return embeddingModels.value.find(m => m.isDefault) || embeddingModels.value[0] || null
  })

  /**
   * 根据 ID 获取模型
   */
  const getQAModelById = (id) => {
    return qaModels.value.find(m => m.id === id)
  }

  const getEmbeddingModelById = (id) => {
    return embeddingModels.value.find(m => m.id === id)
  }

  /**
   * 获取模型样式
   */
  const getModelStyleById = (modelId) => {
    return getModelStyle(modelId)
  }

  return {
    qaModels,
    embeddingModels,
    loading,
    defaultQAModel,
    defaultEmbeddingModel,
    loadQAModels,
    loadEmbeddingModels,
    getQAModelById,
    getEmbeddingModelById,
    getModelStyleById
  }
}

