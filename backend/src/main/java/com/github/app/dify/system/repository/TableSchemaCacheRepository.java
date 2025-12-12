package com.github.app.dify.system.repository;

import com.github.app.dify.system.domain.TableSchemaCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
/**
 * 表结构缓存Repository
 */
@Repository
public interface TableSchemaCacheRepository extends JpaRepository<TableSchemaCache, Long> {
    
    /**
     * 根据数据源ID查找所有表结构缓存
     */
    List<TableSchemaCache> findByDataSourceId(Long dataSourceId);
    
    /**
     * 根据数据源ID和表名查找表结构缓存
     */
    Optional<TableSchemaCache> findByDataSourceIdAndTableName(Long dataSourceId, String tableName);
    
    /**
     * 删除数据源的所有表结构缓存
     */
    void deleteByDataSourceId(Long dataSourceId);
    
    /**
     * 根据数据源ID和表名删除表结构缓存
     */
    void deleteByDataSourceIdAndTableName(Long dataSourceId, String tableName);
}