package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

/**
 * 模型配置响应
 */
@ApiModel("模型配置响应")
public class ModelConfigResponse {
    
    @ApiModelProperty("问答模型列表")
    private List<QAModelResp> qaModels;
    
    @ApiModelProperty("向量化模型列表")
    private List<EmbeddingModelResp> embeddingModels;

    public List<QAModelResp> getQaModels() {
        return qaModels;
    }

    public void setQaModels(List<QAModelResp> qaModels) {
        this.qaModels = qaModels;
    }

    public List<EmbeddingModelResp> getEmbeddingModels() {
        return embeddingModels;
    }

    public void setEmbeddingModels(List<EmbeddingModelResp> embeddingModels) {
        this.embeddingModels = embeddingModels;
    }
}

