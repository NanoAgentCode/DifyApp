package com.github.app.dify.userlog.service;

import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.userlog.domain.UserActionLog;
import com.github.app.dify.userlog.req.UserActionLogQueryReq;
import com.github.app.dify.userlog.resp.UserActionLogResp;

/**
 * 用户行为日志Service
 */
public interface UserActionLogService {

    /**
     * 保存日志
     */
    void saveLog(UserActionLog log);

    /**
     * 分页查询日志
     */
    PageResponse<UserActionLogResp> queryLogs(UserActionLogQueryReq request);

    /**
     * 根据ID查询日志详情
     */
    UserActionLogResp getLogById(Long id);

    /**
     * 删除日志
     */
    void deleteLog(Long id);

    /**
     * 批量删除日志
     */
    void batchDeleteLogs(java.util.List<Long> ids);
}
