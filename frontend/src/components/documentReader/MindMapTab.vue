<template>
  <div class="mindmap-tab" :class="{ 'fullscreen-mode': isFullscreen }" ref="mindmapTabRef">
    <div class="tab-header">
      <div class="header-info">
        <el-icon><Connection /></el-icon>
        <span>脑图</span>
      </div>
      <div class="header-actions">
        <el-tooltip
          v-if="mindMapData"
          :content="isFullscreen ? '退出全屏' : '全屏'"
          placement="bottom"
        >
          <el-button
            type="primary"
            size="small"
            @click="toggleFullscreen"
            circle
          >
            <el-icon><FullScreen /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip
          :content="mindMapData ? '重新生成' : '生成脑图'"
          placement="bottom"
        >
          <el-button
            type="success"
            size="small"
            @click="handleGenerate"
            :loading="generating"
            :disabled="generating"
            circle
          >
            <el-icon><MagicStick /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>
    
    <div class="tab-content">
      <!-- 显示模式 -->
      <div class="display-mode">
        <div v-if="generating" class="loading-state">
          <el-icon class="loading-icon is-loading"><Loading /></el-icon>
          <p>正在生成脑图，请稍候...</p>
        </div>
        <div v-else-if="mindMapData" class="mindmap-container">
          <!-- HTML URL类型：使用iframe显示 -->
          <iframe 
            v-if="isHtmlUrlType(mindMapData)" 
            ref="mindmapIframe"
            :src="htmlUrl" 
            class="mindmap-iframe"
            frameborder="0"
            allowfullscreen
          ></iframe>
          <!-- jsMind格式：使用jsMind显示 -->
          <div v-else ref="mindmapContainer" class="jsmind-container"></div>
        </div>
        <div v-else class="empty-state">
          <el-icon class="empty-icon"><Document /></el-icon>
          <p>暂无脑图数据</p>
          <div class="empty-actions">
            <el-tooltip content="生成脑图" placement="top">
              <el-button type="success" size="small" @click="handleGenerate" :loading="generating" circle>
                <el-icon><MagicStick /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Document, MagicStick, Loading, FullScreen } from '@element-plus/icons-vue'
import { getDocumentMindMap, generateDocumentMindMap } from '@/api/documentReader'
import jsMind from 'jsmind'
import 'jsmind/style/jsmind.css'

const props = defineProps({
  docId: {
    type: Number,
    required: true
  }
})

const mindMapData = ref(null)
const htmlUrl = ref('')
const mindmapIframe = ref(null)
const mindmapTabRef = ref(null)
const isFullscreen = ref(false)

// 检查是否为HTML URL类型
const isHtmlUrlType = (data) => {
  if (!data) {
    console.log('isHtmlUrlType: 数据为空')
    return false
  }
  try {
    const parsed = typeof data === 'string' ? JSON.parse(data) : data
    const result = parsed && parsed.type === 'html_url' && parsed.url
    console.log('isHtmlUrlType: 检查结果', { 
      hasData: !!data, 
      isString: typeof data === 'string',
      parsed: parsed,
      type: parsed?.type,
      url: parsed?.url,
      result 
    })
    return result
  } catch (e) {
    console.warn('isHtmlUrlType: 解析失败', e)
    return false
  }
}

// 获取HTML URL（将后端URL替换为前端代理路径）
const getHtmlUrl = (data) => {
  if (!data) {
    console.log('getHtmlUrl: 数据为空')
    return ''
  }
  try {
    const parsed = typeof data === 'string' ? JSON.parse(data) : data
    let url = parsed.url || ''
    
    // 清理URL：去除首尾空白和引号
    url = url.trim()
    // 去除首尾的双引号或单引号
    if ((url.startsWith('"') && url.endsWith('"')) ||
        (url.startsWith("'") && url.endsWith("'"))) {
      url = url.substring(1, url.length - 1).trim()
    }
    
    // 将后端URL的前缀替换为前端代理路径
    // 例如: http://192.168.1.100:6066/html/xxx.html -> /proxy/html/xxx.html
    try {
      const urlObj = new URL(url)
      // 提取路径部分（如 /html/xxx.html）
      const path = urlObj.pathname
      // 如果路径以 /html/ 开头，替换为前端代理路径
      if (path.startsWith('/html/')) {
        url = `/proxy${path}`
        console.log('getHtmlUrl: URL已替换为前端代理路径', { 
          originalUrl: parsed.url, 
          replacedUrl: url,
          path 
        })
      } else {
        console.log('getHtmlUrl: URL路径不符合预期，保持原样', { originalUrl: parsed.url, path })
      }
    } catch (urlError) {
      // 如果URL解析失败，可能是相对路径，尝试直接处理
      if (url.startsWith('/html/')) {
        url = `/proxy${url}`
        console.log('getHtmlUrl: 相对路径已替换为前端代理路径', { originalUrl: parsed.url, replacedUrl: url })
      } else {
        console.warn('getHtmlUrl: URL解析失败，保持原样', { originalUrl: parsed.url, error: urlError })
      }
    }
    
    console.log('getHtmlUrl: 最终URL', { originalUrl: parsed.url, finalUrl: url, parsed })
    return url
  } catch (e) {
    console.warn('getHtmlUrl: 解析失败', e)
    return ''
  }
}
const generating = ref(false)
const mindmapContainer = ref(null)
let mind = null
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
      const rootTopic = data.rootTopic || ''
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
    
    // 默认格式（空脑图）
    return {
      meta: {
        name: '思维导图',
        author: '系统',
        version: '1.0'
      },
      format: 'node_tree',
      data: {
        id: 'root',
        topic: '',
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
  // 如果是HTML URL类型，不需要初始化jsMind
  if (isHtmlUrlType(mindMapData.value)) {
    return
  }
  
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
        editable: false,
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
    } catch (error) {
      console.error('初始化jsMind失败:', error)
      ElMessage.error('渲染脑图失败：' + (error.message || '未知错误'))
    } finally {
      initJsMindTimer = null
    }
  }, 100) // 100ms 防抖延迟
}

// 检查脑图数据是否有效
const isValidMindMapData = (data) => {
  if (!data) return false
  
  // 如果是字符串，检查是否为空或只包含空白
  if (typeof data === 'string') {
    const trimmed = data.trim()
    if (!trimmed || trimmed === '{}' || trimmed === '[]' || trimmed === 'null') {
      return false
    }
  }
  
  // 如果是对象，检查是否有实际内容
  if (typeof data === 'object') {
    // 检查是否是HTML URL类型
    if (data.type === 'html_url' && data.url) {
      return true
    }
    
    // 检查是否有data字段且不为空
    if (data.data) {
      // 检查data是否有topic或children
      if (data.data.topic || (data.data.children && data.data.children.length > 0)) {
        return true
      }
    }
    
    // 检查是否有nodes数组且不为空
    if (data.nodes && Array.isArray(data.nodes) && data.nodes.length > 0) {
      return true
    }
    
    // 如果只有meta和format但没有实际数据，视为无效
    if (data.meta && data.format && data.data) {
      const topic = data.data.topic || ''
      const children = data.data.children || []
      if (topic.trim() || children.length > 0) {
        return true
      }
    }
    
    return false
  }
  
  return true
}

// 加载脑图数据
const loadMindMap = async () => {
  try {
    const response = await getDocumentMindMap(props.docId)
    let data = response?.mindMapData || response || null
    
    if (data && typeof data === 'string') {
      // 尝试解析JSON字符串
      try {
        // 先尝试直接解析
        data = JSON.parse(data)
      } catch (e1) {
        // 如果直接解析失败，尝试提取JSON部分
        const jsonStr = extractJsonFromString(data)
        if (jsonStr) {
          try {
            data = JSON.parse(jsonStr)
          } catch (e2) {
            console.warn('解析脑图数据失败:', e2, '原始数据:', data)
            data = null
          }
        } else {
          console.warn('无法从字符串中提取JSON，原始数据:', data)
          data = null
        }
      }
    }
    
    // 检查数据是否有效
    if (isValidMindMapData(data)) {
      mindMapData.value = data
      
      // 如果是HTML URL类型，不需要初始化jsMind，直接显示iframe
      if (isHtmlUrlType(mindMapData.value)) {
        htmlUrl.value = getHtmlUrl(mindMapData.value)
        console.log('检测到HTML URL类型脑图，URL:', htmlUrl.value)
      } else {
        htmlUrl.value = ''
        // 只有非HTML URL类型才需要初始化jsMind
        await initJsMind()
      }
    } else {
      // 数据无效或为空，清空并自动生成
      console.log('脑图数据无效或为空，触发自动生成')
      mindMapData.value = null
      htmlUrl.value = ''
      // 首次打开时自动生成脑图
      await autoGenerateMindMap()
    }
  } catch (error) {
    console.error('加载脑图失败:', error)
    mindMapData.value = null
    htmlUrl.value = ''
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
      // 尝试解析JSON字符串
      try {
        // 先尝试直接解析
        data = JSON.parse(data)
      } catch (e1) {
        // 如果直接解析失败，尝试提取JSON部分
        const jsonStr = extractJsonFromString(data)
        if (jsonStr) {
          try {
            data = JSON.parse(jsonStr)
          } catch (e2) {
            console.warn('解析生成的脑图数据失败:', e2, '原始数据:', data)
            data = null
          }
        } else {
          console.warn('无法从字符串中提取JSON，原始数据:', data)
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
      
      // 如果是HTML URL类型，不需要初始化jsMind，直接显示iframe
      if (isHtmlUrlType(mindMapData.value)) {
        htmlUrl.value = getHtmlUrl(mindMapData.value)
        console.log('检测到HTML URL类型脑图，URL:', htmlUrl.value)
        ElMessage.success('脑图生成成功')
      } else {
        htmlUrl.value = ''
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
      }
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
        // 尝试解析JSON字符串
        try {
          // 先尝试直接解析
          data = JSON.parse(data)
        } catch (e1) {
          // 如果直接解析失败，尝试提取JSON部分
          const jsonStr = extractJsonFromString(data)
          if (jsonStr) {
            try {
              data = JSON.parse(jsonStr)
            } catch (e2) {
              console.warn('解析生成的脑图数据失败:', e2, '原始数据:', data)
              data = null
            }
          } else {
            console.warn('无法从字符串中提取JSON，原始数据:', data)
            data = null
          }
        }
      }
      
      mindMapData.value = data || null
      if (mindMapData.value) {
        // 如果是HTML URL类型，不需要初始化jsMind，直接显示iframe
        if (isHtmlUrlType(mindMapData.value)) {
          htmlUrl.value = getHtmlUrl(mindMapData.value)
          console.log('检测到HTML URL类型脑图，URL:', htmlUrl.value)
          ElMessage.success('脑图生成成功')
        } else {
          htmlUrl.value = ''
          await initJsMind()
          ElMessage.success('脑图生成成功')
        }
      } else {
        htmlUrl.value = ''
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
  
  // 如果是HTML URL类型，不需要初始化jsMind
  if (isHtmlUrlType(newVal)) {
    htmlUrl.value = getHtmlUrl(newVal)
    console.log('watch mindMapData: HTML URL类型，跳过jsMind初始化，URL:', htmlUrl.value)
    return
  } else {
    htmlUrl.value = ''
  }
  
  console.log('watch mindMapData: 触发初始化', { hasContainer: !!mindmapContainer.value })
  nextTick(() => {
    initJsMind()
  })
}, { deep: true })

// 切换全屏
const toggleFullscreen = async () => {
  if (!mindmapTabRef.value) return
  
  try {
    if (!isFullscreen.value) {
      // 进入全屏
      if (mindmapTabRef.value.requestFullscreen) {
        await mindmapTabRef.value.requestFullscreen()
      } else if (mindmapTabRef.value.webkitRequestFullscreen) {
        await mindmapTabRef.value.webkitRequestFullscreen()
      } else if (mindmapTabRef.value.mozRequestFullScreen) {
        await mindmapTabRef.value.mozRequestFullScreen()
      } else if (mindmapTabRef.value.msRequestFullscreen) {
        await mindmapTabRef.value.msRequestFullscreen()
      }
    } else {
      // 退出全屏
      if (document.exitFullscreen) {
        await document.exitFullscreen()
      } else if (document.webkitExitFullscreen) {
        await document.webkitExitFullscreen()
      } else if (document.mozCancelFullScreen) {
        await document.mozCancelFullScreen()
      } else if (document.msExitFullscreen) {
        await document.msExitFullscreen()
      }
    }
  } catch (error) {
    console.error('全屏操作失败:', error)
    ElMessage.error('全屏操作失败')
  }
}

// 监听全屏状态变化
const handleFullscreenChange = () => {
  isFullscreen.value = !!(
    document.fullscreenElement ||
    document.webkitFullscreenElement ||
    document.mozFullScreenElement ||
    document.msFullscreenElement
  )
}

onMounted(() => {
  if (props.docId) {
    loadMindMap()
  }
  
  // 监听全屏状态变化
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('webkitfullscreenchange', handleFullscreenChange)
  document.addEventListener('mozfullscreenchange', handleFullscreenChange)
  document.addEventListener('msfullscreenchange', handleFullscreenChange)
})

onUnmounted(() => {
  // 清理resize监听
  cleanupResizeObserver()
  
  // 清理mind实例
  if (mindmapContainer.value) {
    try {
      mindmapContainer.value.innerHTML = ''
    } catch (e) {
      console.warn('清空容器失败:', e)
    }
  }
  mind = null
  
  // 移除全屏状态监听
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', handleFullscreenChange)
  document.removeEventListener('mozfullscreenchange', handleFullscreenChange)
  document.removeEventListener('msfullscreenchange', handleFullscreenChange)
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

.tab-content {
  flex: 1;
  overflow: hidden;
  padding: 8px;
  display: flex;
  flex-direction: column;
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

.mindmap-iframe {
  width: 100%;
  height: 100%;
  border: none;
  min-height: 600px;
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

/* 全屏模式样式 */
.mindmap-tab.fullscreen-mode {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100vw;
  height: 100vh;
  z-index: 9999;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.mindmap-tab.fullscreen-mode .tab-content {
  flex: 1;
  padding: 16px;
  overflow: hidden;
}

.mindmap-tab.fullscreen-mode .mindmap-container {
  height: 100%;
  width: 100%;
}

.mindmap-tab.fullscreen-mode .mindmap-iframe {
  min-height: 100%;
}

.mindmap-tab.fullscreen-mode .jsmind-container {
  min-height: 100%;
}
</style>
