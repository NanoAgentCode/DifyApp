<template>
  <div class="text2sql-container">
    <el-tabs v-model="activeTab" class="sql-tabs">
      <el-tab-pane label="智能框图" name="aiDrawio">
        <AIDrawIO />
      </el-tab-pane>
      <el-tab-pane label="SQL 生成" name="sql">
        <el-row :gutter="20" class="text2sql-row">
      <!-- 左侧：查询表单 -->
      <el-col :span="10" class="left-panel">
        <el-card class="query-card">
          <template #header>
            <span>SQL 生成</span>
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
            <el-form-item label="选择表" required>
              <div style="display: flex; gap: 8px; width: 100%;">
                <el-select 
                  v-model="queryForm.tableNames" 
                  multiple 
                  filterable
                  placeholder="请至少选择一个表（必选，支持多选）" 
                  style="flex: 1; min-width: 0;"
                  collapse-tags
                  collapse-tags-tooltip
                  :max-collapse-tags="3"
                >
                  <template #header>
                    <div style="padding: 8px 12px; border-bottom: 1px solid #e4e7ed;">
                      <el-button 
                        text 
                        type="primary" 
                        size="small" 
                        @click="selectAllTables"
                        style="padding: 0;"
                      >
                        全选
                      </el-button>
                      <el-button 
                        text 
                        type="primary" 
                        size="small" 
                        @click="clearAllTables"
                        style="padding: 0; margin-left: 12px;"
                      >
                        清空
                      </el-button>
                    </div>
                  </template>
                  <el-option
                    v-for="table in tables"
                    :key="table"
                    :label="table"
                    :value="table"
                  />
                </el-select>
                <el-button 
                  v-if="queryForm.tableNames && queryForm.tableNames.length > 0"
                  @click="showTableSchema"
                  type="info"
                  size="default"
                  title="查看表结构"
                  style="flex-shrink: 0;"
                >
                  查看结构
                </el-button>
              </div>
              <div v-if="queryForm.tableNames && queryForm.tableNames.length > 0" style="margin-top: 8px; font-size: 12px; color: #909399;">
                已选择 {{ queryForm.tableNames.length }} 个表
              </div>
            </el-form-item>
            <el-form-item label="选择模型">
              <el-select v-model="queryForm.modelId" placeholder="使用默认模型（可选）" clearable style="width: 100%">
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
                :placeholder="'例如：查询所有用户的信息\n多表关联示例：查询订单及其用户信息\n统计查询示例：统计每个用户的订单数量'"
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
        <el-card class="result-card">
          <template #header>
            <div class="result-header">
              <span>查询结果</span>
              <el-tag v-if="result" type="success" size="small">共 {{ result.rowCount || 0 }} 条记录</el-tag>
            </div>
          </template>
          <div v-if="result">
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
                >
                  <template #default="scope">
                    <span>{{ formatCellValue(scope.row[column]) }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
          <div v-else class="empty-state">
            <el-empty description="请在左侧输入查询问题并执行查询" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 表结构查看对话框 -->
    <el-dialog v-model="schemaDialogVisible" title="表结构信息" width="70%" :close-on-click-modal="false">
      <div v-loading="schemaLoading">
        <div v-for="(schema, index) in tableSchemas" :key="index" style="margin-bottom: 20px;">
          <el-card>
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <strong>{{ schema.tableName || schema.collectionName }}</strong>
                <el-tag v-if="schema.primaryKeys && schema.primaryKeys.length > 0" type="success" size="small">
                  主键: {{ schema.primaryKeys.map(pk => pk.columnName).join(', ') }}
                </el-tag>
              </div>
            </template>
            <div>
              <div v-if="schema.columns || schema.fields">
                <h4 style="margin-top: 0;">列信息：</h4>
                <el-table :data="schema.columns || schema.fields" border size="small" max-height="300">
                  <el-table-column prop="name" label="列名" width="200" />
                  <el-table-column prop="type" label="数据类型" width="150" />
                  <el-table-column prop="size" label="长度" width="100" v-if="schema.columns" />
                  <el-table-column prop="nullable" label="可空" width="80" v-if="schema.columns">
                    <template #default="{ row }">
                      <el-tag :type="row.nullable ? 'info' : 'warning'" size="small">
                        {{ row.nullable ? '是' : '否' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div v-if="schema.foreignKeys && schema.foreignKeys.length > 0" style="margin-top: 15px;">
                <h4>外键关联：</h4>
                <el-table :data="schema.foreignKeys" border size="small">
                  <el-table-column prop="columnName" label="外键列" width="150" />
                  <el-table-column label="关联关系" width="300">
                    <template #default="{ row }">
                      <span>{{ schema.tableName }}.{{ row.columnName }} → {{ row.pkTableName }}.{{ row.pkColumnName }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-card>
        </div>
      </div>
      <template #footer>
        <el-button @click="schemaDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
      </el-tab-pane>
      <el-tab-pane label="数据源管理" name="dataSource">
        <DataSourceManagement />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getDataSourceList } from '@/api/dataSource'
import { getTableList, executeText2SqlQuery, getTableSchema } from '@/api/text2sql'
import { getModelConfig } from '@/api/model'
import DataSourceManagement from './DataSourceManagement.vue'
import AIDrawIO from './AIDrawIO.vue'

const activeTab = ref('aiDrawio')

// 监听选项卡切换，当切换到SQL生成tab时刷新数据源列表
watch(activeTab, (newTab) => {
  if (newTab === 'sql') {
    loadDataSources()
  }
})

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
const schemaDialogVisible = ref(false)
const schemaLoading = ref(false)
const tableSchemas = ref([])

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
  if (!queryForm.value.tableNames || queryForm.value.tableNames.length === 0) {
    ElMessage.warning('请至少选择一个表')
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
      tableNames: queryForm.value.tableNames,
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

// 格式化单元格值，处理对象类型
const formatCellValue = (value) => {
  if (value === null || value === undefined) {
    return ''
  }
  
  // 如果是对象或数组，转换为JSON字符串
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value, null, 2)
    } catch (e) {
      return String(value)
    }
  }
  
  // 其他类型直接返回
  return String(value)
}

const showTableSchema = async () => {
  if (!queryForm.value.dataSourceId || !queryForm.value.tableNames || queryForm.value.tableNames.length === 0) {
    ElMessage.warning('请先选择数据源和表')
    return
  }
  
  schemaDialogVisible.value = true
  schemaLoading.value = true
  tableSchemas.value = []
  
  try {
    const schemas = []
    for (const tableName of queryForm.value.tableNames) {
      try {
        const schemaJson = await getTableSchema(queryForm.value.dataSourceId, tableName, false)
        const schema = typeof schemaJson === 'string' ? JSON.parse(schemaJson) : schemaJson
        schemas.push(schema)
      } catch (error) {
        console.error(`获取表 ${tableName} 的结构失败:`, error)
        ElMessage.error(`获取表 ${tableName} 的结构失败`)
      }
    }
    tableSchemas.value = schemas
  } catch (error) {
    ElMessage.error('获取表结构失败')
    console.error('获取表结构失败:', error)
  } finally {
    schemaLoading.value = false
  }
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

.sql-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sql-tabs :deep(.el-tabs__header) {
  margin: 0 0 20px 0;
  flex-shrink: 0;
}

.sql-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sql-tabs :deep(.el-tab-pane) {
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

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}
</style>

