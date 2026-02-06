package com.github.app.dify.mcp.browsersearch.strategy;

import com.github.app.dify.mcp.browsersearch.BrowserSearchConfig;
import com.github.app.dify.mcp.browsersearch.McpBrowserSearchService.SearchResult;
import com.github.app.dify.mcp.browsersearch.util.SearXNGSearchHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * SearX-NG搜索API策略实现（保留原有实现作为降级方案）
 * 这是原有的SearX-NG实现，作为最后的降级选项
 */
@Component
public class SearXNGSearchApiStrategy implements SearchApiStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SearXNGSearchApiStrategy.class);

    @Autowired
    private BrowserSearchConfig browserSearchConfig;

    @Autowired
    private SearXNGSearchHelper searXNGSearchHelper;

    @Override
    public String getType() {
        return "searxng";
    }

    @Override
    public int getPriority() {
        return 10; // 最低优先级，作为降级方案
    }

    @Override
    public boolean isAvailable() {
        String baseUrl = browserSearchConfig.getSearxngBaseUrl();
        return baseUrl != null && !baseUrl.trim().isEmpty();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (!isAvailable()) {
            logger.warn("SearX-NG未配置，跳过搜索");
            return new ArrayList<>();
        }

        try {
            logger.info("使用SearX-NG搜索（降级方案） - 查询: {}, 最大结果数: {}", query, maxResults);
            return searXNGSearchHelper.search(query, maxResults);
        } catch (Exception e) {
            logger.error("SearX-NG搜索失败 - 查询: {}", query, e);
            return new ArrayList<>();
        }
    }

    @Override
    public String getApiName() {
        return "SearX-NG (本地部署)";
    }
}
