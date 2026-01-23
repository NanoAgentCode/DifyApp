package com.github.app.dify.knowledgebase.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 知识库导入结果
 */
@Schema(description = "知识库导入结果")
public class KnowledgeBaseImportResult {
    
    @Schema(description = "状态：SUCCESS, ERROR, PARTIAL_SUCCESS")
    private String status;
    
    @Schema(description = "知识库ID")
    private Long knowledgeBaseId;
    
    @Schema(description = "知识库名称")
    private String knowledgeBaseName;
    
    @Schema(description = "总文档数")
    private Integer totalDocuments;
    
    @Schema(description = "成功导入的文档数")
    private Integer successCount;
    
    @Schema(description = "导入失败的文档数")
    private Integer failedCount;
    
    @Schema(description = "错误列表")
    private List<DocumentImportError> errors;
    
    @Schema(description = "状态消息")
    private String message;
    
    @Schema(description = "检测到的文件数量")
    private Integer fileCount;
    
    // Getters and Setters
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }
    
    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }
    
    public String getKnowledgeBaseName() {
        return knowledgeBaseName;
    }
    
    public void setKnowledgeBaseName(String knowledgeBaseName) {
        this.knowledgeBaseName = knowledgeBaseName;
    }
    
    public Integer getTotalDocuments() {
        return totalDocuments;
    }
    
    public void setTotalDocuments(Integer totalDocuments) {
        this.totalDocuments = totalDocuments;
    }
    
    public Integer getSuccessCount() {
        return successCount;
    }
    
    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }
    
    public Integer getFailedCount() {
        return failedCount;
    }
    
    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }
    
    public List<DocumentImportError> getErrors() {
        return errors;
    }
    
    public void setErrors(List<DocumentImportError> errors) {
        this.errors = errors;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Integer getFileCount() {
        return fileCount;
    }
    
    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }
}
