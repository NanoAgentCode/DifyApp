/**
 * 内置应用图标配置
 * 提供20个常用的应用图标供用户选择
 */

export const builtInIcons = [
  {
    id: 'chat',
    name: '聊天',
    icon: 'ChatDotRound',
    description: '适用于聊天类应用'
  },
  {
    id: 'workflow',
    name: '工作流',
    icon: 'Operation',
    description: '适用于工作流应用'
  },
  {
    id: 'robot',
    name: '机器人',
    icon: 'Robot',
    description: 'AI助手类应用'
  },
  {
    id: 'document',
    name: '文档',
    icon: 'Document',
    description: '文档处理类应用'
  },
  {
    id: 'message',
    name: '消息',
    icon: 'Message',
    description: '消息通知类应用'
  },
  {
    id: 'setting',
    name: '设置',
    icon: 'Setting',
    description: '设置配置类应用'
  },
  {
    id: 'data-analysis',
    name: '数据分析',
    icon: 'DataAnalysis',
    description: '数据分析类应用'
  },
  {
    id: 'star',
    name: '收藏',
    icon: 'Star',
    description: '收藏推荐类应用'
  },
  {
    id: 'lightning',
    name: '闪电',
    icon: 'Lightning',
    description: '快速响应类应用'
  },
  {
    id: 'magic-stick',
    name: '魔法棒',
    icon: 'MagicStick',
    description: '智能处理类应用'
  },
  {
    id: 'trophy',
    name: '奖杯',
    icon: 'Trophy',
    description: '成就奖励类应用'
  },
  {
    id: 'medal',
    name: '勋章',
    icon: 'Medal',
    description: '荣誉认证类应用'
  },
  {
    id: 'briefcase',
    name: '公文包',
    icon: 'Briefcase',
    description: '商务办公类应用'
  },
  {
    id: 'folder',
    name: '文件夹',
    icon: 'Folder',
    description: '文件管理类应用'
  },
  {
    id: 'notebook',
    name: '笔记本',
    icon: 'Notebook',
    description: '笔记记录类应用'
  },
  {
    id: 'calendar',
    name: '日历',
    icon: 'Calendar',
    description: '日程管理类应用'
  },
  {
    id: 'clock',
    name: '时钟',
    icon: 'Clock',
    description: '时间管理类应用'
  },
  {
    id: 'user',
    name: '用户',
    icon: 'User',
    description: '用户管理类应用'
  },
  {
    id: 'team',
    name: '团队',
    icon: 'UserFilled',
    description: '团队协作类应用'
  },
  {
    id: 'connection',
    name: '连接',
    icon: 'Connection',
    description: '连接集成类应用'
  }
]

/**
 * 根据图标ID获取图标配置
 */
export const getIconById = (iconId) => {
  return builtInIcons.find(icon => icon.id === iconId)
}

/**
 * 根据图标名称获取图标配置
 */
export const getIconByName = (iconName) => {
  return builtInIcons.find(icon => icon.icon === iconName)
}

/**
 * 获取所有图标
 */
export const getAllIcons = () => {
  return builtInIcons
}

/**
 * 解析图标值，返回图标信息
 * @param {string} iconValue - 图标值，可能是 "icon:iconId" 或 URL
 * @returns {object} 返回 { type: 'builtin'|'url', icon: 图标组件名或URL, id: 图标ID }
 */
export const parseIconValue = (iconValue) => {
  if (!iconValue) {
    return { type: null, icon: null, id: null }
  }
  
  if (iconValue.startsWith('icon:')) {
    const iconId = iconValue.substring(5)
    const iconConfig = getIconById(iconId)
    if (iconConfig) {
      return {
        type: 'builtin',
        icon: iconConfig.icon,
        id: iconId,
        name: iconConfig.name
      }
    }
  }
  
  return {
    type: 'url',
    icon: iconValue,
    id: null
  }
}

