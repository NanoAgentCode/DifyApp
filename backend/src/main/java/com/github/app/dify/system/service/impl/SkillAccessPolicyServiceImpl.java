package com.github.app.dify.system.service.impl;

import com.github.app.dify.system.domain.AgentSkillConfig;
import com.github.app.dify.system.repository.AgentSkillConfigRepository;
import com.github.app.dify.system.service.SkillAccessPolicyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
public class SkillAccessPolicyServiceImpl implements SkillAccessPolicyService {

    @Autowired
    private AgentSkillConfigRepository agentSkillConfigRepository;

    @Override
    public boolean canAccess(String skillKey, String role) {
        if (skillKey == null || skillKey.trim().isEmpty()) {
            return false;
        }
        Optional<AgentSkillConfig> optional = agentSkillConfigRepository.findBySkillKeyAndNotDeleted(skillKey.trim());
        if (optional.isEmpty()) {
            return false;
        }
        AgentSkillConfig config = optional.get();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        if (!isSkillFileAvailable(config)) {
            return false;
        }
        if ("admin".equalsIgnoreCase(role)) {
            return true;
        }
        return Boolean.TRUE.equals(config.getVisibleToUser());
    }

    private boolean isSkillFileAvailable(AgentSkillConfig config) {
        if (config.getSkillPath() == null || config.getSkillPath().trim().isEmpty()) {
            return false;
        }
        try {
            Path root = Paths.get("").toAbsolutePath().normalize();
            Path resolved = root.resolve(config.getSkillPath()).normalize();
            Path skillMd = Files.isDirectory(resolved) ? resolved.resolve("SKILL.md") : resolved;
            return skillMd.startsWith(root) && Files.exists(skillMd) && Files.isRegularFile(skillMd);
        } catch (Exception ignored) {
            return false;
        }
    }
}
