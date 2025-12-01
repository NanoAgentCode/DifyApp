package com.github.app.dify.service;

import com.github.app.dify.domain.EmbeddingModel;
import com.github.app.dify.domain.QAModel;
import com.github.app.dify.repository.EmbeddingModelRepository;
import com.github.app.dify.repository.QAModelRepository;
import com.github.app.dify.req.ModelConfigRequest;
import com.github.app.dify.req.TestModelConnectionRequest;
import com.github.app.dify.resp.EmbeddingModelResp;
import com.github.app.dify.resp.ModelConfigResponse;
import com.github.app.dify.resp.QAModelResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 模型配置服务
 */
@Service
public class ModelConfigService {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelConfigService.class);
    
    @Autowired
    private QAModelRepository qaModelRepository;
    
    @Autowired
    private EmbeddingModelRepository embeddingModelRepository;
    
    /**
     * 获取所有模型配置
     */
    @Cacheable(value = "modelConfig", key = "'all'")
    public ModelConfigResponse getModelConfig() {
        ModelConfigResponse response = new ModelConfigResponse();
        
        // 获取问答模型列表
        List<QAModel> qaModels = qaModelRepository.findAllActive();
        List<QAModelResp> qaModelResps = qaModels.stream()
                .map(this::convertToQAModelResp)
                .collect(Collectors.toList());
        response.setQaModels(qaModelResps);
        
        // 获取向量化模型列表
        List<EmbeddingModel> embeddingModels = embeddingModelRepository.findAllActive();
        List<EmbeddingModelResp> embeddingModelResps = embeddingModels.stream()
                .map(this::convertToEmbeddingModelResp)
                .collect(Collectors.toList());
        response.setEmbeddingModels(embeddingModelResps);
        
        return response;
    }
    
    /**
     * 获取可用的问答模型列表（根据使用场景）
     */
    public List<QAModelResp> getAvailableQAModels(String useFor) {
        List<QAModel> models = qaModelRepository.findByUseFor(useFor);
        return models.stream()
                .map(this::convertToQAModelResp)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取向量化模型（根据ID，如果为null则返回默认模型）
     */
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
        
        throw new RuntimeException("没有可用的向量化模型，请在管理端配置向量化模型");
    }
    
    /**
     * 获取问答模型（根据ID）
     */
    @Cacheable(value = "qaModel", key = "#modelId")
    public QAModel getQAModelById(Long modelId) {
        Optional<QAModel> optional = qaModelRepository.findById(modelId);
        if (!optional.isPresent()) {
            throw new RuntimeException("问答模型不存在: " + modelId);
        }
        
        QAModel model = optional.get();
        if (model.getDeleted() != null && model.getDeleted() == 1) {
            throw new RuntimeException("问答模型已删除: " + modelId);
        }
        
        if (model.getEnabled() == null || !model.getEnabled()) {
            throw new RuntimeException("问答模型未启用: " + modelId);
        }
        
        return model;
    }
    
    /**
     * 获取默认的RAG问答模型
     */
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
        
        throw new RuntimeException("没有可用的RAG问答模型，请在管理端大模型管理页面配置问答模型（使用场景选择'知识库问答'或'两者'）");
    }
    
    /**
     * 更新模型配置
     */
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
            throw new RuntimeException("不支持的操作类型: " + action);
        }
    }
    
    /**
     * 添加模型
     */
    private Object addModel(ModelConfigRequest request, String type) {
        ModelConfigRequest.ModelInfo modelInfo = request.getModel();
        Date now = new Date();
        
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
            qaModel.setIsDefault(false);
            qaModel.setCreateTime(now);
            qaModel.setUpdateTime(now);
            qaModel.setDeleted(0);
            
            qaModel = qaModelRepository.save(qaModel);
            return convertToQAModelResp(qaModel);
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
            embeddingModel.setCreateTime(now);
            embeddingModel.setUpdateTime(now);
            embeddingModel.setDeleted(0);
            
            embeddingModel = embeddingModelRepository.save(embeddingModel);
            return convertToEmbeddingModelResp(embeddingModel);
        } else {
            throw new RuntimeException("不支持的模型类型: " + type);
        }
    }
    
    /**
     * 更新模型
     */
    private Object updateModel(ModelConfigRequest request, String type) {
        ModelConfigRequest.ModelInfo modelInfo = request.getModel();
        Date now = new Date();
        
        if ("qa".equals(type) || type == null) {
            // 更新问答模型
            Optional<QAModel> optional = qaModelRepository.findById(modelInfo.getId());
            if (!optional.isPresent()) {
                throw new RuntimeException("问答模型不存在: " + modelInfo.getId());
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
            qaModel.setUpdateTime(now);
            
            qaModel = qaModelRepository.save(qaModel);
            return convertToQAModelResp(qaModel);
        } else if ("embedding".equals(type)) {
            // 更新向量化模型
            Optional<EmbeddingModel> optional = embeddingModelRepository.findById(modelInfo.getId());
            if (!optional.isPresent()) {
                throw new RuntimeException("向量化模型不存在: " + modelInfo.getId());
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
            embeddingModel.setUpdateTime(now);
            
            embeddingModel = embeddingModelRepository.save(embeddingModel);
            return convertToEmbeddingModelResp(embeddingModel);
        } else {
            throw new RuntimeException("不支持的模型类型: " + type);
        }
    }
    
    /**
     * 删除模型
     */
    private void deleteModel(Long modelId, String type) {
        Date now = new Date();
        
        if ("qa".equals(type) || type == null) {
            Optional<QAModel> optional = qaModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new RuntimeException("问答模型不存在: " + modelId);
            }
            
            QAModel qaModel = optional.get();
            if (qaModel.getIsDefault() != null && qaModel.getIsDefault()) {
                throw new RuntimeException("不能删除默认模型");
            }
            
            qaModel.setDeleted(1);
            qaModel.setUpdateTime(now);
            qaModelRepository.save(qaModel);
        } else if ("embedding".equals(type)) {
            Optional<EmbeddingModel> optional = embeddingModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new RuntimeException("向量化模型不存在: " + modelId);
            }
            
            EmbeddingModel embeddingModel = optional.get();
            if (embeddingModel.getIsDefault() != null && embeddingModel.getIsDefault()) {
                throw new RuntimeException("不能删除默认模型");
            }
            
            embeddingModel.setDeleted(1);
            embeddingModel.setUpdateTime(now);
            embeddingModelRepository.save(embeddingModel);
        } else {
            throw new RuntimeException("不支持的模型类型: " + type);
        }
    }
    
    /**
     * 设置默认模型
     */
    private void setDefaultModel(Long modelId, String type, String useFor) {
        Date now = new Date();
        
        if ("qa".equals(type) || type == null) {
            // 设置问答模型为默认
            Optional<QAModel> optional = qaModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new RuntimeException("问答模型不存在: " + modelId);
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
                model.setUpdateTime(now);
                qaModelRepository.save(model);
            }
            
            qaModel.setIsDefault(true);
            qaModel.setUpdateTime(now);
            qaModelRepository.save(qaModel);
        } else if ("embedding".equals(type)) {
            // 设置向量化模型为默认
            Optional<EmbeddingModel> optional = embeddingModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new RuntimeException("向量化模型不存在: " + modelId);
            }
            
            EmbeddingModel embeddingModel = optional.get();
            
            // 清除其他默认模型
            List<EmbeddingModel> defaultModels = embeddingModelRepository.findAllActive().stream()
                    .filter(m -> m.getIsDefault() != null && m.getIsDefault()
                            && !m.getId().equals(modelId))
                    .collect(Collectors.toList());
            
            for (EmbeddingModel model : defaultModels) {
                model.setIsDefault(false);
                model.setUpdateTime(now);
                embeddingModelRepository.save(model);
            }
            
            embeddingModel.setIsDefault(true);
            embeddingModel.setUpdateTime(now);
            embeddingModelRepository.save(embeddingModel);
        } else {
            throw new RuntimeException("不支持的模型类型: " + type);
        }
    }
    
    /**
     * 切换启用状态
     */
    private void toggleEnabled(Long modelId, String type, Boolean enabled) {
        Date now = new Date();
        
        if ("qa".equals(type) || type == null) {
            Optional<QAModel> optional = qaModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new RuntimeException("问答模型不存在: " + modelId);
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
                    newDefault.setUpdateTime(now);
                    qaModelRepository.save(newDefault);
                }
            }
            
            qaModel.setEnabled(enabled);
            qaModel.setUpdateTime(now);
            qaModelRepository.save(qaModel);
        } else if ("embedding".equals(type)) {
            Optional<EmbeddingModel> optional = embeddingModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new RuntimeException("向量化模型不存在: " + modelId);
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
                    newDefault.setUpdateTime(now);
                    embeddingModelRepository.save(newDefault);
                }
            }
            
            embeddingModel.setEnabled(enabled);
            embeddingModel.setUpdateTime(now);
            embeddingModelRepository.save(embeddingModel);
        } else {
            throw new RuntimeException("不支持的模型类型: " + type);
        }
    }
    
    /**
     * 测试模型连接
     */
    public void testModelConnection(TestModelConnectionRequest request) {
        // 这里可以调用实际的模型API进行测试
        // 暂时只做基本验证
        if (request.getApiUrl() == null || request.getApiUrl().trim().isEmpty()) {
            throw new RuntimeException("API 地址不能为空");
        }
        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new RuntimeException("模型标识不能为空");
        }
        
        // TODO: 实现实际的连接测试逻辑
        logger.info("测试模型连接: type={}, provider={}, apiUrl={}, model={}", 
                request.getType(), request.getProvider(), request.getApiUrl(), request.getModel());
    }
    
    /**
     * 转换为问答模型响应
     */
    private QAModelResp convertToQAModelResp(QAModel qaModel) {
        QAModelResp resp = new QAModelResp();
        BeanUtils.copyProperties(qaModel, resp);
        return resp;
    }
    
    /**
     * 转换为向量化模型响应
     */
    private EmbeddingModelResp convertToEmbeddingModelResp(EmbeddingModel embeddingModel) {
        EmbeddingModelResp resp = new EmbeddingModelResp();
        BeanUtils.copyProperties(embeddingModel, resp);
        return resp;
    }
}

