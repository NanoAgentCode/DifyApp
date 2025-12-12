package com.github.app.dify.system.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

/**
 * DrawIO 历史记录响应
 */
@Schema(description = "DrawIO 历史记录响应")
public class DrawIOHistoryResp {
    
    @Schema(description = "历史记录ID")
    private Long id;
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "提示词内容")
    private String prompt;
    
    @Schema(description = "图表类型")
    private String diagramType;
    
    @Schema(description = "创建时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}

