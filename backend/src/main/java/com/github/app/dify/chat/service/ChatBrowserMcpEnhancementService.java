package com.github.app.dify.chat.service;

import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.mcp.browsersearch.McpBrowserSearchService;
import com.github.app.dify.system.util.SkillLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatBrowserMcpEnhancementService {

    private static final Logger logger = LoggerFactory.getLogger(ChatBrowserMcpEnhancementService.class);

    private final McpBrowserSearchService browserSearchService;

    public ChatBrowserMcpEnhancementService(McpBrowserSearchService browserSearchService) {
        this.browserSearchService = browserSearchService;
    }

    public String searchContext(ChatRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.getEnableBrowserSearch())) {
            return "";
        }
        try {
            List<McpBrowserSearchService.SearchResult> results = browserSearchService.search(request.getQuestion(), 5);
            if (results != null && !results.isEmpty()) {
                return browserSearchService.formatSearchResultsForContext(results);
            }
            return SkillLoader.loadSkillWithTemplate("chat/browser_search_no_results_fallback_template",
                    Map.of("question", String.valueOf(request.getQuestion())));
        } catch (Exception e) {
            logger.error("浏览器检索失败 - 查询: {}", request.getQuestion(), e);
            return "";
        }
    }
}
