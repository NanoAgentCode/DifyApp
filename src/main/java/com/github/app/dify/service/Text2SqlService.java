package com.github.app.dify.service;

import java.util.List;
/**
 * Text2SQL 服务接口
 * 使用 LangChain4j 实现自然语言到 SQL 的转换
 */
public interface Text2SqlService {
    
    /**
     * 执行 Text2SQL 查询
     * @param dataSourceId 数据源ID
     * @param question 用户问题
     * @param modelId 模型ID（可选，如果不指定则使用默认模型）
     * @param tableNames 表名列表（可选，如果指定则只使用这些表的结构）
     * @return 查询结果
     */
    Text2SqlResult executeQuery(Long dataSourceId, String question, Long modelId, List<String> tableNames);
    
    /**
     * Text2SQL 查询结果
     */
    class Text2SqlResult {
        private String sql;
        private List<String> columns;
        private List<java.util.Map<String, Object>> rows;
        private int rowCount;
        
        public String getSql() {
            return sql;
        }
        
        public void setSql(String sql) {
            this.sql = sql;
        }
        
        public List<String> getColumns() {
            return columns;
        }
        
        public void setColumns(List<String> columns) {
            this.columns = columns;
        }
        
        public List<java.util.Map<String, Object>> getRows() {
            return rows;
        }
        
        public void setRows(List<java.util.Map<String, Object>> rows) {
            this.rows = rows;
        }
        
        public int getRowCount() {
            return rowCount;
        }
        
        public void setRowCount(int rowCount) {
            this.rowCount = rowCount;
        }
    }
}