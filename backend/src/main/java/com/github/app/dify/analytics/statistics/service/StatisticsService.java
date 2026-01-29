package com.github.app.dify.analytics.statistics.service;

import com.github.app.dify.analytics.statistics.resp.StatisticsResponse;

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
     * 获取用户统计（支持时间范围）
     * @param days 统计天数，默认30天
     */
    StatisticsResponse.UserStatistics getUserStatistics(Integer days);
    
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
     * 获取模型Token统计（支持时间范围）
     * @param days 统计天数，默认30天
     */
    StatisticsResponse.ModelTokenStatistics getModelTokenStatistics(Integer days);
    
    /**
     * 获取所有统计数据
     */
    StatisticsResponse getAllStatistics();
    
    /**
     * 获取所有统计数据（支持时间范围）
     * @param days 统计天数，默认30天
     */
    StatisticsResponse getAllStatistics(Integer days);
}

