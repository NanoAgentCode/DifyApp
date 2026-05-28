package com.github.app.dify.chat.service.impl;

import com.github.app.dify.chat.domain.ChatConversation;
import com.github.app.dify.chat.domain.ChatMessage;
import com.github.app.dify.chat.repository.ChatConversationRepository;
import com.github.app.dify.chat.repository.ChatMessageRepository;
import com.github.app.dify.chat.service.ConversationSummaryService;
import com.github.app.dify.chat.util.ChatDateTimeUtil;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import com.github.app.dify.system.config.RagConfig;
import com.github.app.dify.system.util.SkillLoader;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ConversationSummaryServiceImpl implements ConversationSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSummaryServiceImpl.class);

    @Autowired
    private ChatConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private QAModelRepository qaModelRepository;

    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;

    @Autowired
    private RagConfig ragConfig;

    @Async
    @Override
    @Transactional
    public void updateSummaryIfNeededAsync(Long conversationId, Long modelId) {
        if (!ragConfig.isEnableConversationSummary() || conversationId == null) {
            return;
        }
        try {
            Optional<ChatConversation> conversationOpt = conversationRepository.findById(conversationId);
            if (conversationOpt.isEmpty()) {
                return;
            }
            ChatConversation conversation = conversationOpt.get();
            if (conversation.getDeleted() != null && conversation.getDeleted() == 1) {
                return;
            }

            long rounds = messageRepository.countConversationRoundsByConversationId(conversationId);
            int threshold = Math.max(1, ragConfig.getConversationSummaryThresholdRounds());
            if (rounds < threshold) {
                return;
            }

            int maxSequence = messageRepository.getMaxSequenceByConversationId(conversationId);
            Integer lastSummarySequence = conversation.getSummaryUpdatedSequence();
            int intervalMessages = Math.max(1, ragConfig.getConversationSummaryUpdateIntervalRounds()) * 2;
            if (lastSummarySequence != null && maxSequence - lastSummarySequence < intervalMessages) {
                return;
            }

            List<ChatMessage> sourceMessages = loadMessagesForSummary(conversationId, lastSummarySequence);
            if (sourceMessages.isEmpty()) {
                return;
            }

            Optional<QAModel> modelOpt = resolveModel(modelId != null ? modelId : conversation.getModelId());
            if (modelOpt.isEmpty()) {
                logger.debug("找不到可用模型，跳过会话摘要更新 - conversationId={}", conversationId);
                return;
            }

            String summary = generateSummary(modelOpt.get(), conversation.getSummary(), sourceMessages);
            if (summary == null || summary.trim().isEmpty()) {
                return;
            }

            conversation.setSummary(summary.trim());
            conversation.setSummaryUpdatedSequence(maxSequence);
            conversation.setSummaryUpdateTime(new Date());
            ChatDateTimeUtil.setUpdateTime(conversation);
            conversationRepository.save(conversation);
            logger.debug("会话摘要已更新 - conversationId={}, rounds={}, sequence={}, length={}",
                    conversationId, rounds, maxSequence, summary.length());
        } catch (Exception e) {
            logger.debug("会话摘要更新失败 - conversationId={}", conversationId, e);
        }
    }

    private List<ChatMessage> loadMessagesForSummary(Long conversationId, Integer lastSummarySequence) {
        int maxMessages = Math.max(4, ragConfig.getConversationSummaryMaxMessages());
        if (lastSummarySequence != null) {
            List<ChatMessage> messages = messageRepository.findByConversationIdAndSequenceGreaterThan(
                    conversationId, lastSummarySequence);
            if (messages.size() <= maxMessages) {
                return messages;
            }
            return messages.subList(messages.size() - maxMessages, messages.size());
        }

        List<ChatMessage> recentDesc = messageRepository.findRecentByConversationId(
                conversationId, PageRequest.of(0, maxMessages));
        List<ChatMessage> recent = new ArrayList<>(recentDesc);
        Collections.reverse(recent);
        return recent;
    }

    private Optional<QAModel> resolveModel(Long modelId) {
        Optional<QAModel> modelOpt = modelId != null ? qaModelRepository.findById(modelId) : Optional.empty();
        if (modelOpt.isEmpty()) {
            modelOpt = qaModelRepository.findDefaultByUseFor("chat");
        }
        if (modelOpt.isEmpty()) {
            modelOpt = qaModelRepository.findDefaultByUseFor("both");
        }
        return modelOpt.filter(model -> (model.getDeleted() == null || model.getDeleted() == 0)
                && Boolean.TRUE.equals(model.getEnabled()));
    }

    private String generateSummary(QAModel qaModel, String existingSummary, List<ChatMessage> messages) {
        List<dev.langchain4j.data.message.ChatMessage> summaryMessages = new ArrayList<>();
        String systemSkill = SkillLoader.loadSkill("dialog/summary_system_prompt");
        if (systemSkill == null || systemSkill.trim().isEmpty()) {
            systemSkill = SkillLoader.loadSkill("dialog/summary_system_prompt_fallback");
        }
        summaryMessages.add(SystemMessage.from(systemSkill));

        StringBuilder historyText = new StringBuilder();
        if (existingSummary != null && !existingSummary.trim().isEmpty()) {
            historyText.append("已有会话摘要:\n").append(existingSummary.trim()).append("\n\n");
            historyText.append("新增对话:\n");
        }
        for (ChatMessage message : messages) {
            if ("user".equalsIgnoreCase(message.getRole())) {
                historyText.append("用户: ").append(safeContent(message.getContent())).append("\n");
            } else if ("assistant".equalsIgnoreCase(message.getRole())) {
                historyText.append("助手: ").append(safeContent(message.getContent())).append("\n");
            }
        }

        String prompt = SkillLoader.loadSkillWithTemplate("dialog/summary_user_prompt_template", Map.of(
                "historyText", historyText.toString()));
        summaryMessages.add(UserMessage.from(prompt));

        modelLanguageModelFactory.setTraceSource("Conversation Summary");
        ChatLanguageModel model;
        try {
            model = modelLanguageModelFactory.createChatLanguageModel(qaModel);
        } finally {
            modelLanguageModelFactory.clearTraceSource();
        }
        Response<AiMessage> response = model.generate(summaryMessages);
        return response != null && response.content() != null ? response.content().text() : null;
    }

    private String safeContent(String content) {
        return content == null ? "" : content.trim();
    }
}
