package com.github.app.dify.system.repository;

import com.github.app.dify.system.domain.AgentSkillConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentSkillConfigRepository extends JpaRepository<AgentSkillConfig, Long> {

    Optional<AgentSkillConfig> findBySkillKeyAndDeleted(String skillKey, Integer deleted);

    default Optional<AgentSkillConfig> findBySkillKeyAndNotDeleted(String skillKey) {
        return findBySkillKeyAndDeleted(skillKey, 0);
    }

    Optional<AgentSkillConfig> findBySkillKey(String skillKey);

    List<AgentSkillConfig> findByDeletedOrderBySkillKeyAsc(Integer deleted);

    List<AgentSkillConfig> findByDeletedAndEnabledOrderBySkillKeyAsc(Integer deleted, Boolean enabled);
}
