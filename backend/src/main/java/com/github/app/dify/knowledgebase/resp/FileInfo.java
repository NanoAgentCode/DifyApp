package com.github.app.dify.knowledgebase.resp;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文件信息
 */
@Schema(description = "文件信息")
public class FileInfo {
    
    @Schema(description = "文件名")
    private String fileName;
    
    @Schema(description = "文件大小（字节）")
    private Long fileSize;
    
    @Schema(description = "文件类型（扩展名）")
    private String fileType;
    
    @Schema(description = "MIME类型")
    private String mimeType;
    
    @Schema(description = "文件路径（ZIP中的相对路径）")
    private String filePath;
    
    // Getters and Setters
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getFileType() {
        return fileType;
    }
    
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
