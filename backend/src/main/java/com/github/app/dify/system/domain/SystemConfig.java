package com.github.app.dify.system.domain;

import com.github.app.dify.common.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 系统配置表（通用配置存储）
 * @TableName SYSTEM_CONFIG
 */
@Entity
@Table(name = "SYSTEM_CONFIG")
public class SystemConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "配置编号")
    private Long id;

    @Size(max = 100)
    @Schema(description = "配置键")
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    @Schema(description = "配置值")
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Size(max = 500)
    @Schema(description = "配置描述")
    @Column(name = "description", length = 500)
    private String description;

    @Size(max = 50)
    @Schema(description = "配置分组")
    @Column(name = "config_group", length = 50)
    private String configGroup;

    @Size(max = 20)
    @Schema(description = "配置类型")
    @Column(name = "config_type", length = 20)
    private String configType;

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

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConfigGroup() {
        return configGroup;
    }

    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
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
