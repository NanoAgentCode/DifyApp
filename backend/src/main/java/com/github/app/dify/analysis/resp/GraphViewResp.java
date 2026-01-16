package com.github.app.dify.analysis.resp;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "图数据视图")
public class GraphViewResp {

    @Schema(description = "节点列表")
    private List<GraphNodeResp> nodes;

    @Schema(description = "关系边列表")
    private List<GraphLinkResp> links;

    @Schema(description = "节点数量统计（label -> count）")
    private Map<String, Long> nodeCounts;

    @Schema(description = "关系数量统计（type -> count）")
    private Map<String, Long> relationshipCounts;

    public List<GraphNodeResp> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphNodeResp> nodes) {
        this.nodes = nodes;
    }

    public List<GraphLinkResp> getLinks() {
        return links;
    }

    public void setLinks(List<GraphLinkResp> links) {
        this.links = links;
    }

    public Map<String, Long> getNodeCounts() {
        return nodeCounts;
    }

    public void setNodeCounts(Map<String, Long> nodeCounts) {
        this.nodeCounts = nodeCounts;
    }

    public Map<String, Long> getRelationshipCounts() {
        return relationshipCounts;
    }

    public void setRelationshipCounts(Map<String, Long> relationshipCounts) {
        this.relationshipCounts = relationshipCounts;
    }
}

