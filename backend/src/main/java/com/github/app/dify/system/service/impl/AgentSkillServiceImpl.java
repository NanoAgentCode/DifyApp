package com.github.app.dify.system.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        for (AgentSkillConfig config : configs) {
            if (!discoveredMap.containsKey(config.getSkillKey())) {
                result.add(toAdminMap(config, null, false));
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAdminSkillDetail(String skillKey) {
        String normalizedKey = normalizeRequiredSkillKey(skillKey);
        Map<String, DiscoveredSkill> discoveredMap = discoverSkills();
        DiscoveredSkill discoveredSkill = discoveredMap.get(normalizedKey);
        Optional<AgentSkillConfig> optional = agentSkillConfigRepository.findBySkillKeyAndNotDeleted(normalizedKey);

        if (optional.isEmpty() && discoveredSkill == null) {
            throw new IllegalArgumentException("skill does not exist: " + normalizedKey);
        }

        Map<String, Object> detail = optional
                .map(config -> toAdminMap(config, discoveredSkill, discoveredSkill != null))
                .orElseGet(() -> toAdminMap(discoveredSkill));

        String skillContent = readSkillBody(optional.orElse(null), discoveredSkill);
        SkillAvailability availability = checkAvailability(optional.orElse(null), discoveredSkill, skillContent);
        detail.put("skillContent", skillContent);
        detail.put("contentPreview", summarize(skillContent));
        detail.put("contentLength", skillContent == null ? 0 : skillContent.length());
        detail.put("extJsonObject", parseExtJson(detail.get("extJson")));
        fillAvailability(detail, availability);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateSkill(String skillKey, String skillName, Boolean enabled, Boolean visibleToUser, String description,
            String extJson, Long userId, String username) {
        String normalizedKey = normalizeRequiredSkillKey(skillKey);
        validateExtJson(extJson);

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
        if (skillName != null) {
            config.setSkillName(trimToNull(skillName));
        }
        if (enabled != null) {
            config.setEnabled(enabled);
        }
        if (visibleToUser != null) {
            config.setVisibleToUser(visibleToUser);
        }
        if (description != null) {
            config.setDescription(trimToNull(description));
        }
        if (extJson != null) {
            config.setExtJson(trimToNull(extJson));
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
        List<AgentSkillConfig> enabledConfigs = agentSkillConfigRepository.findByDeletedAndEnabledOrderBySkillKeyAsc(NOT_DELETED, true);
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
            String skillContent = readSkillBody(config, discovered);
            SkillAvailability availability = checkAvailability(config, discovered, skillContent);
            if (!availability.usable) {
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
        if (isBlank(config.getSkillName()) && !isBlank(discoveredSkill.name)) {
            config.setSkillName(discoveredSkill.name);
            changed = true;
        }
        if (!equalsNullable(config.getSkillPath(), discoveredSkill.relativePath)) {
            config.setSkillPath(discoveredSkill.relativePath);
            changed = true;
        }
        if (isBlank(config.getDescription()) && !isBlank(discoveredSkill.description)) {
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
        map.put("extJson", config.getExtJson());
        map.put("createTime", config.getCreateTime());
        map.put("updateTime", config.getUpdateTime());
        String skillContent = readSkillBody(config, discoveredSkill);
        fillAvailability(map, checkAvailability(config, discoveredSkill, skillContent));
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
        map.put("extJson", null);
        map.put("createTime", null);
        map.put("updateTime", null);
        String skillContent = readSkillBody(null, discoveredSkill);
        fillAvailability(map, checkAvailability(null, discoveredSkill, skillContent));
        return map;
    }

    private String nonBlank(String first, String second) {
        if (!isBlank(first)) {
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequiredSkillKey(String skillKey) {
        if (skillKey == null || skillKey.trim().isEmpty()) {
            throw new IllegalArgumentException("skillKey cannot be blank");
        }
        return skillKey.trim();
    }

    private void validateExtJson(String extJson) {
        if (isBlank(extJson)) {
            return;
        }
        try {
            OBJECT_MAPPER.readTree(extJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("extJson must be valid JSON");
        }
    }

    private Object parseExtJson(Object extJson) {
        if (!(extJson instanceof String jsonText) || isBlank(jsonText)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(jsonText, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return null;
        }
    }

    private void fillAvailability(Map<String, Object> map, SkillAvailability availability) {
        map.put("usable", availability.usable);
        map.put("availabilityStatus", availability.usable ? "usable" : "invalid");
        map.put("availabilityIssues", availability.issues);
        map.put("hasSkillContent", availability.hasSkillContent);
        map.put("hasAllowedCommands", availability.hasAllowedCommands);
    }

    private boolean hasAllowedCommands(String extJson) {
        if (isBlank(extJson)) {
            return false;
        }
        try {
            Map<String, Object> config = OBJECT_MAPPER.readValue(extJson, new TypeReference<Map<String, Object>>() {
            });
            Object allowedCommands = config.get("allowedCommands");
            if (allowedCommands == null) {
                allowedCommands = config.get("allowed_commands");
            }
            if (allowedCommands instanceof List<?> list) {
                return !list.isEmpty();
            }
            if (allowedCommands instanceof String text) {
                return !isBlank(text);
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private SkillAvailability checkAvailability(AgentSkillConfig config, DiscoveredSkill discoveredSkill, String skillContent) {
        SkillAvailability availability = new SkillAvailability();
        availability.hasSkillContent = !isBlank(skillContent);
        availability.hasAllowedCommands = hasAllowedCommands(config == null ? null : config.getExtJson());

        if (discoveredSkill == null) {
            availability.issues.add("Skill source directory or SKILL.md is missing");
        } else if (discoveredSkill.skillMdPath == null || !Files.exists(discoveredSkill.skillMdPath)) {
            availability.issues.add("SKILL.md file does not exist");
        }

        Path resolvedPath = resolveSkillMarkdownPath(config, discoveredSkill);
        if (resolvedPath == null) {
            availability.issues.add("Skill path is invalid or outside workspace");
        } else if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            availability.issues.add("Resolved skill file is not readable");
        }

        if (!availability.hasSkillContent) {
            availability.issues.add("Skill content is empty");
        }

        String extJson = config == null ? null : config.getExtJson();
        if (!isBlank(extJson)) {
            try {
                Object parsed = parseExtJson(extJson);
                if (parsed == null) {
                    availability.issues.add("extJson is not a valid JSON object");
                }
            } catch (Exception e) {
                availability.issues.add("extJson is invalid");
            }
        }

        availability.usable = availability.issues.isEmpty();
        return availability;
    }

    private Map<String, DiscoveredSkill> discoverSkills() {
        Path root = resolveAgentSkillsRoot();
        if (root == null || !Files.exists(root) || !Files.isDirectory(root)) {
            return Collections.emptyMap();
        }
        Map<String, DiscoveredSkill> result = new HashMap<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(skillDir -> {
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
                item.skillMdPath = skillMd;
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

    private String readSkillBody(AgentSkillConfig config, DiscoveredSkill discoveredSkill) {
        try {
            Path skillMd = resolveSkillMarkdownPath(config, discoveredSkill);
            if (skillMd == null || !Files.exists(skillMd) || !Files.isRegularFile(skillMd)) {
                return null;
            }
            return Files.readString(skillMd, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path resolveSkillMarkdownPath(AgentSkillConfig config, DiscoveredSkill discoveredSkill) {
        if (discoveredSkill != null && discoveredSkill.skillMdPath != null) {
            return discoveredSkill.skillMdPath;
        }
        if (config == null || isBlank(config.getSkillPath())) {
            return null;
        }
        Path root = Paths.get("").toAbsolutePath().normalize();
        Path resolved = root.resolve(config.getSkillPath()).normalize();
        if (!resolved.startsWith(root)) {
            return null;
        }
        if (Files.isDirectory(resolved)) {
            return resolved.resolve("SKILL.md");
        }
        return resolved;
    }

    private String summarize(String skillContent) {
        if (skillContent == null) {
            return null;
        }
        String normalized = skillContent.replace("\r", "").trim();
        if (normalized.length() <= 400) {
            return normalized;
        }
        return normalized.substring(0, 400) + "...";
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
        private Path skillMdPath;
    }

    private static class SkillAvailability {
        private boolean usable;
        private boolean hasSkillContent;
        private boolean hasAllowedCommands;
        private final List<String> issues = new ArrayList<>();
    }
}
