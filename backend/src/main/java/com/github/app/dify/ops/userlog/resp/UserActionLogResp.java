package com.github.app.dify.ops.userlog.resp;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户行为日志响应
 */
@Data
public class UserActionLogResp {

    /**
     * 日志ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
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
     * 操作描述
     */
    private String description;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 请求路径
     */
    private String requestPath;

    /**
     * 请求参数
     */
    private String requestParams;

    /**
     * 响应结果
     */
    private String result;

    /**
     * 异常信息
     */
    private String errorMsg;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 执行时长（毫秒）
     */
    private Long executionTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
