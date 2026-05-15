<template>
  <div class="app-management-hub">
    <el-tabs v-model="activeTab" class="app-hub-tabs" @tab-change="handleTabChange">
      <el-tab-pane
        v-for="tab in visibleTabs"
        :key="tab.name"
        :label="tab.label"
        :name="tab.name"
        lazy
      >
        <component
          :is="tab.component"
          v-bind="tab.props || {}"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, markRaw, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { hasPermission } from '@/utils/permission'
import AppList from './AppList.vue'

const route = useRoute()
const router = useRouter()
const activeTab = ref('dify')

const tabs = [
  {
    name: 'dify',
    label: 'Dify 应用',
    permission: 'admin.apps',
    component: markRaw(AppList),
    props: { embedded: true }
  },
  {
    name: 'kb-qa',
    label: '知识问答',
    permission: 'admin.knowledge_base',
    component: markRaw(defineAsyncComponent(() => import('./KnowledgeBaseQA.vue')))
  },
  {
    name: 'knowledge-base',
    label: '知识管理',
    permission: 'admin.knowledge_base',
    component: markRaw(defineAsyncComponent(() => import('./KnowledgeBaseManagement.vue')))
  },
  {
    name: 'document-reader',
    label: '文档解读',
    permission: 'admin.document_reader',
    component: markRaw(defineAsyncComponent(() => import('./DocumentReaderManagement.vue')))
  },
  {
    name: 'ai-drawio',
    label: '智能框图',
    permission: 'admin.ai_drawio',
    component: markRaw(defineAsyncComponent(() => import('./AIDrawIO.vue')))
  }
]

const visibleTabs = computed(() => tabs.filter(tab => hasPermission(tab.permission)))

const normalizeTab = () => {
  if (!visibleTabs.value.length) {
    activeTab.value = ''
    return
  }

  const queryTab = String(route.query.tab || '')
  const target = visibleTabs.value.find(tab => tab.name === queryTab) || visibleTabs.value[0]
  activeTab.value = target.name

  if (queryTab !== target.name) {
    router.replace({
      path: '/admin/apps',
      query: target.name === 'dify' ? {} : { tab: target.name }
    })
  }
}

const handleTabChange = tabName => {
  router.replace({
    path: '/admin/apps',
    query: tabName === 'dify' ? {} : { tab: tabName }
  })
}

watch(
  () => [route.query.tab, visibleTabs.value.map(tab => tab.name).join(',')],
  normalizeTab,
  { immediate: true }
)
</script>

<style scoped>
.app-management-hub {
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  background: var(--color-bg-secondary);
  box-sizing: border-box;
  overflow: hidden;
}

.app-hub-tabs {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.app-hub-tabs :deep(.el-tabs__header) {
  flex-shrink: 0;
  margin: 0;
  padding: 0 var(--spacing-lg);
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

.app-hub-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 0;
}

.app-hub-tabs :deep(.el-tabs__item) {
  height: 44px;
  line-height: 44px;
  font-weight: var(--font-weight-medium);
}

.app-hub-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  width: 100%;
}

.app-hub-tabs :deep(.el-tab-pane) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.app-hub-tabs :deep(.app-list),
.app-hub-tabs :deep(.knowledge-base-qa),
.app-hub-tabs :deep(.knowledge-base-management),
.app-hub-tabs :deep(.document-reader-management),
.app-hub-tabs :deep(.ai-drawio-container) {
  width: 100%;
  padding: 0;
  background: transparent;
  box-sizing: border-box;
}

@media (max-width: 900px) {
  .app-management-hub {
    padding: var(--spacing-md);
  }

  .app-hub-tabs {
    gap: var(--spacing-sm);
  }

  .app-hub-tabs :deep(.el-tabs__header) {
    padding: 0 var(--spacing-md);
    overflow-x: auto;
  }
}
</style>
