package com.github.app.dify.system.util;

import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 classpath skills/ 目录加载 .md 技能模板（系统提示、用户模板等）
 */
public final class SkillLoader {

    private static final String SKILLS_PREFIX = "skills/";
    private static final String SUFFIX = ".md";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    private SkillLoader() {
    }

    /**
     * 加载技能文件内容（UTF-8），路径相对于 skills/，自动补全 .md
     * 例如 loadSkill("chat/system_prompt") 读取 skills/chat/system_prompt.md
     *
     * @param path 相对路径，不含 .md
     * @return 文件内容，不存在或读失败时返回空字符串
     */
    public static String loadSkill(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        String resourcePath = SKILLS_PREFIX + path.trim() + SUFFIX;
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                return "";
            }
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 加载技能模板并替换占位符 {key} 为 variables 中的值
     *
     * @param path     相对路径，不含 .md
     * @param variables 占位符 key -> 替换值
     * @return 替换后的内容，不存在或读失败时返回空字符串
     */
    public static String loadSkillWithTemplate(String path, Map<String, ?> variables) {
        String raw = loadSkill(path);
        if (raw.isEmpty() || variables == null || variables.isEmpty()) {
            return raw;
        }
        Matcher m = PLACEHOLDER_PATTERN.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object value = variables.get(key);
            String replacement = value != null ? value.toString() : m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
