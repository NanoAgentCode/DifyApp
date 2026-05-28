<template>
  <div class="user-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
        </div>
      </template>
      
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索用户名"
          clearable
          style="width: 300px"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="filterStatus"
          placeholder="筛选状态"
          clearable
          style="width: 150px; margin-left: 10px"
          @change="handleFilter"
        >
          <el-option label="全部" value="" />
          <el-option label="待审核" :value="0" />
          <el-option label="已激活" :value="1" />
          <el-option label="已禁用" :value="2" />
        </el-select>
        <el-select
          v-model="filterRole"
          placeholder="筛选角色"
          clearable
          style="width: 150px; margin-left: 10px"
          @change="handleFilter"
        >
          <el-option label="全部" value="" />
          <el-option label="管理员" :value="1" />
          <el-option label="普通用户" :value="2" />
        </el-select>
      </div>
      
      <div class="table-wrapper">
        <el-table
          :data="userList"
          v-loading="loading"
          style="width: 100%"
          row-key="id"
          border
          stripe
          fit
        >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="role" label="角色" min-width="120" align="center">
          <template #default="scope">
            <div class="role-tags">
              <el-tag
                v-for="role in scope.row.roles || []"
                :key="role.id"
                size="small"
                :type="role.roleCode === 'SUPER_ADMIN' ? 'danger' : role.roleCode === 'ADMIN' ? 'warning' : 'info'"
              >
                {{ role.roleName }}
              </el-tag>
              <el-tag v-if="!scope.row.roles || scope.row.roles.length === 0" size="small" type="info">
                {{ scope.row.role === 1 ? '管理员' : '普通用户' }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="应用管理" min-width="130" align="center">
          <template #default="{ row }">
            <el-dropdown 
              trigger="click" 
              placement="auto-start"
              :popper-options="{ strategy: 'fixed', modifiers: [{ name: 'preventOverflow', options: { boundary: 'viewport', padding: 10 } }] }"
              @visible-change="handleAppDropdownVisibleChange(row, $event)"
            >
              <el-button type="primary" size="small" :loading="row.loadingApps" plain>
                应用管理
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <div class="app-dropdown-menu">
                  <div class="app-dropdown-header">
                    <span class="header-title">应用可见性管理</span>
                    <span class="header-count">
                      {{ getVisibleCount(row.appVisibilities) }}/{{ row.appVisibilities?.length || 0 }}
                    </span>
                  </div>
                  <div v-if="row.role === 1" class="admin-tip">
                    <el-icon><InfoFilled /></el-icon>
                    <span>管理员拥有所有应用的访问权限，不可修改</span>
                  </div>
                  <div v-loading="row.loadingApps" class="app-dropdown-content">
                    <div
                      v-for="app in getPaginatedApps(row)"
                      :key="app.appId"
                      class="app-dropdown-item"
                    >
                      <div class="app-info">
                        <span class="app-name" :title="app.appName">{{ app.appName }}</span>
                        <el-tag 
                          :type="app.appType === 1 ? 'success' : 'info'" 
                          size="small"
                          class="app-type-tag"
                        >
                          {{ app.appType === 1 ? 'Chat' : 'Workflow' }}
                        </el-tag>
                      </div>
                      <el-switch
                        v-model="app.visible"
                        size="small"
                        :disabled="row.role === 1"
                        @change="handleAppVisibilityChange(row.id, app.appId, app.visible)"
                      />
                    </div>
                    <div 
                      v-if="!row.loadingApps && (!row.appVisibilities || row.appVisibilities.length === 0)" 
                      class="app-empty"
                    >
                      <span>暂无应用</span>
                    </div>
                  </div>
                  <div v-if="row.appVisibilities && row.appVisibilities.length > 10" class="app-dropdown-pagination">
                    <el-pagination
                      v-model:current-page="row.appPage"
                      :page-size="10"
                      :total="row.appVisibilities.length"
                      layout="prev, pager, next"
                      small
                    />
                  </div>
                </div>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <el-table-column label="知识管理" min-width="130" align="center">
          <template #default="{ row }">
            <el-dropdown 
              trigger="click" 
              placement="auto-start"
              :popper-options="{ strategy: 'fixed', modifiers: [{ name: 'preventOverflow', options: { boundary: 'viewport', padding: 10 } }] }"
              @visible-change="handleKbDropdownVisibleChange(row, $event)"
            >
              <el-button type="primary" size="small" :loading="row.loadingKbs" plain>
                知识管理
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <div class="app-dropdown-menu">
                  <div class="app-dropdown-header">
                    <span class="header-title">知识库可见性管理</span>
                    <span class="header-count">
                      {{ getKbVisibleCount(row.kbVisibilities) }}/{{ row.kbVisibilities?.length || 0 }}
                    </span>
                  </div>
                  <div v-if="row.role === 1" class="admin-tip">
                    <el-icon><InfoFilled /></el-icon>
                    <span>管理员拥有所有知识库的访问权限，不可修改</span>
                  </div>
                  <div v-loading="row.loadingKbs" class="app-dropdown-content">
                    <div
                      v-for="kb in getPaginatedKbs(row)"
                      :key="kb.knowledgeBaseId"
                      class="app-dropdown-item"
                    >
                      <div class="app-info">
                        <span class="app-name" :title="kb.knowledgeBaseName">{{ kb.knowledgeBaseName }}</span>
                        <el-tag 
                          :type="kb.knowledgeBaseStatus === 1 ? 'success' : 'info'" 
                          size="small"
                          class="app-type-tag"
                        >
                          {{ kb.knowledgeBaseStatus === 1 ? '启用' : '禁用' }}
                        </el-tag>
                      </div>
                      <el-switch
                        v-model="kb.visible"
                        size="small"
                        :disabled="row.role === 1"
                        @change="handleKbVisibilityChange(row.id, kb.knowledgeBaseId, kb.visible)"
                      />
                    </div>
                    <div 
                      v-if="!row.loadingKbs && (!row.kbVisibilities || row.kbVisibilities.length === 0)" 
                      class="app-empty"
                    >
                      <span>暂无知识库</span>
                    </div>
                  </div>
                  <div v-if="row.kbVisibilities && row.kbVisibilities.length > 10" class="app-dropdown-pagination">
                    <el-pagination
                      v-model:current-page="row.kbPage"
                      :page-size="10"
                      :total="row.kbVisibilities.length"
                      layout="prev, pager, next"
                      small
                    />
                  </div>
                </div>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="100" align="center">
          <template #default="scope">
            <el-tooltip :content="getStatusText(scope.row.status)" placement="top">
              <el-icon :size="20" :color="getStatusColor(scope.row.status)">
                <Check v-if="scope.row.status === 1" />
                <Close v-else-if="scope.row.status === 2" />
                <Clock v-else-if="scope.row.status === 0" />
              </el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="180" align="center">
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="240" align="center" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-button
                v-if="scope.row.status === 0"
                type="success"
                size="small"
                @click="handleApprove(scope.row)"
              >
                审核通过
              </el-button>
              <el-tooltip
                v-if="scope.row.status === 1"
                :content="scope.row.role === 1 ? '管理员账号不能被禁用' : ''"
                :disabled="scope.row.role !== 1"
              >
                <el-button
                  type="danger"
                  size="small"
                  :disabled="scope.row.role === 1"
                  @click="handleDisable(scope.row)"
                >
                  禁用
                </el-button>
              </el-tooltip>
              <el-button
                v-if="scope.row.status === 2"
                type="success"
                size="small"
                @click="handleApprove(scope.row)"
              >
                激活
              </el-button>
              <el-button
                type="warning"
                size="small"
                @click="handleResetPassword(scope.row)"
              >
                重置
              </el-button>
              <el-button
                size="small"
                type="primary"
                plain
                :disabled="isSuperAdmin(scope.row)"
                @click="openRoleDialog(scope.row)"
              >
                角色
              </el-button>
              <el-dropdown trigger="click" placement="bottom-end">
                <el-button type="primary" size="small" plain>
                  更多
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="openMemoryDialog(scope.row)">查看记忆</el-dropdown-item>
                    <el-dropdown-item divided @click="handleClearMemory(scope.row)">清空记忆</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        </el-table>
      </div>
      
      <!-- 分页 -->
      <div class="pagination-fixed">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
    
    <ResetPasswordDialog
      v-model="showResetPasswordDialog"
      :user-info="currentUser"
      @success="handleResetPasswordSuccess"
    />

    <el-dialog
      v-model="showMemoryDialog"
      title="记忆管理"
      width="700px"
      class="memory-dialog"
      destroy-on-close
    >
      <template #header>
        <div class="dialog-header">
          <div class="dialog-title">记忆管理</div>
          <div class="dialog-subtitle">
            {{ memoryUser?.username ? `用户：${memoryUser.username}（ID: ${memoryUser.id}）` : '' }}
          </div>
        </div>
      </template>
      <div class="memory-dialog-body">
        <div class="memory-toolbar">
          <el-select v-model="memoryScopeType" size="small" style="width: 140px" @change="handleMemoryScopeChange">
            <el-option label="所有范围" value="all" />
            <el-option label="智能对话" value="chat" />
            <el-option label="知识问答" value="knowledge_base" />
            <el-option label="智能应用" value="app" />
          </el-select>
          <el-select v-model="memoryType" size="small" style="width: 140px" @change="handleMemoryTypeChange">
            <el-option label="全部类型" value="all" />
            <el-option label="长期记忆" value="long_term" />
            <el-option label="实体记忆" value="entity" />
          </el-select>
          <el-input
            v-model="memorySearch"
            placeholder="搜索 Key / 内容"
            clearable
            style="width: 280px"
            @input="handleMemorySearch"
          />
          <el-button type="warning" plain @click="handleClearMemoryFilters">
            重置
          </el-button>
          <el-button type="primary" plain :loading="memoryLoading" @click="loadUserMemory">刷新</el-button>
          <el-button type="danger" plain :disabled="memoryClearing" @click="handleClearMemoryInDialog">
            清空
          </el-button>
        </div>
        <div class="memory-table-wrapper">
          <el-table :data="pagedMemoryItems" v-loading="memoryLoading" border stripe style="width: 100%" height="100%">
            <el-table-column type="expand" width="44">
              <template #default="{ row }">
                <div style="display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 8px;">
                  <div style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                    <el-tag :type="row.memoryType === 'entity' ? 'info' : 'success'" size="small">
                      {{ row.memoryType === 'entity' ? '实体' : '长期' }}
                    </el-tag>
                    <el-tag style="margin-left: 8px;" size="small">
                      {{ row.scopeType || 'chat' }}
                    </el-tag>
                    <span style="margin-left: 8px;">{{ row.memoryKey }}</span>
                  </div>
                  <el-button size="small" plain @click="copyMemory(row)">复制内容</el-button>
                </div>
                <div class="memory-meta">
                  <span>首次记录：{{ formatDate(row.firstSeenTime) }}</span>
                  <span>最近提及：{{ formatDate(row.lastMentionedTime) }}</span>
                  <span>最近使用：{{ formatDate(row.lastAccessedTime) }}</span>
                  <span>使用次数：{{ row.accessCount || 0 }}</span>
                </div>
                <pre class="memory-content">{{ formatMemoryContent(row) }}</pre>
              </template>
            </el-table-column>
            <el-table-column prop="scopeType" label="Scope" width="140" align="center">
              <template #default="{ row }">
                <el-tag size="small">{{ row.scopeType || 'chat' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="memoryType" label="类型" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.memoryType === 'entity' ? 'info' : 'success'" size="small">
                  {{ row.memoryType === 'entity' ? '实体' : '长期' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="memoryKey" label="Key" min-width="160" show-overflow-tooltip />
            <el-table-column prop="importance" label="重要度" width="90" align="center" />
            <el-table-column prop="lastMentionedTime" label="最近提及" width="180" align="center">
              <template #default="{ row }">
                {{ formatDate(row.lastMentionedTime || row.updateTime) }}
              </template>
            </el-table-column>
          <el-table-column label="操作" width="100" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="danger" plain :disabled="memoryDeleting" @click="handleDeleteMemoryRow(row)">删除</el-button>
            </template>
          </el-table-column>
          </el-table>
        </div>
        <div class="memory-pagination">
          <el-pagination
            v-model:current-page="memoryPage"
            v-model:page-size="memoryPageSize"
            :page-sizes="[20, 50, 100, 200]"
            :total="filteredMemoryItems.length"
            layout="total, sizes, prev, pager, next"
            @size-change="handleMemoryPageSizeChange"
            @current-change="handleMemoryPageChange"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="showMemoryDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRoleDialog" title="分配角色" width="520px">
      <div class="assign-role-header">
        <span>{{ roleUser?.username }}</span>
        <el-tag v-if="roleUser && isSuperAdmin(roleUser)" type="danger" size="small">超级管理员</el-tag>
      </div>
      <el-select v-model="selectedRoleIds" multiple filterable style="width: 100%" placeholder="请选择角色">
        <el-option
          v-for="role in roleOptions"
          :key="role.id"
          :label="role.roleName"
          :value="role.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="showRoleDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingRoles" @click="saveUserRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, InfoFilled, Search, Check, Close, Clock } from '@element-plus/icons-vue'
import { getUserList, approveUser, disableUser, getUserAppVisibilities, updateUserAppVisibility, getUserKnowledgeBaseVisibilities, updateUserKnowledgeBaseVisibility, updateUserRole, clearUserMemory, getUserMemoryItems, deleteUserMemoryItem } from '@/api/user'
import { getRoles, getUserRoles, updateUserRoles } from '@/api/rbac'
import ResetPasswordDialog from '@/components/ResetPasswordDialog.vue'

const loading = ref(false)
const userList = ref([])
const showResetPasswordDialog = ref(false)
const currentUser = ref(null)
const searchKeyword = ref('')
const filterStatus = ref('')
const filterRole = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const showMemoryDialog = ref(false)
const memoryLoading = ref(false)
const memoryItems = ref([])
const memoryUser = ref(null)
const memoryType = ref('all')
const memorySearch = ref('')
const memoryPage = ref(1)
const memoryPageSize = ref(50)
const memoryScopeType = ref('all')
const memoryClearing = ref(false)
const memoryDeleting = ref(false)
const showRoleDialog = ref(false)
const roleUser = ref(null)
const roleOptions = ref([])
const selectedRoleIds = ref([])
const savingRoles = ref(false)

const filteredMemoryItems = computed(() => {
  const keyword = (memorySearch.value || '').trim().toLowerCase()
  const items = Array.isArray(memoryItems.value) ? memoryItems.value : []
  if (!keyword) return items
  return items.filter(item => {
    const key = (item.memoryKey || '').toString().toLowerCase()
    const content = (item.content || '').toString().toLowerCase()
    return key.includes(keyword) || content.includes(keyword)
  })
})

const pagedMemoryItems = computed(() => {
  const page = Math.max(1, memoryPage.value)
  const size = Math.max(1, memoryPageSize.value)
  const start = (page - 1) * size
  const end = start + size
  return filteredMemoryItems.value.slice(start, end)
})

const loadUsers = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }
    if (filterStatus.value !== '') {
      params.status = filterStatus.value
    }
    if (filterRole.value !== '') {
      params.role = filterRole.value
    }
    const response = await getUserList(params)
    
    // 检查是否是分页响应
    if (response && typeof response === 'object' && 'content' in response && 'total' in response) {
      // 分页响应
      userList.value = response.content || []
      total.value = response.total || 0
    } else {
      // 兼容旧接口（非分页响应）
      userList.value = Array.isArray(response) ? response : []
      total.value = userList.value.length
    }
    
    // 初始化应用列表和知识库列表为空，延迟加载
    userList.value.forEach(user => {
      user.appVisibilities = []
      user.loadingApps = false
      user.appPage = 1 // 初始化应用分页
      user.kbVisibilities = []
      user.loadingKbs = false
      user.kbPage = 1 // 初始化知识库分页
    })
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const loadRoles = async () => {
  try {
    roleOptions.value = await getRoles()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '获取角色失败')
  }
}

const handleApprove = async (user) => {
  try {
    await ElMessageBox.confirm(
      `确定要${user.status === 0 ? '审核通过' : '激活'}用户 "${user.username}" 吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await approveUser(user.id)
    ElMessage.success('操作成功')
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.error || error.message || '操作失败')
    }
  }
}

const handleDisable = async (user) => {
  try {
    await ElMessageBox.confirm(
      `确定要禁用用户 "${user.username}" 吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await disableUser(user.id)
    ElMessage.success('操作成功')
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.error || error.message || '操作失败')
    }
  }
}

const handleResetPassword = (user) => {
  currentUser.value = user
  showResetPasswordDialog.value = true
}

const handleResetPasswordSuccess = () => {
  loadUsers()
}

const openMemoryDialog = async (user) => {
  memoryUser.value = user
  memoryItems.value = []
  memoryType.value = 'all'
  memorySearch.value = ''
  memoryPage.value = 1
  memoryPageSize.value = 50
  memoryScopeType.value = 'all'
  showMemoryDialog.value = true
  await loadUserMemory()
}

const loadUserMemory = async () => {
  if (!memoryUser.value) return
  memoryLoading.value = true
  try {
    const params = { page: 1, size: 200 }
    if (memoryType.value !== 'all') params.type = memoryType.value
    if (memoryScopeType.value !== 'all') {
      params.scopeType = memoryScopeType.value
    }
    const data = await getUserMemoryItems(memoryUser.value.id, params)
    memoryItems.value = Array.isArray(data) ? data : []
    memoryPage.value = 1
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '获取用户记忆失败')
    memoryItems.value = []
  } finally {
    memoryLoading.value = false
  }
}

const handleMemorySearch = () => {
  memoryPage.value = 1
}

const handleMemoryScopeChange = async () => {
  memoryPage.value = 1
  await loadUserMemory()
}

const handleMemoryTypeChange = async () => {
  memoryPage.value = 1
  await loadUserMemory()
}

const handleClearMemoryFilters = () => {
  memorySearch.value = ''
  memoryScopeType.value = 'all'
  memoryType.value = 'all'
  memoryPage.value = 1
  memoryPageSize.value = 50
  ElMessage.success('查询条件已重置')
}

const handleClearMemoryInDialog = async () => {
  if (!memoryUser.value) return

  let message = `确定要清空用户 "${memoryUser.value.username}" 的记忆吗？`
  if (memoryScopeType.value === 'chat') {
    message = `确定要清空用户 "${memoryUser.value.username}" 的 Chat 记忆吗？`
  } else if (memoryScopeType.value === 'knowledge_base') {
    message = `确定要清空用户 "${memoryUser.value.username}" 的知识库记忆吗？`
  } else if (memoryScopeType.value === 'app') {
    message = `确定要清空用户 "${memoryUser.value.username}" 的应用记忆吗？`
  }

  try {
    await ElMessageBox.confirm(message, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  const params = {}
  if (memoryScopeType.value !== 'all') {
    params.scopeType = memoryScopeType.value
  }

  memoryClearing.value = true
  try {
    await clearUserMemory(memoryUser.value.id, Object.keys(params).length ? params : undefined)
    ElMessage.success('已清空用户记忆')
    await loadUserMemory()
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '清空失败')
  } finally {
    memoryClearing.value = false
  }
}

const handleMemoryPageChange = (page) => {
  memoryPage.value = page
}

const handleMemoryPageSizeChange = (size) => {
  memoryPageSize.value = size
  memoryPage.value = 1
}

const makeMemorySnippet = (content) => {
  if (!content) return ''
  const text = String(content).replace(/\s+/g, ' ').trim()
  if (text.length <= 120) return text
  return text.slice(0, 119) + '…'
}

const formatMemoryContent = (row) => {
  if (!row || !row.content) return ''
  if (row.memoryType === 'entity') {
    try {
      const obj = JSON.parse(row.content)
      return JSON.stringify(obj, null, 2)
    } catch (e) {
      return String(row.content)
    }
  }
  return String(row.content)
}

const copyText = async (text) => {
  const value = text == null ? '' : String(text)
  try {
    await navigator.clipboard.writeText(value)
    return true
  } catch (e) {
    try {
      const textarea = document.createElement('textarea')
      textarea.value = value
      textarea.style.position = 'fixed'
      textarea.style.left = '-9999px'
      textarea.style.top = '-9999px'
      document.body.appendChild(textarea)
      textarea.focus()
      textarea.select()
      const ok = document.execCommand('copy')
      document.body.removeChild(textarea)
      return ok
    } catch (err) {
      return false
    }
  }
}

const copyMemory = async (row) => {
  const ok = await copyText(formatMemoryContent(row))
  if (ok) ElMessage.success('已复制')
  else ElMessage.error('复制失败')
}

const handleDeleteMemoryRow = async (row) => {
  if (!memoryUser.value) return
  const id = row?.id
  if (!id) {
    ElMessage.error('缺少记忆ID')
    return
  }

  try {
    await ElMessageBox.confirm('确定要删除这条记忆吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }

  memoryDeleting.value = true
  try {
    await deleteUserMemoryItem(memoryUser.value.id, id)
    ElMessage.success('已删除')
    await loadUserMemory()
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '删除失败')
  } finally {
    memoryDeleting.value = false
  }
}

const handleClearMemory = async (user) => {
  try {
    await ElMessageBox.confirm(
      `确定要清空用户 "${user.username}" 的长期记忆与实体记忆吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await clearUserMemory(user.id)
    ElMessage.success('已清空用户记忆')
    if (showMemoryDialog.value && memoryUser.value && memoryUser.value.id === user.id) {
      await loadUserMemory()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.error || error.message || '清空失败')
    }
  }
}

const loadUserAppVisibilities = async (user) => {
  user.loadingApps = true
  try {
    const data = await getUserAppVisibilities(user.id)
    user.appVisibilities = data || []
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '获取应用列表失败')
    user.appVisibilities = []
  } finally {
    user.loadingApps = false
  }
}

const handleAppDropdownVisibleChange = async (user, visible) => {
  // 重置分页
  if (visible) {
    user.appPage = 1
  }
  // 当下拉菜单打开时，如果应用列表未加载，则加载
  if (visible && (!user.appVisibilities || user.appVisibilities.length === 0)) {
    await loadUserAppVisibilities(user)
  }
}

const handleKbDropdownVisibleChange = async (user, visible) => {
  // 重置分页
  if (visible) {
    user.kbPage = 1
  }
  // 当下拉菜单打开时，如果知识库列表未加载，则加载
  if (visible && (!user.kbVisibilities || user.kbVisibilities.length === 0)) {
    await loadUserKnowledgeBaseVisibilities(user)
  }
}

const loadUserKnowledgeBaseVisibilities = async (user) => {
  user.loadingKbs = true
  try {
    const data = await getUserKnowledgeBaseVisibilities(user.id)
    user.kbVisibilities = data || []
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '获取知识库列表失败')
      user.kbVisibilities = []
    } finally {
      user.loadingKbs = false
    }
  }

const getKbTypeTag = (type) => {
  const tagMap = {
    'knowledge-base': 'primary',
    mysql: 'success',
    oracle: 'warning',
    mongodb: 'info'
  }
  return tagMap[type] || ''
}

const handleAppVisibilityChange = async (userId, appId, visible) => {
  // 检查是否是管理员
  const user = userList.value.find(u => u.id === userId)
  if (user && user.role === 1) {
    ElMessage.warning('管理员拥有所有应用的访问权限，不可修改')
    // 恢复原状态
    if (user.appVisibilities) {
      const app = user.appVisibilities.find(a => a.appId === appId)
      if (app) {
        app.visible = !visible
      }
    }
    return
  }
  
  try {
    await updateUserAppVisibility(userId, appId, visible)
    ElMessage.success(visible ? '应用已设为可见' : '应用已设为不可见')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '更新失败')
    // 恢复原状态
    if (user && user.appVisibilities) {
      const app = user.appVisibilities.find(a => a.appId === appId)
      if (app) {
        app.visible = !visible
      }
    }
  }
}

const isSuperAdmin = (user) => {
  // 判断是否是超级管理员：username为"admin"或id为1的用户
  return user.username === 'admin' || user.id === 1
}

const handleRoleChange = async (user) => {
  // 检查是否是超级管理员
  if (isSuperAdmin(user)) {
    ElMessage.warning('超级管理员的角色不能被修改')
    // 恢复原角色
    await loadUsers()
    return
  }
  
  try {
    await updateUserRole(user.id, user.role)
    ElMessage.success('角色修改成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '修改失败')
    // 恢复原角色
    await loadUsers()
  }
}

const openRoleDialog = async (user) => {
  if (isSuperAdmin(user)) {
    ElMessage.warning('超级管理员的角色不能被修改')
    return
  }
  roleUser.value = user
  if (roleOptions.value.length === 0) {
    await loadRoles()
  }
  const roles = await getUserRoles(user.id)
  selectedRoleIds.value = (roles || []).map(role => role.id)
  showRoleDialog.value = true
}

const saveUserRoles = async () => {
  if (!roleUser.value) return
  if (!selectedRoleIds.value.length) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  savingRoles.value = true
  try {
    await updateUserRoles(roleUser.value.id, selectedRoleIds.value)
    ElMessage.success('用户角色已保存')
    showRoleDialog.value = false
    await loadUsers()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存失败')
  } finally {
    savingRoles.value = false
  }
}

const handleKbVisibilityChange = async (userId, knowledgeBaseId, visible) => {
  // 检查是否是管理员
  const user = userList.value.find(u => u.id === userId)
  if (user && user.role === 1) {
    ElMessage.warning('管理员拥有所有知识库的访问权限，不可修改')
    // 恢复原状态
    if (user.kbVisibilities) {
      const kb = user.kbVisibilities.find(k => k.knowledgeBaseId === knowledgeBaseId)
      if (kb) {
        kb.visible = !visible
      }
    }
    return
  }
  
  try {
    await updateUserKnowledgeBaseVisibility(userId, knowledgeBaseId, visible)
    ElMessage.success(visible ? '知识库已设为可见' : '知识库已设为不可见')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '更新失败')
    // 恢复原状态
    if (user && user.kbVisibilities) {
      const kb = user.kbVisibilities.find(k => k.knowledgeBaseId === knowledgeBaseId)
      if (kb) {
        kb.visible = !visible
      }
    }
  }
}

const getVisibleCount = (appVisibilities) => {
  if (!appVisibilities || appVisibilities.length === 0) return 0
  return appVisibilities.filter(app => app.visible).length
}

const getKbVisibleCount = (kbVisibilities) => {
  if (!kbVisibilities || kbVisibilities.length === 0) return 0
  return kbVisibilities.filter(kb => kb.visible).length
}

// 获取分页后的应用列表
const getPaginatedApps = (row) => {
  if (!row.appVisibilities || row.appVisibilities.length === 0) return []
  const page = row.appPage || 1
  const pageSize = 10
  const start = (page - 1) * pageSize
  const end = start + pageSize
  return row.appVisibilities.slice(start, end)
}

// 获取分页后的知识库列表
const getPaginatedKbs = (row) => {
  if (!row.kbVisibilities || row.kbVisibilities.length === 0) return []
  const page = row.kbPage || 1
  const pageSize = 10
  const start = (page - 1) * pageSize
  const end = start + pageSize
  return row.kbVisibilities.slice(start, end)
}

// 获取状态文本
const getStatusText = (status) => {
  if (status === 0) return '待审核'
  if (status === 1) return '已激活'
  if (status === 2) return '已禁用'
  return '未知'
}

// 获取状态颜色
const getStatusColor = (status) => {
  if (status === 0) return '#e6a23c' // 橙色 - 待审核
  if (status === 1) return '#67c23a' // 绿色 - 已激活
  if (status === 2) return '#f56c6c' // 红色 - 已禁用
  return '#909399' // 灰色 - 未知
}

const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleString('zh-CN')
}

const handleSearch = () => {
  currentPage.value = 1
  loadUsers()
}

const handleFilter = () => {
  currentPage.value = 1
  loadUsers()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadUsers()
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadUsers()
}

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.user-list {
  padding: var(--spacing-lg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-secondary);
}

/* ========== 卡片样式 ========== */
:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
  border-radius: var(--card-border-radius);
  box-shadow: var(--card-shadow);
  transition: box-shadow var(--transition-base);
}

:deep(.el-card:hover) {
  box-shadow: var(--card-shadow-hover);
}

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: var(--spacing-md) var(--card-padding);
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-lighter);
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: var(--card-padding);
  min-height: 0;
  background: var(--color-bg-primary);
}

/* ========== 卡片头部 ========== */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header span {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

/* ========== 搜索栏 ========== */
.search-bar {
  margin-bottom: var(--spacing-lg);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

.search-bar :deep(.el-input__wrapper) {
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.search-bar :deep(.el-input__wrapper:hover) {
  box-shadow: var(--shadow-xs);
}

.search-bar :deep(.el-input__wrapper.is-focus) {
  box-shadow: var(--shadow-primary);
}

.search-bar :deep(.el-select) {
  transition: all var(--transition-base);
}

.search-bar :deep(.el-select:hover .el-input__wrapper) {
  box-shadow: var(--shadow-xs);
}

/* ========== 表格容器 ========== */
.table-wrapper {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-bottom: 80px; /* 为固定分页留出空间 */
}

:deep(.el-table) {
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--color-bg-primary);
}

:deep(.el-table__header) {
  background: var(--table-header-bg);
}

:deep(.el-table th) {
  background: var(--table-header-bg);
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
  border-bottom: 2px solid var(--color-border-base);
}

:deep(.el-table td) {
  border-bottom: 1px solid var(--table-border-color);
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background-color: var(--color-bg-tertiary);
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--table-row-hover-bg);
  transition: background-color var(--transition-fast);
}

/* ========== 下拉菜单样式 ========== */
.app-dropdown-menu {
  min-width: 360px;
  max-width: 400px;
  background: var(--color-bg-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  border: 1px solid var(--color-border-lighter);
}

.app-dropdown-header {
  padding: var(--spacing-md);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border-lighter);
}

.header-title {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
}

.header-count {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  background: var(--color-bg-primary);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-full);
  font-weight: var(--font-weight-medium);
}

.app-dropdown-content {
  padding: 4px 0;
  min-height: 484px; /* 固定最小高度：10个项目(440px) + 提示信息高度(约44px) */
  max-height: calc(100vh - 250px); /* 动态计算最大高度，确保不超出视口，为分页留出空间 */
  overflow-y: auto;
  overflow-x: hidden;
}

.app-dropdown-pagination {
  padding: 8px 16px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  justify-content: center;
  background: #fafafa;
}

.app-dropdown-pagination :deep(.el-pagination) {
  justify-content: center;
}

.app-dropdown-pagination :deep(.el-pagination .el-pager li) {
  min-width: 28px;
  height: 28px;
  line-height: 28px;
  font-size: 12px;
}

.app-dropdown-pagination :deep(.el-pagination .btn-prev),
.app-dropdown-pagination :deep(.el-pagination .btn-next) {
  width: 28px;
  height: 28px;
  line-height: 28px;
}

.app-dropdown-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md);
  transition: all var(--transition-base);
  border-bottom: 1px solid var(--color-border-extra-light);
}

.app-dropdown-item:last-child {
  border-bottom: none;
}

.app-dropdown-item:hover {
  background-color: var(--color-bg-hover);
  transform: translateX(2px);
}

.app-info {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: 8px;
}

.app-name {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.app-type-tag {
  flex-shrink: 0;
}

.app-empty {
  padding: 40px 20px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.app-dropdown-pagination {
  padding: 8px 16px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  justify-content: center;
  background: #fafafa;
}

.app-dropdown-pagination :deep(.el-pagination) {
  justify-content: center;
}

.app-dropdown-pagination :deep(.el-pagination .el-pager li) {
  min-width: 28px;
  height: 28px;
  line-height: 28px;
  font-size: 12px;
}

.app-dropdown-pagination :deep(.el-pagination .btn-prev),
.app-dropdown-pagination :deep(.el-pagination .btn-next) {
  width: 28px;
  height: 28px;
  line-height: 28px;
}

.admin-tip {
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-primary-light-5);
  border-left: 4px solid var(--color-primary);
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--color-primary-dark-1);
  font-size: var(--font-size-xs);
  margin: var(--spacing-sm) 0;
  border-radius: var(--radius-sm);
}

.admin-tip .el-icon {
  font-size: 16px;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
  align-items: center;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}

.assign-role-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
}

.search-bar {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.pagination-fixed {
  position: fixed;
  bottom: var(--spacing-lg);
  right: var(--spacing-lg);
  z-index: var(--z-fixed);
  background: var(--color-bg-primary);
  padding: var(--spacing-sm) var(--spacing-lg);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--color-border-lighter);
}

.pagination {
  margin-top: var(--spacing-lg);
  display: flex;
  justify-content: flex-end;
}

:deep(.el-pagination .el-pager li) {
  border-radius: var(--radius-sm);
  transition: all var(--transition-base);
}

:deep(.el-pagination .el-pager li:hover) {
  background-color: var(--color-bg-hover);
}

:deep(.el-pagination .el-pager li.is-active) {
  background-color: var(--color-primary);
  color: #ffffff;
}

.memory-content {
  max-height: 320px;
  overflow: auto;
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  background: var(--color-bg-tertiary);
  white-space: pre-wrap;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
  margin: 0;
}

.memory-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin: 0 0 8px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
}

.memory-snippet {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.memory-dialog .el-dialog__body) {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

:deep(.memory-dialog .el-dialog) {
  height: 720px;
  display: flex;
  flex-direction: column;
}

:deep(.memory-dialog .el-tabs__header) {
  margin: 0 0 8px 0;
}

.dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dialog-title {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
}

.dialog-subtitle {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.memory-dialog-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.memory-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-shrink: 0;
  min-height: 40px;
  margin-bottom: 8px;
}

.memory-tabs {
  flex-shrink: 0;
}

.memory-table-wrapper {
  flex: 1;
  min-height: 0;
}

.memory-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  flex-shrink: 0;
}

:deep(.memory-dialog .el-table__body-wrapper .el-scrollbar__wrap) {
  overflow-y: scroll;
}
</style>

