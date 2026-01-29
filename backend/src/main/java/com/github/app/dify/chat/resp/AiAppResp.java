package com.github.app.dify.chat.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
/**
 * AI应用响应
 */
@Setter
@Getter
@Schema(description = "AI应用响应")
public class AiAppResp {

    // Getters and Setters
    @Schema(description = "应用编号")
    private Long id;
    
    @Schema(description = "应用名称")
    private String name;
    
    @Schema(description = "应用描述")
    private String description;
    
    @Schema(description = "应用类型：1-chatFlow，2-workflow")
    private Integer type;
    
    @Schema(description = "应用状态")
    private Integer status;
    
    @Schema(description = "应用配置")
    private String inputs;
    
    @Schema(description = "应用图标")
    private String icon;
    
    @Schema(description = "排序")
    private Integer sort;
    
    @Schema(description = "创建者")
    private String creator;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新者")
    private String updater;
    
    @Schema(description = "更新时间")
    private Date updateTime;
    
    @Schema(description = "租户编号")
    private Integer tenantId;
    
    @Schema(description = "Dify API Key")
    private String appId;
    
    @Schema(description = "Dify API Base URL")
    private String apiBaseUrl;
    
    @Schema(description = "是否支持流式响应")
    private Boolean streamEnabled;
    
    @Schema(description = "是否需要上传文件")
    private Boolean fileUploadEnabled;
    
    @Schema(description = "主题色")
    private String themeColor;

}