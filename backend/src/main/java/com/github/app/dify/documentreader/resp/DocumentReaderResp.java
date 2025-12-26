package com.github.app.dify.documentreader.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

/**
 * 文档解读响应
 */
@Schema(description = "文档解读响应")
public class DocumentReaderResp {
    
    @Schema(description = "文档ID")
    private Long id;
    
    @Schema(description = "文件名")
    private String fileName;
    
    @Schema(description = "原始文件名")
    private String originalFileName;
    
    @Schema(description = "文件路径")
    private String filePath;
    
    @Schema(description = "文件URL")
    private String fileUrl;
    
    @Schema(description = "文件大小（字节）")
    private Long fileSize;
    
    @Schema(description = "文件类型")
    private String fileType;
    
    @Schema(description = "MIME类型")
    private String mimeType;
    
    @Schema(description = "存储类型")
    private String storageType;
    
    @Schema(description = "文档状态")
    private Integer status;
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新时间")
    private Date updateTime;
    
    @Schema(description = "总页数")
    private Integer totalPages;
    
    @Schema(description = "上传时间")
    private Date uploadTime;
    
    @Schema(description = "向量化状态：0-未向量化，1-向量化中，2-向量化成功，3-向量化失败")
    private Integer vectorizedStatus;
    
    @Schema(description = "向量化错误信息")
    private String vectorizedError;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
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

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Date getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(Date uploadTime) {
        this.uploadTime = uploadTime;
    }
    
    public Integer getVectorizedStatus() {
        return vectorizedStatus;
    }
    
    public void setVectorizedStatus(Integer vectorizedStatus) {
        this.vectorizedStatus = vectorizedStatus;
    }
    
    public String getVectorizedError() {
        return vectorizedError;
    }
    
    public void setVectorizedError(String vectorizedError) {
        this.vectorizedError = vectorizedError;
    }
}

