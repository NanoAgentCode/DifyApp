package com.github.app.dify.mcp.location;

import com.github.app.dify.ops.trace.api.TraceFacade;
import com.github.app.dify.ops.trace.model.TraceHandle;
import com.github.app.dify.ops.trace.model.TraceStartRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${mcp.time.default-time-zone:Asia/Shanghai}")
    private String defaultTimeZone;

    @Autowired
    private TraceFacade traceFacade;

    /**
     * 获取格式化的地理位置信息，用于注入系统消息
     *
     * @return 格式化字符串，未启用时返回空字符串
     */
    public String getFormattedLocationInfo() {
        TraceHandle traceHandle = startBusinessTrace("mcp_location");
        if (locationConfig == null || !locationConfig.isEnabled()) {
            traceFacade.success(traceHandle, "disabled");
            return "";
        }
        String desc = locationConfig.getDefaultDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            String info = "【地理位置信息（MCP）】\n" + desc.trim() + "\n";
            logger.debug("MCP 地理位置信息已注入（配置描述）");
            traceFacade.success(traceHandle, "source=config_desc");
            return info;
        }
        String timeZone = defaultTimeZone;
        if (timeZone != null && !timeZone.isEmpty()) {
            String info = "【地理位置信息（MCP）】\n默认时区：" + timeZone + "\n";
            logger.debug("MCP 地理位置信息已注入（来自时区）: {}", timeZone);
            traceFacade.success(traceHandle, "source=time_zone");
            return info;
        }
        traceFacade.success(traceHandle, "empty");
        return "";
    }

    private TraceHandle startBusinessTrace(String traceSource) {
        try {
            TraceStartRequest request = new TraceStartRequest();
            request.setTraceSource(traceSource);
            request.setRequestType("mcp_location");
            request.setRequestSummary("location_info");
            return traceFacade.start(request);
        } catch (Exception e) {
            logger.debug("启动mcp location追踪失败，降级继续", e);
            return null;
        }
    }
}
