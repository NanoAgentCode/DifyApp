package com.github.app.dify.prompt.service;

import com.github.app.dify.prompt.req.PromptCreateReq;
import com.github.app.dify.prompt.req.PromptUpdateReq;
import com.github.app.dify.prompt.resp.PromptResp;

import java.util.List;

/**
 * 提示词服务
 */
public interface PromptService {

    /**
     * 分页查询提示词列表，支持关键词搜索
     */
    List<PromptResp> list(String keyword);

    /**
     * 根据ID获取提示词
     */
    PromptResp getById(Long id);

    /**
     * 创建提示词
     */
    PromptResp create(PromptCreateReq req);

    /**
     * 更新提示词
     */
    PromptResp update(Long id, PromptUpdateReq req);

    /**
     * 删除提示词（软删除）
     */
    void delete(Long id);
}
