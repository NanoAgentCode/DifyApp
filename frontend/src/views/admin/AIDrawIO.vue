<template>
  <div class="ai-drawio-container">
    <el-container class="full-height">
      <!-- 左侧工具栏 -->
      <el-aside width="300px" class="toolbar-panel">
        <div class="toolbar-header">
          <h3>智能框图助手</h3>
          <div class="header-top">
            <el-button type="text" @click="handleBack" size="small">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
          </div>
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

        <!-- LLM模型提示 -->
        <div class="model-section">
          <div style="font-size: 12px; color: #909399; text-align: center; padding: 8px 0;">
            模型配置在系统配置中设置
          </div>
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
          <div class="section-title">图表管理</div>
          <div class="management-buttons">
            <el-button 
              type="success" 
              @click="handleSave"
              :disabled="!hasDiagram"
              class="management-button"
            >
              <el-icon><DocumentAdd /></el-icon>
              保存图表
            </el-button>
            <el-button 
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
          </div>
        </div>

        <!-- 历史记录 -->
        <el-divider style="margin: 8px 0;" />
        <div class="history-section">
          <div class="section-title">历史记录</div>
          <el-scrollbar class="history-scrollbar">
            <div 
              v-for="item in historyList" 
              :key="item.id"
              class="history-item"
            >
              <div class="history-prompt" @click="loadHistoryPrompt(item.prompt)">{{ item.prompt }}</div>
              <el-button
                type="danger"
                :icon="Delete"
                size="small"
                text
                circle
                @click.stop="deleteHistoryItem(item.id)"
                class="history-delete-btn"
                title="删除"
              />
            </div>
            <el-empty v-if="historyList.length === 0" description="暂无历史记录" :image-size="60" />
          </el-scrollbar>
        </div>
      </el-aside>

      <!-- 主画布区域 -->
      <el-main class="canvas-panel">
        <div class="canvas-header">
          <div class="canvas-title">
            <el-icon><DataAnalysis /></el-icon>
            <span>图表编辑器</span>
          </div>
          <div class="canvas-actions">
            <el-button-group>
              <el-button size="small" @click="zoomOut" :disabled="zoomLevel <= 0.5" title="缩小">
                <el-icon><ZoomOut /></el-icon>
              </el-button>
              <el-button size="small" @click="resetZoom" title="重置缩放">
                <span style="min-width: 50px;">{{ Math.round(zoomLevel * 100) }}%</span>
              </el-button>
              <el-button size="small" @click="zoomIn" :disabled="zoomLevel >= 2" title="放大">
                <el-icon><ZoomIn /></el-icon>
              </el-button>
              <el-button size="small" @click="fitToWindow" title="适应窗口">
                <el-icon><FullScreen /></el-icon>
              </el-button>
            </el-button-group>
            <el-button size="small" @click="handleExport">
              <el-icon><Download /></el-icon>
              导出
            </el-button>
            <el-button size="small" @click="handleImport">
              <el-icon><Upload /></el-icon>
              导入
            </el-button>
          </div>
        </div>
        
        <!-- Mermaid 图表容器 -->
        <div 
          class="mermaid-wrapper"
          :class="{ dragging: isDragging }"
          ref="mermaidWrapper" 
          @wheel="handleWheel"
          @mousedown="handleMouseDown"
          @mousemove="handleMouseMove"
          @mouseup="handleMouseUp"
          @mouseleave="handleMouseUp"
        >
          <div 
            class="mermaid-container" 
            ref="mermaidContainer"
            :style="{ 
              transform: `translate(${panOffset.x}px, ${panOffset.y}px) scale(${zoomLevel})`, 
              transformOrigin: 'top left',
              cursor: isDragging ? 'grabbing' : 'grab'
            }"
          ></div>
        </div>
      </el-main>
    </el-container>

    <!-- 保存对话框 -->
    <el-dialog
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

// 动态导入 Mermaid，只在访问 AIDrawIO 页面时才加载
let mermaid = null
const loadMermaid = async () => {
  if (!mermaid) {
    const module = await import('mermaid')
    mermaid = module.default || module
  }
  return mermaid
}
import {
  MagicStick,
  Edit,
  DocumentAdd,
  FolderOpened,
  Delete,
  DataAnalysis,
  Download,
  Upload,
  Operation,
  Connection,
  Share,
  Timer,
  Box,
  UserFilled,
  Link,
  Setting,
  User,
  ShoppingCart,
  DocumentChecked,
  Cloudy,
  Menu,
  Folder,
  Reading,
  CreditCard,
  Lock,
  ShoppingBag,
  OfficeBuilding,
  DataBoard,
  ZoomIn,
  ZoomOut,
  FullScreen,
  ArrowLeft
} from '@element-plus/icons-vue'
import { 
  generateDiagram, 
  modifyDiagram, 
  saveDiagram, 
  getDiagramList, 
  getDiagramDetail,
  deleteDiagram,
  saveHistory,
  getHistoryList,
  deleteHistory
} from '@/api/drawio'

const router = useRouter()

// 返回主页
const handleBack = () => {
  router.push('/admin/chat')
}

// Mermaid 图表相关
const mermaidContainer = ref(null)
const mermaidWrapper = ref(null)
let mermaidInstance = null

// 缩放相关
const zoomLevel = ref(1)
const minZoom = 0.5
const maxZoom = 2
const zoomStep = 0.1

// 拖拽相关
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const panOffset = ref({ x: 0, y: 0 })
const lastPanOffset = ref({ x: 0, y: 0 })

// 图表类型
const selectedDiagramType = ref('flowchart')

// 图表类型选项（用于下拉菜单）
const diagramTypeOptions = [
  { value: 'flowchart', label: '流程图', icon: 'Operation' },
  { value: 'architecture', label: '架构图', icon: 'Connection' },
  { value: 'mindmap', label: '思维导图', icon: 'Share' },
  { value: 'sequence', label: '时序图', icon: 'Timer' },
  { value: 'uml', label: 'UML图', icon: 'Box' },
  { value: 'org', label: '组织架构', icon: 'UserFilled' },
  { value: 'network', label: '网络图', icon: 'Link' },
  { value: 'custom', label: '自定义', icon: 'Setting' }
]

// 图表类型配置
const diagramTypeConfig = {
  flowchart: {
    name: '流程图',
    icon: 'Operation',
    placeholder: '描述流程步骤，例如：用户登录流程，包含输入用户名密码、验证、登录成功/失败等步骤',
    promptPrefix: '请绘制一个流程图，'
  },
  architecture: {
    name: '架构图',
    icon: 'Connection',
    placeholder: '描述系统架构，例如：微服务架构，包含API网关、用户服务、订单服务、支付服务、数据库等',
    promptPrefix: '请绘制一个系统架构图，'
  },
  mindmap: {
    name: '思维导图',
    icon: 'Share',
    placeholder: '描述思维导图主题和分支，例如：项目管理，包含需求分析、设计、开发、测试、部署等分支',
    promptPrefix: '请绘制一个思维导图，'
  },
  sequence: {
    name: '时序图',
    icon: 'Timer',
    placeholder: '描述交互序列，例如：用户下单流程，包含用户、前端、订单服务、支付服务、库存服务之间的交互',
    promptPrefix: '请绘制一个时序图，'
  },
  uml: {
    name: 'UML图',
    icon: 'Box',
    placeholder: '描述UML类图或用例图，例如：电商系统类图，包含用户、商品、订单、购物车等类及其关系',
    promptPrefix: '请绘制一个UML图，'
  },
  org: {
    name: '组织架构',
    icon: 'UserFilled',
    placeholder: '描述组织架构，例如：公司组织架构，包含CEO、CTO、CFO，以及各部门经理和员工',
    promptPrefix: '请绘制一个组织架构图，'
  },
  network: {
    name: '网络图',
    icon: 'Link',
    placeholder: '描述网络拓扑，例如：企业网络架构，包含路由器、交换机、防火墙、服务器等设备',
    promptPrefix: '请绘制一个网络拓扑图，'
  },
  custom: {
    name: '自定义',
    icon: 'Setting',
    placeholder: '用自然语言描述您想要绘制的图表',
    promptPrefix: '请绘制一个图表，'
  }
}

// 快速模板
const templates = {
  flowchart: [
    { id: 'login', name: '用户登录流程', icon: 'User', prompt: '绘制用户登录流程图，包含输入账号密码、验证、登录成功、登录失败等步骤' },
    { id: 'order', name: '订单处理流程', icon: 'ShoppingCart', prompt: '绘制订单处理流程图，包含下单、支付、发货、收货、评价等步骤' },
    { id: 'approval', name: '审批流程', icon: 'DocumentChecked', prompt: '绘制审批流程图，包含提交申请、部门审批、财务审批、总经理审批等步骤' }
  ],
  architecture: [
    { id: 'microservice', name: '微服务架构', icon: 'Connection', prompt: '绘制微服务架构图，包含API网关、用户服务、订单服务、支付服务、商品服务、数据库等组件' },
    { id: 'cloud', name: '云架构', icon: 'Cloudy', prompt: '绘制云架构图，包含负载均衡、Web服务器、应用服务器、数据库、缓存等组件' },
    { id: 'layered', name: '分层架构', icon: 'Menu', prompt: '绘制分层架构图，包含表示层、业务层、数据访问层、数据库层' }
  ],
  mindmap: [
    { id: 'project', name: '项目管理', icon: 'Folder', prompt: '绘制项目管理思维导图，包含需求分析、设计、开发、测试、部署、运维等分支' },
    { id: 'product', name: '产品规划', icon: 'Box', prompt: '绘制产品规划思维导图，包含市场分析、用户需求、功能设计、技术方案等分支' },
    { id: 'learning', name: '学习计划', icon: 'Reading', prompt: '绘制学习计划思维导图，包含基础知识、进阶内容、实践项目、总结复习等分支' }
  ],
  sequence: [
    { id: 'payment', name: '支付流程', icon: 'CreditCard', prompt: '绘制支付时序图，包含用户、前端、订单服务、支付服务、银行之间的交互序列' },
    { id: 'login', name: '登录验证', icon: 'Lock', prompt: '绘制登录验证时序图，包含用户、前端、认证服务、数据库之间的交互' }
  ],
  uml: [
    { id: 'ecommerce', name: '电商系统', icon: 'ShoppingBag', prompt: '绘制电商系统UML类图，包含用户、商品、订单、购物车、支付等类及其关系' },
    { id: 'library', name: '图书管理系统', icon: 'Reading', prompt: '绘制图书管理系统UML类图，包含图书、借阅者、借阅记录、管理员等类及其关系' }
  ],
  org: [
    { id: 'company', name: '公司组织', icon: 'OfficeBuilding', prompt: '绘制公司组织架构图，包含CEO、CTO、CFO，以及技术部、市场部、财务部等部门' },
    { id: 'team', name: '项目团队', icon: 'User', prompt: '绘制项目团队架构图，包含项目经理、产品经理、开发团队、测试团队等' }
  ],
  network: [
    { id: 'enterprise', name: '企业网络', icon: 'Connection', prompt: '绘制企业网络拓扑图，包含核心交换机、接入交换机、路由器、防火墙、服务器等设备' },
    { id: 'data-center', name: '数据中心', icon: 'DataBoard', prompt: '绘制数据中心网络架构图，包含核心交换机、汇聚交换机、接入交换机、服务器、存储设备等' }
  ]
}

// 计算当前可用的模板
const availableTemplates = computed(() => {
  return templates[selectedDiagramType.value] || []
})

// 计算当前占位符
const currentPlaceholder = computed(() => {
  return diagramTypeConfig[selectedDiagramType.value]?.placeholder || '用自然语言描述您想要绘制的图表'
})

// AI 相关
const aiPrompt = ref('')
const generating = ref(false)
const modifying = ref(false)
const hasDiagram = ref(false)
const currentDiagramJson = ref('')


// 历史记录
const historyList = ref([])

// 保存相关
const saveDialogVisible = ref(false)
const saveForm = ref({ name: '' })
const saving = ref(false)

// 加载相关
const loadDialogVisible = ref(false)
const diagramList = ref([])

// Mermaid 初始化标志
let mermaidInitialized = false

// 初始化 Mermaid
const initMermaid = async () => {
  if (mermaidInitialized) {
    return
  }

  try {
    // 动态加载 Mermaid
    const mermaidModule = await loadMermaid()
    
    // 初始化 Mermaid
    mermaidModule.initialize({
      startOnLoad: false,
      theme: 'default',
      securityLevel: 'loose',
      flowchart: {
        useMaxWidth: true,
        htmlLabels: true,
        curve: 'basis'
      },
      themeVariables: {
        // 基于 Transformer 架构图的颜色主题
        primaryColor: '#409eff',
        primaryTextColor: '#303133',
        primaryBorderColor: '#409eff',
        lineColor: '#606266',
        secondaryColor: '#ecf5ff',
        tertiaryColor: '#f5f7fa',
        // 自定义颜色变量
        lightBlue: '#ADD8E6',      // 浅蓝色 - 输入/输出嵌入层
        yellow: '#FFD700',          // 黄色 - 位置编码
        purple: '#9370DB',          // 紫色 - 编码器块
        red: '#FF5252',             // 红色 - 注意力机制、前馈网络
        green: '#4CAF50',           // 绿色 - 归一化层
        orange: '#FF9800',          // 橙色 - 解码器块
        darkBlue: '#1976D2',        // 深蓝色 - 输出层
        gray: '#808080'             // 灰色 - 连接线
      }
    })
    
    mermaidInitialized = true
    console.log('Mermaid 初始化完成')
  } catch (e) {
    console.error('Mermaid 初始化失败:', e)
  }
}

// 渲染 Mermaid 图表
const renderMermaid = async (mermaidCode) => {
  if (!mermaidContainer.value) {
    ElMessage.warning('容器未初始化')
    return
  }

  if (!mermaidCode || !mermaidCode.trim()) {
    mermaidContainer.value.innerHTML = ''
    hasDiagram.value = false
    return
  }

  // 确保 Mermaid 已初始化
  if (!mermaidInitialized) {
    await initMermaid()
  }

  try {
    // 对于架构图，强制确保使用 TD（从上到下）方向，并添加必要的布局指令
    let codeToRender = mermaidCode.trim()
    if (selectedDiagramType.value === 'architecture') {
      // 强制确保架构图使用 TD 方向（整体垂直分层）
      // 最关键：强制替换第一行的方向为 TD
      codeToRender = codeToRender.replace(/^(flowchart|graph)\s+(LR|TD|BT|RL)/i, '$1 TD')
      // 先检查并替换所有可能的 LR
      codeToRender = codeToRender.replace(/flowchart\s+LR/gi, 'flowchart TD')
      codeToRender = codeToRender.replace(/graph\s+LR/gi, 'graph TD')
      // 如果完全没有指定方向，添加 TD
      if (!codeToRender.match(/^(flowchart|graph)\s+(TD|LR|BT|RL)/i)) {
        codeToRender = codeToRender.replace(/^(flowchart|graph)/i, '$1 TD')
      }
      // 再次确保第一行是 TD（防止遗漏）
      const firstLine = codeToRender.split('\n')[0]
      if (!firstLine.match(/^(flowchart|graph)\s+TD/i)) {
        codeToRender = codeToRender.replace(/^(flowchart|graph)(\s+(TD|LR|BT|RL))?/i, '$1 TD')
        console.log('前端强制修复：已设置第一行为 TD')
      }
      
      // 确保每个 subgraph 内有 direction LR（用于水平布局）
      const subgraphRegex = /subgraph\s+[\w"']+[^\n]*\n/g
      let match
      const subgraphs = []
      while ((match = subgraphRegex.exec(codeToRender)) !== null) {
        subgraphs.push(match.index)
      }
      
      // 为每个 subgraph 检查并添加 direction LR（如果缺失）
      for (let i = subgraphs.length - 1; i >= 0; i--) {
        const subgraphStart = subgraphs[i]
        const nextSubgraph = i < subgraphs.length - 1 ? subgraphs[i + 1] : codeToRender.length
        const subgraphContent = codeToRender.substring(subgraphStart, nextSubgraph)
        
        // 检查是否已有 direction LR（必须是独立的一行）
        const hasDirectionLR = subgraphContent.match(/^\s*direction\s+LR\s*$/m)
        
        // 检查是否有错误的 direction TD，需要改为 LR
        const wrongDirectionPattern = /direction\s+TD\s*[^\n]*/g
        let wrongMatch
        const wrongMatches = []
        while ((wrongMatch = wrongDirectionPattern.exec(subgraphContent)) !== null) {
          wrongMatches.push({
            index: subgraphStart + wrongMatch.index,
            fullMatch: wrongMatch[0]
          })
        }
        
        // 从后往前修复，将 direction TD 改为 direction LR
        for (let j = wrongMatches.length - 1; j >= 0; j--) {
          const wrongMatch = wrongMatches[j]
          const wrongLine = wrongMatch.fullMatch
          const lineStartIndex = wrongMatch.index
          const lineEndIndex = codeToRender.indexOf('\n', lineStartIndex)
          const fullLine = codeToRender.substring(lineStartIndex, lineEndIndex >= 0 ? lineEndIndex : codeToRender.length)
          const indent = fullLine.match(/^(\s*)/)?.[1] || ''
          
          // 修复：将 direction TD 改为 direction LR
          const fixed = indent + '        direction LR'
          const lineEnd = lineEndIndex >= 0 ? lineEndIndex : codeToRender.length
          codeToRender = codeToRender.substring(0, lineStartIndex) + 
                        fixed + 
                        codeToRender.substring(lineEnd)
        }
        
        // 如果没有 direction LR，添加它
        if (!hasDirectionLR && !wrongMatches.length) {
          // 找到 subgraph 行的结束位置
          const subgraphLineEnd = codeToRender.indexOf('\n', subgraphStart) + 1
          // 在 subgraph 后添加 direction LR（独立的一行）
          const indent = codeToRender.substring(subgraphStart, subgraphLineEnd).match(/^(\s*)/)?.[1] || ''
          codeToRender = codeToRender.substring(0, subgraphLineEnd) + 
                        indent + '        direction LR\n' + 
                        codeToRender.substring(subgraphLineEnd)
        }
      }
      
      // 在 subgraph 之间添加不可见的连接来强制垂直布局
      // 提取所有 subgraph ID（处理带引号和不带引号的情况）
      const subgraphIdRegex = /subgraph\s+([\w"']+)/g
      const subgraphIds = []
      let idMatch
      while ((idMatch = subgraphIdRegex.exec(codeToRender)) !== null) {
        let subgraphId = idMatch[1]
        // 移除引号
        subgraphId = subgraphId.replace(/["']/g, '')
        // 移除可能的方括号内容（如 Layer1["表示层"] -> Layer1）
        subgraphId = subgraphId.split('[')[0].trim()
        if (subgraphId) {
          subgraphIds.push(subgraphId)
        }
      }
      
      // 在 subgraph 之间添加不可见连接（使用 --- 但通过 linkStyle 隐藏）
      // 在第一个 subgraph 的 end 之后，第二个 subgraph 之前添加连接
      let linkIndex = 0
      for (let i = 0; i < subgraphIds.length - 1; i++) {
        const currentId = subgraphIds[i]
        const nextId = subgraphIds[i + 1]
        // 查找当前 subgraph 的 end，并在其后添加不可见连接
        const endPattern = new RegExp(`(\\s+end\\s*\\n)(?=\\s*subgraph\\s+[\\w"']*${nextId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'g')
        if (endPattern.test(codeToRender)) {
          codeToRender = codeToRender.replace(
            endPattern,
            `$1    ${currentId} --- ${nextId}\n`
          )
          linkIndex++
        }
      }
      
      // 在 subgraph 内的节点之间添加不可见的水平连接来强制水平布局
      const subgraphContentRegex = /subgraph\s+[\w"']+[^\n]*\n([\s\S]*?)\s+end/g
      let subgraphMatch
      const subgraphContents = []
      while ((subgraphMatch = subgraphContentRegex.exec(codeToRender)) !== null) {
        subgraphContents.push({
          start: subgraphMatch.index,
          end: subgraphMatch.index + subgraphMatch[0].length,
          content: subgraphMatch[1],
          fullMatch: subgraphMatch[0]
        })
      }
      
      // 从后往前处理，避免索引变化
      for (let i = subgraphContents.length - 1; i >= 0; i--) {
        const subgraphInfo = subgraphContents[i]
        const content = subgraphInfo.content
        
        // 提取节点ID（从节点定义中提取，例如 A[Web前端]:::presentation -> A）
        const nodeIdRegex = /^\s*([A-Za-z_][\w]*)\s*\[/gm
        const nodeIds = []
        let nodeMatch
        while ((nodeMatch = nodeIdRegex.exec(content)) !== null) {
          nodeIds.push(nodeMatch[1])
        }
        
        // 如果节点数量大于1，添加水平连接
        if (nodeIds.length > 1) {
          // 找到 end 的位置
          const endIndex = codeToRender.indexOf('end', subgraphInfo.start)
          if (endIndex > 0) {
            // 在 end 之前添加水平连接
            let horizontalLinks = ''
            for (let j = 0; j < nodeIds.length - 1; j++) {
              horizontalLinks += `        ${nodeIds[j]} --- ${nodeIds[j + 1]}\n`
            }
            codeToRender = codeToRender.substring(0, endIndex) + 
                          horizontalLinks + 
                          codeToRender.substring(endIndex)
            linkIndex += (nodeIds.length - 1)
          }
        }
      }
      
      // 添加 linkStyle 来隐藏所有连接线（包括所有类型的连接：-->, ---, <-->, <->, -.-> 等）
      // 重新计算所有连接线的数量（包括我们添加的和原有的）
      const allLinkPattern = /(\w+)\s+(--|==|-\.-|<-|->|<->|<-->|<-\|->|==>|<=>|<-\|)\s*(\w+)/g
      let allLinkMatch
      let totalLinkCount = 0
      while ((allLinkMatch = allLinkPattern.exec(codeToRender)) !== null) {
        totalLinkCount++
      }
      
      if (totalLinkCount > 0) {
        const hiddenLinks = []
        for (let i = 0; i < totalLinkCount; i++) {
          hiddenLinks.push(`    linkStyle ${i} stroke-width:0px,stroke:transparent`)
        }
        codeToRender = codeToRender + '\n' + hiddenLinks.join('\n')
        console.log(`已添加 ${totalLinkCount} 个隐藏连接样式（包括所有类型的连接线）`)
      }
    }
    
    // 生成唯一 ID
    const id = `mermaid-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    
    // 清空容器
    mermaidContainer.value.innerHTML = ''
    
    // 等待 DOM 更新
    await nextTick()
    
    // 使用 render 方法渲染（不需要创建 DOM 元素）
    const mermaidModule = await loadMermaid()
    const { svg } = await mermaidModule.render(id, codeToRender)
    
    // 直接设置 SVG 内容
    mermaidContainer.value.innerHTML = svg
    
    // 更新当前图表代码（使用修正后的代码）
    currentDiagramJson.value = codeToRender
    hasDiagram.value = true
    ElMessage.success('图表渲染成功')
  } catch (e) {
    console.error('渲染 Mermaid 图表失败:', e)
    const errorMsg = e.message || '未知错误'
    ElMessage.error('渲染图表失败: ' + errorMsg)
    mermaidContainer.value.innerHTML = `<div class="mermaid-error">图表渲染失败: ${errorMsg}<br/><pre style="margin-top: 10px; font-size: 12px; color: #666;">${mermaidCode.substring(0, 200)}...</pre></div>`
  }
}

// 加载 Mermaid 代码
const loadMermaidCode = (mermaidCode) => {
  if (typeof mermaidCode === 'string') {
    renderMermaid(mermaidCode)
  } else {
    ElMessage.warning('无效的图表数据格式')
  }
}

// 导出 Mermaid 代码
const exportMermaidCode = () => {
  return currentDiagramJson.value || ''
}

// 清空图表
const clearMermaid = () => {
  if (mermaidContainer.value) {
    mermaidContainer.value.innerHTML = ''
    currentDiagramJson.value = ''
    hasDiagram.value = false
  }
}

// 图表类型变更处理
const onDiagramTypeChange = (type) => {
  // 切换类型时清空输入（可选）
  // aiPrompt.value = ''
}

// 获取图标组件
const getIconComponent = (iconName) => {
  const iconMap = {
    User,
    ShoppingCart,
    DocumentChecked,
    Connection,
    Cloudy,
    Menu,
    Folder,
    Box,
    Reading,
    CreditCard,
    Lock,
    ShoppingBag,
    OfficeBuilding,
    DataBoard
  }
  return iconMap[iconName] || Box
}

// 加载模板
const loadTemplate = (template) => {
  aiPrompt.value = template.prompt
  ElMessage.success(`已加载模板：${template.name}`)
}

// 构建完整的 prompt（根据图表类型添加前缀）
const buildFullPrompt = (userPrompt) => {
  const config = diagramTypeConfig[selectedDiagramType.value]
  if (config && selectedDiagramType.value !== 'custom') {
    // 如果用户输入已经包含图表类型关键词，则不添加前缀
    const typeKeywords = ['流程图', '架构图', '思维导图', '时序图', 'UML', '组织架构', '网络图']
    const hasTypeKeyword = typeKeywords.some(keyword => userPrompt.includes(keyword))
    
    if (!hasTypeKeyword) {
      return config.promptPrefix + userPrompt
    }
  }
  return userPrompt
}

// 生成图表
const handleGenerate = async () => {
  if (!aiPrompt.value.trim()) {
    ElMessage.warning('请输入图表描述')
    return
  }

  generating.value = true
  try {
    const fullPrompt = buildFullPrompt(aiPrompt.value)
    // 不传递modelId，让后端从系统配置读取
    const response = await generateDiagram(fullPrompt, null, selectedDiagramType.value)
    
    if (response && response.diagramJson) {
      loadMermaidCode(response.diagramJson)
      // 保存历史记录到数据库
      const historyItem = `${diagramTypeConfig[selectedDiagramType.value]?.name || ''}: ${aiPrompt.value}`
      try {
        await saveHistory(historyItem, selectedDiagramType.value)
        // 重新加载历史记录列表
        await loadHistoryList()
      } catch (error) {
        console.error('保存历史记录失败:', error)
        // 保存失败不影响主流程，只记录错误
      }
      ElMessage.success('图表生成成功！')
    } else {
      ElMessage.error('生成失败，请检查后端API实现')
    }
  } catch (error) {
    console.error('生成图表失败:', error)
    ElMessage.error(error.response?.data?.error || '生成图表失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

// 修改图表
const handleModify = async () => {
  if (!aiPrompt.value.trim()) {
    ElMessage.warning('请输入修改指令')
    return
  }

  if (!currentDiagramJson.value) {
    ElMessage.warning('请先获取当前图表内容')
    return
  }

  modifying.value = true
  try {
    // 不传递modelId，让后端从系统配置读取
    const response = await modifyDiagram(currentDiagramJson.value, aiPrompt.value, null)
    
    if (response && response.diagramJson) {
      loadMermaidCode(response.diagramJson)
      ElMessage.success('图表修改成功！')
    } else {
      ElMessage.error('修改失败，请检查后端API实现')
    }
  } catch (error) {
    console.error('修改图表失败:', error)
    ElMessage.error(error.response?.data?.error || '修改图表失败，请稍后重试')
  } finally {
    modifying.value = false
  }
}

// 保存图表
const handleSave = () => {
  const code = exportMermaidCode()
  if (!code) {
    ElMessage.warning('没有可保存的图表')
    return
  }
  saveForm.value.name = ''
  saveDialogVisible.value = true
}

const confirmSave = async () => {
  if (!saveForm.value.name.trim()) {
    ElMessage.warning('请输入图表名称')
    return
  }

  const code = exportMermaidCode()
  if (!code) {
    ElMessage.warning('没有可保存的图表')
    return
  }

  saving.value = true
  try {
    await saveDiagram(saveForm.value.name, code, selectedDiagramType.value)
    ElMessage.success('保存成功！')
    saveDialogVisible.value = false
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error(error.response?.data?.error || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

// 加载图表列表
const handleLoadList = async () => {
  loadDialogVisible.value = true
  try {
    const response = await getDiagramList()
    diagramList.value = response || []
  } catch (error) {
    console.error('加载列表失败:', error)
    ElMessage.error('加载图表列表失败')
  }
}

// 加载图表
const loadDiagram = async (diagram) => {
  try {
    const response = await getDiagramDetail(diagram.id)
    if (response && response.diagramJson) {
      loadMermaidCode(response.diagramJson)
      loadDialogVisible.value = false
      ElMessage.success('加载成功！')
    }
  } catch (error) {
    console.error('加载图表失败:', error)
    ElMessage.error('加载图表失败')
  }
}

// 删除图表
const deleteDiagramItem = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这个图表吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await deleteDiagram(id)
    ElMessage.success('删除成功！')
    // 刷新列表
    const response = await getDiagramList()
    diagramList.value = response || []
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 清空画布
const handleClear = () => {
  ElMessageBox.confirm('确定要清空画布吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    clearMermaid()
    resetZoom()
    ElMessage.success('画布已清空')
  }).catch(() => {})
}

// 缩放功能
const zoomIn = () => {
  if (zoomLevel.value < maxZoom) {
    zoomLevel.value = Math.min(zoomLevel.value + zoomStep, maxZoom)
  }
}

const zoomOut = () => {
  if (zoomLevel.value > minZoom) {
    zoomLevel.value = Math.max(zoomLevel.value - zoomStep, minZoom)
  }
}

const resetZoom = () => {
  zoomLevel.value = 1
  panOffset.value = { x: 0, y: 0 }
  lastPanOffset.value = { x: 0, y: 0 }
}

// 拖拽处理
const handleMouseDown = (e) => {
  // 只在鼠标左键按下时开始拖拽
  if (e.button !== 0) return
  
  // 如果点击的是 SVG 内部的链接或按钮，不拖拽
  if (e.target.tagName === 'A' || e.target.closest('a')) {
    return
  }
  
  isDragging.value = true
  dragStart.value = {
    x: e.clientX - panOffset.value.x,
    y: e.clientY - panOffset.value.y
  }
  lastPanOffset.value = { ...panOffset.value }
  
  // 阻止默认行为
  e.preventDefault()
}

const handleMouseMove = (e) => {
  if (!isDragging.value) return
  
  panOffset.value = {
    x: e.clientX - dragStart.value.x,
    y: e.clientY - dragStart.value.y
  }
  
  e.preventDefault()
}

const handleMouseUp = () => {
  if (isDragging.value) {
    isDragging.value = false
  }
}

const fitToWindow = () => {
  if (!mermaidContainer.value || !mermaidWrapper.value) {
    return
  }
  
  const svg = mermaidContainer.value.querySelector('svg')
  if (!svg) {
    resetZoom()
    return
  }
  
  try {
    const wrapperWidth = mermaidWrapper.value.clientWidth
    const wrapperHeight = mermaidWrapper.value.clientHeight
    
    // 尝试多种方式获取 SVG 尺寸
    let svgWidth = 0
    let svgHeight = 0
    
    if (svg.getBBox) {
      try {
        const bbox = svg.getBBox()
        svgWidth = bbox.width
        svgHeight = bbox.height
      } catch (e) {
        // getBBox 可能失败，使用备用方法
      }
    }
    
    if (svgWidth === 0 || svgHeight === 0) {
      svgWidth = svg.clientWidth || svg.getAttribute('width') || svg.viewBox?.baseVal?.width || 0
      svgHeight = svg.clientHeight || svg.getAttribute('height') || svg.viewBox?.baseVal?.height || 0
    }
    
    if (svgWidth > 0 && svgHeight > 0) {
      const scaleX = (wrapperWidth - 40) / svgWidth  // 留出边距
      const scaleY = (wrapperHeight - 40) / svgHeight
      zoomLevel.value = Math.min(scaleX, scaleY, maxZoom)
    } else {
      resetZoom()
    }
  } catch (e) {
    console.error('适应窗口失败:', e)
    resetZoom()
  }
}

// 鼠标滚轮缩放（Ctrl/Cmd + 滚轮）
const handleWheel = (event) => {
  if (event.ctrlKey || event.metaKey) {
    event.preventDefault()
    const delta = event.deltaY > 0 ? -zoomStep : zoomStep
    const newZoom = Math.max(minZoom, Math.min(maxZoom, zoomLevel.value + delta))
    zoomLevel.value = newZoom
  }
}

// 导出
const handleExport = () => {
  const code = exportMermaidCode()
  if (!code) {
    ElMessage.warning('没有可导出的图表')
    return
  }
  
  // 创建下载链接
  const blob = new Blob([code], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `diagram-${Date.now()}.mmd`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  
  ElMessage.success('图表已导出')
}

// 导入
const handleImport = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.mmd,.txt,.md'
  input.onchange = (e) => {
    const file = e.target.files[0]
    if (!file) return
    
    const reader = new FileReader()
    reader.onload = (event) => {
      try {
        const code = event.target.result
        loadMermaidCode(code)
        ElMessage.success('图表导入成功')
      } catch (error) {
        console.error('导入失败:', error)
        ElMessage.error('导入失败: ' + error.message)
      }
    }
    reader.readAsText(file)
  }
  input.click()
}

// 加载历史提示
const loadHistoryPrompt = (prompt) => {
  // 如果历史记录包含类型前缀（格式：类型: 提示），则只提取提示部分
  const colonIndex = prompt.indexOf(':')
  if (colonIndex > 0) {
    aiPrompt.value = prompt.substring(colonIndex + 1).trim()
  } else {
    aiPrompt.value = prompt
  }
}

// 加载历史记录列表
const loadHistoryList = async () => {
  try {
    const response = await getHistoryList()
    if (response && Array.isArray(response)) {
      historyList.value = response
    }
  } catch (error) {
    console.error('加载历史记录失败:', error)
    historyList.value = []
  }
}

// 删除历史记录项
const deleteHistoryItem = async (id) => {
  try {
    await deleteHistory(id)
    // 重新加载历史记录列表
    await loadHistoryList()
    ElMessage.success('已删除历史记录')
  } catch (error) {
    console.error('删除历史记录失败:', error)
    ElMessage.error('删除历史记录失败')
  }
}

// 加载系统配置的默认模型信息
onMounted(async () => {
  // 加载历史记录列表
  await loadHistoryList()
  
  // 等待 DOM 渲染完成后初始化 Mermaid
  await nextTick()
  await initMermaid()
})
</script>

<style scoped>
/* ========== 页面容器 ========== */
.ai-drawio-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-secondary);
}

.full-height {
  height: 100%;
}

/* ========== 工具栏面板 ========== */
.toolbar-panel {
  background: var(--color-bg-primary);
  border-right: 1px solid var(--color-border-lighter);
  padding: var(--spacing-md) var(--spacing-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
  box-shadow: var(--shadow-sm);
}

.toolbar-header {
  margin-bottom: var(--spacing-md);
  flex-shrink: 0;
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--color-border-lighter);
}

.header-top {
  display: flex;
  align-items: center;
}

.header-top .el-button {
  transition: all var(--transition-base);
}

.header-top .el-button:hover {
  color: var(--color-primary);
  transform: translateX(-2px);
}

.toolbar-header h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark-1) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* ========== 图表类型选择 ========== */
.diagram-type-section {
  margin-bottom: var(--spacing-md);
  margin-left: 0;
  margin-right: 0;
  flex-shrink: 0;
}

.model-section {
  margin-bottom: var(--spacing-md);
  margin-left: 0;
  margin-right: 0;
  flex-shrink: 0;
  padding: var(--spacing-sm);
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-lighter);
}

.model-section div {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  text-align: center;
  padding: var(--spacing-xs) 0;
}

.model-select {
  width: 100%;
}

.diagram-type-select {
  width: 100%;
}

.diagram-type-select :deep(.el-input__wrapper) {
  margin: 0;
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.diagram-type-select :deep(.el-input__wrapper:hover) {
  box-shadow: var(--shadow-xs);
}

.diagram-type-select :deep(.el-input__wrapper.is-focus) {
  box-shadow: var(--shadow-primary);
}

.diagram-type-option {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.diagram-type-option .el-icon {
  font-size: var(--font-size-md);
  color: var(--color-primary);
}

.diagram-type-selected {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.diagram-type-selected .el-icon {
  font-size: var(--font-size-md);
  color: var(--color-primary);
}

/* ========== 快速模板 ========== */
.template-section {
  margin-bottom: var(--spacing-md);
  margin-left: 0;
  margin-right: 0;
  flex-shrink: 0;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.template-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-sm);
  background: var(--color-bg-primary);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-base);
  border: 1px solid var(--color-border-light);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-tight);
}

.template-item:hover {
  background: var(--color-bg-active);
  border-color: var(--color-primary);
  transform: translateX(2px);
  box-shadow: var(--shadow-xs);
}

.template-item .el-icon {
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  flex-shrink: 0;
  transition: all var(--transition-base);
}

.template-item:hover .el-icon {
  transform: scale(1.1);
}

/* ========== AI输入区域 ========== */
.ai-input-section {
  margin-bottom: var(--spacing-md);
  margin-left: 0;
  margin-right: 0;
  flex-shrink: 0;
}

.prompt-input {
  margin-bottom: var(--spacing-sm);
}

.prompt-input :deep(.el-textarea__inner) {
  margin: 0;
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

.prompt-input :deep(.el-textarea__inner:hover) {
  box-shadow: var(--shadow-xs);
}

.prompt-input :deep(.el-textarea__inner:focus) {
  box-shadow: var(--shadow-primary);
}

.button-group {
  display: flex;
  gap: var(--spacing-sm);
  width: 100%;
  margin: 0;
}

.action-button {
  flex: 1;
  min-width: 0;
  height: var(--button-height-md);
  transition: all var(--transition-base);
  border-radius: var(--radius-md);
}

.action-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-primary);
}

.action-button:active {
  transform: translateY(0);
}

.diagram-management {
  margin-top: 8px;
  margin-left: 0;
  margin-right: 0;
  flex-shrink: 0;
}

.management-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  margin: 0;
}

.management-button {
  width: 100%;
  margin: 0 !important;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.management-button .el-icon {
  margin-right: 6px;
}

.action-button .el-icon {
  margin-right: 6px;
}

/* ========== 区域标题 ========== */
.section-title {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-regular);
  margin-bottom: var(--spacing-sm);
  font-size: var(--font-size-sm);
  flex-shrink: 0;
}

/* ========== 历史记录 ========== */
.history-section {
  margin-top: var(--spacing-sm);
  margin-left: 0;
  margin-right: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 150px;
  overflow: hidden;
}

.history-scrollbar {
  flex: 1;
  min-height: 0;
}

.history-item {
  padding: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
  background: var(--color-bg-primary);
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
  border: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.history-item:hover {
  background: var(--color-bg-active);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-xs);
  transform: translateX(2px);
}

.history-delete-btn {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--transition-base);
}

.history-item:hover .history-delete-btn {
  opacity: 1;
}

.history-prompt {
  font-size: var(--font-size-xs);
  color: var(--color-text-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  flex: 1;
  cursor: pointer;
  min-width: 0;
  transition: color var(--transition-base);
}

.history-prompt:hover {
  color: var(--color-primary);
}

/* ========== 画布面板 ========== */
.canvas-panel {
  padding: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary);
}

.canvas-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--color-border-lighter);
  background: var(--color-bg-tertiary);
  box-shadow: var(--shadow-xs);
}

.canvas-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.canvas-title .el-icon {
  color: var(--color-primary);
  transition: all var(--transition-base);
}

.canvas-header:hover .canvas-title .el-icon {
  transform: scale(1.1);
}

.canvas-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.canvas-actions .el-button {
  transition: all var(--transition-base);
}

.canvas-actions .el-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-xs);
}

/* ========== Mermaid图表容器 ========== */
.mermaid-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  user-select: none;
  box-shadow: var(--shadow-sm);
}

.mermaid-wrapper.dragging {
  cursor: grabbing;
}

.mermaid-container {
  position: relative;
  padding: var(--spacing-lg);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  transition: transform var(--transition-base);
  min-width: 100%;
  min-height: 100%;
}

.mermaid-container :deep(svg) {
  max-width: none;
  height: auto;
  display: block;
}

.mermaid-error {
  color: var(--color-danger);
  padding: var(--spacing-lg);
  text-align: center;
  font-size: var(--font-size-sm);
  background: var(--color-danger-light);
  border-radius: var(--radius-md);
  margin: var(--spacing-lg);
}
</style>
