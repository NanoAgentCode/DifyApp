/**
 * 模型颜色工具函数
 * 根据模型ID生成稳定的背景颜色
 */

// 预定义的颜色列表（柔和的颜色）
const MODEL_COLORS = [
  { bg: '#e3f2fd', text: '#1976d2' }, // 蓝色
  { bg: '#f3e5f5', text: '#7b1fa2' }, // 紫色
  { bg: '#e8f5e9', text: '#388e3c' }, // 绿色
  { bg: '#fff3e0', text: '#f57c00' }, // 橙色
  { bg: '#fce4ec', text: '#c2185b' }, // 粉色
  { bg: '#e0f2f1', text: '#00796b' }, // 青色
  { bg: '#fff9c4', text: '#f9a825' }, // 黄色
  { bg: '#e1bee7', text: '#8e24aa' }, // 深紫色
  { bg: '#b2dfdb', text: '#00695c' }, // 深青色
  { bg: '#ffccbc', text: '#d84315' }, // 深橙色
  { bg: '#c5cae9', text: '#3f51b5' }, // 靛蓝色
  { bg: '#b39ddb', text: '#5e35b1' }, // 深紫色
  { bg: '#90caf9', text: '#1565c0' }, // 亮蓝色
  { bg: '#81c784', text: '#2e7d32' }, // 亮绿色
  { bg: '#ffb74d', text: '#e65100' }, // 亮橙色
  { bg: '#f48fb1', text: '#ad1457' }, // 亮粉色
]

/**
 * 简单的哈希函数，将字符串转换为数字
 */
function hashString(str) {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash // 转换为32位整数
  }
  return Math.abs(hash)
}

/**
 * 根据模型ID获取颜色
 * @param {number|string} modelId - 模型ID
 * @returns {Object} 包含背景色和文字色的对象
 */
export function getModelColor(modelId) {
  if (!modelId) {
    // 如果没有模型ID，返回默认颜色
    return { bg: '#f5f5f5', text: '#909399' }
  }
  
  const hash = hashString(String(modelId))
  const colorIndex = hash % MODEL_COLORS.length
  return MODEL_COLORS[colorIndex]
}

/**
 * 根据模型ID获取样式对象（用于内联样式）
 * @param {number|string} modelId - 模型ID
 * @returns {Object} 样式对象
 */
export function getModelStyle(modelId) {
  const color = getModelColor(modelId)
  return {
    backgroundColor: color.bg,
    color: color.text,
    borderColor: color.text
  }
}

/**
 * 描边标签样式（不填充、使用设计令牌，与 design-tokens 中 --tag-* 一致）
 * @param {number|string} modelId - 模型ID
 * @returns {Object} 样式对象
 */
export function getModelPlainStyle(modelId) {
  const { text } = getModelColor(modelId)
  return {
    backgroundColor: 'var(--tag-plain-bg)',
    color: text,
    border: `var(--tag-border-width) var(--tag-border-style) ${text}`
  }
}

