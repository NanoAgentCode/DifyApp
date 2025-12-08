package com.github.app.dify.service.impl;

import com.github.app.dify.domain.KnowledgeBase;
import com.github.app.dify.repository.KnowledgeBaseDocumentRepository;
import com.github.app.dify.repository.KnowledgeBaseRepository;
import com.github.app.dify.req.CreateKnowledgeBaseReq;
import com.github.app.dify.req.UpdateKnowledgeBaseReq;
import com.github.app.dify.resp.KnowledgeBaseResp;
import com.github.app.dify.resp.PageResponse;
import com.github.app.dify.service.KnowledgeBaseService;
import com.github.app.dify.service.UserKnowledgeBaseVisibilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 知识库服务实现
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private KnowledgeBaseDocumentRepository documentRepository;
    
    @Autowired
    private UserKnowledgeBaseVisibilityService userKnowledgeBaseVisibilityService;
    
    @Autowired(required = false)
    private com.github.app.dify.repository.VectorDatabaseRepository vectorDatabaseRepository;
    
    @Override
    @Transactional
    public KnowledgeBaseResp createKnowledgeBase(CreateKnowledgeBaseReq req, Long userId, String username, Integer role, Boolean force) {
        // 检查是否存在相同名称的知识库（除非强制创建）
        if (force == null || !force) {
            List<KnowledgeBase> existingKbs = knowledgeBaseRepository.findByNameAndNotDeleted(req.getName());
            if (!existingKbs.isEmpty()) {
                throw new RuntimeException("DUPLICATE_NAME:已存在名称为 \"" + req.getName() + "\" 的知识库，是否继续创建？");
            }
        }
        
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        BeanUtils.copyProperties(req, knowledgeBase);
        
        // 设置创建者信息
        knowledgeBase.setCreator(username);
        knowledgeBase.setCreatorId(userId);
        
        // 设置可见性：普通用户只能创建私有知识库，管理员可以创建公开或私有知识库
        if (role == null || role != 1) {
            // 普通用户，强制设置为私有
            knowledgeBase.setIsPublic(false);
            logger.info("普通用户创建知识库，强制设置为私有 - 用户ID: {}, 名称: {}", userId, knowledgeBase.getName());
        } else {
            // 管理员，使用请求中的设置，如果没有设置则默认为私有
            if (knowledgeBase.getIsPublic() == null) {
                knowledgeBase.setIsPublic(false);
            }
            logger.info("管理员创建知识库 - 用户ID: {}, 名称: {}, 是否公开: {}", userId, knowledgeBase.getName(), knowledgeBase.getIsPublic());
        }
        
        // 设置默认值
        if (knowledgeBase.getStatus() == null) {
            knowledgeBase.setStatus(1); // 默认启用
        }
        knowledgeBase.setDeleted(0); // 默认未删除
        knowledgeBase.setCreateTime(new Date());
        knowledgeBase.setUpdateTime(new Date());
        
        // 如果没有设置租户ID，使用默认值1
        if (knowledgeBase.getTenantId() == null) {
            knowledgeBase.setTenantId(1);
        }
        
        // 处理向量库实例ID和类型
        if (req.getVectorDatabaseId() != null) {
            // 如果指定了向量库实例ID，验证并设置
            if (vectorDatabaseRepository != null) {
                Optional<com.github.app.dify.domain.VectorDatabase> vectorDb = vectorDatabaseRepository.findById(req.getVectorDatabaseId());
                if (vectorDb.isPresent() && vectorDb.get().getEnabled()) {
                    knowledgeBase.setVectorDatabaseId(req.getVectorDatabaseId());
                    // 同时设置类型（用于兼容）
                    knowledgeBase.setVectorStoreType(vectorDb.get().getType());
                    logger.info("使用指定的向量库实例 - ID: {}, 类型: {}, 名称: {}", 
                            req.getVectorDatabaseId(), vectorDb.get().getType(), vectorDb.get().getName());
                } else {
                    logger.warn("指定的向量库实例不存在或未启用 - ID: {}, 使用默认配置", req.getVectorDatabaseId());
                    // 使用默认配置
                    setDefaultVectorDatabase(knowledgeBase);
                }
            } else {
                logger.warn("VectorDatabaseRepository未注入，无法验证向量库实例ID，使用默认配置");
                setDefaultVectorDatabase(knowledgeBase);
            }
        } else {
            // 如果没有指定向量库实例ID，使用默认配置
            setDefaultVectorDatabase(knowledgeBase);
        }
        
        logger.info("创建知识库 - 名称: {}, 创建者: {}, 是否公开: {}, 向量存储类型: {}, 向量库实例ID: {}", 
                knowledgeBase.getName(), username, knowledgeBase.getIsPublic(), 
                knowledgeBase.getVectorStoreType(), knowledgeBase.getVectorDatabaseId());
        
        knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);
        
        logger.info("知识库创建成功 - ID: {}", knowledgeBase.getId());
        
        return convertToResp(knowledgeBase);
    }
    
    @Override
    @Transactional
    public KnowledgeBaseResp updateKnowledgeBase(Long id, UpdateKnowledgeBaseReq req) {
        Optional<KnowledgeBase> optional = knowledgeBaseRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("知识库不存在: " + id);
        }
        
        KnowledgeBase knowledgeBase = optional.get();
        
        // 检查是否已删除
        if (knowledgeBase.getDeleted() != null && knowledgeBase.getDeleted() == 1) {
            throw new RuntimeException("知识库已删除: " + id);
        }
        
        // 更新字段
        if (req.getName() != null) {
            knowledgeBase.setName(req.getName());
        }
        if (req.getDescription() != null) {
            knowledgeBase.setDescription(req.getDescription());
        }
        if (req.getStatus() != null) {
            knowledgeBase.setStatus(req.getStatus());
        }
        if (req.getIsPublic() != null) {
            knowledgeBase.setIsPublic(req.getIsPublic());
        }
        if (req.getEmbeddingModelId() != null) {
            knowledgeBase.setEmbeddingModelId(req.getEmbeddingModelId());
        }
        if (req.getTopK() != null) {
            knowledgeBase.setTopK(req.getTopK());
        }
        // 处理向量库实例ID和类型
        if (req.getVectorDatabaseId() != null) {
            // 如果指定了向量库实例ID，验证并设置
            if (vectorDatabaseRepository != null) {
                Optional<com.github.app.dify.domain.VectorDatabase> vectorDb = vectorDatabaseRepository.findById(req.getVectorDatabaseId());
                if (vectorDb.isPresent() && vectorDb.get().getEnabled()) {
                    knowledgeBase.setVectorDatabaseId(req.getVectorDatabaseId());
                    // 同时设置类型（用于兼容）
                    knowledgeBase.setVectorStoreType(vectorDb.get().getType());
                    logger.info("更新知识库时使用指定的向量库实例 - ID: {}, 类型: {}, 名称: {}", 
                            req.getVectorDatabaseId(), vectorDb.get().getType(), vectorDb.get().getName());
                } else {
                    logger.warn("指定的向量库实例不存在或未启用 - ID: {}, 保持原有配置", req.getVectorDatabaseId());
                }
            } else {
                logger.warn("VectorDatabaseRepository未注入，无法验证向量库实例ID");
            }
        } else if (req.getVectorStoreType() != null && !req.getVectorStoreType().trim().isEmpty()) {
            // 如果只指定了类型（兼容旧逻辑），设置类型但不设置实例ID
            knowledgeBase.setVectorStoreType(req.getVectorStoreType());
            logger.info("更新知识库时使用指定的向量存储类型 - 类型: {}", req.getVectorStoreType());
        }
        
        knowledgeBase.setUpdateTime(new Date());
        knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);
        
        logger.info("知识库更新成功 - ID: {}", knowledgeBase.getId());
        
        return convertToResp(knowledgeBase);
    }
    
    @Override
    public KnowledgeBaseResp getKnowledgeBaseById(Long id) {
        Optional<KnowledgeBase> optional = knowledgeBaseRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("知识库不存在: " + id);
        }
        
        KnowledgeBase knowledgeBase = optional.get();
        
        // 检查是否已删除
        if (knowledgeBase.getDeleted() != null && knowledgeBase.getDeleted() == 1) {
            throw new RuntimeException("知识库已删除: " + id);
        }
        
        return convertToResp(knowledgeBase);
    }
    
    @Override
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        Optional<KnowledgeBase> optional = knowledgeBaseRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("知识库不存在: " + id);
        }
        
        KnowledgeBase knowledgeBase = optional.get();
        knowledgeBase.setDeleted(1);
        knowledgeBase.setUpdateTime(new Date());
        knowledgeBaseRepository.save(knowledgeBase);
        
        logger.info("知识库删除成功 - ID: {}", id);
    }
    
    @Override
    public List<KnowledgeBaseResp> listKnowledgeBases(Integer tenantId, Integer status, String keyword, Long userId, Integer userRole) {
        List<KnowledgeBase> knowledgeBases;
        
        // 根据条件查询
        if (keyword != null && !keyword.trim().isEmpty()) {
            // 有搜索关键词
            if (status != null) {
                knowledgeBases = knowledgeBaseRepository.findByStatusAndNameOrDescriptionContaining(
                        status, keyword.trim());
            } else {
                knowledgeBases = knowledgeBaseRepository.findByNameOrDescriptionContaining(keyword.trim());
            }
        } else {
            // 无搜索关键词
            if (tenantId != null && status != null) {
                knowledgeBases = knowledgeBaseRepository.findByTenantIdAndStatus(tenantId, status);
            } else if (tenantId != null) {
                knowledgeBases = knowledgeBaseRepository.findByTenantId(tenantId);
            } else if (status != null) {
                knowledgeBases = knowledgeBaseRepository.findByStatus(status);
            } else {
                knowledgeBases = knowledgeBaseRepository.findAll();
            }
        }
        
        // 过滤已删除的知识库
        knowledgeBases = knowledgeBases.stream()
                .filter(kb -> kb.getDeleted() == null || kb.getDeleted() == 0)
                .collect(Collectors.toList());
        
        // 权限过滤：根据用户角色和知识库的公开/私有属性
        if (userId != null) {
            final Long finalUserId = userId;
            final Integer finalUserRole = userRole;
            
            knowledgeBases = knowledgeBases.stream()
                    .filter(kb -> {
                        // 管理员可以看到所有知识库
                        if (finalUserRole != null && finalUserRole == 1) {
                            // 管理员还需要检查用户可见性设置（如果设置了的话）
                            return userKnowledgeBaseVisibilityService.hasAccess(finalUserId, kb.getId());
                        }
                        
                        // 普通用户只能看到：
                        // 1. 公开的知识库（is_public = true）
                        // 2. 自己创建的私有知识库（creator_id = userId AND is_public = false）
                        boolean isPublic = Boolean.TRUE.equals(kb.getIsPublic());
                        boolean isOwner = finalUserId.equals(kb.getCreatorId());
                        
                        if (isPublic || isOwner) {
                            // 还需要检查用户可见性设置（如果设置了的话）
                            return userKnowledgeBaseVisibilityService.hasAccess(finalUserId, kb.getId());
                        }
                        
                        return false;
                    })
                    .collect(Collectors.toList());
        }
        
        return knowledgeBases.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<KnowledgeBaseResp> listKnowledgeBases(Integer tenantId, Integer status, String keyword, Long userId) {
        return listKnowledgeBases(tenantId, status, keyword, userId, null);
    }
    
    @Override
    public PageResponse<KnowledgeBaseResp> listKnowledgeBasesWithPagination(
            Integer tenantId, Integer status, String keyword, String vectorStoreType, Long userId, Integer userRole, 
            int page, int pageSize) {
        Pageable pageable = PageRequest.of(
                page - 1, pageSize, Sort.by("createTime").descending());
        
        // 使用分页查询
        Page<KnowledgeBase> kbPage = knowledgeBaseRepository.findByFiltersWithPagination(
                status, keyword != null ? keyword.trim() : null, vectorStoreType, pageable);
        
        // 过滤已删除的知识库
        List<KnowledgeBase> knowledgeBases = kbPage.getContent().stream()
                .filter(kb -> kb.getDeleted() == null || kb.getDeleted() == 0)
                .collect(Collectors.toList());
        
        // 权限过滤：根据用户角色和知识库的公开/私有属性
        if (userId != null) {
            final Long finalUserId = userId;
            final Integer finalUserRole = userRole;
            
            knowledgeBases = knowledgeBases.stream()
                    .filter(kb -> {
                        // 管理员可以看到所有知识库
                        if (finalUserRole != null && finalUserRole == 1) {
                            // 管理员还需要检查用户可见性设置（如果设置了的话）
                            return userKnowledgeBaseVisibilityService.hasAccess(finalUserId, kb.getId());
                        }
                        
                        // 普通用户只能看到：
                        // 1. 公开的知识库（is_public = true）
                        // 2. 自己创建的私有知识库（creator_id = userId AND is_public = false）
                        boolean isPublic = Boolean.TRUE.equals(kb.getIsPublic());
                        boolean isOwner = finalUserId.equals(kb.getCreatorId());
                        
                        if (isPublic || isOwner) {
                            // 还需要检查用户可见性设置（如果设置了的话）
                            return userKnowledgeBaseVisibilityService.hasAccess(finalUserId, kb.getId());
                        }
                        
                        return false;
                    })
                    .collect(Collectors.toList());
        }
        
        // 转换为响应对象
        List<KnowledgeBaseResp> content = knowledgeBases.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
        
        // 计算总数（需要考虑权限过滤后的实际数量）
        // 注意：由于权限过滤是在分页后进行的，总数可能不准确
        // 为了准确计算，我们需要查询所有数据并过滤，但这会影响性能
        // 这里先使用分页查询的总数，实际应用中可能需要优化
        long total = kbPage.getTotalElements();
        
        return new PageResponse<>(content, total, page, pageSize);
    }
    
    /**
     * 转换为响应对象
     */
    private KnowledgeBaseResp convertToResp(KnowledgeBase knowledgeBase) {
        KnowledgeBaseResp resp = new KnowledgeBaseResp();
        BeanUtils.copyProperties(knowledgeBase, resp);
        
        // 查询实际的文档数量
        Long documentCount = documentRepository.countByKnowledgeBaseId(knowledgeBase.getId());
        resp.setDocumentCount(documentCount != null ? documentCount.intValue() : 0);
        
        // 查询成功向量化的文档数量（向量化状态为2）
        Long successCount = documentRepository.countSuccessDocumentsByKnowledgeBaseId(knowledgeBase.getId());
        resp.setSuccessDocumentCount(successCount != null ? successCount.intValue() : 0);
        
        // 查询向量化失败的文档数量（向量化状态为3）
        Long failedCount = documentRepository.countFailedDocumentsByKnowledgeBaseId(knowledgeBase.getId());
        resp.setFailedDocumentCount(failedCount != null ? failedCount.intValue() : 0);
        
        return resp;
    }
    
    /**
     * 设置默认向量库配置
     * 从向量库配置中查找默认选中的向量库实例，设置ID和类型
     */
    private void setDefaultVectorDatabase(KnowledgeBase knowledgeBase) {
        if (vectorDatabaseRepository == null) {
            logger.debug("VectorDatabaseRepository未注入，使用默认值qdrant");
            knowledgeBase.setVectorStoreType("qdrant");
            knowledgeBase.setVectorDatabaseId(null);
            return;
        }
        
        try {
            // 查找所有启用的向量库配置
            List<com.github.app.dify.domain.VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabled();
            
            if (enabledConfigs != null && !enabledConfigs.isEmpty()) {
                // 查找第一个默认的配置（is_default = true）
                Optional<com.github.app.dify.domain.VectorDatabase> defaultConfig = enabledConfigs.stream()
                        .filter(config -> config.getIsDefault() != null && config.getIsDefault())
                        .findFirst();
                
                if (defaultConfig.isPresent()) {
                    com.github.app.dify.domain.VectorDatabase db = defaultConfig.get();
                    knowledgeBase.setVectorDatabaseId(db.getId());
                    knowledgeBase.setVectorStoreType(db.getType());
                    logger.debug("找到默认向量库配置 - ID: {}, 类型: {}, 名称: {}", 
                            db.getId(), db.getType(), db.getName());
                } else {
                    // 如果没有默认配置，使用第一个启用的配置
                    com.github.app.dify.domain.VectorDatabase db = enabledConfigs.get(0);
                    knowledgeBase.setVectorDatabaseId(db.getId());
                    knowledgeBase.setVectorStoreType(db.getType());
                    logger.debug("未找到默认向量库配置，使用第一个启用的配置 - ID: {}, 类型: {}, 名称: {}", 
                            db.getId(), db.getType(), db.getName());
                }
            } else {
                logger.debug("未找到启用的向量库配置，使用默认值qdrant");
                knowledgeBase.setVectorStoreType("qdrant");
                knowledgeBase.setVectorDatabaseId(null);
            }
        } catch (Exception e) {
            logger.warn("获取默认向量库配置失败，使用默认值qdrant", e);
            knowledgeBase.setVectorStoreType("qdrant");
            knowledgeBase.setVectorDatabaseId(null);
        }
    }
}
