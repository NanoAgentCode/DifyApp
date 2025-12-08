package com.github.app.dify.service.impl;

import com.github.app.dify.domain.DataSource;
import com.github.app.dify.repository.DataSourceRepository;
import com.github.app.dify.req.CreateDataSourceReq;
import com.github.app.dify.req.UpdateDataSourceReq;
import com.github.app.dify.resp.DataSourceResp;
import com.github.app.dify.service.DataSourceService;
import com.github.app.dify.service.DatabaseConnectionService;
import com.github.app.dify.service.UserDataSourceVisibilityService;
import com.github.app.dify.util.PasswordEncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
/**
 * 数据源服务实现
 */
@Service
public class DataSourceServiceImpl implements DataSourceService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceServiceImpl.class);
    
    @Autowired
    private DataSourceRepository dataSourceRepository;
    
    @Autowired
    private PasswordEncryptionUtil passwordEncryptionUtil;
    
    @Autowired
    private DatabaseConnectionService connectionService;
    
    @Autowired
    private UserDataSourceVisibilityService userDataSourceVisibilityService;
    
    @Override
    @Transactional
    public DataSourceResp createDataSource(CreateDataSourceReq req, Long userId, String username, Integer role, Boolean force) {
        // 检查是否存在相同名称的数据源（除非强制创建）
        if (force == null || !force) {
            List<DataSource> existing = dataSourceRepository.findByNameAndNotDeleted(req.getName());
            if (!existing.isEmpty()) {
                throw new RuntimeException("DUPLICATE_NAME:已存在名称为 \"" + req.getName() + "\" 的数据源，是否继续创建？");
            }
        }
        
        // 验证数据库类型
        try {
            com.github.app.dify.util.DatabaseDriverManager.DatabaseType.fromString(req.getType());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("不支持的数据库类型: " + req.getType());
        }
        
        DataSource dataSource = new DataSource();
        BeanUtils.copyProperties(req, dataSource);
        
        // 加密密码
        if (dataSource.getPassword() != null && !dataSource.getPassword().isEmpty()) {
            dataSource.setPassword(passwordEncryptionUtil.encrypt(dataSource.getPassword()));
        }
        
        // 设置创建者信息
        dataSource.setCreator(username);
        dataSource.setCreatorId(userId);
        
        // 设置可见性：普通用户只能创建私有数据源，管理员可以创建公开或私有数据源
        if (role == null || role != 1) {
            dataSource.setIsPublic(false);
            logger.info("普通用户创建数据源，强制设置为私有 - 用户ID: {}, 名称: {}", userId, dataSource.getName());
        } else {
            if (dataSource.getIsPublic() == null) {
                dataSource.setIsPublic(false);
            }
            logger.info("管理员创建数据源 - 用户ID: {}, 名称: {}, 是否公开: {}", userId, dataSource.getName(), dataSource.getIsPublic());
        }
        
        // 设置默认值
        if (dataSource.getStatus() == null) {
            dataSource.setStatus(1); // 默认启用
        }
        dataSource.setDeleted(0); // 默认未删除
        dataSource.setCreateTime(new Date());
        dataSource.setUpdateTime(new Date());
        
        // 如果没有设置租户ID，使用默认值1
        if (dataSource.getTenantId() == null) {
            dataSource.setTenantId(1);
        }
        
        dataSource = dataSourceRepository.save(dataSource);
        
        logger.info("创建数据源成功 - ID: {}, 名称: {}, 类型: {}", dataSource.getId(), dataSource.getName(), dataSource.getType());
        
        return convertToResp(dataSource);
    }
    
    @Override
    @Transactional
    public DataSourceResp updateDataSource(Long id, UpdateDataSourceReq req) {
        Optional<DataSource> optional = dataSourceRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("数据源不存在: " + id);
        }
        
        DataSource dataSource = optional.get();
        
        // 检查是否已删除
        if (dataSource.getDeleted() != null && dataSource.getDeleted() == 1) {
            throw new RuntimeException("数据源已被删除");
        }
        
        // 更新字段
        if (req.getName() != null) {
            dataSource.setName(req.getName());
        }
        if (req.getDescription() != null) {
            dataSource.setDescription(req.getDescription());
        }
        if (req.getType() != null) {
            dataSource.setType(req.getType());
        }
        if (req.getHost() != null) {
            dataSource.setHost(req.getHost());
        }
        if (req.getPort() != null) {
            dataSource.setPort(req.getPort());
        }
        if (req.getDatabase() != null) {
            dataSource.setDatabase(req.getDatabase());
        }
        if (req.getUsername() != null) {
            dataSource.setUsername(req.getUsername());
        }
        if (req.getPassword() != null && !req.getPassword().isEmpty()) {
            // 加密新密码
            dataSource.setPassword(passwordEncryptionUtil.encrypt(req.getPassword()));
        }
        if (req.getStatus() != null) {
            dataSource.setStatus(req.getStatus());
        }
        if (req.getIsPublic() != null) {
            dataSource.setIsPublic(req.getIsPublic());
        }
        
        dataSource.setUpdateTime(new Date());
        
        dataSource = dataSourceRepository.save(dataSource);
        
        // 清除连接池（配置已更新）
        connectionService.clearConnectionPool(id);
        
        logger.info("更新数据源成功 - ID: {}, 名称: {}", dataSource.getId(), dataSource.getName());
        
        return convertToResp(dataSource);
    }
    
    @Override
    @Transactional
    public void deleteDataSource(Long id) {
        Optional<DataSource> optional = dataSourceRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("数据源不存在: " + id);
        }
        
        DataSource dataSource = optional.get();
        dataSource.setDeleted(1);
        dataSource.setUpdateTime(new Date());
        dataSourceRepository.save(dataSource);
        
        // 清除连接池
        connectionService.clearConnectionPool(id);
        
        logger.info("删除数据源成功 - ID: {}, 名称: {}", dataSource.getId(), dataSource.getName());
    }
    
    @Override
    public DataSource getDataSourceEntityById(Long id) {
        Optional<DataSource> optional = dataSourceRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("数据源不存在: " + id);
        }
        
        DataSource dataSource = optional.get();
        if (dataSource.getDeleted() != null && dataSource.getDeleted() == 1) {
            throw new RuntimeException("数据源已被删除");
        }
        
        return dataSource;
    }
    
    @Override
    public DataSourceResp getDataSourceById(Long id) {
        DataSource dataSource = getDataSourceEntityById(id);
        return convertToResp(dataSource);
    }
    
    @Override
    public List<DataSourceResp> listDataSources(Integer tenantId, Integer status, String keyword, String type, Long userId, Integer userRole) {
        List<DataSource> dataSources;
        
        // 根据条件查询
        if (keyword != null && !keyword.trim().isEmpty()) {
            if (status != null) {
                dataSources = dataSourceRepository.findByStatusAndNameOrDescriptionContaining(status, keyword.trim());
            } else {
                dataSources = dataSourceRepository.findByNameOrDescriptionContaining(keyword.trim());
            }
        } else {
            if (tenantId != null && status != null) {
                dataSources = dataSourceRepository.findByTenantIdAndStatus(tenantId, status);
            } else if (tenantId != null) {
                dataSources = dataSourceRepository.findByTenantId(tenantId);
            } else if (status != null) {
                dataSources = dataSourceRepository.findByStatus(status);
            } else {
                dataSources = dataSourceRepository.findAll();
            }
        }
        
        // 过滤已删除的数据源
        dataSources = dataSources.stream()
                .filter(ds -> ds.getDeleted() == null || ds.getDeleted() == 0)
                .collect(Collectors.toList());
        
        // 按类型过滤
        if (type != null && !type.trim().isEmpty()) {
            final String finalType = type.trim();
            dataSources = dataSources.stream()
                    .filter(ds -> finalType.equalsIgnoreCase(ds.getType()))
                    .collect(Collectors.toList());
        }
        
        // 权限过滤：根据用户角色和数据源的公开/私有属性
        if (userId != null) {
            final Long finalUserId = userId;
            final Integer finalUserRole = userRole;
            
            dataSources = dataSources.stream()
                    .filter(ds -> {
                        // 检查用户是否被明确禁止访问（通过UserDataSourceVisibility表）
                        // 如果用户被明确设置为不可见，则直接返回false
                        boolean hasAccess = userDataSourceVisibilityService.hasAccess(finalUserId, ds.getId());
                        if (!hasAccess) {
                            return false;
                        }
                        
                        // 管理员可以看到所有被授权的数据源
                        if (finalUserRole != null && finalUserRole == 1) {
                            return true;
                        }
                        
                        // 普通用户的访问规则：
                        // 1. 公开的数据源（is_public = true）
                        // 2. 自己创建的私有数据源（creator_id = userId）
                        // 3. 被管理员明确授权访问的私有数据源（在UserDataSourceVisibility表中visible=true）
                        boolean isPublic = Boolean.TRUE.equals(ds.getIsPublic());
                        boolean isOwner = finalUserId.equals(ds.getCreatorId());
                        
                        // 检查是否被明确授权（在UserDataSourceVisibility表中有记录且visible=true）
                        boolean isExplicitlyGranted = userDataSourceVisibilityService.isExplicitlyGranted(finalUserId, ds.getId());
                        
                        return isPublic || isOwner || isExplicitlyGranted;
                    })
                    .collect(Collectors.toList());
        }
        
        return dataSources.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean testConnection(Long id) {
        Optional<DataSource> optional = dataSourceRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("数据源不存在: " + id);
        }
        
        DataSource dataSource = optional.get();
        return connectionService.testConnection(dataSource);
    }
    
    @Override
    public boolean testConnectionConfig(CreateDataSourceReq req) {
        // 创建一个临时的DataSource对象用于测试
        DataSource tempDataSource = new DataSource();
        tempDataSource.setType(req.getType());
        tempDataSource.setHost(req.getHost());
        tempDataSource.setPort(req.getPort());
        // 处理可能为null的字段
        tempDataSource.setDatabase(req.getDatabase() != null && !req.getDatabase().trim().isEmpty() ? req.getDatabase().trim() : null);
        tempDataSource.setUsername(req.getUsername() != null && !req.getUsername().trim().isEmpty() ? req.getUsername().trim() : null);
        // 密码不需要加密，因为这是临时测试，DatabaseConnectionService会处理
        // 如果密码为null或空，设置为空字符串（某些数据库允许空密码）
        tempDataSource.setPassword(req.getPassword() != null ? req.getPassword() : "");
        
        // 传入false表示密码未加密
        return connectionService.testConnection(tempDataSource, false);
    }
    
    /**
     * 转换为响应对象
     */
    private DataSourceResp convertToResp(DataSource dataSource) {
        DataSourceResp resp = new DataSourceResp();
        BeanUtils.copyProperties(dataSource, resp);
        // 注意：响应对象中不包含密码
        return resp;
    }
}