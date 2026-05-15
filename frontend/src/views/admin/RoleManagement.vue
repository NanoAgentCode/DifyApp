<template>
  <div class="role-management">
    <div class="toolbar">
      <div>
        <h2>角色管理</h2>
        <p>按角色分配用户可访问的菜单和模块</p>
      </div>
      <el-button type="primary" @click="openRoleDialog()">
        <el-icon><Plus /></el-icon>
        新增角色
      </el-button>
    </div>

    <div class="content">
      <el-table :data="roles" v-loading="loading" border stripe height="100%">
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleCode" label="角色编码" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限数量" width="110" align="center">
          <template #default="{ row }">{{ row.permissionCodes?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openRoleDialog(row)">编辑</el-button>
            <el-button size="small" type="primary" plain @click="openPermissionDialog(row)">授权</el-button>
            <el-button size="small" type="danger" plain :disabled="row.systemRole" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="roleDialogVisible" :title="editingRole?.id ? '编辑角色' : '新增角色'" width="520px">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="roleForm.roleCode" :disabled="editingRole?.systemRole" placeholder="例如 DATA_OPERATOR" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="roleEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="roleForm.sortOrder" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionDialogVisible" title="模块授权" width="640px">
      <div class="permission-title">
        <span>{{ currentRole?.roleName }}</span>
        <el-tag v-if="currentRole?.systemRole" size="small">系统角色</el-tag>
      </div>
      <el-tree
        ref="permissionTreeRef"
        :data="permissionTree"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'label', children: 'children' }"
        class="permission-tree"
      />
      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPermissions" :disabled="currentRole?.roleCode === 'SUPER_ADMIN'" @click="savePermissions">
          保存授权
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createRole,
  deleteRole,
  getPermissions,
  getRolePermissionIds,
  getRoles,
  updateRole,
  updateRolePermissions
} from '@/api/rbac'

const loading = ref(false)
const saving = ref(false)
const savingPermissions = ref(false)
const roles = ref([])
const permissions = ref([])
const roleDialogVisible = ref(false)
const permissionDialogVisible = ref(false)
const editingRole = ref(null)
const currentRole = ref(null)
const roleFormRef = ref(null)
const permissionTreeRef = ref(null)

const roleForm = reactive({
  roleCode: '',
  roleName: '',
  description: '',
  status: 1,
  sortOrder: 100
})

const roleEnabled = computed({
  get: () => roleForm.status === 1,
  set: value => {
    roleForm.status = value ? 1 : 0
  }
})

const roleRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const permissionTree = computed(() => {
  const groups = [
    { id: 'admin-root', label: '管理端模块', clientType: 'admin', children: [] },
    { id: 'user-root', label: '用户端模块', clientType: 'user', children: [] }
  ]
  permissions.value.forEach(item => {
    const group = groups.find(g => g.clientType === item.clientType)
    if (group) {
      group.children.push({
        id: item.id,
        label: item.permissionName,
        permissionCode: item.permissionCode
      })
    }
  })
  return groups.filter(group => group.children.length > 0)
})

const loadData = async () => {
  loading.value = true
  try {
    const [roleData, permissionData] = await Promise.all([getRoles(), getPermissions()])
    roles.value = roleData || []
    permissions.value = permissionData || []
  } finally {
    loading.value = false
  }
}

const openRoleDialog = (role) => {
  editingRole.value = role || null
  roleForm.roleCode = role?.roleCode || ''
  roleForm.roleName = role?.roleName || ''
  roleForm.description = role?.description || ''
  roleForm.status = role?.status ?? 1
  roleForm.sortOrder = role?.sortOrder ?? 100
  roleDialogVisible.value = true
}

const saveRole = async () => {
  if (!roleFormRef.value) return
  const valid = await roleFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...roleForm }
    if (editingRole.value?.id) {
      await updateRole(editingRole.value.id, payload)
    } else {
      await createRole(payload)
    }
    ElMessage.success('角色已保存')
    roleDialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

const openPermissionDialog = async (role) => {
  currentRole.value = role
  permissionDialogVisible.value = true
  const checkedIds = await getRolePermissionIds(role.id)
  await nextTick()
  permissionTreeRef.value?.setCheckedKeys(checkedIds || [])
}

const savePermissions = async () => {
  if (!currentRole.value) return
  const checkedKeys = permissionTreeRef.value?.getCheckedKeys(false) || []
  const permissionIds = checkedKeys.filter(key => typeof key === 'number')
  savingPermissions.value = true
  try {
    await updateRolePermissions(currentRole.value.id, permissionIds)
    ElMessage.success('授权已保存')
    permissionDialogVisible.value = false
    await loadData()
  } finally {
    savingPermissions.value = false
  }
}

const handleDelete = async (role) => {
  try {
    await ElMessageBox.confirm(`确定删除角色 "${role.roleName}" 吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteRole(role.id)
    ElMessage.success('角色已删除')
    await loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || error.message || '删除失败')
    }
  }
}

onMounted(loadData)
</script>

<style scoped>
.role-management {
  height: 100%;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  background: var(--color-bg-secondary);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 64px;
}

.toolbar h2 {
  margin: 0;
  font-size: var(--font-size-xl);
  color: var(--color-text-primary);
}

.toolbar p {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.content {
  flex: 1;
  min-height: 0;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
}

.permission-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  font-weight: var(--font-weight-semibold);
}

.permission-tree {
  max-height: 460px;
  overflow: auto;
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  padding: var(--spacing-sm);
}
</style>
