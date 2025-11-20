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
        :expand-row-keys="expandedRows"
        @expand-change="handleExpandChange"
      >
        <el-table-column type="expand" width="50">
          <template #default="{ row }">
            <div class="app-visibility-container">
              <div v-loading="row.loadingApps" class="app-list">
                <div
                  v-for="app in row.appVisibilities"
                  :key="app.appId"
                  class="app-item"
                >
                  <div class="app-info">
                    <span class="app-name">{{ app.appName }}</span>
                    <el-tag :type="app.appType === 1 ? 'success' : 'info'" size="small" style="margin-left: 8px">
                      {{ app.appType === 1 ? 'Chat Flow' : 'Workflow' }}
                    </el-tag>
                  </div>
                  <el-switch
                    v-model="app.visible"
                    @change="handleVisibilityChange(row.id, app.appId, app.visible)"
                  />
                </div>
                <el-empty v-if="!row.loadingApps && (!row.appVisibilities || row.appVisibilities.length === 0)" description="暂无应用" :image-size="80" />
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="role" label="角色" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.role === 1" type="danger">管理员</el-tag>
            <el-tag v-else type="info">普通用户</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 0" type="warning">待审核</el-tag>
            <el-tag v-else-if="scope.row.status === 1" type="success">已激活</el-tag>
            <el-tag v-else type="danger">已禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="scope">
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
import { getUserList, approveUser, disableUser, getUserAppVisibilities, updateUserAppVisibility } from '@/api/user'
import ResetPasswordDialog from '@/components/ResetPasswordDialog.vue'

const loading = ref(false)
const userList = ref([])
const showResetPasswordDialog = ref(false)
const currentUser = ref(null)
const expandedRows = ref([])

const loadUsers = async () => {
  loading.value = true
  try {
    const data = await getUserList()
    userList.value = data
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

const handleExpandChange = async (row, expandedRows) => {
  if (expandedRows.includes(row.id)) {
    // 展开时加载应用列表
    if (!row.appVisibilities) {
      await loadUserAppVisibilities(row)
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

const handleVisibilityChange = async (userId, appId, visible) => {
  try {
    await updateUserAppVisibility(userId, appId, visible)
    ElMessage.success(visible ? '应用已设为可见' : '应用已设为不可见')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '更新失败')
    // 恢复原状态
    const user = userList.value.find(u => u.id === userId)
    if (user && user.appVisibilities) {
      const app = user.appVisibilities.find(a => a.appId === appId)
      if (app) {
        app.visible = !visible
      }
    }
  }
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

.app-visibility-container {
  padding: 20px;
  background: #f5f7fa;
}

.app-list {
  min-height: 100px;
}

.app-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  margin-bottom: 8px;
  background: white;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.app-info {
  display: flex;
  align-items: center;
  flex: 1;
}

.app-name {
  font-weight: 500;
  color: #303133;
}
</style>

