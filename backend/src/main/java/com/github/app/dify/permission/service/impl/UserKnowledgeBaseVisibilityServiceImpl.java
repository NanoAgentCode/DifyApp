package com.github.app.dify.permission.service.impl;

import com.github.app.dify.permission.domain.UserKnowledgeBaseVisibility;
import com.github.app.dify.permission.repository.UserKnowledgeBaseVisibilityRepository;
import com.github.app.dify.permission.resp.UserKnowledgeBaseVisibilityResp;
import com.github.app.dify.permission.service.UserKnowledgeBaseVisibilityService;
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
 * 用户知识库可见性服务实现
 */
@Service
public class UserKnowledgeBaseVisibilityServiceImpl implements UserKnowledgeBaseVisibilityService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserKnowledgeBaseVisibilityServiceImpl.class);
    
    @Autowired
    private UserKnowledgeBaseVisibilityRepository repository;
    
    @Override
    public boolean hasAccess(Long userId, Long knowledgeBaseId) {
        if (userId == null || knowledgeBaseId == null) {
            return false;
        }
        
        Optional<UserKnowledgeBaseVisibility> optional = repository.findByUserIdAndKnowledgeBaseId(userId, knowledgeBaseId);
        
        // 如果没有记录，默认允许访问（由业务逻辑层决定）
        if (!optional.isPresent()) {
            return true;
        }
        
        UserKnowledgeBaseVisibility visibility = optional.get();
        // 如果visible为true，允许访问；如果visible为false，拒绝访问
        return Boolean.TRUE.equals(visibility.getVisible());
    }
    
    @Override
    public List<UserKnowledgeBaseVisibilityResp> getUserKnowledgeBaseVisibilities(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        
        List<UserKnowledgeBaseVisibility> visibilities = repository.findByUserId(userId);
        return visibilities.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void updateUserKnowledgeBaseVisibility(Long userId, Long knowledgeBaseId, Boolean visible) {
        if (userId == null || knowledgeBaseId == null || visible == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        
        Optional<UserKnowledgeBaseVisibility> optional = repository.findByUserIdAndKnowledgeBaseId(userId, knowledgeBaseId);
        
        UserKnowledgeBaseVisibility visibility;
        if (optional.isPresent()) {
            // 更新现有记录
            visibility = optional.get();
            visibility.setVisible(visible);
            visibility.setUpdateTime(new Date());
        } else {
            // 创建新记录
            visibility = new UserKnowledgeBaseVisibility();
            visibility.setUserId(userId);
            visibility.setKnowledgeBaseId(knowledgeBaseId);
            visibility.setVisible(visible);
            visibility.setCreateTime(new Date());
            visibility.setUpdateTime(new Date());
        }
        
        repository.save(visibility);
        logger.info("更新用户知识库可见性 - 用户ID: {}, 知识库ID: {}, 可见性: {}", userId, knowledgeBaseId, visible);
    }
    
    /**
     * 转换为响应对象
     */
    private UserKnowledgeBaseVisibilityResp convertToResp(UserKnowledgeBaseVisibility visibility) {
        UserKnowledgeBaseVisibilityResp resp = new UserKnowledgeBaseVisibilityResp();
        BeanUtils.copyProperties(visibility, resp);
        return resp;
    }
}

