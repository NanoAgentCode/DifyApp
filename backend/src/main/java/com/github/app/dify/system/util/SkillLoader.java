package com.github.app.dify.system.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkillLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(SkillLoader.class);
    
    private static final String SKILL_BASE_PATH = "skills/";
    
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    
    public static String loadSkill(String skillName) {
        if (skillName == null || skillName.trim().isEmpty()) {
            return null;
        }
        String key = skillName.trim();
        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }
        String fileName = key.endsWith(".md") ? key : key + ".md";
        String path = SKILL_BASE_PATH + fileName;
        try {
            Resource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                logger.warn("技能文件不存在: {}", path);
                return null;
            }
            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                String content = FileCopyUtils.copyToString(reader);
                CACHE.put(key, content);
                return content;
            }
        } catch (Exception e) {
            logger.error("加载技能文件失败: {}", path, e);
            return null;
        }
    }
}

