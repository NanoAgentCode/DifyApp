package com.github.app.dify.service;

import com.github.app.dify.domain.AiApp;
import com.github.app.dify.domain.UserAppVisibility;
import com.github.app.dify.repository.AiAppRepository;
import com.github.app.dify.repository.UserAppVisibilityRepository;
import com.github.app.dify.resp.UserAppVisibilityResp;
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
 * 用户应用可见性服务
 */
@Service
public class UserAppVisibilityService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserAppVisibilityService.class);
    
    @Autowired
    private UserAppVisibilityRepository visibilityRepository;
    
    @Autowired
    private AiAppRepository aiAppRepository;
    
    /**
     * 获取用户的所有应用可见性列表
     * 如果用户没有设置可见性，默认所有应用都可见
     */
    public List<UserAppVisibilityResp> getUserAppVisibilities(Long userId) {
        // 获取所有已启用的应用
        List<AiApp> allApps = aiAppRepository.findAll().stream()
                .filter(app -> app.getDeleted() == null || app.getDeleted() == 0)
                .filter(app -> app.getStatus() != null && app.getStatus() == 1)
                .collect(Collectors.toList());
        
        // 获取用户已设置的应用可见性
        List<UserAppVisibility> userVisibilities = visibilityRepository.findByUserId(userId);
        
        // 构建响应列表
        return allApps.stream().map(app -> {
            UserAppVisibilityResp resp = new UserAppVisibilityResp();
            resp.setAppId(app.getId());
            resp.setAppName(app.getName());
            resp.setAppDescription(app.getDescription());
            resp.setAppType(app.getType());
            
            // 查找用户是否设置了可见性
            Optional<UserAppVisibility> visibility = userVisibilities.stream()
                    .filter(v -> v.getAppId().equals(app.getId()))
                    .findFirst();
            
            // 如果设置了，使用设置的值；否则默认可见
            resp.setVisible(visibility.map(UserAppVisibility::getVisible).orElse(true));
            
            return resp;
        }).collect(Collectors.toList());
    }
    
    /**
     * 更新用户对应用的可见性
     */
    @Transactional
    public void updateUserAppVisibility(Long userId, Long appId, Boolean visible) {
        Optional<UserAppVisibility> optional = visibilityRepository.findByUserIdAndAppId(userId, appId);
        
        UserAppVisibility visibility;
        if (optional.isPresent()) {
            visibility = optional.get();
            visibility.setVisible(visible);
            visibility.setUpdateTime(new Date());
        } else {
            visibility = new UserAppVisibility();
            visibility.setUserId(userId);
            visibility.setAppId(appId);
            visibility.setVisible(visible);
            visibility.setCreateTime(new Date());
            visibility.setUpdateTime(new Date());
        }
        
        visibilityRepository.save(visibility);
        
        logger.info("更新用户应用可见性 - 用户ID: {}, 应用ID: {}, 可见性: {}", userId, appId, visible);
    }
}

