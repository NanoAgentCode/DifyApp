package com.github.app.dify.appknowledgebase.service;

import com.github.app.dify.appknowledgebase.req.CreateKnowledgeBaseReq;
import com.github.app.dify.appknowledgebase.req.UpdateKnowledgeBaseReq;
import com.github.app.dify.appknowledgebase.resp.KnowledgeBaseResp;
import com.github.app.dify.appcommon.resp.PageResponse;
import java.util.List;
/**
 * 知识库服务接口
 */
public interface KnowledgeBaseService {
    
    /**
     * 创建知识库
     * @param req 创建请求
     * @param userId 用户ID
     * @param username 用户名
     * @param role 用户角色（1-管理员，0-普通用户）
     * @param force 是否强制创建（忽略重复名称检查）
     */
    KnowledgeBaseResp createKnowledgeBase(CreateKnowledgeBaseReq req, Long userId, String username, Integer role, Boolean force);
    
    /**
     * 更新知识库
     */
    KnowledgeBaseResp updateKnowledgeBase(Long id, UpdateKnowledgeBaseReq req);
    
    /**
     * 根据ID获取知识库
     */
    KnowledgeBaseResp getKnowledgeBaseById(Long id);
    
    /**
     * 删除知识库
     */
    void deleteKnowledgeBase(Long id);
    
    /**
     * 获取知识库列表
     * @param tenantId 租户ID
     * @param status 状态
     * @param keyword 关键词
     * @param userId 用户ID（用于权限过滤，如果为null则不进行权限过滤）
     * @param userRole 用户角色（1-管理员，0-普通用户），如果为null则按普通用户处理
     */
    List<KnowledgeBaseResp> listKnowledgeBases(Integer tenantId, Integer status, String keyword, Long userId, Integer userRole);
    
    /**
     * 获取知识库列表（兼容旧接口，不传userRole时按普通用户处理）
     */
    List<KnowledgeBaseResp> listKnowledgeBases(Integer tenantId, Integer status, String keyword, Long userId);
    
    /**
     * 获取知识库列表（分页）
     * @param tenantId 租户ID
     * @param status 状态
     * @param keyword 关键词
     * @param vectorStoreType 向量库类型
     * @param userId 用户ID（用于权限过滤，如果为null则不进行权限过滤）
     * @param userRole 用户角色（1-管理员，0-普通用户），如果为null则按普通用户处理
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     */
    PageResponse<KnowledgeBaseResp> listKnowledgeBasesWithPagination(
            Integer tenantId, Integer status, String keyword, String vectorStoreType, Long userId, Integer userRole, 
            int page, int pageSize);
    
    /**
     * 生成知识库智能摘要
     * @param knowledgeBaseId 知识库ID
     * @param modelId 模型ID（可选，如果为null则使用默认模型）
     * @return 生成的摘要
     */
    String generateSummary(Long knowledgeBaseId, Long modelId);
}