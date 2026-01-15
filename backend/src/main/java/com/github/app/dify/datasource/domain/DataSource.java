package com.github.app.dify.datasource.domain;

import com.github.app.dify.common.domain.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
/**
 * 数据源表
 * @TableName DATA_SOURCE
 */
@Entity
@Table(name = "DATA_SOURCE")
public class DataSource extends BaseSoftDeleteEntity {

    /**
     * 数据源编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "数据源编号")
    private Long id;
    
    /**
     * 数据源名称
     */
    @NotBlank(message="[数据源名称]不能为空")
    @Size(max= 100,message="编码长度不能超过100")
    @Schema(description = "数据源名称")
    @Length(max= 100,message="编码长度不能超过100")
    @Column(name = "name", columnDefinition = "VARCHAR(100)")
    private String name;
    
    /**
     * 数据源描述
     */
    @Size(max= 500,message="编码长度不能超过500")
    @Schema(description = "数据源描述")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "description", columnDefinition = "VARCHAR(500)")
    private String description;
    
    /**
     * 数据库类型：postgresql, mysql, oracle, mongodb, neo4j
     */
    @NotBlank(message="[数据库类型]不能为空")
    @Size(max= 20,message="编码长度不能超过20")
    @Schema(description = "数据库类型：postgresql, mysql, oracle, mongodb, neo4j")
    @Length(max= 20,message="编码长度不能超过20")
    @Column(name = "type", columnDefinition = "VARCHAR(20)")
    private String type;
    
    /**
     * 主机地址
     */
    @NotBlank(message="[主机地址]不能为空")
    @Size(max= 255,message="编码长度不能超过255")
    @Schema(description = "主机地址")
    @Length(max= 255,message="编码长度不能超过255")
    @Column(name = "host", columnDefinition = "VARCHAR(255)")
    private String host;
    
    /**
     * 端口号
     */
    @Schema(description = "端口号")
    @Column(name = "port")
    private Integer port;
    
    /**
     * 数据库名称
     */
    @Size(max= 100,message="编码长度不能超过100")
    @Schema(description = "数据库名称")
    @Length(max= 100,message="编码长度不能超过100")
    @Column(name = "database", columnDefinition = "VARCHAR(100)")
    private String database;
    
    /**
     * 用户名
     */
    @Size(max= 100,message="编码长度不能超过100")
    @Schema(description = "用户名")
    @Length(max= 100,message="编码长度不能超过100")
    @Column(name = "username", columnDefinition = "VARCHAR(100)")
    private String username;
    
    /**
     * 密码（加密存储）
     */
    @Size(max= 500,message="编码长度不能超过500")
    @Schema(description = "密码（加密存储）")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "password", columnDefinition = "VARCHAR(500)")
    private String password;
    
    /**
     * 数据源状态：1-启用，0-禁用
     */
    @Schema(description = "数据源状态：1-启用，0-禁用")
    private Integer status;
    
    /**
     * 创建者
     */
    @Size(max= 64,message="编码长度不能超过64")
    @Schema(description = "创建者")
    @Length(max= 64,message="编码长度不能超过64")
    private String creator;
    
    /**
     * 创建者ID
     */
    @Schema(description = "创建者ID")
    @Column(name = "creator_id")
    private Long creatorId;
    
    /**
     * 是否公开：true-公开，false-私有
     */
    @Schema(description = "是否公开：true-公开，false-私有")
    @Column(name = "is_public")
    private Boolean isPublic;
    
    /**
     * 更新者
     */
    @Size(max= 64,message="编码长度不能超过64")
    @Schema(description = "更新者")
    @Length(max= 64,message="编码长度不能超过64")
    private String updater;
    
    /**
     * 租户编号
     */
    @Schema(description = "租户编号")
    private Integer tenantId;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getUpdater() {
        return updater;
    }

    public void setUpdater(String updater) {
        this.updater = updater;
    }

    public Integer getTenantId() {
        return tenantId;
    }

    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }
}

