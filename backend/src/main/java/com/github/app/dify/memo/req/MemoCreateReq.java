package com.github.app.dify.memo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "创建备忘录请求（自然语言）")
public class MemoCreateReq {

    @NotBlank(message = "内容不能为空")
    @Schema(description = "自然语言描述，如：30分钟后提醒我开会、明天9点提醒吃药", requiredMode = Schema.RequiredMode.REQUIRED)
    private String rawInput;

    public String getRawInput() {
        return rawInput;
    }

    public void setRawInput(String rawInput) {
        this.rawInput = rawInput;
    }
}
