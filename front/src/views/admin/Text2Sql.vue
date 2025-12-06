<template>
  <div class="text2sql-container">
    <el-row :gutter="20" class="text2sql-row">
      <!-- 左侧：查询表单 -->
      <el-col :span="10" class="left-panel">
        <el-card class="query-card">
          <template #header>
            <span>Text2SQL 查询</span>
          </template>

          <el-form :model="queryForm" label-width="100px">
            <el-form-item label="选择数据源" required>
              <el-select v-model="queryForm.dataSourceId" placeholder="请选择数据源" style="width: 100%" @change="handleDataSourceChange">
                <el-option
                  v-for="ds in dataSources"
                  :key="ds.id"
                  :label="ds.name"
                  :value="ds.id"
                >
                  <span>{{ ds.name }} ({{ getTypeName(ds.type) }})</span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="选择表（可选）">
              <el-select v-model="queryForm.tableNames" multiple placeholder="不选择则使用所有表" style="width: 100%">
                <el-option
                  v-for="table in tables"
                  :key="table"
                  :label="table"
                  :value="table"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="选择模型（可选）">
              <el-select v-model="queryForm.modelId" placeholder="使用默认模型" clearable style="width: 100%">
                <el-option
                  v-for="model in models"
                  :key="model.id"
                  :label="model.name"
                  :value="model.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="输入问题" required>
              <el-input
                v-model="queryForm.question"
                type="textarea"
                :rows="8"
                placeholder="例如：查询所有用户的信息"
              />
            </el-form-item>
            <el-form-item>
              <div class="button-group">
                <el-button type="primary" @click="handleQuery" :loading="querying">执行查询</el-button>
                <el-button @click="handleClear">清空</el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：查询结果 -->
      <el-col :span="14" class="right-panel">
        <el-card class="result-card" v-if="result">
          <template #header>
            <div class="result-header">
              <span>查询结果</span>
              <el-tag type="success" size="small">共 {{ result.rowCount || 0 }} 条记录</el-tag>
            </div>
          </template>
          <div class="sql-display">
            <div class="sql-label">
              <strong>生成的SQL：</strong>
              <el-button
                text
                type="primary"
                size="small"
                @click="copySql"
                style="margin-left: 10px"
              >
                复制
              </el-button>
            </div>
            <el-input
              v-model="result.sql"
              type="textarea"
              :rows="4"
              readonly
              class="sql-textarea"
            />
          </div>
          <div class="table-display">
            <el-table :data="result.rows" border stripe max-height="500" v-loading="querying">
              <el-table-column
                v-for="column in result.columns"
                :key="column"
                :prop="column"
                :label="column"
                show-overflow-tooltip
              />
            </el-table>
          </div>
        </el-card>
        <el-empty v-else description="请在左侧输入查询问题并执行查询" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDataSourceList } from '@/api/dataSource'
import { getTableList, executeText2SqlQuery } from '@/api/text2sql'
import { getModelConfig } from '@/api/model'

const dataSources = ref([])
const tables = ref([])
const models = ref([])
const queryForm = ref({
  dataSourceId: null,
  question: '',
  tableNames: [],
  modelId: null
})
const querying = ref(false)
const result = ref(null)

onMounted(() => {
  loadDataSources()
  loadModels()
})

const loadDataSources = async () => {
  try {
    const response = await getDataSourceList({ status: 1 })
    dataSources.value = Array.isArray(response) ? response : []
  } catch (error) {
    ElMessage.error('加载数据源列表失败')
  }
}

const handleDataSourceChange = async () => {
  if (queryForm.value.dataSourceId) {
    try {
      const response = await getTableList(queryForm.value.dataSourceId)
      tables.value = Array.isArray(response) ? response : []
    } catch (error) {
      ElMessage.error('加载表列表失败')
      tables.value = []
    }
  } else {
    tables.value = []
  }
}

const loadModels = async () => {
  try {
    const response = await getModelConfig()
    if (response && response.qaModels) {
      models.value = response.qaModels.filter(m => m.enabled)
    }
  } catch (error) {
    console.error('加载模型列表失败', error)
  }
}

const handleQuery = async () => {
  if (!queryForm.value.dataSourceId) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!queryForm.value.question.trim()) {
    ElMessage.warning('请输入问题')
    return
  }

  querying.value = true
  try {
    const data = {
      dataSourceId: queryForm.value.dataSourceId,
      question: queryForm.value.question,
      tableNames: queryForm.value.tableNames.length > 0 ? queryForm.value.tableNames : null,
      modelId: queryForm.value.modelId || null
    }
    const response = await executeText2SqlQuery(data)
    result.value = response
    ElMessage.success('查询成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.error || error.message || '查询失败')
  } finally {
    querying.value = false
  }
}

const copySql = () => {
  if (result.value && result.value.sql) {
    navigator.clipboard.writeText(result.value.sql).then(() => {
      ElMessage.success('SQL已复制到剪贴板')
    }).catch(() => {
      ElMessage.error('复制失败')
    })
  }
}

const handleClear = () => {
  queryForm.value = {
    dataSourceId: null,
    question: '',
    tableNames: [],
    modelId: null
  }
  result.value = null
  tables.value = []
}

const getTypeName = (type) => {
  const typeMap = {
    postgresql: 'PostgreSQL',
    mysql: 'MySQL',
    oracle: 'Oracle',
    mongodb: 'MongoDB'
  }
  return typeMap[type] || type
}
</script>

<style scoped>
.text2sql-container {
  padding: 0;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.text2sql-row {
  height: 100%;
  margin: 0;
}

.left-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.right-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding-right: 40px;
}

.query-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.query-card :deep(.el-card__body) {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.result-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.result-card :deep(.el-card__body) {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sql-display {
  margin-bottom: 20px;
}

.sql-label {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.sql-textarea {
  font-family: 'Courier New', monospace;
}

.table-display {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-display :deep(.el-table) {
  flex: 1;
}

.button-group {
  display: flex;
  gap: 12px;
  width: 100%;
}

.button-group .el-button {
  flex: 1;
}
</style>

