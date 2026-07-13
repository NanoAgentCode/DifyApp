package com.github.app.dify.chat.service;

import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.ops.trace.api.TraceFacade;
import com.github.app.dify.ops.trace.model.TraceHandle;
import com.github.app.dify.ops.trace.model.TraceStartRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatTraceService {

    private static final Logger logger = LoggerFactory.getLogger(ChatTraceService.class);
    private final TraceFacade traceFacade;
    private final ModelLanguageModelFactory modelFactory;

    public ChatTraceService(TraceFacade traceFacade, ModelLanguageModelFactory modelFactory) {
        this.traceFacade = traceFacade;
        this.modelFactory = modelFactory;
    }

    public TraceHandle start(String source, Long userId, ChatRequest request, boolean stream) {
        try {
            TraceStartRequest trace = new TraceStartRequest();
            trace.setTraceSource(source);
            trace.setConversationId(request != null ? request.getConversationId() : null);
            trace.setUserId(userId);
            trace.setRequestType(stream ? "chat_stream" : "chat");
            trace.setBusinessId(null);
            trace.setRequestSummary(request == null ? null : request.getQuestion());
            TraceHandle handle = traceFacade.start(trace);
            if (handle != null && handle.getTraceId() != null && !handle.getTraceId().isBlank()) {
                modelFactory.setTraceId(handle.getTraceId());
                modelFactory.markBusinessTraceStarted();
            }
            return handle;
        } catch (Exception e) {
            logger.debug("启动chat业务追踪失败，降级继续", e);
            return null;
        }
    }
}
