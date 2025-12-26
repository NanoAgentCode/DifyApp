package com.github.app.dify.documentreader.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;

/**
 * 文档翻译表
 */
@Entity
@Table(name = "DOCUMENT_TRANSLATION")
public class DocumentTranslation implements Serializable {

    /**
     * 编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "编号")
    private Long id;
    
    /**
     * 文档ID
     */
    @NotNull(message = "文档ID不能为空")
    @Schema(description = "文档ID")
    @Column(name = "document_id")
    private Long documentId;
    
    /**
     * 目标语言
     */
    @Size(max = 10, message = "编码长度不能超过10")
    @Schema(description = "目标语言")
    @Length(max = 10, message = "编码长度不能超过10")
    @Column(name = "target_language")
    private String targetLanguage;
    
    /**
     * 翻译内容
     */
    @Schema(description = "翻译内容")
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @Column(name = "create_time")
    private Date createTime;
    
    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    @Column(name = "update_time")
    private Date updateTime;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
}

