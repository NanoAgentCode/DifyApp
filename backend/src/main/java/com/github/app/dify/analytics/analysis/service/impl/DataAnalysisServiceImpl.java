package com.github.app.dify.analytics.analysis.service.impl;

import com.github.app.dify.analytics.analysis.req.DataAnalysisSettingsReq;
import com.github.app.dify.analytics.analysis.req.GraphQAReq;
import com.github.app.dify.analytics.analysis.req.GraphRAGReq;
import com.github.app.dify.analytics.analysis.resp.DataAnalysisSettingsResp;
import com.github.app.dify.analytics.analysis.resp.DataAnalysisStatusResp;
import com.github.app.dify.analytics.analysis.resp.GraphQAResp;
import com.github.app.dify.analytics.analysis.resp.GraphRAGResp;
import com.github.app.dify.analytics.analysis.resp.GraphViewResp;
import com.github.app.dify.analytics.analysis.service.DataAnalysisService;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.datasource.domain.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DataAnalysisServiceImpl implements DataAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(DataAnalysisServiceImpl.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    private DataAnalysisConfigService dataAnalysisConfigService;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    private TaskExecutor taskExecutor;

    @Autowired
    private Neo4jSyncService neo4jSyncService;

    @Autowired
    private GraphQaService graphQaService;

    @Autowired
    private GraphRagService graphRagService;

    @Override
    public DataAnalysisSettingsResp getSettings() {
        return dataAnalysisConfigService.getSettings();
    }

    @Override
    public DataAnalysisSettingsResp updateSettings(DataAnalysisSettingsReq req, Long userId, String username) {
        return dataAnalysisConfigService.updateSettings(req, userId, username);
    }

    @Override
    public DataAnalysisStatusResp getStatus() {
        return dataAnalysisConfigService.getStatus(running.get());
    }
    @Override
    public void triggerRun(Long userId, String username) {
        DataAnalysisSettingsResp settings = dataAnalysisConfigService.getSettings();
        dataAnalysisConfigService.getValidatedNeo4jDataSourceOrThrow(settings);
        taskExecutor.execute(() -> runInternal(userId, username, true));
    }

    @Override
    public void runIfDue() {
        boolean enabled = dataAnalysisConfigService.readBoolean();
        if (!enabled) {
            return;
        }

        Integer intervalMinutes = dataAnalysisConfigService.readIntervalMinutes();
        if (intervalMinutes == null || intervalMinutes <= 0) {
            intervalMinutes = DataAnalysisConfigService.DEFAULT_INTERVAL_MINUTES;
        }

        Long lastRunAtMs = dataAnalysisConfigService.readLong(DataAnalysisConfigService.KEY_LAST_RUN_AT_MS, null);
        long now = System.currentTimeMillis();
        if (lastRunAtMs == null) {
            taskExecutor.execute(() -> runInternal(0L, "system", false));
            return;
        }

        long nextAt = lastRunAtMs + intervalMinutes * 60L * 1000L;
        if (now >= nextAt) {
            taskExecutor.execute(() -> runInternal(0L, "system", false));
        }
    }

    @Override
    public GraphViewResp getGraphView(Integer limit, String keyword, String nodeLabel, String relationshipType, Integer depth) {
        return graphQaService.getGraphView(limit, keyword, nodeLabel, relationshipType, depth);
    }

    @Override
    public GraphQAResp answerGraphQuestion(GraphQAReq req) {
        return graphQaService.answerGraphQuestion(req);
    }

    @Override
    public GraphRAGResp answerGraphRAG(GraphRAGReq req, Long userId) {
        return graphRagService.answerGraphRAG(req, userId);
    }

    private void runInternal(Long userId, String username, boolean force) {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        long start = System.currentTimeMillis();
        dataAnalysisConfigService.writeRuntimeStatus("running", "同步任务开始", start, null, null, userId, username);

        try {
            DataAnalysisSettingsResp settings = dataAnalysisConfigService.getSettings();
            if (!Boolean.TRUE.equals(settings.getEnabled()) && !force) {
                dataAnalysisConfigService.writeRuntimeStatus("never", "同步未启用", null, null, null, userId, username);
                return;
            }

            DataSource neo4jDataSource;
            try {
                neo4jDataSource = dataAnalysisConfigService.getValidatedNeo4jDataSourceOrThrow(settings);
            } catch (BusinessException ex) {
                dataAnalysisConfigService.writeRuntimeStatus("failed", ex.getMessage(), null, null, null, userId, username);
                return;
            }

            Map<String, Object> metrics = neo4jSyncService.syncToNeo4j(neo4jDataSource);

            long duration = System.currentTimeMillis() - start;
            dataAnalysisConfigService.writeRuntimeStatus("success", "同步成功", null, System.currentTimeMillis(), duration, userId, username);
            dataAnalysisConfigService.writeMetrics(metrics, userId, username);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String message = e.getMessage() != null ? e.getMessage() : "同步失败";
            logger.error("数据同步到Neo4j失败", e);
            dataAnalysisConfigService.writeRuntimeStatus("failed", message, null, null, duration, userId, username);
        } finally {
            running.set(false);
        }
    }

}
