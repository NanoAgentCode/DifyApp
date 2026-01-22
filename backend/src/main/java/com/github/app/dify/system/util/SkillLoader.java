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
    
    /**
     * 加载技能文件并替换模板变量
     * @param skillName 技能文件名（不含 .md 后缀）
     * @param variables 模板变量映射，key 为变量名（不含花括号），value 为替换值
     * @return 替换后的内容，如果文件不存在返回 null
     */
    public static String loadSkillWithTemplate(String skillName, Map<String, String> variables) {
        String content = loadSkill(skillName);
        if (content == null || variables == null || variables.isEmpty()) {
            return content;
        }
        
        // 替换模板变量 {variableName}
        String result = content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue() != null ? entry.getValue() : "");
        }
        
        return result;
    }
    
    /**
     * 清除缓存（用于开发环境，当提示词文件更新后可以清除缓存重新加载）
     */
    public static void clearCache() {
        CACHE.clear();
        logger.info("技能文件缓存已清除");
    }
    
    /**
     * 清除指定技能的缓存
     */
    public static void clearCache(String skillName) {
        if (skillName != null) {
            CACHE.remove(skillName.trim());
            logger.info("技能文件缓存已清除: {}", skillName);
        }
    }
}

