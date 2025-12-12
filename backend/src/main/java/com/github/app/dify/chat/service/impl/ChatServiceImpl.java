package com.github.app.dify.chat.service.impl;

import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.langchain4j.StreamingChatLanguageModel;
import com.github.app.dify.chat.mcp.McpBrowserSearchService;
import com.github.app.dify.chat.mcp.McpLocationService;
import com.github.app.dify.chat.mcp.McpTimeService;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.chat.service.ChatHistoryService;
import com.github.app.dify.chat.service.ChatService;
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
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 智能问答服务实现（直接对话，不使用知识库）
 */
@Service
public class ChatServiceImpl implements ChatService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    
    @Autowired
    private QAModelRepository qaModelRepository;
    
    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;
    
    @Autowired
    private ContextCompressionService contextCompressionService;
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
    @Autowired
    private McpBrowserSearchService mcpBrowserSearchService;
    
    @Autowired
    private McpTimeService mcpTimeService;
    
    @Autowired
    private McpLocationService mcpLocationService;
    
    @Override
    public ChatResponse chat(ChatRequest request, Long userId) {
        try {
            // 获取模型配置
            QAModel qaModel = getQAModel(request.getModelId());
            if (qaModel == null) {
                throw new IllegalStateException("未找到可用的问答模型，请先配置模型");
            }
            
            logger.info("使用问答模型: {} (ID: {})", qaModel.getName(), qaModel.getId());
            
            // 创建模型实例
            ChatLanguageModel chatLanguageModel = 
                    modelLanguageModelFactory.createChatLanguageModel(qaModel);
            
            // 如果启用了MCP支持，直接使用浏览器检索（不再进行检测）
            String browserSearchContext = "";
            if (Boolean.TRUE.equals(request.getEnableBrowserSearch())) {
                logger.info("MCP支持已开启，直接启用浏览器检索 - 查询: {}", request.getQuestion());
                try {
                    List<McpBrowserSearchService.SearchResult> searchResults = 
                            mcpBrowserSearchService.search(request.getQuestion(), 5);
                    if (searchResults != null && !searchResults.isEmpty()) {
                        browserSearchContext = mcpBrowserSearchService.formatSearchResultsForContext(searchResults);
                        logger.info("浏览器检索完成 - 找到 {} 个结果，检索内容长度: {} 字符", 
                                searchResults.size(), browserSearchContext.length());
                        // 记录检索结果详情（仅记录前200字符，避免日志过长）
                        logger.debug("检索结果预览: {}", 
                                browserSearchContext.length() > 200 ? 
                                browserSearchContext.substring(0, 200) + "..." : browserSearchContext);
                    } else {
                        logger.warn("浏览器检索未找到结果 - 查询: {}", request.getQuestion());
                        // 即使没有找到结果，也告知LLM已尝试检索
                        browserSearchContext = "【网络搜索提示】已启用MCP支持（浏览器检索功能），但未找到与问题相关的搜索结果。\n\n" +
                                "问题：" + request.getQuestion() + "\n\n" +
                                "请基于你的知识来回答这个问题。如果问题涉及实时信息或最新动态，请说明需要访问相关网站获取最新信息。";
                    }
                } catch (Exception e) {
                    logger.error("浏览器检索失败，继续使用原始问题 - 查询: {}", request.getQuestion(), e);
                    // 不抛出异常，继续使用原始问题
                }
            } else {
                logger.info("MCP支持已关闭，跳过浏览器检索");
            }
            
            // 构建消息列表（包含历史对话）
            List<ChatMessage> messages = buildMessages(request, browserSearchContext);
            
            // 记录历史对话信息
            if (request.getHistory() != null && !request.getHistory().isEmpty()) {
                logger.info("使用历史对话，历史消息数量: {}", request.getHistory().size());
            }
            logger.debug("构建的消息列表大小: {}", messages.size());
            
            // 应用上下文压缩策略（转换为KnowledgeBaseQARequest格式以复用压缩逻辑）
            com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest kbRequest = convertToKBQARequest(request);
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
    
    @Override
    public Flux<ChatResponse> chatStream(ChatRequest request, Long userId) {
        try {
            // 获取模型配置
            QAModel qaModel = getQAModel(request.getModelId());
            if (qaModel == null) {
                return Flux.error(new IllegalStateException("未找到可用的问答模型，请先配置模型"));
            }
            
            logger.info("使用问答模型（流式）: {} (ID: {})", qaModel.getName(), qaModel.getId());
            
            // 创建流式模型实例
            StreamingChatLanguageModel streamingChatLanguageModel = 
                    modelLanguageModelFactory.createStreamingChatLanguageModel(qaModel);
            
            // 如果启用了MCP支持，直接使用浏览器检索（不再进行检测）
            String browserSearchContext = "";
            if (Boolean.TRUE.equals(request.getEnableBrowserSearch())) {
                logger.info("MCP支持已开启（流式），直接启用浏览器检索 - 查询: {}", request.getQuestion());
                try {
                    List<McpBrowserSearchService.SearchResult> searchResults = 
                            mcpBrowserSearchService.search(request.getQuestion(), 5);
                    if (searchResults != null && !searchResults.isEmpty()) {
                        browserSearchContext = mcpBrowserSearchService.formatSearchResultsForContext(searchResults);
                        logger.info("浏览器检索完成（流式） - 找到 {} 个结果，检索内容长度: {} 字符", 
                                searchResults.size(), browserSearchContext.length());
                        // 记录检索结果详情（仅记录前200字符，避免日志过长）
                        logger.debug("检索结果预览（流式）: {}", 
                                browserSearchContext.length() > 200 ? 
                                browserSearchContext.substring(0, 200) + "..." : browserSearchContext);
                    } else {
                        logger.warn("浏览器检索未找到结果（流式） - 查询: {}", request.getQuestion());
                        // 即使没有找到结果，也告知LLM已尝试检索
                        browserSearchContext = "【网络搜索提示】已启用MCP支持（浏览器检索功能），但未找到与问题相关的搜索结果。\n\n" +
                                "问题：" + request.getQuestion() + "\n\n" +
                                "请基于你的知识来回答这个问题。如果问题涉及实时信息或最新动态，请说明需要访问相关网站获取最新信息。";
                    }
                } catch (Exception e) {
                    logger.error("浏览器检索失败（流式），继续使用原始问题 - 查询: {}", request.getQuestion(), e);
                    // 不抛出异常，继续使用原始问题
                }
            } else {
                logger.info("MCP支持已关闭（流式），跳过浏览器检索");
            }
            
            // 构建消息列表（包含历史对话）
            List<ChatMessage> messages = buildMessages(request, browserSearchContext);
            
            // 应用上下文压缩策略（转换为KnowledgeBaseQARequest格式以复用压缩逻辑）
            com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest kbRequest = convertToKBQARequest(request);
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
            final AtomicReference<Long> conversationIdRef = new AtomicReference<>(null);
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
            final AtomicReference<String> lastAnswer = new AtomicReference<>("");
            
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
     * 构建消息列表（包含历史对话和浏览器检索结果）
     */
    private List<ChatMessage> buildMessages(ChatRequest request, String browserSearchContext) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 构建系统消息
        StringBuilder systemMessageBuilder = new StringBuilder();
        systemMessageBuilder.append("你是一个专业的AI助手，能够回答各种问题，特别擅长编程和技术问题。\n\n");
        
        // 如果启用了MCP支持，添加时间信息和地理位置信息
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        if (Boolean.TRUE.equals(request.getEnableBrowserSearch())) {
            // 获取当前时间信息（用于时效性判断）
            String currentTimeInfo = mcpTimeService.getFormattedTimeInfo();
            McpTimeService.TimeInfo timeInfo = mcpTimeService.getCurrentTime();
            currentYear = timeInfo.getYear();
            systemMessageBuilder.append(currentTimeInfo);
            
            // 获取地理位置信息
            try {
                String locationInfo = mcpLocationService.getFormattedLocationInfo();
                systemMessageBuilder.append("\n\n");
                systemMessageBuilder.append(locationInfo);
            } catch (Exception e) {
                logger.warn("获取地理位置信息失败，跳过", e);
                // 不抛出异常，继续执行
            }
        } else {
            // MCP支持关闭时，只提供基本的年份信息（不告知用户MCP已关闭）
            systemMessageBuilder.append("【当前时间信息】\n");
            systemMessageBuilder.append(String.format("当前年份：%d年\n", currentYear));
        }
        
        // 如果提供了浏览器检索结果，在系统消息中强调要使用检索结果
        if (browserSearchContext != null && !browserSearchContext.trim().isEmpty()) {
            
            systemMessageBuilder.append("\n\n【重要提示】当用户问题中包含网络搜索结果时，你必须：");
            systemMessageBuilder.append("\n1. 优先使用搜索结果中的信息来回答问题");
            systemMessageBuilder.append("\n2. 在回答中明确引用搜索结果中的内容，并标注来源链接");
            systemMessageBuilder.append("\n3. 当前年份是").append(currentYear).append("年，请根据信息的时效性自行判断是否需要提醒用户信息可能已过期");
            systemMessageBuilder.append("\n4. 如果搜索结果与问题相关，必须基于搜索结果来回答，不要仅依赖你的训练数据");
            systemMessageBuilder.append("\n5. 如果搜索结果与问题不相关，可以结合你的知识来回答，但要说明信息来源");
        }
        
        systemMessageBuilder.append("\n\n重要：请使用Markdown格式来组织你的回答，包括：\n" +
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
                "7. 如果回答涉及数学、物理、工程等领域的公式，必须使用上述格式完整写出，不要省略或使用占位符");
        
        // 添加系统消息
        messages.add(SystemMessage.from(systemMessageBuilder.toString()));
        
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
        
        // 构建用户消息：如果有检索结果，将检索结果和问题一起发送
        String userMessageContent;
        if (Boolean.TRUE.equals(request.getEnableBrowserSearch())) {
            // MCP支持已开启
            if (browserSearchContext != null && !browserSearchContext.trim().isEmpty()) {
                // 将检索结果和问题组合在一起，让LLM更容易理解要使用检索结果
                userMessageContent = browserSearchContext + 
                        "\n\n---\n\n" +
                        "基于以上网络搜索结果，请回答以下问题：\n" + 
                        request.getQuestion() +
                        "\n\n【重要要求】\n" +
                        "1. 必须优先使用上述搜索结果中的信息来回答问题\n" +
                        "2. 如果搜索结果中包含相关信息，必须明确引用并标注来源链接\n" +
                        "3. 当前年份是" + currentYear + "年，请根据信息的时效性自行判断是否需要提醒用户信息可能已过期\n" +
                        "4. 如果搜索结果与问题不相关，请明确说明\"未在搜索结果中找到相关信息\"，然后可以结合你的知识回答\n" +
                        "5. 绝对不要声称搜索结果包含信息，如果搜索结果中没有相关内容，请明确说明";
            } else {
                // 如果启用了MCP支持但没有找到结果，明确告知LLM
                userMessageContent = "【网络搜索提示】已启用MCP支持（浏览器检索功能），但未找到与问题相关的搜索结果。\n\n" +
                        "问题：" + request.getQuestion() + "\n\n" +
                        "请基于你的知识来回答这个问题。如果问题涉及实时信息或最新动态，请说明需要访问相关网站获取最新信息。";
            }
        } else {
            // MCP支持已关闭，直接使用原始问题
            userMessageContent = request.getQuestion();
        }
        
        // 添加当前问题（包含检索结果）
        messages.add(UserMessage.from(userMessageContent));
        
        return messages;
    }
    
    /**
     * 将ChatRequest转换为KnowledgeBaseQARequest（用于上下文压缩）
     */
    private com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest convertToKBQARequest(ChatRequest request) {
        com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest kbRequest =
                new com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest();
        kbRequest.setQuestion(request.getQuestion());
        kbRequest.setHistory(convertHistory(request.getHistory()));
        return kbRequest;
    }
    
    /**
     * 转换历史消息格式
     */
    private List<com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest.Message> convertHistory(
            List<ChatRequest.Message> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        
        List<com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest.Message> kbHistory = new ArrayList<>();
        for (ChatRequest.Message msg : history) {
            com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest.Message kbMsg =
                    new com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest.Message();
            kbMsg.setRole(msg.getRole());
            kbMsg.setContent(msg.getContent());
            kbHistory.add(kbMsg);
        }
        return kbHistory;
    }
    
    /**
     * 获取问答模型
     * 如果指定了modelId，则使用指定的模型；否则使用默认模型
     */
    private QAModel getQAModel(Long modelId) {
        if (modelId != null) {
            // 使用指定的模型
            Optional<QAModel> optional = qaModelRepository.findById(modelId);
            if (optional.isPresent()) {
                QAModel model = optional.get();
                // 检查模型是否启用且未删除
                if ((model.getDeleted() == null || model.getDeleted() == 0) 
                        && model.getEnabled() != null && model.getEnabled()) {
                    // 检查使用场景
                    if ("chat".equals(model.getUseFor()) || "both".equals(model.getUseFor())) {
                        return model;
                    }
                }
            }
            throw new IllegalStateException("指定的模型不可用或未启用");
        } else {
            // 使用默认模型（使用场景为 chat 或 both）
            Optional<QAModel> defaultModel = qaModelRepository.findDefaultByUseFor("chat");
            if (defaultModel.isPresent()) {
                QAModel model = defaultModel.get();
                if (model.getEnabled() != null && model.getEnabled()) {
                    return model;
                }
            }
            
            // 如果没有默认模型，尝试获取第一个启用的模型
            List<QAModel> enabledModels = qaModelRepository.findByUseFor("chat");
            if (!enabledModels.isEmpty()) {
                return enabledModels.get(0);
            }
            
            // 如果数据库中没有模型，返回null
            return null;
        }
    }
}

