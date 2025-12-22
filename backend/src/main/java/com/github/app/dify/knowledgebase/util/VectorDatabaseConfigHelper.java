package com.github.app.dify.knowledgebase.util;

import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 向量数据库配置辅助工具类
 * 提供公共的配置获取和解析方法
 */
@Component
public class VectorDatabaseConfigHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorDatabaseConfigHelper.class);
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    /**
     * 根据类型获取向量数据库配置
     * 优先级：默认启用配置 > 第一个启用配置 > 第一个配置（包括未启用的）
     * 
     * @param type 向量数据库类型（如：qdrant、milvus、pgvector等）
     * @return 向量数据库配置，如果不存在则返回null
     */
    public VectorDatabase getConfigByType(String type) {
        if (vectorDatabaseRepository == null) {
            return null;
        }
        try {
            // 先尝试查找默认的启用配置
            Optional<VectorDatabase> defaultConfig = vectorDatabaseRepository.findDefaultEnabledByType(type);
            if (defaultConfig.isPresent()) {
                return defaultConfig.get();
            }
            
            // 如果没有默认配置，尝试查找第一个启用的配置
            List<VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType(type);
            if (!enabledConfigs.isEmpty()) {
                return enabledConfigs.get(0);
            }
            
            // 如果连启用的配置都没有，尝试查找任何配置（包括未启用的）
            List<VectorDatabase> allConfigs = vectorDatabaseRepository.findByType(type);
            if (!allConfigs.isEmpty()) {
                return allConfigs.get(0);
            }
        } catch (Exception e) {
            logger.warn("获取{}配置失败: {}", type, e.getMessage());
        }
        return null;
    }
    
    /**
     * 从 extraConfig JSON 字符串中解析用户名和密码
     * 
     * @param extraConfig extraConfig JSON 字符串
     * @return 包含 username 和 password 的数组，[0] 为 username，[1] 为 password，如果解析失败则返回 null
     */
    public String[] parseUsernamePasswordFromExtraConfig(String extraConfig) {
        if (extraConfig == null || extraConfig.trim().isEmpty()) {
            return null;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> config = mapper.readValue(extraConfig, Map.class);
            
            String username = null;
            String password = null;
            
            if (config.containsKey("username")) {
                username = (String) config.get("username");
            }
            if (config.containsKey("password")) {
                password = (String) config.get("password");
            }
            
            if (username != null || password != null) {
                return new String[]{username, password};
            }
        } catch (Exception e) {
            logger.debug("解析extraConfig失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 从 VectorDatabase 配置中提取用户名和密码
     * 优先从 extraConfig 中解析，如果没有则尝试从 apiKey 中解析（向后兼容）
     * 
     * @param config 向量数据库配置
     * @return 包含 username 和 password 的数组，[0] 为 username，[1] 为 password，如果都不存在则返回 null
     */
    public String[] extractUsernamePassword(VectorDatabase config) {
        if (config == null) {
            return null;
        }
        
        // 优先从 extraConfig 中解析
        String[] credentials = parseUsernamePasswordFromExtraConfig(config.getExtraConfig());
        if (credentials != null && credentials[0] != null) {
            return credentials;
        }
        
        // 如果没有从 extraConfig 获取到，尝试从 apiKey（向后兼容）
        // apiKey 可能包含 "username:password" 格式
        if (config.getApiKey() != null && !config.getApiKey().trim().isEmpty()) {
            String apiKey = config.getApiKey();
            if (apiKey.contains(":")) {
                String[] parts = apiKey.split(":", 2);
                return new String[]{parts[0], parts.length > 1 ? parts[1] : ""};
            }
        }
        
        return null;
    }
    
    /**
     * 从 VectorDatabase 配置中提取用户名
     * 
     * @param config 向量数据库配置
     * @return 用户名，如果不存在则返回 null
     */
    public String extractUsername(VectorDatabase config) {
        String[] credentials = extractUsernamePassword(config);
        return credentials != null ? credentials[0] : null;
    }
    
    /**
     * 从 VectorDatabase 配置中提取密码
     * 
     * @param config 向量数据库配置
     * @return 密码，如果不存在则返回 null
     */
    public String extractPassword(VectorDatabase config) {
        String[] credentials = extractUsernamePassword(config);
        return credentials != null ? credentials[1] : null;
    }
}

