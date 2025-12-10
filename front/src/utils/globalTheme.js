import { industrialThemes, getThemeById, getThemeCSSVariables } from './themes'

/**
 * 全局主题色配置键
 */
export const GLOBAL_THEME_CONFIG_KEY = 'system.globalTheme'

/**
 * 应用全局主题色到文档根元素
 * @param {string} themeValue - 主题值，格式：themeId:primaryColor
 */
export function applyGlobalTheme(themeValue) {
  if (!themeValue) {
    // 如果没有配置，使用默认主题
    return
  }

  const root = document.documentElement
  let cssVariables = {}

  // 检查是否是主题格式 themeId:color
  if (themeValue.includes(':')) {
    const [themeId] = themeValue.split(':')
    const theme = getThemeById(themeId)
    if (theme) {
      cssVariables = getThemeCSSVariables(theme)
    }
  } else {
    // 尝试根据ID查找主题
    const theme = getThemeById(themeValue)
    if (theme) {
      cssVariables = getThemeCSSVariables(theme)
    } else {
      // 如果不是有效的主题ID，默认使用饿了么蓝
      const defaultTheme = getThemeById('element')
      if (defaultTheme) {
        cssVariables = getThemeCSSVariables(defaultTheme)
      }
    }
  }

  // 应用CSS变量到根元素
  Object.keys(cssVariables).forEach(key => {
    root.style.setProperty(key, cssVariables[key])
  })

  // 同时设置Element Plus的主色（如果存在主色）
  if (cssVariables['--theme-primary'] || cssVariables['--global-theme-primary']) {
    const primaryColor = cssVariables['--theme-primary'] || cssVariables['--global-theme-primary']
    root.style.setProperty('--el-color-primary', primaryColor)
    
    // 生成并设置Element Plus的主色变体
    setElementPlusPrimaryColor(primaryColor)
  }
}

/**
 * 将hex颜色转换为RGB值
 * @param {string} color - hex颜色值
 * @returns {string} RGB值字符串，格式：r, g, b
 */
function hexToRgb(color) {
  const hex = color.replace('#', '')
  const r = parseInt(hex.substring(0, 2), 16)
  const g = parseInt(hex.substring(2, 4), 16)
  const b = parseInt(hex.substring(4, 6), 16)
  return `${r}, ${g}, ${b}`
}

/**
 * 设置Element Plus的主色及其变体
 * @param {string} primaryColor - 主色值
 */
function setElementPlusPrimaryColor(primaryColor) {
  const root = document.documentElement
  
  // 设置基础主色
  root.style.setProperty('--el-color-primary', primaryColor)
  
  // 设置RGB值，用于rgba()函数（格式：r, g, b）
  const rgb = hexToRgb(primaryColor)
  root.style.setProperty('--el-color-primary-rgb', rgb)
  
  // 设置rgba版本（用于阴影等效果）
  root.style.setProperty('--el-color-primary-rgba-03', `rgba(${rgb}, 0.3)`)
  root.style.setProperty('--el-color-primary-rgba-04', `rgba(${rgb}, 0.4)`)
  root.style.setProperty('--el-color-primary-rgba-05', `rgba(${rgb}, 0.5)`)
  
  // 生成主色的浅色和深色变体（优化版 - 更柔和的渐变）
  // 使用更温和的变体生成算法，避免过于刺眼的颜色
  root.style.setProperty('--el-color-primary-light-3', lightenColor(primaryColor, 0.15))
  root.style.setProperty('--el-color-primary-light-5', lightenColor(primaryColor, 0.30))
  root.style.setProperty('--el-color-primary-light-7', lightenColor(primaryColor, 0.50))
  root.style.setProperty('--el-color-primary-light-8', lightenColor(primaryColor, 0.65))
  root.style.setProperty('--el-color-primary-light-9', lightenColor(primaryColor, 0.80))
  root.style.setProperty('--el-color-primary-dark-2', darkenColor(primaryColor, 0.15))
}

/**
 * 将颜色变亮（优化版 - 更柔和的变亮算法）
 * @param {string} color - 颜色值（hex格式）
 * @param {number} amount - 变亮程度（0-1）
 */
function lightenColor(color, amount) {
  const hex = color.replace('#', '')
  const r = parseInt(hex.substring(0, 2), 16)
  const g = parseInt(hex.substring(2, 4), 16)
  const b = parseInt(hex.substring(4, 6), 16)
  
  // 使用更柔和的变亮算法，避免颜色过于刺眼
  // 通过降低饱和度来使颜色更柔和
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const saturation = max === 0 ? 0 : (max - min) / max
  
  // 变亮时同时降低饱和度，使颜色更柔和
  const desaturateFactor = amount * 0.3 // 降低30%的饱和度
  const adjustedSaturation = Math.max(0, saturation - desaturateFactor)
  
  const newR = Math.min(255, Math.floor(r + (255 - r) * amount * (1 - adjustedSaturation * 0.2)))
  const newG = Math.min(255, Math.floor(g + (255 - g) * amount * (1 - adjustedSaturation * 0.2)))
  const newB = Math.min(255, Math.floor(b + (255 - b) * amount * (1 - adjustedSaturation * 0.2)))
  
  return `#${newR.toString(16).padStart(2, '0')}${newG.toString(16).padStart(2, '0')}${newB.toString(16).padStart(2, '0')}`
}

/**
 * 将颜色变暗（优化版 - 更柔和的变暗算法）
 * @param {string} color - 颜色值（hex格式）
 * @param {number} amount - 变暗程度（0-1）
 */
function darkenColor(color, amount) {
  const hex = color.replace('#', '')
  const r = parseInt(hex.substring(0, 2), 16)
  const g = parseInt(hex.substring(2, 4), 16)
  const b = parseInt(hex.substring(4, 6), 16)
  
  // 使用更柔和的变暗算法，保持颜色的自然感
  // 不完全按比例变暗，而是稍微保持亮度，使颜色更柔和
  const newR = Math.max(0, Math.floor(r * (1 - amount * 0.85)))
  const newG = Math.max(0, Math.floor(g * (1 - amount * 0.85)))
  const newB = Math.max(0, Math.floor(b * (1 - amount * 0.85)))
  
  return `#${newR.toString(16).padStart(2, '0')}${newG.toString(16).padStart(2, '0')}${newB.toString(16).padStart(2, '0')}`
}

/**
 * 从系统配置加载并应用全局主题色
 * @param {Function} getConfigValue - 获取配置值的函数
 */
export async function loadAndApplyGlobalTheme(getConfigValue) {
  try {
    const response = await getConfigValue(GLOBAL_THEME_CONFIG_KEY)
    if (response && response.configValue) {
      applyGlobalTheme(response.configValue)
    } else {
      // 如果没有配置，使用饿了么蓝（Element Plus默认蓝色）
      const defaultTheme = getThemeById('element')
      if (defaultTheme) {
        applyGlobalTheme(`element:${defaultTheme.colors.primary}`)
      } else {
        // 如果找不到主题，直接设置颜色
        const defaultColor = '#409EFF' // Element Plus 默认蓝色
        const root = document.documentElement
        root.style.setProperty('--el-color-primary', defaultColor)
        setElementPlusPrimaryColor(defaultColor)
      }
      console.log('未找到全局主题色配置，使用饿了么蓝默认主题')
    }
  } catch (error) {
    // 配置不存在或其他错误，使用默认主题
    console.log('加载全局主题色配置失败，使用默认主题:', error)
  }
}

