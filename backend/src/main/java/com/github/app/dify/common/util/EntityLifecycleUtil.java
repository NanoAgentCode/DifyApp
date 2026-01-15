package com.github.app.dify.common.util;

import org.springframework.data.repository.CrudRepository;

import java.lang.reflect.Method;
import java.util.Date;

public class EntityLifecycleUtil {

    public static <T> void setCreateTime(T entity) {
        setDateProperty(entity, "setCreateTime", DateTimeUtil.now());
    }

    public static <T> void setUpdateTime(T entity) {
        setDateProperty(entity, "setUpdateTime", DateTimeUtil.now());
    }

    public static <T> void setCreateAndUpdateTime(T entity) {
        Date now = DateTimeUtil.now();
        setDateProperty(entity, "setCreateTime", now);
        setDateProperty(entity, "setUpdateTime", now);
    }

    public static <T, ID> void softDelete(T entity, CrudRepository<T, ID> repository) {
        setDeleted(entity, 1);
        setUpdateTime(entity);
        repository.save(entity);
    }

    public static <T, ID> void restore(T entity, CrudRepository<T, ID> repository) {
        setDeleted(entity, 0);
        setUpdateTime(entity);
        repository.save(entity);
    }

    public static <T> boolean isDeleted(T entity) {
        Integer deleted = (Integer) invokeGetter(entity, "getDeleted");
        return deleted != null && deleted == 1;
    }

    private static <T> void setDateProperty(T entity, String setterName, Date value) {
        invokeSetter(entity, setterName, Date.class, value);
    }

    private static <T> void setDeleted(T entity, int deleted) {
        if (!invokeSetter(entity, "setDeleted", Integer.class, deleted)) {
            invokeSetter(entity, "setDeleted", int.class, deleted);
        }
    }

    private static <T> Object invokeGetter(T entity, String getterName) {
        try {
            Method method = entity.getClass().getMethod(getterName);
            return method.invoke(entity);
        } catch (Exception e) {
            throw new IllegalStateException(entity.getClass().getName() + "#" + getterName + " 调用失败", e);
        }
    }

    private static <T> boolean invokeSetter(T entity, String setterName, Class<?> parameterType, Object value) {
        try {
            Method method = entity.getClass().getMethod(setterName, parameterType);
            method.invoke(entity, value);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            throw new IllegalStateException(entity.getClass().getName() + "#" + setterName + " 调用失败", e);
        }
    }
}

