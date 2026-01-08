package com.github.app.dify.userlog.req;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户行为日志查询请求
 */
@Data
public class UserActionLogQueryReq {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名（模糊查询）
     */
    private String username;

    /**
     * 操作模块
     */
    private String module;

    /**
     * 操作类型
     */
    private String actionType;

    /**
     * 操作结果：SUCCESS/FAILURE
     */
    private String result;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 20;
}
