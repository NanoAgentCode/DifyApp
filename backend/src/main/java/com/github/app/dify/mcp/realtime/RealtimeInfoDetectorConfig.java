package com.github.app.dify.mcp.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 实时信息检测配置
 */
@Configuration
@ConfigurationProperties(prefix = "mcp.realtime-info-detector")
public class RealtimeInfoDetectorConfig {

    /**
     * 检测置信度阈值（0.0-1.0）
     */
    private double confidenceThreshold = 0.5;

    /**
     * 是否启用智能检测
     */
    private boolean enabled = true;

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
