package com.github.app.dify.service;

import java.util.concurrent.TimeUnit;

/**
 * 缓存服务接口
 * 提供统一的缓存操作方法
 */
public interface CacheService {
    
    /**
     * 缓存前缀常量
     */
    class CacheKey {
        // 用户相关
        public static final String USER_PREFIX = "user:";
        public static final String USER_BY_ID = USER_PREFIX + "id:";
        public static final String USER_BY_USERNAME = USER_PREFIX + "username:";
        
        // 模型配置相关
        public static final String MODEL_PREFIX = "model:";
        public static final String QA_MODEL = MODEL_PREFIX + "qa:";
        public static final String EMBEDDING_MODEL = MODEL_PREFIX + "embedding:";
        public static final String MODEL_CONFIG = MODEL_PREFIX + "config";
        
        // AI应用相关
        public static final String APP_PREFIX = "app:";
        public static final String APP_BY_ID = APP_PREFIX + "id:";
        public static final String APP_BY_API_KEY = APP_PREFIX + "apikey:";
        public static final String APP_LIST = APP_PREFIX + "list:";
        
        // 知识库相关
        public static final String KB_PREFIX = "kb:";
        public static final String KB_BY_ID = KB_PREFIX + "id:";
        public static final String KB_LIST = KB_PREFIX + "list:";
        
        // 权限相关
        public static final String PERMISSION_PREFIX = "permission:";
        public static final String USER_APP_VISIBILITY = PERMISSION_PREFIX + "app:user:";
        public static final String USER_KB_VISIBILITY = PERMISSION_PREFIX + "kb:user:";
    }
    
    /**
     * 设置缓存
     */
    void set(String key, Object value);
    
    /**
     * 设置缓存（带过期时间）
     */
    void set(String key, Object value, long timeout, TimeUnit unit);
    
    /**
     * 获取缓存
     */
    Object get(String key);
    
    /**
     * 删除缓存
     */
    void delete(String key);
    
    /**
     * 根据前缀删除缓存
     */
    void deleteByPrefix(String prefix);
    
    /**
     * 检查缓存是否存在
     */
    boolean exists(String key);
    
    /**
     * 设置缓存过期时间
     */
    void expire(String key, long timeout, TimeUnit unit);
    
    /**
     * 获取缓存剩余过期时间（秒）
     */
    long getExpire(String key);
}
