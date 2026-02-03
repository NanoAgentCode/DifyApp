package com.github.app.dify.memo.service;

import com.github.app.dify.memo.resp.MemoResp;

import java.util.List;

/**
 * 备忘录服务
 */
public interface MemoService {

    /**
     * 列表（分页，按状态筛选）
     */
    List<MemoResp> list(Long userId, String status, int page, int size);

    /**
     * 当前用户已到期的待提醒列表（用于前端轮询弹通知）
     */
    List<MemoResp> listDue(Long userId);

    /**
     * 根据自然语言创建备忘录
     */
    MemoResp create(Long userId, String rawInput);

    /**
     * 标记为已提醒
     */
    void markDone(Long userId, Long id);

    /**
     * 取消
     */
    void cancel(Long userId, Long id);

    /**
     * 删除（软删）
     */
    void delete(Long userId, Long id);
}
