package com.github.app.dify.chat.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建AI应用请求
 */
@Setter
@Getter
@Schema(description = "创建AI应用请求")
public class CreateAiAppReq {

    // Getters and Setters
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 100, message = "应用名称长度不能超过100")
    @Schema(description = "应用名称")
    private String name;
    
    @Size(max = 500, message = "应用描述长度不能超过500")
    @Schema(description = "应用描述")
    private String description;
    
    @NotNull(message = "应用类型不能为空")
    @Schema(description = "应用类型：1-chatFlow，2-workflow")
    private Integer type;
    
    @NotBlank(message = "Dify API Key不能为空")
    @Size(max = 255, message = "API Key长度不能超过255")
    @Schema(description = "Dify API Key")
    private String appId;
    
    @Size(max = 500, message = "API Base URL长度不能超过500")
    @Schema(description = "Dify API Base URL")
    private String apiBaseUrl;
    
    @Schema(description = "是否支持流式响应")
    private Boolean streamEnabled;
    
    @Schema(description = "是否需要上传文件")
    private Boolean fileUploadEnabled;
    
    @Size(max = 255, message = "应用图标长度不能超过255")
    @Schema(description = "应用图标")
    private String icon;
    
    @Schema(description = "排序")
    private Integer sort;
    
    @Size(max = 64, message = "主题色长度不能超过64")
    @Schema(description = "主题色")
    private String themeColor;
    
    @Schema(description = "应用配置JSON")
    private String inputs;
    
    @NotNull(message = "租户编号不能为空")
    @Schema(description = "租户编号")
    private Integer tenantId;

}