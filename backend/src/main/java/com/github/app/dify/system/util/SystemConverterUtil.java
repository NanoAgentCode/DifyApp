package com.github.app.dify.system.util;

import com.github.app.dify.system.domain.DrawIODiagram;
import com.github.app.dify.system.domain.Prompt;
import com.github.app.dify.system.domain.SystemConfig;
import com.github.app.dify.system.resp.DrawIODiagramResp;
import com.github.app.dify.system.resp.PromptResp;
import com.github.app.dify.system.resp.SystemConfigResp;
import org.springframework.beans.BeanUtils;

/**
 * 系统实体转换工具类
 * 提供系统相关实体的转换方法
 */
public class SystemConverterUtil {
    
    /**
     * 将 SystemConfig 转换为 SystemConfigResp
     * 
     * @param config 系统配置实体
     * @return 系统配置响应对象
     */
    public static SystemConfigResp convertToResp(SystemConfig config) {
        if (config == null) {
            return null;
        }
        
        SystemConfigResp resp = new SystemConfigResp();
        BeanUtils.copyProperties(config, resp);
        return resp;
    }
    
    /**
     * 将 Prompt 转换为 PromptResp
     * 
     * @param prompt 提示词实体
     * @return 提示词响应对象
     */
    public static PromptResp convertToResp(Prompt prompt) {
        if (prompt == null) {
            return null;
        }
        
        PromptResp resp = new PromptResp();
        BeanUtils.copyProperties(prompt, resp);
        return resp;
    }
    
    /**
     * 将 DrawIODiagram 转换为 DrawIODiagramResp
     * 
     * @param diagram 图表实体
     * @return 图表响应对象
     */
    public static DrawIODiagramResp convertToResp(DrawIODiagram diagram) {
        if (diagram == null) {
            return null;
        }
        
        DrawIODiagramResp resp = new DrawIODiagramResp();
        resp.setId(diagram.getId());
        resp.setName(diagram.getName());
        resp.setDiagramType(diagram.getDiagramType());
        resp.setDiagramJson(diagram.getDiagramJson());
        resp.setUserId(diagram.getUserId());
        resp.setCreateTime(diagram.getCreateTime());
        resp.setUpdateTime(diagram.getUpdateTime());
        return resp;
    }
}

