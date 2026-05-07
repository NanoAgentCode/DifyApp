package com.github.app.dify.analytics.analysis.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "图谱问答请求")
public class GraphQAReq {

    @NotBlank(message = "问题不能为空")
    @Schema(description = "自然语言问题")
    private String question;

    @Schema(description = "返回结果数量限制")
    private Integer limit;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
