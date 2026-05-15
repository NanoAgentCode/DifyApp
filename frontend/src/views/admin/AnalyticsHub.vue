<template>
  <div class="analytics-hub">
    <div class="hub-tabs">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane
          v-for="tab in visibleTabs"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        />
      </el-tabs>
    </div>

    <div class="hub-content">
      <component v-if="activeComponent" :is="activeComponent" />
      <el-empty v-else description="暂无可访问的数据与日志功能" />
    </div>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, markRaw, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { hasPermission } from '@/utils/permission'

const route = useRoute()
const router = useRouter()

const tabs = [
  {
    name: 'statistics',
    label: '数据统计',
    permission: 'admin.statistics',
    component: markRaw(defineAsyncComponent(() => import('@/views/admin/Statistics.vue')))
  },
  {
    name: 'data-analysis',
    label: '数据分析',
    permission: 'admin.data_analysis',
    component: markRaw(defineAsyncComponent(() => import('@/views/admin/DataAnalysis.vue')))
  },
  {
    name: 'user-action-logs',
    label: '行为日志',
    permission: 'admin.user_logs',
    component: markRaw(defineAsyncComponent(() => import('@/views/admin/UserActionLog.vue')))
  },
  {
    name: 'observability',
    label: '日志监控',
    permission: 'admin.observability',
    component: markRaw(defineAsyncComponent(() => import('@/views/observability/LogList.vue')))
  }
]

const visibleTabs = computed(() => tabs.filter(tab => hasPermission(tab.permission)))
const activeTab = ref('')

const activeComponent = computed(() => {
  return visibleTabs.value.find(tab => tab.name === activeTab.value)?.component || null
})

const normalizeTab = () => {
  if (!visibleTabs.value.length) {
    activeTab.value = ''
    return
  }

  const queryTab = String(route.query.tab || '')
  const target = visibleTabs.value.find(tab => tab.name === queryTab) || visibleTabs.value[0]
  activeTab.value = target.name

  if (queryTab !== target.name) {
    router.replace({ path: '/admin/analytics', query: { ...route.query, tab: target.name } })
  }
}

const handleTabChange = tabName => {
  router.replace({ path: '/admin/analytics', query: { ...route.query, tab: tabName } })
}

watch(
  () => route.query.tab,
  () => normalizeTab(),
  { immediate: true }
)
</script>

<style scoped>
.analytics-hub {
  height: 100%;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--color-bg-secondary);
  overflow: hidden;
}

.hub-tabs {
  flex-shrink: 0;
  padding: 0 14px;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

.hub-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.hub-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 0;
}

.hub-tabs :deep(.el-tabs__item) {
  height: 44px;
  line-height: 44px;
  font-weight: var(--font-weight-medium);
}

.hub-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.hub-content :deep(> *) {
  min-height: 100%;
}

@media (max-width: 900px) {
  .analytics-hub {
    padding: 12px;
    gap: 10px;
  }

  .hub-tabs {
    padding: 0 10px;
    overflow-x: auto;
  }
}
</style>
