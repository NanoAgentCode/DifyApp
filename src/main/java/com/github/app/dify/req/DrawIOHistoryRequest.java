package com.github.app.dify.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * DrawIO 历史记录请求
 */
@Schema(description = "DrawIO 历史记录请求")
public class DrawIOHistoryRequest {
    
    @NotBlank(message = "提示词不能为空")
    @Schema(description = "提示词内容")
    private String prompt;
    
    @Schema(description = "图表类型：flowchart, architecture, mindmap, sequence, uml, org, network, custom")
    private String diagramType;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(String diagramType) {
        this.diagramType = diagramType;
    }
}

