package com.github.app.dify.ops.userlog.service;

import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.ops.userlog.domain.UserActionLog;
import com.github.app.dify.ops.userlog.req.UserActionLogQueryReq;
import com.github.app.dify.ops.userlog.resp.UserActionLogResp;
import java.util.List;

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
    void batchDeleteLogs(List<Long> ids);
    
    /**
     * 获取所有操作类型选项（用于下拉菜单）
     */
    java.util.List<String> getActionTypes();

    /**
     * 获取所有操作模块选项（用于下拉菜单）
     */
    java.util.List<String> getModules();
}
