package com.github.app.dify.appsystemdata.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * DrawIO 生成图表请求
 */
@Schema(description = "DrawIO 生成图表请求")
public class DrawIOGenerateRequest {
    
    @NotBlank(message = "提示词不能为空")
    @Schema(description = "自然语言描述")
    private String prompt;
    
    @Schema(description = "模型ID（可选）")
    private Long modelId;
    
    @Schema(description = "图表类型：flowchart, architecture, mindmap, sequence, uml, org, network, custom")
    private String diagramType;

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

    public String getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(String diagramType) {
        this.diagramType = diagramType;
    }
}

