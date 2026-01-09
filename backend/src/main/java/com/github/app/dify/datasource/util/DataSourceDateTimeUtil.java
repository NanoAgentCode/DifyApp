package com.github.app.dify.datasource.util;

import com.github.app.dify.common.util.DateTimeUtil;
import com.github.app.dify.datasource.domain.DataSource;

import java.util.Date;

/**
 * 数据源日期时间工具类
 * 提供数据源相关实体的日期时间处理方法
 */
public class DataSourceDateTimeUtil {
    
    /**
     * 设置数据源的创建时间和更新时间
     * 适用于新建数据源
     * 
     * @param dataSource 数据源实体
     */
    public static void setCreateAndUpdateTime(DataSource dataSource) {
        Date now = DateTimeUtil.now();
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
        dataSource.setUpdateTime(DateTimeUtil.now());
    }
}
