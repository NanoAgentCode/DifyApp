package com.github.app.dify.prompt.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "更新提示词请求")
public class PromptUpdateReq {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200)
    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "内容不能为空")
    @Schema(description = "内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

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
