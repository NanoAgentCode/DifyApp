package com.github.app.dify.knowledgebase.resp;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档导入错误信息
 */
@Schema(description = "文档导入错误信息")
public class DocumentImportError {
    
    @Schema(description = "文件名")
    private String fileName;
    
    @Schema(description = "错误信息")
    private String errorMessage;
    
    // Getters and Setters
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
