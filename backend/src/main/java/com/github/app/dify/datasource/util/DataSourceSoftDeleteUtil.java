package com.github.app.dify.datasource.util;

import com.github.app.dify.datasource.domain.DataSource;
import org.springframework.data.repository.CrudRepository;

/**
 * 数据源软删除工具类
 * 提供统一的软删除操作方法
 */
public class DataSourceSoftDeleteUtil {
    
    /**
     * 软删除数据源（设置 deleted = 1 和 updateTime）
     * 
     * @param dataSource 数据源实体
     * @param repository 数据源仓库
     */
    public static void softDelete(DataSource dataSource, CrudRepository<DataSource, Long> repository) {
        dataSource.setDeleted(1);
        DataSourceDateTimeUtil.setUpdateTime(dataSource);
        repository.save(dataSource);
    }
    
    /**
     * 恢复软删除的数据源（设置 deleted = 0 和 updateTime）
     * 
     * @param dataSource 数据源实体
     * @param repository 数据源仓库
     */
    public static void restore(DataSource dataSource, CrudRepository<DataSource, Long> repository) {
        dataSource.setDeleted(0);
        DataSourceDateTimeUtil.setUpdateTime(dataSource);
        repository.save(dataSource);
    }
    
    /**
     * 检查数据源是否已删除
     * 
     * @param dataSource 数据源实体
     * @return true 如果已删除，false 如果未删除
     */
    public static boolean isDeleted(DataSource dataSource) {
        return dataSource.getDeleted() != null && dataSource.getDeleted() == 1;
    }
}

