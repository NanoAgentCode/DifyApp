package com.github.app.dify.service;

import com.github.app.dify.domain.AiApp;
import com.github.app.dify.repository.AiAppRepository;
import com.github.app.dify.req.CreateAiAppReq;
import com.github.app.dify.req.DifyChatRequest;
import com.github.app.dify.req.DifyWorkflowRequest;
import com.github.app.dify.req.UpdateAiAppReq;
import com.github.app.dify.resp.AiAppResp;
import com.github.app.dify.resp.DifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI应用服务
 */
@Service
public class AiAppService {
    
    private static final Logger logger = LoggerFactory.getLogger(AiAppService.class);
    
    @Autowired
    private AiAppRepository aiAppRepository;
    
    @Autowired
    private DifyApiClient difyApiClient;
    
    /**
     * 创建AI应用
     */
    @Transactional
    public AiAppResp createAiApp(CreateAiAppReq req) {
        // 检查API Key是否已存在
        if (aiAppRepository.existsByAppId(req.getAppId())) {
            throw new RuntimeException("API Key已存在: " + req.getAppId());
        }
        
        AiApp aiApp = new AiApp();
        BeanUtils.copyProperties(req, aiApp);
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
        
        aiApp = aiAppRepository.save(aiApp);
        
        return convertToResp(aiApp);
    }
    
    /**
     * 更新AI应用
     */
    @Transactional
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
        if (req.getStatus() != null) {
            aiApp.setStatus(req.getStatus());
        }
        if (req.getApiBaseUrl() != null) {
            aiApp.setApiBaseUrl(req.getApiBaseUrl());
        }
        if (req.getStreamEnabled() != null) {
            aiApp.setStreamEnabled(req.getStreamEnabled());
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
        }
        
        aiApp.setUpdateTime(new Date());
        aiApp = aiAppRepository.save(aiApp);
        
        return convertToResp(aiApp);
    }
    
    /**
     * 根据ID获取AI应用
     */
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
    public List<AiAppResp> listAiApps(Integer tenantId, Integer type, Integer status) {
        List<AiApp> apps;
        
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
        
        return apps.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }
    
    /**
     * 调用Chat Flow
     */
    public Mono<DifyResponse> chat(Long appId, DifyChatRequest request) {
        AiApp app = aiAppRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("应用不存在: " + appId));
        
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new RuntimeException("应用已禁用");
        }
        
        if (app.getType() == null || app.getType() != 1) {
            throw new RuntimeException("应用类型不是Chat Flow");
        }
        
        // 检查是否支持流式响应
        boolean stream = request.getStream() != null && request.getStream();
        if (stream && (app.getStreamEnabled() == null || !app.getStreamEnabled())) {
            stream = false; // 如果应用不支持流式，强制使用非流式
        }
        
        if (stream) {
            // 流式响应
            Flux<DifyResponse> flux = difyApiClient.chatStream(
                    app.getAppId(),
                    app.getApiBaseUrl(),
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
                    app.getApiBaseUrl(),
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
    public Flux<DifyResponse> chatStream(Long appId, DifyChatRequest request) {
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
        
        return difyApiClient.chatStream(
                app.getAppId(),
                app.getApiBaseUrl(),
                request.getQuery(),
                request.getConversationId(),
                request.getUserId(),
                request.getInputs()
        );
    }
    
    /**
     * 调用Workflow
     */
    public Mono<DifyResponse> workflow(Long appId, DifyWorkflowRequest request) {
        AiApp app = aiAppRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("应用不存在: " + appId));
        
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new RuntimeException("应用已禁用");
        }
        
        if (app.getType() == null || app.getType() != 2) {
            throw new RuntimeException("应用类型不是Workflow");
        }
        
        // 检查是否支持流式响应
        boolean stream = request.getStream() != null && request.getStream();
        if (stream && (app.getStreamEnabled() == null || !app.getStreamEnabled())) {
            stream = false; // 如果应用不支持流式，强制使用非流式
        }
        
        if (stream) {
            // 流式响应
            Flux<DifyResponse> flux = difyApiClient.workflowStream(
                    app.getAppId(),
                    app.getApiBaseUrl(),
                    request.getUserId(),
                    request.getInputs()
            );
            // 转换为Mono，返回最后一个响应
            return flux.last();
        } else {
            // 非流式响应
            return difyApiClient.workflow(
                    app.getAppId(),
                    app.getApiBaseUrl(),
                    request.getUserId(),
                    request.getInputs()
            );
        }
    }
    
    /**
     * 调用Workflow（流式）
     */
    public Flux<DifyResponse> workflowStream(Long appId, DifyWorkflowRequest request) {
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
        
        return difyApiClient.workflowStream(
                app.getAppId(),
                app.getApiBaseUrl(),
                request.getUserId(),
                request.getInputs()
        );
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

