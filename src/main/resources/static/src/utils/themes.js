/**
 * 工业风主题预设
 */
export const industrialThemes = [
  {
    id: 'classic',
    name: '经典工业风',
    description: '深灰背景 + 橙色强调',
    colors: {
      primary: '#FF6B35',      // 橙色
      secondary: '#2C3E50',    // 深灰蓝
      background: '#34495E',   // 深灰
      surface: '#ECF0F1',      // 浅灰
      text: '#2C3E50',         // 深色文字
      accent: '#E67E22'        // 深橙色
    }
  },
  {
    id: 'modern',
    name: '现代工业风',
    description: '深蓝背景 + 青色强调',
    colors: {
      primary: '#00D4FF',      // 青色
      secondary: '#1A1F2E',    // 深蓝黑
      background: '#2C3E50',   // 深蓝灰
      surface: '#ECF0F1',      // 浅灰
      text: '#1A1F2E',         // 深色文字
      accent: '#0099CC'        // 深青色
    }
  },
  {
    id: 'metal',
    name: '金属工业风',
    description: '银灰背景 + 深蓝强调',
    colors: {
      primary: '#3498DB',      // 蓝色
      secondary: '#7F8C8D',    // 银灰
      background: '#95A5A6',   // 浅银灰
      surface: '#ECF0F1',      // 极浅灰
      text: '#2C3E50',         // 深色文字
      accent: '#2980B9'        // 深蓝色
    }
  },
  {
    id: 'dark',
    name: '暗黑工业风',
    description: '黑色背景 + 红色强调',
    colors: {
      primary: '#E74C3C',      // 红色
      secondary: '#1C1C1C',    // 深黑
      background: '#2C2C2C',   // 深灰黑
      surface: '#3C3C3C',      // 中灰黑
      text: '#ECF0F1',         // 浅色文字
      accent: '#C0392B'        // 深红色
    }
  },
  {
    id: 'steampunk',
    name: '蒸汽朋克',
    description: '棕色背景 + 金色强调',
    colors: {
      primary: '#F39C12',      // 金色
      secondary: '#5D4E37',    // 深棕
      background: '#8B7355',   // 中棕
      surface: '#D4C5A9',      // 浅棕
      text: '#2C2C2C',         // 深色文字
      accent: '#D68910'        // 深金色
    }
  },
  {
    id: 'cyber',
    name: '赛博工业风',
    description: '深紫背景 + 霓虹绿强调',
    colors: {
      primary: '#00FF88',      // 霓虹绿
      secondary: '#1A1A2E',    // 深紫黑
      background: '#16213E',   // 深紫蓝
      surface: '#0F3460',      // 中紫蓝
      text: '#E94560',         // 粉红文字
      accent: '#00CC6A'        // 深霓虹绿
    }
  }
]

/**
 * 获取主题CSS变量
 */
export function getThemeCSSVariables(theme) {
  if (!theme) return {}
  
  return {
    '--theme-primary': theme.colors.primary,
    '--theme-secondary': theme.colors.secondary,
    '--theme-background': theme.colors.background,
    '--theme-surface': theme.colors.surface,
    '--theme-text': theme.colors.text,
    '--theme-accent': theme.colors.accent
  }
}

/**
 * 根据主题ID获取主题
 */
export function getThemeById(themeId) {
  return industrialThemes.find(theme => theme.id === themeId) || null
}

/**
 * 根据颜色值查找主题
 */
export function findThemeByColor(color) {
  if (!color) return null
  return industrialThemes.find(theme => 
    theme.colors.primary.toLowerCase() === color.toLowerCase() ||
    theme.colors.secondary.toLowerCase() === color.toLowerCase()
  ) || null
}

