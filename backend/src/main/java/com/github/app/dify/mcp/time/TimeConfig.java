package com.github.app.dify.mcp.time;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 时间服务配置
 */
@Configuration
@ConfigurationProperties(prefix = "mcp.time")
public class TimeConfig {

    /**
     * 默认时区
     */
    private String defaultTimeZone = "Asia/Shanghai";

    /**
     * 时间信息缓存时间（秒）
     */
    private int cacheSeconds = 1;

    public String getDefaultTimeZone() {
        return defaultTimeZone;
    }

    public void setDefaultTimeZone(String defaultTimeZone) {
        this.defaultTimeZone = defaultTimeZone;
    }

    public int getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(int cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }
}
