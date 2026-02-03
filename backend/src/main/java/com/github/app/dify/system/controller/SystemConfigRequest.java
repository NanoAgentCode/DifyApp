package com.github.app.dify.system.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统配置新增/更新请求体（管理端）
 */
@Data
@Schema(description = "系统配置新增/更新请求")
public class SystemConfigRequest {

    @Schema(description = "配置键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String configKey;

    @Schema(description = "配置值")
    private String configValue;

    @Schema(description = "配置分组")
    private String configGroup;

    @Schema(description = "配置类型")
    private String configType;

    @Schema(description = "配置描述")
    private String description;
}
