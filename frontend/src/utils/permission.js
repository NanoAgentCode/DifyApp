export function getStoredPermissions() {
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    return Array.isArray(userInfo.permissions) ? userInfo.permissions : []
  } catch (e) {
    return []
  }
}

export function hasPermission(permissionCode) {
  if (!permissionCode) return true
  return getStoredPermissions().includes(permissionCode)
}

export function getFirstPermittedPath(preferredPath, fallbackPrefix = '') {
  const preferredPermission = routePermissionMap[preferredPath]
  if (preferredPath && (
    Array.isArray(preferredPermission)
      ? preferredPermission.some(permission => hasPermission(permission))
      : hasPermission(preferredPermission)
  )) {
    return preferredPath
  }
  const permissions = getStoredPermissions()
  const first = menuPermissionOrder.find(item => {
    const allowed = item.permissions
      ? item.permissions.some(permission => permissions.includes(permission))
      : permissions.includes(item.permission)
    return (!fallbackPrefix || item.path.startsWith(fallbackPrefix)) && allowed
  })
  return first?.path || '/login'
}

export const routePermissionMap = {
  '/admin/chat': 'admin.chat',
  '/admin/apps': 'admin.apps',
  '/admin/apps/create': 'admin.apps',
  '/admin/apps/edit': 'admin.apps',
  '/admin/apps/detail': 'admin.apps',
  '/admin/users': 'admin.users',
  '/admin/roles': 'admin.roles',
  '/admin/kb-qa': 'admin.knowledge_base',
  '/admin/knowledge-base': 'admin.knowledge_base',
  '/admin/chat-history': 'admin.chat_history',
  '/admin/models': 'admin.models',
  '/admin/system-config': 'admin.system_config',
  '/admin/ai-drawio': 'admin.ai_drawio',
  '/admin/analytics': ['admin.statistics', 'admin.data_analysis', 'admin.user_logs', 'admin.observability'],
  '/admin/statistics': 'admin.statistics',
  '/admin/data-analysis': 'admin.data_analysis',
  '/admin/user-action-logs': 'admin.user_logs',
  '/admin/observability': 'admin.observability',
  '/admin/memos': 'admin.memos',
  '/admin/document-reader': 'admin.document_reader',
  '/user/chat': 'user.chat',
  '/user/apps': 'user.apps',
  '/user/kb-qa': 'user.kb_qa',
  '/user/knowledge-base': 'user.knowledge_base',
  '/user/chat-history': 'user.chat_history',
  '/user/ai-drawio': 'user.ai_drawio',
  '/user/document-reader': 'user.document_reader',
  '/user/memos': 'user.memos'
}

export const menuPermissionOrder = [
  { path: '/admin/chat', permission: 'admin.chat' },
  { path: '/admin/apps', permission: 'admin.apps' },
  { path: '/admin/models', permissions: ['admin.models', 'admin.skills'] },
  { path: '/admin/users', permission: 'admin.users' },
  { path: '/admin/system-config', permission: 'admin.system_config' },
  { path: '/admin/analytics', permissions: ['admin.statistics', 'admin.data_analysis', 'admin.user_logs', 'admin.observability'] },
  { path: '/admin/memos', permission: 'admin.memos' },
  { path: '/admin/knowledge-base', permission: 'admin.knowledge_base' },
  { path: '/admin/chat-history', permission: 'admin.chat_history' },
  { path: '/admin/document-reader', permission: 'admin.document_reader' },
  { path: '/admin/ai-drawio', permission: 'admin.ai_drawio' },
  { path: '/user/chat', permission: 'user.chat' },
  { path: '/user/apps', permission: 'user.apps' },
  { path: '/user/kb-qa', permission: 'user.kb_qa' },
  { path: '/user/knowledge-base', permission: 'user.knowledge_base' },
  { path: '/user/chat-history', permission: 'user.chat_history' },
  { path: '/user/ai-drawio', permission: 'user.ai_drawio' },
  { path: '/user/document-reader', permission: 'user.document_reader' },
  { path: '/user/memos', permission: 'user.memos' }
]
