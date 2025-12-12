package com.github.app.dify.system.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新提示词请求
 */
@Schema(description = "更新提示词请求")
public class UpdatePromptReq {
    
    @Size(max = 200, message = "提示词标题长度不能超过200")
    @Schema(description = "提示词标题")
    private String title;
    
    @Schema(description = "提示词正文")
    private String content;
    
    // Getters and Setters
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
}
