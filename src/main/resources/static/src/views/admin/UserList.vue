<template>
  <div class="user-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
        </div>
      </template>
      
      <el-table
        :data="userList"
        v-loading="loading"
        style="width: 100%"
        row-key="id"
        border
        stripe
        fit
      >
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
        <el-table-column label="应用管理" min-width="130" align="center">
          <template #default="{ row }">
            <el-dropdown 
              trigger="click" 
              placement="bottom-start"
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
                      v-for="app in row.appVisibilities"
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
                </div>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <el-table-column label="知识库管理" min-width="130" align="center">
          <template #default="{ row }">
            <el-dropdown 
              trigger="click" 
              placement="bottom-start"
              @visible-change="handleKbDropdownVisibleChange(row, $event)"
            >
              <el-button type="success" size="small" :loading="row.loadingKbs" plain>
                知识库管理
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
                      v-for="kb in row.kbVisibilities"
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
                </div>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <el-table-column prop="role" label="角色" min-width="120" align="center">
          <template #default="scope">
            <el-select
              v-model="scope.row.role"
              size="small"
              :disabled="isSuperAdmin(scope.row)"
              @change="handleRoleChange(scope.row)"
              style="width: 100px"
            >
              <el-option label="管理员" :value="1" />
              <el-option label="普通用户" :value="2" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 0" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="scope.row.status === 1" type="success" size="small">已激活</el-tag>
            <el-tag v-else type="danger" size="small">已禁用</el-tag>
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
                重置密码
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <ResetPasswordDialog
      v-model="showResetPasswordDialog"
      :user-info="currentUser"
      @success="handleResetPasswordSuccess"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, InfoFilled } from '@element-plus/icons-vue'
import { getUserList, approveUser, disableUser, getUserAppVisibilities, updateUserAppVisibility, getUserKnowledgeBaseVisibilities, updateUserKnowledgeBaseVisibility, updateUserRole } from '@/api/user'
import ResetPasswordDialog from '@/components/ResetPasswordDialog.vue'

const loading = ref(false)
const userList = ref([])
const showResetPasswordDialog = ref(false)
const currentUser = ref(null)

const loadUsers = async () => {
  loading.value = true
  try {
    const data = await getUserList()
    userList.value = data
    // 初始化应用列表和知识库列表为空，延迟加载
    data.forEach(user => {
      user.appVisibilities = []
      user.loadingApps = false
      user.kbVisibilities = []
      user.loadingKbs = false
    })
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '获取用户列表失败')
  } finally {
    loading.value = false
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
  // 当下拉菜单打开时，如果应用列表未加载，则加载
  if (visible && (!user.appVisibilities || user.appVisibilities.length === 0)) {
    await loadUserAppVisibilities(user)
  }
}

const handleKbDropdownVisibleChange = async (user, visible) => {
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

const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleString('zh-CN')
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-list {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.app-dropdown-menu {
  min-width: 360px;
  max-width: 400px;
  background: white;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.app-dropdown-header {
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.header-title {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}

.header-count {
  color: #909399;
  font-size: 12px;
  background: white;
  padding: 2px 8px;
  border-radius: 10px;
}

.app-dropdown-content {
  padding: 4px 0;
  max-height: 400px;
  overflow-y: auto;
}

.app-dropdown-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  transition: background-color 0.2s;
  border-bottom: 1px solid #f0f0f0;
}

.app-dropdown-item:last-child {
  border-bottom: none;
}

.app-dropdown-item:hover {
  background-color: #f5f7fa;
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

.admin-tip {
  padding: 10px 16px;
  background: #ecf5ff;
  border-left: 4px solid #409eff;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #409eff;
  font-size: 12px;
  margin: 8px 0;
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
</style>

