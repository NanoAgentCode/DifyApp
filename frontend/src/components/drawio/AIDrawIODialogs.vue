<template>
    <!-- 保存对话框 -->
    <el-dialog
      v-if="!userMode"
      v-model="saveDialogVisible"
      title="保存图表"
      width="400px"
    >
      <el-form :model="saveForm" label-width="80px">
        <el-form-item label="图表名称">
          <el-input v-model="saveForm.name" placeholder="请输入图表名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 加载图表对话框 -->
    <el-dialog
      v-if="!userMode"
      v-model="loadDialogVisible"
      title="加载图表"
      width="600px"
    >
      <el-table :data="diagramList" style="width: 100%">
        <el-table-column prop="name" label="图表名称" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="150">
          <template #default="scope">
            <el-button
              type="primary"
              size="small"
              @click="loadDiagram(scope.row)"
            >
              加载
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="deleteDiagramItem(scope.row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ userMode:Boolean, saveDialogVisible:Boolean, saveForm:Object, saving:Boolean, confirmSave:Function, loadDialogVisible:Boolean, diagramList:Array, loadDiagram:Function, deleteDiagramItem:Function })
const emit = defineEmits(['update:saveDialogVisible', 'update:loadDialogVisible'])
const saveDialogVisible = computed({ get: () => props.saveDialogVisible, set: value => emit('update:saveDialogVisible', value) })
const loadDialogVisible = computed({ get: () => props.loadDialogVisible, set: value => emit('update:loadDialogVisible', value) })
</script>
