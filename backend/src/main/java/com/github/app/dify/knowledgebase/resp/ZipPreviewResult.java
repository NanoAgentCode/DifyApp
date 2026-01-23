package com.github.app.dify.knowledgebase.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * ZIP文件预览结果
 */
@Schema(description = "ZIP文件预览结果")
public class ZipPreviewResult {
    
    @Schema(description = "文件总数")
    private Integer fileCount;
    
    @Schema(description = "文件列表")
    private List<FileInfo> files;
    
    // Getters and Setters
    public Integer getFileCount() {
        return fileCount;
    }
    
    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }
    
    public List<FileInfo> getFiles() {
        return files;
    }
    
    public void setFiles(List<FileInfo> files) {
        this.files = files;
    }
}
