package com.github.app.dify.analytics.analysis.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.analytics.analysis.req.DataAnalysisSettingsReq;
import com.github.app.dify.analytics.analysis.resp.DataAnalysisSettingsResp;
import com.github.app.dify.analytics.analysis.resp.DataAnalysisStatusResp;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.datasource.domain.DataSource;
import com.github.app.dify.datasource.service.DataSourceService;
import com.github.app.dify.system.domain.SystemConfig;
import com.github.app.dify.system.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Service
class DataAnalysisConfigService {

    static final String KEY_ENABLED = "analysis.etl.enabled";
    static final String KEY_INTERVAL_MINUTES = "analysis.etl.intervalMinutes";
    static final String KEY_NEO4J_DATASOURCE_ID = "analysis.neo4j.dataSourceId";
    static final String KEY_LAST_RUN_AT_MS = "analysis.etl.lastRunAtMs";
    static final String KEY_LAST_SUCCESS_AT_MS = "analysis.etl.lastSuccessAtMs";
    static final String KEY_LAST_STATUS = "analysis.etl.lastStatus";
    static final String KEY_LAST_MESSAGE = "analysis.etl.lastMessage";
    static final String KEY_LAST_DURATION_MS = "analysis.etl.lastDurationMs";
    static final String KEY_LAST_METRICS_JSON = "analysis.etl.lastMetricsJson";
    static final int DEFAULT_INTERVAL_MINUTES = 60;

    private static final String CONFIG_GROUP = "analysis";

    private final SystemConfigRepository systemConfigRepository;
    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;

    DataAnalysisConfigService(SystemConfigRepository systemConfigRepository,
                              DataSourceService dataSourceService,
                              ObjectMapper objectMapper) {
        this.systemConfigRepository = systemConfigRepository;
        this.dataSourceService = dataSourceService;
        this.objectMapper = objectMapper;
    }

    DataAnalysisSettingsResp getSettings() {
        DataAnalysisSettingsResp resp = new DataAnalysisSettingsResp();
        resp.setEnabled(readBoolean());
        resp.setIntervalMinutes(readIntervalMinutes());
        resp.setNeo4jDataSourceId(readLong(KEY_NEO4J_DATASOURCE_ID, null));
        return resp;
    }

    DataAnalysisSettingsResp updateSettings(DataAnalysisSettingsReq req, Long userId, String username) {
        Integer intervalMinutes = req.getIntervalMinutes();
        if (intervalMinutes != null && (intervalMinutes <= 0 || intervalMinutes > 1440)) {
            throw new BusinessException(intervalMinutes <= 0 ? "同步间隔必须大于0" : "同步间隔不能超过1440分钟", ErrorCode.BAD_REQUEST);
        }

        Long neo4jDataSourceId = req.getNeo4jDataSourceId();
        if (neo4jDataSourceId != null) {
            validateNeo4jDataSource(dataSourceService.getDataSourceEntityById(neo4jDataSourceId), "所选");
        }

        writeConfig(KEY_ENABLED, String.valueOf(Boolean.TRUE.equals(req.getEnabled())), "boolean", "是否启用定时同步", userId, username);
        if (intervalMinutes != null) {
            writeConfig(KEY_INTERVAL_MINUTES, String.valueOf(intervalMinutes), "number", "同步间隔（分钟）", userId, username);
        }
        if (neo4jDataSourceId != null) {
            writeConfig(KEY_NEO4J_DATASOURCE_ID, String.valueOf(neo4jDataSourceId), "number", "Neo4j 数据源ID", userId, username);
        } else {
            deleteConfig(KEY_NEO4J_DATASOURCE_ID);
        }
        return getSettings();
    }

    DataAnalysisStatusResp getStatus(boolean running) {
        DataAnalysisStatusResp resp = new DataAnalysisStatusResp();
        DataAnalysisSettingsResp settings = getSettings();
        resp.setEnabled(settings.getEnabled());
        resp.setNeo4jDataSourceId(settings.getNeo4jDataSourceId());
        resp.setIntervalMinutes(settings.getIntervalMinutes());
        resp.setRunning(running);
        if (settings.getNeo4jDataSourceId() != null) {
            try {
                resp.setNeo4jDataSourceName(dataSourceService.getDataSourceEntityById(settings.getNeo4jDataSourceId()).getName());
            } catch (Exception ignored) {
                resp.setNeo4jDataSourceName(null);
            }
        }
        resp.setLastRunAtMs(readLong(KEY_LAST_RUN_AT_MS, null));
        resp.setLastSuccessAtMs(readLong(KEY_LAST_SUCCESS_AT_MS, null));
        resp.setLastStatus(readString(KEY_LAST_STATUS, "never"));
        resp.setLastMessage(readString(KEY_LAST_MESSAGE, null));
        resp.setLastDurationMs(readLong(KEY_LAST_DURATION_MS, null));
        String metricsJson = readString(KEY_LAST_METRICS_JSON, null);
        if (metricsJson != null && !metricsJson.trim().isEmpty()) {
            try {
                resp.setMetrics(objectMapper.readValue(metricsJson, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignored) {
                resp.setMetrics(null);
            }
        }
        return resp;
    }

    DataSource getValidatedNeo4jDataSourceOrThrow(DataAnalysisSettingsResp settings) {
        Long id = settings.getNeo4jDataSourceId();
        if (id == null) {
            throw new BusinessException("未配置Neo4j数据源", ErrorCode.BAD_REQUEST);
        }
        DataSource dataSource = dataSourceService.getDataSourceEntityById(id);
        validateNeo4jDataSource(dataSource, "配置的");
        return dataSource;
    }

    boolean readBoolean() {
        return "true".equalsIgnoreCase(readString(KEY_ENABLED, "false").trim());
    }

    int readIntervalMinutes() {
        String value = readString(KEY_INTERVAL_MINUTES, null);
        try {
            return value == null || value.trim().isEmpty() ? DEFAULT_INTERVAL_MINUTES : Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return DEFAULT_INTERVAL_MINUTES;
        }
    }

    Long readLong(String key, Long defaultValue) {
        try {
            String value = readString(key, null);
            return value == null || value.trim().isEmpty() ? defaultValue : Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    void writeRuntimeStatus(String status, String message, Long lastRunAtMs, Long lastSuccessAtMs,
                            Long durationMs, Long userId, String username) {
        writeIfPresent(KEY_LAST_RUN_AT_MS, lastRunAtMs, "最近一次开始同步时间（毫秒）", userId, username);
        writeIfPresent(KEY_LAST_SUCCESS_AT_MS, lastSuccessAtMs, "最近一次同步成功时间（毫秒）", userId, username);
        writeIfPresent(KEY_LAST_DURATION_MS, durationMs, "最近一次同步耗时（毫秒）", userId, username);
        if (status != null) writeConfig(KEY_LAST_STATUS, status, "string", "最近一次同步状态", userId, username);
        if (message != null) writeConfig(KEY_LAST_MESSAGE, message, "string", "最近一次同步消息", userId, username);
    }

    void writeMetrics(Map<String, Object> metrics, Long userId, String username) throws Exception {
        writeConfig(KEY_LAST_METRICS_JSON, objectMapper.writeValueAsString(metrics), "json", "最近一次同步指标", userId, username);
    }

    private void writeIfPresent(String key, Long value, String description, Long userId, String username) {
        if (value != null) writeConfig(key, String.valueOf(value), "number", description, userId, username);
    }

    private void validateNeo4jDataSource(DataSource dataSource, String prefix) {
        if (!"neo4j".equalsIgnoreCase(dataSource.getType())) {
            throw new BusinessException(prefix + "数据源不是Neo4j类型", ErrorCode.BAD_REQUEST);
        }
        if (dataSource.getStatus() != null && dataSource.getStatus() == 0) {
            throw new BusinessException(prefix + "Neo4j数据源已禁用", ErrorCode.BAD_REQUEST);
        }
    }

    private String readString(String key, String defaultValue) {
        return systemConfigRepository.findByConfigKeyAndNotDeleted(key).map(SystemConfig::getConfigValue).orElse(defaultValue);
    }

    private void writeConfig(String key, String value, String type, String description, Long userId, String username) {
        Optional<SystemConfig> optional = systemConfigRepository.findByConfigKeyAndNotDeleted(key);
        SystemConfig config = optional.orElseGet(() -> systemConfigRepository.findByConfigKey(key).orElseGet(() -> {
            SystemConfig created = new SystemConfig();
            created.setConfigKey(key);
            created.setCreator(username);
            created.setCreatorId(userId);
            created.setCreateTime(new Date());
            return created;
        }));
        config.setDeleted(0);
        config.setConfigValue(value);
        config.setConfigGroup(CONFIG_GROUP);
        config.setConfigType(type);
        config.setDescription(description);
        config.setUpdateTime(new Date());
        systemConfigRepository.save(config);
    }

    private void deleteConfig(String key) {
        systemConfigRepository.findByConfigKeyAndNotDeleted(key).ifPresent(config -> {
            config.setDeleted(1);
            config.setUpdateTime(new Date());
            systemConfigRepository.save(config);
        });
    }
}
