package com.github.app.dify.domain;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * 表结构缓存表
 * @TableName TABLE_SCHEMA_CACHE
 */
@Entity
@Table(name = "TABLE_SCHEMA_CACHE", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"data_source_id", "table_name"})
})
public class TableSchemaCache implements Serializable {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 数据源ID
     */
    @Column(name = "data_source_id", nullable = false)
    private Long dataSourceId;
    
    /**
     * 表名
     */
    @Column(name = "table_name", nullable = false, length = 255)
    private String tableName;
    
    /**
     * 表结构信息（JSON格式）
     */
    @Column(name = "schema_info", columnDefinition = "TEXT")
    private String schemaInfo;
    
    /**
     * 最后刷新时间
     */
    @Column(name = "last_refresh_time")
    private Date lastRefreshTime;
    
    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;
    
    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private Date updateTime;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSchemaInfo() {
        return schemaInfo;
    }

    public void setSchemaInfo(String schemaInfo) {
        this.schemaInfo = schemaInfo;
    }

    public Date getLastRefreshTime() {
        return lastRefreshTime;
    }

    public void setLastRefreshTime(Date lastRefreshTime) {
        this.lastRefreshTime = lastRefreshTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}

