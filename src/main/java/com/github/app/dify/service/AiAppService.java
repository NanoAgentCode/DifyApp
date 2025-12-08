package com.github.app.dify.service;

import com.github.app.dify.req.ChatFlowRequest;
import com.github.app.dify.req.CreateAiAppReq;
import com.github.app.dify.req.UpdateAiAppReq;
import com.github.app.dify.req.WorkFlowRequest;
import com.github.app.dify.resp.AiAppResp;
import com.github.app.dify.resp.DifyResponse;
import com.github.app.dify.resp.PageResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * AI应用服务接口
 */
public interface AiAppService {
    
    /**
     * 创建AI应用
     */
    AiAppResp createAiApp(CreateAiAppReq req);
    
    /**
     * 更新AI应用
     */
    AiAppResp updateAiApp(Long id, UpdateAiAppReq req);
    
    /**
     * 根据ID获取AI应用
     */
    AiAppResp getAiAppById(Long id);
    
    /**
     * 根据API Key获取AI应用
     */
    AiAppResp getAiAppByApiKey(String apiKey);
    
    /**
     * 删除AI应用
     */
    void deleteAiApp(Long id);
    
    /**
     * 获取AI应用列表
     */
    List<AiAppResp> listAiApps(Integer tenantId, Integer type, Integer status, String keyword);
    
    /**
     * 分页获取AI应用列表
     */
    PageResponse<AiAppResp> listAiAppsWithPagination(
            Integer tenantId, Integer type, Integer status, String keyword, 
            Integer page, Integer pageSize);
    
    /**
     * 获取用户可见的AI应用列表
     */
    List<AiAppResp> listVisibleAppsForUser(
            Long userId, Integer tenantId, Integer type, Integer status, String keyword);
    
    /**
     * 聊天（非流式）
     */
    Mono<DifyResponse> chat(Long appId, ChatFlowRequest request);
    
    /**
     * 聊天（流式）
     */
    Flux<DifyResponse> chatStream(Long appId, ChatFlowRequest request);
    
    /**
     * 工作流（非流式）
     */
    Mono<DifyResponse> workflow(Long appId, WorkFlowRequest request);
    
    /**
     * 工作流（流式）
     */
    Flux<DifyResponse> workflowStream(Long appId, WorkFlowRequest request);
    
    /**
     * 上传文件
     */
    Mono<Map<String, Object>> uploadFile(Long appId, org.springframework.web.multipart.MultipartFile file, String userId);
}
