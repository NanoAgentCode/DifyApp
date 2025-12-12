package com.github.app.dify.auth.service;

import com.github.app.dify.auth.resp.UserKnowledgeBaseVisibilityResp;
import java.util.List;

/**
 * 用户知识库可见性服务接口
 */
public interface UserKnowledgeBaseVisibilityService {
    
    /**
     * 检查用户是否有权限访问知识库
     * 
     * @param userId 用户ID
     * @param knowledgeBaseId 知识库ID
     * @return true-有权限，false-无权限
     */
    boolean hasAccess(Long userId, Long knowledgeBaseId);
    
    /**
     * 获取用户的知识库可见性列表
     * 
     * @param userId 用户ID
     * @return 知识库可见性列表
     */
    List<UserKnowledgeBaseVisibilityResp> getUserKnowledgeBaseVisibilities(Long userId);
    
    /**
     * 更新用户对知识库的可见性
     * 
     * @param userId 用户ID
     * @param knowledgeBaseId 知识库ID
     * @param visible 是否可见
     */
    void updateUserKnowledgeBaseVisibility(Long userId, Long knowledgeBaseId, Boolean visible);
}

