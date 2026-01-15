package com.github.app.dify.knowledgebase.domain;

import com.github.app.dify.common.domain.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
/**
 * 知识库文档表
 * @TableName KNOWLEDGE_BASE_DOCUMENT
 */
@Entity
@Table(name = "KNOWLEDGE_BASE_DOCUMENT")
public class KnowledgeBaseDocument extends BaseSoftDeleteEntity {

    /**
     * 文档编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "文档编号")
    private Long id;
    
    /**
     * 知识库编号
     */
    @NotNull(message="[知识库编号]不能为空")
    @Schema(description = "知识库编号")
    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;
    
    /**
     * 文件名（存储后的文件名）
     */
    @Size(max= 255,message="编码长度不能超过255")
    @Schema(description = "文件名（存储后的文件名）")
    @Length(max= 255,message="编码长度不能超过255")
    @Column(name = "file_name")
    private String fileName;
    
    /**
     * 原始文件名
     */
    @Size(max= 255,message="编码长度不能超过255")
    @Schema(description = "原始文件名")
    @Length(max= 255,message="编码长度不能超过255")
    @Column(name = "original_file_name")
    private String originalFileName;
    
    /**
     * 文件路径（在MinIO中的路径）
     */
    @Size(max= 500,message="编码长度不能超过500")
    @Schema(description = "文件路径（在MinIO中的路径）")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "file_path")
    private String filePath;
    
    /**
     * 文件访问URL
     */
    @Size(max= 500,message="编码长度不能超过500")
    @Schema(description = "文件访问URL")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "file_url")
    private String fileUrl;
    
    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小（字节）")
    @Column(name = "file_size")
    private Long fileSize;
    
    /**
     * 文件类型（扩展名）
     */
    @Size(max= 50,message="编码长度不能超过50")
    @Schema(description = "文件类型（扩展名）")
    @Length(max= 50,message="编码长度不能超过50")
    @Column(name = "file_type")
    private String fileType;
    
    /**
     * MIME类型
     */
    @Size(max= 100,message="编码长度不能超过100")
    @Schema(description = "MIME类型")
    @Length(max= 100,message="编码长度不能超过100")
    @Column(name = "mime_type")
    private String mimeType;
    
    /**
     * 存储类型（minio）
     */
    @Size(max= 20,message="编码长度不能超过20")
    @Schema(description = "存储类型（minio）")
    @Length(max= 20,message="编码长度不能超过20")
    @Column(name = "storage_type")
    private String storageType;
    
    /**
     * 文档状态：1-正常，0-已删除
     */
    @Schema(description = "文档状态：1-正常，0-已删除")
    private Integer status;
    
    /**
     * 上传用户
     */
    @Size(max= 64,message="编码长度不能超过64")
    @Schema(description = "上传用户")
    @Length(max= 64,message="编码长度不能超过64")
    @Column(name = "upload_user")
    private String uploadUser;
    
    /**
     * 租户编号
     */
    @Schema(description = "租户编号")
    @Column(name = "tenant_id")
    private Integer tenantId;
    
    /**
     * 向量化状态：0-未向量化，1-向量化中，2-向量化成功，3-向量化失败
     */
    @Schema(description = "向量化状态：0-未向量化，1-向量化中，2-向量化成功，3-向量化失败")
    @Column(name = "vectorized_status")
    private Integer vectorizedStatus;
    
    /**
     * 向量化完成时间
     */
    @Schema(description = "向量化完成时间")
    @Column(name = "vectorized_time")
    private Date vectorizedTime;
    
    /**
     * 向量化错误信息
     */
    @Size(max= 500,message="编码长度不能超过500")
    @Schema(description = "向量化错误信息")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "vectorized_error")
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
