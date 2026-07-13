package com.github.app.dify.chat.service;

import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.util.TokenEstimator;
import com.github.app.dify.intent.service.IntentRecognitionService;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.StreamingChatLanguageModel;
import com.github.app.dify.memo.resp.MemoResp;
import com.github.app.dify.ops.trace.api.TraceFacade;
import com.github.app.dify.ops.trace.core.TraceSanitizer;
import com.github.app.dify.ops.trace.core.TraceStepCollector;
import com.github.app.dify.ops.trace.model.TraceHandle;
import dev.langchain4j.data.message.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 流式问答执行流程及流结束后的持久化处理。 */
@Service
public class ChatStreamExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(ChatStreamExecutionService.class);
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

    public ChatStreamExecutionService(ModelLanguageModelFactory modelFactory,
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

    public Flux<ChatResponse> execute(ChatRequest request, Long userId) {
        TraceHandle handle = traceService.start("chat_stream_main", userId, request, true);
        TraceStepCollector steps = new TraceStepCollector(traceFacade, handle, traceSanitizer);
        try {
            CompletableFuture<String> searchFuture = startSearch(request);
            QAModel model = steps.trace("CHAT_MODEL_RESOLVE", "解析问答模型（流式）", "modelId=" + request.getModelId(),
                    () -> modelResolver.resolve(request.getModelId()), item -> item == null ? "model=null" : "modelId=" + item.getId());
            if (model == null) {
                IllegalStateException error = new IllegalStateException("未找到可用的问答模型，请先配置模型");
                traceFacade.error(handle, error);
                return Flux.error(error);
            }
            MemoResp memo = detectMemo(steps, request, userId);
            AtomicReference<Long> conversationId = new AtomicReference<>(createConversation(request, userId));
            if (memo != null) return memoResponse(handle, request, userId, model, memo, conversationId.get());
            if (!Boolean.FALSE.equals(request.getEnableMemo()) && intentService.hasMemoIntent(request.getQuestion())) {
                return memoResponse(handle, request, userId, model, null, conversationId.get());
            }
            StreamingChatLanguageModel streamModel = steps.trace("CHAT_MODEL_BUILD", "创建聊天模型实例（流式）", "modelId=" + model.getId(),
                    () -> modelFactory.createStreamingChatLanguageModel(model), item -> "created=" + (item != null));
            List<ChatMessage> builtMessages = steps.trace("CHAT_MESSAGES_BUILD", "构建聊天消息（流式）", "",
                    () -> contextAssembler.buildMessages(request, awaitSearch(searchFuture, request), model, userId, memo),
                    item -> "message_count=" + (item == null ? 0 : item.size()));
            List<ChatMessage> messages = steps.trace("CHAT_CONTEXT_COMPRESS", "压缩聊天上下文（流式）", "before_size=" + builtMessages.size(),
                    () -> messagePreparationService.compress(builtMessages, request), item -> "after_size=" + (item == null ? 0 : item.size()));
            return generate(request, userId, model, memo, conversationId, streamModel, messages, handle);
        } catch (Exception e) {
            traceFacade.error(handle, e);
            logger.error("智能问答失败（流式）", e);
            return Flux.error(new BusinessException("智能问答失败，请稍后重试", ErrorCode.SYSTEM_BUSY, e));
        }
    }

    private Flux<ChatResponse> generate(ChatRequest request, Long userId, QAModel model, MemoResp memo,
            AtomicReference<Long> conversationId, StreamingChatLanguageModel streamModel,
            List<ChatMessage> messages, TraceHandle handle) {
        if (request.getImages() != null && !request.getImages().isEmpty()) modelFactory.setImageData(request.getImages());
        AtomicReference<String> answer = new AtomicReference<>("");
        return streamModel.generateStream(messages)
                .doFinally(signal -> modelFactory.clearImageData())
                .scan("", (accumulated, token) -> { String value = accumulated + token; answer.set(value); return value; })
                .skip(1)
                .map(value -> response(value, false, conversationId.get(), memo))
                .switchIfEmpty(Flux.defer(() -> {
                    ChatResponse emptyResponse = response("未收到LLM响应，请检查日志", true, conversationId.get(), memo);
                    traceFacade.success(handle, emptyResponse.getAnswer());
                    return Flux.just(emptyResponse);
                }))
                .concatWith(Flux.defer(() -> {
                    saveFinalAnswer(request, userId, model, messages, conversationId.get(), answer.get());
                    ChatResponse finalResponse = response(answer.get(), true, conversationId.get(), memo);
                    traceFacade.success(handle, answer.get());
                    return Flux.just(finalResponse);
                }))
                .onErrorResume(error -> {
                    traceFacade.error(handle, error);
                    logger.error("流式问答失败", error);
                    return Flux.just(response("系统繁忙，请稍后重试", true, conversationId.get(), memo));
                });
    }

    private Long createConversation(ChatRequest request, Long userId) {
        if (userId == null) return null;
        try {
            Long conversationId = conversationService.createConversationAndSaveUserMessage(request, userId, true);
            modelFactory.setConversationId(conversationId.toString());
            return conversationId;
        } catch (Exception e) {
            logger.error("创建会话失败（流式）", e);
            return null;
        }
    }

    private Flux<ChatResponse> memoResponse(TraceHandle handle, ChatRequest request, Long userId, QAModel model,
            MemoResp memo, Long conversationId) {
        String answer = memo == null ? "我可以帮你创建备忘录，请告诉我具体在什么时候提醒。"
                : intentService.buildMemoConfirmationAnswer(memo);
        if (userId != null && conversationId != null) {
            try { conversationService.saveAssistantMessage(conversationId, answer, model.getId(), 0L, 0L, 0L); }
            catch (Exception e) { logger.error("保存备忘录消息失败（流式）", e); }
        }
        traceFacade.success(handle, answer);
        return Flux.just(response(answer, true, conversationId, memo));
    }

    private void saveFinalAnswer(ChatRequest request, Long userId, QAModel model, List<ChatMessage> messages,
            Long conversationId, String answer) {
        if (userId == null || conversationId == null || answer.trim().isEmpty()) return;
        try {
            Long[] tokens = TokenEstimator.estimateTokenUsage(messages.toArray(new ChatMessage[0]), answer);
            conversationService.saveAssistantMessage(conversationId, answer, model.getId(), tokens[0], tokens[1], tokens[2]);
            conversationService.updateMemory(userId, conversationService.getHistoryQuestion(request), answer, model.getId(), conversationId);
        } catch (Exception e) { logger.error("保存助手消息失败（流式）", e); }
    }

    private ChatResponse response(String answer, boolean finished, Long conversationId, MemoResp memo) {
        ChatResponse response = new ChatResponse();
        response.setAnswer(answer);
        response.setFinished(finished);
        response.setConversationId(conversationId);
        response.setMemoId(null);
        response.setMemoCandidate(memo);
        return response;
    }

    private CompletableFuture<String> startSearch(ChatRequest request) {
        return Boolean.TRUE.equals(request.getEnableBrowserSearch()) && taskExecutor != null
                ? CompletableFuture.supplyAsync(() -> browserService.searchContext(request), taskExecutor) : null;
    }

    private String awaitSearch(CompletableFuture<String> future, ChatRequest request) {
        if (future == null) return Boolean.TRUE.equals(request.getEnableBrowserSearch()) ? browserService.searchContext(request) : "";
        try { return future.get(15, TimeUnit.SECONDS); }
        catch (Exception e) { logger.warn("浏览器检索失败或超时，继续原始问答", e); return ""; }
    }

    private MemoResp detectMemo(TraceStepCollector steps, ChatRequest request, Long userId) {
        if (Boolean.FALSE.equals(request.getEnableMemo())) return null;
        return steps.trace("CHAT_MEMO_DETECT", "识别备忘录意图（流式）", "question=" + request.getQuestion(),
                () -> intentService.previewMemo(userId, request.getQuestion()), item -> item == null ? "memo_candidate=false" : "memo_candidate=true");
    }
}
