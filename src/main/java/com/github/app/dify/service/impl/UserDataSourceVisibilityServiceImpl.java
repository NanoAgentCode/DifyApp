package com.github.app.dify.service.impl;

import com.github.app.dify.domain.DataSource;
import com.github.app.dify.domain.UserDataSourceVisibility;
import com.github.app.dify.repository.DataSourceRepository;
import com.github.app.dify.repository.UserDataSourceVisibilityRepository;
import com.github.app.dify.service.UserDataSourceVisibilityService;
import com.github.app.dify.resp.UserDataSourceVisibilityResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
/**
 * 用户数据源可见性服务
 */
@Service
public class UserDataSourceVisibilityServiceImpl implements UserDataSourceVisibilityService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserDataSourceVisibilityServiceImpl.class);
    
    @Autowired
    private UserDataSourceVisibilityRepository visibilityRepository;
    
    @Autowired
    private DataSourceRepository dataSourceRepository;
    
    /**
     * 获取用户的所有数据源可见性列表
     * 如果用户没有设置可见性，默认所有数据源都可见
     */
    @Override
    public List<UserDataSourceVisibilityResp> getUserDataSourceVisibilities(Long userId) {
        // 获取所有已启用且未删除的数据源
        List<DataSource> allDataSources = dataSourceRepository.findAll().stream()
                .filter(ds -> ds.getDeleted() == null || ds.getDeleted() == 0)
                .filter(ds -> ds.getStatus() != null && ds.getStatus() == 1)
                .collect(Collectors.toList());
        
        // 获取用户已设置的数据源可见性
        List<UserDataSourceVisibility> userVisibilities = visibilityRepository.findByUserId(userId);
        
        // 构建响应列表
        return allDataSources.stream().map(ds -> {
            UserDataSourceVisibilityResp resp = new UserDataSourceVisibilityResp();
            resp.setDataSourceId(ds.getId());
            resp.setDataSourceName(ds.getName());
            resp.setDataSourceDescription(ds.getDescription());
            resp.setDataSourceType(ds.getType());
            resp.setDataSourceStatus(ds.getStatus());
            
            // 查找用户是否设置了可见性
            Optional<UserDataSourceVisibility> visibility = userVisibilities.stream()
                    .filter(v -> v.getDataSourceId().equals(ds.getId()))
                    .findFirst();
            
            // 如果设置了，使用设置的值；否则默认可见
            resp.setVisible(visibility.map(UserDataSourceVisibility::getVisible).orElse(true));
            
            return resp;
        }).collect(Collectors.toList());
    }
    
    /**
     * 更新用户对数据源的可见性
     */
    @Transactional
    @Override
    public void updateUserDataSourceVisibility(Long userId, Long dataSourceId, Boolean visible) {
        Optional<UserDataSourceVisibility> optional = visibilityRepository.findByUserIdAndDataSourceId(userId, dataSourceId);
        
        UserDataSourceVisibility visibility;
        if (optional.isPresent()) {
            visibility = optional.get();
            visibility.setVisible(visible);
            visibility.setUpdateTime(new Date());
        } else {
            visibility = new UserDataSourceVisibility();
            visibility.setUserId(userId);
            visibility.setDataSourceId(dataSourceId);
            visibility.setVisible(visible);
            visibility.setCreateTime(new Date());
            visibility.setUpdateTime(new Date());
        }
        
        visibilityRepository.save(visibility);
        
        logger.info("更新用户数据源可见性 - 用户ID: {}, 数据源ID: {}, 可见性: {}", userId, dataSourceId, visible);
    }
    
    /**
     * 批量更新用户对数据源的可见性
     */
    @Transactional
    @Override
    public void batchUpdateUserDataSourceVisibility(Long userId, List<Long> dataSourceIds, Boolean visible) {
        Date now = new Date();
        for (Long dataSourceId : dataSourceIds) {
            Optional<UserDataSourceVisibility> optional = visibilityRepository.findByUserIdAndDataSourceId(userId, dataSourceId);
            
            UserDataSourceVisibility visibility;
            if (optional.isPresent()) {
                visibility = optional.get();
                visibility.setVisible(visible);
                visibility.setUpdateTime(now);
            } else {
                visibility = new UserDataSourceVisibility();
                visibility.setUserId(userId);
                visibility.setDataSourceId(dataSourceId);
                visibility.setVisible(visible);
                visibility.setCreateTime(now);
                visibility.setUpdateTime(now);
            }
            
            visibilityRepository.save(visibility);
        }
        
        logger.info("批量更新用户数据源可见性 - 用户ID: {}, 数据源数量: {}, 可见性: {}", userId, dataSourceIds.size(), visible);
    }
    
    /**
     * 检查用户是否有权限访问数据源
     * 如果没有设置可见性，默认可见
     */
    @Override
    public boolean hasAccess(Long userId, Long dataSourceId) {
        Optional<UserDataSourceVisibility> optional = visibilityRepository.findByUserIdAndDataSourceId(userId, dataSourceId);
        return optional.map(UserDataSourceVisibility::getVisible).orElse(true);
    }
    
    /**
     * 检查用户是否被明确授权访问数据源
     * 只有在UserDataSourceVisibility表中有记录且visible=true时，才返回true
     * 如果没有记录，返回false（表示没有明确授权）
     */
    @Override
    public boolean isExplicitlyGranted(Long userId, Long dataSourceId) {
        Optional<UserDataSourceVisibility> optional = visibilityRepository.findByUserIdAndDataSourceId(userId, dataSourceId);
        return optional.isPresent() && Boolean.TRUE.equals(optional.get().getVisible());
    }
}