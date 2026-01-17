package com.github.app.dify.cache;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 自定义缓存键生成器
 * 
 * 优化点：
 * 1. 使用MD5哈希替代hashCode，避免冲突
 * 2. 统一缓存键格式：{namespace}:{type}:{id}:{hash}
 * 3. 支持多参数组合
 * 4. 避免缓存键过长（Redis键长度限制）
 */
@Component
public class CacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder keyBuilder = new StringBuilder();
        
        // 添加类名简写作为命名空间
        String className = target.getClass().getSimpleName();
        String namespace = className.replaceAll("(Impl|Service|Controller)", "")
                            .toLowerCase();
        keyBuilder.append(namespace).append(":");
        
        // 添加方法名作为类型
        keyBuilder.append(method.getName()).append(":");
        
        // 处理参数
        if (params != null && params.length > 0) {
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                
                if (param == null) {
                    keyBuilder.append("null");
                } else if (param instanceof String) {
                    // 字符串参数使用MD5哈希，避免键过长
                    String md5Hash = DigestUtils.md5DigestAsHex(
                        ((String) param).getBytes(StandardCharsets.UTF_8));
                    keyBuilder.append(md5Hash);
                } else {
                    // 其他类型直接使用toString
                    keyBuilder.append(param.toString());
                }
                
                if (i < params.length - 1) {
                    keyBuilder.append(":");
                }
            }
        }
        
        String key = keyBuilder.toString();
        
        // 限制键长度（Redis建议键长度不超过250字节）
        if (key.length() > 250) {
            key = DigestUtils.md5DigestAsHex(
                key.getBytes(StandardCharsets.UTF_8));
        }
        
        return key;
    }
    
    /**
     * 生成向量化缓存键
     * 格式：embedding:query:{modelId}:{textHash}
     * 优化：使用MD5替代hashCode，避免冲突
     */
    public static String generateEmbeddingKey(Long modelId, String text) {
        String modelKey = modelId != null ? modelId.toString() : "default";
        String textHash = DigestUtils.md5DigestAsHex(
            text.getBytes(StandardCharsets.UTF_8));
        return "embedding:query:" + modelKey + ":" + textHash;
    }
    
    /**
     * 生成RAG检索缓存键
     * 格式：rag:kb:{knowledgeBaseId}:{modelId}:{topK}:{queryHash}
     */
    public static String generateRagRetrievalKey(Long knowledgeBaseId, String query, 
                                               Long modelId, Integer topK) {
        String modelKey = modelId != null ? modelId.toString() : "default";
        String topKKey = topK != null ? topK.toString() : "default";
        String queryHash = DigestUtils.md5DigestAsHex(
            query.getBytes(StandardCharsets.UTF_8));
        
        return "rag:kb:" + knowledgeBaseId + ":model:" + modelKey 
               + ":topK:" + topKKey + ":" + queryHash;
    }
    
    /**
     * 生成用户缓存键
     * 格式：user:{userId} 或 user:username:{username}
     */
    public static String generateUserKey(Object userIdOrUsername) {
        if (userIdOrUsername instanceof Long) {
            return "user:" + userIdOrUsername;
        } else {
            return "user:username:" + userIdOrUsername;
        }
    }
    
    /**
     * 生成模型缓存键
     * 格式：{modelType}:{modelId} 或 {modelType}:default:{type}
     */
    public static String generateModelKey(String modelType, Object modelIdOrType) {
        if (modelIdOrType instanceof Long) {
            return modelType + ":" + modelIdOrType;
        } else {
            return modelType + ":default:" + modelIdOrType;
        }
    }
    
    /**
     * 生成应用缓存键
     * 格式：aiApp:{id} 或 aiApp:apikey:{apiKey}
     */
    public static String generateAiAppKey(Object idOrApiKey) {
        if (idOrApiKey instanceof Long) {
            return "aiApp:" + idOrApiKey;
        } else {
            String apiKeyHash = DigestUtils.md5DigestAsHex(
                ((String) idOrApiKey).getBytes(StandardCharsets.UTF_8));
            return "aiApp:apikey:" + apiKeyHash;
        }
    }
    
    /**
     * 生成向量数据库缓存键
     * 格式：vectorDatabase:{type}_default
     */
    public static String generateVectorDatabaseKey(String type) {
        return "vectorDatabase:" + type + "_default";
    }
    
    /**
     * 生成统计缓存键
     * 格式：statistics:{type}:{paramsHash}
     */
    public static String generateStatisticsKey(String type, Map<String, Object> params) {
        StringBuilder keyBuilder = new StringBuilder("statistics:").append(type);
        
        if (params != null && !params.isEmpty()) {
            keyBuilder.append(":");
            // 对参数进行排序，确保相同参数生成相同的键
            params.entrySet().stream()
                   .sorted(Map.Entry.comparingByKey())
                   .forEach(entry -> {
                       keyBuilder.append(entry.getKey())
                               .append("=")
                               .append(entry.getValue())
                               .append("&");
                   });
            // 移除最后一个&
            if (keyBuilder.charAt(keyBuilder.length() - 1) == '&') {
                keyBuilder.setLength(keyBuilder.length() - 1);
            }
        }
        
        String key = keyBuilder.toString();
        
        // 如果键过长，使用MD5
        if (key.length() > 250) {
            key = "statistics:" + type + ":" + DigestUtils.md5DigestAsHex(
                key.getBytes(StandardCharsets.UTF_8));
        }
        
        return key;
    }
}
