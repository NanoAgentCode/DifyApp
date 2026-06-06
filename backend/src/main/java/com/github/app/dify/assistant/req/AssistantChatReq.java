package com.github.app.dify.assistant.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * 全局页面助手问答请求。
 */
@Schema(description = "全局页面助手问答请求")
public class AssistantChatReq {

    @NotBlank(message = "问题不能为空")
    @Schema(description = "用户问题")
    private String message;

    @Schema(description = "对话ID")
    private String conversationId;

    @Schema(description = "模型ID")
    private Long modelId;

    @Schema(description = "页面上下文")
    private AssistantPageContext pageContext;

    @Schema(description = "Page context hash for server-side cache reuse")
    private String pageContextHash;

    @Schema(description = "对话历史")
    private List<AssistantMessage> history;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public AssistantPageContext getPageContext() {
        return pageContext;
    }

    public void setPageContext(AssistantPageContext pageContext) {
        this.pageContext = pageContext;
    }

    public String getPageContextHash() {
        return pageContextHash;
    }

    public void setPageContextHash(String pageContextHash) {
        this.pageContextHash = pageContextHash;
    }

    public List<AssistantMessage> getHistory() {
        return history;
    }

    public void setHistory(List<AssistantMessage> history) {
        this.history = history;
    }

    public static class AssistantMessage {
        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public static class AssistantPageContext {
        private String source;
        private PageInfo page;
        private SelectionInfo selection;
        private List<SectionInfo> sections;
        private Map<String, Object> meta;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public PageInfo getPage() {
            return page;
        }

        public void setPage(PageInfo page) {
            this.page = page;
        }

        public SelectionInfo getSelection() {
            return selection;
        }

        public void setSelection(SelectionInfo selection) {
            this.selection = selection;
        }

        public List<SectionInfo> getSections() {
            return sections;
        }

        public void setSections(List<SectionInfo> sections) {
            this.sections = sections;
        }

        public Map<String, Object> getMeta() {
            return meta;
        }

        public void setMeta(Map<String, Object> meta) {
            this.meta = meta;
        }
    }

    public static class PageInfo {
        private String route;
        private String title;
        private String type;

        public String getRoute() {
            return route;
        }

        public void setRoute(String route) {
            this.route = route;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class SelectionInfo {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static class SectionInfo {
        private String type;
        private String title;
        private String content;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
