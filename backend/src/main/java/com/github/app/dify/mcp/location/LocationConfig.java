package com.github.app.dify.mcp.location;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 地理位置服务配置
 */
@Configuration
@ConfigurationProperties(prefix = "mcp.location")
public class LocationConfig {

    /**
     * 是否启用地理位置信息注入
     */
    private boolean enabled = false;

    /**
     * 默认地理位置描述（如 "中国"、"北京"）；为空时使用时间服务的默认时区作为描述
     */
    private String defaultDescription = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultDescription() {
        return defaultDescription;
    }

    public void setDefaultDescription(String defaultDescription) {
        this.defaultDescription = defaultDescription;
    }
}
