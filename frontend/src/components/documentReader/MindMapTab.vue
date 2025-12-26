<template>
  <div class="mindmap-tab">
    <div class="tab-header">
      <div class="header-info">
        <el-icon><Connection /></el-icon>
        <span>脑图</span>
      </div>
      <div v-if="!isEditing" class="header-actions">
        <el-button
          type="success"
          size="small"
          @click="handleGenerate"
          :loading="generating"
          :disabled="generating"
        >
          <el-icon><MagicStick /></el-icon>
          {{ mindMapData ? '重新生成' : '生成脑图' }}
        </el-button>
        <el-button
          type="primary"
          size="small"
          @click="handleEdit"
          :disabled="generating || !mindMapData"
        >
          <el-icon><Edit /></el-icon>
          编辑脑图
        </el-button>
      </div>
      <div v-else class="edit-actions">
        <el-button size="small" @click="handleCancel">取消</el-button>
        <el-button type="primary" size="small" @click="handleSave" :loading="saving">
          保存
        </el-button>
      </div>
    </div>
    
    <div class="tab-content">
      <!-- 编辑模式 -->
      <div v-if="isEditing" class="edit-mode">
        <el-input
          v-model="editData"
          type="textarea"
          :rows="15"
          placeholder="请输入脑图数据（jsMind JSON格式）..."
          class="edit-input"
        />
        <div class="edit-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>脑图数据格式为jsMind JSON格式，包含meta、format和data字段</span>
        </div>
      </div>
      
      <!-- 显示模式 -->
      <div v-else class="display-mode">
        <div v-if="generating" class="loading-state">
          <el-icon class="loading-icon is-loading"><Loading /></el-icon>
          <p>正在生成脑图，请稍候...</p>
        </div>
        <div v-else-if="mindMapData" class="mindmap-container">
          <div ref="mindmapContainer" class="jsmind-container"></div>
        </div>
        <div v-else class="empty-state">
          <el-icon class="empty-icon"><Document /></el-icon>
          <p>暂无脑图数据</p>
          <div class="empty-actions">
            <el-button type="success" size="small" @click="handleGenerate" :loading="generating">
              <el-icon><MagicStick /></el-icon>
              生成脑图
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Edit, Document, InfoFilled, MagicStick, Loading } from '@element-plus/icons-vue'
import { getDocumentMindMap, saveDocumentMindMap, generateDocumentMindMap } from '@/api/documentReader'
import jsMind from 'jsmind'
import 'jsmind/style/jsmind.css'

const props = defineProps({
  docId: {
    type: Number,
    required: true
  }
})

const mindMapData = ref(null)
const isEditing = ref(false)
const editData = ref('')
const saving = ref(false)
const generating = ref(false)
const mindmapContainer = ref(null)
let mind = null
let doubleClickHandler = null
let resizeObserver = null
let resizeTimer = null

// 从字符串中提取JSON部分
const extractJsonFromString = (str) => {
  if (!str || typeof str !== 'string') return null
  
  const trimmed = str.trim()
  
  // 如果整个字符串就是JSON
  if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
    try {
      JSON.parse(trimmed)
      return trimmed
    } catch (e) {
      // 不是有效JSON，继续处理
    }
  }
  
  // 尝试提取代码块中的JSON
  const jsonBlockMatch = trimmed.match(/```(?:json)?\s*(\{[\s\S]*?\})\s*```/)
  if (jsonBlockMatch && jsonBlockMatch[1]) {
    try {
      JSON.parse(jsonBlockMatch[1])
      return jsonBlockMatch[1]
    } catch (e) {
      // 不是有效JSON
    }
  }
  
  // 尝试提取第一个完整的JSON对象
  const firstBrace = trimmed.indexOf('{')
  if (firstBrace !== -1) {
    let braceCount = 0
    let lastBrace = -1
    for (let i = firstBrace; i < trimmed.length; i++) {
      const c = trimmed[i]
      if (c === '{') braceCount++
      else if (c === '}') {
        braceCount--
        if (braceCount === 0) {
          lastBrace = i
          break
        }
      }
    }
    
    if (lastBrace !== -1) {
      const jsonContent = trimmed.substring(firstBrace, lastBrace + 1).trim()
      try {
        JSON.parse(jsonContent)
        return jsonContent
      } catch (e) {
        // 不是有效JSON
      }
    }
  }
  
  return null
}

// 限制节点层级不超过3层（中心主题为第0层）
const limitNodeDepth = (node, currentDepth = 0, maxDepth = 3) => {
  if (currentDepth >= maxDepth) {
    // 超过最大深度，移除子节点
    return {
      ...node,
      children: []
    }
  }
  
  if (node.children && Array.isArray(node.children)) {
    return {
      ...node,
      children: node.children.map(child => limitNodeDepth(child, currentDepth + 1, maxDepth))
    }
  }
  
  return node
}

// 将节点结构转换为jsMind格式
const convertToJsMindFormat = (data) => {
  try {
    // 如果已经是jsMind格式，验证并限制层级
    if (data.meta && data.format && data.data) {
      // 限制层级不超过3层
      const limitedData = {
        ...data,
        data: limitNodeDepth(data.data, 0, 3)
      }
      return limitedData
    }
    
    // 如果是节点结构格式，转换为jsMind格式
    if (data.nodes && Array.isArray(data.nodes)) {
      const rootTopic = data.rootTopic || '中心主题'
      const children = []
      
      data.nodes.forEach((node, index) => {
        const child = {
          id: node.id || `node${index}`,
          topic: node.label || node.name || node.topic || `节点${index + 1}`,
          children: []
        }
        
        if (node.children && Array.isArray(node.children)) {
          node.children.forEach((subNode, subIndex) => {
            child.children.push({
              id: subNode.id || `${child.id}-${subIndex}`,
              topic: subNode.label || subNode.name || subNode.topic || `子节点${subIndex + 1}`,
              children: []
            })
          })
        }
        
        children.push(child)
      })
      
      const result = {
        meta: {
          name: data.name || '思维导图',
          author: '系统',
          version: '1.0'
        },
        format: 'node_tree',
        data: {
          id: 'root',
          topic: rootTopic,
          children: children.map(child => limitNodeDepth(child, 1, 3))
        }
      }
      return result
    }
    
    // 如果数据有data字段，尝试直接使用，并限制层级
    if (data.data) {
      return {
        meta: data.meta || {
          name: '思维导图',
          author: '系统',
          version: '1.0'
        },
        format: data.format || 'node_tree',
        data: limitNodeDepth(data.data, 0, 3)
      }
    }
    
    // 默认格式
    return {
      meta: {
        name: '思维导图',
        author: '系统',
        version: '1.0'
      },
      format: 'node_tree',
      data: {
        id: 'root',
        topic: '中心主题',
        children: []
      }
    }
  } catch (error) {
    console.error('转换jsMind格式失败:', error)
    throw error
  }
}

// 检查元素是否可见
const isElementVisible = (element) => {
  if (!element) return false
  const style = window.getComputedStyle(element)
  if (style.display === 'none') return false
  
  // 检查父元素
  let parent = element.parentElement
  while (parent && parent !== document.body) {
    const parentStyle = window.getComputedStyle(parent)
    if (parentStyle.display === 'none') return false
    parent = parent.parentElement
  }
  
  return true
}

// 等待元素可见
const waitForElementVisible = (element, timeout = 5000) => {
  return new Promise((resolve, reject) => {
    if (!element) {
      reject(new Error('Element is null'))
      return
    }
    
    if (isElementVisible(element)) {
      resolve(true)
      return
    }
    
    const startTime = Date.now()
    const checkInterval = setInterval(() => {
      if (isElementVisible(element)) {
        clearInterval(checkInterval)
        resolve(true)
      } else if (Date.now() - startTime > timeout) {
        clearInterval(checkInterval)
        reject(new Error('Timeout waiting for element to be visible'))
      }
    }, 100)
  })
}

// 应用自适应缩放，使思维导图完整显示在容器内
const applyAutoFit = () => {
  if (!mind || !mindmapContainer.value) return
  
  try {
    // 获取容器的实际尺寸
    const containerRect = mindmapContainer.value.getBoundingClientRect()
    const containerWidth = containerRect.width
    const containerHeight = containerRect.height
    
    if (containerWidth === 0 || containerHeight === 0) {
      console.warn('容器尺寸为0，跳过自适应缩放')
      return
    }
    
    // 获取jsMind的视图对象
    const view = mind.view
    if (!view) {
      console.warn('jsMind view对象不存在')
      return
    }
    
    // 获取思维导图的边界框
    // jsMind可能使用canvas或svg，需要找到实际的渲染元素
    const jsmindElement = mindmapContainer.value.querySelector('.jsmind-inner') || 
                         mindmapContainer.value.querySelector('svg') ||
                         mindmapContainer.value.querySelector('canvas') ||
                         mindmapContainer.value
    
    if (!jsmindElement) {
      console.warn('未找到jsMind渲染元素')
      return
    }
    
    // 获取思维导图的实际尺寸
    // 尝试多种方式获取尺寸
    let mindMapWidth = 0
    let mindMapHeight = 0
    
    // 方法1: 从jsMind的view对象获取
    if (view.get_bounding_box) {
      const bbox = view.get_bounding_box()
      if (bbox) {
        mindMapWidth = bbox.width || bbox.w || 0
        mindMapHeight = bbox.height || bbox.h || 0
      }
    }
    
    // 方法2: 从DOM元素获取
    if (mindMapWidth === 0 || mindMapHeight === 0) {
      const jsmindRect = jsmindElement.getBoundingClientRect()
      mindMapWidth = jsmindRect.width || jsmindElement.offsetWidth || 0
      mindMapHeight = jsmindRect.height || jsmindElement.offsetHeight || 0
    }
    
    // 方法3: 从scrollWidth/scrollHeight获取
    if (mindMapWidth === 0 || mindMapHeight === 0) {
      mindMapWidth = jsmindElement.scrollWidth || 0
      mindMapHeight = jsmindElement.scrollHeight || 0
    }
    
    if (mindMapWidth === 0 || mindMapHeight === 0) {
      console.warn('无法获取思维导图尺寸，跳过自适应缩放')
      return
    }
    
    // 计算缩放比例（留出一些边距）
    const padding = 40 // 边距
    const scaleX = (containerWidth - padding) / mindMapWidth
    const scaleY = (containerHeight - padding) / mindMapHeight
    const scale = Math.min(scaleX, scaleY, 1) // 不超过1，不放大
    
    // 如果思维导图比容器小，不需要缩放
    if (scale >= 1) {
      // 重置缩放，居中显示
      if (view.reset) {
        view.reset()
      } else if (view.set_zoom) {
        view.set_zoom(1)
      }
      return
    }
    
    // 应用缩放
    if (view.set_zoom) {
      view.set_zoom(scale)
      console.log('应用自适应缩放:', { scale, containerWidth, containerHeight, mindMapWidth, mindMapHeight })
    } else if (view.scale) {
      view.scale(scale)
    } else {
      // 如果API不可用，尝试直接操作DOM
      const transform = `scale(${scale})`
      if (jsmindElement.style) {
        jsmindElement.style.transform = transform
        jsmindElement.style.transformOrigin = 'center center'
      }
    }
    
    // 居中显示
    if (view.center_root) {
      view.center_root()
    } else if (view.focus_node) {
      const rootNode = mind.get_node('root')
      if (rootNode) {
        view.focus_node(rootNode.id)
      }
    }
    
  } catch (error) {
    console.warn('应用自适应缩放失败:', error)
  }
}

// 窗口resize事件处理器
let windowResizeHandler = null

// 设置窗口和容器大小变化监听
const setupResizeObserver = () => {
  if (!mindmapContainer.value) return
  
  // 清理旧的observer
  cleanupResizeObserver()
  
  // 使用ResizeObserver监听容器大小变化
  if (window.ResizeObserver) {
    resizeObserver = new ResizeObserver((entries) => {
      // 防抖处理
      if (resizeTimer) {
        clearTimeout(resizeTimer)
      }
      resizeTimer = setTimeout(() => {
        if (mind && mindmapContainer.value) {
          applyAutoFit()
        }
      }, 300)
    })
    
    resizeObserver.observe(mindmapContainer.value)
  }
  
  // 同时监听窗口大小变化（作为后备）
  windowResizeHandler = () => {
    if (resizeTimer) {
      clearTimeout(resizeTimer)
    }
    resizeTimer = setTimeout(() => {
      if (mind && mindmapContainer.value) {
        applyAutoFit()
      }
    }, 300)
  }
  
  window.addEventListener('resize', windowResizeHandler)
}

// 清理resize监听
const cleanupResizeObserver = () => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (resizeTimer) {
    clearTimeout(resizeTimer)
    resizeTimer = null
  }
  if (windowResizeHandler) {
    window.removeEventListener('resize', windowResizeHandler)
    windowResizeHandler = null
  }
}

// 初始化jsMind（添加防抖机制）
let initJsMindTimer = null
const initJsMind = async () => {
  // 防抖：如果正在初始化，取消之前的调用
  if (initJsMindTimer) {
    clearTimeout(initJsMindTimer)
  }
  
  initJsMindTimer = setTimeout(async () => {
    try {
      // 检查基本条件
      if (!mindmapContainer.value || !mindMapData.value) {
        console.warn('初始化jsMind：容器或数据为空', {
          hasContainer: !!mindmapContainer.value,
          hasData: !!mindMapData.value
        })
        return
      }
      
      // 等待DOM更新
      await nextTick()
      
      // 等待容器可见（最多等待5秒）
      try {
        await waitForElementVisible(mindmapContainer.value, 5000)
      } catch (e) {
        console.warn('等待容器可见超时，继续尝试初始化:', e.message)
        // 即使超时也继续，因为容器可能已经存在只是不可见
      }
      
      // 再次等待一小段时间确保渲染完成
      await new Promise(resolve => setTimeout(resolve, 300))
      
      // 再次检查容器是否存在且可见
      if (!mindmapContainer.value) {
        console.warn('初始化jsMind：容器不存在')
        return
      }
      
      const containerStyle = window.getComputedStyle(mindmapContainer.value)
      if (containerStyle.display === 'none') {
        console.warn('初始化jsMind：容器仍然不可见，延迟重试')
        // 延迟重试
        setTimeout(() => initJsMind(), 500)
        return
      }
      
      // 解析数据
      let data = mindMapData.value
      if (typeof data === 'string') {
        const jsonStr = extractJsonFromString(data)
        if (jsonStr) {
          try {
            data = JSON.parse(jsonStr)
          } catch (e) {
            console.error('解析脑图数据失败:', e)
            throw new Error('无法解析脑图数据: ' + e.message)
          }
        } else {
          throw new Error('无法从字符串中提取JSON')
        }
      }
      
      // 转换为jsMind格式
      const jsmindData = convertToJsMindFormat(data)
      
      // 清理旧的mind实例
      if (mind && mindmapContainer.value) {
        try {
          // 清理resize监听
          cleanupResizeObserver()
          // 移除事件监听器
          if (doubleClickHandler) {
            mindmapContainer.value.removeEventListener('dblclick', doubleClickHandler)
            doubleClickHandler = null
          }
          // 清空容器内容
          mindmapContainer.value.innerHTML = ''
        } catch (e) {
          console.warn('清理旧mind实例失败:', e)
        }
      }
      
      // 创建新的mind实例
      const options = {
        container: mindmapContainer.value,
        theme: 'primary',
        mode: 'full',
        editable: true,
        support_html: true
      }
      
      console.log('创建jsMind实例，容器:', mindmapContainer.value, '数据:', jsmindData)
      mind = new jsMind(options)
      mind.show(jsmindData)
      console.log('jsMind实例创建成功')
      
      // 等待jsMind渲染完成后，应用自适应缩放
      await nextTick()
      await new Promise(resolve => setTimeout(resolve, 200))
      
      // 应用自适应缩放
      applyAutoFit()
      
      // 设置窗口大小变化监听
      setupResizeObserver()
      
      // 添加编辑功能
      if (mind && mindmapContainer.value) {
        // 双击节点编辑
        doubleClickHandler = (e) => {
          const target = e.target
          if (target.classList.contains('jmnode')) {
            const nodeId = target.getAttribute('nodeid')
            if (nodeId && mind) {
              const node = mind.get_node(nodeId)
              if (node) {
                const newTopic = prompt('请输入新内容:', node.topic)
                if (newTopic !== null && newTopic !== node.topic) {
                  mind.update_node(nodeId, newTopic)
                  // 自动保存
                  saveMindMapData()
                }
              }
            }
          }
        }
        mindmapContainer.value.addEventListener('dblclick', doubleClickHandler)
      }
    } catch (error) {
      console.error('初始化jsMind失败:', error)
      ElMessage.error('渲染脑图失败：' + (error.message || '未知错误'))
    } finally {
      initJsMindTimer = null
    }
  }, 100) // 100ms 防抖延迟
}

// 保存脑图数据
const saveMindMapData = async () => {
  if (!mind) return
  
  try {
    const jsmindData = mind.get_data()
    await saveDocumentMindMap(props.docId, jsmindData)
    mindMapData.value = jsmindData
  } catch (error) {
    console.error('自动保存脑图失败:', error)
  }
}

// 加载脑图数据
const loadMindMap = async () => {
  try {
    const response = await getDocumentMindMap(props.docId)
    let data = response?.mindMapData || response || null
    
    if (data && typeof data === 'string') {
      const jsonStr = extractJsonFromString(data)
      if (jsonStr) {
        try {
          data = JSON.parse(jsonStr)
        } catch (e) {
          console.warn('解析脑图数据失败:', e)
          data = null
        }
      }
    }
    
    mindMapData.value = data
    
    if (mindMapData.value) {
      await initJsMind()
    } else {
      // 如果没有脑图数据，自动生成
      await autoGenerateMindMap()
    }
  } catch (error) {
    console.error('加载脑图失败:', error)
    mindMapData.value = null
    // 如果加载失败，也尝试自动生成
    await autoGenerateMindMap()
  }
}

// 自动生成脑图（无确认框）
const autoGenerateMindMap = async () => {
  if (generating.value) return
  
  generating.value = true
  try {
    const response = await generateDocumentMindMap(props.docId)
    let data = response?.mindMapData || response || null
    
    if (data && typeof data === 'string') {
      const jsonStr = extractJsonFromString(data)
      if (jsonStr) {
        try {
          data = JSON.parse(jsonStr)
        } catch (e) {
          console.warn('解析生成的脑图数据失败:', e)
          data = null
        }
      }
    }
    
    // 先设置数据，但保持generating为true，避免watch触发
    mindMapData.value = data || null
    
    if (mindMapData.value) {
      // 先设置为false，让容器显示
      generating.value = false
      
      // 等待DOM更新（多次nextTick确保完全更新）
      await nextTick()
      await nextTick()
      
      // 等待容器渲染完成
      await new Promise(resolve => setTimeout(resolve, 300))
      
      // 确保容器存在且可见后再初始化
      if (mindmapContainer.value) {
        console.log('自动生成后准备初始化jsMind，容器:', mindmapContainer.value)
        await initJsMind()
      } else {
        console.warn('自动生成后容器不存在，等待watch触发')
        // 如果容器还不存在，watch会处理
      }
      
      ElMessage.success('脑图生成成功')
    } else {
      generating.value = false
      ElMessage.warning('脑图生成完成，但内容为空')
    }
  } catch (error) {
    console.error('自动生成脑图失败:', error)
    generating.value = false
    ElMessage.error('自动生成脑图失败：' + (error.message || '未知错误'))
  }
}

// 手动生成脑图（带确认框）
const handleGenerate = async () => {
  try {
    await ElMessageBox.confirm(
      '将使用大模型自动生成文档脑图，可能需要一些时间，是否继续？',
      '生成脑图',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }
    )
    
    generating.value = true
    try {
      const response = await generateDocumentMindMap(props.docId)
      let data = response?.mindMapData || response || null
      
      if (data && typeof data === 'string') {
        const jsonStr = extractJsonFromString(data)
        if (jsonStr) {
          try {
            data = JSON.parse(jsonStr)
          } catch (e) {
            console.warn('解析生成的脑图数据失败:', e)
            data = null
          }
        }
      }
      
      mindMapData.value = data || null
      if (mindMapData.value) {
        ElMessage.success('脑图生成成功')
        await initJsMind()
      } else {
        ElMessage.warning('脑图生成完成，但内容为空')
      }
    } catch (error) {
      ElMessage.error('生成脑图失败：' + (error.message || '未知错误'))
    } finally {
      generating.value = false
    }
  } catch (error) {
    // 用户取消
    if (error !== 'cancel') {
      console.error('生成脑图失败:', error)
    }
  }
}

// 编辑
const handleEdit = () => {
  editData.value = typeof mindMapData.value === 'string' 
    ? mindMapData.value 
    : JSON.stringify(mindMapData.value || {}, null, 2)
  isEditing.value = true
}

// 取消编辑
const handleCancel = () => {
  isEditing.value = false
  editData.value = ''
}

// 保存
const handleSave = async () => {
  saving.value = true
  try {
    // 验证JSON格式
    let parsedData
    try {
      parsedData = JSON.parse(editData.value)
    } catch (e) {
      ElMessage.error('JSON格式错误，请检查输入')
      return
    }
    
    // 转换为jsMind格式
    const jsmindData = convertToJsMindFormat(parsedData)
    
    await saveDocumentMindMap(props.docId, jsmindData)
    mindMapData.value = jsmindData
    isEditing.value = false
    ElMessage.success('保存成功')
    await initJsMind()
  } catch (error) {
    ElMessage.error('保存失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 监听docId变化
watch(() => props.docId, () => {
  if (props.docId) {
    loadMindMap()
  }
}, { immediate: true })

// 监听mindMapData变化（仅在非生成状态下触发，避免与autoGenerateMindMap冲突）
watch(mindMapData, (newVal, oldVal) => {
  // 如果正在生成，不触发watch（由autoGenerateMindMap自己处理）
  if (generating.value) {
    console.log('watch mindMapData: 正在生成中，跳过')
    return
  }
  
  // 如果正在编辑，不触发
  if (isEditing.value) {
    console.log('watch mindMapData: 正在编辑中，跳过')
    return
  }
  
  // 如果数据为空，不触发
  if (!newVal) {
    console.log('watch mindMapData: 数据为空，跳过')
    return
  }
  
  // 如果数据没有变化，不触发
  if (newVal === oldVal) {
    console.log('watch mindMapData: 数据未变化，跳过')
    return
  }
  
  console.log('watch mindMapData: 触发初始化', { hasContainer: !!mindmapContainer.value })
  nextTick(() => {
    initJsMind()
  })
}, { deep: true })

onMounted(() => {
  if (props.docId) {
    loadMindMap()
  }
})

onUnmounted(() => {
  // 清理resize监听
  cleanupResizeObserver()
  
  // 清理mind实例
  if (mindmapContainer.value && doubleClickHandler) {
    try {
      mindmapContainer.value.removeEventListener('dblclick', doubleClickHandler)
    } catch (e) {
      console.warn('移除事件监听器失败:', e)
    }
  }
  if (mindmapContainer.value) {
    try {
      mindmapContainer.value.innerHTML = ''
    } catch (e) {
      console.warn('清空容器失败:', e)
    }
  }
  mind = null
  doubleClickHandler = null
})
</script>

<style scoped>
.mindmap-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tab-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
}

.edit-actions {
  display: flex;
  gap: 8px;
}

.tab-content {
  flex: 1;
  overflow: hidden;
  padding: 8px;
  display: flex;
  flex-direction: column;
}

.edit-mode {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.edit-input {
  width: 100%;
  flex: 1;
}

.edit-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 12px;
  background: #f0f9ff;
  border-radius: 4px;
  color: #409eff;
  font-size: 14px;
}

.display-mode {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.mindmap-container {
  width: 100%;
  flex: 1;
  background: white;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  position: relative;
}

.jsmind-container {
  width: 100%;
  height: 100%;
  min-height: 400px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: #909399;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  margin: 8px 0 16px;
}

.empty-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  color: #909399;
}

.loading-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.loading-icon.is-loading {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.loading-state p {
  margin: 8px 0;
  font-size: 14px;
}
</style>
