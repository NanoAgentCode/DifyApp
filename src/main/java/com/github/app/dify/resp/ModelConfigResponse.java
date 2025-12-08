package com.github.app.dify.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
/**
 * 模型配置响应
 */
@Schema(description = "模型配置响应")
public class ModelConfigResponse {
    
    @Schema(description = "问答模型列表")
    private List<QAModelResp> qaModels;
    
    @Schema(description = "向量化模型列表")
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