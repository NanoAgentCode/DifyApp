<template>
      <!-- 左侧工具栏 -->
      <el-aside width="300px" class="toolbar-panel">
        <div v-if="userMode" class="toolbar-header">
          <h3 class="toolbar-title">
            智能框图助手
            <el-tooltip content="模型配置在系统配置中设置" placement="top">
              <el-icon class="title-tip-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </h3>
          <el-button link @click="handleBack" size="small">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
        </div>
        <!-- 图表类型选择 -->
        <div class="diagram-type-section">
          <div class="section-title">图表类型</div>
          <el-select
            v-model="selectedDiagramType"
            @change="onDiagramTypeChange"
            class="diagram-type-select"
            placeholder="请选择图表类型"
          >
            <el-option
              v-for="type in diagramTypeOptions"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            >
              <div class="diagram-type-option">
                <el-icon><component :is="getIconComponent(type.icon)" /></el-icon>
                <span>{{ type.label }}</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <!-- 快速模板 -->
        <div class="template-section" v-if="selectedDiagramType !== 'custom'">
          <div class="section-title">快速模板</div>
          <div class="template-list">
            <div
              v-for="template in availableTemplates"
              :key="template.id"
              class="template-item"
              @click="loadTemplate(template)"
            >
              <el-icon><component :is="getIconComponent(template.icon)" /></el-icon>
              <span>{{ template.name }}</span>
            </div>
          </div>
        </div>

        <!-- AI 输入区域 -->
        <div class="ai-input-section">
          <el-input
            v-model="aiPrompt"
            type="textarea"
            :rows="4"
            :placeholder="currentPlaceholder"
            class="prompt-input"
          />
          <div class="button-group">
            <el-button
              type="primary"
              :loading="generating"
              @click="handleGenerate"
              :disabled="!aiPrompt.trim()"
              class="action-button"
            >
              <el-icon><MagicStick /></el-icon>
              生成图表
            </el-button>
            <el-button
              v-if="hasDiagram"
              :loading="modifying"
              @click="handleModify"
              :disabled="!aiPrompt.trim()"
              class="action-button"
            >
              <el-icon><Edit /></el-icon>
              修改图表
            </el-button>
          </div>
        </div>

        <!-- 图表管理 -->
        <el-divider style="margin: 8px 0;" />
        <div class="diagram-management">
          <div class="section-title">{{ userMode ? '图表操作' : '图表管理' }}</div>
          <div class="management-buttons">
            <el-button
              v-if="!userMode"
              type="success"
              @click="handleSave"
              :disabled="!hasDiagram"
              class="management-button"
            >
              <el-icon><DocumentAdd /></el-icon>
              保存图表
            </el-button>
            <el-button
              v-if="!userMode"
              @click="handleLoadList"
              class="management-button"
            >
              <el-icon><FolderOpened /></el-icon>
              加载图表
            </el-button>
            <el-button
              @click="handleClear"
              :disabled="!hasDiagram"
              class="management-button"
            >
              <el-icon><Delete /></el-icon>
              清空画布
            </el-button>
            <el-button
              v-if="userMode"
              @click="handleExport"
              :disabled="!hasDiagram"
              class="management-button"
            >
              <el-icon><Download /></el-icon>
              导出图表
            </el-button>
          </div>
        </div>

        <!-- 历史记录 -->
        <el-divider style="margin: 8px 0;" />
        <div class="history-section">
          <div class="section-title">历史记录</div>
          <el-scrollbar class="history-scrollbar">
            <div
              v-for="(item, index) in historyList"
              :key="item?.id || index"
              class="history-item"
            >
              <div class="history-prompt" @click="loadHistoryPrompt(item)">
                {{ typeof item === 'string' ? item : item.prompt }}
              </div>
              <el-button
                type="danger"
                :icon="Delete"
                size="small"
                text
                circle
                @click.stop="deleteHistoryItem(item)"
                class="history-delete-btn"
                title="删除"
              />
            </div>
            <el-empty v-if="historyList.length === 0" description="暂无历史记录" :image-size="60" />
          </el-scrollbar>
        </div>
      </el-aside>

</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ userMode:Boolean, handleBack:Function, selectedDiagramType:String, aiPrompt:String, diagramTypeOptions:Array, getIconComponent:Function, onDiagramTypeChange:Function, availableTemplates:Array, loadTemplate:Function, currentPlaceholder:String, generating:Boolean, modifying:Boolean, hasDiagram:Boolean, handleGenerate:Function, handleModify:Function, handleSave:Function, handleLoadList:Function, handleClear:Function, handleExport:Function, historyList:Array, loadHistoryPrompt:Function, deleteHistoryItem:Function })
const emit = defineEmits(['update:selectedDiagramType', 'update:aiPrompt'])
const selectedDiagramType = computed({ get: () => props.selectedDiagramType, set: value => emit('update:selectedDiagramType', value) })
const aiPrompt = computed({ get: () => props.aiPrompt, set: value => emit('update:aiPrompt', value) })
</script>
