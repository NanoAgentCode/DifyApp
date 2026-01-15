package com.github.app.dify.model.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.knowledgebase.domain.EmbeddingModel;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.repository.EmbeddingModelRepository;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import com.github.app.dify.model.req.ModelConfigRequest;
import com.github.app.dify.model.req.TestModelConnectionRequest;
import com.github.app.dify.knowledgebase.resp.EmbeddingModelResp;
import com.github.app.dify.model.resp.ModelConfigResponse;
import com.github.app.dify.knowledgebase.resp.QAModelResp;
import com.github.app.dify.model.service.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import com.github.app.dify.model.util.ModelConverterUtil;
import com.github.app.dify.model.util.ModelDateTimeUtil;
import com.github.app.dify.model.util.ModelWebClientUtil;
import com.github.app.dify.common.util.ErrorUtil;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 模型配置服务实现
 */
@Service
public class ModelConfigServiceImpl implements ModelConfigService {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelConfigServiceImpl.class);
    
    @Autowired
    private QAModelRepository qaModelRepository;
    
    @Autowired
    private EmbeddingModelRepository embeddingModelRepository;
    
    @Override
    @Cacheable(value = "modelConfig", key = "'all'")
    public ModelConfigResponse getModelConfig() {
        ModelConfigResponse response = new ModelConfigResponse();
        
        // 获取问答模型列表
        List<QAModel> qaModels = qaModelRepository.findAllActive();
        List<QAModelResp> qaModelResps = qaModels.stream()
                .map(ModelConverterUtil::convertToQAModelResp)
                .collect(Collectors.toList());
        response.setQaModels(qaModelResps);
        
        // 获取向量化模型列表
        List<EmbeddingModel> embeddingModels = embeddingModelRepository.findAllActive();
        List<EmbeddingModelResp> embeddingModelResps = embeddingModels.stream()
                .map(ModelConverterUtil::convertToEmbeddingModelResp)
                .collect(Collectors.toList());
        response.setEmbeddingModels(embeddingModelResps);
        
        return response;
    }
    
    @Override
    public List<QAModelResp> getAvailableQAModels(String useFor) {
        List<QAModel> models = qaModelRepository.findByUseFor(useFor);
        return models.stream()
                .map(ModelConverterUtil::convertToQAModelResp)
                .collect(Collectors.toList());
    }
    
    @Override
    @Cacheable(value = "embeddingModel", key = "#modelId != null ? #modelId : 'default'")
    public EmbeddingModel getEmbeddingModelById(Long modelId) {
        if (modelId != null) {
            Optional<EmbeddingModel> optional = embeddingModelRepository.findById(modelId);
            if (optional.isPresent()) {
                EmbeddingModel model = optional.get();
                if (model.getDeleted() == null || model.getDeleted() == 0) {
                    return model;
                }
            }
        }
        
        // 如果没有指定模型ID或模型不存在，返回默认模型
        Optional<EmbeddingModel> defaultModel = embeddingModelRepository.findDefaultEnabled();
        if (defaultModel.isPresent()) {
            return defaultModel.get();
        }
        
        // 如果连默认模型都没有，返回第一个启用的模型
        List<EmbeddingModel> enabledModels = embeddingModelRepository.findAllEnabled();
        if (!enabledModels.isEmpty()) {
            return enabledModels.get(0);
        }
        
        throw new BusinessException("没有可用的向量化模型，请在管理端配置向量化模型", ErrorCode.MODEL_NOT_FOUND);
    }
    
    @Override
    @Cacheable(value = "qaModel", key = "#modelId")
    public QAModel getQAModelById(Long modelId) {
        Optional<QAModel> optional = qaModelRepository.findById(modelId);
        if (!optional.isPresent()) {
            throw new NotFoundException("问答模型不存在");
        }
        
        QAModel model = optional.get();
        if (model.getDeleted() != null && model.getDeleted() == 1) {
            throw new NotFoundException("问答模型已删除");
        }
        
        if (model.getEnabled() == null || !model.getEnabled()) {
            throw new BusinessException("问答模型未启用", ErrorCode.MODEL_NOT_FOUND);
        }
        
        return model;
    }
    
    @Override
    @Cacheable(value = "qaModel", key = "'default:rag'")
    public QAModel getDefaultQAModelForRAG() {
        // 先尝试获取默认的RAG模型
        Optional<QAModel> defaultRAGModel = qaModelRepository.findDefaultByUseFor("rag");
        if (defaultRAGModel.isPresent()) {
            return defaultRAGModel.get();
        }
        
        // 如果没有默认的RAG模型，尝试获取默认的both模型
        Optional<QAModel> defaultBothModel = qaModelRepository.findDefaultByUseFor("both");
        if (defaultBothModel.isPresent()) {
            return defaultBothModel.get();
        }
        
        // 如果都没有，返回第一个启用的RAG或both模型
        List<QAModel> ragModels = qaModelRepository.findByUseFor("rag");
        if (!ragModels.isEmpty()) {
            return ragModels.get(0);
        }
        
        List<QAModel> bothModels = qaModelRepository.findByUseFor("both");
        if (!bothModels.isEmpty()) {
            return bothModels.get(0);
        }
        
        throw new BusinessException("没有可用的RAG问答模型，请在管理端大模型管理页面配置问答模型（使用场景选择'知识库问答'或'两者'）", ErrorCode.MODEL_NOT_FOUND);
    }
    
    @Override
    @Transactional
    @CacheEvict(value = {"modelConfig", "qaModel", "embeddingModel"}, allEntries = true)
    public Object updateModelConfig(ModelConfigRequest request) {
        String action = request.getAction();
        String type = request.getType();
        
        if ("add".equals(action)) {
            return addModel(request, type);
        } else if ("update".equals(action)) {
            return updateModel(request, type);
        } else if ("delete".equals(action)) {
            deleteModel(request.getModelId(), type);
            return null;
        } else if ("setDefault".equals(action)) {
            setDefaultModel(request.getModelId(), type, request.getUseFor());
            return null;
        } else if ("toggleEnabled".equals(action)) {
            toggleEnabled(request.getModelId(), type, request.getEnabled());
            return null;
        } else {
            throw new BusinessException("不支持的操作类型", ErrorCode.BAD_REQUEST);
        }
    }
    
    @Override
    public void testModelConnection(TestModelConnectionRequest request) {
        // 这里可以调用实际的模型API进行测试
        // 暂时只做基本验证
        if (request.getApiUrl() == null || request.getApiUrl().trim().isEmpty()) {
            throw new BusinessException("API 地址不能为空", ErrorCode.API_CONFIG_ERROR);
        }
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new BusinessException("模型标识不能为空", ErrorCode.API_CONFIG_ERROR);
        }
        
        // 实现实际的连接测试逻辑
        logger.info("测试模型连接: type={}, provider={}, apiUrl={}, model={}", 
                request.getType(), request.getProvider(), request.getApiUrl(), request.getModel());
        
        try {
            // 构建测试请求
            String apiUrl = request.getApiUrl().trim();
            if (apiUrl.endsWith("/")) {
                apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
            }
            
            // 根据模型类型和提供商类型发送测试请求
            if ("embedding".equalsIgnoreCase(request.getType())) {
                // Embedding 模型测试
                if ("openai".equalsIgnoreCase(request.getProvider()) || "vllm".equalsIgnoreCase(request.getProvider())) {
                    // OpenAI 兼容 API 测试（使用 embeddings 端点）
                    testOpenAIEmbeddingConnection(apiUrl, request.getApiKey(), request.getModel());
                } else {
                    // 默认尝试 OpenAI 兼容 API
                    testOpenAIEmbeddingConnection(apiUrl, request.getApiKey(), request.getModel());
                }
            } else {
                // QA 模型测试
                if ("openai".equalsIgnoreCase(request.getProvider()) || "vllm".equalsIgnoreCase(request.getProvider())) {
                    // OpenAI 兼容 API 测试（使用 chat/completions 端点）
                    testOpenAICompatibleConnection(apiUrl, request.getApiKey(), request.getModel());
                } else if ("ollama".equalsIgnoreCase(request.getProvider())) {
                    // Ollama API 测试
                    testOllamaConnection(apiUrl, request.getModel());
                } else {
                    // 默认尝试 OpenAI 兼容 API
                    testOpenAICompatibleConnection(apiUrl, request.getApiKey(), request.getModel());
                }
            }
            
            logger.info("模型连接测试成功 - type={}, provider={}, apiUrl={}, model={}", 
                    request.getType(), request.getProvider(), request.getApiUrl(), request.getModel());
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            // 如果错误消息已经包含友好的提示，直接使用
            if (errorMessage != null && errorMessage.contains("模型") && 
                (errorMessage.contains("不可用") || errorMessage.contains("no available") || 
                 errorMessage.contains("not found") || errorMessage.contains("不存在"))) {
                // 已经是友好的错误消息
            } else {
                // 提取更友好的错误消息
                if (e.getCause() instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                    org.springframework.web.reactive.function.client.WebClientResponseException webClientEx = 
                        (org.springframework.web.reactive.function.client.WebClientResponseException) e.getCause();
                    String errorBody = webClientEx.getResponseBodyAsString();
                    errorMessage = ErrorUtil.extractErrorMessage(errorBody, webClientEx.getStatusCode());
                }
            }
            
            logger.error("模型连接测试失败 - type={}, provider={}, apiUrl={}, model={}, 错误: {}", 
                    request.getType(), request.getProvider(), request.getApiUrl(), request.getModel(), errorMessage);
            throw new BusinessException(errorMessage != null ? errorMessage : "模型连接测试失败", ErrorCode.API_CALL_FAILED, e);
        }
    }
    
    /**
     * 添加模型
     */
    private Object addModel(ModelConfigRequest request, String type) {
        ModelConfigRequest.ModelInfo modelInfo = request.getModel();
        
        if ("qa".equals(type) || type == null) {
            // 添加问答模型
            QAModel qaModel = new QAModel();
            qaModel.setName(modelInfo.getName());
            qaModel.setProvider(modelInfo.getProvider());
            qaModel.setProviderType(modelInfo.getProviderType());
            qaModel.setApiUrl(modelInfo.getApiUrl());
            qaModel.setApiKey(modelInfo.getApiKey());
            qaModel.setModel(modelInfo.getModel());
            qaModel.setUseFor(modelInfo.getUseFor() != null ? modelInfo.getUseFor() : "both");
            qaModel.setEnabled(modelInfo.getEnabled() != null ? modelInfo.getEnabled() : true);
            
            // 动态判断是否支持多模态和视觉输入
            if (modelInfo.getSupportsMultimodal() != null) {
                qaModel.setSupportsMultimodal(modelInfo.getSupportsMultimodal());
            } else {
                qaModel.setSupportsMultimodal(detectSupportsMultimodal(modelInfo.getModel(), modelInfo.getName()));
            }
            
            if (modelInfo.getSupportsVision() != null) {
                qaModel.setSupportsVision(modelInfo.getSupportsVision());
            } else {
                qaModel.setSupportsVision(detectSupportsVision(modelInfo.getModel(), modelInfo.getName()));
            }
            qaModel.setIsDefault(false);
            ModelDateTimeUtil.setCreateAndUpdateTime(qaModel);
            qaModel.setDeleted(0);
            
            qaModel = qaModelRepository.save(qaModel);
            return ModelConverterUtil.convertToQAModelResp(qaModel);
        } else if ("embedding".equals(type)) {
            // 添加向量化模型
            EmbeddingModel embeddingModel = new EmbeddingModel();
            embeddingModel.setName(modelInfo.getName());
            embeddingModel.setProvider(modelInfo.getProvider());
            embeddingModel.setProviderType(modelInfo.getProviderType());
            embeddingModel.setApiUrl(modelInfo.getApiUrl());
            embeddingModel.setApiKey(modelInfo.getApiKey());
            embeddingModel.setModel(modelInfo.getModel());
            embeddingModel.setTimeout(modelInfo.getTimeout() != null ? modelInfo.getTimeout() : 300000);
            embeddingModel.setBatchSize(modelInfo.getBatchSize() != null ? modelInfo.getBatchSize() : 100);
            embeddingModel.setEnabled(modelInfo.getEnabled() != null ? modelInfo.getEnabled() : true);
            embeddingModel.setIsDefault(false);
            ModelDateTimeUtil.setCreateAndUpdateTime(embeddingModel);
            embeddingModel.setDeleted(0);
            
            embeddingModel = embeddingModelRepository.save(embeddingModel);
            return ModelConverterUtil.convertToEmbeddingModelResp(embeddingModel);
        } else {
            throw new BusinessException("不支持的模型类型", ErrorCode.BAD_REQUEST);
        }
    }
    
    /**
     * 更新模型
     */
    private Object updateModel(ModelConfigRequest request, String type) {
        ModelConfigRequest.ModelInfo modelInfo = request.getModel();
        
        if ("qa".equals(type) || type == null) {
            // 更新问答模型
            Optional<QAModel> optional = qaModelRepository.findById(modelInfo.getId());
            if (!optional.isPresent()) {
                throw new NotFoundException("问答模型不存在");
            }
            
            QAModel qaModel = optional.get();
            qaModel.setName(modelInfo.getName());
            qaModel.setProvider(modelInfo.getProvider());
            qaModel.setProviderType(modelInfo.getProviderType());
            qaModel.setApiUrl(modelInfo.getApiUrl());
            qaModel.setApiKey(modelInfo.getApiKey());
            qaModel.setModel(modelInfo.getModel());
            if (modelInfo.getUseFor() != null) {
                qaModel.setUseFor(modelInfo.getUseFor());
            }
            if (modelInfo.getEnabled() != null) {
                qaModel.setEnabled(modelInfo.getEnabled());
            }
            
            // 动态判断是否支持多模态和视觉输入（如果用户没有明确指定，则根据模型名称自动判断）
            if (modelInfo.getSupportsMultimodal() != null) {
                qaModel.setSupportsMultimodal(modelInfo.getSupportsMultimodal());
            } else {
                // 如果模型名称或标识发生变化，重新检测
                qaModel.setSupportsMultimodal(detectSupportsMultimodal(qaModel.getModel(), qaModel.getName()));
            }
            
            if (modelInfo.getSupportsVision() != null) {
                qaModel.setSupportsVision(modelInfo.getSupportsVision());
            } else {
                // 如果模型名称或标识发生变化，重新检测
                qaModel.setSupportsVision(detectSupportsVision(qaModel.getModel(), qaModel.getName()));
            }
            ModelDateTimeUtil.setUpdateTime(qaModel);
            
            qaModel = qaModelRepository.save(qaModel);
            return ModelConverterUtil.convertToQAModelResp(qaModel);
        } else if ("embedding".equals(type)) {
            // 更新向量化模型
            Optional<EmbeddingModel> optional = embeddingModelRepository.findById(modelInfo.getId());
            if (!optional.isPresent()) {
                throw new NotFoundException("向量化模型不存在");
            }
            
            EmbeddingModel embeddingModel = optional.get();
            embeddingModel.setName(modelInfo.getName());
            embeddingModel.setProvider(modelInfo.getProvider());
            embeddingModel.setProviderType(modelInfo.getProviderType());
            embeddingModel.setApiUrl(modelInfo.getApiUrl());
            embeddingModel.setApiKey(modelInfo.getApiKey());
            embeddingModel.setModel(modelInfo.getModel());
            if (modelInfo.getTimeout() != null) {
                embeddingModel.setTimeout(modelInfo.getTimeout());
            }
            if (modelInfo.getBatchSize() != null) {
                embeddingModel.setBatchSize(modelInfo.getBatchSize());
            }
            if (modelInfo.getEnabled() != null) {
                embeddingModel.setEnabled(modelInfo.getEnabled());
            }
            ModelDateTimeUtil.setUpdateTime(embeddingModel);
            
            embeddingModel = embeddingModelRepository.save(embeddingModel);
            return ModelConverterUtil.convertToEmbeddingModelResp(embeddingModel);
        } else {
            throw new BusinessException("不支持的模型类型", ErrorCode.BAD_REQUEST);
        }
    }
    
    /**
     * 删除模型
     */
    private void deleteModel(Long modelId, String type) {
        if ("qa".equals(type) || type == null) {
            Optional<QAModel> optional = qaModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new NotFoundException("问答模型不存在");
            }
            
            QAModel qaModel = optional.get();
            if (qaModel.getIsDefault() != null && qaModel.getIsDefault()) {
                throw new BusinessException("不能删除默认模型", ErrorCode.BAD_REQUEST);
            }
            
            qaModel.setDeleted(1);
            ModelDateTimeUtil.setUpdateTime(qaModel);
            qaModelRepository.save(qaModel);
        } else if ("embedding".equals(type)) {
            Optional<EmbeddingModel> optional = embeddingModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new NotFoundException("向量化模型不存在");
            }
            
            EmbeddingModel embeddingModel = optional.get();
            if (embeddingModel.getIsDefault() != null && embeddingModel.getIsDefault()) {
                throw new BusinessException("不能删除默认模型", ErrorCode.BAD_REQUEST);
            }
            
            embeddingModel.setDeleted(1);
            ModelDateTimeUtil.setUpdateTime(embeddingModel);
            embeddingModelRepository.save(embeddingModel);
        } else {
            throw new BusinessException("不支持的模型类型", ErrorCode.BAD_REQUEST);
        }
    }
    
    /**
     * 设置默认模型
     */
    private void setDefaultModel(Long modelId, String type, String useFor) {
        if ("qa".equals(type) || type == null) {
            // 设置问答模型为默认
            Optional<QAModel> optional = qaModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new NotFoundException("问答模型不存在");
            }
            
            QAModel qaModel = optional.get();
            
            // 清除同一使用场景下的其他默认模型
            List<QAModel> defaultModels;
            if (useFor != null && !"both".equals(useFor)) {
                // 清除该使用场景下的默认模型
                defaultModels = qaModelRepository.findAllActive().stream()
                        .filter(m -> (m.getUseFor().equals(useFor) || "both".equals(m.getUseFor()))
                                && m.getIsDefault() != null && m.getIsDefault()
                                && !m.getId().equals(modelId))
                        .collect(Collectors.toList());
            } else {
                // 清除所有默认模型
                defaultModels = qaModelRepository.findAllActive().stream()
                        .filter(m -> m.getIsDefault() != null && m.getIsDefault()
                                && !m.getId().equals(modelId))
                        .collect(Collectors.toList());
            }
            
            for (QAModel model : defaultModels) {
                model.setIsDefault(false);
                ModelDateTimeUtil.setUpdateTime(model);
                qaModelRepository.save(model);
            }
            
            qaModel.setIsDefault(true);
            ModelDateTimeUtil.setUpdateTime(qaModel);
            qaModelRepository.save(qaModel);
        } else if ("embedding".equals(type)) {
            // 设置向量化模型为默认
            Optional<EmbeddingModel> optional = embeddingModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new NotFoundException("向量化模型不存在");
            }
            
            EmbeddingModel embeddingModel = optional.get();
            
            // 清除其他默认模型
            List<EmbeddingModel> defaultModels = embeddingModelRepository.findAllActive().stream()
                    .filter(m -> m.getIsDefault() != null && m.getIsDefault()
                            && !m.getId().equals(modelId))
                    .collect(Collectors.toList());
            
            for (EmbeddingModel model : defaultModels) {
                model.setIsDefault(false);
                ModelDateTimeUtil.setUpdateTime(model);
                embeddingModelRepository.save(model);
            }
            
            embeddingModel.setIsDefault(true);
            ModelDateTimeUtil.setUpdateTime(embeddingModel);
            embeddingModelRepository.save(embeddingModel);
        } else {
            throw new BusinessException("不支持的模型类型", ErrorCode.BAD_REQUEST);
        }
    }
    
    /**
     * 切换启用状态
     */
    private void toggleEnabled(Long modelId, String type, Boolean enabled) {
        if ("qa".equals(type) || type == null) {
            Optional<QAModel> optional = qaModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new NotFoundException("问答模型不存在");
            }
            
            QAModel qaModel = optional.get();
            
            // 如果禁用的是默认模型，需要取消其默认状态
            if (!enabled && qaModel.getIsDefault() != null && qaModel.getIsDefault()) {
                qaModel.setIsDefault(false);
                // 尝试自动选择另一个启用的模型作为默认（同一使用场景）
                String useFor = qaModel.getUseFor();
                List<QAModel> candidates = qaModelRepository.findAllActive().stream()
                        .filter(m -> !m.getId().equals(modelId) 
                                && m.getEnabled() != null && m.getEnabled()
                                && (m.getUseFor().equals(useFor) || "both".equals(m.getUseFor()) || "both".equals(useFor)))
                        .collect(Collectors.toList());
                
                if (!candidates.isEmpty()) {
                    // 选择第一个启用的模型作为默认
                    QAModel newDefault = candidates.get(0);
                    newDefault.setIsDefault(true);
                    ModelDateTimeUtil.setUpdateTime(newDefault);
                    qaModelRepository.save(newDefault);
                }
            }
            
            qaModel.setEnabled(enabled);
            ModelDateTimeUtil.setUpdateTime(qaModel);
            qaModelRepository.save(qaModel);
        } else if ("embedding".equals(type)) {
            Optional<EmbeddingModel> optional = embeddingModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new NotFoundException("向量化模型不存在");
            }
            
            EmbeddingModel embeddingModel = optional.get();
            
            // 如果禁用的是默认模型，需要取消其默认状态
            if (!enabled && embeddingModel.getIsDefault() != null && embeddingModel.getIsDefault()) {
                embeddingModel.setIsDefault(false);
                // 尝试自动选择另一个启用的模型作为默认
                List<EmbeddingModel> candidates = embeddingModelRepository.findAllActive().stream()
                        .filter(m -> !m.getId().equals(modelId) 
                                && m.getEnabled() != null && m.getEnabled())
                        .collect(Collectors.toList());
                
                if (!candidates.isEmpty()) {
                    // 选择第一个启用的模型作为默认
                    EmbeddingModel newDefault = candidates.get(0);
                    newDefault.setIsDefault(true);
                    ModelDateTimeUtil.setUpdateTime(newDefault);
                    embeddingModelRepository.save(newDefault);
                }
            }
            
            embeddingModel.setEnabled(enabled);
            ModelDateTimeUtil.setUpdateTime(embeddingModel);
            embeddingModelRepository.save(embeddingModel);
        } else {
            throw new BusinessException("不支持的模型类型", ErrorCode.BAD_REQUEST);
        }
    }
    
    /**
     * 根据模型名称和标识检测是否支持多模态
     * 
     * @param model 模型标识（如：Qwen/Qwen3-VL-235B-A22B-Instruct）
     * @param name 模型名称
     * @return true表示支持多模态，false表示不支持
     */
    private boolean detectSupportsMultimodal(String model, String name) {
        if (model == null && name == null) {
            return false;
        }
        
        String modelLower = model != null ? model.toLowerCase() : "";
        String nameLower = name != null ? name.toLowerCase() : "";
        
        // 检测常见的多模态模型标识
        return modelLower.contains("qwen-vl") 
            || modelLower.contains("qwen2-vl")
            || modelLower.contains("qwen3-vl")
            || modelLower.contains("gpt-4-vision")
            || modelLower.contains("gpt-4o")
            || modelLower.contains("claude-3")
            || modelLower.contains("claude-3.5")
            || modelLower.contains("gemini-pro-vision")
            || modelLower.contains("gemini-1.5")
            || modelLower.contains("gemini-2.0")
            || nameLower.contains("qwen-vl")
            || nameLower.contains("qwen2-vl")
            || nameLower.contains("qwen3-vl")
            || nameLower.contains("视觉")
            || nameLower.contains("vision")
            || nameLower.contains("multimodal");
    }
    
    /**
     * 根据模型名称和标识检测是否支持视觉输入
     * 
     * @param model 模型标识（如：Qwen/Qwen3-VL-235B-A22B-Instruct）
     * @param name 模型名称
     * @return true表示支持视觉输入，false表示不支持
     */
    private boolean detectSupportsVision(String model, String name) {
        // 视觉输入需要同时支持多模态
        return detectSupportsMultimodal(model, name);
    }
    
    
    /**
     * 测试 OpenAI 兼容 Embedding API 连接
     */
    private void testOpenAIEmbeddingConnection(String apiUrl, String apiKey, String model) {
        // 处理完整 URL（包含路径）的情况
        String baseUrl;
        String path;
        
        if (apiUrl.contains("/v1/embeddings")) {
            // 如果 URL 已经包含完整路径，分离基础 URL 和路径
            int pathIndex = apiUrl.indexOf("/v1/");
            baseUrl = apiUrl.substring(0, pathIndex);
            path = apiUrl.substring(pathIndex);
        } else if (apiUrl.contains("/api/") || apiUrl.contains("/v1/")) {
            // 如果包含其他路径，尝试提取基础 URL
            int pathIndex = Math.max(apiUrl.indexOf("/api/"), apiUrl.indexOf("/v1/"));
            baseUrl = apiUrl.substring(0, pathIndex);
            path = "/v1/embeddings"; // 使用 embeddings 端点
        } else {
            // 基础 URL，需要添加路径
            baseUrl = apiUrl;
            path = "/v1/embeddings";
        }
        
        WebClient.Builder builder = ModelWebClientUtil.createBuilder(baseUrl);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }
        WebClient webClient = builder.build();
        
        // 构建 embedding 请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("input", "test"); // 测试用的输入文本
        
        WebClient.RequestBodySpec requestSpec = webClient.post()
                .uri(path);
        
        try {
            String response = requestSpec
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            // 验证响应是否有效
            if (response == null || response.trim().isEmpty()) {
                throw new BusinessException("API返回空响应", ErrorCode.API_CALL_FAILED);
            }
            
            logger.debug("测试 embedding 连接成功，响应: {}", response.length() > 200 ? response.substring(0, 200) + "..." : response);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            // 获取更详细的错误信息
            String errorBody = e.getResponseBodyAsString();
            String errorMessage = ErrorUtil.extractErrorMessage(errorBody, e.getStatusCode());
            
            logger.error("Embedding API返回错误响应，状态码: {}, 错误消息: {}", e.getStatusCode(), errorMessage);
            throw new BusinessException(errorMessage, ErrorCode.API_CALL_FAILED, e);
        }
    }
    
    /**
     * 测试 OpenAI 兼容 API 连接（用于 QA 模型）
     */
    private void testOpenAICompatibleConnection(String apiUrl, String apiKey, String model) {
        // 处理完整 URL（包含路径）的情况
        String baseUrl;
        String path;
        
        if (apiUrl.contains("/v1/chat/completions")) {
            // 如果 URL 已经包含完整路径，分离基础 URL 和路径
            int pathIndex = apiUrl.indexOf("/v1/");
            baseUrl = apiUrl.substring(0, pathIndex);
            path = apiUrl.substring(pathIndex);
        } else if (apiUrl.contains("/api/") || apiUrl.contains("/v1/")) {
            // 如果包含其他路径，尝试提取基础 URL
            int pathIndex = Math.max(apiUrl.indexOf("/api/"), apiUrl.indexOf("/v1/"));
            baseUrl = apiUrl.substring(0, pathIndex);
            path = "/v1/chat/completions"; // 使用标准路径
        } else {
            // 基础 URL，需要添加路径
            baseUrl = apiUrl;
            path = "/v1/chat/completions";
        }
        
        WebClient.Builder builder = ModelWebClientUtil.createBuilder(baseUrl);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }
        WebClient webClient = builder.build();
        
        // 构建完整的请求体，与实际调用保持一致
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        
        // 构建消息列表
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", "test");
        requestBody.put("messages", Collections.singletonList(message));
        
        // 添加标准参数（与buildRequestBody保持一致）
        requestBody.put("stream", false);
        requestBody.put("max_tokens", 10); // 测试时使用较小的值，但不要太小
        requestBody.put("temperature", 0.7);
        
        WebClient.RequestBodySpec requestSpec = webClient.post()
                .uri(path);
        
        try {
            String response = requestSpec
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            // 验证响应是否有效
            if (response == null || response.trim().isEmpty()) {
                throw new BusinessException("API返回空响应", ErrorCode.API_CALL_FAILED);
            }
            
            logger.debug("测试连接成功，响应: {}", response.length() > 200 ? response.substring(0, 200) + "..." : response);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            // 获取更详细的错误信息
            String errorBody = e.getResponseBodyAsString();
            String errorMessage = ErrorUtil.extractErrorMessage(errorBody, e.getStatusCode());
            
            logger.error("API返回错误响应，状态码: {}, 错误消息: {}", e.getStatusCode(), errorMessage);
            throw new BusinessException(errorMessage, ErrorCode.API_CALL_FAILED, e);
        }
    }
    
    
    /**
     * 测试 Ollama API 连接
     */
    private void testOllamaConnection(String apiUrl, String model) {
        WebClient webClient = ModelWebClientUtil.createBuilder(apiUrl).build();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", "test");
        requestBody.put("stream", false);
        
        webClient.post()
                .uri("/api/generate")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }
}
