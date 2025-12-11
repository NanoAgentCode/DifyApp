package com.github.app.dify.appsystemdata.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

/**
 * DrawIO 图表响应
 */
@Schema(description = "DrawIO 图表响应")
public class DrawIODiagramResp {
    
    @Schema(description = "图表ID")
    private Long id;
    
    @Schema(description = "图表名称")
    private String name;
    
    @Schema(description = "图表类型")
    private String diagramType;
    
    @Schema(description = "图表JSON内容（X6格式）")
    private String diagramJson;
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新时间")
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(String diagramType) {
        this.diagramType = diagramType;
    }

    public String getDiagramJson() {
        return diagramJson;
    }

    public void setDiagramJson(String diagramJson) {
        this.diagramJson = diagramJson;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}

