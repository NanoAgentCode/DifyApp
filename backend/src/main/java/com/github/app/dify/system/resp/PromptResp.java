package com.github.app.dify.system.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

/**
 * 提示词响应
 */
@Schema(description = "提示词响应")
public class PromptResp {
    
    @Schema(description = "提示词编号")
    private Long id;
    
    @Schema(description = "提示词标题")
    private String title;
    
    @Schema(description = "提示词正文")
    private String content;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新时间")
    private Date updateTime;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
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
