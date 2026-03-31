package com.github.app.dify.system.service.impl;

import com.github.app.dify.system.domain.AgentSkillConfig;
import com.github.app.dify.system.repository.AgentSkillConfigRepository;
import com.github.app.dify.system.service.AgentSkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class AgentSkillServiceImpl implements AgentSkillService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final String SOURCE_TYPE_SYSTEM = "system";

    @Autowired
    private AgentSkillConfigRepository agentSkillConfigRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> listAdminSkills() {
        Map<String, DiscoveredSkill> discoveredMap = discoverSkills();
        List<AgentSkillConfig> configs = agentSkillConfigRepository.findByDeletedOrderBySkillKeyAsc(NOT_DELETED);
        Map<String, AgentSkillConfig> configByKey = new HashMap<>();
        for (AgentSkillConfig config : configs) {
            configByKey.put(config.getSkillKey(), config);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        // 先返回目录中存在的 skills（有配置则覆盖，无配置则显示为“已发现”）
        for (Map.Entry<String, DiscoveredSkill> entry : discoveredMap.entrySet()) {
            AgentSkillConfig config = configByKey.get(entry.getKey());
            DiscoveredSkill discoveredSkill = entry.getValue();
            if (config != null) {
                maybeRefreshScannedFields(config, discoveredSkill);
                result.add(toAdminMap(config, discoveredSkill, true));
            } else {
                result.add(toAdminMap(discoveredSkill));
            }
        }
        // 再补充“源不存在但仍有配置”的历史记录
        for (AgentSkillConfig config : configs) {
            if (!discoveredMap.containsKey(config.getSkillKey())) {
                result.add(toAdminMap(config, null, false));
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateSkill(String skillKey, Boolean enabled, Boolean visibleToUser, String description, Long userId,
            String username) {
        if (skillKey == null || skillKey.trim().isEmpty()) {
            throw new IllegalArgumentException("skillKey 不能为空");
        }
        String normalizedKey = skillKey.trim();
        Date now = new Date();
        Optional<AgentSkillConfig> optional = agentSkillConfigRepository.findBySkillKeyAndNotDeleted(normalizedKey);
        AgentSkillConfig config;
        if (optional.isPresent()) {
            config = optional.get();
        } else {
            config = new AgentSkillConfig();
            config.setSkillKey(normalizedKey);
            config.setEnabled(Boolean.TRUE);
            config.setVisibleToUser(Boolean.FALSE);
            config.setCreateTime(now);
            config.setDeleted(NOT_DELETED);
            config.setCreator(username);
            config.setCreatorId(userId);
            config.setSourceType(SOURCE_TYPE_SYSTEM);
        }
        if (enabled != null) {
            config.setEnabled(enabled);
        }
        if (visibleToUser != null) {
            config.setVisibleToUser(visibleToUser);
        }
        if (description != null) {
            config.setDescription(description);
        }
        config.setUpdateTime(now);
        AgentSkillConfig saved = agentSkillConfigRepository.save(config);
        Map<String, DiscoveredSkill> discoveredMap = discoverSkills();
        DiscoveredSkill discoveredSkill = discoveredMap.get(saved.getSkillKey());
        return toAdminMap(saved, discoveredSkill, discoveredSkill != null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncSkills() {
        Map<String, DiscoveredSkill> discoveredMap = discoverSkills();
        return ensureConfigForDiscovered(discoveredMap);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAvailableSkills(String role) {
        Map<String, DiscoveredSkill> discoveredMap = discoverSkills();
        List<AgentSkillConfig> enabledConfigs = agentSkillConfigRepository.findByDeletedAndEnabledOrderBySkillKeyAsc(NOT_DELETED,
                true);
        List<Map<String, Object>> result = new ArrayList<>();
        boolean isAdminRole = "admin".equalsIgnoreCase(role);
        for (AgentSkillConfig config : enabledConfigs) {
            if (!isAdminRole && !Boolean.TRUE.equals(config.getVisibleToUser())) {
                continue;
            }
            DiscoveredSkill discovered = discoveredMap.get(config.getSkillKey());
            if (discovered == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skillKey", config.getSkillKey());
            item.put("skillName", nonBlank(config.getSkillName(), discovered.name));
            item.put("skillPath", discovered.relativePath);
            item.put("description", nonBlank(config.getDescription(), discovered.description));
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSkill(String skillKey) {
        if (skillKey == null || skillKey.trim().isEmpty()) {
            return;
        }
        agentSkillConfigRepository.findBySkillKeyAndNotDeleted(skillKey.trim())
                .ifPresent(agentSkillConfigRepository::delete);
    }

    private int ensureConfigForDiscovered(Map<String, DiscoveredSkill> discoveredMap) {
        if (discoveredMap.isEmpty()) {
            return 0;
        }
        int count = 0;
        Date now = new Date();
        for (Map.Entry<String, DiscoveredSkill> entry : discoveredMap.entrySet()) {
            String skillKey = entry.getKey();
            DiscoveredSkill discoveredSkill = entry.getValue();
            Optional<AgentSkillConfig> optional = agentSkillConfigRepository.findBySkillKey(skillKey);
            if (optional.isPresent()) {
                AgentSkillConfig existing = optional.get();
                if (existing.getDeleted() != null && existing.getDeleted() == DELETED) {
                    existing.setDeleted(NOT_DELETED);
                }
                maybeRefreshScannedFields(existing, discoveredSkill);
                continue;
            }
            AgentSkillConfig config = new AgentSkillConfig();
            config.setSkillKey(skillKey);
            config.setSkillName(discoveredSkill.name);
            config.setSkillPath(discoveredSkill.relativePath);
            config.setDescription(discoveredSkill.description);
            config.setEnabled(Boolean.TRUE);
            config.setVisibleToUser(Boolean.FALSE);
            config.setSourceType(SOURCE_TYPE_SYSTEM);
            config.setCreateTime(now);
            config.setUpdateTime(now);
            config.setDeleted(NOT_DELETED);
            agentSkillConfigRepository.save(config);
            count++;
        }
        return count;
    }

    private void maybeRefreshScannedFields(AgentSkillConfig config, DiscoveredSkill discoveredSkill) {
        if (config == null || discoveredSkill == null) {
            return;
        }
        boolean changed = false;
        if (!equalsNullable(config.getSkillName(), discoveredSkill.name)) {
            config.setSkillName(discoveredSkill.name);
            changed = true;
        }
        if (!equalsNullable(config.getSkillPath(), discoveredSkill.relativePath)) {
            config.setSkillPath(discoveredSkill.relativePath);
            changed = true;
        }
        if ((config.getDescription() == null || config.getDescription().trim().isEmpty())
                && discoveredSkill.description != null && !discoveredSkill.description.trim().isEmpty()) {
            config.setDescription(discoveredSkill.description);
            changed = true;
        }
        if (!equalsNullable(config.getSourceType(), SOURCE_TYPE_SYSTEM)) {
            config.setSourceType(SOURCE_TYPE_SYSTEM);
            changed = true;
        }
        if (changed) {
            config.setUpdateTime(new Date());
            agentSkillConfigRepository.save(config);
        }
    }

    private Map<String, Object> toAdminMap(AgentSkillConfig config, DiscoveredSkill discoveredSkill, boolean sourceExists) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", config.getId());
        map.put("skillKey", config.getSkillKey());
        map.put("skillName", nonBlank(config.getSkillName(), discoveredSkill == null ? null : discoveredSkill.name));
        map.put("skillPath", nonBlank(config.getSkillPath(), discoveredSkill == null ? null : discoveredSkill.relativePath));
        map.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        map.put("visibleToUser", Boolean.TRUE.equals(config.getVisibleToUser()));
        map.put("description", config.getDescription());
        map.put("fileDescription", discoveredSkill == null ? null : discoveredSkill.description);
        map.put("sourceType", config.getSourceType());
        map.put("sourceExists", sourceExists);
        map.put("createTime", config.getCreateTime());
        map.put("updateTime", config.getUpdateTime());
        return map;
    }

    private Map<String, Object> toAdminMap(DiscoveredSkill discoveredSkill) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", null);
        map.put("skillKey", discoveredSkill.key);
        map.put("skillName", discoveredSkill.name);
        map.put("skillPath", discoveredSkill.relativePath);
        map.put("enabled", false);
        map.put("visibleToUser", false);
        map.put("description", discoveredSkill.description);
        map.put("fileDescription", discoveredSkill.description);
        map.put("sourceType", SOURCE_TYPE_SYSTEM);
        map.put("sourceExists", true);
        map.put("createTime", null);
        map.put("updateTime", null);
        return map;
    }

    private String nonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second;
    }

    private boolean equalsNullable(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private Map<String, DiscoveredSkill> discoverSkills() {
        Path root = resolveAgentSkillsRoot();
        if (root == null || !Files.exists(root) || !Files.isDirectory(root)) {
            return Collections.emptyMap();
        }
        Map<String, DiscoveredSkill> result = new HashMap<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream
                    .filter(Files::isDirectory)
                    .forEach(skillDir -> {
                        Path skillMd = skillDir.resolve("SKILL.md");
                        if (!Files.exists(skillMd) || !Files.isRegularFile(skillMd)) {
                            return;
                        }
                        String skillKey = skillDir.getFileName().toString();
                        FrontMatter frontMatter = readFrontMatter(skillMd);
                        String name = nonBlank(frontMatter.name, skillKey);
                        String relativePath = root.getParent().relativize(skillDir).toString().replace("\\", "/");
                        DiscoveredSkill item = new DiscoveredSkill();
                        item.key = skillKey;
                        item.name = name;
                        item.description = frontMatter.description;
                        item.relativePath = relativePath;
                        result.put(skillKey, item);
                    });
        } catch (IOException ignored) {
            return Collections.emptyMap();
        }
        return result;
    }

    private Path resolveAgentSkillsRoot() {
        List<Path> candidates = List.of(
                Paths.get("agent_skills"),
                Paths.get("../agent_skills"),
                Paths.get("../../agent_skills"));
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalized) && Files.isDirectory(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private FrontMatter readFrontMatter(Path skillMd) {
        FrontMatter frontMatter = new FrontMatter();
        try {
            List<String> lines = Files.readAllLines(skillMd, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !"---".equals(lines.get(0).trim())) {
                return frontMatter;
            }
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if ("---".equals(line.trim())) {
                    break;
                }
                int index = line.indexOf(":");
                if (index <= 0) {
                    continue;
                }
                String key = line.substring(0, index).trim();
                String value = line.substring(index + 1).trim();
                if ("name".equalsIgnoreCase(key)) {
                    frontMatter.name = value;
                } else if ("description".equalsIgnoreCase(key)) {
                    frontMatter.description = value;
                }
            }
        } catch (Exception ignored) {
            return frontMatter;
        }
        return frontMatter;
    }

    private static class FrontMatter {
        private String name;
        private String description;
    }

    private static class DiscoveredSkill {
        private String key;
        private String name;
        private String description;
        private String relativePath;
    }
}
