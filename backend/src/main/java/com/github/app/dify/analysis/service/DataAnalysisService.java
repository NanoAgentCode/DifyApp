package com.github.app.dify.analysis.service;

import com.github.app.dify.analysis.req.DataAnalysisSettingsReq;
import com.github.app.dify.analysis.resp.GraphViewResp;
import com.github.app.dify.analysis.resp.DataAnalysisSettingsResp;
import com.github.app.dify.analysis.resp.DataAnalysisStatusResp;

public interface DataAnalysisService {

    DataAnalysisSettingsResp getSettings();

    DataAnalysisSettingsResp updateSettings(DataAnalysisSettingsReq req, Long userId, String username);

    DataAnalysisStatusResp getStatus();

    void triggerRun(Long userId, String username);

    void runIfDue();

    GraphViewResp getGraphView(Integer limit);
}
