import {createRouter, createWebHistory} from 'vue-router'
import {ElMessage} from 'element-plus'
import {validateToken} from '@/api/auth'

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/auth/Login.vue'),
        meta: {title: '登录', requiresAuth: false}
    },
    {
        path: '/register',
        name: 'Register',
        component: () => import('@/views/auth/Register.vue'),
        meta: {title: '注册', requiresAuth: false}
    },
    {
        path: '/',
        redirect: (to) => {
            const userInfoStr = localStorage.getItem('userInfo')
            if (userInfoStr) {
                try {
                    const userInfo = JSON.parse(userInfoStr)
                    return userInfo.role === 1 ? '/admin/chat' : '/user/chat'
                } catch (e) {
                    return '/login'
                }
            }
            return '/login'
        }
    },
    {
        path: '/admin',
        component: () => import('@/layouts/AdminLayout.vue'),
        meta: {requiresAuth: true},
        redirect: '/admin/chat',
        children: [
            {
                path: 'chat',
                name: 'Chat',
                component: () => import('@/components/chat/Chat.vue'),
                props: {
                    title: '智能问答',
                    showNewConversationButton: false,
                    showBrowserSearch: true
                },
                meta: {title: '智能问答', requiresAuth: true}
            },
            {
                path: 'apps',
                name: 'AppList',
                component: () => import('@/views/admin/AppList.vue'),
                meta: {title: '应用列表', requiresAuth: true}
            },
            {
                path: 'apps/create',
                name: 'AppCreate',
                component: () => import('@/views/admin/AppForm.vue'),
                meta: {title: '创建应用', requiresAuth: true}
            },
            {
                path: 'apps/edit/:id',
                name: 'AppEdit',
                component: () => import('@/views/admin/AppForm.vue'),
                meta: {title: '编辑应用', requiresAuth: true}
            },
            {
                path: 'apps/detail/:id',
                name: 'AppDetail',
                component: () => import('@/views/admin/AppDetail.vue'),
                meta: {title: '应用详情', requiresAuth: true}
            },
            {
                path: 'users',
                name: 'UserList',
                component: () => import('@/views/admin/UserList.vue'),
                meta: {title: '用户管理', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'kb-qa',
                name: 'KnowledgeBaseQA',
                component: () => import('@/views/admin/KnowledgeBaseQA.vue'),
                meta: {title: '知识问答', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'knowledge-base',
                name: 'KnowledgeBaseManagement',
                component: () => import('@/views/admin/KnowledgeBaseManagement.vue'),
                meta: {title: '知识管理', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'knowledge-base/:kbId/documents',
                name: 'DocumentManagement',
                component: () => import('@/views/admin/DocumentManagement.vue'),
                meta: {title: '文档管理', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'knowledge-base/:kbId/documents/upload',
                redirect: (to) => `/admin/knowledge-base/${to.params.kbId}/documents`
            },
            {
                path: 'knowledge-base/:kbId/documents/list',
                redirect: (to) => `/admin/knowledge-base/${to.params.kbId}/documents`
            },
            {
                path: 'chat-history',
                name: 'AdminChatHistory',
                component: () => import('@/views/admin/ChatHistory.vue'),
                meta: {title: '会话历史', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'models',
                name: 'ModelManagement',
                component: () => import('@/views/admin/ModelManagement.vue'),
                meta: {title: '大模型管理', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'vector-databases',
                name: 'VectorDatabaseManagement',
                component: () => import('@/views/admin/VectorDatabaseManagement.vue'),
                meta: {title: '向量数据库', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'data-sources',
                name: 'DataSourceManagement',
                component: () => import('@/views/admin/DataSourceManagement.vue'),
                meta: {title: '数据源管理', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'text2sql',
                name: 'Text2Sql',
                component: () => import('@/views/admin/Text2Sql.vue'),
                meta: {title: 'SQL 生成', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'system-config',
                name: 'SystemConfig',
                component: () => import('@/views/admin/SystemConfig.vue'),
                meta: {title: '系统配置', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'ai-drawio',
                name: 'AIDrawIO',
                component: () => import('@/views/admin/AIDrawIO.vue'),
                meta: {title: '智能框图', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'statistics',
                name: 'Statistics',
                component: () => import('@/views/admin/Statistics.vue'),
                meta: {title: '数据统计', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'document-reader',
                name: 'DocumentReaderManagement',
                component: () => import('@/views/admin/DocumentReaderManagement.vue'),
                meta: {title: '文档解读', requiresAuth: true}
            },
            {
                path: 'document-reader/:docId',
                name: 'DocumentReader',
                component: () => import('@/views/admin/DocumentReader.vue'),
                meta: {title: '文档解读', requiresAuth: true}
            },
        ]
    },
    {
        path: '/user',
        component: () => import('@/layouts/UserLayout.vue'),
        meta: {requiresAuth: true},
        redirect: '/user/chat',
        children: [
            {
                path: 'chat',
                name: 'UserChat',
                component: () => import('@/components/chat/Chat.vue'),
                props: {
                    title: '智能问答',
                    showNewConversationButton: true,
                    showBrowserSearch: true
                },
                meta: {title: '智能问答', requiresAuth: true}
            },
            {
                path: 'apps',
                name: 'UserAppList',
                component: () => import('@/views/user/AppList.vue'),
                meta: {title: '智能应用', requiresAuth: true}
            },
            {
                path: 'kb-qa',
                name: 'UserKnowledgeBaseQA',
                component: () => import('@/views/user/KnowledgeBaseQA.vue'),
                meta: {title: '知识检索', requiresAuth: true}
            },
            {
                path: 'knowledge-base',
                name: 'UserKnowledgeBaseManagement',
                component: () => import('@/views/user/KnowledgeBaseManagement.vue'),
                meta: {title: '知识管理', requiresAuth: true}
            },
            {
                path: 'knowledge-base/:kbId/documents',
                name: 'UserDocumentManagement',
                component: () => import('@/views/user/DocumentManagement.vue'),
                meta: {title: '文档管理', requiresAuth: true}
            },
            {
                path: 'knowledge-base/:kbId/documents/upload',
                redirect: (to) => `/user/knowledge-base/${to.params.kbId}/documents`
            },
            {
                path: 'knowledge-base/:kbId/documents/list',
                redirect: (to) => `/user/knowledge-base/${to.params.kbId}/documents`
            },
            {
                path: 'chat-history',
                name: 'UserChatHistory',
                component: () => import('@/views/user/ChatHistory.vue'),
                meta: {title: '会话历史', requiresAuth: true}
            },
            {
                path: 'ai-drawio',
                name: 'UserAIDrawIO',
                component: () => import('@/views/user/AIDrawIO.vue'),
                meta: {title: '智能框图', requiresAuth: true}
            },
            {
                path: 'document-reader',
                name: 'UserDocumentReaderManagement',
                component: () => import('@/views/user/DocumentReaderManagement.vue'),
                meta: {title: '文档解读', requiresAuth: true}
            },
            {
                path: 'document-reader/:docId',
                name: 'UserDocumentReader',
                component: () => import('@/views/user/DocumentReader.vue'),
                meta: {title: '文档解读', requiresAuth: true}
            }
        ]
    },
    {
        path: '/app',
        component: () => import('@/layouts/AppLayout.vue'),
        meta: {requiresAuth: true},
        children: [
            {
                path: 'chat/:id',
                name: 'ChatApp',
                component: () => import('@/views/app/ChatApp.vue'),
                meta: {title: '聊天应用', requiresAuth: true}
            },
            {
                path: 'workflow/:id',
                name: 'WorkflowApp',
                component: () => import('@/views/app/WorkflowApp.vue'),
                meta: {title: '工作流应用', requiresAuth: true}
            }
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// Token 验证缓存，避免重复验证
let tokenValidationCache = {
    isValid: null,
    timestamp: null,
    cacheDuration: 5 * 60 * 1000 // 缓存5分钟
}

// 清除 token 验证缓存
function clearTokenCache() {
    tokenValidationCache.isValid = null
    tokenValidationCache.timestamp = null
}

// 将清除缓存的函数暴露到全局，供其他模块使用
if (typeof window !== 'undefined') {
    window.clearTokenCache = clearTokenCache
}

// 路由守卫
router.beforeEach(async (to, from, next) => {
    const token = localStorage.getItem('token')
    const userInfoStr = localStorage.getItem('userInfo')
    const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
    const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin)

    // 如果访问不需要认证的页面，直接通过
    if (!requiresAuth && to.path !== '/login' && to.path !== '/register') {
        next()
        return
    }

    // 如果需要认证但没有 token，直接跳转到登录页
    if (requiresAuth && !token) {
        ElMessage.warning('请先登录')
        next('/login')
        return
    }

    // 如果有 token 且需要认证，验证 token 是否有效
    if (token && requiresAuth) {
        // 检查缓存
        const now = Date.now()
        const cacheValid = tokenValidationCache.isValid !== null && 
                          tokenValidationCache.timestamp !== null &&
                          (now - tokenValidationCache.timestamp) < tokenValidationCache.cacheDuration

        if (!cacheValid) {
            // 缓存无效，进行验证
            try {
                const isValid = await validateToken()
                tokenValidationCache.isValid = isValid
                tokenValidationCache.timestamp = now

                if (!isValid) {
                    // Token 无效，清除登录信息并跳转到登录页
                    localStorage.removeItem('token')
                    localStorage.removeItem('userInfo')
                    clearTokenCache()
                    ElMessage.error('登录已过期，请重新登录')
                    if (to.path !== '/login' && to.path !== '/register') {
                        next('/login')
                        return
                    }
                }
            } catch (error) {
                // 验证过程中出错，清除 token 并跳转
                console.error('Token 验证失败', error)
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                clearTokenCache()
                ElMessage.error('登录验证失败，请重新登录')
                if (to.path !== '/login' && to.path !== '/register') {
                    next('/login')
                    return
                }
            }
        } else if (tokenValidationCache.isValid === false) {
            // 缓存显示 token 无效
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            clearTokenCache()
            ElMessage.error('登录已过期，请重新登录')
            if (to.path !== '/login' && to.path !== '/register') {
                next('/login')
                return
            }
        }
    }

    // 检查用户状态（如果已登录）
    if (token && userInfoStr) {
        try {
            const userInfo = JSON.parse(userInfoStr)
            // 检查用户状态：0-待审核，1-已激活，2-已禁用
            if (userInfo.status === 0) {
                // 待审核状态，清除登录信息并跳转到登录页
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                clearTokenCache()
                ElMessage.warning('账号待审核，请联系管理员')
                if (to.path !== '/login') {
                    next('/login')
                    return
                }
            } else if (userInfo.status === 2) {
                // 已禁用状态，清除登录信息并跳转到登录页
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                clearTokenCache()
                ElMessage.error('账号已被禁用，请联系管理员')
                if (to.path !== '/login') {
                    next('/login')
                    return
                }
            }
        } catch (e) {
            console.error('解析用户信息失败', e)
            // 解析失败，清除无效数据
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            clearTokenCache()
            if (requiresAuth && to.path !== '/login') {
                next('/login')
                return
            }
        }
    }

    if (requiresAdmin) {
        // 需要管理员权限
        if (!userInfoStr) {
            ElMessage.warning('请先登录')
            next('/login')
            return
        }
        try {
            const userInfo = JSON.parse(userInfoStr)
            // 检查用户状态
            if (userInfo.status === 0) {
                ElMessage.warning('账号待审核，请联系管理员')
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                clearTokenCache()
                next('/login')
                return
            } else if (userInfo.status === 2) {
                ElMessage.error('账号已被禁用，请联系管理员')
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                clearTokenCache()
                next('/login')
                return
            }
            if (userInfo.role !== 1) {
                ElMessage.error('需要管理员权限')
                next('/user/chat')
                return
            }
        } catch (e) {
            ElMessage.error('用户信息解析失败')
            next('/login')
            return
        }
        next()
    } else if (to.path === '/login' && token) {
        // 已登录用户访问登录页，根据角色跳转
        if (userInfoStr) {
            try {
                const userInfo = JSON.parse(userInfoStr)
                // 检查用户状态
                if (userInfo.status === 0 || userInfo.status === 2) {
                    localStorage.removeItem('token')
                    localStorage.removeItem('userInfo')
                    clearTokenCache()
                    next()
                    return
                }
                next(userInfo.role === 1 ? '/admin/chat' : '/user/chat')
            } catch (e) {
                next('/admin/chat')
            }
        } else {
            next('/admin/chat')
        }
    } else {
        next()
    }
})

export default router

