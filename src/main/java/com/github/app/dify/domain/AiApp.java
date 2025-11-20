package com.github.app.dify.domain;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import javax.validation.constraints.NotNull;

import java.io.Serializable;

import java.util.Date;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.validator.constraints.Length;

/**
* AI 微应用表
* @TableName AI_APP
*/
@Entity
@Table(name = "AI_APP")
public class AiApp implements Serializable {

    /**
    * 应用编号
    */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty("应用编号")
    private Long id;
    /**
    * 应用名称
    */
    @NotBlank(message="[应用名称]不能为空")
    @Size(max= 100,message="编码长度不能超过100")
    @ApiModelProperty("应用名称")
    @Length(max= 100,message="编码长度不能超过100")
    private String name;
    /**
    * 应用描述
    */
    @Size(max= 500,message="编码长度不能超过500")
    @ApiModelProperty("应用描述")
    @Length(max= 500,message="编码长度不能超过500")
    private String description;
    /**
    * 应用类型：1-chatFlow，2-workflow
    */
    @NotNull(message="[应用类型]不能为空")
    @ApiModelProperty("应用类型：1-chatFlow，2-workflow")
    @Column(name = "type")
    private Integer type;
    /**
    * 应用状态
    */
    @ApiModelProperty("应用状态")
    private Integer status;
    /**
    * 应用配置
    */
    @ApiModelProperty("应用配置")
    @Column(name = "inputs", columnDefinition = "text")
    private String inputs;
    /**
    * 应用图标
    */
    @Size(max= 255,message="编码长度不能超过255")
    @ApiModelProperty("应用图标")
    @Length(max= 255,message="编码长度不能超过255")
    private String icon;
    /**
    * 排序
    */
    @ApiModelProperty("排序")
    private Integer sort;
    /**
    * 创建者
    */
    @Size(max= 64,message="编码长度不能超过64")
    @ApiModelProperty("创建者")
    @Length(max= 64,message="编码长度不能超过64")
    private String creator;
    /**
    * 创建时间
    */
    @ApiModelProperty("创建时间")
    private Date createTime;
    /**
    * 更新者
    */
    @Size(max= 64,message="编码长度不能超过64")
    @ApiModelProperty("更新者")
    @Length(max= 64,message="编码长度不能超过64")
    private String updater;
    /**
    * 更新时间
    */
    @ApiModelProperty("更新时间")
    private Date updateTime;
    /**
    * 是否删除
    */
    @ApiModelProperty("是否删除")
    private Integer deleted;
    /**
    * 租户编号
    */
    @NotNull(message="[租户编号]不能为空")
    @ApiModelProperty("租户编号")
    private Integer tenantId;
    /**
    * Dify API_KEY，需要唯一
    */
    @Size(max= 255,message="编码长度不能超过255")
    @ApiModelProperty("Dify API_KEY，需要唯一")
    @Length(max= 255,message="编码长度不能超过255")
    @Column(name = "app_id", unique = true)
    private String appId;
    
    /**
    * Dify API Base URL
    */
    @Size(max= 500,message="编码长度不能超过500")
    @ApiModelProperty("Dify API Base URL")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "api_base_url")
    private String apiBaseUrl;
    
    /**
    * 是否支持流式响应
    */
    @ApiModelProperty("是否支持流式响应")
    @Column(name = "stream_enabled")
    private Boolean streamEnabled;
    
    /**
    * 是否需要上传文件
    */
    @ApiModelProperty("是否需要上传文件")
    @Column(name = "file_upload_enabled")
    private Boolean fileUploadEnabled;
    
    /**
    * 是否显示文本输入框
    */
    @ApiModelProperty("是否显示文本输入框")
    @Column(name = "input_enabled")
    private Boolean inputEnabled;
    
    /**
    * 主题色
    */
    @Size(max= 64,message="编码长度不能超过64")
    @ApiModelProperty("主题色")
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
    public void setCreateTime(Date createTime){
    this.createTime = createTime;
    }

    /**
    * 更新者
    */
    public void setUpdater(String updater){
    this.updater = updater;
    }

    /**
    * 更新时间
    */
    public void setUpdateTime(Date updateTime){
    this.updateTime = updateTime;
    }

    /**
    * 是否删除
    */
    public void setDeleted(Integer deleted){
    this.deleted = deleted;
    }

    /**
    * 租户编号
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
    public Date getCreateTime(){
    return this.createTime;
    }

    /**
    * 更新者
    */
    public String getUpdater(){
    return this.updater;
    }

    /**
    * 更新时间
    */
    public Date getUpdateTime(){
    return this.updateTime;
    }

    /**
    * 是否删除
    */
    public Integer getDeleted(){
    return this.deleted;
    }

    /**
    * 租户编号
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
