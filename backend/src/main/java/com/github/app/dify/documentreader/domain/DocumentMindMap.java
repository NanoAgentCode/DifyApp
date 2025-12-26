package com.github.app.dify.documentreader.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档脑图表
 */
@Entity
@Table(name = "DOCUMENT_MINDMAP")
public class DocumentMindMap implements Serializable {

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
     * 脑图数据（JSON格式）
     */
    @Schema(description = "脑图数据（JSON格式）")
    @Column(name = "mind_map_data", columnDefinition = "TEXT")
    private String mindMapData;
    
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

    public String getMindMapData() {
        return mindMapData;
    }

    public void setMindMapData(String mindMapData) {
        this.mindMapData = mindMapData;
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

