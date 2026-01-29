package com.github.app.dify.analytics.analysis.resp;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "图节点")
public class GraphNodeResp {

    @Schema(description = "节点唯一ID（label:id）")
    private String id;

    @Schema(description = "节点标签")
    private String label;

    @Schema(description = "节点显示名称")
    private String name;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
