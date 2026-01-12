package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.system.config.RagConfig;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest;
import com.github.app.dify.documentreader.req.DocumentQARequest;
import com.github.app.dify.model.service.ModelConfigService;
import com.github.app.dify.knowledgebase.service.ContextCompressionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import com.github.app.dify.system.util.SkillLoader;
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
        
        // 如果没有历史对话，只压缩系统消息中的文档内容
        if (request.getHistory() == null || request.getHistory().isEmpty()) {
            return compressSystemMessage(messages);
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
        
        // 重新构建消息列表，同时压缩系统消息中的文档内容
        List<ChatMessage> compressedMessages = new ArrayList<>();
        // 压缩系统消息中的文档内容
        ChatMessage systemMsg = messages.get(0);
        if (systemMsg instanceof SystemMessage) {
            String originalText = ((SystemMessage) systemMsg).text();
            String compressedText = compressDocumentContent(originalText, ragConfig.getMaxSystemMessageLength());
            compressedMessages.add(SystemMessage.from(compressedText));
        } else {
            compressedMessages.add(systemMsg);
        }
        compressedMessages.addAll(compressedHistory); // 压缩后的历史
        compressedMessages.add(messages.get(messages.size() - 1)); // 当前问题
        
        int originalSize = historyMessages.size();
        int compressedSize = compressedHistory.size();
        logger.info("上下文压缩完成 - 原始历史消息数: {}, 压缩后: {}, 策略: {}", 
                originalSize, compressedSize, strategy);
        
        return compressedMessages;
    }
    
    /**
     * 压缩历史对话消息（文档问答）
     */
    @Override
    public List<ChatMessage> compressContext(List<ChatMessage> messages, DocumentQARequest request) {
        if (!ragConfig.isEnableContextCompression()) {
            logger.debug("上下文压缩未启用，跳过压缩");
            return messages;
        }
        
        // 如果没有历史对话，只压缩系统消息中的文档内容
        if (request.getHistory() == null || request.getHistory().isEmpty()) {
            return compressSystemMessage(messages);
        }
        
        // 系统消息（第一个）和当前问题（最后一个）需要保留
        if (messages.size() <= 2) {
            return compressSystemMessage(messages);
        }
        
        // 提取历史对话消息（不包括系统消息和当前问题）
        List<ChatMessage> historyMessages = new ArrayList<>();
        for (int i = 1; i < messages.size() - 1; i++) {
            historyMessages.add(messages.get(i));
        }
        
        // 根据策略压缩历史对话
        String strategy = ragConfig.getCompressionStrategy();
        List<ChatMessage> compressedHistory;
        
        switch (strategy.toLowerCase()) {
            case "sliding_window":
                compressedHistory = compressBySlidingWindow(historyMessages);
                break;
            case "summary":
                if (ragConfig.isEnableSummary()) {
                    compressedHistory = compressBySummaryForDocument(historyMessages, request);
                } else {
                    logger.warn("总结压缩策略已选择但未启用，回退到滑动窗口策略");
                    compressedHistory = compressBySlidingWindow(historyMessages);
                }
                break;
            case "hybrid":
                compressedHistory = compressByHybridForDocument(historyMessages, request);
                break;
            default:
                logger.warn("未知的压缩策略: {}，使用滑动窗口策略", strategy);
                compressedHistory = compressBySlidingWindow(historyMessages);
        }
        
        // 重新构建消息列表，同时压缩系统消息
        List<ChatMessage> compressedMessages = new ArrayList<>();
        // 压缩系统消息中的文档内容
        ChatMessage systemMsg = messages.get(0);
        if (systemMsg instanceof SystemMessage) {
            String originalText = ((SystemMessage) systemMsg).text();
            String compressedText = compressDocumentContent(originalText, ragConfig.getMaxSystemMessageLength());
            compressedMessages.add(SystemMessage.from(compressedText));
        } else {
            compressedMessages.add(systemMsg);
        }
        compressedMessages.addAll(compressedHistory); // 压缩后的历史
        compressedMessages.add(messages.get(messages.size() - 1)); // 当前问题
        
        int originalSize = historyMessages.size();
        int compressedSize = compressedHistory.size();
        logger.info("文档问答上下文压缩完成 - 原始历史消息数: {}, 压缩后: {}, 策略: {}", 
                originalSize, compressedSize, strategy);
        
        return compressedMessages;
    }
    
    /**
     * 压缩系统消息中的文档内容
     */
    private List<ChatMessage> compressSystemMessage(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return messages;
        }
        
        ChatMessage systemMsg = messages.get(0);
        if (systemMsg instanceof SystemMessage) {
            String originalText = ((SystemMessage) systemMsg).text();
            String compressedText = compressDocumentContent(originalText, ragConfig.getMaxSystemMessageLength());
            
            List<ChatMessage> compressed = new ArrayList<>();
            compressed.add(SystemMessage.from(compressedText));
            // 保留其他消息
            for (int i = 1; i < messages.size(); i++) {
                compressed.add(messages.get(i));
            }
            return compressed;
        }
        
        return messages;
    }
    
    /**
     * 压缩系统消息中的文档内容（检索到的文档片段）
     */
    @Override
    public String compressDocumentContent(String systemMessageText, int maxLength) {
        if (systemMessageText == null || systemMessageText.length() <= maxLength) {
            return systemMessageText;
        }
        
        // 如果系统消息包含文档片段，尝试智能压缩
        // 支持多种格式：相关文档片段、文档片段、基于以下知识库内容
        String fragmentMarker = null;
        String fragmentLabelFormat = null;
        if (systemMessageText.contains("相关文档片段：")) {
            fragmentMarker = "相关文档片段：";
            fragmentLabelFormat = "片段%d：\n";
        } else if (systemMessageText.contains("文档片段 ")) {
            fragmentMarker = "基于以下知识库内容回答问题：";
            fragmentLabelFormat = "文档片段 %d:\n";
        } else if (systemMessageText.contains("片段")) {
            fragmentMarker = "片段";
            fragmentLabelFormat = "片段%d：\n";
        }
        
        if (fragmentMarker != null) {
            // 提取文档片段部分
            int markerIndex = systemMessageText.indexOf(fragmentMarker);
            if (markerIndex >= 0) {
                String header = systemMessageText.substring(0, markerIndex + fragmentMarker.length()) + "\n\n";
                String fragments = systemMessageText.substring(markerIndex + fragmentMarker.length());
                
                // 按片段分割（支持"片段1"、"文档片段 1"等格式）
                String[] fragmentArray = fragments.split("(片段\\d+|文档片段 \\d+)[:：]?\\s*\n");
                List<String> validFragments = new ArrayList<>();
                int currentLength = header.length();
                
                // 计算固定后缀长度（提示词部分）
                String suffix = "";
                if (systemMessageText.contains("如果知识库中没有相关信息")) {
                    int suffixStart = systemMessageText.indexOf("如果知识库中没有相关信息");
                    suffix = systemMessageText.substring(suffixStart);
                } else if (systemMessageText.contains("请基于以上文档片段")) {
                    int suffixStart = systemMessageText.indexOf("请基于以上文档片段");
                    suffix = systemMessageText.substring(suffixStart);
                }
                int suffixLength = suffix.length();
                int availableLength = maxLength - header.length() - suffixLength - 200; // 预留200字符缓冲
                
                // 优先保留前面的片段（通常相关性更高）
                for (String fragment : fragmentArray) {
                    if (fragment.trim().isEmpty()) {
                        continue;
                    }
                    String cleanFragment = fragment.trim();
                    // 计算带标签的片段长度
                    String fragmentLabel = String.format(fragmentLabelFormat, validFragments.size() + 1);
                    int fragmentWithLabelLength = fragmentLabel.length() + cleanFragment.length() + 2; // +2 for \n\n
                    
                    if (currentLength + fragmentWithLabelLength <= availableLength) {
                        validFragments.add(cleanFragment);
                        currentLength += fragmentWithLabelLength;
                    } else {
                        // 如果当前片段太长，尝试截取部分内容
                        int remainingLength = availableLength - currentLength - 50; // 预留一些空间
                        if (remainingLength > 100) {
                            String truncated = cleanFragment;
                            if (truncated.length() > remainingLength) {
                                // 尝试在句子边界截断
                                int lastPeriod = truncated.lastIndexOf('。', remainingLength);
                                int lastPeriodCN = truncated.lastIndexOf('.', remainingLength);
                                int cutPoint = Math.max(lastPeriod, lastPeriodCN);
                                if (cutPoint > remainingLength * 0.7) {
                                    truncated = truncated.substring(0, cutPoint + 1) + "...";
                                } else {
                                    truncated = truncated.substring(0, remainingLength) + "...";
                                }
                            }
                            validFragments.add(truncated);
                        }
                        break;
                    }
                }
                
                // 重新构建系统消息
                StringBuilder compressed = new StringBuilder(header);
                for (int i = 0; i < validFragments.size(); i++) {
                    compressed.append(String.format(fragmentLabelFormat, i + 1));
                    compressed.append(validFragments.get(i)).append("\n\n");
                }
                compressed.append(suffix);
                
                logger.info("文档内容压缩 - 原始长度: {}, 压缩后长度: {}, 保留片段数: {}, 最大长度: {}", 
                        systemMessageText.length(), compressed.length(), validFragments.size(), maxLength);
                
                return compressed.toString();
            }
        }
        
        // 如果无法智能压缩，直接截断
        if (systemMessageText.length() > maxLength) {
            String truncated = systemMessageText.substring(0, maxLength - 100) + "...\n\n[内容已压缩，仅保留前" + maxLength + "个字符]";
            logger.debug("文档内容截断压缩 - 原始长度: {}, 压缩后长度: {}", 
                    systemMessageText.length(), truncated.length());
            return truncated;
        }
        
        return systemMessageText;
    }
    
    /**
     * 总结策略：使用LLM总结历史对话（文档问答版本）
     */
    private List<ChatMessage> compressBySummaryForDocument(List<ChatMessage> historyMessages, DocumentQARequest request) {
        try {
            List<ChatMessage> summaryMessages = new ArrayList<>();
            String systemSkill = SkillLoader.loadSkill("dialog_summary_system_prompt");
            if (systemSkill == null || systemSkill.trim().isEmpty()) {
                systemSkill = "你是一个专业的对话总结助手，能够准确总结对话历史的关键信息。";
            }
            summaryMessages.add(SystemMessage.from(systemSkill));
            
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
            summaryMessages.add(UserMessage.from(summaryPrompt));
            
            // 使用默认的RAG模型进行总结
            QAModel qaModel = modelConfigService.getDefaultQAModelForRAG();
            ChatLanguageModel chatLanguageModel = 
                    modelLanguageModelFactory.createChatLanguageModel(qaModel);
            Response<AiMessage> response = chatLanguageModel.generate(summaryMessages);
            String summary = response.content().text();
            
            // 将总结作为用户消息，表示这是对历史对话的总结
            List<ChatMessage> compressed = new ArrayList<>();
            compressed.add(UserMessage.from("【历史对话总结】" + summary));
            
            logger.debug("文档问答总结压缩完成 - 原始消息数: {}, 总结长度: {}", 
                    historyMessages.size(), summary.length());
            
            return compressed;
        } catch (Exception e) {
            logger.error("文档问答总结压缩失败，回退到滑动窗口策略", e);
            return compressBySlidingWindow(historyMessages);
        }
    }
    
    /**
     * 混合策略：结合滑动窗口和总结（文档问答版本）
     */
    private List<ChatMessage> compressByHybridForDocument(List<ChatMessage> historyMessages, DocumentQARequest request) {
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
            List<ChatMessage> summary = compressBySummaryForDocument(oldMessages, request);
            compressed.addAll(summary);
        }
        
        // 保留最近的对话
        compressed.addAll(recentMessages);
        
        logger.debug("文档问答混合压缩完成 - 旧对话数: {}, 新对话数: {}, 总结后消息数: {}", 
                oldMessages.size(), recentMessages.size(), compressed.size());
        
        return compressed;
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
            
            List<ChatMessage> summaryMessages = new ArrayList<>();
            String systemSkill = com.github.app.dify.system.util.SkillLoader.loadSkill("dialog_summary_system_prompt");
            if (systemSkill == null || systemSkill.trim().isEmpty()) {
                systemSkill = "你是一个专业的对话总结助手，能够准确总结对话历史的关键信息。";
            }
            summaryMessages.add(SystemMessage.from(systemSkill));
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
