import request from '@/utils/request'

export function getAdminSkillList() {
  return request({
    url: '/api/admin/skills',
    method: 'get'
  })
}

export function updateSkillConfig(skillKey, data) {
  return request({
    url: `/api/admin/skills/${skillKey}`,
    method: 'put',
    data
  })
}

export function syncSkills() {
  return request({
    url: '/api/admin/skills/sync',
    method: 'post'
  })
}

export function getAvailableSkills(forRole) {
  return request({
    url: '/api/skills/available',
    method: 'get',
    params: { forRole }
  })
}

export function deleteSkillConfig(skillKey) {
  return request({
    url: `/api/admin/skills/${skillKey}`,
    method: 'delete'
  })
}
