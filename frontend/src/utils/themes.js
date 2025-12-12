/**
 * 工业风主题预设（优化版 - 柔和配色）
 */
export const industrialThemes = [
  {
    id: 'element',
    name: '饿了么蓝',
    description: '默认蓝色（推荐）',
    colors: {
      primary: '#409EFF',      // 默认蓝色
      secondary: '#337ECC',    // 深蓝色
      background: '#F5F7FA',   // 浅灰背景
      surface: '#FFFFFF',      // 白色表面
      text: '#303133',         // 深色文字
      accent: '#66B1FF'        // 浅蓝色
    }
  },
  {
    id: 'jd',
    name: '京东红',
    description: '京东红色主题',
    colors: {
      primary: '#E1251B',      // 京东红
      secondary: '#C41E17',    // 深红色
      background: '#F5F7FA',   // 浅灰背景
      surface: '#FFFFFF',      // 白色表面
      text: '#303133',         // 深色文字
      accent: '#F56C6C'        // 浅红色
    }
  },
  {
    id: 'classic',
    name: '经典工业风',
    description: '深灰背景 + 柔和橙色',
    colors: {
      primary: '#D97757',      // 柔和的橙棕色
      secondary: '#2C3E50',    // 深灰蓝
      background: '#34495E',   // 深灰
      surface: '#ECF0F1',      // 浅灰
      text: '#2C3E50',         // 深色文字
      accent: '#B85C38'        // 深橙棕色
    }
  },
  {
    id: 'modern',
    name: '现代工业风',
    description: '深蓝背景 + 柔和青色',
    colors: {
      primary: '#5B9BD5',      // 柔和的蓝色
      secondary: '#1A1F2E',    // 深蓝黑
      background: '#2C3E50',   // 深蓝灰
      surface: '#ECF0F1',      // 浅灰
      text: '#1A1F2E',         // 深色文字
      accent: '#4A7BA7'        // 深蓝色
    }
  },
  {
    id: 'metal',
    name: '金属工业风',
    description: '银灰背景 + 柔和蓝',
    colors: {
      primary: '#5B8DB8',      // 柔和的钢蓝色
      secondary: '#7F8C8D',    // 银灰
      background: '#95A5A6',   // 浅银灰
      surface: '#ECF0F1',      // 极浅灰
      text: '#2C3E50',         // 深色文字
      accent: '#4A6FA5'        // 深钢蓝色
    }
  },
  {
    id: 'dark',
    name: '暗黑工业风',
    description: '黑色背景 + 柔和红色',
    colors: {
      primary: '#C85A5A',      // 柔和的砖红色
      secondary: '#1C1C1C',    // 深黑
      background: '#2C2C2C',   // 深灰黑
      surface: '#3C3C3C',      // 中灰黑
      text: '#ECF0F1',         // 浅色文字
      accent: '#A84A4A'        // 深砖红色
    }
  },
  {
    id: 'steampunk',
    name: '蒸汽朋克',
    description: '棕色背景 + 柔和金色',
    colors: {
      primary: '#D4A574',      // 柔和的古铜色
      secondary: '#5D4E37',    // 深棕
      background: '#8B7355',   // 中棕
      surface: '#D4C5A9',      // 浅棕
      text: '#2C2C2C',         // 深色文字
      accent: '#B8945F'        // 深古铜色
    }
  },
  {
    id: 'cyber',
    name: '赛博工业风',
    description: '深紫背景 + 柔和青绿',
    colors: {
      primary: '#5FB3A1',      // 柔和的青绿色
      secondary: '#1A1A2E',    // 深紫黑
      background: '#16213E',   // 深紫蓝
      surface: '#0F3460',      // 中紫蓝
      text: '#E0E0E0',         // 浅灰文字
      accent: '#4A9B8A'        // 深青绿色
    }
  },
  {
    id: 'warm',
    name: '温暖橙',
    description: '温暖的橙色主题',
    colors: {
      primary: '#D97757',      // 柔和的橙棕色
      secondary: '#B85C38',    // 深橙棕色
      background: '#F5F7FA',   // 浅灰背景
      surface: '#FFFFFF',      // 白色表面
      text: '#2C3E50',         // 深色文字
      accent: '#C85A5A'        // 强调色
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

