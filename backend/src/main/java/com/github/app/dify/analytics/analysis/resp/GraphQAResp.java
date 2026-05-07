package com.github.app.dify.analytics.analysis.resp;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "图谱问答响应")
public class GraphQAResp {

    @Schema(description = "答案")
    private String answer;

    @Schema(description = "识别到的问题意图")
    private String intent;

    @Schema(description = "命中数量")
    private Integer count;

    @Schema(description = "结果列表")
    private List<Map<String, Object>> results;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public List<Map<String, Object>> getResults() {
        return results;
    }

    public void setResults(List<Map<String, Object>> results) {
        this.results = results;
    }
}
