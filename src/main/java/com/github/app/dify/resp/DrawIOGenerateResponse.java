package com.github.app.dify.resp;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DrawIO 生成图表响应
 */
@Schema(description = "DrawIO 生成图表响应")
public class DrawIOGenerateResponse {
    
    @Schema(description = "生成的图表JSON内容（X6格式）")
    private String diagramJson;
    
    @Schema(description = "图表类型")
    private String diagramType;

    public String getDiagramJson() {
        return diagramJson;
    }

    public void setDiagramJson(String diagramJson) {
        this.diagramJson = diagramJson;
    }

    public String getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(String diagramType) {
        this.diagramType = diagramType;
    }
}

