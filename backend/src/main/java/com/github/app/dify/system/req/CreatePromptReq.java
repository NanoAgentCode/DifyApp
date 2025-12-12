package com.github.app.dify.system.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建提示词请求
 */
@Schema(description = "创建提示词请求")
public class CreatePromptReq {
    
    @NotBlank(message = "提示词标题不能为空")
    @Size(max = 200, message = "提示词标题长度不能超过200")
    @Schema(description = "提示词标题")
    private String title;
    
    @NotBlank(message = "提示词正文不能为空")
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
