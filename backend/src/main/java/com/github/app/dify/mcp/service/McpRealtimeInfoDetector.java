package com.github.app.dify.mcp.service;

import com.github.app.dify.mcp.config.McpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
/**
 * MCP实时信息检测器
 * 智能检测问题是否涉及实时信息，需要网络检索
 */
@Service
public class McpRealtimeInfoDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(McpRealtimeInfoDetector.class);
    
    @Autowired
    private McpConfig mcpConfig;
    
    // 时间相关关键词（表示需要最新信息）- 使用HashSet提高查找效率
    private static final Set<String> TIME_KEYWORDS = new HashSet<>(Arrays.asList(
            "现在", "当前", "今天", "昨天", "明天", "最近", "最新", "近期", "刚刚", "刚才", "实时", "目前", "当下", "此刻",
            "现在", "当前", "今日", "昨日", "明日", "本周", "本月", "今年", "现在", "当前",
            "now", "current", "today", "yesterday", "tomorrow", "recent", "latest", "lately", "just now", "realtime",
            "this week", "this month", "this year", "nowadays"
    ));
    
    // 实时信息类型关键词
    private static final Set<String> REALTIME_TYPE_KEYWORDS = new HashSet<>(Arrays.asList(
            "新闻", "消息", "动态", "事件", "发生", "发布", "宣布", "声明", "言论", "发表", "情况", "状态", "进展",
            "报道", "资讯", "资讯", "快讯", "通报", "公告", "通知", "报告", "数据", "统计", "结果", "排名",
            "news", "update", "event", "happened", "announced", "released", "statement", "said", "situation",
            "report", "information", "data", "statistics", "result", "ranking"
    ));
    
    // 实时数据类关键词
    private static final Set<String> REALTIME_DATA_KEYWORDS = new HashSet<>(Arrays.asList(
            "价格", "汇率", "股价", "行情", "指数", "热搜", "排行榜", "榜单", "趋势", "排名",
            "气温", "温度", "天气", "湿度", "降雨", "降水", "风速", "空气质量", "pm2.5", "pm10",
            "price", "rate", "stock", "trend", "ranking", "hot", "trending", "exchange rate",
            "temperature", "weather", "humidity", "rainfall", "precipitation", "wind speed", "air quality"
    ));
    
    // 问题模式（表示询问最新情况）
    private static final Pattern REALTIME_QUESTION_PATTERNS = Pattern.compile(
            ".*(现在|当前|今天|最近|最新|刚刚|刚才).*(怎么样|如何|什么|多少|哪里|何时|谁).*|" +
            ".*(怎么样|如何|什么|多少|哪里|何时|谁).*(现在|当前|今天|最近|最新|刚刚|刚才).*|" +
            ".*(最新|最近|现在|当前).*(消息|新闻|动态|事件|情况|状态).*|" +
            ".*(价格|汇率|股价|行情|指数|热搜|排行榜|气温|温度|天气).*(现在|当前|今天|最新|多少).*|" +
            ".*(今天|现在|当前).*(的).*(气温|温度|天气|湿度|降雨|降水|风速|空气质量).*|" +
            ".*[\\u4e00-\\u9fa5a-zA-Z]{2,}.*(今天|现在|当前).*(的).*(气温|温度|天气|湿度|降雨|降水|风速|空气质量).*",
            Pattern.CASE_INSENSITIVE
    );
    
    /**
     * 检测问题是否涉及实时信息
     * @param question 用户问题
     * @return true表示涉及实时信息，需要网络检索
     */
    public boolean isRealtimeInfoQuestion(String question) {
        if (!mcpConfig.getRealtimeInfoDetector().isEnabled()) {
            return false;
        }
        
        if (question == null || question.trim().isEmpty()) {
            return false;
        }
        
        double confidence = getRealtimeInfoConfidence(question);
        double threshold = mcpConfig.getRealtimeInfoDetector().getConfidenceThreshold();
        boolean isRealtime = confidence >= threshold;
        
        if (isRealtime) {
            logger.debug("检测到实时信息问题 - 查询: {}, 置信度: {:.2f}, 阈值: {:.2f}", 
                    question, confidence, threshold);
        }
        
        return isRealtime;
    }
    
    
    /**
     * 检测是否包含实体+实时上下文的组合
     */
    private boolean containsEntityWithRealtimeContext(String question) {
        // 检测模式：实体 + (的|关于) + (最新|最近|现在|当前|动态|消息|新闻|气温|温度|天气)
        Pattern entityRealtimePattern = Pattern.compile(
                ".*[\\u4e00-\\u9fa5a-zA-Z]{2,}.*(的|关于).*(最新|最近|现在|当前|动态|消息|新闻|情况|状态|怎么样|如何|气温|温度|天气|湿度|降雨|降水|风速|空气质量).*",
                Pattern.CASE_INSENSITIVE
        );
        
        if (entityRealtimePattern.matcher(question).matches()) {
            return true;
        }
        
        // 检测模式：(最新|最近|现在|当前|今天) + 实体 + (的|关于) + (消息|新闻|动态|气温|温度|天气)
        Pattern realtimeEntityPattern = Pattern.compile(
                ".*(最新|最近|现在|当前|今天).*[\\u4e00-\\u9fa5a-zA-Z]{2,}.*(的|关于).*(消息|新闻|动态|情况|状态|气温|温度|天气|湿度|降雨|降水|风速|空气质量).*",
                Pattern.CASE_INSENSITIVE
        );
        
        if (realtimeEntityPattern.matcher(question).matches()) {
            return true;
        }
        
        // 检测模式：实体 + (今天|现在|当前) + 的 + (气温|温度|天气|湿度|降雨|降水|风速|空气质量)
        // 例如："北京今天的气温"、"上海现在的天气"
        Pattern locationTimeWeatherPattern = Pattern.compile(
                ".*[\\u4e00-\\u9fa5a-zA-Z]{2,}.*(今天|现在|当前).*的.*(气温|温度|天气|湿度|降雨|降水|风速|空气质量|pm2\\.5|pm10).*",
                Pattern.CASE_INSENSITIVE
        );
        
        return locationTimeWeatherPattern.matcher(question).matches();
    }
    
    /**
     * 检测是否为短问题且包含疑问词
     */
    private boolean isShortQuestionWithQuestionWord(String question) {
        // 短问题：长度在5-30个字符之间
        if (question.length() < 5 || question.length() > 30) {
            return false;
        }
        
        // 包含疑问词
        String[] questionWords = {"什么", "怎么", "如何", "为什么", "哪里", "何时", "谁", "多少", "哪个",
                                 "what", "how", "why", "where", "when", "who", "which", "how much", "how many"};
        
        String lowerQuestion = question.toLowerCase();
        return Arrays.stream(questionWords).anyMatch(lowerQuestion::contains);
    }
    
    /**
     * 检测是否可能包含实体名称（人名、地名、公司名等）
     */
    private boolean containsLikelyEntityName(String question) {
        // 简单启发式：包含2-4个连续中文字符，可能是人名、地名等
        Pattern chineseEntityPattern = Pattern.compile(".*[\\u4e00-\\u9fa5]{2,4}.*");
        
        // 检测是否包含常见的人名、地名、组织名关键词
        String[] entityIndicators = {"公司", "企业", "机构", "组织", "政府", "国家", "地区", "城市",
                                    "company", "corporation", "organization", "government", "country", "city"};
        
        String lowerQuestion = question.toLowerCase();
        boolean hasEntityIndicator = Arrays.stream(entityIndicators).anyMatch(lowerQuestion::contains);
        boolean matchesChinesePattern = chineseEntityPattern.matcher(question).matches();
        
        // 如果包含实体指示词，或者匹配中文实体模式，认为可能包含实体名称
        return hasEntityIndicator || matchesChinesePattern;
    }
    
    /**
     * 获取检测置信度（0.0-1.0）
     * 用于更精细的控制
     */
    public double getRealtimeInfoConfidence(String question) {
        if (question == null || question.trim().isEmpty()) {
            return 0.0;
        }
        
        String lowerQuestion = question.toLowerCase().trim();
        double confidence = 0.0;
        
        // 时间关键词：提高权重，使检测更宽松
        long timeKeywordCount = TIME_KEYWORDS.stream().filter(lowerQuestion::contains).count();
        if (timeKeywordCount > 0) {
            confidence += 0.4; // 只要包含时间关键词就给予较高置信度
        }
        
        // 实时信息类型关键词：提高权重
        long realtimeTypeCount = REALTIME_TYPE_KEYWORDS.stream().filter(lowerQuestion::contains).count();
        if (realtimeTypeCount > 0) {
            confidence += 0.3; // 只要包含实时信息类型关键词就给予较高置信度
        }
        
        // 实时数据类关键词：提高权重
        long realtimeDataCount = REALTIME_DATA_KEYWORDS.stream().filter(lowerQuestion::contains).count();
        if (realtimeDataCount > 0) {
            confidence += 0.3; // 只要包含实时数据类关键词就给予较高置信度
        }
        
        // 模式匹配：提高权重
        if (REALTIME_QUESTION_PATTERNS.matcher(question).matches()) {
            confidence += 0.5;
        }
        
        // 实体+实时上下文：提高权重
        if (containsEntityWithRealtimeContext(question)) {
            confidence += 0.4;
        }
        
        // 短问题+疑问词+实体：提高权重
        // 注意：此条件与下面的条件逻辑相同，已合并以避免重复计算
        if (isShortQuestionWithQuestionWord(question) && containsLikelyEntityName(question)) {
            confidence += 0.3;
        }
        
        // 如果问题长度较短（可能是简单查询），给予基础置信度
        if (question.length() >= 3 && question.length() <= 20) {
            // 检查是否包含常见查询词
            String[] commonQueryWords = {"的", "是", "有", "在", "什么", "多少", "如何", "怎么", "哪个", "哪里"};
            boolean hasCommonQueryWord = Arrays.stream(commonQueryWords).anyMatch(question::contains);
            if (hasCommonQueryWord) {
                confidence += 0.15;
            }
        }
        
        return Math.min(confidence, 1.0);
    }
}

