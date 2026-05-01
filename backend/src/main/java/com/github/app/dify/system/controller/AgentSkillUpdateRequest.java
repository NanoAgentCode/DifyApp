package com.github.app.dify.system.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Update skill config request")
public class AgentSkillUpdateRequest {

    @Schema(description = "Skill name")
    private String skillName;

    @Schema(description = "Enabled")
    private Boolean enabled;

    @Schema(description = "Visible to normal user")
    private Boolean visibleToUser;

    @Schema(description = "Skill description")
    private String description;

    @Schema(description = "Extended json config")
    private String extJson;
}
