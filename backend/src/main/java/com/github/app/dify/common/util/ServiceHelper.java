package com.github.app.dify.common.util;

import com.github.app.dify.common.exception.NotFoundException;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * 服务层通用工具类
 * 用于减少服务实现中的重复代码
 */
public class ServiceHelper {

    /**
     * 检查值是否为 null，为 null 则抛出 NotFoundException（用于 Controller/Service 返回非 Optional 的场景）
     *
     * @param value 可能为 null 的返回值
     * @param resourceName 资源名称（如"知识库"、"数据源"等）
     * @param resourceId 资源ID（用于日志与异常信息）
     * @param logger Logger，可为 null
     * @param <T> 类型
     * @return 非 null 的值
     */
    public static <T> T checkNotNull(T value, String resourceName, Object resourceId, Logger logger) {
        if (value == null) {
            if (logger != null) {
                logger.warn("{}不存在 - ID: {}", resourceName, resourceId);
            }
            throw new NotFoundException(resourceName + "不存在" + (resourceId != null ? ": " + resourceId : ""));
        }
        return value;
    }

    /**
     * 检查Optional是否存在，如果不存在则抛出NotFoundException
     *
     * @param optional Optional对象
     * @param resourceName 资源名称（如"知识库"、"文档"等）
     * @param <T> 实体类型
     * @return 存在的实体
     */
    public static <T> T checkExistsAndGet(Optional<T> optional, String resourceName) {
        if (!optional.isPresent()) {
            throw new NotFoundException(resourceName + "不存在");
        }
        return optional.get();
    }

    /**
     * 检查Optional是否存在，如果不存在则抛出NotFoundException
     *
     * @param optional Optional对象
     * @param resourceName 资源名称
     * @param resourceId 资源ID（用于日志）
     * @param logger Logger对象（用于记录日志）
     * @param <T> 实体类型
     * @return 存在的实体
     */
    public static <T> T checkExistsAndGet(Optional<T> optional, String resourceName, Object resourceId, Logger logger) {
        if (!optional.isPresent()) {
            if (logger != null) {
                logger.warn("{}不存在 - ID: {}", resourceName, resourceId);
            }
            throw new NotFoundException(resourceName + "不存在");
        }
        return optional.get();
    }

    /**
     * 记录资源创建成功的日志
     *
     * @param logger Logger对象
     * @param resourceName 资源名称（如"知识库"、"文档"等）
     * @param resourceId 资源ID
     */
    public static void logCreateSuccess(Logger logger, String resourceName, Long resourceId) {
        logger.info("{}创建成功 - ID: {}", resourceName, resourceId);
    }

    /**
     * 记录资源更新成功的日志
     *
     * @param logger Logger对象
     * @param resourceName 资源名称
     * @param resourceId 资源ID
     */
    public static void logUpdateSuccess(Logger logger, String resourceName, Long resourceId) {
        logger.info("{}更新成功 - ID: {}", resourceName, resourceId);
    }

    /**
     * 记录资源删除成功的日志
     *
     * @param logger Logger对象
     * @param resourceName 资源名称
     * @param resourceId 资源ID
     */
    public static void logDeleteSuccess(Logger logger, String resourceName, Long resourceId) {
        logger.info("{}删除成功 - ID: {}", resourceName, resourceId);
    }

    /**
     * 记录资源操作成功的日志（通用）
     *
     * @param logger Logger对象
     * @param action 操作类型（如"创建"、"更新"、"删除"等）
     * @param resourceName 资源名称
     * @param resourceId 资源ID
     * @param additionalInfo 额外信息（可选）
     */
    public static void logSuccess(Logger logger, String action, String resourceName, Long resourceId, String additionalInfo) {
        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            logger.info("{}{}成功 - ID: {}, {}", action, resourceName, resourceId, additionalInfo);
        } else {
            logger.info("{}{}成功 - ID: {}", action, resourceName, resourceId);
        }
    }
}
