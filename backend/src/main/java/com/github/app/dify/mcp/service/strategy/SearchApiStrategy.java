package com.github.app.dify.mcp.service.strategy;

import com.github.app.dify.mcp.service.McpBrowserSearchService.SearchResult;
import java.util.List;

/**
 * 搜索API策略接口
 * 定义不同搜索API的统一操作接口，支持多种搜索服务提供商
 */
public interface SearchApiStrategy {
    
    /**
     * 获取策略类型（如：tavily, serpapi, bing, google, searxng, zhipu）
     */
    String getType();
    
    /**
     * 获取策略优先级（数字越小优先级越高，用于自动降级）
     */
    int getPriority();
    
    /**
     * 检查API是否可用
     * @return true表示可用，false表示不可用
     */
    boolean isAvailable();
    
    /**
     * 执行搜索
     * @param query 搜索查询
     * @param maxResults 最大结果数量
     * @return 搜索结果列表
     */
    List<SearchResult> search(String query, int maxResults);
    
    /**
     * 获取API名称（用于日志和显示）
     */
    default String getApiName() {
        return getType();
    }
}

