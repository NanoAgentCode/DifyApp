<template>
  <div class="skills-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <div class="page-title">Skills Management</div>
            <div class="page-subtitle">Manage skill availability, visibility, metadata and skill file preview.</div>
          </div>
          <div class="header-actions">
            <el-button :loading="syncing" @click="handleSync">Sync</el-button>
            <el-button type="primary" :loading="loading" @click="loadSkills">Refresh</el-button>
          </div>
        </div>
      </template>

      <div class="toolbar">
        <el-input
          v-model="searchKeyword"
          placeholder="Search by key, name, path or description"
          clearable
          class="toolbar-input"
        />
        <el-select v-model="statusFilter" clearable placeholder="Config status" class="toolbar-select">
          <el-option label="Saved" value="saved" />
          <el-option label="Discovered only" value="discovered" />
          <el-option label="Missing source" value="missing" />
          <el-option label="Usable" value="usable" />
          <el-option label="Unavailable" value="unusable" />
        </el-select>
        <el-select v-model="enabledFilter" clearable placeholder="Enabled" class="toolbar-select">
          <el-option label="Enabled" value="enabled" />
          <el-option label="Disabled" value="disabled" />
        </el-select>
        <el-select v-model="visibilityFilter" clearable placeholder="Visible to user" class="toolbar-select">
          <el-option label="Visible" value="visible" />
          <el-option label="Hidden" value="hidden" />
        </el-select>
      </div>

      <div class="summary-row">
        <el-tag type="info">Total {{ filteredSkillList.length }}</el-tag>
        <el-tag type="success">Enabled {{ enabledCount }}</el-tag>
        <el-tag type="warning">Visible {{ visibleCount }}</el-tag>
        <el-tag type="primary">Saved {{ savedCount }}</el-tag>
        <el-tag type="danger">Missing {{ missingCount }}</el-tag>
        <el-tag :type="unusableCount ? 'danger' : 'success'">Unavailable {{ unusableCount }}</el-tag>
      </div>

      <div class="table-wrapper">
        <el-table v-loading="loading" :data="filteredSkillList" border stripe height="100%">
          <el-table-column prop="skillKey" label="Skill Key" min-width="180" show-overflow-tooltip />
          <el-table-column prop="skillName" label="Name" min-width="180" show-overflow-tooltip />
          <el-table-column prop="skillPath" label="Path" min-width="220" show-overflow-tooltip />
          <el-table-column label="Status" width="130" align="center">
            <template #default="{ row }">
              <el-tag :type="getSourceTagType(row)" size="small">{{ getSourceLabel(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Usability" width="130" align="center">
            <template #default="{ row }">
              <el-tooltip
                :content="formatAvailabilityIssues(row.availabilityIssues)"
                :disabled="!row.availabilityIssues || row.availabilityIssues.length === 0"
                placement="top"
              >
                <el-tag :type="row.usable ? 'success' : 'danger'" size="small">
                  {{ row.usable ? 'Usable' : 'Invalid' }}
                </el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="Enabled" width="100" align="center">
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :loading="savingKey === row.skillKey"
                @change="handleQuickToggle(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="Visible" width="100" align="center">
            <template #default="{ row }">
              <el-switch
                v-model="row.visibleToUser"
                :loading="savingKey === row.skillKey"
                @change="handleQuickToggle(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="Description" min-width="260">
            <template #default="{ row }">
              <div class="ellipsis-text">{{ row.description || row.fileDescription || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="Updated" min-width="180">
            <template #default="{ row }">
              {{ formatDate(row.updateTime) }}
            </template>
          </el-table-column>
          <el-table-column label="Actions" width="220" fixed="right" align="center">
            <template #default="{ row }">
              <div class="action-group">
                <el-button size="small" @click="handlePreview(row)">Preview</el-button>
                <el-button size="small" type="primary" @click="handleEdit(row)">Edit</el-button>
                <el-button
                  v-if="row._saved"
                  size="small"
                  type="danger"
                  :loading="savingKey === row.skillKey"
                  @click="handleDeleteRow(row)"
                >
                  Delete
                </el-button>
                <el-button
                  v-else
                  size="small"
                  type="success"
                  :loading="savingKey === row.skillKey"
                  @click="handleCreateRow(row)"
                >
                  Save
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="editDialogVisible" title="Edit Skill" width="760px" destroy-on-close>
      <el-form :model="editForm" label-width="120px">
        <el-form-item label="Skill Key">
          <el-input v-model="editForm.skillKey" disabled />
        </el-form-item>
        <el-form-item label="Skill Name">
          <el-input v-model="editForm.skillName" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="Enabled">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
        <el-form-item label="Visible To User">
          <el-switch v-model="editForm.visibleToUser" />
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="editForm.description" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="Ext JSON">
          <el-input
            v-model="editForm.extJson"
            type="textarea"
            :rows="10"
            placeholder='{"allowedCommands":["npm","mvn"]}'
          />
          <div class="form-tip">Leave blank if the skill only provides prompt context.</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="savingDetail" @click="submitEdit">Save</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="previewVisible" title="Skill Preview" size="55%">
      <div v-loading="previewLoading" class="preview-panel">
        <div v-if="previewDetail" class="preview-meta">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Skill Key">{{ previewDetail.skillKey }}</el-descriptions-item>
            <el-descriptions-item label="Skill Name">{{ previewDetail.skillName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Path">{{ previewDetail.skillPath || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Description">{{ previewDetail.description || previewDetail.fileDescription || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <div class="preview-section">
          <div class="section-title">Availability</div>
          <el-alert
            :title="previewDetail?.usable ? 'Skill is usable' : 'Skill is unavailable'"
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
          <pre class="code-block">{{ previewDetail?.skillContent || 'No skill file content found.' }}</pre>
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
    ElMessage.error('Failed to load skills: ' + (error.message || 'unknown error'))
  } finally {
    loading.value = false
  }
}

const handleSync = async () => {
  syncing.value = true
  try {
    const data = await syncSkills()
    ElMessage.success(`Sync completed, added ${data?.syncedCount ?? 0} skill configs`)
    await loadSkills()
  } catch (error) {
    ElMessage.error('Sync failed: ' + (error.message || 'unknown error'))
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
    ElMessage.success('Skill config saved')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || 'Save failed')
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
    ElMessage.error('Failed to load skill detail: ' + (error.message || 'unknown error'))
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
    ElMessage.success('Skill updated')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || 'Update failed')
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
    ElMessage.error('Failed to load preview: ' + (error.message || 'unknown error'))
  } finally {
    previewLoading.value = false
  }
}

const handleDeleteRow = async row => {
  try {
    await ElMessageBox.confirm(`Delete saved config for "${row.skillKey}"?`, 'Confirm', {
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
      type: 'warning'
    })
    savingKey.value = row.skillKey
    await deleteSkillConfig(row.skillKey)
    ElMessage.success('Skill config deleted')
    await loadSkills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || 'Delete failed')
    }
  } finally {
    savingKey.value = ''
  }
}

const getSourceLabel = row => {
  if (!row.sourceExists) {
    return 'Missing'
  }
  if (row.usable === false) {
    return 'Invalid'
  }
  return row._saved ? 'Saved' : 'Discovered'
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
