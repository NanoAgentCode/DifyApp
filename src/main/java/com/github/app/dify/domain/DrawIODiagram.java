package com.github.app.dify.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DrawIO 图表实体
 */
@Entity
@Table(name = "DRAWIO_DIAGRAM")
public class DrawIODiagram implements Serializable {

    /**
     * 图表ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "图表ID")
    private Long id;
    
    /**
     * 图表名称
     */
    @NotBlank(message = "[图表名称]不能为空")
    @Size(max = 255, message = "图表名称长度不能超过255")
    @Schema(description = "图表名称")
    @Column(name = "name", nullable = false)
    private String name;
    
    /**
     * 图表类型：flowchart, architecture, mindmap, sequence, uml, org, network, custom
     */
    @Schema(description = "图表类型")
    @Column(name = "diagram_type")
    private String diagramType;
    
    /**
     * 图表JSON内容（X6格式）
     */
    @Schema(description = "图表JSON内容（X6格式）")
    @Column(name = "diagram_json", columnDefinition = "TEXT")
    private String diagramJson;
    
    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDiagramType() {
        return diagramType;
    }

    public void setDiagramType(String diagramType) {
        this.diagramType = diagramType;
    }

    public String getDiagramJson() {
        return diagramJson;
    }

    public void setDiagramJson(String diagramJson) {
        this.diagramJson = diagramJson;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}

