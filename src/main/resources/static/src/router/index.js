import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/admin/apps'
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      {
        path: 'apps',
        name: 'AppList',
        component: () => import('@/views/admin/AppList.vue'),
        meta: { title: '应用列表' }
      },
      {
        path: 'apps/create',
        name: 'AppCreate',
        component: () => import('@/views/admin/AppForm.vue'),
        meta: { title: '创建应用' }
      },
      {
        path: 'apps/edit/:id',
        name: 'AppEdit',
        component: () => import('@/views/admin/AppForm.vue'),
        meta: { title: '编辑应用' }
      },
      {
        path: 'apps/detail/:id',
        name: 'AppDetail',
        component: () => import('@/views/admin/AppDetail.vue'),
        meta: { title: '应用详情' }
      }
    ]
  },
  {
    path: '/app',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      {
        path: 'chat/:id',
        name: 'ChatApp',
        component: () => import('@/views/app/ChatApp.vue'),
        meta: { title: '聊天应用' }
      },
      {
        path: 'workflow/:id',
        name: 'WorkflowApp',
        component: () => import('@/views/app/WorkflowApp.vue'),
        meta: { title: '工作流应用' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

