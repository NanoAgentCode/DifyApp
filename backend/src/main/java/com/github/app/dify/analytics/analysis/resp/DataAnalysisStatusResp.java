package com.github.app.dify.analytics.analysis.resp;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "数据分析同步状态响应")
public class DataAnalysisStatusResp {

    @Schema(description = "是否启用定时同步")
    private Boolean enabled;

    @Schema(description = "Neo4j 数据源ID")
    private Long neo4jDataSourceId;

    @Schema(description = "Neo4j 数据源名称")
    private String neo4jDataSourceName;

    @Schema(description = "同步间隔（分钟）")
    private Integer intervalMinutes;

    @Schema(description = "是否正在同步")
    private Boolean running;

    @Schema(description = "最近一次开始同步时间（毫秒时间戳）")
    private Long lastRunAtMs;

    @Schema(description = "最近一次同步成功时间（毫秒时间戳）")
    private Long lastSuccessAtMs;

    @Schema(description = "最近一次同步状态（success/failed/running/never）")
    private String lastStatus;

    @Schema(description = "最近一次同步消息")
    private String lastMessage;

    @Schema(description = "最近一次同步耗时（毫秒）")
    private Long lastDurationMs;

    @Schema(description = "最近一次同步指标")
    private Map<String, Object> metrics;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getNeo4jDataSourceId() {
        return neo4jDataSourceId;
    }

    public void setNeo4jDataSourceId(Long neo4jDataSourceId) {
        this.neo4jDataSourceId = neo4jDataSourceId;
    }

    public String getNeo4jDataSourceName() {
        return neo4jDataSourceName;
    }

    public void setNeo4jDataSourceName(String neo4jDataSourceName) {
        this.neo4jDataSourceName = neo4jDataSourceName;
    }

    public Integer getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(Integer intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    public Boolean getRunning() {
        return running;
    }

    public void setRunning(Boolean running) {
        this.running = running;
    }

    public Long getLastRunAtMs() {
        return lastRunAtMs;
    }

    public void setLastRunAtMs(Long lastRunAtMs) {
        this.lastRunAtMs = lastRunAtMs;
    }

    public Long getLastSuccessAtMs() {
        return lastSuccessAtMs;
    }

    public void setLastSuccessAtMs(Long lastSuccessAtMs) {
        this.lastSuccessAtMs = lastSuccessAtMs;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Long getLastDurationMs() {
        return lastDurationMs;
    }

    public void setLastDurationMs(Long lastDurationMs) {
        this.lastDurationMs = lastDurationMs;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }
}
