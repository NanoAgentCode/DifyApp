package com.github.app.dify.analytics.analysis.resp;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "GraphRAG 响应")
public class GraphRAGResp {

    @Schema(description = "答案")
    private String answer;

    @Schema(description = "是否使用LLM生成")
    private Boolean llmGenerated;

    @Schema(description = "使用的模型ID")
    private Long modelId;

    @Schema(description = "使用的模型名称")
    private String modelName;

    @Schema(description = "图谱召回命中数量")
    private Integer graphHitCount;

    @Schema(description = "答案引用是否通过校验")
    private Boolean citationValid;

    @Schema(description = "识别到的图谱实体")
    private List<Map<String, Object>> recognizedEntities;

    @Schema(description = "图谱召回结果")
    private List<Map<String, Object>> graphSources;

    @Schema(description = "降级或提示消息")
    private String message;

    @Schema(description = "错误码或降级码")
    private String errorCode;

    @Schema(description = "降级原因")
    private String fallbackReason;

    @Schema(description = "GraphRAG 可观测指标")
    private Map<String, Object> metrics;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Boolean getLlmGenerated() {
        return llmGenerated;
    }

    public void setLlmGenerated(Boolean llmGenerated) {
        this.llmGenerated = llmGenerated;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Integer getGraphHitCount() {
        return graphHitCount;
    }

    public void setGraphHitCount(Integer graphHitCount) {
        this.graphHitCount = graphHitCount;
    }

    public Boolean getCitationValid() {
        return citationValid;
    }

    public void setCitationValid(Boolean citationValid) {
        this.citationValid = citationValid;
    }

    public List<Map<String, Object>> getRecognizedEntities() {
        return recognizedEntities;
    }

    public void setRecognizedEntities(List<Map<String, Object>> recognizedEntities) {
        this.recognizedEntities = recognizedEntities;
    }

    public List<Map<String, Object>> getGraphSources() {
        return graphSources;
    }

    public void setGraphSources(List<Map<String, Object>> graphSources) {
        this.graphSources = graphSources;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }
}
