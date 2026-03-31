package com.github.app.dify.system.domain;

import com.github.app.dify.common.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

/**
 * Agent Skills 配置表
 */
@Entity
@Table(name = "AGENT_SKILL_CONFIG")
public class AgentSkillConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "主键ID")
    private Long id;

    @Size(max = 100)
    @Schema(description = "Skill 唯一键（目录名）")
    @Column(name = "skill_key", nullable = false, unique = true, length = 100)
    private String skillKey;

    @Size(max = 200)
    @Schema(description = "Skill 名称")
    @Column(name = "skill_name", length = 200)
    private String skillName;

    @Size(max = 500)
    @Schema(description = "Skill 路径（相对项目根）")
    @Column(name = "skill_path", length = 500)
    private String skillPath;

    @Schema(description = "是否启用")
    @Column(name = "enabled")
    private Boolean enabled;

    @Schema(description = "普通用户是否可见")
    @Column(name = "visible_to_user")
    private Boolean visibleToUser;

    @Size(max = 1000)
    @Schema(description = "技能描述")
    @Column(name = "description", length = 1000)
    private String description;

    @Size(max = 30)
    @Schema(description = "来源类型（system/custom）")
    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Schema(description = "扩展JSON")
    @Column(name = "ext_json", columnDefinition = "TEXT")
    private String extJson;

    @Size(max = 64)
    @Schema(description = "创建者")
    @Column(name = "creator", length = 64)
    private String creator;

    @Schema(description = "创建者ID")
    @Column(name = "creator_id")
    private Long creatorId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSkillKey() {
        return skillKey;
    }

    public void setSkillKey(String skillKey) {
        this.skillKey = skillKey;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillPath() {
        return skillPath;
    }

    public void setSkillPath(String skillPath) {
        this.skillPath = skillPath;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getVisibleToUser() {
        return visibleToUser;
    }

    public void setVisibleToUser(Boolean visibleToUser) {
        this.visibleToUser = visibleToUser;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getExtJson() {
        return extJson;
    }

    public void setExtJson(String extJson) {
        this.extJson = extJson;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }
}
