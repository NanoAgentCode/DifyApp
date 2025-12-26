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

// 将节点结构转换为jsMind格式
const convertToJsMindFormat = (data) => {
  try {
    // 如果已经是jsMind格式，直接返回
    if (data.meta && data.format && data.data) {
      return data
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
      
      return {
        meta: {
          name: data.name || '思维导图',
          author: '系统',
          version: '1.0'
        },
        format: 'node_tree',
        data: {
          id: 'root',
          topic: rootTopic,
          children: children
        }
      }
    }
    
    // 如果数据有data字段，尝试直接使用
    if (data.data) {
      return {
        meta: data.meta || {
          name: '思维导图',
          author: '系统',
          version: '1.0'
        },
        format: data.format || 'node_tree',
        data: data.data
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

// 初始化jsMind
const initJsMind = async () => {
  if (!mindmapContainer.value || !mindMapData.value) return
  
  await nextTick()
  
  try {
    // 等待容器可见
    await waitForElementVisible(mindmapContainer.value)
    
    // 再次等待一小段时间确保渲染完成
    await new Promise(resolve => setTimeout(resolve, 100))
    
    // 解析数据
    let data = mindMapData.value
    if (typeof data === 'string') {
      const jsonStr = extractJsonFromString(data)
      if (jsonStr) {
        data = JSON.parse(jsonStr)
      } else {
        throw new Error('无法解析脑图数据')
      }
    }
    
    // 转换为jsMind格式
    const jsmindData = convertToJsMindFormat(data)
    
    // 清理旧的mind实例
    if (mind && mindmapContainer.value) {
      try {
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
    
    mind = new jsMind(options)
    mind.show(jsmindData)
    
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
  }
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
    
    mindMapData.value = data || null
    generating.value = false // 先设置为false，让容器显示
    
    if (mindMapData.value) {
      ElMessage.success('脑图生成成功')
      // 等待DOM更新，确保容器可见
      await nextTick()
      await new Promise(resolve => setTimeout(resolve, 200))
      await initJsMind()
    } else {
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

// 监听mindMapData变化
watch(mindMapData, () => {
  if (mindMapData.value && !isEditing.value) {
    nextTick(() => {
      initJsMind()
    })
  }
})

onMounted(() => {
  if (props.docId) {
    loadMindMap()
  }
})

onUnmounted(() => {
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
