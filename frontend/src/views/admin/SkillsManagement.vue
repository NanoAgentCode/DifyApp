<template>
  <div class="skills-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <div class="page-title">技能管理</div>
            <div class="page-subtitle">管理技能可用性、用户可见性、配置信息及技能文件预览。</div>
          </div>
          <div class="header-actions">
            <el-button :loading="syncing" @click="handleSync">同步技能</el-button>
            <el-button type="primary" :loading="loading" @click="loadSkills">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="toolbar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索技能键、名称、路径或描述"
          clearable
          class="toolbar-input"
        />
        <el-select v-model="statusFilter" clearable placeholder="配置状态" class="toolbar-select">
          <el-option label="已保存" value="saved" />
          <el-option label="仅已发现" value="discovered" />
          <el-option label="来源缺失" value="missing" />
          <el-option label="可用" value="usable" />
          <el-option label="不可用" value="unusable" />
        </el-select>
        <el-select v-model="enabledFilter" clearable placeholder="启用状态" class="toolbar-select">
          <el-option label="已启用" value="enabled" />
          <el-option label="已禁用" value="disabled" />
        </el-select>
        <el-select v-model="visibilityFilter" clearable placeholder="用户可见性" class="toolbar-select">
          <el-option label="可见" value="visible" />
          <el-option label="隐藏" value="hidden" />
        </el-select>
      </div>

      <div class="summary-row">
        <el-tag type="info">总计 {{ filteredSkillList.length }}</el-tag>
        <el-tag type="success">已启用 {{ enabledCount }}</el-tag>
        <el-tag type="warning">用户可见 {{ visibleCount }}</el-tag>
        <el-tag type="primary">已保存 {{ savedCount }}</el-tag>
        <el-tag type="danger">来源缺失 {{ missingCount }}</el-tag>
        <el-tag :type="unusableCount ? 'danger' : 'success'">不可用 {{ unusableCount }}</el-tag>
      </div>

      <div class="table-wrapper">
        <el-table v-loading="loading" :data="filteredSkillList" border stripe height="100%">
          <el-table-column prop="skillKey" label="技能键" min-width="180" show-overflow-tooltip />
          <el-table-column prop="skillName" label="技能名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="skillPath" label="技能路径" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="130" align="center">
            <template #default="{ row }">
              <el-tag :type="getSourceTagType(row)" size="small">{{ getSourceLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="可用性" width="130" align="center">
            <template #default="{ row }">
              <el-tooltip
                :content="formatAvailabilityIssues(row.availabilityIssues)"
                :disabled="!row.availabilityIssues || row.availabilityIssues.length === 0"
                placement="top"
              >
                <el-tag :type="row.usable ? 'success' : 'danger'" size="small">
                  {{ row.usable ? '可用' : '不可用' }}
                </el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="100" align="center">
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :loading="savingKey === row.skillKey"
                @change="handleQuickToggle(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="可见" width="100" align="center">
            <template #default="{ row }">
              <el-switch
                v-model="row.visibleToUser"
                :loading="savingKey === row.skillKey"
                @change="handleQuickToggle(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="描述" min-width="260">
            <template #default="{ row }">
              <div class="ellipsis-text">{{ row.description || row.fileDescription || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" min-width="180">
            <template #default="{ row }">
              {{ formatDate(row.updateTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right" align="center">
            <template #default="{ row }">
              <div class="action-group">
                <el-button size="small" @click="handlePreview(row)">预览</el-button>
                <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
                <el-button
                  v-if="row._saved"
                  size="small"
                  type="danger"
                  :loading="savingKey === row.skillKey"
                  @click="handleDeleteRow(row)"
                >
                  删除
                </el-button>
                <el-button
                  v-else
                  size="small"
                  type="success"
                  :loading="savingKey === row.skillKey"
                  @click="handleCreateRow(row)"
                >
                  保存
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="editDialogVisible" title="编辑技能" width="760px" destroy-on-close>
      <el-form :model="editForm" label-width="120px">
        <el-form-item label="技能键">
          <el-input v-model="editForm.skillKey" disabled />
        </el-form-item>
        <el-form-item label="技能名称">
          <el-input v-model="editForm.skillName" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
        <el-form-item label="用户可见">
          <el-switch v-model="editForm.visibleToUser" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="Ext JSON">
          <el-input
            v-model="editForm.extJson"
            type="textarea"
            :rows="10"
            placeholder='{"allowedCommands":["npm","mvn"]}'
          />
          <div class="form-tip">仅提供提示词上下文的技能可留空。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDetail" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="previewVisible" title="技能预览" size="55%">
      <div v-loading="previewLoading" class="preview-panel">
        <div v-if="previewDetail" class="preview-meta">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="技能键">{{ previewDetail.skillKey }}</el-descriptions-item>
            <el-descriptions-item label="技能名称">{{ previewDetail.skillName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="路径">{{ previewDetail.skillPath || '-' }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ previewDetail.description || previewDetail.fileDescription || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <div class="preview-section">
          <div class="section-title">可用性</div>
          <el-alert
            :title="previewDetail?.usable ? '技能可用' : '技能不可用'"
            :type="previewDetail?.usable ? 'success' : 'error'"
            :closable="false"
            show-icon
          />
          <div v-if="previewDetail?.availabilityIssues?.length" class="issues-list">
            <el-tag
              v-for="issue in previewDetail.availabilityIssues"
              :key="issue"
              type="danger"
              effect="plain"
            >
              {{ issue }}
            </el-tag>
          </div>
        </div>
        <div class="preview-section">
          <div class="section-title">Ext JSON</div>
          <pre class="code-block">{{ previewDetail?.extJson || '-' }}</pre>
        </div>
        <div class="preview-section">
          <div class="section-title">SKILL.md</div>
          <pre class="code-block">{{ previewDetail?.skillContent || '未找到技能文件内容。' }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteSkillConfig,
  getAdminSkillDetail,
  getAdminSkillList,
  syncSkills,
  updateSkillConfig
} from '@/api/skills'

const loading = ref(false)
const syncing = ref(false)
const savingKey = ref('')
const savingDetail = ref(false)
const previewLoading = ref(false)

const skillList = ref([])
const searchKeyword = ref('')
const statusFilter = ref('')
const enabledFilter = ref('')
const visibilityFilter = ref('')

const editDialogVisible = ref(false)
const previewVisible = ref(false)
const previewDetail = ref(null)

const editForm = ref({
  skillKey: '',
  skillName: '',
  enabled: false,
  visibleToUser: false,
  description: '',
  extJson: ''
})

const filteredSkillList = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return skillList.value.filter(item => {
    if (keyword) {
      const text = [
        item.skillKey,
        item.skillName,
        item.skillPath,
        item.description,
        item.fileDescription
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      if (!text.includes(keyword)) {
        return false
      }
    }

    if (statusFilter.value === 'saved' && !item._saved) {
      return false
    }
    if (statusFilter.value === 'discovered' && item._saved) {
      return false
    }
    if (statusFilter.value === 'missing' && item.sourceExists) {
      return false
    }
    if (statusFilter.value === 'usable' && !item.usable) {
      return false
    }
    if (statusFilter.value === 'unusable' && item.usable) {
      return false
    }
    if (enabledFilter.value === 'enabled' && !item.enabled) {
      return false
    }
    if (enabledFilter.value === 'disabled' && item.enabled) {
      return false
    }
    if (visibilityFilter.value === 'visible' && !item.visibleToUser) {
      return false
    }
    if (visibilityFilter.value === 'hidden' && item.visibleToUser) {
      return false
    }

    return true
  })
})

const enabledCount = computed(() => skillList.value.filter(item => item.enabled).length)
const visibleCount = computed(() => skillList.value.filter(item => item.visibleToUser).length)
const savedCount = computed(() => skillList.value.filter(item => item._saved).length)
const missingCount = computed(() => skillList.value.filter(item => !item.sourceExists).length)
const unusableCount = computed(() => skillList.value.filter(item => item.usable === false).length)

const normalizeRow = item => ({
  ...item,
  enabled: !!item.enabled,
  visibleToUser: !!item.visibleToUser,
  usable: item.usable !== false,
  availabilityIssues: Array.isArray(item.availabilityIssues) ? item.availabilityIssues : [],
  _saved: !!item.id
})

const loadSkills = async () => {
  loading.value = true
  try {
    const data = await getAdminSkillList()
    skillList.value = (Array.isArray(data) ? data : []).map(normalizeRow)
  } catch (error) {
    ElMessage.error('加载技能列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleSync = async () => {
  syncing.value = true
  try {
    const data = await syncSkills()
    ElMessage.success(`同步完成，新增 ${data?.syncedCount ?? 0} 条技能配置`)
    await loadSkills()
  } catch (error) {
    ElMessage.error('同步失败：' + (error.message || '未知错误'))
  } finally {
    syncing.value = false
  }
}

const persistRow = async row => {
  savingKey.value = row.skillKey
  try {
    const data = await updateSkillConfig(row.skillKey, {
      skillName: row.skillName || null,
      enabled: row.enabled,
      visibleToUser: row.visibleToUser,
      description: row.description || null,
      extJson: row.extJson || null
    })
    Object.assign(row, normalizeRow(data))
    ElMessage.success('技能配置已保存')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存失败')
    await loadSkills()
  } finally {
    savingKey.value = ''
  }
}

const handleQuickToggle = async row => {
  await persistRow(row)
}

const handleCreateRow = async row => {
  await persistRow(row)
}

const handleEdit = async row => {
  savingDetail.value = true
  try {
    const detail = await getAdminSkillDetail(row.skillKey)
    editForm.value = {
      skillKey: detail.skillKey,
      skillName: detail.skillName || '',
      enabled: !!detail.enabled,
      visibleToUser: !!detail.visibleToUser,
      description: detail.description || '',
      extJson: detail.extJson || ''
    }
    editDialogVisible.value = true
  } catch (error) {
    ElMessage.error('加载技能详情失败：' + (error.message || '未知错误'))
  } finally {
    savingDetail.value = false
  }
}

const submitEdit = async () => {
  savingDetail.value = true
  try {
    const data = await updateSkillConfig(editForm.value.skillKey, {
      skillName: editForm.value.skillName || null,
      enabled: editForm.value.enabled,
      visibleToUser: editForm.value.visibleToUser,
      description: editForm.value.description || null,
      extJson: editForm.value.extJson || null
    })

    const index = skillList.value.findIndex(item => item.skillKey === data.skillKey)
    if (index >= 0) {
      skillList.value[index] = normalizeRow(data)
    } else {
      skillList.value.unshift(normalizeRow(data))
    }

    editDialogVisible.value = false
    ElMessage.success('技能已更新')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '更新失败')
  } finally {
    savingDetail.value = false
  }
}

const handlePreview = async row => {
  previewVisible.value = true
  previewLoading.value = true
  previewDetail.value = null
  try {
    previewDetail.value = await getAdminSkillDetail(row.skillKey)
  } catch (error) {
    ElMessage.error('加载技能预览失败：' + (error.message || '未知错误'))
  } finally {
    previewLoading.value = false
  }
}

const handleDeleteRow = async row => {
  try {
    await ElMessageBox.confirm(`确认删除技能“${row.skillKey}”的已保存配置吗？`, '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    savingKey.value = row.skillKey
    await deleteSkillConfig(row.skillKey)
    ElMessage.success('技能配置已删除')
    await loadSkills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  } finally {
    savingKey.value = ''
  }
}

const getSourceLabel = row => {
  if (!row.sourceExists) {
    return '来源缺失'
  }
  if (row.usable === false) {
    return '不可用'
  }
  return row._saved ? '已保存' : '已发现'
}

const getSourceTagType = row => {
  if (!row.sourceExists) {
    return 'danger'
  }
  if (row.usable === false) {
    return 'warning'
  }
  return row._saved ? 'success' : 'info'
}

const formatAvailabilityIssues = issues => {
  if (!Array.isArray(issues) || issues.length === 0) {
    return ''
  }
  return issues.join('; ')
}

const formatDate = value => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

onMounted(() => {
  loadSkills()
})
</script>

<style scoped>
.skills-management {
  padding: var(--spacing-lg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-secondary);
}

.skills-management :deep(.el-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.skills-management :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-lg);
}

.page-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.page-subtitle {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.header-actions,
.toolbar,
.summary-row,
.action-group {
  display: flex;
  gap: var(--spacing-sm);
}

.toolbar {
  flex-wrap: wrap;
  margin-bottom: var(--spacing-md);
}

.toolbar-input {
  width: 320px;
}

.toolbar-select {
  width: 160px;
}

.summary-row {
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}

.table-wrapper {
  flex: 1;
  min-height: 0;
}

.ellipsis-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-tip {
  margin-top: 8px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}

.preview-panel {
  padding-right: var(--spacing-sm);
}

.preview-meta {
  margin-bottom: var(--spacing-lg);
}

.preview-section + .preview-section {
  margin-top: var(--spacing-lg);
}

.issues-list {
  margin-top: var(--spacing-sm);
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.section-title {
  margin-bottom: var(--spacing-sm);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.code-block {
  margin: 0;
  padding: var(--spacing-md);
  white-space: pre-wrap;
  word-break: break-word;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  background: var(--color-bg-tertiary);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-relaxed);
}
</style>
