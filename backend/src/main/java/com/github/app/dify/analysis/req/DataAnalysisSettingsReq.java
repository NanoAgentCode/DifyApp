package com.github.app.dify.analysis.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "数据分析同步配置请求")
public class DataAnalysisSettingsReq {

    @Schema(description = "是否启用定时同步")
    @NotNull
    private Boolean enabled;

    @Schema(description = "Neo4j 数据源ID（数据源管理中创建的 neo4j）")
    private Long neo4jDataSourceId;

    @Schema(description = "同步间隔（分钟）")
    private Integer intervalMinutes;

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

    public Integer getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(Integer intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }
}

