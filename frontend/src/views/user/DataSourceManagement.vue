<template>
  <div class="data-source-management">
    <el-card>
      <template #header>
        <span>数据源管理</span>
      </template>

      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索数据源名称或描述"
          clearable
          style="width: 300px"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <div class="table-container">
        <el-table
          :data="dataSources"
          v-loading="loading"
          stripe
        >
          <el-table-column prop="id" label="ID" width="80" align="center" />
          <el-table-column prop="name" label="数据源名称" min-width="200" />
          <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
          <el-table-column prop="type" label="数据库类型" width="150" align="center">
            <template #default="{ row }">
              <el-tag :type="getTypeTag(row.type)">{{ getTypeName(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="host" label="主机" width="150" />
          <el-table-column prop="port" label="端口" width="80" align="center" />
          <el-table-column prop="database" label="数据库" width="120" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getDataSourceList } from '@/api/dataSource'

const dataSources = ref([])
const loading = ref(false)
const searchKeyword = ref('')

let searchTimer = null

onMounted(() => {
  loadDataSources()
})

const loadDataSources = async () => {
  loading.value = true
  try {
    const params = {}
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }
    const response = await getDataSourceList(params)
    dataSources.value = Array.isArray(response) ? response : []
  } catch (error) {
    ElMessage.error('加载数据源列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    loadDataSources()
  }, 500)
}

const getTypeName = (type) => {
  const typeMap = {
    postgresql: 'PostgreSQL',
    mysql: 'MySQL',
    oracle: 'Oracle',
    mongodb: 'MongoDB',
    neo4j: 'Neo4j',
    elasticsearch: 'Elasticsearch'
  }
  return typeMap[type] || type
}

const getTypeTag = (type) => {
  const tagMap = {
    postgresql: 'primary',
    mysql: 'success',
    oracle: 'warning',
    mongodb: 'info',
    neo4j: 'danger',
    elasticsearch: ''
  }
  return tagMap[type] || ''
}
</script>

<style scoped>
.data-source-management {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
}

:deep(.el-card__header) {
  flex-shrink: 0;
  padding: 18px 20px;
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 20px;
  min-height: 0;
}

.search-bar {
  margin-bottom: 20px;
  flex-shrink: 0;
}

.table-container {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>

