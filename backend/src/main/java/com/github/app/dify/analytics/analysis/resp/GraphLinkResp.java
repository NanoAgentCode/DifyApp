package com.github.app.dify.analytics.analysis.resp;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "图关系边")
public class GraphLinkResp {

    @Schema(description = "起点节点ID（label:id）")
    private String source;

    @Schema(description = "终点节点ID（label:id）")
    private String target;

    @Schema(description = "关系类型")
    private String type;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
