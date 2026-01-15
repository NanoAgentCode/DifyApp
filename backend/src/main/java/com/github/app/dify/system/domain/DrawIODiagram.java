package com.github.app.dify.system.domain;

import com.github.app.dify.common.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DrawIO 图表实体
 */
@Entity
@Table(name = "DRAWIO_DIAGRAM")
public class DrawIODiagram extends BaseEntity {

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

}

