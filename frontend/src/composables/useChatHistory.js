import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { renderMarkdown } from '@/composables/useMarkdown'
import { useErrorHandler } from '@/composables/useErrorHandler'
import {
  getMyConversations,
  getAllConversations,
  createConversation,
  updateConversationTitle,
  deleteConversation,
  batchDeleteConversations,
  getConversationMessages
} from '@/api/chat'

/**
 * 会话历史管理 Composable
 * @param {Object} options 配置选项
 * @param {boolean} options.isAdmin 是否是管理员模式
 * @param {boolean} options.enableCreate 是否启用创建会话功能（仅用户模式）
 * @param {boolean} options.enableEdit 是否启用编辑标题功能（仅用户模式）
 * @param {boolean} options.enableContinue 是否启用继续对话功能（仅用户模式）
 * @param {boolean} options.enableBatchDelete 是否启用批量删除功能（仅管理员模式）
 * @param {number} options.defaultPageSize 默认每页大小
 */
export function useChatHistory(options = {}) {
  const {
    isAdmin = false,
    enableCreate = false,
    enableEdit = false,
    enableContinue = false,
    enableBatchDelete = false,
    defaultPageSize = isAdmin ? 15 : 20
  } = options

  const router = useRouter()
  const { handleError, confirmDelete, confirmBatchDelete } = useErrorHandler()
  
  const loading = ref(false)
  const conversations = ref([])
  const searchKeyword = ref('')
  const selectedType = ref(null)
  const selectedIds = ref([])
  const selectedConversationId = ref(null)
  const currentPage = ref(1)
  const pageSize = ref(defaultPageSize)
  const total = ref(0)
  const editTitleDialogVisible = ref(false)
  const editTitle = ref('')
  const editingConversation = ref(null)
  const showConversationDetail = ref(false)
  const conversationDetail = ref({
    conversation: null,
    messages: []
  })

  // 加载会话列表
  const loadConversations = async () => {
    loading.value = true
    try {
      const response = isAdmin
        ? await getAllConversations(
            currentPage.value,
            pageSize.value,
            searchKeyword.value,
            selectedType.value
          )
        : await getMyConversations(
            currentPage.value,
            pageSize.value,
            searchKeyword.value,
            selectedType.value
          )
      
      conversations.value = response.content || []
      total.value = response.total || 0
    } catch (error) {
      handleError(error, '加载会话列表失败')
    } finally {
      loading.value = false
    }
  }

  // 搜索
  const handleSearch = () => {
    currentPage.value = 1
    loadConversations()
  }

  // 重置
  const handleReset = () => {
    searchKeyword.value = ''
    selectedType.value = null
    currentPage.value = 1
    loadConversations()
  }

  // 选择变化（仅管理员模式）
  const handleSelectionChange = (selection) => {
    selectedIds.value = selection.map(item => item.id)
  }

  // 选择会话，查看会话详情
  const selectConversation = async (conv) => {
    selectedConversationId.value = conv.id
    try {
      const messagesResponse = await getConversationMessages(conv.id)
      showConversationDetail.value = true
      conversationDetail.value = {
        conversation: conv,
        messages: messagesResponse || []
      }
    } catch (error) {
      handleError(error, '加载会话消息失败')
    }
  }

  // 查看会话详情（管理员模式）
  const handleView = async (row) => {
    await selectConversation(row)
  }

  // 创建新会话（仅用户模式）
  const handleCreateConversation = async () => {
    if (!enableCreate) return
    
    try {
      const response = await createConversation('新会话', null, null, 1)
      ElMessage.success('创建成功')
      loadConversations()
      if (response && response.id) {
        selectConversation(response)
      }
    } catch (error) {
      handleError(error, '创建会话失败')
    }
  }

  // 编辑标题（仅用户模式）
  const handleEditTitle = (conv) => {
    if (!enableEdit) return
    
    editingConversation.value = conv
    editTitle.value = conv.title
    editTitleDialogVisible.value = true
  }

  // 保存标题（仅用户模式）
  const handleSaveTitle = async () => {
    if (!editTitle.value.trim()) {
      ElMessage.warning('标题不能为空')
      return
    }
    try {
      await updateConversationTitle(editingConversation.value.id, editTitle.value)
      ElMessage.success('更新成功')
      editTitleDialogVisible.value = false
      loadConversations()
    } catch (error) {
      handleError(error, '更新标题失败')
    }
  }

  // 删除会话
  const handleDelete = async (conv) => {
    const confirmed = await confirmDelete('确定要删除这个会话吗？删除后将无法恢复该会话中的所有对话记录。')
    if (!confirmed) return
    
    try {
      await deleteConversation(conv.id)
      ElMessage.success('删除成功')
      loadConversations()
    } catch (error) {
      handleError(error, '删除失败')
    }
  }

  // 批量删除会话（仅管理员模式）
  const handleBatchDelete = async () => {
    if (!enableBatchDelete || selectedIds.value.length === 0) return
    
    const confirmed = await confirmBatchDelete(selectedIds.value.length, '会话')
    if (!confirmed) return
    
    try {
      await batchDeleteConversations(selectedIds.value)
      ElMessage.success('批量删除成功')
      selectedIds.value = []
      loadConversations()
    } catch (error) {
      handleError(error, '批量删除失败')
    }
  }

  // 继续对话（仅用户模式）
  const handleContinueConversation = async (conv) => {
    if (!enableContinue) return

    if (conv.type === 5) {
      ElMessage.info('页面助手会话暂不支持跨页面继续，已为你打开会话详情')
      await selectConversation(conv)
      return
    }
    
    // 将 conversationId 存储到 localStorage，聊天页面会读取
    localStorage.setItem('continueConversationId', conv.id.toString())
    localStorage.setItem('continueConversationType', conv.type?.toString() || '')
    // 根据会话类型跳转到对应的页面
    if (conv.type === 1) {
      router.push('/user/chat')
    } else if (conv.type === 2) {
      router.push('/user/kb-qa')
    } else {
      ElMessage.info('该类型会话暂不支持直接继续，已为你打开会话详情')
      localStorage.removeItem('continueConversationId')
      await selectConversation(conv)
    }
  }

  // 分页
  const handleSizeChange = (size) => {
    pageSize.value = size
    currentPage.value = 1
    loadConversations()
  }

  const handlePageChange = (page) => {
    currentPage.value = page
    loadConversations()
  }

  // 格式化时间
  const formatTime = (time) => {
    if (!time) return ''
    const date = new Date(time)
    const now = new Date()
    const diff = now - date
    const minutes = Math.floor(diff / 60000)
    const hours = Math.floor(diff / 3600000)
    const days = Math.floor(diff / 86400000)

    if (minutes < 1) return '刚刚'
    if (minutes < 60) return `${minutes}分钟前`
    if (hours < 24) return `${hours}小时前`
    if (days < 7) return `${days}天前`
    return date.toLocaleDateString()
  }

  // 格式化日期时间
  const formatDateTime = (time) => {
    if (!time) return ''
    return new Date(time).toLocaleString('zh-CN')
  }

  // 格式化消息内容（使用统一的 Markdown 渲染）
  const formatMessageContent = renderMarkdown

  onMounted(() => {
    loadConversations()
  })

  return {
    // 状态
    loading,
    conversations,
    searchKeyword,
    selectedType,
    selectedIds,
    selectedConversationId,
    currentPage,
    pageSize,
    total,
    editTitleDialogVisible,
    editTitle,
    showConversationDetail,
    conversationDetail,
    // 方法
    loadConversations,
    handleSearch,
    handleReset,
    handleSelectionChange,
    selectConversation,
    handleView,
    handleCreateConversation,
    handleEditTitle,
    handleSaveTitle,
    handleDelete,
    handleBatchDelete,
    handleContinueConversation,
    handleSizeChange,
    handlePageChange,
    formatTime,
    formatDateTime,
    formatMessageContent
  }
}
