package com.github.app.dify.service.impl;

import com.github.app.dify.domain.KnowledgeBase;
import com.github.app.dify.domain.UserKnowledgeBaseVisibility;
import com.github.app.dify.repository.KnowledgeBaseRepository;
import com.github.app.dify.repository.UserKnowledgeBaseVisibilityRepository;
import com.github.app.dify.service.UserKnowledgeBaseVisibilityService;
import com.github.app.dify.resp.UserKnowledgeBaseVisibilityResp;
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
 * 用户知识库可见性服务
 */
@Service
public class UserKnowledgeBaseVisibilityServiceImpl implements UserKnowledgeBaseVisibilityService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserKnowledgeBaseVisibilityServiceImpl.class);
    
    @Autowired
    private UserKnowledgeBaseVisibilityRepository visibilityRepository;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    /**
     * 获取用户的所有知识库可见性列表
     * 如果用户没有设置可见性，默认所有知识库都可见
     */
    @Override
    public List<UserKnowledgeBaseVisibilityResp> getUserKnowledgeBaseVisibilities(Long userId) {
        // 获取所有已启用且未删除的知识库
        List<KnowledgeBase> allKnowledgeBases = knowledgeBaseRepository.findAll().stream()
                .filter(kb -> kb.getDeleted() == null || kb.getDeleted() == 0)
                .filter(kb -> kb.getStatus() != null && kb.getStatus() == 1)
                .collect(Collectors.toList());
        
        // 获取用户已设置的知识库可见性
        List<UserKnowledgeBaseVisibility> userVisibilities = visibilityRepository.findByUserId(userId);
        
        // 构建响应列表
        return allKnowledgeBases.stream().map(kb -> {
            UserKnowledgeBaseVisibilityResp resp = new UserKnowledgeBaseVisibilityResp();
            resp.setKnowledgeBaseId(kb.getId());
            resp.setKnowledgeBaseName(kb.getName());
            resp.setKnowledgeBaseDescription(kb.getDescription());
            resp.setKnowledgeBaseStatus(kb.getStatus());
            
            // 查找用户是否设置了可见性
            Optional<UserKnowledgeBaseVisibility> visibility = userVisibilities.stream()
                    .filter(v -> v.getKnowledgeBaseId().equals(kb.getId()))
                    .findFirst();
            
            // 如果设置了，使用设置的值；否则默认可见
            resp.setVisible(visibility.map(UserKnowledgeBaseVisibility::getVisible).orElse(true));
            
            return resp;
        }).collect(Collectors.toList());
    }
    
    /**
     * 更新用户对知识库的可见性
     */
    @Transactional
    @Override
    public void updateUserKnowledgeBaseVisibility(Long userId, Long knowledgeBaseId, Boolean visible) {
        Optional<UserKnowledgeBaseVisibility> optional = visibilityRepository.findByUserIdAndKnowledgeBaseId(userId, knowledgeBaseId);
        
        UserKnowledgeBaseVisibility visibility;
        if (optional.isPresent()) {
            visibility = optional.get();
            visibility.setVisible(visible);
            visibility.setUpdateTime(new Date());
        } else {
            visibility = new UserKnowledgeBaseVisibility();
            visibility.setUserId(userId);
            visibility.setKnowledgeBaseId(knowledgeBaseId);
            visibility.setVisible(visible);
            visibility.setCreateTime(new Date());
            visibility.setUpdateTime(new Date());
        }
        
        visibilityRepository.save(visibility);
        
        logger.info("更新用户知识库可见性 - 用户ID: {}, 知识库ID: {}, 可见性: {}", userId, knowledgeBaseId, visible);
    }
    
    /**
     * 批量更新用户对知识库的可见性
     */
    @Transactional
    @Override
    public void batchUpdateUserKnowledgeBaseVisibility(Long userId, List<Long> knowledgeBaseIds, Boolean visible) {
        Date now = new Date();
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            Optional<UserKnowledgeBaseVisibility> optional = visibilityRepository.findByUserIdAndKnowledgeBaseId(userId, knowledgeBaseId);
            
            UserKnowledgeBaseVisibility visibility;
            if (optional.isPresent()) {
                visibility = optional.get();
                visibility.setVisible(visible);
                visibility.setUpdateTime(now);
            } else {
                visibility = new UserKnowledgeBaseVisibility();
                visibility.setUserId(userId);
                visibility.setKnowledgeBaseId(knowledgeBaseId);
                visibility.setVisible(visible);
                visibility.setCreateTime(now);
                visibility.setUpdateTime(now);
            }
            
            visibilityRepository.save(visibility);
        }
        
        logger.info("批量更新用户知识库可见性 - 用户ID: {}, 知识库数量: {}, 可见性: {}", userId, knowledgeBaseIds.size(), visible);
    }
    
    /**
     * 检查用户是否有权限访问知识库
     * 如果没有设置可见性，默认可见
     */
    @Override
    public boolean hasAccess(Long userId, Long knowledgeBaseId) {
        Optional<UserKnowledgeBaseVisibility> optional = visibilityRepository.findByUserIdAndKnowledgeBaseId(userId, knowledgeBaseId);
        return optional.map(UserKnowledgeBaseVisibility::getVisible).orElse(true);
    }
}