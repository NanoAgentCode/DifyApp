package com.github.app.dify.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * DrawIO 保存图表请求
 */
@Schema(description = "DrawIO 保存图表请求")
public class DrawIOSaveRequest {
    
    @NotBlank(message = "图表名称不能为空")
    @Schema(description = "图表名称")
    private String name;
    
    @NotBlank(message = "图表JSON不能为空")
    @Schema(description = "图表JSON内容（X6格式）")
    private String diagramJson;
    
    @Schema(description = "图表类型")
    private String diagramType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

