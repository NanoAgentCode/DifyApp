<template>
  <div class="access-management">
    <el-tabs v-model="activeTab" class="access-tabs" @tab-change="handleTabChange">
      <el-tab-pane v-if="canManageUsers" label="用户管理" name="users">
        <UserList />
      </el-tab-pane>
      <el-tab-pane v-if="canManageRoles" label="角色管理" name="roles">
        <RoleManagement />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UserList from './UserList.vue'
import RoleManagement from './RoleManagement.vue'
import { hasPermission } from '@/utils/permission'

const route = useRoute()
const router = useRouter()
const activeTab = ref('users')

const canManageUsers = computed(() => hasPermission('admin.users'))
const canManageRoles = computed(() => hasPermission('admin.roles'))

const normalizeTab = (tab) => {
  if (tab === 'roles' && canManageRoles.value) return 'roles'
  if (tab === 'users' && canManageUsers.value) return 'users'
  if (canManageUsers.value) return 'users'
  if (canManageRoles.value) return 'roles'
  return 'users'
}

const syncTabFromRoute = () => {
  activeTab.value = normalizeTab(route.query.tab)
}

const handleTabChange = (tabName) => {
  router.replace({
    path: '/admin/users',
    query: tabName === 'roles' ? { tab: 'roles' } : {}
  })
}

onMounted(syncTabFromRoute)

watch(() => route.query.tab, syncTabFromRoute)
</script>

<style scoped>
.access-management {
  height: 100%;
  min-height: 0;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  background: var(--color-bg-secondary);
  box-sizing: border-box;
  overflow: hidden;
}

.access-tabs {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.access-tabs :deep(.el-tabs__header) {
  flex-shrink: 0;
  margin: 0;
  padding: 0 var(--spacing-lg);
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

.access-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 0;
}

.access-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  width: 100%;
}

.access-tabs :deep(.el-tab-pane) {
  height: 100%;
  min-height: 0;
}

.access-tabs :deep(.user-list),
.access-tabs :deep(.role-management) {
  width: 100%;
  padding: 0;
  background: transparent;
  box-sizing: border-box;
}

@media (max-width: 900px) {
  .access-management {
    padding: var(--spacing-md);
  }

  .access-tabs {
    gap: var(--spacing-sm);
  }

  .access-tabs :deep(.el-tabs__header) {
    padding: 0 var(--spacing-md);
    overflow-x: auto;
  }
}
</style>
