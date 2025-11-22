import {createRouter, createWebHistory} from 'vue-router'
import {ElMessage} from 'element-plus'

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
                    return userInfo.role === 1 ? '/admin/apps' : '/user/apps'
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
        children: [
            {
                path: 'chat',
                name: 'Chat',
                component: () => import('@/views/admin/Chat.vue'),
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
                meta: {title: '知识库问答', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'knowledge-base',
                name: 'KnowledgeBaseManagement',
                component: () => import('@/views/admin/KnowledgeBaseManagement.vue'),
                meta: {title: '知识库管理', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'knowledge-base/:kbId/documents/upload',
                name: 'DocumentUpload',
                component: () => import('@/views/admin/DocumentUpload.vue'),
                meta: {title: '文档上传', requiresAuth: true, requiresAdmin: true}
            },
            {
                path: 'knowledge-base/:kbId/documents/list',
                name: 'DocumentList',
                component: () => import('@/views/admin/DocumentList.vue'),
                meta: {title: '文件列表', requiresAuth: true, requiresAdmin: true}
            }
        ]
    },
    {
        path: '/user',
        component: () => import('@/layouts/UserLayout.vue'),
        meta: {requiresAuth: true},
        children: [
            {
                path: 'chat',
                name: 'UserChat',
                component: () => import('@/views/user/Chat.vue'),
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
                meta: {title: '知识库问答', requiresAuth: true}
            },
            {
                path: 'knowledge-base',
                name: 'UserKnowledgeBaseManagement',
                component: () => import('@/views/user/KnowledgeBaseManagement.vue'),
                meta: {title: '知识库管理', requiresAuth: true}
            },
            {
                path: 'knowledge-base/:kbId/documents/upload',
                name: 'UserDocumentUpload',
                component: () => import('@/views/user/DocumentUpload.vue'),
                meta: {title: '文档上传', requiresAuth: true}
            },
            {
                path: 'knowledge-base/:kbId/documents/list',
                name: 'UserDocumentList',
                component: () => import('@/views/user/DocumentList.vue'),
                meta: {title: '文件列表', requiresAuth: true}
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

// 路由守卫
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    const userInfoStr = localStorage.getItem('userInfo')
    const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
    const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin)

    if (requiresAuth && !token) {
        // 需要登录但没有token，跳转到登录页
        ElMessage.warning('请先登录')
        next('/login')
    } else if (requiresAdmin) {
        // 需要管理员权限
        if (!userInfoStr) {
            ElMessage.warning('请先登录')
            next('/login')
            return
        }
        try {
            const userInfo = JSON.parse(userInfoStr)
            if (userInfo.role !== 1) {
                ElMessage.error('需要管理员权限')
                next('/user/apps')
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
                next(userInfo.role === 1 ? '/admin/apps' : '/user/apps')
            } catch (e) {
                next('/admin/apps')
            }
        } else {
            next('/admin/apps')
        }
    } else {
        next()
    }
})

export default router

