package com.github.app.dify.system.util;

import com.github.app.dify.common.util.EntityLifecycleUtil;
import com.github.app.dify.system.domain.DrawIODiagram;
import com.github.app.dify.system.domain.DrawIOHistory;
import com.github.app.dify.system.domain.Prompt;
import com.github.app.dify.system.domain.SystemConfig;

/**
 * 系统日期时间工具类
 * 提供系统相关实体的日期时间处理方法
 */
public class SystemDateTimeUtil {
    
    /**
     * 设置系统配置的创建时间
     * 适用于新建系统配置
     * 
     * @param config 系统配置实体
     */
    public static void setCreateTime(SystemConfig config) {
        EntityLifecycleUtil.setCreateTime(config);
    }
    
    /**
     * 设置系统配置的更新时间
     * 适用于更新系统配置
     * 
     * @param config 系统配置实体
     */
    public static void setUpdateTime(SystemConfig config) {
        EntityLifecycleUtil.setUpdateTime(config);
    }
    
    /**
     * 设置提示词的创建时间和更新时间
     * 适用于新建提示词
     * 
     * @param prompt 提示词实体
     */
    public static void setCreateAndUpdateTime(Prompt prompt) {
        EntityLifecycleUtil.setCreateAndUpdateTime(prompt);
    }
    
    /**
     * 设置提示词的更新时间
     * 适用于更新提示词
     * 
     * @param prompt 提示词实体
     */
    public static void setUpdateTime(Prompt prompt) {
        EntityLifecycleUtil.setUpdateTime(prompt);
    }
    
    /**
     * 设置图表的创建时间和更新时间
     * 适用于新建图表
     * 
     * @param diagram 图表实体
     */
    public static void setCreateAndUpdateTime(DrawIODiagram diagram) {
        EntityLifecycleUtil.setCreateAndUpdateTime(diagram);
    }
    
    /**
     * 设置图表的更新时间
     * 适用于更新图表
     * 
     * @param diagram 图表实体
     */
    public static void setUpdateTime(DrawIODiagram diagram) {
        EntityLifecycleUtil.setUpdateTime(diagram);
    }
    
    /**
     * 设置历史记录的创建时间
     * 适用于新建历史记录
     * 
     * @param history 历史记录实体
     */
    public static void setCreateTime(DrawIOHistory history) {
        EntityLifecycleUtil.setCreateTime(history);
    }
}

