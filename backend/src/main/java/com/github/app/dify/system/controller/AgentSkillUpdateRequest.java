package com.github.app.dify.system.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新技能配置请求")
public class AgentSkillUpdateRequest {

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "普通用户是否可见")
    private Boolean visibleToUser;

    @Schema(description = "技能描述（可选）")
    private String description;
}
