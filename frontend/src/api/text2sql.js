import request from '@/utils/request'

/**
 * 执行 Text2SQL 查询
 * @param {Object} data 查询参数
 * @param {Number} data.dataSourceId 数据源ID
 * @param {String} data.question 用户问题
 * @param {Number} data.modelId 模型ID（可选）
 * @param {Array<String>} data.tableNames 表名列表（可选）
 */
export function executeText2SqlQuery(data) {
  return request({
    url: '/api/text2sql/query',
    method: 'post',
    data
  })
}

/**
 * 获取表列表
 * @param {Number} dataSourceId 数据源ID
 */
export function getTableList(dataSourceId) {
  return request({
    url: `/api/text2sql/${dataSourceId}/tables`,
    method: 'get'
  })
}

/**
 * 获取表结构
 * @param {Number} dataSourceId 数据源ID
 * @param {String} tableName 表名
 * @param {Boolean} forceRefresh 是否强制刷新
 */
export function getTableSchema(dataSourceId, tableName, forceRefresh = false) {
  return request({
    url: `/api/text2sql/${dataSourceId}/tables/${tableName}/schema`,
    method: 'get',
    params: { forceRefresh }
  })
}

