package com.github.app.dify.permission.service.impl;

import com.github.app.dify.common.util.DateTimeUtil;
import com.github.app.dify.permission.domain.UserDataSourceVisibility;
import com.github.app.dify.permission.repository.UserDataSourceVisibilityRepository;
import com.github.app.dify.permission.resp.UserDataSourceVisibilityResp;
import com.github.app.dify.permission.service.UserDataSourceVisibilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.app.dify.permission.util.PermissionConverterUtil;
import com.github.app.dify.permission.util.PermissionDateTimeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户数据源可见性服务实现
 */
@Service
public class UserDataSourceVisibilityServiceImpl implements UserDataSourceVisibilityService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserDataSourceVisibilityServiceImpl.class);
    
    @Autowired
    private UserDataSourceVisibilityRepository repository;
    
    @Override
    public boolean hasAccess(Long userId, Long dataSourceId) {
        if (userId == null || dataSourceId == null) {
            return false;
        }
        
        Optional<UserDataSourceVisibility> optional = repository.findByUserIdAndDataSourceId(userId, dataSourceId);
        
        // 如果没有记录，默认允许访问（由业务逻辑层决定）
        if (!optional.isPresent()) {
            return true;
        }
        
        UserDataSourceVisibility visibility = optional.get();
        // 如果visible为true，允许访问；如果visible为false，拒绝访问
        return Boolean.TRUE.equals(visibility.getVisible());
    }
    
    @Override
    public boolean isExplicitlyGranted(Long userId, Long dataSourceId) {
        if (userId == null || dataSourceId == null) {
            return false;
        }
        
        Optional<UserDataSourceVisibility> optional = repository.findByUserIdAndDataSourceId(userId, dataSourceId);
        
        // 如果没有记录，表示未被明确授权
        if (!optional.isPresent()) {
            return false;
        }
        
        UserDataSourceVisibility visibility = optional.get();
        // 只有在visible为true时，才表示被明确授权
        return Boolean.TRUE.equals(visibility.getVisible());
    }
    
    @Override
    public List<UserDataSourceVisibilityResp> getUserDataSourceVisibilities(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        
        List<UserDataSourceVisibility> visibilities = repository.findByUserId(userId);
        return visibilities.stream()
                .map(PermissionConverterUtil::convertToResp)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void updateUserDataSourceVisibility(Long userId, Long dataSourceId, Boolean visible) {
        if (userId == null || dataSourceId == null || visible == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        
        Optional<UserDataSourceVisibility> optional = repository.findByUserIdAndDataSourceId(userId, dataSourceId);
        
        UserDataSourceVisibility visibility;
        if (optional.isPresent()) {
            // 更新现有记录
            visibility = optional.get();
            visibility.setVisible(visible);
            PermissionDateTimeUtil.setUpdateTime(visibility);
        } else {
            // 创建新记录
            visibility = new UserDataSourceVisibility();
            visibility.setUserId(userId);
            visibility.setDataSourceId(dataSourceId);
            visibility.setVisible(visible);
            PermissionDateTimeUtil.setCreateAndUpdateTime(visibility);
        }
        
        repository.save(visibility);
        logger.info("更新用户数据源可见性 - 用户ID: {}, 数据源ID: {}, 可见性: {}", userId, dataSourceId, visible);
    }
    
    @Override
    @Transactional
    public void batchUpdateUserDataSourceVisibility(Long userId, List<Long> dataSourceIds, Boolean visible) {
        if (userId == null || dataSourceIds == null || visible == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        
        java.util.Date now = DateTimeUtil.now();
        // 性能优化：批量查询现有记录
        List<UserDataSourceVisibility> existingVisibilities = repository.findByUserIdAndDataSourceIdIn(userId, dataSourceIds);
        Map<Long, UserDataSourceVisibility> existingMap = existingVisibilities.stream()
                .collect(Collectors.toMap(UserDataSourceVisibility::getDataSourceId, v -> v));
        
        List<UserDataSourceVisibility> toSave = new ArrayList<>();
        for (Long dataSourceId : dataSourceIds) {
            UserDataSourceVisibility visibility = existingMap.get(dataSourceId);
            if (visibility != null) {
                // 更新现有记录
                visibility.setVisible(visible);
                PermissionDateTimeUtil.setUpdateTime(visibility);
            } else {
                // 创建新记录
                visibility = new UserDataSourceVisibility();
                visibility.setUserId(userId);
                visibility.setDataSourceId(dataSourceId);
                visibility.setVisible(visible);
                PermissionDateTimeUtil.setCreateAndUpdateTime(visibility);
            }
            toSave.add(visibility);
        }
        
        // 批量保存（JPA会自动批量处理）
        repository.saveAll(toSave);
        
        logger.info("批量更新用户数据源可见性 - 用户ID: {}, 数据源数量: {}, 可见性: {}", userId, dataSourceIds.size(), visible);
    }
    
}

