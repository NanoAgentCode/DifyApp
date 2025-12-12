package com.github.app.dify.appsystemdata.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DrawIO 历史记录实体
 */
@Entity
@Table(name = "DRAWIO_HISTORY")
public class DrawIOHistory implements Serializable {

    /**
     * 历史记录ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "历史记录ID")
    private Long id;
    
    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /**
     * 提示词内容
     */
    @NotBlank(message = "[提示词]不能为空")
    @Size(max = 500, message = "提示词长度不能超过500")
    @Schema(description = "提示词内容")
    @Column(name = "prompt", nullable = false, length = 500)
    private String prompt;
    
    /**
     * 图表类型：flowchart, architecture, mindmap, sequence, uml, org, network, custom
     */
    @Schema(description = "图表类型")
    @Column(name = "diagram_type", length = 50)
    private String diagramType;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @Column(name = "create_time")
    private Date createTime;
    
    /**
     * 是否删除：0-未删除，1-已删除
     */
    @Schema(description = "是否删除：0-未删除，1-已删除")
    @Column(name = "deleted")
    private Integer deleted;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(String diagramType) {
        this.diagramType = diagramType;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}

