package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.system.config.RagConfig;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest;
import com.github.app.dify.system.service.ModelConfigService;
import com.github.app.dify.knowledgebase.service.ContextCompressionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
/**
 * 上下文压缩服务
 * 用于在连续对话时压缩历史上下文，避免超过token限制
 */
@Service
public class ContextCompressionServiceImpl implements ContextCompressionService {
    
    private static final Logger logger = LoggerFactory.getLogger(ContextCompressionServiceImpl.class);
    
    @Autowired
    private RagConfig ragConfig;
    
    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;
    
    @Autowired
    private ModelConfigService modelConfigService;
    
    /**
     * 压缩历史对话消息
     * 
     * @param messages 原始消息列表（包含系统消息、历史对话和当前问题）
     * @param request 当前请求
     * @return 压缩后的消息列表
     */
    @Override
    public List<ChatMessage> compressContext(List<ChatMessage> messages, KnowledgeBaseQARequest request) {
        if (!ragConfig.isEnableContextCompression()) {
            logger.debug("上下文压缩未启用，跳过压缩");
            return messages;
        }
        
        // 如果没有历史对话，直接返回
        if (request.getHistory() == null || request.getHistory().isEmpty()) {
            return messages;
        }
        
        // 系统消息（第一个）和当前问题（最后一个）需要保留
        if (messages.size() <= 2) {
            return messages;
        }
        
        // 提取历史对话消息（不包括系统消息和当前问题）
        List<ChatMessage> historyMessages = new ArrayList<>();
        for (int i = 1; i < messages.size() - 1; i++) {
            historyMessages.add(messages.get(i));
        }
        
        if (historyMessages.isEmpty()) {
            return messages;
        }
        
        // 根据策略压缩
        String strategy = ragConfig.getCompressionStrategy();
        List<ChatMessage> compressedHistory;
        
        switch (strategy.toLowerCase()) {
            case "sliding_window":
                compressedHistory = compressBySlidingWindow(historyMessages);
                break;
            case "summary":
                if (ragConfig.isEnableSummary()) {
                    compressedHistory = compressBySummary(historyMessages, request);
                } else {
                    logger.warn("总结压缩策略已选择但未启用，回退到滑动窗口策略");
                    compressedHistory = compressBySlidingWindow(historyMessages);
                }
                break;
            case "hybrid":
                compressedHistory = compressByHybrid(historyMessages, request);
                break;
            default:
                logger.warn("未知的压缩策略: {}，使用滑动窗口策略", strategy);
                compressedHistory = compressBySlidingWindow(historyMessages);
        }
        
        // 重新构建消息列表
        List<ChatMessage> compressedMessages = new ArrayList<>();
        compressedMessages.add(messages.get(0)); // 系统消息
        compressedMessages.addAll(compressedHistory); // 压缩后的历史
        compressedMessages.add(messages.get(messages.size() - 1)); // 当前问题
        
        int originalSize = historyMessages.size();
        int compressedSize = compressedHistory.size();
        logger.info("上下文压缩完成 - 原始历史消息数: {}, 压缩后: {}, 策略: {}", 
                originalSize, compressedSize, strategy);
        
        return compressedMessages;
    }
    
    /**
     * 滑动窗口策略：只保留最近的N轮对话
     */
    private List<ChatMessage> compressBySlidingWindow(List<ChatMessage> historyMessages) {
        int maxRounds = ragConfig.getMaxHistoryRounds();
        // 每轮对话包含一个用户消息和一个助手消息
        int maxMessages = maxRounds * 2;
        
        if (historyMessages.size() <= maxMessages) {
            return new ArrayList<>(historyMessages);
        }
        
        // 保留最近的N轮对话
        List<ChatMessage> compressed = new ArrayList<>();
        int startIndex = historyMessages.size() - maxMessages;
        for (int i = startIndex; i < historyMessages.size(); i++) {
            compressed.add(historyMessages.get(i));
        }
        
        logger.debug("滑动窗口压缩 - 保留最近 {} 轮对话（{} 条消息）", maxRounds, compressed.size());
        return compressed;
    }
    
    /**
     * 总结策略：使用LLM总结历史对话
     */
    private List<ChatMessage> compressBySummary(List<ChatMessage> historyMessages, KnowledgeBaseQARequest request) {
        try {
            // 构建总结提示
            StringBuilder historyText = new StringBuilder();
            for (ChatMessage msg : historyMessages) {
                if (msg instanceof UserMessage) {
                    historyText.append("用户: ").append(((UserMessage) msg).singleText()).append("\n");
                } else if (msg instanceof AiMessage) {
                    historyText.append("助手: ").append(((AiMessage) msg).text()).append("\n");
                }
            }
            
            String summaryPrompt = String.format(
                    "请总结以下对话历史，保留关键信息和上下文，使总结后的内容能够帮助理解当前对话的上下文。\n\n" +
                    "对话历史：\n%s\n\n" +
                    "请提供简洁的总结，保留重要的问答内容和上下文信息：",
                    historyText.toString()
            );
            
            // 调用LLM生成总结
            List<ChatMessage> summaryMessages = new ArrayList<>();
            summaryMessages.add(SystemMessage.from("你是一个专业的对话总结助手，能够准确总结对话历史的关键信息。"));
            summaryMessages.add(UserMessage.from(summaryPrompt));
            
            // 使用默认的RAG模型进行总结
            QAModel qaModel = modelConfigService.getDefaultQAModelForRAG();
            ChatLanguageModel chatLanguageModel = 
                    modelLanguageModelFactory.createChatLanguageModel(qaModel);
            Response<AiMessage> response = chatLanguageModel.generate(summaryMessages);
            String summary = response.content().text();
            
            // 将总结作为用户消息，表示这是对历史对话的总结
            // 这样可以保持对话的连贯性
            List<ChatMessage> compressed = new ArrayList<>();
            compressed.add(UserMessage.from("【历史对话总结】" + summary));
            
            logger.debug("总结压缩完成 - 原始消息数: {}, 总结长度: {}", 
                    historyMessages.size(), summary.length());
            
            return compressed;
        } catch (Exception e) {
            logger.error("总结压缩失败，回退到滑动窗口策略", e);
            return compressBySlidingWindow(historyMessages);
        }
    }
    
    /**
     * 混合策略：结合滑动窗口和总结
     * 对于较新的对话使用滑动窗口，对于较旧的对话进行总结
     */
    private List<ChatMessage> compressByHybrid(List<ChatMessage> historyMessages, KnowledgeBaseQARequest request) {
        int maxRounds = ragConfig.getMaxHistoryRounds();
        int maxMessages = maxRounds * 2;
        
        if (historyMessages.size() <= maxMessages) {
            return new ArrayList<>(historyMessages);
        }
        
        // 分离新旧对话
        int splitIndex = historyMessages.size() - maxMessages;
        List<ChatMessage> oldMessages = historyMessages.subList(0, splitIndex);
        List<ChatMessage> recentMessages = historyMessages.subList(splitIndex, historyMessages.size());
        
        // 对旧对话进行总结
        List<ChatMessage> compressed = new ArrayList<>();
        if (!oldMessages.isEmpty() && ragConfig.isEnableSummary()) {
            List<ChatMessage> summary = compressBySummary(oldMessages, request);
            compressed.addAll(summary);
        }
        
        // 保留最近的对话
        compressed.addAll(recentMessages);
        
        logger.debug("混合压缩完成 - 旧对话数: {}, 新对话数: {}, 总结后消息数: {}", 
                oldMessages.size(), recentMessages.size(), compressed.size());
        
        return compressed;
    }
    
    /**
     * 估算消息的token数量（简单估算：1 token ≈ 4 字符）
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 简单估算：中文字符按2字符计算，英文按1字符计算
        int chineseChars = 0;
        int englishChars = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chineseChars++;
            } else if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
                englishChars++;
            }
        }
        // 粗略估算：中文字符按2 token，英文按0.25 token
        return (int) (chineseChars * 2 + englishChars * 0.25);
    }
    
    /**
     * 检查是否需要压缩（基于token数量）
     */
    @Override
    public boolean needsCompression(List<ChatMessage> messages) {
        if (!ragConfig.isEnableContextCompression()) {
            return false;
        }
        
        int totalTokens = 0;
        for (ChatMessage msg : messages) {
            String text = null;
            if (msg instanceof UserMessage) {
                text = ((UserMessage) msg).singleText();
            } else if (msg instanceof AiMessage) {
                text = ((AiMessage) msg).text();
            } else if (msg instanceof SystemMessage) {
                text = ((SystemMessage) msg).text();
            }
            if (text != null) {
                totalTokens += estimateTokens(text);
            }
        }
        
        boolean needs = totalTokens > ragConfig.getMaxHistoryTokens();
        if (needs) {
            logger.debug("检测到需要压缩 - 总token数: {}, 阈值: {}", 
                    totalTokens, ragConfig.getMaxHistoryTokens());
        }
        return needs;
    }
}