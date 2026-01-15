package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseDocumentRepository;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository;
import com.github.app.dify.knowledgebase.req.CreateKnowledgeBaseReq;
import com.github.app.dify.knowledgebase.req.UpdateKnowledgeBaseReq;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseResp;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseService;
import com.github.app.dify.permission.service.UserKnowledgeBaseVisibilityService;
import com.github.app.dify.knowledgebase.service.RagRetrievalService;
import com.github.app.dify.model.service.ModelConfigService;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.app.dify.knowledgebase.util.KnowledgeBaseConverterUtil;
import com.github.app.dify.knowledgebase.util.KnowledgeBaseDateTimeUtil;
import com.github.app.dify.common.util.PageUtil;
import com.github.app.dify.knowledgebase.util.KnowledgeBaseSoftDeleteUtil;
import java.util.List;
import com.github.app.dify.system.util.SkillLoader;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;
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
    private com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository vectorDatabaseRepository;
    
    @Autowired(required = false)
    private RagRetrievalService ragRetrievalService;
    
    @Autowired(required = false)
    private ModelConfigService modelConfigService;
    
    @Autowired(required = false)
    private ModelLanguageModelFactory modelLanguageModelFactory;
    
    @Override
    @Transactional
    public KnowledgeBaseResp createKnowledgeBase(CreateKnowledgeBaseReq req, Long userId, String username, Integer role, Boolean force) {
        // 检查是否存在相同名称的知识库（除非强制创建）
        if (force == null || !force) {
            List<KnowledgeBase> existingKbs = knowledgeBaseRepository.findByNameAndNotDeleted(req.getName());
            if (!existingKbs.isEmpty()) {
                throw new BusinessException("已存在名称为 \"" + req.getName() + "\" 的知识库，是否继续创建？", ErrorCode.RESOURCE_ALREADY_EXISTS);
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
        KnowledgeBaseDateTimeUtil.setCreateAndUpdateTime(knowledgeBase);
        
        // 如果没有设置租户ID，使用默认值1
        if (knowledgeBase.getTenantId() == null) {
            knowledgeBase.setTenantId(1);
        }
        
        // 处理向量库实例ID和类型
        if (req.getVectorDatabaseId() != null) {
            // 如果指定了向量库实例ID，验证并设置
            if (vectorDatabaseRepository != null) {
                Optional<VectorDatabase> vectorDb = vectorDatabaseRepository.findById(req.getVectorDatabaseId());
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
        
        // 检查知识库ID是否为0，0保留给文档解读使用
        if (knowledgeBase.getId() != null && knowledgeBase.getId() == 0L) {
            logger.error("知识库ID为0，与文档解读冲突 - 知识库名称: {}", knowledgeBase.getName());
            // 删除刚创建的知识库
            knowledgeBaseRepository.delete(knowledgeBase);
            throw new BusinessException("知识库创建失败：ID为0，0保留给文档解读使用。请检查数据库序列配置。", ErrorCode.BAD_REQUEST);
        }
        
        logger.info("知识库创建成功 - ID: {}", knowledgeBase.getId());
        
        return KnowledgeBaseConverterUtil.convertToResp(knowledgeBase, documentRepository);
    }
    
    @Override
    @Transactional
    public KnowledgeBaseResp updateKnowledgeBase(Long id, UpdateKnowledgeBaseReq req) {
        // 检查知识库ID是否为0，0保留给文档解读使用
        if (id != null && id == 0L) {
            throw new IllegalArgumentException("知识库ID不能为0，0保留给文档解读使用");
        }
        
        Optional<KnowledgeBase> optional = knowledgeBaseRepository.findById(id);
        if (!optional.isPresent()) {
            throw new NotFoundException("知识库不存在");
        }
        
        KnowledgeBase knowledgeBase = optional.get();
        
        // 检查是否已删除
        if (knowledgeBase.getDeleted() != null && knowledgeBase.getDeleted() == 1) {
            throw new NotFoundException("知识库已删除");
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
                Optional<VectorDatabase> vectorDb = vectorDatabaseRepository.findById(req.getVectorDatabaseId());
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
        
        KnowledgeBaseDateTimeUtil.setUpdateTime(knowledgeBase);
        knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);
        
        logger.info("知识库更新成功 - ID: {}", knowledgeBase.getId());
        
        return KnowledgeBaseConverterUtil.convertToResp(knowledgeBase, documentRepository);
    }
    
    @Override
    public KnowledgeBaseResp getKnowledgeBaseById(Long id) {
        Optional<KnowledgeBase> optional = knowledgeBaseRepository.findById(id);
        if (!optional.isPresent()) {
            throw new NotFoundException("知识库不存在");
        }
        
        KnowledgeBase knowledgeBase = optional.get();
        
        // 检查是否已删除
        if (knowledgeBase.getDeleted() != null && knowledgeBase.getDeleted() == 1) {
            throw new NotFoundException("知识库已删除");
        }
        
        return KnowledgeBaseConverterUtil.convertToResp(knowledgeBase, documentRepository);
    }
    
    @Override
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        Optional<KnowledgeBase> optional = knowledgeBaseRepository.findById(id);
        if (!optional.isPresent()) {
            throw new NotFoundException("知识库不存在");
        }
        
        KnowledgeBase knowledgeBase = optional.get();
        KnowledgeBaseSoftDeleteUtil.softDelete(knowledgeBase, knowledgeBaseRepository);
        
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
                .map(kb -> KnowledgeBaseConverterUtil.convertToResp(kb, documentRepository))
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
        Pageable pageable = PageUtil.createPageable(page, pageSize);
        
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
                .map(kb -> KnowledgeBaseConverterUtil.convertToResp(kb, documentRepository))
                .collect(Collectors.toList());
        
        // 计算总数（需要考虑权限过滤后的实际数量）
        // 注意：由于权限过滤是在分页后进行的，总数可能不准确
        // 为了准确计算，我们需要查询所有数据并过滤，但这会影响性能
        // 这里先使用分页查询的总数，实际应用中可能需要优化
        long total = kbPage.getTotalElements();
        
        PageResponse<KnowledgeBaseResp> response = new PageResponse<>();
        response.setContent(content);
        response.setTotal(total);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotalPages((int) Math.ceil((double) total / pageSize));
        return response;
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
            List<com.github.app.dify.knowledgebase.domain.VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabled();
            
            if (enabledConfigs != null && !enabledConfigs.isEmpty()) {
                // 查找第一个默认的配置（is_default = true）
                Optional<com.github.app.dify.knowledgebase.domain.VectorDatabase> defaultConfig = enabledConfigs.stream()
                        .filter(config -> config.getIsDefault() != null && config.getIsDefault())
                        .findFirst();
                
                if (defaultConfig.isPresent()) {
                    com.github.app.dify.knowledgebase.domain.VectorDatabase db = defaultConfig.get();
                    knowledgeBase.setVectorDatabaseId(db.getId());
                    knowledgeBase.setVectorStoreType(db.getType());
                    logger.debug("找到默认向量库配置 - ID: {}, 类型: {}, 名称: {}", 
                            db.getId(), db.getType(), db.getName());
                } else {
                    // 如果没有默认配置，使用第一个启用的配置
                    com.github.app.dify.knowledgebase.domain.VectorDatabase db = enabledConfigs.get(0);
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
    
    @Override
    @Transactional
    public String generateSummary(Long knowledgeBaseId, Long modelId) {
        logger.info("开始生成知识库摘要 - 知识库ID: {}, 模型ID: {}", knowledgeBaseId, modelId);
        
        // 1. 验证知识库是否存在
        Optional<KnowledgeBase> optional = knowledgeBaseRepository.findById(knowledgeBaseId);
        if (!optional.isPresent()) {
            throw new NotFoundException("知识库不存在");
        }
        
        KnowledgeBase knowledgeBase = optional.get();
        
        // 检查是否已删除
        if (knowledgeBase.getDeleted() != null && knowledgeBase.getDeleted() == 1) {
            throw new NotFoundException("知识库已删除");
        }
        
        // 2. 检查是否有文档
        Long documentCount = documentRepository.countByKnowledgeBaseId(knowledgeBaseId);
        if (documentCount == null || documentCount == 0) {
            throw new BusinessException("知识库中没有文档，无法生成摘要。请先上传文档。", ErrorCode.BAD_REQUEST);
        }
        
        // 3. 检查是否有已成功向量化的文档
        Long successDocumentCount = documentRepository.countSuccessDocumentsByKnowledgeBaseId(knowledgeBaseId);
        if (successDocumentCount == null || successDocumentCount == 0) {
            throw new BusinessException("知识库中没有已成功向量化的文档，无法生成摘要。请等待文档向量化完成后再试。", ErrorCode.BAD_REQUEST);
        }
        
        // 4. 从向量数据库中检索代表性文档片段
        // 使用多个查询词来获取更全面的内容
        List<String> queryTerms = new ArrayList<>();
        queryTerms.add("概述");
        queryTerms.add("介绍");
        queryTerms.add("主要内容");
        queryTerms.add("总结");
        
        // 如果知识库有描述，也作为查询词
        if (knowledgeBase.getDescription() != null && !knowledgeBase.getDescription().trim().isEmpty()) {
            queryTerms.add(knowledgeBase.getDescription());
        }
        
        // 收集所有检索到的文档片段
        List<RagRetrievalService.RetrievalResult> allResults = new ArrayList<>();
        for (String query : queryTerms) {
            try {
                List<RagRetrievalService.RetrievalResult> results = ragRetrievalService.retrieve(
                    knowledgeBaseId, 
                    query, 
                    knowledgeBase.getEmbeddingModelId(), 
                    knowledgeBase.getTopK() != null ? knowledgeBase.getTopK() : 10
                );
                allResults.addAll(results);
            } catch (Exception e) {
                logger.warn("检索文档片段失败 - 查询词: {}, 错误: {}", query, e.getMessage());
            }
        }
        
        // 如果没有检索到结果，尝试使用知识库名称作为查询词
        if (allResults.isEmpty()) {
            try {
                allResults = ragRetrievalService.retrieve(
                    knowledgeBaseId, 
                    knowledgeBase.getName(), 
                    knowledgeBase.getEmbeddingModelId(), 
                    knowledgeBase.getTopK() != null ? knowledgeBase.getTopK() : 10
                );
            } catch (Exception e) {
                logger.warn("使用知识库名称检索文档片段失败 - 错误: {}", e.getMessage());
            }
        }
        
        if (allResults.isEmpty()) {
            throw new BusinessException("无法从知识库中检索到文档内容，请确保文档已成功向量化", ErrorCode.OPERATION_FAILED);
        }
        
        // 5. 去重并合并文档片段（按相似度排序，取前20个）
        List<String> uniqueTexts = allResults.stream()
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore())) // 按相似度降序排序
            .map(RagRetrievalService.RetrievalResult::getText)
            .distinct()
            .limit(20)
            .collect(Collectors.toList());
        
        // 6. 构建用于生成摘要的文本内容
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("知识库名称：").append(knowledgeBase.getName()).append("\n\n");
        if (knowledgeBase.getDescription() != null && !knowledgeBase.getDescription().trim().isEmpty()) {
            contentBuilder.append("知识库描述：").append(knowledgeBase.getDescription()).append("\n\n");
        }
        contentBuilder.append("知识库中的文档内容片段：\n\n");
        for (int i = 0; i < uniqueTexts.size(); i++) {
            contentBuilder.append("片段").append(i + 1).append("：\n");
            contentBuilder.append(uniqueTexts.get(i)).append("\n\n");
        }
        
        String content = contentBuilder.toString();
        
        // 限制内容长度（避免超过模型上下文限制）
        int maxContentLength = 8000; // 保留一些空间给提示词和响应
        if (content.length() > maxContentLength) {
            content = content.substring(0, maxContentLength) + "...";
        }
        
        // 7. 获取问答模型
        QAModel qaModel;
        try {
            if (modelId != null) {
                qaModel = modelConfigService.getQAModelById(modelId);
            } else {
                qaModel = modelConfigService.getDefaultQAModelForRAG();
            }
        } catch (Exception e) {
            logger.error("获取问答模型失败，使用默认模型 - modelId: {}", modelId, e);
            qaModel = modelConfigService.getDefaultQAModelForRAG();
        }
        
        if (qaModel == null) {
            throw new NotFoundException("未找到可用的问答模型，请先配置模型");
        }
        
        // 8. 创建 LLM 模型
        ChatLanguageModel chatModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
        
        String systemPrompt = SkillLoader.loadSkill("document_summary_system_prompt");
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
            systemPrompt = "你是一个专业的文档摘要生成助手。请根据提供的知识库信息，生成一段简洁、准确、全面的摘要。摘要应该：\n" +
                    "1. 概括知识库的主要内容和主题\n" +
                    "2. 突出知识库的核心知识点\n" +
                    "3. 语言简洁明了，控制在200字以内\n" +
                    "4. 使用中文回答";
        }
        
        String userPrompt = "请为以下知识库生成智能摘要：\n\n" + content;
        
        // 10. 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPrompt));
        
        // 11. 调用 LLM 生成摘要
        try {
            Response<AiMessage> response = chatModel.generate(messages);
            String summary = response.content().text();
            
            // 清理摘要（移除可能的标记和多余空白）
            summary = summary.trim();
            if (summary.startsWith("摘要：") || summary.startsWith("摘要:")) {
                summary = summary.substring(3).trim();
            }
            
            // 12. 保存摘要到数据库
            knowledgeBase.setSummary(summary);
            KnowledgeBaseDateTimeUtil.setUpdateTime(knowledgeBase);
            knowledgeBaseRepository.save(knowledgeBase);
            
            logger.info("知识库摘要生成成功 - 知识库ID: {}, 摘要长度: {}", knowledgeBaseId, summary.length());
            
            return summary;
        } catch (Exception e) {
            logger.error("生成知识库摘要失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new BusinessException("生成摘要失败", ErrorCode.OPERATION_FAILED, e);
        }
    }
}
