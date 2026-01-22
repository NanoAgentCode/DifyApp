<template>
  <div class="ai-drawio-container">
    <el-container class="full-height">
      <!-- 左侧工具栏 -->
      <el-aside width="300px" class="toolbar-panel">
        <div class="toolbar-header">
          <h3>智能框图助手</h3>
          <div class="header-top">
            <el-button link @click="handleBack" size="small">
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
        
        <!-- AntV Infographic 图表容器 -->
        <div 
          class="infographic-wrapper"
          :class="{ dragging: isDragging }"
          ref="infographicWrapper" 
          @wheel="handleWheel"
          @mousedown="handleMouseDown"
          @mousemove="handleMouseMove"
          @mouseup="handleMouseUp"
          @mouseleave="handleMouseUp"
        >
          <div 
            class="infographic-container" 
            ref="infographicContainer"
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

// 动态导入 AntV Infographic，只在访问 AIDrawIO 页面时才加载
let Infographic = null
let infographicModule = null
let availableTemplateList = [] // 存储可用模板列表

const loadInfographic = async () => {
  if (!Infographic) {
    try {
      const module = await import('@antv/infographic')
      infographicModule = module
      
      console.log('=== @antv/infographic 模块加载成功 ===')
      console.log('模块导出:', Object.keys(module))
      
      // 获取 Infographic 类
      Infographic = module.Infographic || 
                    module.default?.Infographic || 
                    module.default ||
                    (module.default && typeof module.default === 'function' ? module.default : null)
      
      if (!Infographic) {
        console.error('模块导出内容:', module)
        throw new Error('无法从 @antv/infographic 加载 Infographic 类')
      }
      
      // 获取可用模板列表
      if (module.getTemplates && typeof module.getTemplates === 'function') {
        try {
          const templates = module.getTemplates()
          availableTemplateList = templates || []
          console.log('=== 可用模板列表 ===')
          console.log('模板总数:', availableTemplateList.length)
          
          // 按类型分组显示模板
          const templatesByType = {}
          availableTemplateList.forEach(t => {
            const type = t.split('-')[0] // 获取模板类型前缀
            if (!templatesByType[type]) {
              templatesByType[type] = []
            }
            templatesByType[type].push(t)
          })
          
          console.log('按类型分组的模板:')
          Object.keys(templatesByType).forEach(type => {
            console.log(`  ${type} (${templatesByType[type].length}个):`, templatesByType[type].slice(0, 5))
          })
          
          // 显示前30个模板名称
          console.log('前30个模板名称:', availableTemplateList.slice(0, 30))
        } catch (e) {
          console.warn('获取模板列表失败:', e)
        }
      } else {
        console.log('getTemplates 方法不可用，尝试其他方式...')
        // 尝试从 Infographic 类获取
        if (Infographic.getTemplates) {
          try {
            availableTemplateList = Infographic.getTemplates() || []
            console.log('从 Infographic 类获取模板列表:', availableTemplateList.length)
          } catch (e) {
            console.warn('从 Infographic 类获取模板失败:', e)
          }
        }
      }
      
      console.log('Infographic 类加载完成')
      
    } catch (error) {
      console.error('加载 @antv/infographic 失败:', error)
      throw new Error(`加载图表库失败: ${error.message || '未知错误'}`)
    }
  }
  return Infographic
}
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

// AntV Infographic 图表相关
const infographicContainer = ref(null)
const infographicWrapper = ref(null)
let infographicInstance = null

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
    placeholder: '描述思维导图主题和分支，例如：项目管理，包含需求分析（子分支：用户调研、需求文档）、设计（子分支：架构设计、UI设计）、开发、测试、部署等分支',
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

// AntV Infographic 初始化标志
let infographicInitialized = false

// 初始化 AntV Infographic
const initInfographic = async () => {
  if (infographicInitialized) {
    return
  }

  // 等待容器元素存在
  await nextTick()
  
  // 使用 ref 获取容器元素（官方推荐方式）
  const container = infographicContainer.value
  if (!container) {
    console.warn('容器元素不存在，等待下次调用')
    return
  }

  try {
    const InfographicClass = await loadInfographic()
    if (!InfographicClass) {
      throw new Error('无法加载 Infographic 类')
    }
    
    // 清空容器
    container.innerHTML = ''
    
    // 确保容器有尺寸
    const containerWidth = container.clientWidth || container.offsetWidth || 800
    const containerHeight = container.clientHeight || container.offsetHeight || 600
    
    console.log('初始化 AntV Infographic，容器尺寸:', containerWidth, 'x', containerHeight)
    
    // Vue 3 官方推荐：直接传入 DOM 元素（ref.value）
    infographicInstance = new InfographicClass({
      container: container,
      width: containerWidth > 0 ? containerWidth : 800,
      height: containerHeight > 0 ? containerHeight : 600,
      editable: false
    })
    infographicInitialized = true
    console.log('AntV Infographic 初始化完成')
    
  } catch (e) {
    console.error('AntV Infographic 初始化失败:', e)
    ElMessage.error('初始化图表引擎失败: ' + (e.message || '未知错误'))
    infographicInitialized = false
    infographicInstance = null
  }
}

// 渲染 AntV Infographic 图表
const renderInfographic = async (infographicCode) => {
  const container = infographicContainer.value
  if (!container) {
    ElMessage.warning('容器未初始化')
    return
  }

  if (!infographicCode || !infographicCode.trim()) {
    if (infographicInstance) {
      infographicInstance.destroy?.()
      infographicInstance = null
    }
    container.innerHTML = ''
    hasDiagram.value = false
    infographicInitialized = false
    return
  }

  // 等待 DOM 更新
  await nextTick()

  // 确保 AntV Infographic 已初始化
  if (!infographicInitialized || !infographicInstance) {
    await initInfographic()
  }

  await nextTick()

  try {
    // 清理代码
    let codeToRender = infographicCode.trim()
    codeToRender = codeToRender.replace(/[\u200B-\u200D\uFEFF]/g, '')
    codeToRender = normalizeInfographicTemplate(codeToRender)
    codeToRender = normalizeInfographicText(codeToRender)
    
    // 确保以 infographic 开头
    if (!codeToRender.startsWith('infographic')) {
      const idx = codeToRender.indexOf('infographic')
      if (idx >= 0) {
        codeToRender = codeToRender.substring(idx).trim()
      }
    }
    
    console.log('=== 开始渲染 AntV Infographic ===')
    console.log('模板名:', codeToRender.split('\n')[0])
    console.log('代码长度:', codeToRender.length)
    
    if (!infographicInstance) {
      throw new Error('Infographic 实例未初始化')
    }

    // 清空容器
    container.innerHTML = ''
    
    // 调用 render 方法
    infographicInstance.render(codeToRender)
    
    // 等待渲染完成
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 500))
    
    // 检查渲染结果
    const hasContent = container.children.length > 0 || 
                       container.innerHTML.trim().length > 0 ||
                       container.querySelector('svg') !== null
    
    console.log('渲染结果:', {
      hasContent,
      childrenCount: container.children.length,
      htmlLength: container.innerHTML.length,
      hasSvg: !!container.querySelector('svg'),
      instanceRendered: infographicInstance.rendered,
      instanceNode: !!infographicInstance.node
    })
    
    if (!hasContent) {
      console.warn('渲染后容器为空')
      console.warn('完整代码:', codeToRender)
      
      // 显示代码供调试
      container.innerHTML = `<div class="infographic-error">
        <p style="color: #e74c3c; font-weight: bold;">图表渲染失败</p>
        <p style="color: #666; margin-top: 8px;">可能原因：模板名称不正确或语法错误</p>
        <pre style="margin-top: 10px; font-size: 11px; color: #333; max-height: 400px; overflow: auto; background: #f8f8f8; padding: 12px; border-radius: 4px; white-space: pre-wrap; word-break: break-all;">${codeToRender}</pre>
      </div>`
      
      ElMessage.warning('图表渲染失败，请检查模板名称是否正确')
    } else {
      ElMessage.success('图表渲染成功')
    }
    
    currentDiagramJson.value = codeToRender
    hasDiagram.value = true
    
  } catch (e) {
    console.error('渲染失败:', e)
    ElMessage.error('渲染图表失败: ' + (e.message || '未知错误'))
    
    if (container) {
      container.innerHTML = `<div class="infographic-error">
        <p style="color: #e74c3c;">渲染出错: ${e.message}</p>
        <pre style="margin-top: 10px; font-size: 11px; max-height: 200px; overflow: auto; background: #f8f8f8; padding: 10px;">${infographicCode.substring(0, 500)}</pre>
      </div>`
    }
  }
}

const normalizeInfographicTemplate = (code) => {
  const lines = code.split(/\r?\n/)
  if (lines.length === 0) return code
  const firstLine = lines[0].trim()
  if (!firstLine.startsWith('infographic ')) return code

  const templateName = firstLine.slice('infographic '.length).trim()
  const templateMap = {
    'list-row-simple-vertical-arrow': 'list-column-simple-vertical-arrow',
    'list-column-simple': 'list-column-simple-vertical-arrow',
    'compare-binary-vertical-simple': 'compare-binary-horizontal-simple-fold',
    'chart-bar-horizontal-simple': 'chart-bar-plain-text',
    'relation-dagre-flow': 'relation-dagre-flow-lr-badge-card',
    'relation-dagre-flow-lr-simple-circle-node': 'relation-dagre-flow-lr-badge-card',
    'relation-dagre-flow-tb-simple-circle-node': 'relation-dagre-flow-tb-badge-card'
  }
  const mapped = templateMap[templateName]
  if (!mapped) return code

  lines[0] = `infographic ${mapped}`
  return lines.join('\n')
}

const normalizeInfographicText = (code) => {
  if (!code) return code
  return code.replace(/\\n/g, ' / ').replace(/\\t/g, ' ')
}

// 加载 AntV Infographic 代码
const loadInfographicCode = (infographicCode) => {
  if (typeof infographicCode === 'string') {
    renderInfographic(infographicCode)
  } else {
    ElMessage.warning('无效的图表数据格式')
  }
}

// 导出 AntV Infographic 代码
const exportInfographicCode = () => {
  return currentDiagramJson.value || ''
}

// 清空图表
const clearInfographic = () => {
  if (infographicInstance) {
    infographicInstance.destroy?.()
    infographicInstance = null
  }
  if (infographicContainer.value) {
    infographicContainer.value.innerHTML = ''
  }
  currentDiagramJson.value = ''
  hasDiagram.value = false
  infographicInitialized = false
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
      loadInfographicCode(response.diagramJson)
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
      loadInfographicCode(response.diagramJson)
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
    clearInfographic()
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
  if (!infographicContainer.value || !infographicWrapper.value) {
    return
  }
  
  const svg = infographicContainer.value.querySelector('svg')
  if (!svg) {
    resetZoom()
    return
  }
  
  try {
    const wrapperWidth = infographicWrapper.value.clientWidth
    const wrapperHeight = infographicWrapper.value.clientHeight
    
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
  const code = exportInfographicCode()
  if (!code) {
    ElMessage.warning('没有可导出的图表')
    return
  }
  
  const blob = new Blob([code], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `diagram-${Date.now()}.infographic`
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
  input.accept = '.infographic,.jsx,.txt'
  input.onchange = (e) => {
    const file = e.target.files[0]
    if (!file) return
    
    const reader = new FileReader()
    reader.onload = (event) => {
      try {
        const code = event.target.result
        loadInfographicCode(code)
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
  await initInfographic()
})

onUnmounted(() => {
  if (infographicInstance) {
    infographicInstance.destroy?.()
    infographicInstance = null
  }
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

/* ========== AntV Infographic图表容器 ========== */
.infographic-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border-lighter);
  user-select: none;
  box-shadow: var(--shadow-sm);
}

.infographic-wrapper.dragging {
  cursor: grabbing;
}

.infographic-container {
  /* 重要：不使用 flex 布局，让 @antv/infographic 自己控制内容 */
  position: relative;
  width: 100%;
  height: 100%;
  min-width: 800px;
  min-height: 600px;
  padding: var(--spacing-lg);
  transition: transform var(--transition-base);
  /* 确保容器是块级元素 */
  display: block;
}

.infographic-container :deep(svg) {
  max-width: none;
  height: auto;
  display: block;
}

/* @antv/infographic 渲染的内容样式 */
.infographic-container :deep(> div) {
  width: 100%;
  height: 100%;
}

.infographic-error {
  color: var(--color-danger);
  padding: var(--spacing-lg);
  text-align: center;
  font-size: var(--font-size-sm);
  background: var(--color-danger-light);
  border-radius: var(--radius-md);
  margin: var(--spacing-lg);
}
</style>
