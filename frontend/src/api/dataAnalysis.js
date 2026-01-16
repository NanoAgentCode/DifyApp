import request from '@/utils/request'

export function getDataAnalysisSettings() {
  return request({
    url: '/api/admin/data-analysis/settings',
    method: 'get'
  })
}

export function updateDataAnalysisSettings(data) {
  return request({
    url: '/api/admin/data-analysis/settings',
    method: 'put',
    data
  })
}

export function getDataAnalysisStatus() {
  return request({
    url: '/api/admin/data-analysis/status',
    method: 'get'
  })
}

export function runDataAnalysis() {
  return request({
    url: '/api/admin/data-analysis/run',
    method: 'post'
  })
}

export function getDataAnalysisGraph(params) {
  return request({
    url: '/api/admin/data-analysis/graph',
    method: 'get',
    params
  })
}
