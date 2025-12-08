package com.github.app.dify.service;

import com.github.app.dify.domain.DataSource;
import com.github.app.dify.req.CreateDataSourceReq;
import com.github.app.dify.req.UpdateDataSourceReq;
import com.github.app.dify.resp.DataSourceResp;
import java.util.List;
/**
 * 数据源服务接口
 */
public interface DataSourceService {
    
    /**
     * 创建数据源
     * @param req 创建请求
     * @param userId 用户ID
     * @param username 用户名
     * @param role 用户角色（1-管理员，0-普通用户）
     * @param force 是否强制创建（忽略重复名称检查）
     */
    DataSourceResp createDataSource(CreateDataSourceReq req, Long userId, String username, Integer role, Boolean force);
    
    /**
     * 更新数据源
     */
    DataSourceResp updateDataSource(Long id, UpdateDataSourceReq req);
    
    /**
     * 删除数据源（软删除）
     */
    void deleteDataSource(Long id);
    
    /**
     * 根据ID获取数据源（返回实体）
     */
    DataSource getDataSourceEntityById(Long id);
    
    /**
     * 根据ID获取数据源（返回响应对象）
     */
    DataSourceResp getDataSourceById(Long id);
    
    /**
     * 获取数据源列表
     * @param tenantId 租户ID
     * @param status 状态
     * @param keyword 关键词
     * @param type 数据库类型
     * @param userId 用户ID（用于权限过滤，如果为null则不进行权限过滤）
     * @param userRole 用户角色（1-管理员，0-普通用户），如果为null则按普通用户处理
     */
    List<DataSourceResp> listDataSources(Integer tenantId, Integer status, String keyword, String type, Long userId, Integer userRole);
    
    /**
     * 测试数据源连接
     */
    boolean testConnection(Long id);
    
    /**
     * 测试数据源连接配置（用于创建/编辑时测试）
     */
    boolean testConnectionConfig(CreateDataSourceReq req);
}