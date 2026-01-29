package com.github.app.dify.ops.observability.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 反射缓存工具类
 * 缓存Method和Field，减少反射调用开销
 */
public class ReflectionCache {

    private static final Logger logger = LoggerFactory.getLogger(ReflectionCache.class);

    /**
     * Method缓存：类名 + 方法名 -> Method
     */
    private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    /**
     * Field缓存：类名 + 字段名 -> Field
     */
    private static final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取getter方法（带缓存）
     */
    public static Method getGetterMethod(Class<?> clazz, String propertyName) {
        String key = clazz.getName() + "#get" + capitalize(propertyName);
        return METHOD_CACHE.computeIfAbsent(key, k -> {
            try {
                String getterName = "get" + capitalize(propertyName);
                Method method = clazz.getMethod(getterName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                return null; // 方法不存在
            }
        });
    }

    /**
     * 获取字段（带缓存）
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "#" + fieldName;
        return FIELD_CACHE.computeIfAbsent(key, k -> {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                return null; // 字段不存在
            }
        });
    }

    /**
     * 从对象中提取属性值（使用缓存）
     */
    public static Object getPropertyValue(Object obj, String propertyName) {
        if (obj == null) {
            return null;
        }

        Class<?> clazz = obj.getClass();

        // 优先使用getter方法
        Method getter = getGetterMethod(clazz, propertyName);
        if (getter != null) {
            try {
                return getter.invoke(obj);
            } catch (Exception e) {
                logger.debug("调用getter方法失败: {}", e.getMessage());
            }
        }

        // 如果getter不存在，尝试直接访问字段
        Field field = getField(clazz, propertyName);
        if (field != null) {
            try {
                return field.get(obj);
            } catch (Exception e) {
                logger.debug("访问字段失败: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 首字母大写
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 清空缓存（用于测试或内存优化）
     */
    public static void clearCache() {
        METHOD_CACHE.clear();
        FIELD_CACHE.clear();
        logger.info("反射缓存已清空");
    }

    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats() {
        return String.format("Method缓存: %d, Field缓存: %d", 
                METHOD_CACHE.size(), FIELD_CACHE.size());
    }
}
