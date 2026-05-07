package com.github.app.dify.analytics.analysis.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "GraphRAG 请求")
public class GraphRAGReq {

    @NotBlank(message = "问题不能为空")
    @Schema(description = "自然语言问题")
    private String question;

    @Schema(description = "图谱召回结果限制")
    private Integer limit;

    @Schema(description = "图谱召回深度，1-3")
    private Integer depth;

    @Schema(description = "问答模型ID，不传则使用默认RAG模型")
    private Long modelId;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }
}
