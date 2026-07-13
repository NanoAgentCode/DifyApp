package com.github.app.dify.chat.service;

import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.common.util.ConversationIdUtil;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.memory.service.UserMemoryService;
import com.github.app.dify.mcp.location.McpLocationService;
import com.github.app.dify.mcp.time.McpTimeService;
import com.github.app.dify.system.util.SkillLoader;
import com.github.app.dify.system.util.SkillPaths;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatContextAssembler {
    private static final Logger logger = LoggerFactory.getLogger(ChatContextAssembler.class);
    private static final DateTimeFormatter RETRIEVAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");

    private final McpTimeService mcpTimeService;
    private final McpLocationService mcpLocationService;
    private final UserMemoryService userMemoryService;
    private final ChatHistoryService chatHistoryService;
    private final com.github.app.dify.intent.service.IntentRecognitionService intentRecognitionService;

    public ChatContextAssembler(McpTimeService mcpTimeService, Optional<McpLocationService> mcpLocationService,
            UserMemoryService userMemoryService, ChatHistoryService chatHistoryService,
            com.github.app.dify.intent.service.IntentRecognitionService intentRecognitionService) {
        this.mcpTimeService = mcpTimeService;
        this.mcpLocationService = mcpLocationService.orElse(null);
        this.userMemoryService = userMemoryService;
        this.chatHistoryService = chatHistoryService;
        this.intentRecognitionService = intentRecognitionService;
    }

    public List<ChatMessage> buildMessages(ChatRequest request, String browserSearchContext, QAModel qaModel,
            Long userId, com.github.app.dify.memo.resp.MemoResp memo) {
        List<ChatMessage> messages = new ArrayList<>();

        // 检查模型是否支持视觉输入
        boolean supportsVision = qaModel != null &&
                Boolean.TRUE.equals(qaModel.getSupportsVision()) &&
                Boolean.TRUE.equals(qaModel.getSupportsMultimodal());

        String base = SkillLoader.loadSkill(SkillPaths.CHAT_SYSTEM_PROMPT);
        StringBuilder systemMessageBuilder = new StringBuilder();
        if (!base.trim().isEmpty()) {
            systemMessageBuilder.append(base.trim()).append("\n\n");
        } else {
            // 使用 fallback
            String fallback = SkillLoader.loadSkill(SkillPaths.CHAT_SYSTEM_PROMPT_FALLBACK);
            if (!fallback.trim().isEmpty()) {
                systemMessageBuilder.append(fallback.trim()).append("\n\n");
            } else {
                systemMessageBuilder.append(SkillLoader.loadSkill("chat/system_prompt_default").trim()).append("\n\n");
            }
        }

        // 如果模型支持视觉输入，添加图片处理说明
        if (supportsVision) {
            String visionCapability = SkillLoader.loadSkill(SkillPaths.CHAT_VISION_CAPABILITY);
            if (!visionCapability.trim().isEmpty()) {
                systemMessageBuilder.append(visionCapability.trim()).append("\n\n");
            } else {
                systemMessageBuilder.append(SkillLoader.loadSkill("chat/vision_capability_fallback").trim()).append("\n\n");
            }
        } else {
            // 模型不支持视觉输入，智能问答不支持图片处理
            String noVision = SkillLoader.loadSkill(SkillPaths.CHAT_NO_VISION_CAPABILITY);
            if (!noVision.trim().isEmpty()) {
                systemMessageBuilder.append(noVision.trim()).append("\n\n");
            } else {
                systemMessageBuilder.append(SkillLoader.loadSkill("chat/no_vision_capability_fallback").trim()).append("\n\n");
            }
        }

        // 备忘录意图确认：这里只是候选项，真正创建要等前端用户确认。
        if (memo != null) {
            String remindTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(memo.getRemindAt());
            systemMessageBuilder.append("\n\n")
                    .append(SkillLoader.loadSkillWithTemplate("chat/memo_candidate_hint_template", Map.of(
                            "content", String.valueOf(memo.getContent()),
                            "remindTime", remindTime)));
        } else if (!Boolean.FALSE.equals(request.getEnableMemo()) && intentRecognitionService.hasMemoIntent(request.getQuestion())) {
            systemMessageBuilder.append("\n\n").append(SkillLoader.loadSkill("chat/memo_missing_time_hint").trim());
        } else if (!Boolean.FALSE.equals(request.getEnableMemo())) {
            systemMessageBuilder.append("\n\n").append(SkillLoader.loadSkill("chat/memo_confirmation_rule").trim());
        }

        // 时间信息：与浏览器检索解耦，enableTimeInfo 为 true 或未设置时注入完整时间，否则仅注入年份
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        boolean injectTimeInfo = Boolean.TRUE.equals(request.getEnableTimeInfo())
                || Boolean.TRUE.equals(request.getEnableBrowserSearch());
        if (injectTimeInfo) {
            String currentTimeInfo = mcpTimeService.getFormattedTimeInfo();
            McpTimeService.TimeInfo timeInfo = mcpTimeService.getCurrentTime();
            currentYear = timeInfo.getYear();
            systemMessageBuilder.append(currentTimeInfo);
        } else {
            systemMessageBuilder.append(SkillLoader.loadSkillWithTemplate("chat/current_year_template", Map.of(
                    "currentYear", String.valueOf(currentYear))));
        }

        if (injectTimeInfo && mcpLocationService != null) {
            String locationInfo = mcpLocationService.getFormattedLocationInfo();
            if (locationInfo != null && !locationInfo.trim().isEmpty()) {
                systemMessageBuilder.append("\n").append(locationInfo.trim());
            }
        }

        String memoryContext = userMemoryService.buildMemoryContext(userId, request.getQuestion(), "chat", null);
        if (memoryContext != null && !memoryContext.trim().isEmpty()) {
            systemMessageBuilder.append("\n").append(memoryContext).append("\n");
        }

        String conversationSummary = getConversationSummaryForRequest(request, userId);
        if (conversationSummary != null && !conversationSummary.trim().isEmpty()) {
            systemMessageBuilder.append("\n【当前会话摘要】\n")
                    .append(conversationSummary.trim())
                    .append("\n请把以上摘要作为当前会话的历史上下文；若摘要与最近对话冲突，以最近对话为准。\n");
        }

        // 如果提供了浏览器检索结果，在系统消息中强调要使用检索结果与时效性说明
        String retrievalTime = null;
        if (browserSearchContext != null && !browserSearchContext.trim().isEmpty()) {
            retrievalTime = LocalDateTime.now().format(RETRIEVAL_TIME_FORMATTER);
            Map<String, String> variables = new HashMap<>();
            variables.put("currentYear", String.valueOf(currentYear));
            variables.put("retrievalTime", retrievalTime);
            String browserSearchSystem = SkillLoader.loadSkillWithTemplate(SkillPaths.CHAT_BROWSER_SEARCH_SYSTEM, variables);
            if (!browserSearchSystem.trim().isEmpty()) {
                systemMessageBuilder.append("\n\n").append(browserSearchSystem.trim());
            } else {
                systemMessageBuilder.append("\n\n").append(SkillLoader
                        .loadSkillWithTemplate("chat/browser_search_system_fallback", variables).trim());
            }
        }

        // 加载 Markdown 格式要求
        String markdownFormat = SkillLoader.loadSkill(SkillPaths.COMMON_MARKDOWN_FORMAT);
        if (!markdownFormat.trim().isEmpty()) {
            systemMessageBuilder.append("\n\n").append(markdownFormat.trim());
        } else {
            // Fallback：如果文件不存在，使用硬编码（保持向后兼容）
            systemMessageBuilder
                    .append("""


                            重要：请使用Markdown格式来组织你的回答，包括：
                            - 使用标题（#、##、###）来组织内容结构
                            - 使用列表（-、*、1.）来列举要点
                            - 使用代码块（```）来展示代码或技术内容
                            - 使用**粗体**和*斜体*来强调重要信息
                            - 使用表格来展示结构化数据

                            【关键要求】代码块格式（必须严格遵守）：
                            1. 所有代码块必须包含语言标识符，格式为：```语言标识符
                            代码内容
                            ```
                            2. 语言标识符示例：
                               - JavaScript代码：```javascript
                            代码
                            ```
                               - Python代码：```python
                            代码
                            ```
                               - Java代码：```java
                            代码
                            ```
                               - TypeScript代码：```typescript
                            代码
                            ```
                               - Go代码：```go
                            代码
                            ```
                               - Rust代码：```rust
                            代码
                            ```
                               - C/C++代码：```cpp
                            代码
                            ``` 或 ```c
                            代码
                            ```
                               - C#代码：```csharp
                            代码
                            ```
                               - PHP代码：```php
                            代码
                            ```
                               - Ruby代码：```ruby
                            代码
                            ```
                               - Swift代码：```swift
                            代码
                            ```
                               - Kotlin代码：```kotlin
                            代码
                            ```
                               - SQL代码：```sql
                            代码
                            ```
                               - HTML代码：```html
                            代码
                            ```
                               - CSS代码：```css
                            代码
                            ```
                               - JSON代码：```json
                            代码
                            ```
                               - XML代码：```xml
                            代码
                            ```
                               - YAML代码：```yaml
                            代码
                            ```
                               - Bash/Shell代码：```bash
                            代码
                            ``` 或 ```shell
                            代码
                            ```
                            3. 绝对禁止使用没有语言标识符的代码块（如 ```
                            代码
                            ```），这会导致代码无法正确高亮显示
                            4. 在流式响应中，生成代码块时必须在第一行就包含完整的 ```语言标识符，例如：```javascript
                            5. 代码块中的代码应该完整、可运行，并包含必要的注释
                            6. 如果用户输入包含代码，请确保在回答中正确使用带语言标识符的代码块格式展示

                            【关键要求】数学公式格式（必须严格遵守）：
                            1. 所有数学公式必须使用 LaTeX 格式编写，不要使用占位符或省略公式内容
                            2. 行内公式使用 $...$ 或 \\(...\\)，例如：$E = mc^2$ 或 $\\phi = \\frac{1+\\sqrt{5}}{2}$
                            3. 块级公式使用 $$...$$ 或 \\[...\\]，例如：
                               $$F(n) = \\frac{1}{\\sqrt{5}} \\left( \\left( \\frac{1 + \\sqrt{5}}{2} \\right)^n - \\left( \\frac{1 - \\sqrt{5}}{2} \\right)^n \\right)$$
                            4. 也可用方括号块级公式 [...]（方括号内需含 LaTeX 命令），例如：
                               [ f(x) = \\sum_{n=0}^{\\infty} \\frac{f^{(n)}(a)}{n!}(x-a)^n ]
                            5. 矩阵、向量等使用 \\begin{bmatrix}、\\begin{pmatrix} 等环境，须写完整 \\begin{...}...\\end{...}，可包在 $$...$$ 内或单独使用
                            6. 绝对禁止使用占位符，必须写出完整 LaTeX；公式中特殊字符需反斜杠转义
                            7. 涉及数学、物理、工程等公式时，必须按上述格式完整写出，不要省略或占位符""");
        }

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
                // 使用模板构建用户消息，并注入检索时间以强化时效性说明
                Map<String, String> variables = new HashMap<>();
                variables.put("question", request.getQuestion());
                variables.put("currentYear", String.valueOf(currentYear));
                variables.put("retrievalTime", retrievalTime);
                String retrievalHeader = SkillLoader.loadSkillWithTemplate(
                        "chat/browser_search_retrieval_header_template", variables);
                String template = SkillLoader.loadSkillWithTemplate("chat/browser_search_user_template", variables);
                if (!template.trim().isEmpty()) {
                    userMessageContent = retrievalHeader + "\n\n" + browserSearchContext + "\n\n---\n\n" + template;
                } else {
                    userMessageContent = retrievalHeader + "\n\n" + browserSearchContext + "\n\n---\n\n"
                            + SkillLoader.loadSkillWithTemplate("chat/browser_search_user_fallback_template", variables);
                }
            } else {
                // 如果启用了MCP支持但没有找到结果，明确告知LLM
                Map<String, String> variables = new HashMap<>();
                variables.put("question", request.getQuestion());
                String template = SkillLoader.loadSkillWithTemplate("chat/browser_search_no_results", variables);
                if (!template.trim().isEmpty()) {
                    userMessageContent = template;
                } else {
                    userMessageContent = SkillLoader.loadSkillWithTemplate(
                            "chat/browser_search_no_results_fallback_template", variables);
                }
            }
        } else {
            // MCP支持已关闭，直接使用原始问题
            userMessageContent = request.getQuestion();
        }

        // 确保消息内容不为空
        if (userMessageContent == null || userMessageContent.trim().isEmpty()) {
            userMessageContent = SkillLoader.loadSkill("chat/default_empty_question").trim();
            logger.warn("用户问题为空，使用默认问题");
        }

        // 添加当前问题（包含检索结果）
        messages.add(UserMessage.from(userMessageContent));

        return messages;
    }

    private String getConversationSummaryForRequest(ChatRequest request, Long userId) {
        if (request == null || userId == null) {
            return "";
        }
        Long conversationId = ConversationIdUtil.parseConversationId(request.getConversationId(), logger);
        if (conversationId == null) {
            return "";
        }
        try {
            return chatHistoryService.getConversationSummary(conversationId, userId, false);
        } catch (Exception e) {
            logger.debug("读取会话摘要失败 - conversationId={}", conversationId, e);
            return "";
        }
    }
}
