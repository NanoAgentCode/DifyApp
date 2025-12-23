package com.github.app.dify.statistics.service;

import com.github.app.dify.statistics.resp.StatisticsResponse;

/**
 * 统计服务接口
 */
public interface StatisticsService {
    
    /**
     * 获取概览统计
     */
    StatisticsResponse.OverviewStatistics getOverviewStatistics();
    
    /**
     * 获取用户统计
     */
    StatisticsResponse.UserStatistics getUserStatistics();
    
    /**
     * 获取应用统计
     */
    StatisticsResponse.AppStatistics getAppStatistics();
    
    /**
     * 获取知识库统计
     */
    StatisticsResponse.KnowledgeBaseStatistics getKnowledgeBaseStatistics();
    
    /**
     * 获取模型Token统计
     */
    StatisticsResponse.ModelTokenStatistics getModelTokenStatistics();
    
    /**
     * 获取所有统计数据
     */
    StatisticsResponse getAllStatistics();
}

