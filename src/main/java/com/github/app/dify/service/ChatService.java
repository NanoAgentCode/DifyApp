package com.github.app.dify.service;

import com.github.app.dify.config.RagConfig;
import com.github.app.dify.langchain4j.CustomChatLanguageModel;
import com.github.app.dify.langchain4j.CustomStreamingChatLanguageModel;
import com.github.app.dify.req.ChatRequest;
import com.github.app.dify.resp.ChatResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能问答服务（直接对话，不使用知识库）
 */
@Service
public class ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    
    @Autowired
    private RagConfig ragConfig;
    
    @Autowired
    private CustomChatLanguageModel chatLanguageModel;
    
    @Autowired
    private CustomStreamingChatLanguageModel streamingChatLanguageModel;
    
    @Autowired
    private ContextCompressionService contextCompressionService;
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
    /**
     * 智能问答（非流式）
     */
    public ChatResponse chat(ChatRequest request, Long userId) {
        try {
            // 检查LLM配置
            if (ragConfig.getLlmApiUrl() == null || ragConfig.getLlmApiUrl().trim().isEmpty()) {
                throw new IllegalStateException("LLM API URL未配置，请在application.yml中配置rag.llm-api-url");
            }
            
            // 构建消息列表（包含历史对话）
            List<ChatMessage> messages = buildMessages(request);
            
            // 记录历史对话信息
            if (request.getHistory() != null && !request.getHistory().isEmpty()) {
                logger.info("使用历史对话，历史消息数量: {}", request.getHistory().size());
            }
            logger.debug("构建的消息列表大小: {}", messages.size());
            
            // 应用上下文压缩策略（转换为KnowledgeBaseQARequest格式以复用压缩逻辑）
            com.github.app.dify.req.KnowledgeBaseQARequest kbRequest = convertToKBQARequest(request);
            messages = contextCompressionService.compressContext(messages, kbRequest);
            logger.debug("压缩后的消息列表大小: {}", messages.size());
            
            // 调用LLM生成答案
            Response<AiMessage> response = chatLanguageModel.generate(messages);
            String answer = response.content().text();
            
            // 保存历史记录
            Long conversationId = null;
            if (userId != null) {
                try {
                    Long requestConversationId = null;
                    if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                        try {
                            requestConversationId = Long.parseLong(request.getConversationId());
                        } catch (NumberFormatException e) {
                            logger.warn("无效的conversationId: {}", request.getConversationId());
                        }
                    }
                    conversationId = chatHistoryService.getOrCreateConversation(
                            userId, requestConversationId, 1, null, null, request.getQuestion());
                    logger.info("非流式响应 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}", 
                            requestConversationId, conversationId);
                    chatHistoryService.saveMessage(conversationId, "user", request.getQuestion());
                    chatHistoryService.saveMessage(conversationId, "assistant", answer);
                } catch (Exception e) {
                    logger.error("保存历史记录失败", e);
                    // 不抛出异常，避免影响主流程
                }
            }
            
            // 构建响应
            ChatResponse chatResponse = new ChatResponse();
            chatResponse.setAnswer(answer);
            chatResponse.setConversationId(conversationId);
            
            logger.info("智能问答完成 - 问题: {}", request.getQuestion());
            
            return chatResponse;
            
        } catch (Exception e) {
            logger.error("智能问答失败", e);
            throw new RuntimeException("智能问答失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 智能问答（流式）
     */
    public Flux<ChatResponse> chatStream(ChatRequest request, Long userId) {
        try {
            // 检查LLM配置
            if (ragConfig.getLlmApiUrl() == null || ragConfig.getLlmApiUrl().trim().isEmpty()) {
                return Flux.error(new IllegalStateException("LLM API URL未配置，请在application.yml中配置rag.llm-api-url"));
            }
            
            // 构建消息列表（包含历史对话）
            List<ChatMessage> messages = buildMessages(request);
            
            // 应用上下文压缩策略（转换为KnowledgeBaseQARequest格式以复用压缩逻辑）
            com.github.app.dify.req.KnowledgeBaseQARequest kbRequest = convertToKBQARequest(request);
            messages = contextCompressionService.compressContext(messages, kbRequest);
            logger.debug("压缩后的消息列表大小（流式）: {}", messages.size());
            
            // 调用流式LLM生成答案
            logger.info("开始调用流式LLM生成答案 - 消息数量: {}", messages.size());
            Flux<String> tokenFlux = streamingChatLanguageModel.generateStream(messages)
                    .doOnSubscribe(subscription -> {
                        logger.info("开始订阅token流");
                    })
                    .doOnNext(token -> {
                        logger.debug("收到token: {}", token.length() > 50 ? token.substring(0, 50) + "..." : token);
                    })
                    .doOnComplete(() -> {
                        logger.info("token流完成");
                    })
                    .doOnError(error -> {
                        logger.error("token流发生错误", error);
                    });
            
            // 在流式响应开始前，先创建或获取会话（这样可以在第一个数据包就返回 conversationId）
            final java.util.concurrent.atomic.AtomicReference<Long> conversationIdRef = 
                    new java.util.concurrent.atomic.AtomicReference<>(null);
            if (userId != null) {
                try {
                    Long requestConversationId = null;
                    if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                        try {
                            requestConversationId = Long.parseLong(request.getConversationId());
                        } catch (NumberFormatException e) {
                            logger.warn("无效的conversationId: {}", request.getConversationId());
                        }
                    }
                    Long conversationId = chatHistoryService.getOrCreateConversation(
                            userId, requestConversationId, 1, null, null, request.getQuestion());
                    conversationIdRef.set(conversationId);
                    logger.info("流式响应开始 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}", 
                            requestConversationId, conversationId);
                    // 先保存用户消息
                    chatHistoryService.saveMessage(conversationId, "user", request.getQuestion());
                } catch (Exception e) {
                    logger.error("创建会话失败（流式）", e);
                    // 不抛出异常，避免影响主流程
                }
            }
            
            // 使用scan累积答案，并保存最后一个完整答案
            final java.util.concurrent.atomic.AtomicReference<String> lastAnswer = 
                    new java.util.concurrent.atomic.AtomicReference<>("");
            
            return tokenFlux
                    .scan("", (accumulated, token) -> {
                        String newAccumulated = accumulated + token;
                        lastAnswer.set(newAccumulated);
                        logger.debug("累积答案，当前长度: {}", newAccumulated.length());
                        return newAccumulated;
                    })
                    .skip(1) // 跳过第一个空字符串（scan 的初始值）
                    .map(fullAnswer -> {
                        ChatResponse response = new ChatResponse();
                        response.setAnswer(fullAnswer);
                        response.setFinished(false);
                        // 在流式响应过程中，也包含 conversationId，这样前端可以立即更新
                        response.setConversationId(conversationIdRef.get());
                        return response;
                    })
                    .doOnNext(response -> {
                        logger.debug("发送流式响应，答案长度: {}", response.getAnswer().length());
                    })
                    .switchIfEmpty(Flux.defer(() -> {
                        logger.warn("没有收到任何token，发送空响应");
                        ChatResponse emptyResponse = new ChatResponse();
                        emptyResponse.setAnswer("未收到LLM响应，请检查日志");
                        emptyResponse.setFinished(true);
                        return Flux.just(emptyResponse);
                    }))
                    .concatWith(Flux.defer(() -> {
                        String finalAnswer = lastAnswer.get();
                        logger.info("发送最终响应标记，完整答案长度: {}", finalAnswer.length());
                        
                        // 保存助手消息（会话已在开始时创建）
                        Long conversationId = conversationIdRef.get();
                        if (userId != null && conversationId != null && finalAnswer != null && !finalAnswer.trim().isEmpty()) {
                            try {
                                chatHistoryService.saveMessage(conversationId, "assistant", finalAnswer);
                                logger.info("流式响应完成 - 保存助手消息到会话: {}", conversationId);
                            } catch (Exception e) {
                                logger.error("保存助手消息失败（流式）", e);
                                // 不抛出异常，避免影响主流程
                            }
                        }
                        
                        ChatResponse finalResponse = new ChatResponse();
                        finalResponse.setAnswer(finalAnswer);
                        finalResponse.setFinished(true);
                        finalResponse.setConversationId(conversationId);
                        return Flux.just(finalResponse);
                    }))
                    .onErrorResume(error -> {
                        logger.error("流式问答失败", error);
                        ChatResponse errorResponse = new ChatResponse();
                        errorResponse.setAnswer("生成答案时发生错误: " + error.getMessage());
                        errorResponse.setFinished(true);
                        return Flux.just(errorResponse);
                    });
            
        } catch (Exception e) {
            logger.error("智能问答失败（流式）", e);
            return Flux.error(new RuntimeException("智能问答失败: " + e.getMessage(), e));
        }
    }
    
    /**
     * 构建消息列表（包含历史对话）
     */
    private List<ChatMessage> buildMessages(ChatRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 添加系统消息
        messages.add(SystemMessage.from("你是一个专业的AI助手，能够回答各种问题，特别擅长编程和技术问题。" +
                "\n\n重要：请使用Markdown格式来组织你的回答，包括：\n" +
                "- 使用标题（#、##、###）来组织内容结构\n" +
                "- 使用列表（-、*、1.）来列举要点\n" +
                "- 使用代码块（```）来展示代码或技术内容\n" +
                "- 使用**粗体**和*斜体*来强调重要信息\n" +
                "- 使用表格来展示结构化数据\n" +
                "\n【关键要求】代码块格式（必须严格遵守）：\n" +
                "1. 所有代码块必须包含语言标识符，格式为：```语言标识符\n代码内容\n```\n" +
                "2. 语言标识符示例：\n" +
                "   - JavaScript代码：```javascript\n代码\n```\n" +
                "   - Python代码：```python\n代码\n```\n" +
                "   - Java代码：```java\n代码\n```\n" +
                "   - TypeScript代码：```typescript\n代码\n```\n" +
                "   - Go代码：```go\n代码\n```\n" +
                "   - Rust代码：```rust\n代码\n```\n" +
                "   - C/C++代码：```cpp\n代码\n``` 或 ```c\n代码\n```\n" +
                "   - C#代码：```csharp\n代码\n```\n" +
                "   - PHP代码：```php\n代码\n```\n" +
                "   - Ruby代码：```ruby\n代码\n```\n" +
                "   - Swift代码：```swift\n代码\n```\n" +
                "   - Kotlin代码：```kotlin\n代码\n```\n" +
                "   - SQL代码：```sql\n代码\n```\n" +
                "   - HTML代码：```html\n代码\n```\n" +
                "   - CSS代码：```css\n代码\n```\n" +
                "   - JSON代码：```json\n代码\n```\n" +
                "   - XML代码：```xml\n代码\n```\n" +
                "   - YAML代码：```yaml\n代码\n```\n" +
                "   - Bash/Shell代码：```bash\n代码\n``` 或 ```shell\n代码\n```\n" +
                "3. 绝对禁止使用没有语言标识符的代码块（如 ```\n代码\n```），这会导致代码无法正确高亮显示\n" +
                "4. 在流式响应中，生成代码块时必须在第一行就包含完整的 ```语言标识符，例如：```javascript\n" +
                "5. 代码块中的代码应该完整、可运行，并包含必要的注释\n" +
                "6. 如果用户输入包含代码，请确保在回答中正确使用带语言标识符的代码块格式展示\n" +
                "\n【关键要求】数学公式格式（必须严格遵守）：\n" +
                "1. 所有数学公式必须使用LaTeX格式编写，不要使用占位符或省略公式内容\n" +
                "2. 行内公式使用 $...$ 格式，例如：$E = mc^2$ 或 $\\phi = \\frac{1+\\sqrt{5}}{2}$\n" +
                "3. 块级公式使用 $$...$$ 格式，例如：\n" +
                "   $$F(n) = \\frac{1}{\\sqrt{5}} \\left( \\left( \\frac{1 + \\sqrt{5}}{2} \\right)^n - \\left( \\frac{1 - \\sqrt{5}}{2} \\right)^n \\right)$$\n" +
                "4. 也可以使用 [...] 格式表示块级公式，例如：\n" +
                "   [ f(x) = \\sum_{n=0}^{\\infty} \\frac{f^{(n)}(a)}{n!}(x-a)^n ]\n" +
                "5. 绝对禁止使用占位符（如 <!--KATEX_FORMULA_X--> 或类似格式），必须写出完整的LaTeX公式\n" +
                "6. 公式中的特殊字符需要使用反斜杠转义，例如：\\frac{分子}{分母}、\\sqrt{内容}、\\sum_{i=1}^{n} 等\n" +
                "7. 如果回答涉及数学、物理、工程等领域的公式，必须使用上述格式完整写出，不要省略或使用占位符"));
        
        // 添加历史对话
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            for (ChatRequest.Message historyMsg : request.getHistory()) {
                if ("user".equalsIgnoreCase(historyMsg.getRole())) {
                    messages.add(UserMessage.from(historyMsg.getContent()));
                } else if ("assistant".equalsIgnoreCase(historyMsg.getRole())) {
                    messages.add(AiMessage.from(historyMsg.getContent()));
                }
            }
        }
        
        // 添加当前问题
        messages.add(UserMessage.from(request.getQuestion()));
        
        return messages;
    }
    
    /**
     * 将ChatRequest转换为KnowledgeBaseQARequest（用于上下文压缩）
     */
    private com.github.app.dify.req.KnowledgeBaseQARequest convertToKBQARequest(ChatRequest request) {
        com.github.app.dify.req.KnowledgeBaseQARequest kbRequest = 
                new com.github.app.dify.req.KnowledgeBaseQARequest();
        kbRequest.setQuestion(request.getQuestion());
        kbRequest.setHistory(convertHistory(request.getHistory()));
        return kbRequest;
    }
    
    /**
     * 转换历史消息格式
     */
    private List<com.github.app.dify.req.KnowledgeBaseQARequest.Message> convertHistory(
            List<ChatRequest.Message> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        
        List<com.github.app.dify.req.KnowledgeBaseQARequest.Message> kbHistory = new ArrayList<>();
        for (ChatRequest.Message msg : history) {
            com.github.app.dify.req.KnowledgeBaseQARequest.Message kbMsg = 
                    new com.github.app.dify.req.KnowledgeBaseQARequest.Message();
            kbMsg.setRole(msg.getRole());
            kbMsg.setContent(msg.getContent());
            kbHistory.add(kbMsg);
        }
        return kbHistory;
    }
}

