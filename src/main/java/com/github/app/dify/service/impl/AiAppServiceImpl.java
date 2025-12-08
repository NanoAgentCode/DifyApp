package com.github.app.dify.service.impl;

import com.github.app.dify.domain.AiApp;
import com.github.app.dify.domain.UserAppVisibility;
import com.github.app.dify.repository.AiAppRepository;
import com.github.app.dify.repository.UserAppVisibilityRepository;
import com.github.app.dify.req.ChatFlowRequest;
import com.github.app.dify.req.CreateAiAppReq;
import com.github.app.dify.req.UpdateAiAppReq;
import com.github.app.dify.req.WorkFlowRequest;
import com.github.app.dify.resp.AiAppResp;
import com.github.app.dify.resp.DifyResponse;
import com.github.app.dify.resp.PageResponse;
import com.github.app.dify.service.AiAppService;
import com.github.app.dify.service.DifyApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
/**
 * AI应用服务
 */
@Service
public class AiAppServiceImpl implements AiAppService {
    
    private static final Logger logger = LoggerFactory.getLogger(AiAppServiceImpl.class);
    
    @Autowired
    private AiAppRepository aiAppRepository;
    
    @Autowired
    private DifyApiClient difyApiClient;
    
    @Autowired
    private UserAppVisibilityRepository userAppVisibilityRepository;
    
    /**
     * 创建AI应用
     */
    @Transactional
    @CacheEvict(value = "aiApp", allEntries = true)
    @Override
    public AiAppResp createAiApp(CreateAiAppReq req) {
        // 检查API Key是否已存在
        if (aiAppRepository.existsByAppId(req.getAppId())) {
            throw new RuntimeException("API Key已存在: " + req.getAppId());
        }
        
        AiApp aiApp = new AiApp();
        BeanUtils.copyProperties(req, aiApp);
        
        // 确保API Key被正确设置（显式设置，避免BeanUtils复制问题）
        if (req.getAppId() != null) {
            aiApp.setAppId(req.getAppId());
        }
        
        aiApp.setStatus(1); // 默认启用
        aiApp.setDeleted(0); // 默认未删除
        aiApp.setCreateTime(new Date());
        aiApp.setUpdateTime(new Date());
        
        // 如果没有设置API Base URL，使用默认值
        if (aiApp.getApiBaseUrl() == null || aiApp.getApiBaseUrl().isEmpty()) {
            aiApp.setApiBaseUrl(null); // 使用默认配置
        }
        
        // 如果没有设置流式响应，默认为false
        if (aiApp.getStreamEnabled() == null) {
            aiApp.setStreamEnabled(false);
        }
        
        // 如果没有设置文件上传，默认为false
        if (aiApp.getFileUploadEnabled() == null) {
            aiApp.setFileUploadEnabled(false);
        }
        
        // 如果没有设置输入框显示，默认为true（默认显示）
        if (aiApp.getInputEnabled() == null) {
            aiApp.setInputEnabled(true);
        }
        
        // 确保 inputs 字段被正确设置（即使为 null 也要显式设置）
        if (req.getInputs() != null) {
            aiApp.setInputs(req.getInputs());
            logger.info("设置 inputs 字段，长度: {}", req.getInputs().length());
        } else {
            aiApp.setInputs(null);
            logger.info("inputs 字段为 null，使用默认值");
        }
        
        // 记录保存前的数据
        logger.info("创建应用 - 名称: {}, API Key: {}, API Base URL: {}, Inputs: {}", 
                aiApp.getName(), aiApp.getAppId(), aiApp.getApiBaseUrl(),
                aiApp.getInputs() != null ? "已设置(" + aiApp.getInputs().length() + "字符)" : "null");
        
        aiApp = aiAppRepository.save(aiApp);
        
        // 记录保存后的数据
        logger.info("应用创建成功 - ID: {}, API Key: {}", aiApp.getId(), aiApp.getAppId());
        
        return convertToResp(aiApp);
    }
    
    /**
     * 更新AI应用
     */
    @Transactional
    @CacheEvict(value = "aiApp", key = "#id")
    @Override
    public AiAppResp updateAiApp(Long id, UpdateAiAppReq req) {
        Optional<AiApp> optional = aiAppRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("应用不存在: " + id);
        }
        
        AiApp aiApp = optional.get();
        
        // 更新字段
        if (req.getName() != null) {
            aiApp.setName(req.getName());
        }
        if (req.getDescription() != null) {
            aiApp.setDescription(req.getDescription());
        }
        if (req.getType() != null) {
            aiApp.setType(req.getType());
        }
        if (req.getStatus() != null) {
            aiApp.setStatus(req.getStatus());
        }
        if (req.getApiBaseUrl() != null) {
            aiApp.setApiBaseUrl(req.getApiBaseUrl());
        }
        if (req.getStreamEnabled() != null) {
            aiApp.setStreamEnabled(req.getStreamEnabled());
        }
        if (req.getFileUploadEnabled() != null) {
            aiApp.setFileUploadEnabled(req.getFileUploadEnabled());
        }
        if (req.getInputEnabled() != null) {
            aiApp.setInputEnabled(req.getInputEnabled());
        }
        if (req.getIcon() != null) {
            aiApp.setIcon(req.getIcon());
        }
        if (req.getSort() != null) {
            aiApp.setSort(req.getSort());
        }
        if (req.getThemeColor() != null) {
            aiApp.setThemeColor(req.getThemeColor());
        }
        if (req.getInputs() != null) {
            aiApp.setInputs(req.getInputs());
            logger.info("更新 inputs 字段，长度: {}", req.getInputs().length());
        } else {
            // 如果请求中 inputs 为 null，保持原值不变（不更新）
            logger.info("inputs 字段为 null，保持原值不变");
        }
        
        aiApp.setUpdateTime(new Date());
        aiApp = aiAppRepository.save(aiApp);
        
        // 记录保存后的数据
        logger.info("应用更新成功 - ID: {}, Inputs: {}", 
                aiApp.getId(), 
                aiApp.getInputs() != null ? "已设置(" + aiApp.getInputs().length() + "字符)" : "null");
        
        return convertToResp(aiApp);
    }
    
    /**
     * 根据ID获取AI应用
     */
    @Cacheable(value = "aiApp", key = "#id")
    @Override
    public AiAppResp getAiAppById(Long id) {
        Optional<AiApp> optional = aiAppRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("应用不存在: " + id);
        }
        return convertToResp(optional.get());
    }
    
    /**
     * 根据API Key获取AI应用
     */
    @Cacheable(value = "aiApp", key = "'apikey:' + #apiKey")
    @Override
    public AiAppResp getAiAppByApiKey(String apiKey) {
        Optional<AiApp> optional = aiAppRepository.findByAppId(apiKey);
        if (!optional.isPresent()) {
            throw new RuntimeException("应用不存在: " + apiKey);
        }
        return convertToResp(optional.get());
    }
    
    /**
     * 删除AI应用
     */
    @Transactional
    @CacheEvict(value = "aiApp", key = "#id")
    @Override
    public void deleteAiApp(Long id) {
        Optional<AiApp> optional = aiAppRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("应用不存在: " + id);
        }
        
        AiApp aiApp = optional.get();
        aiApp.setDeleted(1);
        aiApp.setUpdateTime(new Date());
        aiAppRepository.save(aiApp);
    }
    
    /**
     * 获取应用列表
     */
    @Override
    public List<AiAppResp> listAiApps(Integer tenantId, Integer type, Integer status, String keyword) {
        List<AiApp> apps;
        
        // 如果有关键词，使用搜索方法
        if (keyword != null && !keyword.trim().isEmpty()) {
            apps = aiAppRepository.searchByKeywordAndFilters(keyword.trim(), type, status);
        } else {
            // 否则使用原有的查询方法
            if (tenantId != null && type != null && status != null) {
                apps = aiAppRepository.findByTenantIdAndTypeAndStatus(tenantId, type, status);
            } else if (tenantId != null && type != null) {
                apps = aiAppRepository.findByTenantIdAndType(tenantId, type);
            } else if (tenantId != null && status != null) {
                apps = aiAppRepository.findByTenantIdAndStatus(tenantId, status);
            } else if (tenantId != null) {
                apps = aiAppRepository.findByTenantId(tenantId);
            } else if (type != null) {
                apps = aiAppRepository.findByType(type);
            } else if (status != null) {
                apps = aiAppRepository.findByStatus(status);
            } else {
                apps = aiAppRepository.findAll();
            }
            
            // 过滤已删除的应用
            apps = apps.stream()
                    .filter(app -> app.getDeleted() == null || app.getDeleted() == 0)
                    .collect(Collectors.toList());
        }
        
        return apps.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取应用列表（分页）
     */
    @Override
    public PageResponse<AiAppResp> listAiAppsWithPagination(
            Integer tenantId, Integer type, Integer status, String keyword, Integer page, Integer pageSize) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page - 1, pageSize, org.springframework.data.domain.Sort.by("createTime").descending());
        
        org.springframework.data.domain.Page<AiApp> appPage;
        
        // 如果有关键词，使用搜索方法
        if (keyword != null && !keyword.trim().isEmpty()) {
            appPage = aiAppRepository.searchByKeywordAndFiltersWithPagination(
                    keyword.trim(), type, status, pageable);
        } else {
            // 否则使用分页查询方法
            appPage = aiAppRepository.findByFiltersWithPagination(type, status, pageable);
        }
        
        // 过滤已删除的应用并转换
        List<AiAppResp> content = appPage.getContent().stream()
                .filter(app -> app.getDeleted() == null || app.getDeleted() == 0)
                .map(this::convertToResp)
                .collect(Collectors.toList());
        
        // 如果过滤了已删除的应用，需要重新计算总数
        long total = appPage.getTotalElements();
        if (appPage.getContent().size() != content.size()) {
            // 有应用被过滤，需要重新计算总数（排除已删除的）
            // 查询所有数据并过滤已删除的来计算总数
            org.springframework.data.domain.Page<AiApp> allPage = (keyword != null && !keyword.trim().isEmpty()) 
                ? aiAppRepository.searchByKeywordAndFiltersWithPagination(
                    keyword.trim(), type, status, 
                    org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE))
                : aiAppRepository.findByFiltersWithPagination(
                    type, status, 
                    org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE));
            
            total = allPage.getContent().stream()
                    .filter(app -> app.getDeleted() == null || app.getDeleted() == 0)
                    .count();
        }
        
        return new com.github.app.dify.resp.PageResponse<>(content, total, page, pageSize);
    }
    
    /**
     * 获取用户可见的应用列表
     * @param userId 用户ID
     * @param tenantId 租户ID（可选）
     * @param type 应用类型（可选）
     * @param status 应用状态（可选）
     * @param keyword 搜索关键词（可选）
     * @return 可见的应用列表
     */
    @Override
    public List<AiAppResp> listVisibleAppsForUser(Long userId, Integer tenantId, Integer type, Integer status, String keyword) {
        // 先获取所有符合条件的应用
        List<AiAppResp> allApps = listAiApps(tenantId, type, status, keyword);
        
        // 如果用户是管理员，返回所有应用
        // 这里需要从User表中查询用户角色，为了简化，我们假设管理员可以看到所有应用
        // 实际应该从request中获取用户角色
        
        // 获取用户的可见性设置
        List<UserAppVisibility> visibilities = 
                userAppVisibilityRepository.findByUserId(userId);
        
        // 如果没有设置可见性，默认所有应用都可见
        if (visibilities.isEmpty()) {
            return allApps;
        }
        
        // 过滤出用户可见的应用
        return allApps.stream()
                .filter(app -> {
                    // 查找该应用的可见性设置
                    Optional<UserAppVisibility> visibility = visibilities.stream()
                            .filter(v -> v.getAppId().equals(app.getId()))
                            .findFirst();
                    
                    // 如果设置了可见性，使用设置的值；否则默认可见
                    return visibility.map(UserAppVisibility::getVisible)
                            .orElse(true);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 调用Chat Flow
     */
    @Override
    public Mono<DifyResponse> chat(Long appId, ChatFlowRequest request) {
        AiApp app = aiAppRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("应用不存在: " + appId));
        
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new RuntimeException("应用已禁用");
        }
        
        if (app.getType() == null || app.getType() != 1) {
            throw new RuntimeException("应用类型不是Chat Flow");
        }
        
        // 验证并修复API Base URL
        String apiBaseUrl = validateAndFixApiBaseUrl(app.getApiBaseUrl());
        
        // 检查是否支持流式响应
        boolean stream = request.getStream() != null && request.getStream();
        if (stream && (app.getStreamEnabled() == null || !app.getStreamEnabled())) {
            stream = false; // 如果应用不支持流式，强制使用非流式
        }
        
        if (stream) {
            // 流式响应
            Flux<DifyResponse> flux = difyApiClient.chatStream(
                    app.getAppId(),
                    apiBaseUrl,
                    request.getQuery(),
                    request.getConversationId(),
                    request.getUserId(),
                    request.getInputs()
            );
            // 转换为Mono，返回最后一个响应
            return flux.last();
        } else {
            // 非流式响应
            return difyApiClient.chat(
                    app.getAppId(),
                    apiBaseUrl,
                    request.getQuery(),
                    request.getConversationId(),
                    request.getUserId(),
                    request.getInputs()
            );
        }
    }
    
    /**
     * 调用Chat Flow（流式）
     */
    @Override
    public Flux<DifyResponse> chatStream(Long appId, ChatFlowRequest request) {
        AiApp app = aiAppRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("应用不存在: " + appId));
        
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new RuntimeException("应用已禁用");
        }
        
        if (app.getType() == null || app.getType() != 1) {
            throw new RuntimeException("应用类型不是Chat Flow");
        }
        
        // 检查是否支持流式响应
        if (app.getStreamEnabled() == null || !app.getStreamEnabled()) {
            throw new RuntimeException("应用不支持流式响应");
        }
        
        // 验证并修复API Base URL
        String apiBaseUrl = validateAndFixApiBaseUrl(app.getApiBaseUrl());
        
        return difyApiClient.chatStream(
                app.getAppId(),
                apiBaseUrl,
                request.getQuery(),
                request.getConversationId(),
                request.getUserId(),
                request.getInputs()
        );
    }
    
    /**
     * 调用Workflow
     */
    @Override
    public Mono<DifyResponse> workflow(Long appId, WorkFlowRequest request) {
        AiApp app = aiAppRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("应用不存在: " + appId));
        
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new RuntimeException("应用已禁用");
        }
        
        if (app.getType() == null || app.getType() != 2) {
            throw new RuntimeException("应用类型不是Workflow");
        }
        
        // 验证并修复API Base URL
        String apiBaseUrl = validateAndFixApiBaseUrl(app.getApiBaseUrl());
        
        // 记录应用信息，用于调试
        logger.info("调用Workflow - 应用ID: {}, 应用名称: {}, 应用类型: {}, API Key: {}, API Base URL: {}", 
                appId, app.getName(), app.getType(), 
                app.getAppId() != null ? app.getAppId().substring(0, Math.min(10, app.getAppId().length())) + "..." : "null",
                apiBaseUrl);
        
        // 如果收到 not_workflow_app 错误，提供更详细的诊断信息
        logger.info("诊断信息 - 数据库中的应用类型: {}, API Key前10位: {}, 请确认Dify控制台中该API Key对应的应用是否为Workflow类型", 
                app.getType(), 
                app.getAppId() != null ? app.getAppId().substring(0, Math.min(10, app.getAppId().length())) : "null");
        
        // 确定响应模式
        String responseMode = request.getResponseMode();
        boolean stream = false;
        
        if (responseMode != null && !responseMode.trim().isEmpty()) {
            // 如果请求中指定了 response_mode，使用请求中的值
            stream = "streaming".equalsIgnoreCase(responseMode.trim());
        } else {
            // 否则根据 stream 字段和应用配置决定
            stream = request.getStream() != null && request.getStream();
        }
        
        // 如果应用不支持流式，强制使用非流式
        if (stream && (app.getStreamEnabled() == null || !app.getStreamEnabled())) {
            stream = false;
            logger.warn("应用不支持流式响应，已切换到阻塞模式");
        }
        
        if (stream) {
            // 流式响应
            Flux<DifyResponse> flux = difyApiClient.workflowStream(
                    app.getAppId(),
                    apiBaseUrl,
                    request.getUserId(),
                    request.getInputs(),
                    request.getFiles(),
                    request.getTraceId()
            );
            // 转换为Mono，返回最后一个响应
            return flux.last();
        } else {
            // 非流式响应
            return difyApiClient.workflow(
                    app.getAppId(),
                    apiBaseUrl,
                    request.getUserId(),
                    request.getInputs(),
                    request.getFiles(),
                    request.getTraceId()
            );
        }
    }
    
    /**
     * 调用Workflow（流式）
     */
    @Override
    public Flux<DifyResponse> workflowStream(Long appId, WorkFlowRequest request) {
        AiApp app = aiAppRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("应用不存在: " + appId));
        
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new RuntimeException("应用已禁用");
        }
        
        if (app.getType() == null || app.getType() != 2) {
            throw new RuntimeException("应用类型不是Workflow");
        }
        
        // 检查是否支持流式响应
        if (app.getStreamEnabled() == null || !app.getStreamEnabled()) {
            throw new RuntimeException("应用不支持流式响应");
        }
        
        // 验证并修复API Base URL
        String apiBaseUrl = validateAndFixApiBaseUrl(app.getApiBaseUrl());
        
        // 确定响应模式
        String responseMode = request.getResponseMode();
        if (responseMode == null || responseMode.trim().isEmpty()) {
            responseMode = "streaming"; // 默认使用流式
        }
        
        return difyApiClient.workflowStream(
                app.getAppId(),
                apiBaseUrl,
                request.getUserId(),
                request.getInputs(),
                request.getFiles(),
                request.getTraceId()
        );
    }
    
    /**
     * 验证并修复API Base URL
     * 支持Dify前端代理的情况（如80端口）
     */
    private String validateAndFixApiBaseUrl(String apiBaseUrl) {
        if (apiBaseUrl == null || apiBaseUrl.trim().isEmpty()) {
            return null; // 使用默认配置
        }
        
        String url = apiBaseUrl.trim();
        
        // 检查URL格式
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            logger.warn("API Base URL格式不正确: {}, 将使用默认配置", url);
            return null;
        }
        
        // 移除尾随斜杠
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        logger.info("使用API Base URL: {}", url);
        return url;
    }
    
    /**
     * 上传文件到Dify
     */
    @Override
    public Mono<Map<String, Object>> uploadFile(Long appId, 
                                                 org.springframework.web.multipart.MultipartFile file, 
                                                 String userId) {
        // 获取应用信息
        Optional<AiApp> optional = aiAppRepository.findById(appId);
        if (!optional.isPresent()) {
            return Mono.error(new RuntimeException("应用不存在: " + appId));
        }
        
        AiApp aiApp = optional.get();
        String apiKey = aiApp.getAppId();
        String baseUrl = aiApp.getApiBaseUrl();
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Mono.error(new RuntimeException("应用的API Key未配置"));
        }
        
        // 调用Dify API客户端上传文件
        return difyApiClient.uploadFile(apiKey, baseUrl, file, userId);
    }
    
    /**
     * 转换为响应对象
     */
    private AiAppResp convertToResp(AiApp aiApp) {
        AiAppResp resp = new AiAppResp();
        BeanUtils.copyProperties(aiApp, resp);
        return resp;
    }
}