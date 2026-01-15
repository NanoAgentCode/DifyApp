package com.github.app.dify.chat.domain;

import com.github.app.dify.common.domain.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
/**
* AI 微应用表
* @TableName AI_APP
*/
@Entity
@Table(name = "AI_APP")
public class AiApp extends BaseSoftDeleteEntity {

    /**
    * 应用编号
    */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "应用编号")
    private Long id;
    /**
    * 应用名称
    */
    @NotBlank(message="[应用名称]不能为空")
    @Size(max= 100,message="编码长度不能超过100")
    @Schema(description = "应用名称")
    @Length(max= 100,message="编码长度不能超过100")
    private String name;
    /**
    * 应用描述
    */
    @Size(max= 500,message="编码长度不能超过500")
    @Schema(description = "应用描述")
    @Length(max= 500,message="编码长度不能超过500")
    private String description;
    /**
    * 应用类型：1-chatFlow，2-workflow
    */
    @NotNull(message="[应用类型]不能为空")
    @Schema(description = "应用类型：1-chatFlow，2-workflow")
    @Column(name = "type")
    private Integer type;
    /**
    * 应用状态
    */
    @Schema(description = "应用状态")
    private Integer status;
    /**
    * 应用配置
    */
    @Schema(description = "应用配置")
    @Column(name = "inputs", columnDefinition = "text")
    private String inputs;
    /**
    * 应用图标
    */
    @Size(max= 255,message="编码长度不能超过255")
    @Schema(description = "应用图标")
    @Length(max= 255,message="编码长度不能超过255")
    private String icon;
    /**
    * 排序
    */
    @Schema(description = "排序")
    private Integer sort;
    /**
    * 创建者
    */
    @Size(max= 64,message="编码长度不能超过64")
    @Schema(description = "创建者")
    @Length(max= 64,message="编码长度不能超过64")
    private String creator;
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
    @NotNull(message="[租户编号]不能为空")
    @Schema(description = "租户编号")
    private Integer tenantId;
    /**
    * Dify API_KEY，需要唯一
    */
    @Size(max= 255,message="编码长度不能超过255")
    @Schema(description = "Dify API_KEY，需要唯一")
    @Length(max= 255,message="编码长度不能超过255")
    @Column(name = "app_id", unique = true)
    private String appId;
    
    /**
    * Dify API Base URL
    */
    @Size(max= 500,message="编码长度不能超过500")
    @Schema(description = "Dify API Base URL")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "api_base_url")
    private String apiBaseUrl;
    
    /**
    * 是否支持流式响应
    */
    @Schema(description = "是否支持流式响应")
    @Column(name = "stream_enabled")
    private Boolean streamEnabled;
    
    /**
    * 是否需要上传文件
    */
    @Schema(description = "是否需要上传文件")
    @Column(name = "file_upload_enabled")
    private Boolean fileUploadEnabled;
    
    /**
    * 是否显示文本输入框
    */
    @Schema(description = "是否显示文本输入框")
    @Column(name = "input_enabled")
    private Boolean inputEnabled;
    
    /**
    * 主题色
    */
    @Size(max= 64,message="编码长度不能超过64")
    @Schema(description = "主题色")
    @Length(max= 64,message="编码长度不能超过64")
    private String themeColor;

    /**
    * 应用编号
    */
    public void setId(Long id){
    this.id = id;
    }

    /**
    * 应用名称
    */
    public void setName(String name){
    this.name = name;
    }

    /**
    * 应用描述
    */
    public void setDescription(String description){
    this.description = description;
    }

    /**
    * 应用类型
    */
    public void setType(Integer type){
    this.type = type;
    }

    /**
    * 应用状态
    */
    public void setStatus(Integer status){
    this.status = status;
    }

    /**
    * 应用配置
    */
    public void setInputs(String inputs){
    this.inputs = inputs;
    }

    /**
    * 应用图标
    */
    public void setIcon(String icon){
    this.icon = icon;
    }

    /**
    * 排序
    */
    public void setSort(Integer sort){
    this.sort = sort;
    }

    /**
    * 创建者
    */
    public void setCreator(String creator){
    this.creator = creator;
    }

    /**
    * 创建时间
    */
    public void setUpdater(String updater){
    this.updater = updater;
    }

    /**
    * 更新时间
    */
    public void setTenantId(Integer tenantId){
    this.tenantId = tenantId;
    }

    /**
    * API_KEY，需要唯一
    */
    public void setAppId(String appId){
    this.appId = appId;
    }

    /**
    * 主题色
    */
    public void setThemeColor(String themeColor){
    this.themeColor = themeColor;
    }
    
    /**
    * Dify API Base URL
    */
    public void setApiBaseUrl(String apiBaseUrl){
    this.apiBaseUrl = apiBaseUrl;
    }
    
    /**
    * 是否支持流式响应
    */
    public void setStreamEnabled(Boolean streamEnabled){
    this.streamEnabled = streamEnabled;
    }
    
    /**
    * 是否需要上传文件
    */
    public void setFileUploadEnabled(Boolean fileUploadEnabled){
    this.fileUploadEnabled = fileUploadEnabled;
    }


    /**
    * 应用编号
    */
    public Long getId(){
    return this.id;
    }

    /**
    * 应用名称
    */
    public String getName(){
    return this.name;
    }

    /**
    * 应用描述
    */
    public String getDescription(){
    return this.description;
    }

    /**
    * 应用类型
    */
    public Integer getType(){
    return this.type;
    }

    /**
    * 应用状态
    */
    public Integer getStatus(){
    return this.status;
    }

    /**
    * 应用配置
    */
    public String getInputs(){
    return this.inputs;
    }

    /**
    * 应用图标
    */
    public String getIcon(){
    return this.icon;
    }

    /**
    * 排序
    */
    public Integer getSort(){
    return this.sort;
    }

    /**
    * 创建者
    */
    public String getCreator(){
    return this.creator;
    }

    /**
    * 创建时间
    */
    public String getUpdater(){
    return this.updater;
    }

    /**
    * 更新时间
    */
    public Integer getTenantId(){
    return this.tenantId;
    }

    /**
    * API_KEY，需要唯一
    */
    public String getAppId(){
    return this.appId;
    }

    /**
    * 主题色
    */
    public String getThemeColor(){
    return this.themeColor;
    }
    
    /**
    * Dify API Base URL
    */
    public String getApiBaseUrl(){
    return this.apiBaseUrl;
    }
    
    /**
    * 是否支持流式响应
    */
    public Boolean getStreamEnabled(){
    return this.streamEnabled;
    }
    
    /**
    * 是否需要上传文件
    */
    public Boolean getFileUploadEnabled(){
    return this.fileUploadEnabled;
    }
    
    /**
    * 是否显示文本输入框
    */
    public Boolean getInputEnabled(){
    return this.inputEnabled;
    }
    
    /**
    * 是否显示文本输入框
    */
    public void setInputEnabled(Boolean inputEnabled){
    this.inputEnabled = inputEnabled;
    }

}
