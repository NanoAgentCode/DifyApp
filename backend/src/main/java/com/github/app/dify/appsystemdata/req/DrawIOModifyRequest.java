package com.github.app.dify.appsystemdata.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * DrawIO 修改图表请求
 */
@Schema(description = "DrawIO 修改图表请求")
public class DrawIOModifyRequest {
    
    @NotBlank(message = "图表JSON不能为空")
    @Schema(description = "现有图表JSON内容（X6格式）")
    private String diagramJson;
    
    @NotBlank(message = "修改指令不能为空")
    @Schema(description = "修改指令")
    private String prompt;
    
    @Schema(description = "模型ID（可选）")
    private Long modelId;

    public String getDiagramJson() {
        return diagramJson;
    }

    public void setDiagramJson(String diagramJson) {
        this.diagramJson = diagramJson;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }
}

