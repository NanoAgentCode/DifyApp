package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;

/**
 * 向量数据库配置请求
 */
@ApiModel("向量数据库配置请求")
public class VectorDatabaseRequest {
    
    @ApiModelProperty("操作类型：add-添加, update-更新, delete-删除, setDefault-设置默认, toggleEnabled-切换启用状态")
    @NotBlank(message = "操作类型不能为空")
    private String action;
    
    @ApiModelProperty("配置ID（更新、删除、设置默认、切换状态时需要）")
    private Long configId;
    
    @ApiModelProperty("配置信息（添加、更新时需要）")
    private DatabaseInfo database;
    
    @ApiModelProperty("启用状态（切换状态时需要）")
    private Boolean enabled;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public DatabaseInfo getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseInfo database) {
        this.database = database;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 数据库配置信息
     */
    @ApiModel("数据库配置信息")
    public static class DatabaseInfo {
        @ApiModelProperty("配置ID（更新时需要）")
        private Long id;
        
        @ApiModelProperty("配置名称")
        @NotBlank(message = "配置名称不能为空")
        private String name;
        
               @ApiModelProperty("数据库类型：qdrant, milvus, milvus-lite, faiss")
        @NotBlank(message = "数据库类型不能为空")
        private String type;
        
        @ApiModelProperty("连接地址（URL或路径）")
        @NotBlank(message = "连接地址不能为空")
        private String url;
        
        @ApiModelProperty("API Key（可选）")
        private String apiKey;
        
        @ApiModelProperty("超时时间（毫秒）")
        private Integer timeout;
        
        @ApiModelProperty("额外配置（JSON格式）")
        private String extraConfig;
        
        @ApiModelProperty("是否启用")
        private Boolean enabled;
        
        @ApiModelProperty("描述")
        private String description;

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

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Integer getTimeout() {
            return timeout;
        }

        public void setTimeout(Integer timeout) {
            this.timeout = timeout;
        }

        public String getExtraConfig() {
            return extraConfig;
        }

        public void setExtraConfig(String extraConfig) {
            this.extraConfig = extraConfig;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}

