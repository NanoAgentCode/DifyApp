package com.github.app.dify.analytics.analysis.service;

import com.github.app.dify.analytics.analysis.req.DataAnalysisSettingsReq;
import com.github.app.dify.analytics.analysis.req.GraphQAReq;
import com.github.app.dify.analytics.analysis.req.GraphRAGReq;
import com.github.app.dify.analytics.analysis.resp.GraphViewResp;
import com.github.app.dify.analytics.analysis.resp.DataAnalysisSettingsResp;
import com.github.app.dify.analytics.analysis.resp.DataAnalysisStatusResp;
import com.github.app.dify.analytics.analysis.resp.GraphQAResp;
import com.github.app.dify.analytics.analysis.resp.GraphRAGResp;

public interface DataAnalysisService {

    DataAnalysisSettingsResp getSettings();

    DataAnalysisSettingsResp updateSettings(DataAnalysisSettingsReq req, Long userId, String username);

    DataAnalysisStatusResp getStatus();

    void triggerRun(Long userId, String username);

    void runIfDue();

    GraphViewResp getGraphView(Integer limit, String keyword, String nodeLabel, String relationshipType, Integer depth);

    GraphQAResp answerGraphQuestion(GraphQAReq req);

    GraphRAGResp answerGraphRAG(GraphRAGReq req, Long userId);
}
