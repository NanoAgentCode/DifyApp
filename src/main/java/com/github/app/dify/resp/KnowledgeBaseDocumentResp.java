package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;

/**
 * 知识库文档响应
 */
@ApiModel("知识库文档响应")
public class KnowledgeBaseDocumentResp {
    
    @ApiModelProperty("文档编号")
    private Long id;
    
    @ApiModelProperty("知识库编号")
    private Long knowledgeBaseId;
    
    @ApiModelProperty("文件名（存储后的文件名）")
    private String fileName;
    
    @ApiModelProperty("原始文件名")
    private String originalFileName;
    
    @ApiModelProperty("文件路径")
    private String filePath;
    
    @ApiModelProperty("文件访问URL")
    private String fileUrl;
    
    @ApiModelProperty("文件大小（字节）")
    private Long fileSize;
    
    @ApiModelProperty("文件类型（扩展名）")
    private String fileType;
    
    @ApiModelProperty("MIME类型")
    private String mimeType;
    
    @ApiModelProperty("存储类型")
    private String storageType;
    
    @ApiModelProperty("文档状态")
    private Integer status;
    
    @ApiModelProperty("上传用户")
    private String uploadUser;
    
    @ApiModelProperty("创建时间")
    private Date createTime;
    
    @ApiModelProperty("更新时间")
    private Date updateTime;
    
    @ApiModelProperty("租户编号")
    private Integer tenantId;
    
    @ApiModelProperty("向量化状态：0-未向量化，1-向量化中，2-向量化成功，3-向量化失败")
    private Integer vectorizedStatus;
    
    @ApiModelProperty("向量化完成时间")
    private Date vectorizedTime;
    
    @ApiModelProperty("向量化错误信息")
    private String vectorizedError;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }
    
    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
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
    
    public String getUploadUser() {
        return uploadUser;
    }
    
    public void setUploadUser(String uploadUser) {
        this.uploadUser = uploadUser;
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
    
    public Integer getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }
    
    public Integer getVectorizedStatus() {
        return vectorizedStatus;
    }
    
    public void setVectorizedStatus(Integer vectorizedStatus) {
        this.vectorizedStatus = vectorizedStatus;
    }
    
    public Date getVectorizedTime() {
        return vectorizedTime;
    }
    
    public void setVectorizedTime(Date vectorizedTime) {
        this.vectorizedTime = vectorizedTime;
    }
    
    public String getVectorizedError() {
        return vectorizedError;
    }
    
    public void setVectorizedError(String vectorizedError) {
        this.vectorizedError = vectorizedError;
    }
}

