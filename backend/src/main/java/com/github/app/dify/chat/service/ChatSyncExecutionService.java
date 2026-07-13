package com.github.app.dify.chat.service;

import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.util.TokenEstimator;
import com.github.app.dify.intent.service.IntentRecognitionService;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.memo.resp.MemoResp;
import com.github.app.dify.ops.trace.api.TraceFacade;
import com.github.app.dify.ops.trace.core.TraceSanitizer;
import com.github.app.dify.ops.trace.core.TraceStepCollector;
import com.github.app.dify.ops.trace.model.TraceHandle;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** 非流式问答执行流程。 */
@Service
public class ChatSyncExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(ChatSyncExecutionService.class);
    private final ModelLanguageModelFactory modelFactory;
    private final IntentRecognitionService intentService;
    private final ChatBrowserMcpEnhancementService browserService;
    private final ChatModelResolver modelResolver;
    private final ChatContextAssembler contextAssembler;
    private final ChatMessagePreparationService messagePreparationService;
    private final ChatConversationService conversationService;
    private final ChatTraceService traceService;
    private final TraceFacade traceFacade;
    private final TraceSanitizer traceSanitizer;
    private final Executor taskExecutor;

    public ChatSyncExecutionService(ModelLanguageModelFactory modelFactory,
            IntentRecognitionService intentService, ChatBrowserMcpEnhancementService browserService,
            ChatModelResolver modelResolver, ChatContextAssembler contextAssembler,
            ChatMessagePreparationService messagePreparationService, ChatConversationService conversationService,
            ChatTraceService traceService, TraceFacade traceFacade, TraceSanitizer traceSanitizer,
            @Qualifier("applicationTaskExecutor") Optional<Executor> taskExecutor) {
        this.modelFactory = modelFactory;
        this.intentService = intentService;
        this.browserService = browserService;
        this.modelResolver = modelResolver;
        this.contextAssembler = contextAssembler;
        this.messagePreparationService = messagePreparationService;
        this.conversationService = conversationService;
        this.traceService = traceService;
        this.traceFacade = traceFacade;
        this.traceSanitizer = traceSanitizer;
        this.taskExecutor = taskExecutor.orElse(null);
    }

    public ChatResponse execute(ChatRequest request, Long userId) {
        TraceHandle handle = traceService.start("chat_main", userId, request, false);
        TraceStepCollector steps = new TraceStepCollector(traceFacade, handle, traceSanitizer);
        try {
            CompletableFuture<String> searchFuture = startSearch(request);
            QAModel model = steps.trace("CHAT_MODEL_RESOLVE", "解析问答模型", "modelId=" + request.getModelId(),
                    () -> modelResolver.resolve(request.getModelId()),
                    item -> item == null ? "model=null" : "modelId=" + item.getId());
            if (model == null) {
                throw new IllegalStateException("未找到可用的问答模型，请先配置模型");
            }
            MemoResp memo = detectMemo(steps, request, userId, false);
            if (memo != null) {
                return finishMemo(handle, intentService.buildMemoConfirmationAnswer(memo), request, userId, model, memo);
            }
            if (!Boolean.FALSE.equals(request.getEnableMemo()) && intentService.hasMemoIntent(request.getQuestion())) {
                return finishMemo(handle, "我可以帮你创建备忘录，请告诉我具体在什么时候提醒。", request, userId, model, null);
            }
            ChatLanguageModel chatModel = steps.trace("CHAT_MODEL_BUILD", "创建聊天模型实例", "modelId=" + model.getId(),
                    () -> modelFactory.createChatLanguageModel(model), item -> "created=" + (item != null));
            List<ChatMessage> builtMessages = steps.trace("CHAT_MESSAGES_BUILD", "构建聊天消息", "",
                    () -> contextAssembler.buildMessages(request, awaitSearch(searchFuture, request), model, userId, memo),
                    item -> "message_count=" + (item == null ? 0 : item.size()));
            List<ChatMessage> messages = steps.trace("CHAT_CONTEXT_COMPRESS", "压缩聊天上下文", "before_size=" + builtMessages.size(),
                    () -> messagePreparationService.compress(builtMessages, request), item -> "after_size=" + (item == null ? 0 : item.size()));
            return generateAndSave(steps, handle, request, userId, model, chatModel, messages, memo);
        } catch (Exception e) {
            traceFacade.error(handle, e);
            logger.error("智能问答失败", e);
            throw new BusinessException("智能问答失败，请稍后重试", ErrorCode.SYSTEM_BUSY, e);
        }
    }

    private ChatResponse generateAndSave(TraceStepCollector steps, TraceHandle handle, ChatRequest request, Long userId,
            QAModel model, ChatLanguageModel chatModel, List<ChatMessage> messages, MemoResp memo) {
        try {
            if (request.getImages() != null && !request.getImages().isEmpty()) {
                modelFactory.setImageData(request.getImages());
            }
            Response<AiMessage> response = steps.trace("CHAT_LLM_GENERATE", "执行问答生成", "message_count=" + messages.size(),
                    () -> chatModel.generate(messages), item -> "generated=" + (item != null && item.content() != null));
            String answer = response.content().text();
            Long[] tokens = tokenUsage(response, messages, answer);
            ChatResponse result = steps.trace("CHAT_RESPONSE_BUILD", "构建并保存聊天响应", "answer_length=" + answer.length(),
                    () -> conversationService.buildResponse(answer, request, userId, null, model.getId(), tokens[0], tokens[1], tokens[2]),
                    item -> "conversationId=" + (item == null ? null : item.getConversationId()));
            result.setMemoId(null);
            result.setMemoCandidate(memo);
            traceFacade.success(handle, answer);
            return result;
        } finally {
            modelFactory.clearImageData();
        }
    }

    private Long[] tokenUsage(Response<AiMessage> response, List<ChatMessage> messages, String answer) {
        try {
            dev.langchain4j.model.output.TokenUsage usage = response.tokenUsage();
            if (usage != null) {
                return new Long[] { value(usage.inputTokenCount()), value(usage.outputTokenCount()), value(usage.totalTokenCount()) };
            }
        } catch (Exception | NoSuchMethodError ignored) {
            logger.debug("读取模型 Token 用量失败，将使用估算值");
        }
        return TokenEstimator.estimateTokenUsage(messages.toArray(new ChatMessage[0]), answer);
    }

    private Long value(Integer value) { return value == null ? null : value.longValue(); }

    private CompletableFuture<String> startSearch(ChatRequest request) {
        return Boolean.TRUE.equals(request.getEnableBrowserSearch()) && taskExecutor != null
                ? CompletableFuture.supplyAsync(() -> browserService.searchContext(request), taskExecutor) : null;
    }

    private String awaitSearch(CompletableFuture<String> future, ChatRequest request) {
        if (future == null) return Boolean.TRUE.equals(request.getEnableBrowserSearch()) ? browserService.searchContext(request) : "";
        try { return future.get(15, TimeUnit.SECONDS); }
        catch (Exception e) { logger.warn("浏览器检索失败或超时，继续原始问答", e); return ""; }
    }

    private MemoResp detectMemo(TraceStepCollector steps, ChatRequest request, Long userId, boolean stream) {
        if (Boolean.FALSE.equals(request.getEnableMemo())) return null;
        return steps.trace("CHAT_MEMO_DETECT", "识别备忘录意图" + (stream ? "（流式）" : ""), "question=" + request.getQuestion(),
                () -> intentService.previewMemo(userId, request.getQuestion()), item -> item == null ? "memo_candidate=false" : "memo_candidate=true");
    }

    private ChatResponse finishMemo(TraceHandle handle, String answer, ChatRequest request, Long userId, QAModel model, MemoResp memo) {
        ChatResponse response = conversationService.buildResponse(answer, request, userId, null, model.getId(), 0L, 0L, 0L);
        response.setMemoId(null);
        response.setMemoCandidate(memo);
        traceFacade.success(handle, answer);
        return response;
    }
}
