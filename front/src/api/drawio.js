import request from '@/utils/request'

/**
 * 通过自然语言生成图表
 * @param {string} prompt - 自然语言描述
 * @param {string} modelId - 模型ID（可选）
 * @param {string} diagramType - 图表类型（可选）
 * @returns {Promise} 返回图表JSON数据（X6格式）
 */
export function generateDiagram(prompt, modelId = null, diagramType = null) {
  return request({
    url: '/api/drawio/generate',
    method: 'post',
    data: {
      prompt,
      modelId,
      diagramType
    }
  })
}

/**
 * 修改现有图表
 * @param {string} diagramJson - 现有图表JSON（X6格式）
 * @param {string} prompt - 修改指令
 * @param {string} modelId - 模型ID（可选）
 * @returns {Promise} 返回修改后的图表JSON数据（X6格式）
 */
export function modifyDiagram(diagramJson, prompt, modelId = null) {
  return request({
    url: '/api/drawio/modify',
    method: 'post',
    data: {
      diagramJson,
      prompt,
      modelId
    }
  })
}

/**
 * 保存图表
 * @param {string} name - 图表名称
 * @param {string} diagramJson - 图表JSON数据（X6格式）
 * @param {string} diagramType - 图表类型（可选）
 * @returns {Promise}
 */
export function saveDiagram(name, diagramJson, diagramType = null) {
  return request({
    url: '/api/drawio/save',
    method: 'post',
    data: {
      name,
      diagramJson,
      diagramType
    }
  })
}

/**
 * 获取图表列表
 * @returns {Promise}
 */
export function getDiagramList() {
  return request({
    url: '/api/drawio/list',
    method: 'get'
  })
}

/**
 * 获取图表详情
 * @param {string} id - 图表ID
 * @returns {Promise}
 */
export function getDiagramDetail(id) {
  return request({
    url: `/api/drawio/${id}`,
    method: 'get'
  })
}

/**
 * 删除图表
 * @param {string} id - 图表ID
 * @returns {Promise}
 */
export function deleteDiagram(id) {
  return request({
    url: `/api/drawio/${id}`,
    method: 'delete'
  })
}

