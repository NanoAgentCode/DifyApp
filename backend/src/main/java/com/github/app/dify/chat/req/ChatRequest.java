package com.github.app.dify.chat.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
/**
 * 智能问答请求（直接对话，不使用知识库）
 */
@Schema(description = "智能问答请求")
public class ChatRequest {
    
    @NotBlank(message = "问题不能为空")
    @Schema(description = "问题")
    private String question;
    
    @Schema(description = "对话ID（用于多轮对话）")
    private String conversationId;
    
    @Schema(description = "用户ID")
    private String userId;
    
    @Schema(description = "对话历史")
    private List<Message> history;
    
    @Schema(description = "是否流式响应")
    private Boolean stream;
    
    @Schema(description = "模型ID（从数据库中选择的问答模型）")
    private Long modelId;
    
    @Schema(description = "是否启用浏览器检索（MCP协议）")
    private Boolean enableBrowserSearch;

    @Schema(description = "是否注入当前时间信息（MCP时间服务），默认 true；与浏览器检索解耦，关闭检索时仍可提供时间")
    private Boolean enableTimeInfo;

    @Schema(description = "图片OCR识别结果（由后端自动填充，前端无需传递）")
    private String ocrText;
    
    @Schema(description = "图片数据列表（base64编码，用于多模态模型）")
    private List<ImageData> images;
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public List<Message> getHistory() {
        return history;
    }
    
    public void setHistory(List<Message> history) {
        this.history = history;
    }
    
    public Boolean getStream() {
        return stream;
    }
    
    public void setStream(Boolean stream) {
        this.stream = stream;
    }
    
    public Long getModelId() {
        return modelId;
    }
    
    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }
    
    public Boolean getEnableBrowserSearch() {
        return enableBrowserSearch;
    }
    
    public void setEnableBrowserSearch(Boolean enableBrowserSearch) {
        this.enableBrowserSearch = enableBrowserSearch;
    }

    public Boolean getEnableTimeInfo() {
        return enableTimeInfo;
    }

    public void setEnableTimeInfo(Boolean enableTimeInfo) {
        this.enableTimeInfo = enableTimeInfo;
    }

    public String getOcrText() {
        return ocrText;
    }
    
    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }
    
    public List<ImageData> getImages() {
        return images;
    }
    
    public void setImages(List<ImageData> images) {
        this.images = images;
    }
    
    /**
     * 图片数据
     */
    public static class ImageData {
        @Schema(description = "图片base64编码数据")
        private String base64;
        
        @Schema(description = "图片MIME类型")
        private String mimeType;
        
        public String getBase64() {
            return base64;
        }
        
        public void setBase64(String base64) {
            this.base64 = base64;
        }
        
        public String getMimeType() {
            return mimeType;
        }
        
        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }
    }
    
    /**
     * 消息
     */
    public static class Message {
        private String role; // user, assistant
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
}