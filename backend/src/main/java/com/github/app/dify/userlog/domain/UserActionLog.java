package com.github.app.dify.userlog.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户行为日志实体类
 */
@Entity
@Table(name = "USER_ACTION_LOG")
@Data
public class UserActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 用户名
     */
    @Column(name = "username", length = 64)
    private String username;

    /**
     * 操作模块
     */
    @Column(name = "module", length = 50)
    private String module;

    /**
     * 操作类型
     */
    @Column(name = "action_type", length = 50)
    private String actionType;

    /**
     * 操作描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 请求方法
     */
    @Column(name = "method", length = 20)
    private String method;

    /**
     * 请求路径
     */
    @Column(name = "request_path", length = 500)
    private String requestPath;

    /**
     * 请求参数
     */
    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    /**
     * 响应结果（成功/失败）
     */
    @Column(name = "result", length = 20)
    private String result;

    /**
     * 异常信息
     */
    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    /**
     * IP地址
     */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /**
     * 用户代理
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * 执行时长（毫秒）
     */
    @Column(name = "execution_time")
    private Long executionTime;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;
}
