package com.github.app.dify.auth.service.impl;

import com.github.app.dify.auth.domain.UserAppVisibility;
import com.github.app.dify.auth.repository.UserAppVisibilityRepository;
import com.github.app.dify.auth.resp.UserAppVisibilityResp;
import com.github.app.dify.auth.service.UserAppVisibilityService;
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
 * 用户应用可见性服务实现
 */
@Service
public class UserAppVisibilityServiceImpl implements UserAppVisibilityService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserAppVisibilityServiceImpl.class);
    
    @Autowired
    private UserAppVisibilityRepository repository;
    
    @Override
    public List<UserAppVisibilityResp> getUserAppVisibilities(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        
        List<UserAppVisibility> visibilities = repository.findByUserId(userId);
        return visibilities.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void updateUserAppVisibility(Long userId, Long appId, Boolean visible) {
        if (userId == null || appId == null || visible == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        
        Optional<UserAppVisibility> optional = repository.findByUserIdAndAppId(userId, appId);
        
        UserAppVisibility visibility;
        if (optional.isPresent()) {
            // 更新现有记录
            visibility = optional.get();
            visibility.setVisible(visible);
            visibility.setUpdateTime(new Date());
        } else {
            // 创建新记录
            visibility = new UserAppVisibility();
            visibility.setUserId(userId);
            visibility.setAppId(appId);
            visibility.setVisible(visible);
            visibility.setCreateTime(new Date());
            visibility.setUpdateTime(new Date());
        }
        
        repository.save(visibility);
        logger.info("更新用户应用可见性 - 用户ID: {}, 应用ID: {}, 可见性: {}", userId, appId, visible);
    }
    
    /**
     * 转换为响应对象
     */
    private UserAppVisibilityResp convertToResp(UserAppVisibility visibility) {
        UserAppVisibilityResp resp = new UserAppVisibilityResp();
        BeanUtils.copyProperties(visibility, resp);
        return resp;
    }
}

