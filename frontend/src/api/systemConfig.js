import request from '@/utils/request'

/**
 * 根据配置键获取配置值
 */
export function getConfigValue(configKey) {
  return request({
    url: `/api/system-config/value/${configKey}`,
    method: 'get'
  })
}

/**
 * 根据配置键获取配置（完整信息）
 */
export function getConfigByKey(configKey) {
  return request({
    url: `/api/system-config/${configKey}`,
    method: 'get'
  })
}

/**
 * 根据配置分组获取配置列表
 */
export function getConfigsByGroup(configGroup) {
  return request({
    url: `/api/system-config/group/${configGroup}`,
    method: 'get'
  })
}

/**
 * 获取所有配置
 */
export function getAllConfigs() {
  return request({
    url: '/api/system-config',
    method: 'get'
  })
}

/**
 * 分页获取配置
 */
export function getConfigPage(params) {
  return request({
    url: '/api/system-config/page',
    method: 'get',
    params
  })
}

/**
 * 设置或更新配置
 */
export function setOrUpdateConfig(data) {
  return request({
    url: '/api/system-config',
    method: 'post',
    data
  })
}

/**
 * 删除配置
 */
export function deleteConfig(configKey) {
  return request({
    url: `/api/system-config/${configKey}`,
    method: 'delete'
  })
}
