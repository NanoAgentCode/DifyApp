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

        <!-- 图表操作 -->
        <el-divider style="margin: 8px 0;" />
        <div class="diagram-management">
          <div class="section-title">图表操作</div>
          <div class="management-buttons">
            <el-button 
              @click="handleClear"
              :disabled="!hasDiagram"
              class="management-button"
            >
              <el-icon><Delete /></el-icon>
              清空画布
            </el-button>
            <el-button 
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
              :key="index"
              class="history-item"
            >
              <div class="history-prompt" @click="loadHistoryPrompt(item)">{{ item }}</div>
              <el-button
                type="danger"
                :icon="Delete"
                size="small"
                text
                circle
                @click.stop="deleteHistoryItem(index)"
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import mermaid from 'mermaid'
import {
  MagicStick,
  Edit,
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
  ArrowLeft,
} from '@element-plus/icons-vue'
import { 
  generateDiagram, 
  modifyDiagram,
  saveHistory,
  getHistoryList,
  deleteHistory
} from '@/api/drawio'

const router = useRouter()

// 返回主页
const handleBack = () => {
  router.push('/user/chat')
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


// Mermaid 初始化标志
let mermaidInitialized = false

// 初始化 Mermaid（复制管理端的实现）
const initMermaid = async () => {
  if (mermaidInitialized) {
    return
  }

  try {
    mermaid.initialize({
      startOnLoad: false,
      theme: 'default',
      securityLevel: 'loose',
      flowchart: {
        useMaxWidth: true,
        htmlLabels: true,
        curve: 'basis'
      },
      mindmap: {
        useMaxWidth: true,
        htmlLabels: true,
        padding: 10,
        maxNodeWidth: 200
      },
      themeVariables: {
        primaryColor: '#409eff',
        primaryTextColor: '#303133',
        primaryBorderColor: '#409eff',
        lineColor: '#606266',
        secondaryColor: '#ecf5ff',
        tertiaryColor: '#f5f7fa',
        // Mindmap 专用颜色变量 - 匹配 markmap 风格
        cScale0: '#409eff',         // 蓝色 - 主分支
        cScale1: '#FF9800',         // 橙色 - 分支1
        cScale2: '#4CAF50',         // 绿色 - 分支2
        cScale3: '#FF5252',         // 红色 - 分支3
        cScale4: '#9370DB',         // 紫色 - 分支4
        cScale5: '#FFD700',         // 黄色 - 分支5
        cScale6: '#1976D2',         // 深蓝色 - 分支6
        cScale7: '#808080',         // 灰色 - 分支7
        // 兼容旧的颜色变量
        lightBlue: '#409eff',
        yellow: '#FFD700',
        purple: '#9370DB',
        red: '#FF5252',
        green: '#4CAF50',
        orange: '#FF9800',
        darkBlue: '#1976D2',
        gray: '#808080'
      }
    })

    mermaidInitialized = true
    console.log('Mermaid 初始化完成')
  } catch (e) {
    console.error('Mermaid 初始化失败:', e)
  }
}

// 渲染 Mermaid 图表（简化版，复制管理端核心逻辑）
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

  if (!mermaidInitialized) {
    await initMermaid()
  }

  try {
    let codeToRender = mermaidCode.trim()

    // 对于架构图的处理（简化版）
    if (selectedDiagramType.value === 'architecture') {
      codeToRender = codeToRender.replace(/^(flowchart|graph)\s+(LR|TD|BT|RL)/i, '$1 TD')
      codeToRender = codeToRender.replace(/flowchart\s+LR/gi, 'flowchart TD')
      codeToRender = codeToRender.replace(/graph\s+LR/gi, 'graph TD')
      if (!codeToRender.match(/^(flowchart|graph)\s+(TD|LR|BT|RL)/i)) {
        codeToRender = codeToRender.replace(/^(flowchart|graph)/i, '$1 TD')
      }
    }

    // 对于思维导图的特殊处理，确保使用正确的mindmap语法
    if (selectedDiagramType.value === 'mindmap') {
      // 确保mindmap使用正确的语法格式
      if (!codeToRender.startsWith('mindmap')) {
        console.warn('检测到思维导图代码不以mindmap开头，尝试修复')
        if (codeToRender.includes('root((') || codeToRender.includes('root')) {
          codeToRender = 'mindmap\n' + codeToRender.replace(/^mindmap\s*\n?/, '').trim()
        }
      }

      // 清理可能存在的样式标记（mindmap不支持）
      codeToRender = codeToRender.replace(/:::[a-zA-Z0-9_-]+/g, '')
      codeToRender = codeToRender.replace(/classDef\s+[a-zA-Z0-9_-]+\s+.*$/gm, '')
      codeToRender = codeToRender.replace(/class\s+[a-zA-Z0-9_-]+\s+[a-zA-Z0-9_-]+/g, '')
    }
    
    const id = `mermaid-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    mermaidContainer.value.innerHTML = ''
    await nextTick()
    const { svg } = await mermaid.render(id, codeToRender)
    mermaidContainer.value.innerHTML = svg
    
    // 对于思维导图，应用额外的样式优化
    if (selectedDiagramType.value === 'mindmap') {
      await nextTick()
      const svgElement = mermaidContainer.value.querySelector('svg')
      if (svgElement) {
        // 添加 mindmap 类名以便应用样式
        svgElement.classList.add('mindmap-svg')
        
        // 优化连接线的平滑度
        const paths = svgElement.querySelectorAll('path')
        paths.forEach(path => {
          if (path.getAttribute('d') && path.getAttribute('d').includes('C')) {
            // 确保使用平滑曲线
            path.setAttribute('stroke-linecap', 'round')
            path.setAttribute('stroke-linejoin', 'round')
          }
        })
        
        // 优化节点圆圈
        const circles = svgElement.querySelectorAll('circle')
        circles.forEach(circle => {
          circle.setAttribute('stroke-width', '2')
        })
      }
    }
    
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

// 构建完整的 prompt
const buildFullPrompt = (userPrompt) => {
  const config = diagramTypeConfig[selectedDiagramType.value]
  if (config && selectedDiagramType.value !== 'custom') {
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
      const historyItem = `${diagramTypeConfig[selectedDiagramType.value]?.name || ''}: ${aiPrompt.value}`
      // 保存历史记录到数据库
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
  if (e.button !== 0) return
  if (e.target.tagName === 'A' || e.target.closest('a')) {
    return
  }
  
  isDragging.value = true
  dragStart.value = {
    x: e.clientX - panOffset.value.x,
    y: e.clientY - panOffset.value.y
  }
  lastPanOffset.value = { ...panOffset.value }
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
    
    let svgWidth = 0
    let svgHeight = 0
    
    if (svg.getBBox) {
      try {
        const bbox = svg.getBBox()
        svgWidth = bbox.width
        svgHeight = bbox.height
      } catch (e) {
        // getBBox 可能失败
      }
    }
    
    if (svgWidth === 0 || svgHeight === 0) {
      svgWidth = svg.clientWidth || svg.getAttribute('width') || svg.viewBox?.baseVal?.width || 0
      svgHeight = svg.clientHeight || svg.getAttribute('height') || svg.viewBox?.baseVal?.height || 0
    }
    
    if (svgWidth > 0 && svgHeight > 0) {
      const scaleX = (wrapperWidth - 40) / svgWidth
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

// 鼠标滚轮缩放
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


onMounted(async () => {
  // 加载历史记录列表
  await loadHistoryList()
  
  await nextTick()
  await initMermaid()
})
</script>

<style scoped>
.ai-drawio-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.full-height {
  height: 100%;
}

.toolbar-panel {
  background: #f5f7fa;
  border-right: 1px solid #e4e7ed;
  padding: 15px 20px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
}

.toolbar-header {
  margin-bottom: 15px;
  flex-shrink: 0;
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-top {
  display: flex;
  align-items: center;
}

.toolbar-header h3 {
  margin: 0;
  color: #303133;
  font-size: 18px;
}

.diagram-type-section {
  margin-bottom: 15px;
  margin-left: 0;
  margin-right: 0;
  flex-shrink: 0;
}

.diagram-type-select {
  width: 100%;
}

.diagram-type-select :deep(.el-input__wrapper) {
  margin: 0;
}

.diagram-type-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.diagram-type-option .el-icon {
  font-size: 16px;
  color: #409eff;
}

.model-section {
  margin-bottom: 15px;
  margin-left: 0;
  margin-right: 0;
  flex-shrink: 0;
}

.template-section {
  margin-bottom: 12px;
  margin-left: 0;
  margin-right: 0;
  flex-shrink: 0;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.template-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #e4e7ed;
  font-size: 12px;
  line-height: 1.4;
}

.template-item:hover {
  background: #ecf5ff;
  border-color: #409eff;
  transform: translateX(2px);
}

.template-item .el-icon {
  color: #409eff;
  font-size: 14px;
  flex-shrink: 0;
}

.ai-input-section {
  margin-bottom: 15px;
  margin-left: 0;
  margin-right: 0;
  flex-shrink: 0;
}

.prompt-input {
  margin-bottom: 10px;
}

.prompt-input :deep(.el-textarea__inner) {
  margin: 0;
}

.button-group {
  display: flex;
  gap: 10px;
  width: 100%;
  margin: 0;
}

.action-button {
  flex: 1;
  min-width: 0;
  height: 40px;
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

.section-title {
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
  font-size: 14px;
  flex-shrink: 0;
}

.history-section {
  margin-top: 8px;
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
  padding: 8px;
  margin-bottom: 8px;
  background: white;
  border-radius: 4px;
  transition: all 0.3s;
  border: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.history-item:hover {
  background: #ecf5ff;
  border-color: #409eff;
}

.history-delete-btn {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
}

.history-item:hover .history-delete-btn {
  opacity: 1;
}

.history-prompt {
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  flex: 1;
  cursor: pointer;
  min-width: 0;
}

.canvas-panel {
  padding: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.canvas-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.canvas-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.canvas-actions {
  display: flex;
  gap: 10px;
}

.mermaid-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e4e7ed;
  user-select: none;
}

.mermaid-wrapper.dragging {
  cursor: grabbing;
}

.mermaid-container {
  position: relative;
  padding: 20px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  transition: transform 0.2s ease;
  min-width: 100%;
  min-height: 100%;
}

.mermaid-container :deep(svg) {
  max-width: none;
  height: auto;
  display: block;
}

/* Mindmap 专用样式 - 匹配 markmap 风格 */
.mermaid-container :deep(.mindmap-node) {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* 思维导图节点样式 */
.mermaid-container :deep(.nodeLabel) {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

/* 思维导图根节点样式 */
.mermaid-container :deep(.root-node) {
  font-weight: 600;
  font-size: 16px;
}

/* 思维导图连接线样式 - 平滑曲线 */
.mermaid-container :deep(.mindmap path) {
  stroke-width: 2px;
  fill: none;
  transition: stroke-width 0.3s ease;
}

.mermaid-container :deep(.mindmap path:hover) {
  stroke-width: 3px;
}

/* 思维导图节点圆圈样式 */
.mermaid-container :deep(.mindmap circle) {
  stroke-width: 2px;
  transition: r 0.3s ease;
}

.mermaid-container :deep(.mindmap circle:hover) {
  r: 6;
}

/* 思维导图文本样式 */
.mermaid-container :deep(.mindmap text) {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 14px;
  fill: #303133;
}

/* 思维导图根节点文本 */
.mermaid-container :deep(.mindmap .root text) {
  font-weight: 600;
  font-size: 16px;
  fill: #409eff;
}

/* 思维导图 SVG 整体样式 */
.mermaid-container :deep(.mindmap-svg) {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* 思维导图节点文本容器 */
.mermaid-container :deep(.mindmap .nodeLabel) {
  background: transparent;
  border: none;
  padding: 4px 8px;
  border-radius: 4px;
}

/* 思维导图连接线 - 更平滑的曲线 */
.mermaid-container :deep(.mindmap-svg path) {
  stroke-width: 2.5px;
  fill: none;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.1));
}

/* 思维导图节点圆圈 - 更明显的视觉效果 */
.mermaid-container :deep(.mindmap-svg circle) {
  stroke-width: 2.5px;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.1));
}

/* 思维导图文本 - 更好的可读性 */
.mermaid-container :deep(.mindmap-svg text) {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 14px;
  font-weight: 500;
  fill: #303133;
  text-rendering: geometricPrecision;
}

/* 思维导图根节点特殊样式 */
.mermaid-container :deep(.mindmap-svg .root circle) {
  stroke-width: 3px;
  r: 8;
}

.mermaid-container :deep(.mindmap-svg .root text) {
  font-weight: 600;
  font-size: 16px;
  fill: #409eff;
}

.mermaid-error {
  color: #f56c6c;
  padding: 20px;
  text-align: center;
}
</style>

