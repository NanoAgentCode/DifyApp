package com.github.app.dify.mcp.location;

import com.github.app.dify.mcp.time.TimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * MCP 地理位置信息服务
 * 为 LLM 提供地理位置/时区上下文，便于地域相关问答与备忘录等场景
 */
@Service
public class McpLocationService {

    private static final Logger logger = LoggerFactory.getLogger(McpLocationService.class);

    @Autowired
    private LocationConfig locationConfig;

    @Autowired
    private TimeConfig timeConfig;

    /**
     * 获取格式化的地理位置信息，用于注入系统消息
     *
     * @return 格式化字符串，未启用时返回空字符串
     */
    public String getFormattedLocationInfo() {
        if (locationConfig == null || !locationConfig.isEnabled()) {
            return "";
        }
        String desc = locationConfig.getDefaultDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            String info = "【地理位置信息（MCP）】\n" + desc.trim() + "\n";
            logger.debug("MCP 地理位置信息已注入（配置描述）");
            return info;
        }
        String timeZone = timeConfig != null ? timeConfig.getDefaultTimeZone() : null;
        if (timeZone != null && !timeZone.isEmpty()) {
            String info = "【地理位置信息（MCP）】\n默认时区：" + timeZone + "\n";
            logger.debug("MCP 地理位置信息已注入（来自时区）: {}", timeZone);
            return info;
        }
        return "";
    }
}
