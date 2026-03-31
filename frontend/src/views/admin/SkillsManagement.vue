<template>
  <div class="skills-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>Skills 管理</span>
          <div class="header-actions">
            <el-button :loading="syncing" @click="handleSync">同步目录</el-button>
            <el-button type="primary" :loading="loading" @click="loadSkills">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="table-wrapper">
        <el-table v-loading="loading" :data="skillList" border stripe height="100%">
          <el-table-column prop="skillKey" label="Skill Key" min-width="180" />
          <el-table-column prop="skillName" label="名称" min-width="160" />
          <el-table-column prop="skillPath" label="路径" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tooltip
                :content="row.sourceExists ? '' : '技能源目录不存在，仅保留历史配置'"
                :disabled="row.sourceExists"
                placement="top"
              >
                <el-tag :type="row.sourceExists ? (row.id ? 'success' : 'info') : 'danger'" size="small">
                  {{ row.sourceExists ? (row.id ? '已保存' : '已发现') : '源不存在' }}
                </el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="100" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" :loading="savingKey === row.skillKey" @change="handleSwitchChange(row)" />
            </template>
          </el-table-column>
          <el-table-column label="普通用户可见" width="140" align="center">
            <template #default="{ row }">
              <el-switch v-model="row.visibleToUser" :loading="savingKey === row.skillKey" @change="handleSwitchChange(row)" />
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="260">
            <template #default="{ row }">
              <el-tooltip
                :content="row.description || '-'"
                placement="top"
                :show-after="200"
                :disabled="!(row.description && row.description.length > 24)"
              >
                <div class="ellipsis-text">
                  {{ row.description || '-' }}
                </div>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <el-button
                v-if="!row._saved"
                type="primary"
                size="small"
                :loading="savingKey === row.skillKey"
                @click="handleSaveRow(row)"
              >
                保存
              </el-button>
              <el-button
                v-else
                type="danger"
                size="small"
                :loading="savingKey === row.skillKey"
                @click="handleDeleteRow(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminSkillList, updateSkillConfig, syncSkills, deleteSkillConfig } from '@/api/skills'

const loading = ref(false)
const syncing = ref(false)
const savingKey = ref('')
const skillList = ref([])

const loadSkills = async () => {
  loading.value = true
  try {
    const data = await getAdminSkillList()
    skillList.value = (Array.isArray(data) ? data : []).map(item => ({ ...item, _saved: !!item.id }))
  } catch (error) {
    ElMessage.error('加载 skills 失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleSync = async () => {
  syncing.value = true
  try {
    const data = await syncSkills()
    const count = data?.syncedCount ?? 0
    ElMessage.success(`同步完成，新增 ${count} 个 skill`)
    await loadSkills()
  } catch (error) {
    ElMessage.error('同步失败：' + (error.message || '未知错误'))
  } finally {
    syncing.value = false
  }
}

const handleSaveRow = async (row) => {
  if (savingKey.value && savingKey.value !== row.skillKey) {
    return
  }
  savingKey.value = row.skillKey
  try {
    await updateSkillConfig(row.skillKey, {
      enabled: row.enabled,
      visibleToUser: row.visibleToUser
    })
    row._saved = true
    ElMessage.success('保存成功')
  } catch (error) {
    ElMessage.error('保存失败：' + (error.message || '未知错误'))
  } finally {
    savingKey.value = ''
  }
}

const handleSwitchChange = async (row) => {
  await handleSaveRow(row)
}

const handleDeleteRow = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除 "${row.skillKey}" 的配置吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    savingKey.value = row.skillKey
    await deleteSkillConfig(row.skillKey)
    row._saved = false
    ElMessage.success('删除成功')
    await loadSkills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  } finally {
    savingKey.value = ''
  }
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
}

.header-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.table-wrapper {
  flex: 1;
  min-height: 0;
}

:deep(.el-table__body-wrapper .el-scrollbar__wrap) {
  overflow-y: scroll;
}

.ellipsis-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
