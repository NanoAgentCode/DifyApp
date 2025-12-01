package com.github.app.dify.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 缓存服务工具类
 * 提供统一的缓存操作方法
 */
@Service
public class CacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 缓存前缀常量
     */
    public static class CacheKey {
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
     * @param key 缓存键
     * @param value 缓存值
     */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            logger.debug("设置缓存成功 - Key: {}", key);
        } catch (Exception e) {
            logger.error("设置缓存失败 - Key: {}", key, e);
        }
    }
    
    /**
     * 设置缓存（带过期时间）
     * @param key 缓存键
     * @param value 缓存值
     * @param timeout 过期时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            logger.debug("设置缓存成功（带过期时间） - Key: {}, Timeout: {} {}", key, timeout, unit);
        } catch (Exception e) {
            logger.error("设置缓存失败 - Key: {}", key, e);
        }
    }
    
    /**
     * 获取缓存
     * @param key 缓存键
     * @return 缓存值
     */
    public Object get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            logger.debug("获取缓存 - Key: {}, Hit: {}", key, value != null);
            return value;
        } catch (Exception e) {
            logger.error("获取缓存失败 - Key: {}", key, e);
            return null;
        }
    }
    
    /**
     * 获取缓存（指定类型）
     * @param key 缓存键
     * @param clazz 类型
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null && clazz.isInstance(value)) {
                logger.debug("获取缓存成功 - Key: {}, Type: {}", key, clazz.getSimpleName());
                return (T) value;
            }
            return null;
        } catch (Exception e) {
            logger.error("获取缓存失败 - Key: {}, Type: {}", key, clazz.getSimpleName(), e);
            return null;
        }
    }
    
    /**
     * 删除缓存
     * @param key 缓存键
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            logger.debug("删除缓存成功 - Key: {}", key);
        } catch (Exception e) {
            logger.error("删除缓存失败 - Key: {}", key, e);
        }
    }
    
    /**
     * 批量删除缓存（根据前缀）
     * @param prefix 缓存键前缀
     */
    public void deleteByPrefix(String prefix) {
        try {
            redisTemplate.delete(redisTemplate.keys(prefix + "*"));
            logger.debug("批量删除缓存成功 - Prefix: {}", prefix);
        } catch (Exception e) {
            logger.error("批量删除缓存失败 - Prefix: {}", prefix, e);
        }
    }
    
    /**
     * 判断缓存是否存在
     * @param key 缓存键
     * @return 是否存在
     */
    public boolean exists(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return exists != null && exists;
        } catch (Exception e) {
            logger.error("判断缓存是否存在失败 - Key: {}", key, e);
            return false;
        }
    }
    
    /**
     * 设置过期时间
     * @param key 缓存键
     * @param timeout 过期时间
     * @param unit 时间单位
     */
    public void expire(String key, long timeout, TimeUnit unit) {
        try {
            redisTemplate.expire(key, timeout, unit);
            logger.debug("设置过期时间成功 - Key: {}, Timeout: {} {}", key, timeout, unit);
        } catch (Exception e) {
            logger.error("设置过期时间失败 - Key: {}", key, e);
        }
    }
    
    /**
     * 获取剩余过期时间
     * @param key 缓存键
     * @return 剩余过期时间（秒），-1表示永不过期，-2表示键不存在
     */
    public long getExpire(String key) {
        try {
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return expire != null ? expire : -2;
        } catch (Exception e) {
            logger.error("获取过期时间失败 - Key: {}", key, e);
            return -2;
        }
    }
}

