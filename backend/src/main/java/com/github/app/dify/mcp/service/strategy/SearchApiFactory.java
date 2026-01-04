package com.github.app.dify.mcp.service.strategy;

import com.github.app.dify.mcp.service.McpBrowserSearchService.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索API工厂类
 * 负责管理和选择可用的搜索API，支持自动降级和故障转移
 */
@Component
public class SearchApiFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(SearchApiFactory.class);
    
    @Autowired(required = false)
    private List<SearchApiStrategy> searchStrategies;
    
    /**
     * 获取所有可用的搜索策略，按优先级排序
     */
    private List<SearchApiStrategy> getAvailableStrategies() {
        if (searchStrategies == null || searchStrategies.isEmpty()) {
            return new ArrayList<>();
        }
        
        return searchStrategies.stream()
            .filter(SearchApiStrategy::isAvailable)
            .sorted(Comparator.comparingInt(SearchApiStrategy::getPriority))
            .collect(Collectors.toList());
    }
    
    /**
     * 执行搜索，自动尝试多个API直到成功
     * @param query 搜索查询
     * @param maxResults 最大结果数
     * @return 搜索结果列表
     */
    public List<SearchResult> search(String query, int maxResults) {
        List<SearchApiStrategy> availableStrategies = getAvailableStrategies();
        
        if (availableStrategies.isEmpty()) {
            logger.warn("没有可用的搜索API，返回空结果");
            return new ArrayList<>();
        }
        
        logger.info("找到 {} 个可用的搜索API，按优先级尝试", availableStrategies.size());
        
        // 按优先级依次尝试
        for (SearchApiStrategy strategy : availableStrategies) {
            try {
                logger.info("尝试使用 {} (优先级: {}) 进行搜索", strategy.getApiName(), strategy.getPriority());
                
                List<SearchResult> results = strategy.search(query, maxResults);
                
                if (results != null && !results.isEmpty()) {
                    logger.info("{} 搜索成功，返回 {} 个结果", strategy.getApiName(), results.size());
                    return results;
                } else {
                    logger.warn("{} 搜索返回空结果，尝试下一个API", strategy.getApiName());
                }
                
            } catch (Exception e) {
                logger.warn("{} 搜索失败: {}，尝试下一个API", strategy.getApiName(), e.getMessage());
                // 继续尝试下一个API
            }
        }
        
        logger.warn("所有搜索API都失败，返回空结果");
        return new ArrayList<>();
    }
    
    /**
     * 获取当前使用的搜索API信息（用于日志和监控）
     */
    public String getCurrentApiInfo() {
        List<SearchApiStrategy> availableStrategies = getAvailableStrategies();
        if (availableStrategies.isEmpty()) {
            return "无可用API";
        }
        
        return availableStrategies.stream()
            .map(s -> s.getApiName() + " (优先级:" + s.getPriority() + ")")
            .collect(Collectors.joining(", "));
    }
}

