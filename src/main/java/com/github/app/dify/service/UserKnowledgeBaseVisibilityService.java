package com.github.app.dify.service;

import com.github.app.dify.resp.UserKnowledgeBaseVisibilityResp;

import java.util.List;

/**
 * 用户知识库可见性服务接口
 */
public interface UserKnowledgeBaseVisibilityService {
    
    /**
     * 获取用户的所有知识库可见性列表
     * 如果用户没有设置可见性，默认所有知识库都可见
     */
    List<UserKnowledgeBaseVisibilityResp> getUserKnowledgeBaseVisibilities(Long userId);
    
    /**
     * 更新用户对知识库的可见性
     */
    void updateUserKnowledgeBaseVisibility(Long userId, Long knowledgeBaseId, Boolean visible);
    
    /**
     * 批量更新用户对知识库的可见性
     */
    void batchUpdateUserKnowledgeBaseVisibility(Long userId, List<Long> knowledgeBaseIds, Boolean visible);
    
    /**
     * 检查用户是否有权限访问知识库
     */
    boolean hasAccess(Long userId, Long knowledgeBaseId);
}
