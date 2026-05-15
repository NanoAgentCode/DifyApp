import request from '@/utils/request'

export function getMyPermissions() {
  return request({
    url: '/api/rbac/my-permissions',
    method: 'get'
  })
}

export function getPermissions() {
  return request({
    url: '/api/rbac/permissions',
    method: 'get'
  })
}

export function getRoles() {
  return request({
    url: '/api/rbac/roles',
    method: 'get'
  })
}

export function createRole(data) {
  return request({
    url: '/api/rbac/roles',
    method: 'post',
    data
  })
}

export function updateRole(roleId, data) {
  return request({
    url: `/api/rbac/roles/${roleId}`,
    method: 'put',
    data
  })
}

export function deleteRole(roleId) {
  return request({
    url: `/api/rbac/roles/${roleId}`,
    method: 'delete'
  })
}

export function getRolePermissionIds(roleId) {
  return request({
    url: `/api/rbac/roles/${roleId}/permissions`,
    method: 'get'
  })
}

export function updateRolePermissions(roleId, permissionIds) {
  return request({
    url: `/api/rbac/roles/${roleId}/permissions`,
    method: 'put',
    data: { permissionIds }
  })
}

export function getUserRoles(userId) {
  return request({
    url: `/api/rbac/users/${userId}/roles`,
    method: 'get'
  })
}

export function updateUserRoles(userId, roleIds) {
  return request({
    url: `/api/rbac/users/${userId}/roles`,
    method: 'put',
    data: { roleIds }
  })
}
