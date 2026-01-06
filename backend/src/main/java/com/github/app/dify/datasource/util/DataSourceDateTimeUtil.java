package com.github.app.dify.datasource.util;

import com.github.app.dify.datasource.domain.DataSource;
import com.github.app.dify.datasource.domain.TableSchemaCache;

import java.util.Date;

/**
 * 数据源日期时间工具类
 * 提供统一的日期时间处理方法
 */
public class DataSourceDateTimeUtil {
    
    /**
     * 获取当前时间
     * 
     * @return 当前时间
     */
    public static Date now() {
        return new Date();
    }
    
    /**
     * 设置数据源的创建时间和更新时间
     * 适用于新建数据源
     * 
     * @param dataSource 数据源实体
     */
    public static void setCreateAndUpdateTime(DataSource dataSource) {
        Date now = now();
        dataSource.setCreateTime(now);
        dataSource.setUpdateTime(now);
    }
    
    /**
     * 设置数据源的更新时间
     * 适用于更新数据源
     * 
     * @param dataSource 数据源实体
     */
    public static void setUpdateTime(DataSource dataSource) {
        dataSource.setUpdateTime(now());
    }
    
    /**
     * 设置表结构缓存的创建时间和更新时间
     * 适用于新建缓存
     * 
     * @param cache 表结构缓存实体
     */
    public static void setCreateAndUpdateTime(TableSchemaCache cache) {
        Date now = now();
        cache.setCreateTime(now);
        cache.setUpdateTime(now);
    }
    
    /**
     * 设置表结构缓存的更新时间
     * 适用于更新缓存
     * 
     * @param cache 表结构缓存实体
     */
    public static void setUpdateTime(TableSchemaCache cache) {
        cache.setUpdateTime(now());
    }
}

