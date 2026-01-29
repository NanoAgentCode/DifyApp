package com.github.app.dify.analytics.analysis.schedule;

import com.github.app.dify.analytics.analysis.service.DataAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataAnalysisScheduler {

    @Autowired
    private DataAnalysisService dataAnalysisService;

    @Scheduled(fixedDelay = 60000)
    public void tick() {
        dataAnalysisService.runIfDue();
    }
}
