package com.github.app.dify.system.service;

import com.github.app.dify.system.domain.Prompt;
import com.github.app.dify.system.req.CreatePromptReq;
import com.github.app.dify.system.req.UpdatePromptReq;
import com.github.app.dify.system.resp.PromptResp;
import java.util.List;

/**
 * 提示词服务接口
 */
public interface PromptService {
    
    /**
     * 创建提示词
     */
    PromptResp createPrompt(CreatePromptReq req);
    
    /**
     * 更新提示词
     */
    PromptResp updatePrompt(Long id, UpdatePromptReq req);
    
    /**
     * 删除提示词（软删除）
     */
    void deletePrompt(Long id);
    
    /**
     * 根据ID获取提示词（返回实体）
     */
    Prompt getPromptEntityById(Long id);
    
    /**
     * 根据ID获取提示词（返回响应对象）
     */
    PromptResp getPromptById(Long id);
    
    /**
     * 获取提示词列表
     * @param keyword 关键词（搜索标题和内容）
     */
    List<PromptResp> listPrompts(String keyword);
}
