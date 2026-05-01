package com.github.app.dify.system.service;

import java.util.List;
import java.util.Map;

public interface AgentSkillService {

    List<Map<String, Object>> listAdminSkills();

    Map<String, Object> getAdminSkillDetail(String skillKey);

    Map<String, Object> updateSkill(String skillKey, String skillName, Boolean enabled, Boolean visibleToUser, String description,
            String extJson, Long userId, String username);

    int syncSkills();

    List<Map<String, Object>> listAvailableSkills(String role);

    void deleteSkill(String skillKey);
}
